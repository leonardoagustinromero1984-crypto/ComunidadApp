# PRODUCTION SECURITY GATE — LeoVer

Documento definitivo de salida. Fecha de evidencia STAGING: 2026-09-02 (controles) / 2026-09-04 (SEC-04C/D).  
Proyecto inspeccionado: `tobqbddfcyitwgbkthhy` (Free). Destino drill: `wystsapjfpdtoprlmizz` (SEC-04 RESTORE TEST).  
Veredicto de este corte: **NOT READY FOR PRODUCTION**.  
**BLOCKER TO DEVELOPMENT: NO.** **BLOCKER TO PRODUCTION RELEASE: YES.**

Estados permitidos: PASS | FAIL | BLOCKED | ACCEPTED RISK | N/A.  
**ACCEPTED RISK** solo si Leonardo lo decide por escrito. Cursor no acepta riesgos.

Reglas de corte:

- Cualquier P0 abierto → NOT READY.  
- Restore drill no ejecutado → BLOCKED salvo aceptación explícita del propietario.  
- Backup/PITR desconocido → BLOCKED.  
- Secret crítico expuesto → NOT READY.  
- Admin sin MFA → NOT READY.  
- Authorization/RLS critical failure → NOT READY.

| ID | Categoría | Requisito | Estado | EVIDENCE | OWNER | REQUIRED BEFORE PROD? |
|---|---|---|---|---|---|---|
| AUTH-01 | AUTHENTICATION | Login PERSON no almacena password; errores genéricos | PASS | `SupabaseAuthRepository`; mock `AuthRepository` no es runtime canónico | ingeniería | sí |
| AUTH-02 | AUTHENTICATION | Logout limpia sesión remota + local | PASS | `logout()` → `signOut` + `SignOutScope.LOCAL` + unlink push | ingeniería | sí |
| AUTH-03 | AUTHENTICATION | Admin login throttle (username hash) | PASS | 1061 `admin_begin_login` | ingeniería | sí |
| AUTH-04 | AUTHENTICATION | No JWT/password en logs de producto | PASS (inspección) | mappers/allowlist; no brute force hecho | ingeniería | sí |
| AUTH-05 | AUTHENTICATION | Admin/person context separados | PASS | `platform_admin_identities`; admin ≠ PERSON | ingeniería | sí |
| AUTHZ-01 | AUTHORIZATION | Matriz staff vs PERSON | PASS | SEC-05 retest 1064 | ingeniería | sí |
| AUTHZ-02 | AUTHORIZATION | Org membership ≠ platform permissions | PASS | SEC-05 | ingeniería | sí |
| AUTHZ-03 | AUTHORIZATION | Shared responsible: update sí, archive/invite no | PASS | SEC-05 | ingeniería | sí |
| ADMIN-01 | ADMIN | MFA TOTP nativo obligatorio | PASS (STAGING) | 1062; enroll/challenge Android | Leonardo (PROD enroll) | sí |
| ADMIN-02 | ADMIN | AAL2 en operaciones administrativas | PASS | 1062/1064; `sec_final_admin_rpcs.sql` | ingeniería | sí |
| ADMIN-03 | ADMIN | Permission check además de AAL2 | PASS | `has_permission` / `staff.manage` | ingeniería | sí |
| ADMIN-04 | ADMIN | Disabled staff no opera | PASS | `disabled_at` en session/auth_state | ingeniería | sí |
| ADMIN-05 | ADMIN | Root protegido | PASS | `ROOT_PROTECTED` 1062 | ingeniería | sí |
| ADMIN-06 | ADMIN | Último SUPERADMIN protegido | PASS | `LAST_SUPERADMIN_REQUIRED` 1055/1056 (impl envuelto) | ingeniería | sí |
| ADMIN-07 | ADMIN | Technical admin no se convierte en PERSON | PASS | 1056 identities | ingeniería | sí |
| ADMIN-08 | ADMIN | RPCs admin posteriores a SEC-02 cubiertas | PASS | 1064 moderation/search; staff list AAL2 | ingeniería | sí |
| ADMIN-09 | ADMIN | `admin_clear_must_change_password` AAL1 | N/A | self-only; password-before-MFA | ingeniería | no |
| RLS-01 | RLS | Todas las tablas public con RLS | PASS | 130 tablas; rls_off=0 | ingeniería | sí |
| RLS-02 | RLS | `country_markets` RLS + least privilege | PASS | RLS ON; SELECT; DML revoked 1065 | ingeniería | sí |
| RLS-03 | RLS | Grants DML residuales SEC-05 | PASS | 1065 `sec_final_grants_after.sql` | ingeniería | sí |
| STOR-01 | STORAGE | Buckets canónicos presentes | PASS | 5 buckets live (ver matriz abajo) | ingeniería | sí |
| STOR-02 | STORAGE | MIME en bucket (`allowed_mime_types`) | PASS (4 buckets) | 1066 allowlist = register; evidence N/A actual | ingeniería | no |
| STOR-03 | STORAGE | Private read via RLS | PASS | SELECT solo `public-media`; privado via Edge | ingeniería | sí |
| MEDIA-01 | MEDIA | Camino canónico signed URL | PASS | Edge `media-signed-url` + authorize RPC | ingeniería | sí (ACL) |
| MEDIA-02 | MEDIA | Throttle no bypasseable por SDK | PASS | 1066 quita SELECT privado; consume `signed_url.request` | ingeniería | sí |
| MEDIA-03 | MEDIA | Legacy buckets Android | N/A STAGING | `profile-avatars` / `organization-media` no existen live | ingeniería | no crear sin diseño |
| ABUSE-01 | ABUSE | Kill switches video/reels/imports | PASS | `security_feature_flags` 1063 | ingeniería | sí |
| ABUSE-02 | ABUSE | Media/import fail-closed | PASS | 1063 | ingeniería | sí |
| RLIM-01 | RATE LIMITS | Policies STAGING aplicadas | PASS | 1063 + 1067 `signed_url.request` 60/600 fail-closed | ingeniería | sí (STAGING) |
| RLIM-02 | RATE LIMITS | Limits Production iniciales | BLOCKED | propuesta en `PRODUCTION-INITIAL-LIMITS.md`; no aplicadas a PROD | Leonardo | sí en PROD |
| SEC-01 | SECRETS | service_role no en Android/Git tracked source | PASS | Gradle throw; contract tests; no valores en docs | ingeniería | sí |
| SEC-02 | SECRETS | OAuth/Firebase server secrets no en repo | PASS | client `google-services.json` tracked (no private_key) | Leonardo (restricción consola) | sí (server) |
| EDGE-01 | EDGE | `admin-staff` AUTH+AAL2+permission+service_role server | PASS | función v5 `verify_jwt` | ingeniería | sí |
| EDGE-02 | EDGE | `vitacora-import-analyze` JWT+flag+límites; sin service_role | PASS | v2 | ingeniería | sí |
| EDGE-03 | EDGE | Funciones no desplegadas | N/A | `push`, `delete-account`, `klipy-proxy` = NOT IN CURRENT ATTACK SURFACE | ingeniería | review antes de deploy |
| EDGE-04 | EDGE | `media-signed-url` JWT + ACL + throttle + service_role server | PASS | deployed STAGING; verify_jwt; no log URL | ingeniería | sí |
| BAK-01 | BACKUPS | Automatic / scheduled backups | FAIL on STAGING Free; **REQUIRED on PROD Pro** | Leonardo: Free no incluye project backups | Leonardo | **sí** |
| BAK-02 | BACKUPS | PITR | **DEFERRED BY OWNER** (lanzamiento inicial) | Leonardo 2026-09-04; ~USD 100/month add-on; reevaluar por crecimiento | Leonardo | no al launch; sí reevaluar |
| BAK-03 | BACKUPS | Retención documentada | PENDING PROD | no inventar; verificar al crear PROD | Leonardo | **sí** |
| RST-01 | RESTORE | Procedimiento escrito (4 capas) | PASS | `SEC-04-restore-runbook.md` | ingeniería | sí |
| RST-02 | RESTORE | Restore drill aislado | PRODUCTION-CONDITIONAL | SEC-04C `public`; SEC-04D Storage bytes; Auth = physical Pro | ingeniería | **sí** (Auth físico en PROD) |
| RST-03 | RESTORE | DB vs Storage bytes distinguidos | PASS | dump ≠ bytes; scripts LAYER 2 | ingeniería | sí |
| RST-04 | RESTORE | Storage backup + restore sample | PASS | 12 objetos SHA-256; 2 restaurados a `sec04d-probe/` | ingeniería | sí (job diario en PROD) |
| RST-05 | RESTORE | Auth physical / restore-to-new-project | PENDING PROD | Free STAGING no lo ofrece | Leonardo | **sí** |
| OBS-01 | OBSERVABILITY | Eventos rate/quota/admin sanitizados | PASS | 1063 events; allowlist | ingeniería | sí |
| OBS-02 | OBSERVABILITY | Dashboards/alerts Production | BLOCKED | no configurados | Leonardo | sí para operate |
| COST-01 | COST | Spend/billing alerts | BLOCKED | Dashboard | Leonardo | **sí** (presupuesto bajo) |
| COST-02 | COST | Feed/messages sin cursor | PASS | 1066 limit + cursor `(created_at, id)` | ingeniería | no |
| IR-01 | INCIDENT RESPONSE | Runbook operable | PASS | `INCIDENT-RESPONSE.md` | Leonardo | sí |
| DEP-01 | DEPENDENCIES | No mass upgrade este bloque | N/A | no se actualizaron libs | ingeniería | no |
| PRIV-01 | PRIVACY/DATA | Media privada / evidence / imports no públicos | PASS | buckets private + RLS | ingeniería | sí |
| PRIV-02 | PRIVACY/DATA | Logs sin bodies médicos/chat | PASS (diseño) | eventos sanitizados | ingeniería | sí |
| REL-01 | RELEASE | Deploy order documentado | PASS | `PRODUCTION-SECURITY-DEPLOYMENT.md` | ingeniería | sí |
| REL-02 | RELEASE | Proyecto Production existente | FAIL | no creado | Leonardo | **sí** |

