# LEOVER — M01 Auditoría inicial (Identidad y Autenticación)

**Módulo:** M01 — Identidad y Autenticación  
**Etapa:** 1 — Auditoría y diseño  
**Fecha:** 2026-07-14  
**Rama:** `m01/identidad-autenticacion-auditoria`  
**Dependencia:** M00 revisado y aprobado  
**Backend oficial:** Supabase Auth (sin NestJS ni otro backend)  
**Alcance de esta etapa:** solo inventario y diseño; **sin modificar funcionalidad**  

**Documentos de entrada (orden leído):**

1. [`docs/01-producto/D01-Modulos-y-Orden.md`](../01-producto/D01-Modulos-y-Orden.md)  
2. [`docs/03-modulos/M00-Fundacion-Tecnica.md`](../03-modulos/M00-Fundacion-Tecnica.md)  
3. [`docs/02-arquitectura/M00-cierre-final.md`](M00-cierre-final.md)  
4. ADR-0001 … ADR-0005  
5. [`docs/03-modulos/M01-Identidad-y-Autenticacion.md`](../03-modulos/M01-Identidad-y-Autenticacion.md)  

**Estado Git al auditar:**

| Ref | Nota |
|-----|------|
| HEAD tip | `f6049eb` (spec M00 Etapa 4) |
| Working tree | Incluye implementación M00 Etapa 4 **aún no commiteada** (core, cierres M00, etc.) + spec M01 |
| Stash de respaldo | `stash@{0}: backup/pre-m01-identidad-autenticacion` |
| WIP GPS/mapas/pagos | `wip/gps-mapas-pagos` @ `68ceb82` — **no mezclado** |

---

## 1. Resumen ejecutivo

La app **ya tiene autenticación email/password usable** con doble modo (mock / Supabase), restauración de sesión, deep link OAuth/callback, pantallas de login/registro/verificación/olvido de contraseña, y un único `AuthProvider` + `AuthRepository`.

**No hay que recrear Auth desde cero.** M01 debe consolidar, cerrar gaps de la spec y endurecer contratos/calidad **reutilizando** lo existente.

| Área | Estado frente a M01 |
|------|---------------------|
| Login / registro email | Presente (mock + Supabase) |
| Sesión / splash gate | Presente (`SessionViewModel`) |
| Verificación email + OTP + reenvío (cooldown UI) | Presente |
| Envío de recuperación | Presente |
| Aplicar nueva contraseña tras deep link (Supabase) | **Stub** — no implementado en remoto |
| Consentimientos legales versionados | **Ausente** |
| Eliminación de cuenta + Edge Function | **Ausente** |
| Estados oficiales M01 (`AuthState` rico) | **Parcial** — solo `Loading/LoggedOut/LoggedIn` |
| `AppError` / `AppResult` en auth | **No adoptado** aún |
| Tests unitarios de auth | **Ausentes** (0 suites dedicadas) |
| OAuth social (Google, etc.) | **Ausente** (dejar fuera si no existe) |

**Validación de estado inicial (esta etapa, local):**

| Comando | Resultado |
|---------|-----------|
| `:app:assembleDebug` | **SUCCESS** |
| `:app:testDebugUnitTest` | **SUCCESS** — 20 tests, 0 failures |
| `:app:lintDebug` | **SUCCESS** — **0 errors**, 38 warnings, 1 hint |

CI remoto (GitHub Actions): no ejecutado/observado en esta sesión; el workflow de M00 permanece configurado.

---

## 2. Inventario de autenticación actual

### 2.1 Proveedor y repositorio (único camino — no duplicar)

| Pieza | Ruta | Rol |
|-------|------|-----|
| `AuthProvider` | `app/.../data/repository/AuthProvider.kt` | Service locator; elige mock vs `SupabaseAuthRepository` según `AppConfigProvider.featureFlags().useSupabase` |
| `AuthRepository` (interface) | `app/.../data/repository/AuthRepository.kt` | Contrato: login, register, reset send/apply, verify/resend/OTP, current user, logout, `observeAuthState` |
| `MockAuthRepository` | mismo archivo | Implementación in-memory |
| `SupabaseAuthRepository` | `app/.../data/repository/SupabaseAuthRepository.kt` | SDK oficial Supabase Auth |
| Cliente Supabase | `app/.../data/remote/supabase/SupabaseClientProvider.kt` | **Único** cliente; no crear otro |
| Redirect | `app/.../data/remote/supabase/SupabaseAuthConfig.kt` | `com.comunidapp.app://login-callback` |
| Perfil post-auth | `UserSupabaseDataSource` vía repositorio | Lectura/alta de `public.users` |

