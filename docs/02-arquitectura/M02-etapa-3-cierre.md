# M02 — Cierre Etapa 3: Perfil, onboarding, privacidad y RLS

**Fecha:** 2026-07-15  
**Rama:** `m02/etapa-3-perfil-onboarding-privacidad-rls`  
**Módulo:** M02 — Usuarios, Roles y Permisos  
**Estado de entrada:** Etapa 2 aprobada (`30e7dc5`)  
**Spec base:** `5cfb1d0` — docs Etapa 3 aprobada  

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Etapa 2 consolidada | `30e7dc5` — `feat: add M02 stage 2 profile and authorization domain contracts` |
| Spec Etapa 3 en rama | `5cfb1d0` |
| Base trabajo implementación | `5cfb1d0` |
| WIP GPS/mapas/pagos | **No** incorporado |

---

## 2. Migraciones (repo; no afirmar remoto)

| Archivo | Contenido |
|---------|-----------|
| `supabase/migrations/015_user_profile_foundation.sql` | `citext`, columnas de perfil en `public.users`, `reserved_usernames`, `user_privacy_settings`, backfills, trigger `handle_new_user` |
| `supabase/migrations/016_user_profile_security.sql` | RPCs allowlist + RLS SELECT propio; revoke INSERT/UPDATE/DELETE cliente en `users` |
| `supabase/migrations/017_profile_avatar_storage.sql` | Bucket privado `profile-avatars`; ownership por path |

**No se crea** una segunda tabla `profiles`.

### Backfills

- `display_name ← name` cuando aplica.
- Sin inventar usernames.
- Sin username → `onboarding_status = IN_PROGRESS`.
- Privacidad desde `profile_private` / `phone_public` (sin inventar `FRIENDS`).

### Funciones RPC

- `is_username_available`
- `complete_profile_onboarding` (atómica; `auth.uid()`)
- `update_my_profile` (allowlist)
- `get_public_user_profile` / `search_public_user_profiles` (allowlist; sin email/teléfono/modules/estados internos ajenos)
- `are_accepted_friends` sobre `friend_connections` (`ACCEPTED`)

### RLS / privilegios

- SELECT directo: solo fila propia.
- Perfiles ajenos: solo RPC.
- UPDATE/INSERT/DELETE directos de `authenticated` sobre `users`: revocados.

---

## 3. Storage

- Path: `users/{uid}/avatar/{filename}`.
- Android: `StoragePaths.userAvatar`, `ProfileAvatarStorageService` (upload → path; signed URL temporal).
- `profile_image_url` permanece como fallback legacy.

---

## 4. Repositorios Android

- Extensión de `UserRepository` (mock + Supabase): own profile, username, onboarding, update allowlist, public get/search, privacy.
- `User` continúa como bridge; dominio `UserProfile` / `PublicUserProfile`.
- `PermissionRepository` stub remoto sin elevación.
- Moderación sigue bloqueada (sin cambios de Etapa 4).

---

## 5. Gate y onboarding

Flujo: Auth → Legal → Profile gate → MAIN / blocked.

| Estado | UI |
|--------|-----|
| Setup required / onboarding blocked | `ProfileOnboardingScreen` (máx. 3 pasos) o acceso bloqueado |
| SUSPENDED / BANNED | `AccountAccessBlockedScreen` |
| RESTRICTED | MAIN con estado autenticado restringido |
| COMPLETED + ACTIVE | MAIN |

`AuthState` ampliado: `ProfileSetupRequired`, `AccountRestricted`, `AccountSuspended`, `AccountBanned`, `OnboardingBlocked`.

---

## 6. UI

- Onboarding: identidad → ubicación/privacidad → avatar/resumen.
- Edit profile vía `updateMyProfile` (sin AccountType).
- Perfil público / búsqueda amigos vía allowlist (`getPublicProfile` / `searchPublicProfiles`).

---

## 7. Pruebas

| Control | Resultado |
|---------|-----------|
| Documentación SQL/RLS | `docs/04-calidad/M02-pruebas-perfil-privacidad-rls.md` |
| Unitarias | **112** tests, **0** failures (`testDebugUnitTest --rerun-tasks`) |
| `assembleDebug` | OK |
| `lintDebug` | OK (0 errores) |

### Pruebas remotas

**No verificadas.** Migraciones `015`–`017` existen solo en el repositorio hasta deploy en staging + checklist SQL/Storage.

---

## 8. Fuera de alcance (cumplido)

- Sin roles reales / `has_permission`.
- Sin M03.
- Sin GPS, mapas ni pagos.
- Sin Hilt, Retrofit, NestJS u otro backend.
- Sin segunda tabla de perfil.

---

## 9. Riesgos y deuda Etapa 4

- Aplicar y validar migraciones en staging.
- Roles/permisos reales + `has_permission`.
- FRIENDS en mock aún no modela grafo completo (RPC sí).
- Sync `email_verified` en `users` ya no es UPDATE cliente (Auth es fuente de verdad).
- Cambio de username post-onboarding diferido.

---

## 10. Checklist de aceptación

- [x] Etapa 2 consolidada
- [x] Rama limpia sin WIP GPS/mapas/pagos
- [x] Sin segunda tabla de perfil
- [x] `display_name` + `name` preservado
- [x] Username único case-insensitive (SQL + mock)
- [x] Reservados bloqueados
- [x] Onboarding / account status persistidos (SQL)
- [x] Privacidad en tabla separada
- [x] UPDATE sensible revocado
- [x] Propio perfil completo; ajeno por allowlist
- [x] Sin filtrar email/phone/modules en proyección pública
- [x] Storage ownership por path
- [x] Gate Auth → Legal → Profile
- [x] AccountType no concede permisos / no en onboarding
- [x] Moderación bloqueada
- [x] Sin roles reales ni M03
- [x] Build / tests / lint OK
- [x] Remoto declarado honestamente
- [x] Este cierre creado

---

## 11. Parada

**No** se inicia M02 Etapa 4 ni M03.