## Matriz Storage (STAGING live)

| bucket | PUBLIC/PRIVATE | MAX SIZE | MIME policy | READ | WRITE | OWNER | SIGNED URL | BACKUP |
|---|---|---|---|---|---|---|---|---|
| public-media | PUBLIC | 50 MiB | images+video 1066 | storage select + public | auth insert ownership | path/RPC | n/a público | metadata en DB; bytes separados |
| private-media | PRIVATE | 50 MiB | images+video 1066 | no client SELECT | auth insert ownership | `_acl_media_readable` | Edge `media-signed-url` | bytes separados |
| documents | PRIVATE | 50 MiB | pdf/xlsx/xls/csv/octet 1066 | no client SELECT | auth insert | ACL | Edge `media-signed-url` | bytes separados |
| moderation-evidence | PRIVATE | 50 MiB | null (N/A current upload; FUTURE DEPLOYMENT REQUIREMENT) | no client SELECT | no `canon_storage_insert` | staff | no mint masivo cliente | bytes separados |
| vitacora-import | PRIVATE | 5 MiB | xlsx/xls/csv/octet 1066 | owner policies | `vitacora_import_storage_*` | job owner | n/a | bytes separados |

## Matriz Edge (attack surface actual)

| función | AUTH | AAL | PERMISSION | SERVICE_ROLE | INPUT LIMIT | RATE LIMIT | LOG SAFE | SECRET |
|---|---|---|---|---|---|---|---|---|
| admin-staff | JWT required | AAL2 via RPC | `staff.manage` | sí, solo server | body action allowlist | 1063 admin.* | no password/JWT | URL/anon/service_role |
| media-signed-url | JWT required | n/a (PERSON) | `_acl_media_readable` via authorize RPC | sí, solo server | UUID asset_id; TTL ≤ 600 | `signed_url.request` fail-closed | no JWT/URL | URL/anon/service_role |
| vitacora-import-analyze | JWT required | n/a (holder) | import get + flag | no | 5 MB / 500 rows / 12 sheets | imports.* + flag | no file dump | URL + caller JWT |
| push | NOT IN CURRENT ATTACK SURFACE | — | — | — | — | — | — | review antes de deploy |
| delete-account | NOT IN CURRENT ATTACK SURFACE | — | — | — | — | — | — | review antes de deploy |
| klipy-proxy | NOT IN CURRENT ATTACK SURFACE | — | — | — | — | — | — | review antes de deploy |