**Regla de diseño confirmada:** no introducir un segundo `AuthProvider`, cliente ni repositorio paralelo. Extender la interface/implementaciones existentes o introducir capas de dominio **delante** sin romper el locator.

### 2.2 Modelos y mock

| Pieza | Ruta |
|-------|------|
| `User` / `AccountType` | `data/model/` |
| `AuthAccount` | `data/model/AuthAccount.kt` (solo mock) |
| Credenciales demo | `MockAuthDatabase` — email `maria@email.com`, password `demo1234` (≥8), verificada |
| Store usuarios mock | `MockUserStore` / `MockData` |

**Quirk mock (riesgo QA):** si el email no existe en la DB mock, `login` acepta cualquier password y entra como `MockData.currentUser` con ese email (`AuthRepository.kt`). Útil para demos; **inadecuado** como contrato seguro de M01.

### 2.3 Sesión y UI shell

| Pieza | Ruta | Comportamiento |
|-------|------|----------------|
| `SessionState` | `viewmodel/SessionViewModel.kt` | `Loading` \| `LoggedOut` \| `LoggedIn` |
| `SessionViewModel` | idem | Observa `observeAuthState` → perfil `UserRepository` |
| Splash | `MainActivity` + `installSplashScreen` | Condicionado a `SessionState.Loading` |
| Nav gate | `navigation/ComunidappNavGraph.kt` | startDestination `MAIN` vs `LOGIN` |
| Logout | `SessionViewModel.logout` + Profile | Llama `authRepository.logout()` |

### 2.4 Pantallas, rutas y ViewModels de auth

| Ruta (`NavRoutes`) | Pantalla | ViewModel |
|--------------------|----------|-----------|
| `login` | `ui/screens/login/LoginScreen.kt` | `LoginViewModel` (en `LoginViewModel.kt`) |
| `register` | `RegisterScreen.kt` | `RegisterViewModel` |
| `forgot_password` | `ForgotPasswordScreen.kt` | `ForgotPasswordViewModel` |
| `email_verification/{email}` | `EmailVerificationScreen` (mismo archivo que forgot) | `EmailVerificationViewModel` |

**Presentes en UI:** email, password, confirm password (registro/reset mock), tipo de cuenta, OTP 6 dígitos, reenvío con cooldown **60 s**, loading y mensajes de error inline.

**Ausentes en UI:** checkboxes de términos/privacidad, pantalla “seguridad de cuenta / eliminar”, resultado de eliminación, pantalla dedicada de reset post-deep-link para Supabase, error de configuración recuperable formal.

### 2.5 Deep links

| Elemento | Detalle |
|----------|---------|
| Scheme/host | `com.comunidapp.app` / `login-callback` |
| Manifest | Intent VIEW en `MainActivity` (`singleTask`) |
| Handler | `MainActivity.handleAuthDeepLink` → `supabase.handleDeeplinks(intent)` solo si `useSupabase` |
| Uso | Signup confirm + password recovery (redirect URL) |

**No inventar dominio web ni cambiar el paquete** en M01 (alineado a ADR-0006).

### 2.6 Supabase Auth (repo)

| Tema | Hallazgo |
|------|----------|
| Proveedor en código | Solo **Email** (`signInWith(Email)` / `signUpWith(Email)`) |
| Social | No hay Google/Apple/Facebook en app |
| `supabase/config.toml` | **No versionado** — settings del proyecto viven en el dashboard |
| Plantillas email | `supabase/email-templates/` (confirm, reset, magic-link, change-email, invite, reauthentication) |
| Edge Functions | Solo `supabase/functions/push/` — **no** `delete-account` |
| Triggers | `004_auth_user_profile_trigger.sql` + override en `009_profile_privacy.sql` → `handle_new_user` inserta `public.users` al crear `auth.users` |
| Tabla perfil | `public.users` (FK `auth.users`, RLS select authenticated / insert-update own) |
| Migraciones | `001`–`013` presentes; **no** hay `user_consents` ni `account_deletion_requests` |
| RLS delete user | Sin política DELETE en `public.users` (alineado a ausencia de borrado cliente) |

