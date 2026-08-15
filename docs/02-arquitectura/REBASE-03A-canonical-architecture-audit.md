# REBASE-03A — Canonical Architecture & Schema Audit

**Producto:** LeoVer
**Sociedad:** COMUNIDAPP S.A.S.
**Fecha:** 15 de agosto de 2026
**Rama:** `main`
**HEAD (esta sesión):** `32577b9917500fe5e4096129f422ba29da4228b2`
**Gobierno vigente:** Master v1.2 · D01 v1.3
**Tipo:** READ / ANALYSIS / DOCS ONLY
**No ejecutado:** SQL nuevo, db push, reset remoto, nuevo proyecto Supabase, código de producto, APK, commit, push.

Fuentes de gobierno:

- `docs/00-maestro/LeoVer-Documento-Maestro-v1.2.md`
- `docs/01-producto/D01-Modulos-y-Orden-v1.3.md`
- ADR-016 (identidad / contexto; actualizar puntero a Master v1.2 en REBASE-03B)

Master v1.1 y D01 v1.2 no se modifican.

---

## 1. Executive Summary

El repositorio actual es un **sistema acumulativo de 82 migraciones** (`001`–`082`) con ~**219 tablas `public.*`** detectadas en scripts, múltiples pares legacy/moderno, y un backend remoto de **referencia** que **no coincide** con el árbol local.

El gobierno canónico (Master v1.2 / D01 v1.3) ya cerró:

- **VitaCora** = producto + dominio + **M14** (Passport/Pasaporte = histórico).
- Identidad humana única; `account_type` **no canónico**.
- Multi-OWNER personal; `created_by` ≠ autoridad.
- Organización = responsable institucional; un solo ActiveContext por org; org multicapacidad.
- VitaCora = **composición**, no segunda fuente de verdad.
- Grants de servicio: `NONE | ESSENTIAL | HEALTH | ESSENTIAL_AND_HEALTH | FULL_SHAREABLE`.
- Duración: `UNTIL_DATE | INDEFINITE | REVOKED`.
- Reserva ≠ grant; snapshot de instrucciones.
- Mensajería institucional con la entidad.
- Media canónica (candidato M05); fechas date vs timestamptz UTC.
- Marketplace checkout **fuera de V1**.
- Delete de negocio = no destructivo; **erasure legal = PENDING**.
- **Addendum:** cuentas adolescentes (PERSON + age/protection dimension). **No** `AccountType.TEEN`. Guardian ≠ pet owner. Umbrales jurídicos = `PENDING_LEGAL_FINALIZATION`.

La implementación actual **no es ese modelo**. Es un puente útil de conceptos (M08 graph, M05 `file_assets`, M03 memberships, M20/M23/M28) mezclado con:

1. **Snapshot M14 Passport** que duplica identidad/salud.
2. **Un solo PRINCIPAL** (no multi-OWNER).
3. **`account_type` persistido** + `AppMode` de navegación legacy.
4. **Pares duplicados** (chat, bookings, shops, posts, donations, events, clinical).
5. **Drift remoto** documentado (gap `039`–`052`; repo ya en `082`).
6. **Timezone hardcoded** `America/Argentina/Buenos_Aires` en defaults.
7. **Guardería operativa** ausente (M23 es agenda genérica).
8. **Mensajería org** solo como tipo/contexto; participantes = dos personas.
9. **Sin modelo de edad humana / guardian / teen safety** (`users` no tiene `birth_date`; no hay relación guardian).

**Recomendación:** no “arreglar” el backend actual in-place. Crear **nuevo Supabase staging canónico**, archivar `001`–`082` como historia, y construir un baseline limpio alineado a Master v1.2 / D01 v1.3. El backend actual se conserva como rollback/referencia. Producción futura separada.

```text
OLD BACKEND  = reference / rollback temporal
NEW BACKEND  = canonical staging
FUTURE PROD  = separado
```

**Gobierno pendiente (no editado aquí):** enmendar Master v1.2 y D01 v1.3 con `TEEN_ACCOUNT_SUPPORT` (ver §12.O). `GOVERNANCE_AMENDMENT_REQUIRED = YES`.

**Veredicto de esta etapa:** `REBASE_03A_READY_FOR_REVIEW`

---

## 2. Current State Inventory

### 2.1 Working tree (no tocado)

| Campo | Valor |
|-------|--------|
| Branch | `main` |
| HEAD | `32577b9917500fe5e4096129f422ba29da4228b2` |
| Working tree clean | **NO** (~178 paths dirty/untracked) |
| Unrelated WIP preserved | **YES** (no restore/reset/stash/checkout) |

WIP ajeno incluye Kotlin UI V2, catálogo de ubicación, ActiveContext, decoders M08/M09/M11, docs Master v1.2 / D01 v1.3, migración local `082`, scripts de QA. **No se limpia.**

### 2.2 Migraciones locales

**Count:** **82** archivos en `supabase/migrations/` (`001` … `082`, secuencia continua).

`082_lost_found_public_code_pgcrypto_schema.sql` está **untracked** en el working tree; forma parte del inventario real del repo en esta sesión.

#### Matriz consolidada por rango

| Rango | Módulo(s) | Tablas / objetos principales | RPC / RLS | Notas |
|-------|-----------|------------------------------|-----------|-------|
| 001–012 | M00 fundación + fases legacy | `users`, `pets`, `lost_found_*`, `adoptions`, `conversations`/`messages`, `service_profiles`/`service_bookings`, `shop_products`, `payment_intents`, `pet_clinical_records`, `posts`, `donation_campaigns`, `community_events`, `shelters` | RLS temprano; writes directos | **Baseline sucio.** Pares que luego se “rehacen” con Mxx. |
| 013–018 | M01/M02 | `device_tokens`, `user_consents`, `account_deletion_requests`, perfil, `platform_roles`, `permissions`, `user_role_assignments` | `handle_new_user` fuerza `account_type='PERSON'` desde 014/015/079 | Roles plataforma **KEEP**. Columna `users.account_type` **REPLACE**. |
| 019–021 | M03 | `organizations`, memberships, roles/permisos org, invitations, branches, audit | `has_org_permission` | Org core **KEEP**; `organizations.type` **REPLACE** semántica. |
| 022–023 | M04 | moderation/support/admin | RLS proyección sensible | **KEEP** concepto. |
| 024–025 | M05 | `file_assets`, versions, links, upload sessions; buckets | `request_file_signed_url` (no persiste token) | **Candidato autoridad canónica.** Dualidad `photo_url`/`logo_path`. |
| 026–028 | M06 | notifications, outbox, installations | | **KEEP**. |
| 029–034 | M07 | audit, health, metrics, retention | | **KEEP** con estrategia de retención. |
| 035–036 | M08 | `pet_responsibilities`, `pet_authorizations`, `pet_transfers`, `pet_status_history`; `pets.owner_id` nullable | `m08_*` | Graph **REBUILD** hacia multi-OWNER + `created_by`. Header: staging no autorizado; RC1 lo listó aplicado. |
| 037–039 | M09 | publications, applications, interviews, agreements, follow-up | `m09_*` | Identidad mascota no debe duplicarse. Finalización que **archiva** pet vs canon “misma identidad”. |
| 040–041 | M10 | `foster_homes` / profiles, placements, expenses | | Custodia vía `TEMPORARY_CUSTODIAN`. |
| 042–045 | M11 | shelter ops, campaigns, emergencies | | Org CO_RESPONSIBLE en intake, no PRINCIPAL org. |
| 046–047 | M12 | clinic profiles, appointments, availability | timezone default AR | Agenda vet **paralela** a M23. |
| 048–049 | M13 | sightings, match candidates, review | | **KEEP** dominio. |
| 050–052 | M14 Passport | `pet_passports*`, credentials, verification | `m14_*`; `passport.*` permissions | **REPLACE** completo por VitaCora. Gap remoto histórico. |
| 053 | M16 | shelter profiles públicos | | **KEEP** perfil org público. |
| 054–057 | M17 | campaigns, contributions, in-kind, volunteer | sin comisión/escrow | **KEEP** 0% comisión. `provider_reference` no es checkout LeoVer. |
| 058–059 | M18 | events | | Dualidad con `community_events` 006. |
| 060–061 | M19 | `m19_social_posts`, comments, reactions | | Dualidad con `posts`. |
| 062–063 | M20 | `m20_conversations` (dos `users`), messages, blocks | | **REBUILD** institucional. |
| 064–065 | M21 | reviews, verifications, disputes | | **KEEP**. |
| 066–067 | M22 | providers, branches, offerings; `BOARDING` categoría | | Guardería = categoría, **sin** modelo de estadía. |
| 068–069 | M23 | availability, `m23_bookings` + `pet_id` opcional, `policy_snapshot` vacío | `zone_id` IANA | **REBUILD** daycare/snapshot. Dualidad `service_bookings`. |
| 070–071 | M25 | shops, cart, orders, stock, returns | **sin pagos** | **DROP_FROM_CANONICAL_V1_BASELINE** (objetos transaccionales). Catálogo futuro = KEEP_FOR_FUTURE_ARCHIVE. |
| 072–074 | M26 | matching, AI jobs, human review | | **KEEP** slice. |
| 075–077 | M27 | webhooks, oauth, idempotency | | **KEEP** para integraciones futuras; no bloquear V1 social. |
| 078 | M19 patch | `posts.expires_at` | | Parche sobre tabla **legacy** `posts`. |
| 079 | M01 | username required en signup | | **KEEP** idea; rehacer en baseline. |
| 080 | M28 | cares, vaccinations, documents, **passport proposals**, grants | `m28_*` | Salud profesional **KEEP**; bridge Passport **REPLACE**. |
| 081 | Web pública | RPC `get_public_pet` / adoption / lost | depende `pet_passports.public_code` | **REBUILD** contratos públicos VitaCora. |
| 082 | Lost/found | `public_code` pgcrypto | lee también `pet_passports.public_code` | **REBUILD**; no crear `passport_*` nuevo. |

