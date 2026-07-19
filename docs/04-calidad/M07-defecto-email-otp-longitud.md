# M07 — Defecto: longitud OTP de correo en Android

**Fecha apertura:** 2026-07-18
**Fecha cierre manual:** 2026-07-19
**Producto:** LeoVer
**Rama fix:** `m07/fix-email-otp-length`
**Commit fix:** `b2189b9`
**Rama cierre smoke:** `m07/cierre-smoke-apk-otp`
**Entorno:** staging / pruebas (project ref terminado en `mizz`)
**Base remota modificada:** **NO**

---

## Estado

```text
EMAIL OTP 8 DÍGITOS — PASS
DEFECTO OTP CERRADO
```

---

## Defecto (histórico)

Supabase staging envía un OTP de correo de **8 dígitos**. La UI Android truncaba la entrada a **6** (`filter { it.isDigit() }.take(6)`), por lo que el usuario no podía completar la confirmación.

## Evidencia inicial

- Smoke test manual del APK contra staging.
- Campo “Código de 6 dígitos” + `.take(6)`.
- Confirmación de correo: **FAIL**.
- No es defecto de migraciones SQL ni de Auth remoto.

## Causa original

- Truncado en UI: `.take(6)`.
- Backend (Supabase Auth staging) enviaba OTP de **8** dígitos.
- Textos/labels hardcodeados a “6 dígitos”.

## Comportamiento corregido (b2189b9)

- Solo números.
- Longitud admitida: **6–10** dígitos.
- Pegar 8 dígitos **no** trunca.
- Sin verificación automática al llegar a 6.
- Confirmar habilitado solo con longitud válida.
- Texto genérico: “Ingresá el código que recibiste por correo”.
- OTP completo (tras `trim`) enviado a Supabase `verifyEmailOtp`.
- Errores se limpian al editar.
- OTP no se registra en logs/analytics/mensajes de error.

## Pruebas automáticas

- `EmailOtpValidatorsTest`, repo, ViewModel, `AuthErrorMapperTest`.
- Suite: **559** tests, 0 failures (verificación en cierre smoke).
- `assembleDebug` / `testDebugUnitTest` / `lintDebug`: **PASS**.

## Validación manual con APK nuevo

| Ítem | Resultado |
|---|---|
| APK | `apk/Leover-debug.apk` (18/07/2026 ~20:36) |
| OTP 8 dígitos recibido | PASS |
| Ingreso/pegado 8 dígitos | PASS (sin truncar) |
| Confirmación correo | PASS |
| Login posterior | PASS |
| Base remota tocada por el fix | **NO** |
| Migraciones remotas | siguen **001–033** |

Detalle smoke: `docs/04-calidad/M07-smoke-test-apk-staging.md`.

## Resultado

**Defecto OTP cerrado.**
Migraciones / Auth Supabase / producción: **sin cambios**.
Matriz SQL staging: **sigue pendiente de diagnóstico** (no bloquea el cierre de este defecto).
