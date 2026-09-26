# SEC-05 — Verificación defensiva pre-producción

STAGING `tobqbddfcyitwgbkthhy`. Fecha de evidencia: 2026-09-02.  
Repo vs remoto: 65 migraciones, max `20260902210000` (1064). 1057–1063 no editadas.

Edge desplegadas (inventario real): `admin-staff` (v5, verify_jwt), `vitacora-import-analyze` (v2, verify_jwt). No `push` / `delete-account` / `klipy-proxy`.

## Hallazgos

### P0

Ninguno tras 1064. El único fallo vivo de la primera pasada (`list_moderation_queue` en AAL1) se cerró.

### P1

**Finding:** Storage SDK puede mintar signed URL si RLS SELECT lo permite, sin `canon_authorize_media_signed_url`.  
**Boundary:** media privada / documentos.  
**Expected:** toda emisión pasa por authorize + throttle.  
**Actual:** Android canónico llama la RPC en `CanonicalMediaRepositories`; también existen `ProfileAvatarStorageService`, `OrganizationMediaStorageService`, `SupabaseFileDownloadRepository` y `OrganizationViewModels` que llaman `createSignedUrl` directo. curl + JWT con SELECT RLS bypasea el rate limit, no la ACL de Storage.  
**Risk:** abuso de cuota/ancho de banda, no lectura de objetos ajenos si RLS es correcta.  
**Fix:** Edge service_role dedicada + quitar mint del cliente. Rediseño de media.  
**Verification:** inspección de repo. **No implementado** (rediseño).

**Finding:** `canon_list_social_feed` / `canon_list_messages` siguen siendo jsonb sin cursor.  
**Boundary:** lectura social/chat.  
**Expected:** hard cap / cursor.  
**Actual:** sin cap nuevo (congelado en SEC-03 para no esconder historial).  
**Risk:** costo de lectura, no escalada.  
**Fix:** paginación en bloque futuro.  
**Verification:** código 1053 / 1035.

### P2

**Finding:** varias tablas tenían GRANT DML residual a `anon`/`authenticated` (`country_markets`, `vitacora_import_*`, snapshots, etc.) con RLS ON y 0 políticas INSERT.  
**Boundary:** PostgREST table API.  
**Expected:** revoke de writes residuales.  
**Actual (SEC-FINAL):** migration **1065** revocó INSERT/UPDATE/DELETE; SELECT se mantiene. `country_markets` RLS ON + SELECT policy + sin write policy.  
**Status:** FIXED.  
**Verification:** `tests/sec_final_grants_after.sql` (auth_ins=false, auth_sel=true).

### INFO

- Solo dos PERSON no-admin disponibles; tests de extraño (mensajes / listado de mascotas ajenas) se documentan por contrato RPC, no por tercer fixture.
- `_acl_is_admin` cubre ADMIN/SUPERADMIN y exige `p_user_id = auth.uid()` cuando hay JWT.
- SUPPORT no tiene `moderation.*` ni `staff.manage`. MODERATOR no tiene `staff.manage`. ADMIN no tiene `staff.manage` (solo SUPERADMIN).
- `moderation-evidence` no está en `canon_storage_insert`; insert va por otro camino / no client write.
- Health declarado no se tocó.

## Fixes aplicados

Migration **1064** `20260902210000_1064_sec05_authz_gaps.sql`:

- AAL2 en `list_moderation_queue`, `get_moderation_report_for_staff`, `triage_content_report`, `admin_search_users`, `admin_get_user_roles`, `admin_get_user_status_history`.
- Actor en `canon_revoke_vitacora` (`vitacora.share`), `canon_hide_integration` (`vitacora.manage`).
- `canon_create_proposal` exige holder o grant ESSENTIAL/HEALTH.
- `canon_record_vet_care` exige grant HEALTH además de perfil profesional.

Retest: AAL1 moderation/search/session = `MFA_REQUIRED`; responsable no revoca grant; grantee con ESSENTIAL puede proponer.

## Edge

`admin-staff`: Bearer → getUser → `get_admin_auth_state` → AAL2 → `get_admin_session` → `staff.manage` → service_role.  
`vitacora-import-analyze`: JWT caller, flag `imports.enabled`, sin service_role, límites 5 MB / 500 filas.

## Rate-limit paths

Bindings 1063 + consume inline en `canon_create_social_post` / `canon_register_media`. No hay RPC legacy de create post/reel/story ejecutable fuera del wrapper. INSERT de tablas sociales revocado.
