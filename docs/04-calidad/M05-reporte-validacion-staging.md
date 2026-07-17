# M05 — Reporte de validación staging

**Módulo:** M05 — Archivos, Media y Documentos  
**Producto:** LeoVer  
**Fecha del informe:** 2026-07-16  
**Rama:** `m05/etapa-5-validacion-cierre`  
**Commit base Etapa 4:** `0e57c1c3f1482235e10a78565aad0daf4bc3c05c`  
**Entorno remoto:** **sin acceso autorizado ejecutado en esta etapa**  
**Estado global:** **PENDIENTE DE VALIDACIÓN REMOTA**

Migraciones en alcance: `014` → `015` → `016` → `017` → `018` → `019` → `020` → `021` → `022` → `023` → `024` → `025`.

No se aplicaron migraciones en staging ni producción.  
No se afirma deploy ni checklist remoto PASS.

---

## 1. Casos remotos

Formato: caso · entorno · fecha · actor técnico · resultado · evidencia · observaciones.

| Caso | Entorno | Fecha | Actor | Resultado | Evidencia | Observaciones |
|------|---------|-------|-------|-----------|-----------|---------------|
| Aplicar/verificar historial `014`–`023` | Staging | — | — | **NO EJECUTADO** | Sin acceso | Heredado pendiente desde M04 |
| Aplicar/verificar `024` foundation M05 | Staging | — | — | **NO EJECUTADO** | Sin acceso | Buckets, tablas, RLS, RPC |
| Aplicar/verificar `025` RLS profile/org media | Staging | — | — | **NO EJECUTADO** | Sin acceso | Fix paths M05 vs legacy 017/019 |
| `leover` SELECT público legacy | Staging | — | — | **NO EJECUTADO** | Sin acceso | No mover/borrar objetos |
| `leover` INSERT/UPDATE/DELETE authenticated denegados | Staging | — | — | **NO EJECUTADO** | Sin acceso | Hardening `024` |
| Upload M05 a `leover` denegado | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| `public-media` resolve solo PUBLIC/READY | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| Upload avatar/cover vía `profile-avatars` path M05 | Staging | — | — | **NO EJECUTADO** | Sin acceso | Requiere `025` |
| Upload logo/cover vía `organization-media` path M05 | Staging | — | — | **NO EJECUTADO** | Sin acceso | Requiere `025` |
| Buckets sensibles privados (docs/evidencia/soporte) | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| Org A no lee archivos org B | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| Staff sin permiso no lee evidencia | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| Requester no ve adjuntos INTERNAL | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| Signed URL no persistida en SQL | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| Path arbitrario / bucket cliente denegados | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| Legal hold / retención bloquean physical delete | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| Sesión expirada / doble submit | Staging | — | — | **NO EJECUTADO** | Sin acceso | |
| Regresión avatar legacy path `users/.../avatar/` | Staging | — | — | **NO EJECUTADO** | Sin acceso | Policies 017 deben seguir vigentes |
| Regresión org media legacy un segmento | Staging | — | — | **NO EJECUTADO** | Sin acceso | Policies 019 deben seguir vigentes |

---

## 2. Evidencia local (no sustituye staging)

| Control | Resultado |
|---------|-----------|
| Auditoría estática `024`/`025` | Documentada en `M05-etapa-5-cierre.md` |
| Suite unitaria | **358** / 0 / 0 |
| `assembleDebug` / `lintDebug` | SUCCESS |
| Regresión paths `025` | `M05Migration025ProfileOrgStoragePathRegressionTest` |

---

## 3. Condición de release

Release de M05 permanece **BLOQUEADO** hasta que el checklist remoto anterior esté ejecutado en un entorno autorizado con evidencia (logs/migración aplicada/casos PASS) y se actualice este documento.

Texto obligatorio mientras no haya evidencia:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```
