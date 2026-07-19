# LEOVER — M02 Auditoría inicial (Usuarios, Roles y Permisos)

**Módulo:** M02 — Usuarios, Roles y Permisos  
**Etapa:** 1 — Auditoría y diseño  
**Fecha:** 2026-07-15  
**Rama:** `m02/usuarios-roles-permisos-auditoria`  
**Dependencia:** M01 aprobado a nivel código y documentación (staging/remoto de M01 **no** afirmado aquí)  
**Backend oficial:** Supabase (ADR-0001)  
**Alcance de esta etapa:** inventario y diseño; **sin** migraciones aplicadas, **sin** cambios de RLS, **sin** roles/permisos nuevos, **sin** pantallas nuevas, **sin** M03  

**Documentos de entrada (orden leído):**

1. [`docs/01-producto/D01-Modulos-y-Orden.md`](../01-producto/D01-Modulos-y-Orden.md)  
2. [`docs/02-arquitectura/M00-cierre-final.md`](M00-cierre-final.md)  
3. [`docs/02-arquitectura/M01-cierre-final.md`](M01-cierre-final.md)  
4. [`docs/03-modulos/M01-Identidad-y-Autenticacion.md`](../03-modulos/M01-Identidad-y-Autenticacion.md)  
5. ADR-0001 … ADR-0005  
6. [`docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`](../03-modulos/M02-Usuarios-Roles-y-Permisos.md)  

---

## 0. Estado Git y calidad baseline

| Ref | Nota |
|-----|------|
| Rama | `m02/usuarios-roles-permisos-auditoria` (creada desde `m01/etapa-4-recuperacion-seguridad-eliminacion`) |
| HEAD tip | `6a4ee8b` — spec M01 Etapa 4 |
| Working tree | Conserva implementación M01 Etapa 4 **aún no consolidada en commit** (auth recovery/delete, docs M01 cierre, Edge Function, etc.) + spec M02 |
| WIP GPS/mapas/pagos | **No** mezclado (`wip/gps-mapas-pagos` intacto) |
| Stash | `stash@{0}: backup/pre-m01-identidad-autenticacion` (preservado) |

### Baseline build / tests / lint (estado inicial Etapa 1)

| Control | Resultado |
|---------|-----------|
| `testDebugUnitTest` | **83** / 0 failures (§0.1) |
| `assembleDebug` | SUCCESS (§0.1) |
| `lintDebug` | 0 errors (§0.1) |

> Esta etapa **no modifica funcionalidad**. El baseline documenta el estado del árbol actual (incluye cambios locales de M01 Etapa 4).

### §0.1 Resultado de la corrida

| Control | Resultado al cierre de esta auditoría |
|---------|----------------------------------------|
| `testDebugUnitTest` | **83** tests, **0** failures |
| `assembleDebug` | **SUCCESS** (~23m 42s en corrida baseline) |
| `lintDebug` | **SUCCESS**, **0** errors |

*Nota:* baseline del working tree que incluye cambios locales de M01 Etapa 4 aún no committeados. No implica validación remota de M01.

---

## 1. `public.users` — definición actual

### Columnas efectivas (001 + alters)

Origen: `001_initial_schema.sql`, `005_fase1_community.sql`, `006_sprints_abcd.sql`, `009_profile_privacy.sql`.

| Columna | Tipo / default | Rol |
|---------|----------------|-----|
| `id` | uuid PK → `auth.users` CASCADE | Identidad = Auth |
| `email` | text not null | Duplicado de Auth (M01 también) |
| `name` | text not null | Nombre visible (no hay `display_name` / `username`) |
| `account_type` | text not null default `PERSON` | **Capacidad de negocio**, sin CHECK SQL |
| `profile_image_url` | text null | URL, no `avatar_path` |
| `bio` | text null | Perfil |
| `location_text` | text null | Localidad libre (sin city/province/country) |
| `phone` / `phone_public` | text / bool | Contacto; `phone_public` poco usado en UI |
| `email_verified` | bool | Cruza M01 |
| `foster_home_active` | bool | Capacidad M15 anticipada |
| `active_modules` | text[] default `{}` | Feature toggles cliente |
| `reputation_score` | int default 0 | M21 anticipado |
| `profile_private` | bool default true | Privacidad binaria |
| `created_at` / `updated_at` | timestamptz | Auditoría temporal |

