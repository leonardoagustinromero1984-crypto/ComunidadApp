# SEC-04 — Runbook de backup y restauración (LeoVer)

Entorno canónico de STAGING: `tobqbddfcyitwgbkthhy`.  
**Nunca restaurar encima de STAGING actual. Nunca tocar PRODUCCIÓN.**  
**No commitear dumps. No subir dumps a buckets públicos. No imprimir PII.**

## Qué cubre qué

| Superficie | Cubierto por | Notas |
|---|---|---|
| Schema `public` + datos | dump lógico + `psql` (SEC-04C) | PostGIS `extensions` obligatorio si se droppea `public` |
| `auth` (users, identities, MFA) | dump **incluye** filas; COPY a Auth hosted **falla** | Plan de desastre = backup/PITR nativo o proyecto nuevo |
| `storage` metadata | `storage.objects` / buckets | En proyecto existente los buckets históricos no se reemplazan |
| Objetos Storage | backup de buckets **aparte del SQL** | SEC-04C: **NOT COVERED BY DB BACKUP** |
| Edge Functions | Código en repo `infra/supabase-canonical/supabase/functions/` | Deploy state es independiente |
| Secrets | Supabase secrets / gestores; **nombres** abajo | Valores nunca en Git |
| Migraciones | Repo + `supabase_migrations.schema_migrations` | No editar 1057–1062 ni aplicadas |
| Git | Este repositorio | Working tree ≠ backup de datos |

## Capacidad de backup — evidencia Dashboard (Leonardo, 2026-09-04)

SQL/CLI **no** exponen plan ni retención. Los valores de STAGING abajo son evidencia **manual del propietario**, no inferencia de Cursor.

### STAGING `tobqbddfcyitwgbkthhy` (Free)

| Dato | Valor verificado | Estado |
|---|---|---|
| Plan | Free | verificado por Leonardo |
| Scheduled / automatic backups | Free Plan does not include project backups | **NOT AVAILABLE ON CURRENT FREE PLAN** |
| PITR | add-on de Pro; no habilitado | **NOT ENABLED** |
| Restore to new project | requiere Pro + physical backups | **NOT AVAILABLE ON CURRENT FREE PLAN** |
| Retención | no aplica (no hay backup programado) | N/A en Free |

### PRODUCCIÓN (aún no creada)

| Dato | Requisito de lanzamiento | Estado |
|---|---|---|
| Plan | **Supabase Pro** (decisión expresa de Leonardo) | REQUIRED BEFORE LAUNCH |
| Scheduled backups | Daily ON | REQUIRED BEFORE LAUNCH |
| PITR | **no obligatorio** en el lanzamiento inicial (costo; Dashboard ~USD 100/month add-on) | **DEFERRED BY OWNER FOR INITIAL LAUNCH** |
| Restore to new project | debe existir y ensayarse en PROD Pro | MUST BE VERIFIED ON PROD/PRO BEFORE LAUNCH |
| Retención | anotar días reales al crear PROD | **no inventar**; verificar en Dashboard |
| RPO inicial aceptado | ≤ ~24 h según el backup diario real | decisión expresa de Leonardo |

PITR permanece en el risk register como mejora futura. Reevaluar cuando suban usuarios, volumen, criticidad, ingresos o costo de pérdida.

Un dump lógico **no** sustituye el backup físico de Pro. El drill SEC-04C **no** reemplaza la verificación en el futuro proyecto PROD.

## Destino de drill

Orden obligatorio:

1. Proyecto QA/restoration **ya existente y autorizado**.
2. Postgres aislado ya provisionado.
3. Otro entorno explícitamente seguro ya existente.

**No crear proyecto pago nuevo** sin autorización. **Nunca restaurar encima de STAGING** `tobqbddfcyitwgbkthhy`.

Destino autorizado (2026-09-04, SEC-04C): `wystsapjfpdtoprlmizz`, designado **SEC-04 RESTORE TEST**.  
No es STAGING. No es runtime Android. No volver a tratarlo como entorno de la app.

**SEC-04 = PRODUCTION-CONDITIONAL** (SEC-04D).  
Evidencia: `SEC-04-restore-drill-result.md`.  
`public` ensayado. Storage bytes ensayados (backup+muestra). Auth usable = backup físico Pro / restore-to-new-project, **pendiente de activar en PROD**.

## Procedimiento exacto

PROHIBIDO: restaurar encima de STAGING `tobqbddfcyitwgbkthhy`.  
PROHIBIDO: apuntar Android a `wystsapjfpdtoprlmizz`.  
PROHIBIDO: crear proyecto pago sin autorización.

Lecciones del drill SEC-04C (destino `wystsapjfpdtoprlmizz`):

