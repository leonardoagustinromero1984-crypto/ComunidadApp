# LeoVer — Diagnóstico y hotfix de autenticación en APK localDebug

> Estado: hotfix aplicado tras M12 Bloque 3. No inicia el Bloque 4.
> Variante afectada: **localDebug** (flavor `local`, buildType `debug`).
> No se versiona ningún secreto. Este documento nunca imprime claves.

## 1. Síntoma

Al instalar el APK **localDebug** en un dispositivo físico, cualquier intento de
iniciar sesión, registrarse o refrescar sesión mostraba un **mensaje permanente
de error de autenticación**, genérico e imposible de superar (mapeado a
`UNKNOWN_AUTH_ERROR`). La app quedaba efectivamente inutilizable en auth, aunque
las credenciales fueran correctas.

## 2. Causa raíz

El flavor `local` embebía en `BuildConfig` una `SUPABASE_URL` apuntando al
**emulador** (`http://10.0.2.2:55321`, host de Supabase CLI local):

- `10.0.2.2` sólo existe dentro del emulador de Android; en un **APK físico** no
  resuelve a nada.
- El esquema es **cleartext (`http://`)**, y la app tiene el tráfico claro
  **deshabilitado** (`network_security_config.xml` con
  `cleartextTrafficPermitted="false"`).

Resultado: cada llamada de red a Supabase fallaba con errores tipo
`CLEARTEXT communication ... not permitted` o `Failed to connect to /10.0.2.2`.
Esos errores no estaban mapeados y caían en el catch-all
`UNKNOWN_AUTH_ERROR`, produciendo el mensaje permanente.

### Variante

- **localDebug**: única variante que resolvía credenciales desde `SUPABASE_URL` /
  `SUPABASE_ANON_KEY` sin política de host. `staging` y `production` ya exigían
  HTTPS remoto.

## 3. Corrección

La corrección abarca cuatro capas, todas defensivas y sin exponer secretos.

### 3.1 Gradle — resolución de credenciales locales

`app/build.gradle.kts` incorpora `resolveLocalSupabase()`:

- Si `SUPABASE_URL` es HTTPS remota y hay `SUPABASE_ANON_KEY` → se usa
  (`source = "SUPABASE_URL"`).
- Si `SUPABASE_URL` está ausente o es **local/emulador/http**
  (`isForbiddenLocalHost`), se hace **fallback automático a
  `SUPABASE_STAGING_*`** (`source = "STAGING_FALLBACK"`), de modo que el APK
  físico use un host HTTPS válido.
- Si no hay credenciales remotas usables → `SUPABASE_ENABLED=false` (modo mock,
  sin crash).
- `service_role` está **prohibido** en credenciales Android (falla el build).
- Las tareas `assembleLocal*` verifican en `doFirst` que la URL resuelta sea
  HTTPS remota; nunca localhost / `10.0.2.2` / http.

### 3.2 Runtime policy — `SupabaseUrlPolicy`

`core/config/SupabaseUrlPolicy.kt` valida la URL en runtime sin exponer keys:

- `isForbiddenHost`: rechaza blank, `localhost`, `127.0.0.1`, `10.0.2.2`, `http://`.
- `isUsableRemoteUrl`: exige `https://` + host con punto + no prohibido.
- `hostOf`: extrae sólo el host (para logs), nunca la query ni la key.
- `credentialsPresent`: `enabled && isUsableRemoteUrl(url) && anonKey no vacía`.

`SupabaseClientProvider` valida la URL vía `SupabaseUrlPolicy.isUsableRemoteUrl`
antes de crear el cliente; si no es usable lanza `CONFIGURATION_ERROR`
(mensaje seguro) en lugar de crashear o colgar en red.

### 3.3 Error mapping — `AuthErrorMapper`

Mensajes para personas usuarias, seguros y accionables:

| Código | Mensaje |
| --- | --- |
| `INVALID_CREDENTIALS` | El correo o la contraseña son incorrectos. |
| `EMAIL_NOT_VERIFIED` | Tu correo todavía no fue confirmado. |
| `SESSION_EXPIRED` | Tu sesión venció. Iniciá sesión nuevamente. |
| `NETWORK_UNAVAILABLE` | No pudimos conectarnos. Revisá tu conexión. |
| `CONFIGURATION_ERROR` | La configuración de Supabase no está disponible en esta versión. |
| `PERMISSION_DENIED` (nuevo) | No tenés permisos para realizar esta acción. |
| `UNKNOWN_AUTH_ERROR` | No pudimos completar el inicio de sesión. Intentá de nuevo. |