**Ausentes vs spec M02:** `username`, `display_name`, `avatar_path`, `city`/`province`/`country_code`, `locale`, `timezone`, `onboarding_status`, `account_status`.

### Triggers `handle_new_user`

| Migración | Comportamiento |
|-----------|----------------|
| `004_auth_user_profile_trigger.sql` | Alta `public.users` desde metadata |
| `009_profile_privacy.sql` | + `profile_private = (account_type = 'PERSON')`; lee `account_type` de metadata |
| `014_user_consents.sql` (local, **no aplicada remoto**) | Fuerza `PERSON` + `profile_private=true`; consent opcional |

**Hallazgo:** 014 alinea registro M01 (sin AccountType en signup) pero **ignora** metadata `account_type` que 004/009 usaban. Compatible con D-M01-05; las capacidades de negocio quedan para M02/M03, no para el alta Auth.

---

## 2. Migraciones relacionadas (inventario)

| # | Archivo | Relevancia M02 |
|---|---------|----------------|
| 001 | `001_initial_schema.sql` | Crea `users` + RLS permisivo |
| 002 | `002_storage.sql` | Bucket `leover` abierto |
| 004 | `004_auth_user_profile_trigger.sql` | Trigger perfil |
| 005 | `005_fase1_community.sql` | `active_modules` |
| 006 | `006_sprints_abcd.sql` | `reputation_score`, badges |
| 008 | `008_friendships.sql` | Relación social (privacidad app) |
| 009 | `009_profile_privacy.sql` | `profile_private` + trigger |
| 012 | `012_fase1_to_4_closure.sql` | blocks / reports |
| 014 | `014_user_consents.sql` | Consents M01; reescribe trigger |

**No existen tablas:** `platform_roles`, `permissions`, `role_permissions`, `user_role_assignments`, `user_capabilities`, `user_privacy_settings`, `user_status_history`.

---

## 3. Modelos Android

### `User` — `data/model/User.kt`

Campos alineados a columnas DB (+ `badges`, `petIds` locales). Mezcla:

- identidad/perfil (`name`, `bio`, avatar URL, location, privacy);
- Auth cruzado (`email`, `emailVerified`);
- capacidades (`accountType`, `activeModules`, `fosterHomeActive`);
- social/reputación.

### `AccountType` — `data/model/Enums.kt`

`PERSON | SHELTER | VET | TRAINER | WALKER | SHOP | FOSTER_HOME`.

**No es** rol de plataforma M02 (`USER`/`MODERATOR`/`ADMIN`/`SUPERADMIN`). Es **capacidad de negocio** (alineable a `UserCapability` futuro / M03).

### `AuthUser` — `domain/auth/AuthUser.kt`

Identidad M01 (`id`, `email`, `emailVerified`, sesión). Correcto para Auth; **todavía** `AuthRepository` devuelve `User?` y Session mantiene `currentUser: User?`.

### Privacidad / permisos domain

| Pieza | Path | Notas |
|-------|------|-------|
| `ProfilePrivacy` | `domain/ProfilePrivacy.kt` | Relación SELF/PUBLIC/FRIENDS/LOCKED; **solo cliente** |
| `RolePermissions` | `domain/RolePermissions.kt` | Facade UI por AccountType / AppMode |
| `ModulePermissions` | `domain/ModulePermissions.kt` | `canModerateContent` ↔ `LeoverModule.ADMIN` en modules |
| `LeoverModule` | `domain/LeoverModule.kt` | Catálogo de módulos producto |

**Ausente:** `domain/user/`, `domain/authorization/`, catálogo de permission codes, deny-by-default formal.

---

## 4. Repositorios

### `UserRepository` (mock + Supabase)

```text
getUser / createUser / updateUser / searchUsers / observeUser / observeUsers
```