Muchas migraciones 036+ declaran **LOCAL ONLY / STAGING NO AUTORIZADO** en el header, **aunque** documentos de operación posteriores registran applies parciales. Eso es evidencia de proceso inconsistente, no de un baseline limpio.

#### Objetos especialmente relevantes

| Área | Objetos |
|------|---------|
| M03 | `organizations.type` CHECK único; memberships OWNER/ADMIN/MEMBER/VIEWER |
| M05 | `file_assets` + buckets `public-media`, `profile-avatars`, `organization-documents`, legacy `leover` |
| M08 | `pets.owner_id`, **sin** `pets.created_by_user_id`, **sin** `pets.public_code`; un PRINCIPAL activo |
| M09 | `adoptions` + `adoption_*`; public_code en 081 |
| M10/M15 | foster + `TEMPORARY_CUSTODIAN` |
| M14 | 5 tablas `pet_passport_*`; snapshot identidad; visibility ≠ grant |
| M19 | `m19_social_posts` vs `posts` |
| M20 | participantes user-user; `conversation_type=ORGANIZATION` engañoso |
| M21 | reviews post-relación |
| M22 | `m22_service_providers.category` incluye `BOARDING` |
| M23 | bookings genéricos; no check-in/guest/stay |
| M27 | integraciones |
| M28 | `veterinary_vaccination_records` + copia a credential Passport en ACCEPT |
| M29 | **sin SQL** |

Enums: el repo **casi no usa** `CREATE TYPE`; usa `text` + `CHECK`. El baseline nuevo puede seguir así o introducir tipos PostgreSQL explícitos para scopes/status (preferible para grants VitaCora).

### 2.3 Estado remoto (referencia; no modificado)

**Esta sesión no consultó** `schema_migrations` ni `pg_catalog` remoto: no hay CLI link seguro en el working tree y cualquier operación con riesgo de escritura está prohibida.

Último inventario documental de staging (`docs/06-release/RC1-auditoria-migraciones-001-077.md`):

| Métrica | Valor documentado |
|---------|-------------------|
| Local entonces | 77 |
| Staging entonces | **62 versiones:** `001`–`038` + `053`–`077` |
| Gap | **`039`–`052`** (M09 completion → M14) |
| Última remota documentada | **077** |

Repo **ahora:** **82** (`078`–`082` posteriores a ese snapshot). Docs de operación posteriores afirman applies de rangos 053–077 (y 068–069, 062–063, etc.), **en tensión** con headers LOCAL ONLY y con el gap 039–052.

**Drift: YES.**

Resumen de drift (sin corregir):

1. Gap histórico `039`–`052` vs scripts locales de Passport/foster/vet/sightings.
2. Repo adelante (`078`–`082`) respecto del snapshot RC1.
3. Headers “no aplicar remoto” vs cierres que declaran `schema_migrations` registradas.
4. Tablas duales (legacy 001–012 + Mxx) pueden existir ambas en staging.
5. `035` header dice no aplicar; RC1 lo marca aplicado.

**CURRENT_REMOTE_MAX_MIGRATION (live):** no determinado en esta sesión.
**CURRENT_REMOTE_MAX_MIGRATION (documentado):** `077`, con hueco `039`–`052`.

No se inventa un dump remoto. REBASE-03B debe hacer **una** lectura read-only de staging **antes** de archivar, sin apply.

### 2.4 `account_type` / `AppMode`

**Objetivo canónico:** `ACCOUNT_TYPE_CANONICAL = NO`.

| Señal | Conteo (repo, esta sesión) |
|-------|----------------------------|
| `AccountType` / `account_type` | **174 archivos**, **~501 hits** |
| `AppMode` / `appMode` | **4 archivos**, **~12 hits** |

#### Clasificación

| Aparición | Clase | Autoridad real hoy |
|-----------|-------|-------------------|
| `users.account_type` (001+) | DATABASE | Persistido; default/trigger `PERSON` desde 014/015/079 |
| JWT / `raw_user_meta_data.account_type` | AUTH | `handle_new_user` aún lee metadata; signup Android fuerza PERSON (`SessionIdentity.signupAccountType`) |
| `User.accountType`, `UserProfileRow` | DTO / DOMAIN | Campo vivo en mapeo Supabase |
| `AccountType` enum + `AccountTypeDropdown` | UI | Deprecated; todavía en árbol |
| `AppMode` + `toAppMode()` + `bottomNavItemsFor(AccountType)` | NAVIGATION | **Todavía condiciona navegación** si se llama el overload legacy |
| `RolePermissions.canAccessSumate/Comunidad(accountType)` | SECURITY (cliente) | Deprecated; **sí** ramifica por tipo si se usa |
| `ModulePermissions.canPublish*(accountType)` | SECURITY (cliente) | Deprecated |
| `OperationalContext` / `ActiveContextStore` | DOMAIN | **No** lee `account_type` (ADR-016) |
| RLS/RPC `has_permission` / `has_org_permission` / `m08_actor_has_capability` | SECURITY (server) | **No** usa `account_type` como grant (contención explícita en comentarios) |
| Tests / docs / scripts seed | TEST / DOC / SCRIPT | HISTORICAL + fixtures |
| `SessionIdentity.resolveStoredAccountType` → siempre PERSON | DOMAIN | Puente: columna puede existir, identidad no |

**¿Concede autoridad real en backend?** No como grant RLS primario.
**¿Condiciona navegación?** Sí, en APIs legacy `AccountType`/`AppMode` aún presentes; el camino V2 usa `OperationalContext`.
**¿RPC/RLS dependen?** Columna existe; helpers de autorización documentan que **no** otorgan.
**¿DTO/API lo exigen?** Sí: `users.account_type` NOT NULL en 001.
**¿Puede desaparecer del baseline nuevo?** **Sí.** No recrear la columna. Navegación = ActiveContext derivado.

### 2.5 Passport → VitaCora (legado)

| Señal | Conteo |
|-------|--------|
| Passport/Pasaporte/passport | **122 archivos**, **~1911 hits** |

