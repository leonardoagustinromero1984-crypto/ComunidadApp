# M07 — Defecto: longitud OTP de correo en Android

**Fecha:** 2026-07-18  
**Producto:** LeoVer  
**Rama:** `m07/fix-email-otp-length`  
**Entorno de evidencia:** APK smoke vs Supabase staging (ref …`mizz`)  
**Base remota modificada:** **NO**

---

## Defecto

Supabase staging envía un OTP de correo de **8 dígitos**. La UI Android truncaba la entrada a **6** (`filter { isDigit() }.take(6)`), por lo que el usuario no podía completar la confirmación.

## Evidencia

- Smoke test manual del APK contra staging.
- Campo “Código de 6 dígitos” + `take(6)`.
- Confirmación de correo: **FAIL**.
- No es defecto de migraciones SQL ni de Auth remoto.

## Comportamiento anterior

- Solo 6 dígitos aceptados en UI.
- Texto y label fijados a “6 dígitos”.
- Confirmar habilitado con `length >= 6` (pero el 7º/8º nunca entraban).
- Validación de repositorio: `length < 6` → error; sin tope superior explícito en UI.

## Comportamiento corregido

- Solo números.
- Longitud admitida: **6–10** dígitos (compatibilidad 6 + OTP 8 de staging).
- Pegar 8 dígitos no trunca.
- Sin verificación automática al llegar a 6.
- Confirmar habilitado solo con longitud válida.
- Texto genérico: “Ingresá el código que recibiste por correo”.
- Código normalizado (`trim`) enviado completo a Supabase `verifyEmailOtp`.
- Errores se limpian al editar.
- OTP no se registra en logs/analytics/mensajes de error.

## Archivos modificados

| Archivo | Cambio |
|---|---|
| `domain/auth/validation/EmailOtpValidators.kt` | Nuevo validador 6–10 |
| `ui/.../ForgotPasswordScreen.kt` (`EmailVerificationScreen`) | Campo hasta 10; textos; teclado numérico |
| `viewmodel/LoginViewModel.kt` (`EmailVerificationViewModel`) | Validación + `clearOtpFeedback` |
| `data/repository/AuthRepository.kt` (Mock) | Usa `EmailOtpValidators` |
| `data/repository/SupabaseAuthRepository.kt` | Usa `EmailOtpValidators` |
| `domain/auth/AuthErrorMapper.kt` | Mensajes OTP inválido/expirado |
| Tests domain/repo/viewmodel/error mapper | Casos 5/6/8/10/11, pegado, trim |
| `docs/04-calidad/M07-defecto-email-otp-longitud.md` | Este documento |
| `docs/04-calidad/M07-reporte-validacion-staging.md` | Nota smoke OTP |

## Pruebas

- `EmailOtpValidatorsTest`: 5 inválido; 6/8/10 OK; 11 bloqueado; letras; trim; pegado 8.
- `MockAuthRepositoryTest`: 8 dígitos con espacios; rechazo 5 y 11.
- `AuthViewModelsTest`: éxito 8; rechazo 5 sin filtrar OTP en mensaje; clear error.
- `AuthErrorMapperTest`: `otp_expired`; `Invalid OTP token` sin filtrar código.

## Resultado

Corrección Android lista en rama dedicada.  
**APK nuevo:** generado en `apk/Leover-debug.apk` (18/07/2026).  
**Validación manual staging:** **PENDIENTE** con ese APK.

```text
EMAIL OTP 8 DÍGITOS — PENDIENTE DE REVALIDACIÓN CON APK NUEVO
```

Migraciones / Auth Supabase / producción: **sin cambios**.