1. Confirmar SOURCE=`tobqbddfcyitwgbkthhy` y TARGET≠STAGING. Anotar URL Dashboard.  
2. Dump lógico de STAGING a disco privado (no Git): schema `public`, `auth`, `storage` metadata, `supabase_migrations`.  
3. Restore **solo** al destino aislado. `SET ROLE postgres`. Si se hace `DROP SCHEMA public CASCADE`, ejecutar `CREATE EXTENSION postgis WITH SCHEMA extensions` **antes** del schema dump (si no, fallan tablas `geography`).  
4. Verificar: RLS (`relrowsecurity`), grants 1065, RPCs AAL2, `_canon_consume_rate_limit`, `security_feature_flags`, roles/permissions, sequence `vitacora_public_number_seq` vs `max(public_vitacora_number)`, species/catalogs.  
5. Auth: el dump incluye `auth.users` / identities / MFA, pero COPY hacia Auth hosted **falla** (ownership / unique email). No declarar Auth PASS sin `persons` y `platform_admin_identities` enlazados a `auth.users`. MFA secrets no se exportan a tickets.  
6. Storage: metadata ≠ bytes. `storage.buckets` en un proyecto existente no se reemplaza. Copiar objetos por bucket; no declarar Storage PASS por filas en `storage.objects`.  
7. Edge: redeploy `admin-staff` y `vitacora-import-analyze` al destino con **secrets nuevos**. No copiar secrets históricos ni service_role al APK.  
8. RPC smoke: PERSON forbidden en staff; signed URL cruzada FORBIDDEN; import flag.  
9. Dumps fuera de Git. No recargar el dataset histórico en el destino de drill.  
10. Registrar evidencia en `SEC-04-restore-drill-result.md`.

## Cuatro capas independientes (recovery bundle)

El disaster recovery solo está completo cuando las cuatro tienen procedimiento.

| Capa | Qué cubre | Mecanismo canónico | Ensayado ahora |
|---|---|---|---|
| LAYER 1 — DATABASE / AUTH | schema `public`, migraciones, **Auth hosted** | **PROD Pro physical / daily backup** + restore-to-new-project | `public` sí (SEC-04C). Auth físico **no** (Free STAGING no lo ofrece) |
| LAYER 2 — STORAGE BYTES | objetos + manifest | `scripts/security/backup-storage.ps1` / `verify-storage-backup.ps1` / `restore-storage.ps1` a disco privado | sí (SEC-04D) |
| LAYER 3 — CODE | migrations, Edge, Android/web, scripts | este repositorio Git | sí (el código vive en Git) |
| LAYER 4 — CONFIG / SECRETS | URL, anon, service_role server, OAuth, Firebase, Maps, Edge secrets | inventario por **nombre** + secret manager / Dashboard | inventario sí; valores **nunca** en Git |

Un backup de DB **no** es backup de secrets ni de bytes de Storage.

## Auth — clasificación (SEC-04C + 04D)

No se fuerza COPY inseguro a `auth.*` hosted. El dump lógico **puede contener** filas Auth; **no** las aplica sobre un proyecto existente.

| Componente | Clasificación | Evidencia |
|---|---|---|
| `auth.users` | **RECOVERED BY PROVIDER PHYSICAL RESTORE** (UNKNOWN UNTIL PROD VERIFICATION) | dump incluye filas; COPY falló (`users_email_partial_key` / not owner) |
| `auth.identities` | igual | COPY no reemplazó el set hosted |
| MFA factors | **RECOVERED BY PROVIDER PHYSICAL RESTORE** / fallback **REQUIRES RE-ENROLLMENT** | dump incluye `mfa_factors`; no se demostró restore usable |
| sessions / refresh tokens | **EPHEMERAL / SHOULD NOT BE RESTORED** | re-login; no rehidratar sesiones |
| password hashes | **RECOVERED BY PROVIDER PHYSICAL RESTORE** (UNKNOWN UNTIL PROD VERIFICATION) | viven en Auth hosted; no se inspeccionan ni se copian a tickets |
| admin technical users (`platform_admin_identities`) | filas `public` = **RECOVERED BY LOGICAL DUMP**; login = físico | SEC-04C: 6 filas restauradas, **0** matches Auth |
| PERSON linkage (`persons.user_id`) | filas `public` = **RECOVERED BY LOGICAL DUMP**; login = físico | SEC-04C: 2 persons, **0** matches Auth |

Mecanismo canónico de Auth en PRODUCCIÓN: backup físico diario de Supabase Pro y **Restore to a new project**. PROD no pasa el gate de DR hasta comprobar que el backup físico está ON, incluye Auth, restore-to-new-project existe, y un restore de prueba reenlaza Auth/`public`. **No contratar Pro desde Cursor.**