Clasificación por capa:

| Capa | Objetos | Destino CLEAN BREAK |
|------|---------|---------------------|
| HISTORICAL_MIGRATION | 050–052, 080 bridge, 081/082 refs | HISTORICAL_ONLY en repo viejo |
| DATABASE_OBJECT | `pet_passports`, `pet_passport_credentials`, verification_*, status_history | **A** desaparecen del baseline nuevo |
| RPC | `m14_*`, `_m14_*`, `m28_*_passport_*` | **A**; reemplazo `vitacora_*` |
| DTO/DOMAIN/REPO/VM/UI/ROUTE | `M14Passport*`, `NavRoutes.M14_PET_PASSPORT`, `leover://passport/` | **B** rebuild nombres VitaCora |
| TEST | M14Foundation*, PassportCreateFromPet* | HISTORICAL_ONLY / reescribir tests |
| DOC | ADR-014 filename, Master v1.1 | HISTORICAL_ONLY |
| WEB | `get_public_pet`, `PUBLIC_PASSPORT_NOT_AVAILABLE` | **B** contratos públicos |
| KMP/IOS/ANDROID | deeplink passport, public pages | **B** |

M14 **hoy duplica** nombre/especie/sexo/microchip/birth_date y, vía M28 ACCEPT, **copia vacunas** a `VACCINATION_ATTESTATION`. Eso viola composición canónica.

Sharing actual = **visibility enum** (`PRIVATE`…`PUBLIC_REDACTED`), **no** grant por actor/alcance/duración.

### 2.6 M08 — identidad y responsabilidad (real)

```text
PET identity     = public.pets (id persistente)
OWNER projection = pets.owner_id (legacy, 1 persona) + 1 PRINCIPAL activo
CREATOR          = AUSENTE en pets; solo pet_responsibilities.created_by (por fila)
ORG              = PRINCIPAL organization_id XOR person_id
CUSTODY          = role TEMPORARY_CUSTODIAN (misma tabla)
SHARING          = pet_authorizations.capabilities text[] (pet.*), no VitaCora scopes
PUBLIC_CODE      = NO en pets; vive en pet_passports
LIFECYCLE        = pets.status ACTIVE|DECEASED|ARCHIVED
HEALTH DECLARED  = columnas pets + jsonb vaccinations (no dominio profesional)
```

Canon vs real: **multi-OWNER NO**; **created_by separado NO**; **org-as-entity parcial**; **custody mezclada en responsibilities**.

### 2.7 Organizaciones y ActiveContext

- Una fila `organizations`; **`type` es un solo valor**.
- Capacidades reales ya pueden coexistir (M12 clinic, M22 provider, M16 shelter, M25 shop) **como tablas hijas**, no como identidades.
- ActiveContext Android: `OperationalContext` + SharedPreferences; **derivado** de memberships/perfiles; **no** es auth; **no** usa `account_type`.
- Riesgo UX: si el resolver lista por capacidad, puede mostrar “Mundo Mascota — Veterinaria” y “Mundo Mascota — Guardería”. Canon: **una sola vez** la entidad.

### 2.8 Media (M05)

Autoridad emergente: `file_assets` + `storage_path` + metadata + `file_asset_links`.
Signed URL: RPC efímera (**correcto**).
Inconsistencias: `pets.photo_url`, `users.avatar_path`, `organizations.logo_path`/`cover_path`, DTOs `photoUrl` en toda la UI, bucket `leover` legacy.

**MEDIA_CANONICAL_AUTHORITY = M05 (candidato; confirmar en REBASE-03B DDL).**
**MEDIA_REBUILD_REQUIRED = YES** (unificar referencias; no persistir signed URL).

### 2.9 Tiempo y ubicación

| Problema | Evidencia |
|----------|-----------|
| Fechas calendario como `text` | `pets.last_deworming`, `last_vet_visit` |
| Edad en vez de nacimiento | `pets.age_years`/`age_months`; M08 **prohíbe** inventar `birth_date` en pets; Passport **sí** tiene `birth_date date` |
| Default TZ AR | M12 `timezone_name`, M14 número de pasaporte, M16 `zone_id_name`, UI M23/M15 |
| Agenda correcta en parte | M23 `starts_at` timestamptz + `zone_id` |
| Ubicación | `province`/`city` texto; catálogo Argentina **solo in-memory** (`LocationCatalog`); lost/found lat/lng |

### 2.10 Consents / onboarding / tutorial

| Tema | Estado |
|------|--------|
| Legal | `user_consents` (014): `terms_version`, `privacy_version`, `accepted_at`, locale, source. RPC `accept_legal_consents`. |
| Borrado de cuenta | `account_deletion_requests` |
| Tutorial | **solo local** `OnboardingPreferencesRepository` (SharedPreferences). Sin tabla de viewed/skipped/completed versionada. |
| Tutorial ≠ consentimiento | Cumple gobierno; falta persistencia servidor para reopen cross-device. |
| Edad humana / menor | **Ausente.** `users` no tiene `birth_date` ni age band. `birth_date` existe solo en Passport (mascota). No hay guardian links. |

### 2.11 Donaciones y marketplace

- M17: campañas + contribuciones **sin** comisión/escrow. Alineado a 0% LeoVer.
- Legacy `payment_intents` (012) + `shop_products`: **contradice V1** si se interpreta como checkout.
- M25: cart/orders/stock **sin PSP**. Gobierno: transaccional **NO en V1** → no formar parte del baseline canónico V1.

### 2.12 Seguridad / RLS (principios actuales)

Fortalezas a conservar:

- Writes sensibles por RPC `SECURITY DEFINER` + revoke table privileges (patrón M14/M08).
- `has_permission` (plataforma) ≠ `has_org_permission` (membresía).
- Comentarios de contención: AccountType no grant.

Debilidades:

- Políticas duplicadas entre tablas legacy y Mxx.
- Gap 039–052: objetos Passport pueden faltar o existir por otro camino.
- RLS recursivo riesgo en memberships (patrón conocido; auditar en baseline nuevo con helpers `SECURITY DEFINER` estables).
- UI aún puede *parecer* autoridad vía `AppMode`.

Plataforma: `USER | MODERATOR | ADMIN | SUPERADMIN` en `platform_roles` — **KEEP separados** de membership/producto.

---

## 3. Canonical Architecture

### 3.1 Principios (no negociables)

```text
IDENTITY = PERSON (auth.users → persons/users)
ACCOUNT_TYPE = NOT CANONICAL
ACTIVE_CONTEXT = UX ONLY (no grant)
PLATFORM_ROLE ≠ ORG MEMBERSHIP ≠ PET RESPONSIBILITY
ORGANIZATION = ENTITY (multicapacity; one ActiveContext)
PET = PERSISTENT IDENTITY (M08)
CREATOR = PROVENANCE ONLY
OWNER* = 1..N persons (personal) OR organization (institutional)
CUSTODY ≠ RESPONSIBILITY ≠ VITACORA GRANT
VITACORA (M14) = COMPOSITION + GRANTS + INTEGRATION + VISIBILITY
NO passport_* IN NEW BASELINE
NO AccountType.TEEN / MINOR
AGE/PROTECTION = PERSON DIMENSION (not identity)
GUARDIAN != PET OWNER
PET PERMISSION != AGE CAPABILITY
UNDER_13_AUTONOMOUS_ACCOUNT = NO
LEGAL MINOR THRESHOLDS = PENDING LEGAL REVIEW (not hardcoded as law)
NO DUPLICATE AUTHORITATIVE FACTS
DELETE BUSINESS = SOFT/ARCHIVE/REVOKE
PRIVACY ERASURE = PENDING LEGAL
CHECKOUT V1 = NO
SOCIAL ESSENTIAL = FREE (no billing gate)
```

### 3.2 Pet identity & responsibility (propuesto)