- Update Supabase escribe `UserUpdateRow` completo (incluye `account_type`, `active_modules`, `profile_private`, etc.).
- Search por `name` (ilike); **no** username.
- Sin check de disponibilidad, onboarding, roles ni proyección pública.

### Auth vs User

Registro M01 crea fila vía trigger (+ opcional `createUser`). Perfil social se lee/actualiza vía `UserRepository`. Duplicación email/name entre Auth metadata y `public.users`.

---

## 5. Pantallas y ViewModels de perfil

| UI | VM | Editable / comportamiento |
|----|-----|---------------------------|
| `ProfileScreen` | `ProfileViewModel` | Propio; logout; seguridad M01; **Moderación** visible sin gate |
| `EditProfileScreen` | `EditProfileViewModel` | name, bio, location, phone, **accountType**, profilePrivate, avatar |
| `UserPublicProfileScreen` | `UserPublicProfileViewModel` | Vista filtrada por `ProfilePrivacy` |

**Onboarding:** no hay `ui/screens/onboarding/`, ni `OnboardingViewModel`, ni `ProfileSetupRequired`. Tras M01 (auth + consent) → MAIN.

---

## 6. Navegación

| Ruta | Uso |
|------|-----|
| `profile` / `edit_profile` / `user_profile/{id}` | Perfil |
| `admin_moderation` | Moderación abierta |
| Gates sesión | Loading / LoggedOut / LegalConsent / PasswordReset / LoggedIn (M01) |

Sin gate de perfil incompleto.

---

## 7. Administración / moderación existente

- `AdminModerationScreen` + `AdminModerationViewModel`: lista reportes, actúa vía `PlatformRepository`.
- **Sin** chequeo `canModerateContent` / rol en UI.
- Sin roles SQL; “admin” ≈ `LeoverModule.ADMIN` en `active_modules` (actualizable por el **propio** usuario vía `updateUser`).

**Riesgo alto:** autoservicio de capacidades + moderación ungated.

---

## 8. Storage avatares

| Ítem | Estado |
|------|--------|
| Path app | `StoragePaths.userAvatar(userId)` → `users/{userId}/avatar.jpg` |
| Bucket | `leover` (`002_storage.sql`, `public = true`) |
| Políticas Storage | SELECT/INSERT/UPDATE/DELETE autenticados **sin** ownership por path |
| DB | Guarda URL (`profile_image_url`), no path |

---

## 9. RLS (solo lectura — no modificado en esta etapa)

### `public.users` (001, sin endurecer después)

| Policy | Regla |
|--------|-------|
| SELECT authenticated | `using (true)` → **fila completa** a cualquier logueado |
| INSERT own | `auth.uid() = id` |
| UPDATE own | `auth.uid() = id` **sin** restringir columnas |
| DELETE | Ausente |

`profile_private` **no** se usa en RLS. Email/phone/modules visibles vía API a pares autenticados.

### Recursión

Políticas de `users` no recursivas. Chat (`conversations`/`participants`) tiene riesgo clásico entre tablas; fuera del núcleo M02 pero a tener en cuenta.

---

## 10. Tests existentes (perfil / permisos)

| Suite | Cobertura M02 |
|-------|---------------|
| `SupabaseMappersTest` | `parseUser` / accountType |
| Auth / Session / M01 | Identidad, no perfil M02 |
| ProfilePrivacy / username / roles / onboarding | **Ausentes** |

Baseline actual del árbol (con M01 E4 local): ~83 unit tests históricos; confirmar en §0.1.

---

## 11. Duplicación / solapamiento con M01

| Concepto | M01 | M02 (hoy / futuro) |
|----------|-----|---------------------|
| UUID / email / sesión / consent / delete | Propietario | No tocar |
| `email` en `public.users` | Spejado | Evitar exponer en perfil público |
| `email_verified` | Auth | Columna espejo; no lógica de roles |
| `name` en signup metadata | Seed perfil | Evolucionar a `display_name` + `username` |
| `AuthUser` vs `User` | AuthState | Separar: Session no debe exigir perfil social completo |
| AccountType en register | Removido (D-M01-05) | Capacidades vía M02/M03, no signup |

---

## 12. Diferencias contra especificación M02

