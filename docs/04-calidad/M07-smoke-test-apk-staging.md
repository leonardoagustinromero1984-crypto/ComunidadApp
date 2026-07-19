# M07 — Smoke test APK staging

**Fecha:** 2026-07-19
**Producto:** LeoVer
**Entorno:** staging / pruebas (ref …`mizz`)
**Rama:** `m07/cierre-smoke-apk-otp` (docs) · fix OTP `m07/fix-email-otp-length` @ `b2189b9`
**APK:** `C:\Users\Supervielle\StudioProjects\ComunidadApp\apk\Leover-debug.apk`
**Fecha APK:** 2026-07-18 ~20:36
**Tamaño APK:** ~27 144 530 bytes
**APK en Git:** **NO** (binario; solo documentado por ruta/fecha)

```text
SMOKE TEST APK STAGING PASS
```

No se registran correos, OTP, contraseñas ni datos personales del usuario de prueba.

---

## Matriz de resultados

| Caso | Resultado |
|---|---|
| Aplicación inicia | PASS |
| Registro (usuario nuevo) | PASS |
| Recepción OTP (8 dígitos desde Supabase) | PASS |
| Ingreso / pegado de 8 dígitos | PASS (sin truncar) |
| Confirmación de correo | PASS |
| Login posterior | PASS |
| Cierre de sesión (logout) | PASS |
| Username válido | PASS |
| Username único | PASS |
| Username duplicado rechazado | PASS |
| Completar perfil | PASS |
| Persistencia del perfil | PASS |
| Reapertura de app (perfil persistente) | PASS |
| Crashes bloqueantes | NINGUNO |
| Errores / SQL internos visibles | NINGUNO |
| **Resultado general** | **SMOKE TEST APK STAGING PASS** |

---

## Notas

- Corrección OTP: commit `b2189b9` — admite 6–10 dígitos; OTP completo a Supabase.
- Base remota **no** modificada por esta corrección ni por el smoke.
- Migraciones remotas: **001–033** (sin 034).
- Producción: no existe / no tocada.
- M08: no iniciado · `main`: no modificado.
- Matriz SQL staging: **FAIL parcial — 3 resultados pendientes de diagnóstico** (fuera del alcance de este smoke).
- Release: **BLOQUEADO** hasta cierre staging completo (matriz SQL incluida).