### Fallback extremo (Auth no restaurable)

Sin backdoors, sin reset masivo de passwords, sin bypass MFA:

- Sobrevive: `public` (persons, pets, VitaCora, posts, catalogs, RLS/RPC, admin **rows**).
- Preservar `user_id` existentes; no reasignar UUIDs.
- PERSON y staff deben **reautenticarse** (signup/login nuevo solo con proceso de reclamación de cuenta).
- MFA staff: **re-enrollment** TOTP nativo tras identidad Auth nueva; no saltar AAL2.
- Evitar apropiación: no crear usuarios Auth con emails conocidos sin prueba de control del buzón; no reutilizar `user_id` ajenos; congelar altas hasta el procedimiento.
- Comunicación: aviso de re-login / re-enrol MFA; no pedir que envíen passwords por chat.

## Storage bytes (SEC-04D)

Inventario STAGING (lectura): 5 buckets canónicos. Objetos reales: **12** en `public-media` (~26.9 MiB). `private-media`, `documents`, `moderation-evidence`, `vitacora-import` = **0** objetos. No se relajó RLS. Buckets privados no se hicieron públicos.

Tooling (sin credentials; CLI login o env):

```
# destino FUERA del repo (default %LOCALAPPDATA%\LeoVer-SEC04D\<stamp>)
pwsh scripts/security/backup-storage.ps1 -ProjectRef <ref> -OutDir <private>
pwsh scripts/security/verify-storage-backup.ps1 -BackupDir <private>
# NUNCA -DestProjectRef tobqbddfcyitwgbkthhy
pwsh scripts/security/restore-storage.ps1 -BackupDir <private> -DestProjectRef <isolated>
```

Manifest privado: `backup_id`, timestamp, `project_ref`, bucket, object path, size, SHA-256, result. Sin URLs firmadas ni tokens.

Ensayo SEC-04D: backup STAGING read-only → verify 12/12 → restore 2 objetos a `wystsapjfpdtoprlmizz` prefijo `sec04d-probe/` → SHA-256 match. STAGING no escrito.

Frecuencia inicial propuesta para PROD (presupuesto bajo): **job diario** del script (u objeto incremental equivalente). No es PITR de Storage.

## Procedimiento de dump (cuando haya destino)

Herramienta oficial: `supabase db dump --linked` **hacia un path local privado**, o el restore del Dashboard.

```
# Ejemplo — NO ejecutar contra STAGING para overwrite.
# Escribir a un disco no compartido. Borrar después.
npx supabase --workdir infra/supabase-canonical db dump --linked -f %TEMP%\leover-staging-logical.dump
```

Incluir, si el destino lo requiere:

- schema `public`
- `supabase_migrations`
- `auth` (users / identities / MFA) si el desastre lo necesita
- `storage` metadata

No incluir el dump en el repo. Cleanup: borrar el archivo y vaciar papelera.

Verificación no destructiva (este repo):

```
npx supabase --workdir infra/supabase-canonical db query --linked -f scripts/security/verify-backup-readiness.sql
```

## Auth en un disaster restore (detalle)

Restaurar solo `public` **no** recrea:

- `auth.users`
- sesiones
- factores MFA TOTP
- `platform_admin_identities` linkage (`user_id` debe existir en Auth)

SEC-04C demostró además: un dump que **incluye** schema `auth` **tampoco** reemplaza Auth en un proyecto hosted existente (`COPY` no es owner; unique email). Para Auth usable hace falta backup/PITR nativo del proveedor o un proyecto nuevo restaurado por Dashboard — no `psql` contra `auth.users`.

Tras restore Auth + public:

1. Confirmar que el root `is_root` sigue existiendo y **no** se recreó un backdoor.
2. Staff entra por login técnico + MFA nativo (AAL2). No hay recuperación desde la APK.
3. No exportar secretos TOTP. Si hay compromiso: `staff_reset_mfa` por SUPERADMIN AAL2 (1062), nunca root.

## Storage

Un dump SQL no recupera bytes. Usar LAYER 2 (`backup-storage.ps1`). Restaurar metadata `media_assets` sin el objeto deja paths huérfanos. Restaurar objetos sin filas deja basura. No declarar PASS solo por `storage.objects`.

## Edge

Código: redeploy desde repo.

Desplegadas en STAGING (no desplegar las demás solo porque existan):

- `admin-staff`
- `vitacora-import-analyze`

En repo **sin** deploy SEC-03/04: `push`, `delete-account`, `klipy-proxy`.