| Spec M02 | Repo hoy |
|----------|----------|
| Perfil tipado (`display_name`, username, avatar_path, …) | Parcial (`name`, URL, sin username) |
| Onboarding + `ProfileSetupRequired` | Ausente |
| Privacidad granular + tabla settings | Solo `profile_private` + lógica cliente |
| Account status ACTIVE/… | Ausente |
| Roles plataforma + permissions + assignments | Ausente |
| `has_permission` / deny-by-default servidor | Ausente |
| Proyección pública / RLS allowlist | SELECT abierto |
| Capability RESCUER/ORG/PRO/BUSINESS | Solo `AccountType` / modules |
| UI onboarding / roles admin seguro | No |
| Storage path ownership | No |

---

## 13. Riesgos

1. **RLS SELECT abierto** en `users` (PII a cualquier autenticado).  
2. **UPDATE own sin allowlist** → auto-cambio `account_type` / `active_modules` (escalada de capacidades).  
3. **Moderación ungated** en Perfil.  
4. **Storage** sin ownership por path.  
5. Confundir **AccountType** (negocio) con **PlatformRole** (admin).  
6. Onboarding inexistente → usernames/perfiles incompletos sin gate.  
7. 014 no aplicada en remoto: diseño M02 no debe asumir consentimiento/remota lista.  
8. Mezclar debt de M03 (orgs) en M02 perfil.

---

## 14. Propuesta de modelo final (diseño — no implementar aún)

### Principios

1. Reutilizar `public.users` como tabla de perfil (no crear segunda identidad).  
2. Separar **Auth (M01)** / **Perfil (M02)** / **Roles plataforma (M02)** / **Capacidades negocio (contrato M02, activación M03+)**.  
3. Deny-by-default; UI no es seguridad.  
4. Migraciones correctivas **solo** en Etapas 2–4 tras aprobación; Etapa 1 no aplica nada.

### Columnas a añadir a `public.users` (propuesta)

```text
username citext unique null → luego not null tras onboarding
display_name text          -- migrar semántica desde name o alias
avatar_path text null      -- preferir path; URL derivada
city / province / country_code
locale / timezone
onboarding_status text     -- NotStarted|InProgress|Completed|Blocked
account_status text        -- ACTIVE|RESTRICTED|SUSPENDED|BANNED
```

Mantener temporalmente `name` como espejo de `display_name` para no romper clientes; plan de convergencia en Etapa 3.

### Tablas nuevas (si no hay equivalentes — Etapa 4)

```text
platform_roles / permissions / role_permissions / user_role_assignments
user_privacy_settings   -- o columnas JSON controladas
user_status_history
user_capabilities       -- stub RESCUER/ORG/PRO/BUSINESS sin activar módulos
```

Helpers: `has_permission(code)` SECURITY DEFINER, `search_path` fijo; **sin** roles solo en `raw_user_meta_data`.

### RLS (Etapa 3/4 — diseño)

- Vista o RPC `public_user_profile` allowlist (sin email/phone/modules).  
- UPDATE own con columnas permitidas (bloquear `account_status`, `reputation_score`, `active_modules` sensibles, roles).  
- SELECT: propia fila completa; ajenas solo vía proyección pública / amistad según política.  
- Storage: políticas path `users/{auth.uid()}/**`.

### Android (Etapas 2–3)

Reutilizar:

- `UserRepository` → extender contrato (no duplicar repo).  
- `ProfilePrivacy`, pantallas perfil/edición/público.  
- `SessionViewModel` gates: añadir `ProfileSetupRequired` **después** de LegalConsent.  
- `RolePermissions`/`ModulePermissions` → reclasificar como **capacidades UI**, no roles plataforma.  
- Nuevos: `domain/user/`, `domain/authorization/`, onboarding UI, `PermissionRepository`.

### AccountType

- Dejar de editarse libremente en Etapa 3+ (o solo como capability request).  
- Mapear a `UserCapability` / M03 membership; no a `PlatformRole`.

---

## 15. Archivos a crear / modificar (plan, no Etapa 1)

### Crear (Etapas 2–4)