## Signed URL — callers Android

| Caller | Mecanismo | Bucket | Notas |
|---|---|---|---|
| `CanonicalMediaRepositories.requestSignedUrl` | Edge `media-signed-url` (authorize + mint) | private canonical | camino de producción |
| `ProfileAvatarStorageService` | SDK only | `profile-avatars` | bucket **no** existe en STAGING |
| `OrganizationMediaStorageService` / `OrganizationViewModels` | SDK only | `organization-media` | bucket **no** existe en STAGING |
| `SupabaseFileDownloadRepository` | RPC `request_file_signed_url` (legado M05) + SDK | file assets | RPC **no** está en canónico |
| `ProfileAvatarResolver` | via services arriba | mix | residual |

Camino canónico: un solo mint server-side (Edge + service_role) después de `canon_authorize_media_signed_url`.  
SELECT de Storage en buckets privados removido en 1066: el SDK del cliente no puede firmar objetos privados.

## Paginación

`canon_list_social_feed` / `canon_list_messages` (1066): límite server-side + cursor estable `(created_at, id)`.  
0-arg feed = primera página (30). 1-arg messages = recientes (50). Historial accesible con la página siguiente/anterior. Sin OFFSET.

## Observability al salir a producción

Detectar: fallos/throttle `admin_begin_login`; `RATE_LIMIT_EXCEEDED`; `UPLOAD_QUOTA_EXCEEDED`; `SIGNED_URL_THROTTLED`; `IMPORT_THROTTLED`; `FEATURE_TEMPORARILY_DISABLED`; acciones staff (`staff_set_*`, roles); 5xx Edge; spend.  
No loguear: passwords, JWT, service_role, bodies de mensajes, datos médicos innecesarios.