### Nombres de secrets (sin valores)

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY` (solo Edge privilegiada; nunca Android)
- Google OAuth client secret (Dashboard Auth)
- Maps API key (cliente / restricción de APIs)
- Firebase / FCM (push **no** desplegado)
- Cualquier secret futuro de `klipy-proxy` **antes** de un deploy

Rotación: ver escenarios abajo. **No rotar ahora.**

## Configuración externa (inventario, no cambiar)

- Supabase project + Auth redirect URLs + Google OAuth
- Storage buckets y `file_size_limit`
- Edge secrets
- DNS / URL pública de la app
- Cloudflare si aplica (no se cambió)
- Firebase/Push deshabilitado a propósito

## RPO / RTO

| Métrica | Valor obtenido | Condición mínima PASS | Estado |
|---|---|---|---|
| Database RPO (lanzamiento) | ≤ ~24 h con daily backup **PROD Pro** | decisión Leonardo; verificar al crear PROD | target |
| Database RPO (dump lógico SEC-04C) | instante `2026-09-04T15:37:19Z` | no es PITR; no cubre Auth hosted | observado |
| Storage RPO (lanzamiento) | daily object backup (script / job) | propuesto; corpus actual ~27 MiB | target |
| Config/code RPO | commit Git + secret manager | inmediato al commit / rotación | procedimiento |
| PITR | deferred lanzamiento inicial | reevaluar por crecimiento/costo | **DEFERRED BY OWNER** |
| Measured RTO (SEC-04C public) | ~20 min restore+verify; ~37 min con dump | Auth no usable en ese drill | observado |
| Measured RTO (SEC-04D storage sample) | backup 12 objetos ~110 s; restore 2 objetos + hash ~50 s | destino aislado | observado |
| Production target RTO | medir restore-to-new-project + Storage job + Edge redeploy **en PROD** | no inventar horas de cutover | PENDING PROD |

Native Dashboard Restore / PITR **no** ensayados (Free STAGING no los ofrece). No inventar retención.

## Escenarios

### A) DELETE accidental (filas)

- Detección: quejas QA / conteos / audit.
- Contención: no seguir escribiendo sobre las mismas PKs si hay dump reciente.
- Recovery: restore puntual (PITR si existe) o reinsert desde dump aislado.
- Verificación: FK, sequences, `verify-backup-readiness.sql`.

### B) Migration defectuosa

- No editar la migración aplicada. Forward-only.
- Contención: no `db reset`.
- Recovery: migración correctiva nueva; o restore a destino aislado + replay.
- Verificación: `schema_migrations` vs archivos del repo.

### C) Corrupción lógica

- Contención: kill switch (`imports.enabled`, `reels.create.enabled`, `media.video.upload.enabled`).
- Recovery: dump + corrección en destino aislado; no “arreglar” a mano en prod.
- Verificación: módulos congelados intactos; VitaCora sequence sin colisión.

### D) Account compromise (PERSON)

- Contención: sign-out global Auth; reset password; revisar reports.
- Recovery: no restaurar todo el proyecto por una cuenta.
- Rotación: solo si hubo filtración de tokens de ese usuario.

### E) Service-role leak

- Contención: rotar `SUPABASE_SERVICE_ROLE_KEY`; redeploy Edge; revisar audit.
- Recovery: asumir que pudieron bypassear RLS.
- Verificación: no hay service_role en Android (`CanonicalMediaRepositories`, APK).

### F) Storage loss

- Recovery: objetos desde backup de bucket o fixtures.
- Verificación: un asset público + uno privado + un import.

### G) Admin / SUPERADMIN compromise

- Contención: revocar sesiones Auth; reset password; `staff_reset_mfa` (no root) por otro SUPERADMIN AAL2 si existe; si el root cayó, restore Auth + rotar service_role.
- Review: `platform_admin_identities`, roles, Edge secrets.
- **No crear backdoor ni MFA skip.**

### H) Edge deployment defectuoso

- Contención: redeploy de la revisión anterior desde Git.
- Recovery: el código canónico es el repo, no el dashboard.
- No desplegar `push` / `delete-account` / `klipy-proxy` como “arreglo”.

## Recuperación sin Android

Todo lo anterior es Dashboard + CLI + SQL. La APK no es necesaria para restaurar Auth, schema, Edge ni Storage.

## VitaCora sequence

Tras restore: `select last_value, is_called from public.vitacora_public_number_seq;`  
El próximo `nextval` no debe reutilizar un `public_vitacora_number` existente. Ajustar la sequence a `max(public_vitacora_number)` si el dump quedó desfasado. No consumir números de STAGING solo para probar.

## Integridad post-restore (checklist)

- FKs / sequences
- RLS enabled + grants
- RPCs `get_admin_session` (AAL2), rate-limit helpers
- Roles / permissions
- Catálogos (species) presentes
- Auth `user_id` enlaza `persons` y `platform_admin_identities`
- Storage fixture legible