| Área | Candidatos |
|------|------------|
| Domain | `domain/user/*`, `domain/authorization/*` |
| Data | Extensiones `UserRepository`, mappers; `PermissionRepository` |
| UI | `ui/screens/onboarding/*` |
| SQL | Migración `015+` perfil/username/status; luego roles |
| Docs calidad | Pruebas RLS/username |
| Tests | Validators, privacy, authz, VMs |

### Modificar

| Archivo | Cambio previsto |
|---------|-----------------|
| `User.kt` / mappers | Campos M02; separar proyección pública |
| `EditProfile*` | Username; quitar o restringir AccountType self-serve |
| `ProfileScreen` | Gate moderación por permiso real |
| `SessionViewModel` / Nav | Onboarding gate |
| `001` RLS vía **nueva** migración (no editar 001 aplicado) | Endurecer SELECT/UPDATE |
| `002` Storage vía migración nueva | Ownership path |
| `RolePermissions` | Documentar como capabilities |

### No tocar en M02

- Flujos Auth M01 consolidados.  
- GPS/mapas/pagos.  
- M03 orgs reales.  
- service_role en Android.

---

## 16. Plan por etapas (siguiente trabajo autorizado aparte)

| Etapa | Objetivo | Parar cuando |
|-------|----------|--------------|
| **1 (esta)** | Auditoría + diseño | `M02-auditoria-inicial.md` |
| **2** | Contratos dominio, validadores username, authz deny-by-default, tests | Cierre Etapa 2 |
| **3** | Migración perfil/username/privacidad/RLS proyección; onboarding UI | Cierre Etapa 3 |
| **4** | Roles/permisos/assignments/RPC/status history; gate admin | Cierre Etapa 4 |
| **5** | Calidad, staging documentado, `M02-cierre-final.md` | Cierre M02 |

---

## 17. Decisiones que requieren aprobación

1. **¿Reutilizar `name` como `display_name` o agregar columna y deprecar?**  
2. **¿Username nullable hasta Completar onboarding o forzar en migración de datos existentes?**  
3. **¿`account_type` self-service se elimina en Etapa 3 o queda read-only?**  
4. **¿Privacidad: nuevas columnas vs tabla `user_privacy_settings`?**  
5. **¿Roles en tablas normalizadas desde el día 1 o fase intermedia solo `platform_role` en users + seed?** (Spec recomienda catálogo + assignments.)  
6. **¿Cuándo endurecer RLS SELECT (bloquea clientes que lean email ajeno hoy)?**  
7. **¿Storage ownership obligatorio en la misma etapa que avatar_path?**  
8. **¿Moderación en app se oculta hasta M02-E4 / M04 con permiso servidor?** (Recomendado: sí.)  
9. **014 remota:** M02 E3 no debe desplegarse sobre supuestos de consent remoto sin evidencia.  
10. **Claims JWT vs consulta tabla** para permisos en Android (refresco de sesión).

---

## 18. Reutilización explícita (evitar duplicar)

| Reutilizar | No duplicar |
|------------|-------------|
| `public.users` | Segunda tabla “profiles” con mismo UUID |
| `UserRepository` + mock/Supabase | Repo paralelo de perfil |
| `ProfilePrivacy` + pantallas perfil | Nuevo árbol social completo |
| `AuthUser` / gates M01 | Re-auth stack |
| `StoragePaths.userAvatar` | Nuevo esquema de paths |
| `AppResult` / `AppError` / logging M00 | Excepciones ad hoc |
| `RolePermissions` como UI capability | Confundirlo con `PlatformRole` |

---

## 19. Conclusión Etapa 1

El producto ya tiene un **perfil social usable** (edición, privacidad binaria, avatar, perfil público/amigos) sobre `public.users`, pero **no** tiene todavía la identidad de producto M02 (username, onboarding, estados de cuenta) ni la **autorización de plataforma** (roles/permisos/RLS deny-by-default).

M02 debe **extender** lo existente con migraciones correctivas y contratos de dominio, sin reinventar Auth (M01) ni abrir organizaciones (M03).

**Etapa 1 completa.** Esperar aprobación del diseño (§17) antes de Etapa 2. **M03 no iniciado.**