Reglas de detección clave del hotfix:

- **cleartext / `failed to connect` / timeout / host no resoluble → `NETWORK`**
  (antes caían en `UNKNOWN`).
- **`401` / `invalid JWT` / `refresh token invalid` → `SESSION_EXPIRED`.**
- **`403` / `row-level security` / `forbidden` → `PERMISSION_DENIED`** (nuevo).
- Errores de config de Supabase → `CONFIGURATION_ERROR`.
- El mensaje de `UNKNOWN` cambió (ya no dice "Ocurrió un problema de
  autenticación").

### 3.4 Diagnóstico seguro — `AuthConfigDiagnostics`

`logSafe()` registra un snapshot **sin secretos**: `buildType`, `flavor`,
`useSupabase`, `urlPresent`, `host`, `anonKeyPresent`, `anonKeyLength`,
`credentialSource`, `sessionPresent`, `accessTokenPresent`, `expiresAt`,
`exceptionClass`, `httpStatus`.

- **Nunca** concatena el valor de `BuildConfig.SUPABASE_ANON_KEY`, access token
  ni refresh token: sólo `anonKeyPresent` / `anonKeyLength`.

## 4. Comportamiento de sesión

- **Ausencia de sesión** → estado `Unauthenticated` (no error permanente).
- **401** (JWT/refresh inválido/expirado) → se **limpia** la sesión y se pide
  reingresar (`SESSION_EXPIRED`).
- **403 / RLS** → **no cierra sesión**; sólo informa falta de permisos
  (`PERMISSION_DENIED`). La sesión sigue siendo válida.
- **Errores de red** → **no limpian** la sesión (el usuario reintenta al volver
  la conexión).

## 5. Pruebas

- `app/src/test/java/com/comunidapp/app/domain/auth/AuthErrorMapperTest.kt`
  (mensajes/códigos; casos 401, 403, config, cleartext).
- `app/src/test/java/com/comunidapp/app/core/config/SupabaseUrlPolicyTest.kt`
  (URL ausente/blank, key blank, HTTPS válida, `10.0.2.2`, localhost, cleartext,
  `hostOf` sin secretos).
- `app/src/test/java/com/comunidapp/app/core/config/AuthHotfixLocalDebugGuardsTest.kt`
  (guardas estáticas: Gradle, cleartext, mapper, provider, sin service_role,
  migraciones 040–047, diagnóstico seguro, INTERNET, doc de fallback).

Ejecutar:

```bash
./gradlew :app:testLocalDebugUnitTest
```

## 6. Compilación y APK

```bash
./gradlew :app:assembleLocalDebug
```

- El build de `local` copia el APK a: `apk/LeoVer-local-debug.apk`
  (ruta relativa a la raíz del repo — placeholder de salida).
- Requiere `SUPABASE_STAGING_URL` + `SUPABASE_STAGING_PUBLISHABLE_KEY`
  (o `SUPABASE_STAGING_ANON_KEY`) en `local.properties` para que el fallback
  produzca un APK con backend HTTPS real; si faltan, el APK queda en modo mock.

## 7. Pasos manuales (instalar y validar)

1. Configurar `local.properties` (ver `local.properties.example`): dejar
   `SUPABASE_STAGING_*` con un proyecto HTTPS remoto. No usar `10.0.2.2` /
   `localhost` / `http`.
2. `./gradlew :app:assembleLocalDebug`.
3. Instalar en el dispositivo físico:
   `adb install -r apk/LeoVer-local-debug.apk`.
4. Abrir la app y verificar en logcat (tag `AuthConfigDiag`) que
   `host=<staging>.supabase.co`, `source=STAGING_FALLBACK`,
   `anonKeyPresent=true` (sin que aparezca el valor de la key).
5. Probar: credenciales incorrectas → "El correo o la contraseña son
   incorrectos."; sin conexión → "No pudimos conectarnos. Revisá tu conexión.";
   login correcto → sesión iniciada (sin mensaje permanente).

## 8. Seguridad y alcance

- **Sin secretos versionados**: `local.properties` está en `.gitignore`;
  sólo se versiona `local.properties.example` con placeholders ficticios.
- **Nunca** se loguea la anon key, access/refresh token ni Authorization.
- **Sin `service_role`** en fuentes Android (Gradle y runtime lo prohíben).
- Migraciones **040–047 intactas** (nombres y SQL sin cambios).
- La migración **047 no se aplica** en este hotfix; no se modifica ningún SQL.
- **Bloque 4 no iniciado**: este trabajo es exclusivamente un hotfix de auth.
