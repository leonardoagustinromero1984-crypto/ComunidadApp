# RC1 — Entrega APK localDebug

**Proyecto:** LeoVer / ComunidadApp  
**Fecha de generación:** 2026-08-02 22:03 (hora local del build)  
**Propósito:** Primera APK integral para prueba manual RC1 (M00–M27, sin pagos).

> **Versión posterior:** RC1.1 con ícono corregido y onboarding — ver [`RC1.1-icono-y-apk-entrega.md`](RC1.1-icono-y-apk-entrega.md) (`artifacts\rc1.1\LeoVer-RC1.1-local-debug.apk`, versionCode 2).

## Origen Git

| Campo | Valor |
|-------|-------|
| SHA fuente | `702c914142cb92ef8cbfad0e7f9402cc06621a8b` |
| Commit | `chore(platform): consolidate modules and prepare rc1` |
| Rama | `main` |

## Build Gradle

| Campo | Valor |
|-------|-------|
| Comando | `.\gradlew.bat assembleLocalDebug --no-configuration-cache --max-workers=1 --console=plain` |
| Resultado | **BUILD SUCCESSFUL** (10m 18s) |
| Variante | `localDebug` |

## APK

| Campo | Valor |
|-------|-------|
| Nombre entrega | `LeoVer-RC1-local-debug.apk` |
| Ruta build (Gradle) | `app\build\outputs\apk\local\debug\app-local-debug.apk` |
| Ruta copia local | `artifacts\rc1\LeoVer-RC1-local-debug.apk` |
| Tamaño | 31 333 646 bytes (~29,9 MiB) |
| SHA-256 | `8B2D036C7A64E361E0B2331C6A45A9A71EF1986988AB8AC36E89F30FA63091BD` |

## Identidad de paquete (aapt dump badging)

| Campo | Valor |
|-------|-------|
| applicationId | `com.comunidapp.app.local` |
| versionName | `1.0-local` |
| versionCode | `1` |
| minSdk | `26` |
| targetSdk | `36` |
| compileSdk | `36` |
| Label visible | **LeoVer Local** |
| debuggable | **sí** (`application-debuggable`) |

## Permisos declarados (manifest fusionado)

| Permiso | Justificación |
|---------|---------------|
| `INTERNET` | Supabase, API, recursos remotos |
| `POST_NOTIFICATIONS` | M06 notificaciones push |
| `ACCESS_NETWORK_STATE` | Dependencias de red (merged) |
| `WAKE_LOCK` | Firebase Cloud Messaging |
| `com.google.android.c2dm.permission.RECEIVE` | FCM |
| `com.comunidapp.app.local.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX (receivers internos) |

**No declarados:** cámara, ubicación, almacenamiento legacy, micrófono, contactos, teléfono.

## Configuración de red y backend

| Aspecto | Estado |
|---------|--------|
| Cleartext traffic | Deshabilitado (`network_security_config`) |
| Supabase localDebug | Fallback a credenciales **staging** (HTTPS remoto) |
| `SUPABASE_CREDENTIAL_SOURCE` | `STAGING_FALLBACK` (según log Gradle) |
| Producción | **No utilizada** para este build |
| Mock mode | Disponible si no hay credenciales (no fue el caso en este build) |

## Revisión de secretos

| Verificación | Resultado |
|--------------|-----------|
| `service_role` en fuentes Android empaquetadas | **No detectado** (solo referencias en tests/SQL docs) |
| `SUPABASE_DB_PASSWORD` | **No detectado** |
| `PRIVATE_KEY` / `CLIENT_SECRET` / `WEBHOOK_SECRET` / `API_SECRET` | **No detectados** en binario (nombres) |
| `.env` / `local.properties` | **No versionados**; no empaquetados como assets |
| BuildConfig Supabase | URL + clave **cliente** (anon/publishable) compilada desde `local.properties` — esperado; **valor no documentado aquí** |
| Guard Gradle | Rechaza `service_role` en credenciales locales/staging |

**Acción:** generación continuada; no se encontraron secretos prohibidos.

## Alcance funcional

| Tema | Estado |
|------|--------|
| M24 Pagos | **Pospuesto** — sin flujo de pago |
| M28 | **No existe** |
| M27 webhooks | Entrega **simulada** |
| M27 OAuth | **Stub** |
| M26 IA | **Sin proveedor externo real** |
| SQL-001 (039–052) | **Pendiente** — no tratado en esta etapa |

## Entornos no modificados

| Entorno | Modificado |
|---------|------------|
| Staging Supabase | **NO** |
| Producción | **NO** |
| Migraciones SQL | **NO aplicadas** |

## Instalación

Ver `RC1-instalacion-android.md`.

## Advertencia

APK **debug** firmada con clave debug. No es release de producción. No distribuir públicamente.

## Prueba física

| Estado |
|--------|
| **PENDIENTE** — APK generada; ejecución manual no realizada en esta etapa |

## Registro manual

Ver `RC1-ejecucion-prueba-manual.md` (17 recorridos en estado PENDIENTE).