### 2.7 Manejo de errores actual

- Repositorios devuelven `Result` + `IllegalArgumentException` / `EmailNotVerifiedException`.
- `SupabaseAuthRepository.mapSupabaseException` traduce mensajes comunes (credenciales, ya registrado, rate limit, OTP expirado, password ≥ 6).
- **No** usa todavía `AppError` / códigos M01 (`INVALID_CREDENTIALS`, etc.).
- Mensaje de password débil habla de **6** caracteres; M01 exige **mínimo 8** en cliente.

### 2.8 Logging y secretos

- Repos de auth **no** loguean passwords.
- M00 aporta `AppLogger` con sanitización (JWT, email, `password=`).
- Password demo hardcodeada en mock source (aceptable solo como fixture documentada).
- `service_role` no aparece en Android (correcto).

### 2.9 Tests existentes relacionados

| Suite | Relación con auth |
|-------|-------------------|
| `AppLoggerSanitizeTest` | Adyacente (no filtrar PII en logs) |
| `AppErrorMapperTest` | Infra M00; aún no usada por auth |
| `AppConfigProviderTest` | Flag `useSupabase` |
| Resto | Mappers/Ejemplo — **ningún** test de `AuthRepository` / ViewModels de login |

---

## 3. Flujos: qué existe vs qué no se pudo probar

### 3.1 Probado / verificable en esta etapa

| Flujo | Evidencia |
|-------|-----------|
| Compilación + tests + lint | Comandos locales SUCCESS (sección 1) |
| Cableado de código mock/remoto | Lectura estática de repositorios, nav y UI |
| Estructura migraciones/triggers/templates | Archivos en `supabase/` |

### 3.2 No probado en dispositivo/emulador ni proyecto Supabase real

Documentado con honestidad — **no se afirmó éxito de estos flujos:**

| Flujo | Motivo |
|-------|--------|
| Registro + email de confirmación real | Requiere proyecto Supabase, SMTP/templates activos y buzón |
| Deep link confirm / recovery en device | Requiere intent externo + app instalada con scheme |
| Login con cuenta real no verificada | Remoto |
| Rate limit de reenvío en producción | Remoto |
| Restauración tras kill process con sesión persistida | Manual; SDK lo implica pero no se midió aquí |
| Reset password end-to-end Supabase | Código remoto de `resetPassword` está **stubbeado** |
| Eliminación de cuenta | No existe |
| Consentimientos | No existen |
| Dashboard Auth (providers, redirect URLs, confirm email ON/OFF) | Sin acceso al proyecto cloud en esta auditoría |
| CI remoto del branch M01 | No observado |

---

## 4. Diferencias contra la especificación M01

### 4.1 Cumplimiento parcial / ok para reutilizar

| Requisito M01 | Estado |
|---------------|--------|
| Reutilizar auth existente | OK — base sólida |
| Email normalizado trim+lower | OK en repos |
| Dual mock/remoto misma UI | OK vía `AuthProvider` |
| Restaurar sesión | OK vía `sessionStatus` / splash |
| Verificación + resend con cooldown UI | OK (60 s) |
| Deep link actual | OK — no romper |
| Sin Hilt / sin Retrofit / sin NestJS | OK |
| Sin renombre de paquete | OK |
| Sin proveedores sociales nuevos | OK (ninguno hoy) |

### 4.2 Gaps a cerrar en etapas 2–5