```text
pets
  id
  created_by_user_id          -- provenance, immutable
  lifecycle_status
  essential attributes (name, species, …)
  avatar_asset_id → M05
  -- NO owner_id
  -- NO account_type
  -- public_code MAY live on pets OR vitacora_share_tokens; one authority

pet_responsibility_links
  pet_id
  holder_kind                 -- PERSON | ORGANIZATION
  holder_person_id
  holder_organization_id
  role                        -- OWNER | AUTHORIZED | TEMPORARY_CUSTODIAN (custody may split)
  status
  valid_from / valid_until
  granted_by_actor_user_id
  granted_by_entity
  -- UNIQUE: multiple OWNER persons allowed
  -- UNIQUE: at most one ORGANIZATION OWNER at a time (product rule)

pet_permission_grants        -- normalized, not unbounded text[]
  link_id or (pet_id, subject)
  permission_code             -- pet.view, vitacora.share, …
  granted_by, actor, timestamps

pet_responsibility_events    -- append-only history
```

Ejemplo canónico: `created_by = Verónica`; owners = Verónica + Leo; authorized = Carolina.

Org: `holder = Refugio Patitas`; `created_by = Juan`; Juan actúa con `vitacora.manage` vía membership. Decisiones: `responsible_entity` + `actor_user_id`.

Cambio de responsabilidad: misma `pets.id`, misma VitaCora; **no** copiar notas internas, DMs, secretos.

### 3.3 Family / permissions

No columnas booleanas por permiso. Catálogo `permission_codes` + filas de grant.

Invitaciones: `pet_family_invitations` (token, role propuesto, expires, actor).
Revocación: status + event.
Varios OWNER con capacidad equivalente.

### 3.4 VitaCora (M14) — composición

M14 **no** guarda una segunda vacuna.

| Vive en | Dominio |
|---------|---------|
| Identidad esencial | M08 |
| Salud declarada (alergias, etc. del responsable) | M08 o bounded `pet_declared_health` (una autoridad) |
| Salud profesional | M28 (`veterinary_vaccination_records`, cares) |
| Tránsito | M10/M15 |
| Servicios / estadías | M22/M23 |
| Adopción | M09 |
| Momentos | **M14** `vitacora_moments` (privados default) |
| Grants / visibilidad / integración / pending updates | **M14** |
| Media | M05 refs |

```text
vitacora_profiles          -- 1:1 pet, composition settings, public_code?
vitacora_moments           -- PRIVATE BY DEFAULT; ≠ social post
vitacora_access_grants     -- see 3.5
vitacora_update_proposals  -- envelope
vitacora_integration_links -- provenance pointer to source_record
```

Prohibido: `vaccinations` + `vitacora_vaccinations`.

Momentos: llegada, cumpleaños, recuerdos, fotos (M05), viajes, hitos, libres. No auto-share a prestadores. Un momento privado puede coexistir con un post M19 distinto.

### 3.5 Access grants

```text
vitacora_access_grants
  pet_id
  grantee_kind              -- PERSON | ORGANIZATION
  grantee_person_id
  grantee_organization_id
  purpose
  scope                     -- NONE is "no row" or explicit deny
  granted_by_responsibility / actor_user_id
  granted_at
  duration_kind             -- UNTIL_DATE | INDEFINITE
  expires_at                -- null iff INDEFINITE
  revoked_at, revoked_by
  status                    -- ACTIVE | EXPIRED | REVOKED
```

Scopes de **servicio**:

| Producto | Código |
|----------|--------|
| NO COMPARTIR | `NONE` |
| DATOS ESENCIALES | `ESSENTIAL` |
| SALUD | `HEALTH` |
| ESENCIALES + SALUD | `ESSENTIAL_AND_HEALTH` |
| VITACORA COMPLETA (compartible) | `FULL_SHAREABLE` |

`FULL_SHAREABLE` **excluye** momentos privados, mensajes, auditoría, IDs internos, secretos, datos de terceros, contenido marcado privado.

Salud estructural ≠ salud obligatoriamente compartida.

Org grantee: miembros autorizados leen según membership + `vitacora.view` org permission. No grant por empleado.

UX: DAR ACCESO HASTA… / INDETERMINADO / QUITAR ACCESO. Sin períodos hardcodeados.

### 3.6 Booking ≠ grant + snapshot

```text
bookings (M23)
  pet_id                    -- identification for the service
  vitacora_grant_id         -- nullable
  instruction_snapshot jsonb -- feeding, meds as instructed, public consent, scope agreed, specials
  consent_snapshot
```

“No compartir VitaCora” **no** oculta qué mascota está reservada.

No copiar VitaCora completa. VitaCora viva puede seguir cambiando; la estadía conserva lo **acordado**.

### 3.7 Proposals + provenance

Envelope común `vitacora_update_proposals`:

- origin_kind: VET / WALKER / TRAINER / CAREGIVER / DAYCARE / TRANSPORT / FOSTER / OTHER
- payload + source_record_id
- status: PENDING / ACCEPTED / REJECTED / CORRECTION_REQUESTED / CANCELLED
- actor, entity, professional_profile_id, dates

ACCEPT → persistir en **dominio autoritativo** (M28 vacuna, M08 declarado, etc.) → M14 integra pointer.
Nunca third-party direct edit.

Provenance enum **cerrado**: `DECLARED | PROFESSIONAL | THIRD_PARTY | VERIFIED | INFERRED | SYSTEM` + actor/org/professional/source/accepted_by/at. No strings sueltas.

### 3.8 Custody

Autoridad de **hecho de custodia** = dominio que la origina (foster placement, daycare stay, transport).
Proyección canónica `pet_active_custody` (o responsibility TEMPORARY con `purpose` obligatorio) para “quién cuida ahora”.

Campos: start, end, purpose, custodian entity/person, origin record, status.
No tres tablas diciendo lo mismo sin FK de autoridad.

### 3.9 Daycare

M22 categoría `BOARDING` + M23 **extensión de estadía** (no módulo nuevo):

```text
reservation → check-in → temporary custody → guest
→ private care updates → incidents → check-out → stay history
```

Tres flags independientes:

- A operativa privada
- B visibilidad pública (**DEFAULT = NO**, revocable, publicaciones ligadas al consentimiento)
- C guardar estadía en VitaCora **como una unidad** (no scatter de cada paseo)

### 3.10 Institutional messaging (M20)

```text
conversations.subject_kind = PERSON | ORGANIZATION | PLATFORM_SUPPORT
participants = entity inbox, not employee-owned
messages.actor_user_id always
membership leave ≠ conversation leave
```

Hoy: dos `user_id`. **REBUILD.**

Addendum teen (§12.H): restricciones de DM de desconocidos, contacto institucional seguro, block/report, audit, controles de seguridad. El guardian **no** es lector automático de todas las conversaciones salvo definición legal/producto posterior.

### 3.11 Organizations / context

```text
organizations                  -- no type authority
organization_capabilities      -- SHELTER, VET, DAYCARE, SERVICES, …
organization_memberships + org permissions
ActiveContext = (PERSONAL | ORGANIZATION:{orgId} | …)
```

UI “Usar LeoVer como”: **Mundo Mascota** una vez; workspaces internos.

### 3.12 Media / time / location / delete

- Media: asset id + bucket/path + metadata + domain FK. Signed URL ephemeral.
- Date-only: `date`. Events: `timestamptz` UTC. Schedule: instant + IANA zone. **No GMT-3 global.**
- Location: catálogo `location_nodes` (Provincia + Localidad); coords precisas protegidas; público aproximado. PostGIS se conserva. Adolescentes: ubicación precisa **nunca pública**; Lost/Found solo por finalidad/permiso; proteger domicilio/escuela/rutina/tiempo real (§12.I).
- Delete negocio: `archived_at` / `revoked_at` / hide.
  `PRIVACY_ERASURE_POLICY = PENDING_LEGAL_FINALIZATION`.

### 3.13 Legal consents + tutorials (schema only)

```text
legal_acceptances     -- document_code, version, accepted_at, actor, subject (for whom), locale, source, withdrawal
tutorial_progress     -- tutorial_code (incl. TEEN_ACCOUNT_TUTORIAL), version, skipped/completed/viewed, reopened_at
```

