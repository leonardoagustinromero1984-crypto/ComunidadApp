# M01 — Pruebas de recuperación, consentimientos y eliminación de cuenta

**Fecha:** 2026-07-14  
**Alcance:** Etapa 4 (local + SQL planificado; remoto no afirmado sin evidencia)

---

## 1. Trigger `handle_new_user` (014 — NO aplicada en remoto compartido)

| Caso | Esperado |
|------|----------|
| Signup con `terms_version` + `privacy_version` válidos | Crea `public.users` + fila `user_consents` |
| Signup admin/invite sin metadata legal | Crea `public.users`; **no** inserta consentimiento; **no** falla |
| Reintento mismo `(user_id, terms, privacy)` | Idempotente (`ON CONFLICT DO NOTHING`) |
| `NEW.id` como user_id | Nunca UUID libre desde Android |

**Estado:** SQL escrito en `supabase/migrations/014_user_consents.sql`. **No desplegado** en cloud sin revisión explícita.

---

## 2. RPC `accept_legal_consents`

| Caso | Esperado |
|------|----------|
| Sin `auth.uid()` | Excepción `NOT_AUTHENTICATED` |
| Versiones vacías | `CONSENT_VERSIONS_REQUIRED` |
| Source inválido | `CONSENT_SOURCE_INVALID` |
| Idempotente | Segundo call no duplica por unique |

---

## 3. RLS

| Tabla | Política |
|-------|----------|
| `user_consents` | SELECT propio; sin INSERT cliente |
| `account_deletion_requests` | SELECT propio; write solo service role |

---

## 4. Recuperación

| Caso | Esperado |
|------|----------|
| Deep link `type=recovery` | `PasswordResetActive`; consume once |
| Deep link `type=signup` | Confirmación (no reset) |
| `updateUser` sin sesión | `PASSWORD_RESET_NOT_AVAILABLE` |
| Reset exitoso | Sesión recovery invalidada (signOut) |

---

## 5. Eliminación (Edge Function)

| Caso | Esperado |
|------|----------|
| Sin Bearer | 401 |
| Método GET | 405 |
| `user_id` malicioso ≠ JWT | 403 |
| Storage falla | `failed` + `failure_code=storage`; **no** borra Auth |
| Auth delete OK | `completed`; Storage ya limpiado |
| Reintento completed | `already_deleted` |

Orden: request pending → Storage `users/{uid}` → device_tokens → Auth delete → completed.

---

## 6. Unitarias locales (código)

Ver suites: `AuthDeepLinkParserTest`, `MockAuthRepositoryStage4Test`, `AccountSecurityViewModelTest`, previas M01.

---

## 7. Remoto / staging

**No verificado en este cierre.** Checklist pendiente:

- [ ] Aplicar 014 tras aprobación trigger
- [ ] Deploy `delete-account`
- [ ] Email recovery real + deep link dispositivo
- [ ] Eliminación cuenta de prueba + confirmar Auth vacío + Storage
- [ ] Gate `LegalConsentRequired` con tabla real