## Veredicto

**NOT READY FOR PRODUCTION**

BLOCKER TO DEVELOPMENT: **NO** (QA/funcional puede continuar).  
BLOCKER TO PRODUCTION RELEASE: **YES**.

SEC-04 = **PRODUCTION-CONDITIONAL** (no PASS de release).

Blockers de **release** (no de desarrollo):

1. Crear proyecto **PROD** distinto de STAGING y del restore test.  
2. PROD en **Pro** (decisión Leonardo). Daily backup **ON**.  
3. Restore-to-new-project disponible y **Auth/public linkage** verificado en ese restore.  
4. Retención de backup PROD anotada (no inventada).  
5. Job de Storage bytes activo en PROD (script ya ensayado).  
6. Root SUPERADMIN + MFA en PROD.  
7. Spend alerts + owner/contacto de recovery.  
8. PITR: **no** es blocker de launch (deferred by owner); sigue en el risk register.

Acción Storage: programar `backup-storage.ps1` diario (u equivalente) hacia destino privado fuera de Git. Ensayo ya hecho; falta el job en PROD.

Acción Auth: **no** `psql` COPY a `auth.users`. Plan canónico: physical daily backup Pro + restore to new project. Fallback: re-auth / MFA re-enroll; sin backdoors.

P0 de autorización/RLS/MFA/secrets en STAGING: ninguno abierto.  
P1 signed-URL / feed cursor / messages cursor / MIME (4 buckets): **FIXED** (1066 + 1067).  
`moderation-evidence` MIME: **NOT APPLICABLE TO CURRENT UPLOAD SURFACE / FUTURE DEPLOYMENT REQUIREMENT** (no OPEN RISK).