SKIPPABLE = YES, REOPENABLE = YES. Tutorial ≠ legal consent.
Minor/guardian consent: `LEGAL_MINOR_CONSENT_POLICY = PENDING_LEGAL_FINALIZATION` (§12.L).

### 3.14 Community free / no social billing gate

Personas, rescatistas, refugios, ONG: identidad mascota, VitaCora esencial, rescue, lost/found, adoption, foster, community, operación esencial de refugio **sin entitlement de pago**.
Brand Studio / profesionales: comercial separado (M29/M24 futuro). Cuentas adolescentes = segmento **protegido**, no targeting sensible (§12.J).

---

## 4. Legacy Findings

1. **Passport es un segundo perfil de mascota** (050: “snapshot, not M08 data”).
2. **Vacuna triplicada:** jsonb pets + M28 records + credential M14.
3. **Un PRINCIPAL** vs multi-OWNER.
4. **Sin creator de pet.**
5. **`account_type` + AppMode** siguen en cliente.
6. **Org type** único vs multicapacidad.
7. **M20 no es inbox de entidad.**
8. **M23 no modela guardería.**
9. **M12 agenda y M23 agenda duplican** el concepto de turno.
10. **Pares legacy/moderno** (lista §2.2).
11. **`payment_intents` / `shop_products` / M25 orders** amenazan la regla V1 no-checkout si se habilitan.
12. **`photo_url` vs file_assets.**
13. **Fechas como text; TZ AR default.**
14. **Catálogo geo solo cliente.**
15. **Onboarding tutorial no server-side.**
16. **Drift remoto 039–052 + 078–082.**
17. **M09 archive-on-adopt** vs identidad continua.
18. **Shelter intake = CO_RESPONSIBLE** vs org responsable.
19. **Visibility Passport ≠ grant scopes canónicos.**
20. **M29 sin schema; no inventar red social paralela.**
21. **078 parchea `posts` legacy**, no `m19_social_posts`.
22. **082 aún referencia `pet_passports.public_code`.**
23. **No hay age band / guardian / teen protections** en persona. Edad existe para mascota, no para humano.

---

## 5. KEEP / REBUILD / REPLACE / DROP Matrix

Definiciones: KEEP = coincide con gobierno. REBUILD = concepto válido, rehacer. REPLACE = legado sustituido. DROP = no entra a baseline V1. HISTORICAL_ONLY = solo repo/backend viejo.

| CURRENT_OBJECT | MODULE | CURRENT_PURPOSE | DECISION | CANONICAL_REPLACEMENT | DEPENDENCIES | RISK | NOTES |
|----------------|--------|-----------------|----------|----------------------|--------------|------|-------|
| `auth.users` + `public.users` | M01 | Identidad humana | KEEP | same | Auth | Med | Quitar `account_type` |
| `users.account_type` | M01/M02 | Tipo-identidad | REPLACE | capabilities | JWT, DTOs, UI | High | No recrear |
| `AppMode` / `bottomNavItemsFor(AccountType)` | Android | Nav | REPLACE | ActiveContext | NavGraph | Med | Ya hay puente V2 |
| `platform_roles` USER/MOD/ADMIN/SUPERADMIN | M02 | Plataforma | KEEP | same | RLS | Low | No mezclar con org |
| `user_consents` | M01 | Términos/privacidad | REBUILD | `legal_acceptances` versionado | Legal pack | Med | Ampliar docs |
| `account_deletion_requests` | M01 | Baja | REBUILD | pending legal erasure | Legal | High | No irreversible aún |
| `organizations` + memberships + org RBAC | M03 | Entidad | KEEP | + capabilities table | M08/M16/M22 | Med | `type` no autoridad |
| `organizations.type` | M03 | Label único | REPLACE | `organization_capabilities` | UX contexto | Med | |
| `organization_resource_links` | M03 | Links débiles | REBUILD | FKs de dominio | M16/M22 | Low | |
| ActiveContext Android | ADR-016 | UX | KEEP | same | memberships | Low | Un org = un item |
| `pets` identity | M08 | Mascota | REBUILD | pets sin `owner_id`; + `created_by_user_id` | all pet modules | High | |
| `pets.owner_id` | M08 | Owner único | REPLACE | multi OWNER links | Android lists | High | |
| `pets.photo_url` | M08 | Avatar | REPLACE | `avatar_asset_id` M05 | UI | Med | WIP ya va ahí |
| `pets.vaccinations` jsonb | M08 | Salud declarada | REBUILD | declared health 1 autoridad | M28 | High | No duplicar M14 |
| `pets.last_*` text dates | M08 | Salud | REBUILD | `date` columns | forms | Med | |
| `pet_responsibilities` PRINCIPAL único | M08 | Responsabilidad | REBUILD | multi OWNER | transfers | High | |
| `pet_authorizations` text[] | M08 | Familia | REBUILD | permission rows | VitaCora | Med | |
| `TEMPORARY_CUSTODIAN` same table | M08 | Custodia | REBUILD | custody projection + domain FK | M10/M23 | Med | |
| `pet_transfers` | M08 | Cambio responsable | KEEP | + no copy private data | M09 | Med | |
| `pet_passports*` | M14 | Snapshot + QR | REPLACE | `vitacora_*` composition | web 081, M28 | High | no `passport_*` |
| `passport.*` permissions | M14 | ACL | REPLACE | `vitacora.*` | org roles | Med | |
| `m14_*` RPCs | M14 | CRUD snapshot | REPLACE | vitacora RPCs | Android/KMP | High | |
| `veterinary_passport_update_proposals` | M28 | Propose→credential copy | REPLACE | envelope → authoritative domain | M14 | High | |
| `veterinary_vaccination_records` | M28 | Hecho clínico | KEEP | VitaCora pointer | proposals | Low | |
| `veterinary_professional_access_grants` | M28 | Acceso clínica | REBUILD | align with VitaCora grant | M14 | Med | ≠ booking |
| M12 appointments | M12 | Turnos vet | REBUILD | unificar con M23 o delimitar | M23 | High | evitar 2 agendas |
| `m22_service_providers` | M22 | Prestadores | KEEP | + org capabilities | M23 | Low | |
| `m23_bookings` | M23 | Reservas | REBUILD | + snapshot, stay, grant_id | M14 | High | |
| `service_bookings` (011) | legacy | Reservas viejas | DROP | M23 | — | Med | HISTORICAL_ONLY |
| `m25_*` cart/orders/payments-less | M25 | Marketplace | DROP | KEEP_FOR_FUTURE_ARCHIVE | M24 | Med | V1 no checkout |
| `shop_products` / `payment_intents` | legacy | Checkout stub | DROP | none in V1 | — | High | contradice gobierno |
| `m17_*` donations | M17 | Direct + 0% | KEEP | alias/CBU verified | legal | Low | no custody LeoVer |
| `donation_campaigns` (006) | legacy | Dual | DROP | M17 | — | Low | |
| `m20_conversations` user-pair | M20 | Chat | REBUILD | entity inbox + actor | org leave | High | |
| `conversations`/`messages` (007) | legacy | Chat | DROP | M20 | — | Med | |
| `m19_social_posts` | M19 | Social | KEEP | moments ≠ posts | M05 | Low | |
| `posts` + 078 expires | legacy | Social viejo | DROP | M19 | — | Med | |
| `lost_found_*` + M13 | M13 | Alertas | KEEP | public_code propio | web | Med | coords protected |
| `adoptions` / M09 | M09 | Adopción | REBUILD | same pet id continua | M08 | Med | no archive identity |
| foster M10/M15 | M10 | Tránsito | KEEP | custody link | M08 | Low | |
| `file_assets` | M05 | Media | KEEP | **canonical media** | all | Med | confirm 03B |
| storage `leover` bucket | M05 | Legacy | DROP | public-media | — | Low | |
| `m21_*` | M21 | Reputación | KEEP | post-relación | bookings | Low | |
| `m18_*` | M18 | Eventos | KEEP | | | Low | |
| `community_events` (006) | legacy | Dual | DROP | M18 | — | Low | |
| M04 moderation | M04 | Admin | KEEP | | | Low | |
| M06 notifications | M06 | Push | KEEP | | | Low | |
| M07 audit/obs | M07 | Observabilidad | KEEP | retention strategy | legal | Med | unbounded audit |
| M26 matching | M26 | IA | KEEP | human confirm | M13 | Low | |
| M27 integrations | M27 | API | KEEP | sandbox | | Low | not V1 blocker |
| M29 schema | M29 | Brand Studio | KEEP | placements on M19 | quotas | Low | no 2nd social network |
| Tutorial SharedPreferences | M02 | UX | REBUILD | `tutorial_progress` | — | Low | skippable |
| Location in-memory | WIP | Provincia/Localidad | REBUILD | `location_nodes` SQL | forms | Med | |
| Web `get_public_pet` | Web | Páginas públicas | REBUILD | VitaCora public token | 081 | High | no perder capacidad |
| Dual `shelters` (006) vs M16 | M11/M16 | Refugio | REPLACE | org + M16 profile | | Med | |
| Human `birth_date` / age band | M01 | Edad | REBUILD | person protection dimension | legal | High | no existe hoy |
| Guardian relationship | M01/M02 | Adulto responsable | REBUILD | person–person link ≠ M08 | pets | High | no existe hoy |
| `AccountType.TEEN` (no existe; no crear) | — | Identidad | DROP | PERSON + protection_state | — | High | prohibido |
| M20 DM unknown | M20 | Chat teen | REBUILD | restricted inbound + report | safety | High | §12.H |
| M29 targeting | M29 | Ads | KEEP | protected segment exclude | legal | Med | no targeting sensible |