| Gap | Prioridad | Notas |
|-----|-----------|-------|
| Estados oficiales (`Initializing`, `EmailVerificationRequired`, …) | Alta | Hoy solo 3 estados de sesión |
| Validación password ≥ 8 + email max 254 | Alta | UI/VM sin validador compartido; Supabase mensaje “6” |
| Consentimientos + tabla `user_consents` | Alta | UI y migración faltan |
| Reset password remoto post-deep-link (`updateUser` / session recovery) | Alta | Stub actual |
| Mensajes anti-enumeración en recovery | Media | Revisar copy mock/remoto |
| Mapeo a `AppError` códigos M01 | Alta | Etapa 2 |
| Logout: limpieza sensible / tokens FCM | Media | Logout existe; revisar limpieza |
| Eliminación de cuenta + Edge Function | Alta | Etapa 4 |
| `AccountType` en registro | Deuda M02 | Existe en UI; M01 no debe expandir roles de negocio |
| Tests unitarios auth | Alta | Etapa 2+ |
| Analytics eventos auth sin PII | Baja | Documentar; M07 proveedor externo fuera |
| FLAG_SECURE / capturas en forms sensibles | Baja | Decidir y documentar |
| Mock login abierto a emails desconocidos | Media | Endurecer mock para CI/demos controlados |
| `runBlocking` en `logout()` Supabase | Media | Olor de hilo; corregir con cuidado |

### 4.3 Fuera de alcance (no tocar en M01)

- Perfil social completo, RBAC, orgs → **M02+**  
- GPS / mapas / pagos  
- MFA, biometría, passkeys, teléfono  
- Renombre `com.comunidapp.app`  
- Hilt / módulos Gradle / otro backend  

---

## 5. Riesgos

| ID | Riesgo | Mitigación propuesta |
|----|--------|----------------------|
| R1 | Duplicar AuthProvider/cliente al “reorganizar” paquetes | Extender in-place; `data/auth/` solo si se migra sin segunda instancia |
| R2 | Reset Supabase incompleto → usuarios bloqueados | Completar update password en sesión recovery antes de declarar DoD |
| R3 | Mock permisivo enmascara bugs de login | Fixture cerrada + tests |
| R4 | Eliminación sin Edge Function + service_role en cliente | Prohibido; Edge Function JWT-validated |
| R5 | Trigger `handle_new_user` vs `createUser` cliente | Mantener idempotencia; no duplicar lógica de perfil (M02) |
| R6 | Cambiar scheme deep link | No hacerlo en M01 |
| R7 | Mezclar WIP mapas/pagos | Mantener rama limpia |
| R8 | Enumeración de cuentas vía mensajes | Copy genérico + codes internos |

---

## 6. Arquitectura objetivo (diseño, sin implementar aún)

Respetar monolito actual y ADR-0002/0003. **Propuesta adaptativa** (no forzar move masivo):

```text
# Preferido si se introduce dominio sin romper paths actuales:
domain/auth/          # AuthState, AuthUser (proyección), validators, AuthErrorCodes
data/repository/      # AuthRepository + Mock/Supabase (EXISTENTE — extender)
ui/screens/login/…    # pantallas actuales; nuevos screens solo si faltan
viewmodel/            # Session + auth VMs; evolucionar estados
core/                 # AppConfig, AppLogger, AppResult (M00)
```

La estructura sugerida en la spec (`data/auth/`, `ui/auth/`) es **opcional** y solo se adopta si el costo de move es bajo; prioridad = contratos claros, no shuffle de paquetes.

**Fuente de verdad de sesión:** un `StateFlow` de estado auth (evolución de `SessionViewModel` o wrapper de dominio), no flags por pantalla.

---

## 7. Archivos que se propone crear / modificar (etapas futuras)

### 7.1 Crear (propuesta)

| Archivo / artefacto | Etapa | Motivo |
|---------------------|-------|--------|
| `domain/auth/AuthState.kt` (+ commands/user projection) | 2 | Estados oficiales |
| `domain/auth/validation/*` | 2 | Email/password/consents |
| `domain/auth/AuthErrorCodes` + mapper → `AppError` | 2 | Contrato errores |
| Tests `*Auth*Test` / validadores | 2–4 | Cobertura |
| UI consentimientos en registro | 3 | M01-RN-004 |
| Pantalla reset post-link (o extensión forgot) | 4 | Cerrar stub |
| UI account security / delete | 4 | Eliminación |
| Migración `NNN_user_consents.sql` (siguiente N) | 3–4 | Tras aprobar diseño |
| Migración opcional `account_deletion_requests` | 4 | Si hace falta auditoría |
| Edge Function `delete-account` | 4 | Privilegiada |
| `docs/02-arquitectura/M01-cierre-final.md` | 5 | Cierre |

