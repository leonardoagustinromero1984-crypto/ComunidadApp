# M05 — Cierre final: Archivos, Media y Documentos

**Fecha:** 2026-07-16  
**Módulo:** M05  
**Producto:** LeoVer  
**Estado código:** **CERRADO** (implementación + calidad local + corrección `025`)  
**Estado release:** **BLOQUEADO** hasta validación staging documentada  
**Rama de cierre:** `m05/etapa-5-validacion-cierre`  
**Commit base Etapa 4:** `0e57c1c3f1482235e10a78565aad0daf4bc3c05c`

---

## 1. Resumen ejecutivo

M05 entregó la base de archivos de LeoVer: contratos tipados (purpose, ownership, visibilidad, paths), persistencia Supabase (`file_*`), buckets tipados, hardening del bucket legacy `leover` a solo lectura para escrituras nuevas, RPC SECURITY DEFINER, repositorios Android, coordinador de upload con progreso/cancelación/reintento/reemplazo seguro, resolución pública y firmada temporal en memoria, integración gradual en perfil/org/mascotas/publicaciones/moderación/soporte, y limpieza sensible al logout.

La validación remota de migraciones **014–025** permanece **PENDIENTE DE VALIDACIÓN REMOTA**.

---

## 2. Capacidades entregadas

### Assets y versiones

- `FileAsset` / `FileAssetVersion` / links / upload sessions / audit / retención.
- Sin binarios ni base64 en modelos; signed URL no es parte del asset.

### Ownership

- Único: USER | ORGANIZATION | PLATFORM (XOR).  
- Roles M03 solo dentro de su organización.  
- AccountType / `active_modules` sin autoridad.

### Propósitos y visibilidad

- Catálogo central en `FilePurposePolicy`.  
- Sensibles (`MODERATION_EVIDENCE`, `ORGANIZATION_VERIFICATION_DOCUMENT`, `SUPPORT_ATTACHMENT`) nunca PUBLIC.

### Validación

- Nombre, extensión, MIME, tamaño, cantidad, purpose.  
- Servidor revalida; Android no es la única barrera.

### Rutas y buckets

- Paths tipados con `assetId` generados server-side.  
- Físicos: `profile-avatars`, `organization-media`, `public-media`, `organization-documents`, `moderation-evidence`, `support-attachments`.  
- `LEGACY_LEOVER_READ_ONLY` no acepta upload M05.

### Hardening `leover`

- Lectura pública legacy conservada.  
- INSERT/UPDATE/DELETE autenticados eliminados (`024`).  
- Sin mover/borrar objetos; URLs existentes intactas.

### RLS / RPC

- Tablas deny-by-default en escritura; mutación vía RPC.  
- `SECURITY DEFINER`, `search_path = public`, actor `auth.uid()`.  
- Corrección `025`: policies session/asset-gated para paths M05 en `profile-avatars` y `organization-media` (legacy 017/019 intactas).

### Signed URLs

- Temporales; no persistidas en SQL ni en el asset; TTL tipado; limpieza al logout.

### Upload y reemplazo

- Sesión → bytes → progreso → complete.  
- Cancelación idempotente; retry; bloqueo doble envío.  
- `safeReplace`: nuevo READY antes de unlink/delete del anterior.  
- Sin `leover` para uploads nuevos; sin `content://` final.

### Retención y eliminación

- Unlink ≠ delete físico.  
- Legal hold / links activos / retención bloquean physical delete.

### Adjuntos sensibles

- Evidencia y soporte en buckets privados.  
- INTERNAL no expuesto al solicitante.  
- Deep links sensibles revalidan permisos.

### Compatibilidad legacy

- Lectura de URLs/paths legacy; sin ownership ni upload.  
- Sin migración masiva de columnas `*_url`.

### UI y logout

- Photo Picker / OpenDocument; progreso; errores seguros.  
- Integración gradual perfil, onboarding, org, mascotas, publish, moderación, soporte.  
- `FileSessionCleanup` en logout.

---

## 3. Calidad local

| Control | Resultado |
|---------|-----------|
| Suite unitaria | **358** tests, **0** failures, **0** errors |
| `assembleDebug` | SUCCESS |
| `lintDebug` | SUCCESS |
| Migración correctiva | `025` (defecto paths/RLS profile-org) |
| Auth / username | Intactos (fuera de alcance M05) |

---

## 4. Staging y release

| Ítem | Estado |
|------|--------|
| Staging `014`–`025` | **PENDIENTE DE VALIDACIÓN REMOTA** |
| Producción | No usada |
| Release | **BLOQUEADO** hasta staging PASS con evidencia |

Detalle: `docs/04-calidad/M05-reporte-validacion-staging.md`.

---

## 5. Deuda aceptada

- Validación remota pendiente.  
- `RESOURCE_PARTICIPANTS` SQL incompleto.  
- Complete upload sin verificación física del objeto.  
- Galería multi-imagen UI parcial.  
- Migración gradual `*_url` → assetId.  
- Sin antivirus / EXIF strip / thumbnails / sniffing profundo (no afirmados).  
- Error de verificación de usuario / username: tarea separada, **no** en M05.

---

## 6. Condiciones de release

1. Checklist staging `014`–`025` ejecutado con evidencia.  
2. Casos críticos `leover`, buckets tipados, avatar/org media M05 (`025`), sensibles e INTERNAL en PASS.  
3. Sin regresiones M01–M04 documentadas.  
4. Calidad local en verde (o revalidada tras cambios).

Hasta entonces: **no release**.

---

## 7. M06 y merge

- M06: **no iniciado**.  
- Merge a `main`: **no realizado** en este cierre.  
- WIP GPS/mapas/pagos: **no incorporado**.

---

## 8. Referencias de etapa

| Etapa | Rama / commit de consolidación (cuando exista) |
|-------|--------------------------------------------------|
| 1 Auditoría | `39aa369d8e5e45f41cebe6b769f6994d12a255e4` |
| 2 Contratos | `71210f788839e09f5eff99b440059152039c035c` |
| 3 Persistencia | `6b8ee5d2daefc3ad4ed2b07a3af1a4e26391943b` |
| 4 UI flujos | `0e57c1c3f1482235e10a78565aad0daf4bc3c05c` |
| 5 Validación | rama `m05/etapa-5-validacion-cierre` (pendiente consolidar) |