**Conteos de esta matriz (filas explícitas):**

| Decisión | Count |
|----------|-------|
| KEEP | 23 |
| REBUILD | 21 |
| REPLACE | 12 |
| DROP | 10 |
| HISTORICAL_ONLY (implícito en DROP/REPLACE de objetos viejos) | 8 |

---

## 6. Proposed Clean Baseline

No SQL en esta etapa. Dominios lógicos:

| Dominio | Source of truth | Entities | Módulo autoridad |
|---------|-----------------|----------|------------------|
| AUTH / PERSON | `auth.users` + person profile | person, username, **birth_date/age_band/protection_state**, email verify | M01/M02 |
| PLATFORM ROLES | assignments | USER/MOD/ADMIN/SUPERADMIN | M02 |
| GUARDIAN / MINOR SAFETY | person–person guardian links | invitation, verification, audit, revocation | M01/M02 (not M08) |
| ORGANIZATIONS | org row | org, branches, capabilities | M03 |
| MEMBERSHIPS | membership + org permissions | member, invitation, audit | M03 |
| PET IDENTITY | `pets` | pet, lifecycle, essential attrs, public token | M08 |
| PET RESPONSIBILITY | links OWNER/AUTHORIZED | created_by, holders, events | M08 |
| PET FAMILY/PERMISSIONS | grants + invitations | permission catalog | M08 (+ vitacora codes M14) |
| CUSTODY | domain record + projection | foster/stay/transport | M10 / M23 / M08 projection |
| VITACORA | composition profile, moments, grants, proposals, links | M14 | |
| HEALTH | declared (M08) vs professional (M28) | vaccinations, cares, docs | M08/M28 |
| MEDIA | `file_assets` | asset, version, link | M05 |
| SOCIAL | posts/reactions | M19 | ≠ moments |
| MESSAGING | entity conversations | M20 | actor_user_id |
| LOST/FOUND | alerts + sightings | legacy tables + M13 | coords protected |
| ADOPTION | publications/applications | M09 | same pet id |
| FOSTER | homes + placements | M10/M15 | |
| SERVICES | providers/offerings | M22 | |
| BOOKINGS | slots + bookings | M23 | pet_id ≠ grant |
| DAYCARE | stay extension of booking | M22/M23 | public consent default NO |
| VETERINARY | clinic M12, professional M28, offering M22 | no 4th entity | |
| REPUTATION | reviews | M21 | |
| EVENTS | M18 | | |
| DONATIONS | campaigns + direct transfer | M17 | 0% |
| MODERATION | cases | M04 | |
| ADMIN | assignments | M04 | |
| BRAND STUDIO SUPPORT | campaign/placement tables when built | M29 on M19 | no implement now |
| LEGAL CONSENTS | acceptances | M01 | versioned |
| AUDIT | append-only | M07 | retention |

**No** M30 para VitaCora. **No** M24/M25 transaccional en V1. **No** `passport_*`. **No** `account_type`. **No** `AccountType.TEEN`.

Folder proposal (03B, no borrar git history):

```text
supabase/migrations/                    -- HISTORICAL freeze (001-082) — do not replay on new project
supabase/canonical/baseline/            -- new initial schema (version 1000+ or separate project)
supabase/canonical/migrations/          -- forward-only from clean baseline
```

Preferible: **proyecto Supabase nuevo** con migraciones partiendo de `1000_canonical_baseline.sql` (o equivalente), sin copiar 001–082.

---

## 7. Security / RLS Strategy

```text
UI never grants
ActiveContext never grants
JWT account_type never grants (column absent)
RLS/RPC checks:
  auth.uid()
  platform role
  org membership + org permission
  pet responsibility + pet/vitacora permission
  grant row for third parties
Least privilege; SECURITY DEFINER helpers with fixed search_path
Tenant = organization_id or person_id as holder
```

Baseline nuevo:

- Default deny table grants; RPC-only writes for pet/vitacora/org/admin.
- Una familia de helpers `_acl_*` (no 30 copias).
- Grants VitaCora evaluados server-side; refresh de sesión no amplía scope.
- Mensajes institucionales: autorización por membership de la **entidad** de la conversación.
- Public RPCs: proyección redactada; nunca `pet_id` interno ni coords exactas.
- **Age capability** se evalúa **además** de pet/org permissions (deny puede ganar). Guardian link no implica pet ACL.

Políticas legacy duplicadas: **no portar**; reescribir.

---

## 8. Migration / Cutover Strategy

**Diseño, no ejecución.**

### 8.1 Backends

| Backend | Rol |
|---------|-----|
| Actual | Referencia, QA, rollback temporal. No destruir primero. |
| Nuevo staging/canonical | Baseline Master v1.2 / D01 v1.3 |
| Prod futura | Proyecto separado; no = staging |

### 8.2 Datos

Preproducción. **No asumir migrate-all.**

| Clase | Estrategia |
|-------|------------|
| CATALOG/CONFIG | Reseed (location AR, care types, permission catalog, legal versions) |
| QA DATA | Discard / recreate |
| REAL DATA | No hay evidencia en esta sesión de prod real; si staging tiene usuarios, **reportar en 03B** con count read-only |
| AUTH USERS | No mover ahora; 03B decide seed vs invite-only |

`CURRENT_DATA_MIGRATION_REQUIRED = NO` (preprod; catalogs reseed). Si 03B descubre usuarios reales de piloto, reevaluar **sin** copiar Passport snapshots.

### 8.3 Orden futuro (no ejecutar)

1. Inventario live read-only del remoto actual (schema_migrations + pg_catalog).
2. Crear proyecto Supabase **nuevo** (staging canónico).
3. Baseline SQL canónico + seed catálogos.
4. RLS/RPC núcleo: auth, org, pet, media, vitacora grants.
5. Config Android/web/KMP → nuevo URL/keys (no en 03A).
6. Smoke tests.
7. Retener old backend.
8. Retiro eventual.

### 8.4 Performance (piloto pequeño, diseño adulto)

Evitar: jsonb mega-records (`pets.vaccinations` ilimitado), N+1 de grants, audit sin partición/retención, RLS recursivo en memberships, denormalizar `owner_id` como autoridad. Indexes en `(pet_id, status)`, grant grantee, `expires_at`.

---

## 9. Risk Register

