# M05 — Cierre Etapa 5: Validación, staging, calidad y cierre final

**Fecha:** 2026-07-16  
**Rama:** `m05/etapa-5-validacion-cierre`  
**Módulo:** M05 — Archivos, Media y Documentos  
**Producto:** LeoVer  
**Estado de entrada:** Etapa 4 consolidada (`0e57c1c3f1482235e10a78565aad0daf4bc3c05c`)  
**Spec:** `docs/03-modulos/M05-Etapa-5-Validacion-Staging-Calidad-y-Cierre-Final.md`

---

## 1. Git

| Ref | Valor |
|-----|-------|
| Commit base | `0e57c1c3f1482235e10a78565aad0daf4bc3c05c` |
| Rama | `m05/etapa-5-validacion-cierre` |
| Working tree inicial | Limpio salvo spec Etapa 5 untracked |
| Merge a `main` | **No** |
| M06 | **No** iniciado |
| WIP GPS/mapas/pagos | **No** incorporado |
| Producción | **No** usada |
| Username / AuthRepository / `domain/auth` / validadores | **Sin cambios** |
| Migraciones `001`–`024` | **Sin ediciones** |

---

## 2. Auditoría integral (resumen)

### Confirmado OK (estático)

- `024`: `leover` lectura pública; INSERT/UPDATE/DELETE autenticados eliminados; objetos/URLs legacy no tocados.
- Buckets tipados: `public-media`, `organization-documents`, `moderation-evidence`, `support-attachments`; `profile-avatars` / `organization-media` no recreados.
- Tablas `file_*` con ownership XOR, sensibles ≠ PUBLIC, índices y RLS write deny-by-default.
- RPCs `SECURITY DEFINER`, `search_path = public`, actor vía `m05_require_active_actor()` / `auth.uid()`.
- Paths server-side (`m05_build_storage_path`); path cliente divergente → denegado.
- `request_file_signed_url` no persiste ni devuelve token; solo bucket/path/TTL.
- `resolve_public_file_asset` solo PUBLIC + READY.
- Android: uploader rechaza `leover`; coordinator con progreso/cancel/retry/doble submit/`safeReplace`; signed URLs solo memoria; logout limpia vía `FileSessionCleanup`.
- INTERNAL: requester filtra mensajes INTERNAL; adjuntos admin INTERNAL con `AUTHORIZED_STAFF` + `support.view_sensitive`.
- AccountType / `active_modules` sin autoridad; M03 scoped a org; errores seguros (`FileUiErrorMapper`); sin AppLogger en path de upload M05.
- Regresión M01–M04: auth/username intactos; no se mezcló WIP GPS.

### Defectos reales encontrados

| ID | Severidad | Defecto | Corrección |
|----|-----------|---------|------------|
| D1 | **Bloqueante** | Paths M05 en `profile-avatars` / `organization-media` (`…/avatars|covers/{assetId}/…`, `…/logo|cover/{assetId}/…`) no pasan RLS legacy `017`/`019` (`…/avatar/…`, `…/logo|cover/{file}`). Sesión SQL OK + `storage.upload` denegado. | Migración **`025`** (policies adicionales session/asset-gated; legacy intacto) + prueba de regresión |

### Deuda aceptada (no bloqueante de código)

- `RESOURCE_PARTICIPANTS` no modelado en `m05_can_read_asset` SQL (INTERNAL usa `AUTHORIZED_STAFF`; UI requester no lista adjuntos internos).
- `complete_file_upload` no verifica existencia física del objeto en Storage.
- Validación remota `014`–`025` pendiente.
- Galería multi-imagen de mascotas: integración UI parcial (Etapa 4).
- Migración gradual `*_url` → assetId: continua.

---

## 3. Migración correctiva

| Archivo | Motivo |
|---------|--------|
| `supabase/migrations/025_m05_profile_org_media_storage_rls_fix.sql` | Defecto D1 de `024`; alcance mínimo; **no** edita `024`; **no** toca `leover` |

Prueba: `M05Migration025ProfileOrgStoragePathRegressionTest`.

---

## 4. Archivos tocados (Etapa 5)

**Creados:**

- `supabase/migrations/025_m05_profile_org_media_storage_rls_fix.sql`
- `app/src/test/.../M05Migration025ProfileOrgStoragePathRegressionTest.kt`
- `docs/02-arquitectura/M05-etapa-5-cierre.md` (este)
- `docs/02-arquitectura/M05-cierre-final.md`
- `docs/04-calidad/M05-reporte-validacion-staging.md`
- Spec de entrada: `docs/03-modulos/M05-Etapa-5-Validacion-Staging-Calidad-y-Cierre-Final.md`

**Modificados:** ninguno en Kotlin de producto / auth / migraciones `001`–`024`.

**Eliminados:** ninguno.

---

## 5. Calidad local

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **358** tests, **0** failures, **0** errors (353 Etapa 4 + 5 regresión `025`) |
| `lintDebug` | **SUCCESS** |

Comandos:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

---

## 6. Seguridad (confirmaciones Etapa 5)

| Regla | Estado |
|-------|--------|
| `leover` lectura legacy | Conservada |
| INSERT/UPDATE/DELETE `leover` | Denegados (policies drop en `024`) |
| Objetos legacy no movidos/borrados | Cumplido |
| `public-media` resolve solo PUBLIC/READY | Cumplido (RPC) |
| Buckets sensibles no públicos | Cumplido |
| Org A ≠ archivos org B | Cumplido (auth dominio + SQL) |
| Requester ≠ adjuntos INTERNAL | Cumplido |
| Signed URLs / `content://` no persistidos | Cumplido |
| Reemplazo seguro (viejo tras READY nuevo) | Cumplido |
| Logout limpia URI/sesiones/previews/firmadas | Cumplido |
| AccountType / modules sin autoridad | Cumplido |
| M03 solo su org | Cumplido |
| Android no elige bucket/path sensible | Cumplido |
| Sin service role / base64 en cliente | Cumplido |
| Sin antivirus/EXIF/thumbnails afirmados | Cumplido |

---

## 7. Staging

**PENDIENTE DE VALIDACIÓN REMOTA** para migraciones `014`–`025`.  
Sin acceso autorizado ejecutado en esta etapa; sin evidencia de deploy.  
Ver `docs/04-calidad/M05-reporte-validacion-staging.md`.

**Release:** bloqueado hasta staging PASS documentado.

---

## 8. Checklist

- [x] Commit base verificado  
- [x] Rama `m05/etapa-5-validacion-cierre`  
- [x] `024` auditada; `001`–`024` sin ediciones  
- [x] Defecto bloqueante corregido vía `025`  
- [x] `leover` read-only para escrituras nuevas  
- [x] Build / tests / lint  
- [x] Staging marcado honestamente pendiente  
- [x] Username/auth intactos  
- [x] Sin M06 / sin merge a `main`  
- [x] Tres docs de salida  

---

## 9. Parada

Etapa 5 lista para revisión. **Sin commit** en este informe.  
**No** iniciar M06. **No** merge a `main`. **No** corregir username/auth en esta rama.
