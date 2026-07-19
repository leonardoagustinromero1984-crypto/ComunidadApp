# M07 â€” Defecto: longitud OTP de correo en Android

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
EMAIL OTP 8 DÃGITOS â€” PASS
DEFECTO OTP CERRADO
```

---

## Defecto (histÃ³rico)

Supabase staging envÃ­a un OTP de correo de **8 dÃ­gitos**. La UI Android truncaba la entrada a **6** (`filter { it.isDigit() }.take(6)`), por lo que el usuario no podÃ­a completar la confirmaciÃ³n.

## Evidencia inicial

- Smoke test manual del APK contra staging.
- Campo â€œCÃ³digo de 6 dÃ­gitosâ€ + `.take(6)`.
- ConfirmaciÃ³n de correo: **FAIL**.
- No es defecto de migraciones SQL ni de Auth remoto.

## Causa original

- Truncado en UI: `.take(6)`.
- Backend (Supabase Auth staging) enviaba OTP de **8** dÃ­gitos.
- Textos/labels hardcodeados a â€œ6 dÃ­gitosâ€.

## Comportamiento corregido (b2189b9)

- Solo nÃºmeros.
- Longitud admitida: **6â€“10** dÃ­gitos.
- Pegar 8 dÃ­gitos **no** trunca.
- Sin verificaciÃ³n automÃ¡tica al llegar a 6.
- Confirmar habilitado solo con longitud vÃ¡lida.
- Texto genÃ©rico: â€œIngresÃ¡ el cÃ³digo que recibiste por correoâ€.
- OTP completo (tras `trim`) enviado a Supabase `verifyEmailOtp`.
- Errores se limpian al editar.
- OTP no se registra en logs/analytics/mensajes de error.

## Pruebas automÃ¡ticas

- `EmailOtpValidatorsTest`, repo, ViewModel, `AuthErrorMapperTest`.
- Suite: **â‰¥559** tests, 0 failures (verificaciÃ³n en cierre smoke).
- `assembleDebug` / `testDebugUnitTest` / `lintDebug`: **PASS**.

## ValidaciÃ³n manual con APK nuevo

| Ãtem | Resultado |
|---|---|
| APK | `apk/Leover-debug.apk` (18/07/2026 ~20:36) |
| OTP 8 dÃ­gitos recibido | PASS |
| Ingreso/pegado 8 dÃ­gitos | PASS (sin truncar) |
| ConfirmaciÃ³n correo | PASS |
| Login posterior | PASS |
| Base remota tocada por el fix | **NO** |
| Migraciones remotas | siguen **001â€“033** |

Detalle smoke: `docs/04-calidad/M07-smoke-test-apk-staging.md`.

## Resultado

**Defecto OTP cerrado.**
Migraciones / Auth Supabase / producciÃ³n: **sin cambios**.
Matriz SQL staging: **sigue pendiente de diagnÃ³stico** (no bloquea el cierre de este defecto).