| ID | Riesgo | Mitigación |
|----|--------|------------|
| R1 | Aplicar 039–052 sobre staging actual | No hacer; new project |
| R2 | Perder páginas públicas web | Rebuild `public_code` + RPCs en 03B con contrato estable |
| R3 | Dual write Android durante cutover | Un backend a la vez; old retained |
| R4 | Copiar Passport = duplicar verdad | No data-migrate snapshots |
| R5 | AppMode leftover rompe nav | Cutover Android usa solo ActiveContext |
| R6 | Legal erasure mal implementado | PENDING; solo soft-delete negocio |
| R7 | Checkout M25 accidental | DROP del baseline V1 |
| R8 | Org mostrada N veces | Resolver contextos por `organization_id` único |
| R9 | Drift 078–082 vs remoto | Live inventory 03B |
| R10 | WIP ajeno pisado | Esta etapa no toca código |
| R11 | Codificar umbrales 13/16 como si fueran ley | Schema extensible; `LEGAL_REVIEW_REQUIRED`; no hardcode legal |
| R12 | Guardian auto-OWNER de la mascota | Separar tablas/ACL; tests 03B |
| R13 | Adulto lee todos los DMs del adolescente | Default NO; pending legal/product |
| R14 | Targeting comercial por edad adolescente | M29 exclude protected segment |

---

## 10. Open Legal Constraints

| Item | Estado |
|------|--------|
| Paquete legal completo (textos, versiones, menores, profesionales) | Pendiente post-03A |
| Derecho de supresión / anonimización / retención obligatoria | `PRIVACY_ERASURE_POLICY = PENDING_LEGAL_FINALIZATION` |
| Eliminación de cuenta vs registros de terceros (vet, org, denuncias) | Pendiente |
| Consentimientos más allá de terms/privacy (salud, imagen pública guardería, geolocalización precisa) | Schema debe **soportar** versionado; textos no en 03A |
| Tutorial ≠ consentimiento | Registrado |
| Cuentas adolescentes / consentimiento de menores / guardian | `LEGAL_MINOR_CONSENT_POLICY = PENDING_LEGAL_FINALIZATION` |
| Cortes 13 / 16 / 18 como obligación jurídica exacta | **No fijar como ley.** Producto usa bandas operativas; legal review posterior |
| DNI / documentos de age assurance | Data minimization; no almacenar sin necesidad jurídica demostrada |

El baseline **debe** tener `legal_acceptances` auditable (quién consintió, para quién, versión, timestamp, revocación). **No** implementar política irreversible de borrado personal ni consentimiento de menores hasta definición legal.

**Enmienda de gobierno:** Master v1.2 y D01 v1.3 **no se editaron** en esta etapa (restricción REBASE-03A: un documento). `GOVERNANCE_AMENDMENT_REQUIRED = YES` (§12.O).

---

## 11. Exact REBASE-03B Plan

Objetivo 03B: **DDL canónico en proyecto nuevo + seeds**, todavía sin cutover de apps ni APK, salvo que se autorice explícitamente.

1. **Read-only remote snapshot** (schema_migrations, table list). Documentar max version live. No repair, no push.
2. **Crear proyecto Supabase staging canónico** (cuenta/org LeoVer). Separar de actual.
3. **Congelar** `supabase/migrations/001-082` como historia; no replay.
4. **Escribir baseline** (nuevo directorio/proyecto) en este orden de dominio:
   AUTH/PERSON (**incl. age/protection dimension, no AccountType**) → PLATFORM ROLES → GUARDIAN LINKS → MEDIA M05 → LOCATION CATALOG → ORGANIZATIONS/MEMBERSHIPS/CAPABILITIES → PET IDENTITY/RESPONSIBILITY/PERMISSIONS → CUSTODY PROJECTION → VITACORA (profile, moments, grants, proposals, integration links) → HEALTH DECLARED + M28 professional (sin passport) → SOCIAL M19 → MESSAGING M20 entity (**+ teen DM restrictions**) → LOST/FOUND+M13 → ADOPTION M09 → FOSTER M10 → SERVICES M22 → BOOKINGS+DAYCARE M23 → REPUTATION M21 → EVENTS M18 → DONATIONS M17 → MODERATION/ADMIN → CONSENTS/TUTORIALS (**TEEN_ACCOUNT_TUTORIAL**) → AUDIT M07.
   **Omitir:** M25 transactional, M24, `passport_*`, `account_type`, `AccountType.TEEN`, `payment_intents`.
   **Reservar** tablas mínimas M29 (placement) solo si no bloquean; si no, diferir M29. Protected-segment flag para ads.
5. **RLS/RPC** según §7. Tests SQL de: multi-OWNER, created_by ≠ owner, org pet, grant scopes, NONE still books pet, proposal no direct write, moment private default, daycare public consent default NO, leave-org keeps conversation, **guardian ≠ pet owner**, **teen cannot FULL_SHAREABLE without extra capability**, **under-13 cannot create autonomous account**, **18th birthday keeps same user_id**.
6. **Seed:** Argentina location, permission catalog, care types, legal doc versions placeholder.
7. **Mapear contratos web públicos** (`get_public_pet` → nuevo nombre) sin cambiar paths HTTP todavía.
8. **No** Android cutover, **no** APK, **no** borrar backend viejo.
9. Entregar: schema files + matriz de RPCs + informe 03B.
10. Actualizar ADR-016 puntero a Master v1.2 / D01 v1.3 (doc only).
11. **Post-03A / pre- o post-03B documental:** enmendar Master v1.2 y D01 v1.3 con flags §12.O (tarea de gobierno, no este archivo único).

**Fuera de 03B:** implementación UI VitaCora, billing, paquete legal textos, erasure, M29 creativo, iOS paridad, **verificación documental invasiva de edad**.

---

## 12. Addendum — Teen Accounts / Minor Safety Architecture

**Estado:** decisión de producto incorporada al análisis REBASE-03A. **No SQL. No código.**
Umbrales 13 / 16 / 18 son **bandas operativas de producto**. Los cortes de consentimiento jurídico exactos = `PENDING_LEGAL_FINALIZATION`. **No** tratar proyectos normativos como ley vigente.

### 12.A Producto

| Banda operativa | Cuenta |
|-----------------|--------|
| UNDER_13 | **No cuenta autónoma.** |
| 13–15 | Cuenta Adolescente LeoVer; protecciones reforzadas; adulto responsable vinculado. |
| 16–17 | Cuenta Adolescente LeoVer; mayor autonomía; sigue siendo menor protegido. |
| 18+ | Cuenta adulta estándar. |

Transición 18+: misma PERSON, mismo `user_id`, mismas mascotas, misma VitaCora, mismo historial. Solo cambia protection/capability state.

### 12.B No crear AccountType

**Prohibido:** `AccountType.TEEN`, `AccountType.MINOR`, o cualquier identidad humana distinta.

Identidad = **PERSON**. Edad/protección = dimensión de la persona.

Encaje conceptual (nombres físicos **no** finales):

```text
PERSON
  birth_date (date-only) or equivalent
  → derived age_band (UNDER_13 | TEEN_13_15 | TEEN_16_17 | ADULT_18_PLUS)
  → protection_state
  → capability restrictions (server-enforced)
  → guardian relationships (separate graph)
```

`age_band` se **deriva** (fecha + recompute), no se elige como rol de identidad. No vive en JWT como autoridad exclusiva; el server revalida.

**Hoy en repo:** `users` no tiene `birth_date`. `birth_date` es de mascota (Passport). **REBUILD** en baseline nuevo.

### 12.C Guardian relationship

Grafo **persona–persona**, no pet:

```text
MINOR PERSON  ←guardian_link→  ADULT PERSON
  kind: guardian / adult responsible
  invitation → acceptance
  verification_state (aligns with age assurance)
  starts_at / ends_at
  audit events
  revocation / replacement
  legal_relationship_metadata (mínimo; sin DNI por defecto)
```

Data minimization: **no** almacenar documento de identidad salvo que legal lo exija después.

