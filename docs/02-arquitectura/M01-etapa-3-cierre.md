# M01 — Cierre Etapa 3: Registro, login, verificación y consentimientos

**Fecha:** 2026-07-14  
**Rama:** `m01/etapa-3-flujos-auth-consentimientos`  
**Módulo:** M01 — Identidad y Autenticación  
**Estado de entrada:** Etapa 2 aprobada (`9cf8e84`)  

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Base Etapa 2 consolidada | `9cf8e84` — `feat: add M01 stage 2 auth domain contracts…` |
| Rama Etapa 3 | `m01/etapa-3-flujos-auth-consentimientos` (cambios al cierre aún locales) |
| WIP GPS/mapas/pagos | **No** incorporado |

---

## 2. Archivos creados

| Archivo |
|---------|
| `supabase/migrations/014_user_consents.sql` |
| `domain/auth/LegalDocumentConfig.kt` (+ `ConsentMetadata`, `EmailMasking`) |
| `domain/auth/AuthAnalytics.kt` |
| `ui/screens/legal/LegalDraftScreens.kt` |
| `docs/04-calidad/M01-pruebas-user-consents.md` |
| `test/.../AuthViewModelsTest.kt` |
| `docs/02-arquitectura/M01-etapa-3-cierre.md` (este) |
| Spec Etapa 3 (working tree): `docs/02-arquitectura/M01-Etapa-3-Registro-Login-Verificacion-y-Consentimientos.md` |

## 3. Archivos modificados

| Archivo | Cambio |
|---------|--------|
| `AuthRepository` / `MockAuthRepository` | `register(..., consent)`; mock guarda consentimientos |
| `SupabaseAuthRepository` | metadata allowlisted (terms/privacy/locale/source); **sin** `account_type` |
| `LoginViewModel.kt` (Register/Login/Forgot/Verification) | comandos, validadores, AppError, anti–doble envío |
| `RegisterScreen.kt` | sin AccountType; checkboxes legales; links a borradores |
| `ForgotPasswordScreen.kt` | email enmascarado en verificación |
| `NavRoutes` / `ComunidappNavGraph` | `legal_terms` / `legal_privacy` |
| `MockAuthRepositoryTest` | consent en register |

## 4. Eliminados

Ninguno.

---

## 5. Migración y trigger

### Auditoría previa

| Migración | Contenido revisado |
|-----------|-------------------|
| `004_auth_user_profile_trigger.sql` | `handle_new_user` crea `public.users` + trigger |
| `009_profile_privacy.sql` | redefine función con `profile_private` |

**No se sobrescribieron** 004/009. La lógica vigente de 009 se reimplementó en `014` junto con consentimientos.

### `014_user_consents.sql`

- Tabla `public.user_consents` con checks, índice, unique `(user_id, terms_version, privacy_version)`
- RLS: SELECT propio; sin INSERT cliente (`authenticated`)
- `handle_new_user`:
  - conserva alta de `public.users` (`profile_private=true`, `account_type=PERSON`)
  - exige metadata `terms_version` / `privacy_version` (rollback con excepción si faltan)
  - inserta consentimiento con `timezone('utc', now())` + `ON CONFLICT DO NOTHING`
  - UUID desde `NEW.id` (no desde Android)

---

## 6. UI y ViewModels

| Flujo | Estado |
|-------|--------|
| Registro | Validadores + consentimientos obligatorios; sin selector AccountType |
| Documentos | Pantallas internas **BORRADOR — NO PUBLICABLE** (sin URLs inventadas) |
| Verificación | Email enmascarado; cooldown 60s; OTP/link existentes |
| Login | AppError / mensajes seguros; redirect a verificación |
| Recuperación solicitada | Respuesta genérica; reset remoto final **no** implementado |
| Sesión | Adapter `SessionState` desde Etapa 2 se mantiene |

---

## 7. Pruebas

### Unitarias

| Suite | Tests |
|-------|------:|
| Previas (M00 + M01-2) | 54 |
| AuthViewModelsTest (nuevas) | 13 |
| **Total** | **67** (0 failures) |

### SQL/RLS

Documento: [`docs/04-calidad/M01-pruebas-user-consents.md`](../04-calidad/M01-pruebas-user-consents.md)

### Supabase remoto / dashboard

| Ítem | Estado |
|------|--------|
| Aplicar migración 014 en cloud | **No ejecutado / no verificado** en esta sesión |
| Trigger + RLS en vivo | **No verificado** |
| Emails reales / deep link device | **No verificado** |
| Checklist redirect/templates (Etapa 2) | Sigue pendiente humano |

---

## 8. Build / tests / lint

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
```

| Resultado | Valor |
|-----------|-------|
| assembleDebug | **SUCCESS** |
| testDebugUnitTest | **SUCCESS** — 67 tests |
| lintDebug | **SUCCESS** — **0 errors**, 39 warnings |

CI sin secretos (sin cambios de secretos).

---

## 9. Smoke manual (documentado)

| Caso | Resultado en esta sesión |
|------|--------------------------|
| Registro mock | Cubierto por unit tests; UI no ejercitada en device |
| Registro Supabase real | **No probado** |
| Deep link / email real | **No probado** |
| Dark mode / TalkBack | **No probado** (sin regresión de tema Material 3 esperada) |

---

## 10. Riesgos y deuda Etapa 4

- Aplicar y validar `014` en staging antes de release.
- Reset remoto post-deep-link + pantalla de nueva contraseña.
- Eliminación de cuenta + Edge Function.
- Textos legales publicables (`publishable=true`) para release.
- Migrar `SessionState` gate a `AuthState` completo si hace falta.
- Ocultar por completo el reset mock en UI si se unifica solo “solicitud”.

---

## 11. Checklist de aceptación

- [x] Etapa 2 consolidada (`9cf8e84`)
- [x] Rama limpia sin WIP funcional
- [x] Registro con comandos/validadores
- [x] AccountType oculto en registro
- [x] Consentimientos separados + LegalDocumentConfig
- [x] Borradores legales (no URLs inventadas)
- [x] `user_consents` + RLS + trigger idempotente
- [x] Conservada creación de `public.users`
- [x] Verificación + cooldown
- [x] Login con AppError
- [x] Recuperación genérica (sin reset remoto final)
- [x] Sin delete-account / OAuth / Hilt / NestJS / M02
- [x] Tests previos + nuevos OK
- [x] assemble + lint 0 errors
- [x] Remoto no afirmado sin evidencia
- [x] Este cierre creado

---

## 12. Parada

**Etapa 3 completa.** No iniciar Etapa 4 hasta revisión explícita.