### 7.2 Modificar (propuesta)

| Archivo | Cambio previsto |
|---------|-----------------|
| `AuthRepository` + Mock/Supabase | Métodos consent/delete/reset remoto; sin romper firma existente where possible |
| `AuthProvider` | Solo si flags lo requieren — **no duplicar** |
| `LoginViewModel.kt` (+ VMs) | Validadores, AppError, anti-doble-submit |
| `SessionViewModel` | Estados más ricos / limpieza logout |
| `RegisterScreen` | Checkboxes legales |
| `ForgotPassword*` / SupabaseAuthRepository.resetPassword | Flujo real post-deep-link |
| `ComunidappNavGraph` / `NavRoutes` | Rutas nuevas mínimas |
| Docs arquitectura / README auth | Reflejar contratos |

### 7.3 No crear

- Segundo cliente Supabase  
- Retrofit/Hilt/NestJS  
- Proveedores sociales nuevos  
- Migraciones en Etapa 1 (esta)  

---

## 8. Plan por etapas (post-aprobación de esta auditoría)

| Etapa | Contenido | Parada |
|-------|-----------|--------|
| **1** | Auditoría (este documento) | Aquí |
| **2** | Contratos dominio, validadores, mapper errores, tests | Spec M01 §17 |
| **3** | Registro + consentimientos + verificación + login + sesión | Sin delete aún |
| **4** | Recovery completo, logout endurecido, eliminación + Edge Function + RLS | |
| **5** | Smoke manual documentado, build/tests/lint, `M01-cierre-final.md` | Luego M02 |

Criterio transversal: no pasar de etapa con assemble/tests/lint en rojo (M01-RN-015).

---

## 9. Decisiones que requieren aprobación

1. **¿Endurecer mock** para rechazar emails desconocidos (rompe demos “cualquier email”)?  
2. **¿Mover paquetes** hacia `domain/auth` + `ui/auth` o **extender paths actuales**? Recomendación: extender actuales; domain nuevo mínimo.  
3. **¿Password mínima 8** en app aunque Supabase proyecto aún acepte 6? (sí según spec; alinear dashboard después).  
4. **¿Registro sigue mostrando `AccountType`?** Spec M01 dice no roles de negocio — ¿ocultar/diferir a M02 o mantener solo `PERSON` fijo?  
5. **¿Tabla `account_deletion_requests` obligatoria** o delete síncrono vía Edge Function + logs?  
6. **¿FLAG_SECURE** en pantallas de password?  
7. **Templates/redirect URLs del dashboard Supabase:** confirmar que coinciden con `com.comunidapp.app://login-callback` (fuera del repo; requiere dueño del proyecto).  
8. **Commit de M00 Etapa 4** en working tree: ¿commitear/mergear antes de Etapa 2 de M01?

---

## 10. Checklist Etapa 1

- [x] Rama `m01/identidad-autenticacion-auditoria`  
- [x] Cambios locales preservados (stash backup + working tree intacto)  
- [x] Sin mezclar GPS/mapas/pagos  
- [x] Sin modificar funcionalidad de auth  
- [x] Sin migraciones / Edge Functions / reconfiguración Supabase  
- [x] Sin proveedores sociales nuevos  
- [x] Sin Hilt / Retrofit / NestJS / renombre de paquete  
- [x] Build + tests + lint registrados  
- [x] Flujos no probables documentados con honestidad  
- [x] Este archivo creado  

---

## 11. Instrucción de parada (Etapa 1)

**Etapa 1 completa** en el momento de la auditoría. Etapa 2 se ejecutó después de la aprobación (ver §12).

**Entregable Etapa 1:** `/docs/02-arquitectura/M01-auditoria-inicial.md`

## 12. Actualización post Etapa 2 (implementado)

Tras aprobación de esta auditoría, Etapa 2 consolidó:

- D-M01-01…09 (decisions) → ver `M01-etapa-2-cierre.md`
- Mock endurecido; demo password `demo1234`
- `domain/auth/*` + validadores + `AuthErrorMapper` → `AppError`
- `SessionViewModel.authState` + logout suspendido sin `runBlocking`
- Sin migraciones / Edge Functions / pantallas completas