### 12.D Guardian ≠ pet owner

```text
guardian(Carolina, Mateo)  ≠  OWNER(Toby)
Mateo puede ser OWNER de Toby
Carolina NO se vuelve OWNER de Toby por ser guardian
Pet responsibility solo vía M08 explícito
```

Si el producto quiere que Carolina también cuide a Toby, se asigna `AUTHORIZED`/`OWNER` en M08 aparte.

### 12.E Teen OWNER

Un adolescente **puede** ser OWNER/AUTHORIZED de una mascota.

```text
PET OWNER PERMISSION  ≠  LEGAL/AGE CAPABILITY
```

La capa de edad puede **denegar** acciones aunque M08 las permita (p. ej. transferir responsabilidad, FULL_SHAREABLE). Evaluación: `pet_permission AND age_capability AND (guardian_approval if required)`.

### 12.F Protecciones por defecto (sin dark patterns)

Capacidad de arquitectura (defaults teen; no opt-out engañoso):

- perfil privado por defecto;
- VitaCora privada por defecto;
- ubicación precisa nunca pública;
- mensajería restringida;
- controles reforzados de contacto;
- menos exposición de teléfono/email/nombre completo;
- restricciones de descubrimiento;
- protección frente a interacción adulta inapropiada;
- moderación/reporting reforzado.

Relajar protecciones no debe ser un flujo de un tap ni copy que minimice riesgo.

### 12.G Matriz conceptual de acciones sensibles

**No definitiva.** Celdas con `LEGAL_REVIEW_REQUIRED = YES` no se implementan como ley.

Leyenda: `N` = no; `Y` = sí permitido; `G` = requiere guardian/adulto vinculado; `R` = restringido / extra step; `P` = pending legal.

| ACTION | 13_15 | 16_17 | 18_PLUS | GUARDIAN_REQUIRED | LEGAL_REVIEW_REQUIRED |
|--------|-------|-------|---------|-------------------|------------------------|
| Crear cuenta autónoma | N (UNDER_13: N) | Y | Y | 13–17: Y para alta | YES |
| Perfil público amplio | N | R | Y | 13–15 likely | YES |
| VitaCora view (propia, privada) | Y | Y | Y | N | NO |
| VitaCora FULL_SHAREABLE a tercero | N / G | R / G | Y | 13–17 default | YES |
| Compartir salud / datos sensibles | G | R | Y | 13–15 | YES |
| Contratación/pago de servicios | G | R | Y | likely 13–17 | YES |
| Transferir responsabilidad de mascota | G | R | Y | 13–15 | YES |
| Crear/administrar organización | N | N / P | Y | — | YES |
| Activar perfil profesional/comercial | N | N / P | Y | — | YES |
| Consentimiento publicación pública sensible | G | R | Y | 13–15 | YES |
| Compartir ubicación precisa | N pública; G interna | N pública; R | adult policy | 13–17 for share | YES |
| Donaciones / operaciones económicas | G / N | R | Y | 13–17 | YES |
| Cambios importantes de privacidad (relajar defaults) | G | R | Y | 13–15 | YES |
| OWNER de mascota (vínculo M08) | Y | Y | Y | N (≠ guardian) | NO (producto) |
| Lost/Found publicar (sin coords exactas públicas) | R | Y | Y | maybe 13–15 | YES |
| DM de desconocidos | N default | R | Y | safety controls | YES |
| Contacto institucional (org inbox) | Y restringido | Y | Y | N auto-read | YES |

UNDER_13: ninguna fila de cuenta autónoma aplica.

### 12.H Mensajería (M20)

Hoy: conversación = dos `users`; `ORGANIZATION` es label; hay blocks.

Diseño adicional teen:

- inbound de desconocidos restringido / request-to-chat;
- contacto institucional (entidad) permitido con protecciones;
- block + report reforzado + audit;
- no exponer teléfono/email en perfil ni en preview;
- safety controls configurables;
- **guardian no lee automáticamente todos los DMs** sin definición legal/producto explícita (default: NO).

### 12.I Location

Teen: `precise location = PROTECTED`. Nunca pública directa.

Lost/Found puede usar coords internamente (matching/alertas) según finalidad y permiso; proyección pública aproximada.

Protección extra en copy/media que revele: domicilio, escuela, rutina, tiempo real, lugares frecuentes. Moderación/heurística de contenido; no geocercas invasivas en V1 sin legal.

### 12.J Advertising / Brand Studio

Teen = **protected segment**. No targeting sensible. Edad adolescente **no** habilita segmentación comercial individualizada.

Política M29 production: revisión comercial/legal previa. Kill switch / exclude teens de audiencias de anuncios.

### 12.K Tutorial

```text
TEEN_ACCOUNT_TUTORIAL
SKIPPABLE = YES
REOPENABLE = YES
≠ legal consent
```

Contenido simple: privacidad, seguridad, contactos, adulto responsable, por qué algunas acciones piden intervención adulta.

### 12.L Legal consent

Extender `legal_acceptances` conceptualmente:

- terms version; privacy version;
- minor assent when applicable;
- guardian consent when required;
- who consented; for whom;
- document/version; timestamp;
- withdrawal/revocation; evidence/audit.

`LEGAL_MINOR_CONSENT_POLICY = PENDING_LEGAL_FINALIZATION`
No implementar en 03A.

### 12.M Age assurance (extensible)

V1 **no** exige verificación documental invasiva.

```text
SELF_DECLARED → GUARDIAN_CONFIRMED → VERIFIED
```

(o equivalente). Documentos: no almacenar sin necesidad jurídica/operativa demostrada.

### 12.N Account transition

Job/regla: al cruzar banda (cumpleaños), **recompute** `age_band` / `protection_state`. Sin nueva cuenta. Pets y VitaCora intactos. Revocar capacidades adultas no aplica hacia atrás; **ganar** autonomía de 16 y 18 según política (legal review).

### 12.O Governance follow-up

Master v1.2 y D01 v1.3 **no se modificaron** (REBASE-03A = un documento de arquitectura).

`GOVERNANCE_AMENDMENT_REQUIRED = YES`

Enmendar después:

```text
TEEN_ACCOUNT_SUPPORT = YES
UNDER_13_AUTONOMOUS_ACCOUNT = NO
TEEN_13_15_PROTECTED = YES
TEEN_16_17_PROTECTED = YES
GUARDIAN_MODEL = YES
GUARDIAN_IS_PET_OWNER = NO
AGE_CAPABILITY_SEPARATE_FROM_PET_PERMISSION = YES
MINOR_LEGAL_POLICY = PENDING_FINAL_LEGAL_REVIEW
```

---

## Appendix A — Reference counts (this session)

| Item | Value |
|------|-------|
| Local migrations | 82 |
| Unique `public.*` tables parsed from CREATE TABLE | 219 |
| AccountType files / hits | 174 / ~501 |
| AppMode files / hits | 4 / ~12 |
| Passport files / hits | 122 / ~1911 |
| Live remote query | **not performed** |
| Documented remote max | 077 (gap 039–052) |
| Code/SQL/remote changed this stage | NO |
| Teen/guardian model in current SQL | **ABSENT** |

## Appendix B — Dual-table legacy pairs (DROP left / KEEP-or-REBUILD right)

| Legacy (001–012) | Later Mxx |
|------------------|-----------|
| `conversations` / `messages` | `m20_*` |
| `service_bookings` | `m23_bookings` |
| `shop_products` / `payment_intents` | `m25_*` (then DROP from V1) |
| `posts` | `m19_social_posts` |
| `donation_campaigns` | `m17_donation_campaigns` |
| `community_events` | `m18_community_events` |
| `pet_clinical_records` | M28 professional records |
| `shelters` | M03 org + M16 profiles |
| `foster_homes` (early) | M10 profiles |

---

**Fin REBASE-03A (+ addendum teen/minor safety).** No SQL emitido. No backend creado. No commit. Master v1.2 / D01 v1.3 sin editar (`GOVERNANCE_AMENDMENT_REQUIRED = YES`).
