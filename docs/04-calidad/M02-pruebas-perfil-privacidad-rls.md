# M02 — Pruebas de perfil, privacidad y RLS (Etapa 3)

**Módulo:** M02 — Usuarios, Roles y Permisos  
**Alcance:** SQL/RLS documentado + suite unitaria Android  
**Estado remoto:** Migraciones `015`–`017` **no verificadas** en staging/producción en esta etapa.

---

## 1. Suite SQL / RLS (manual / staging)

Ejecutar solo en proyecto de staging o local. **No** correr destructivos en producción.

### 1.1 Fundación (`015_user_profile_foundation.sql`)

| Caso | Esperado |
|------|----------|
| Backfill `display_name = name` | Filas con `name` no vacío y `display_name` null quedan rellenadas |
| Sin inventar usernames | `username` permanece null hasta onboarding |
| Usuarios sin username | `onboarding_status = IN_PROGRESS` |
| Constraints username | Formato 3–30, sin `..`, sin terminar en `.` |
| `reserved_usernames` seed | admin, moderator, support, leover, etc. activos |
| `user_privacy_settings` | Backfill PRIVATE/PUBLIC desde `profile_private`; `show_phone` desde `phone_public` |
| Trigger `handle_new_user` | Crea fila en `public.users` + privacidad inicial; consentimiento solo si existe `user_consents` |

### 1.2 Seguridad (`016_user_profile_security.sql`)

| Caso | Esperado |
|------|----------|
| `is_username_available('Admin')` | `false` (reservado / case-insensitive) |
| Username ocupado case-insensitive | `false` |
| `complete_profile_onboarding` con `auth.uid()` | Atómico; `onboarding_status=COMPLETED`; sync `name=display_name` |
| Complete sin tocar `account_type` / modules / reputation / `account_status` | Sin cambios en columnas administrativas |
| Usuario A no puede completar/actualizar como B | Solo `auth.uid()` |
| `update_my_profile` allowlist | Solo display/bio/location/locale/timezone/avatar_path |
| UPDATE directo `users` como `authenticated` | Rechazado (privilege + sin policy) |
| SELECT propio | Fila completa |
| SELECT ajeno directo | 0 filas |
| `get_public_user_profile` allowlist | Sin email/phone/modules/account_status/onboarding |
| PUBLIC / FRIENDS / PRIVATE | Visibilidad según settings + `friend_connections` ACCEPTED |
| SUSPENDED/BANNED ajeno | RPC retorna null |
| `search_public_user_profiles` | Sin email/teléfono; query &lt; 2 → `[]`; limit ≤ 50 |
| Convivencia consentimientos M01 | Trigger no rompe `user_consents` si existe |

### 1.3 Storage (`017_profile_avatar_storage.sql`)

| Caso | Esperado |
|------|----------|
| Bucket `profile-avatars` privado | MIME jpeg/png/webp; size ≤ 5MB |
| Path owner `users/{uid}/avatar/{file}` | INSERT/UPDATE/DELETE OK |
| Path ajeno / traversal `..` | Denegado |
| SELECT ajeno | Solo si perfil visible (PUBLIC/FRIENDS) |
| Signed URL | Temporal; no URL permanente en DB |

---

## 2. Suite unitaria Android (repo)

| Área | Cobertura |
|------|-----------|
| Gate sesión | `ProfileSessionGateTest` |
| Onboarding VM | `ProfileOnboardingViewModelTest` |
| Mock repository | `MockUserRepositoryProfileTest` |
| Username | `UsernameValidatorsTest` (Etapa 2) |
| Session | `SessionViewModelTest` con perfil COMPLETED |

---

## 3. Manual / smoke (mock local)

1. Auth + legal → onboarding si `NOT_STARTED`/`IN_PROGRESS`.
2. Username reservado / ocupado → error inline.
3. Completar onboarding → MAIN.
4. Editar perfil vía allowlist (sin AccountType).
5. Perfil privado ajeno no visible en búsqueda.
6. Moderación sigue oculta / denegada.

---

## 4. Remoto

**No se afirma** aplicación ni validación de `015`–`017` en el proyecto Supabase remoto sin evidencia de deploy + checklist ejecutada.
