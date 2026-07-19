# M01 — Cierre Etapa 2: Contratos, estado y validaciones

**Fecha:** 2026-07-14  
**Rama:** `m01/etapa-2-contratos-validaciones`  
**Módulo:** M01 — Identidad y Autenticación  
**Estado de entrada:** Auditoría M01 aprobada  

---

## 1. Rama y commits

| Ref | SHA | Nota |
|-----|-----|------|
| Commit M00 Etapa 4 (consolidado) | `629a6c1` | `feat: complete M00 stage 4 config, observability and security foundation` |
| Spec/audit M01 | `e8868d3` | docs auditoría + specs M01 |
| Rama de trabajo | `m01/etapa-2-contratos-validaciones` | cambios de Etapa 2 (working tree al cierre) |
| WIP GPS/mapas/pagos | `wip/gps-mapas-pagos` | **no** incorporado |

SHA base al crear la rama Etapa 2: `e8868d3` (sobre `629a6c1`).

---

## 2. Archivos creados

| Archivo |
|---------|
| `domain/auth/AuthState.kt` |
| `domain/auth/AuthUser.kt` |
| `domain/auth/AuthCommand.kt` (`SignUpCommand` **sin** AccountType) |
| `domain/auth/AuthErrorMapper.kt` (+ `AuthErrorCode`, `AuthException`) |
| `domain/auth/validation/AuthValidators.kt` |
| `test/.../AuthValidatorsTest.kt` |
| `test/.../AuthErrorMapperTest.kt` |
| `test/.../MockAuthRepositoryTest.kt` |
| `test/.../SessionViewModelTest.kt` |
| `docs/02-arquitectura/M01-etapa-2-cierre.md` (este) |

## 3. Archivos modificados

| Archivo | Motivo |
|---------|--------|
| `MockAuthDatabase.kt` | Fixtures + `resetToFixtures` + `DEMO_PASSWORD=demo1234` |
| `AuthRepository.kt` / `MockAuthRepository` | Mock endurecido, validadores, `suspend logout`, errores tipados |
| `SupabaseAuthRepository.kt` | Validadores, mapper, logout suspend, reset remoto = `PASSWORD_RESET_NOT_AVAILABLE` |
| `SessionViewModel.kt` | `authState` oficial + adapter `SessionState` |
| `ProfileViewModel.kt` | logout en coroutine |
| `README.md` | password demo documentada |
| `M01-auditoria-inicial.md` | password demo + nota Etapa 2 |
| `gradle/libs.versions.toml` + `app/build.gradle.kts` | `kotlinx-coroutines-test` |

## 4. Eliminados

Ninguno.

---

## 5. Decisiones implementadas

| ID | Resultado |
|----|-----------|
| D-M01-01 | Mismo `AuthProvider` / cliente Supabase / `AuthRepository` |
| D-M01-02 | Solo `domain/auth/` mínimo; sin move masivo |
| D-M01-03 | Mock rechaza emails desconocidos |
| D-M01-04 | Mínimo **8** caracteres centralizado; mensajes “6” eliminados del mapper remoto |
| D-M01-05 | `SignUpCommand` sin AccountType; registro fuerza `PERSON` |
| D-M01-06 | Sin delete account / migraciones / Edge Functions |
| D-M01-07 | Sin `FLAG_SECURE` |
| D-M01-08 | Deep link sin cambios; checklist dashboard abajo |
| D-M01-09 | M00 consolidado en `629a6c1` |

---

## 6. Pruebas agregadas

| Suite | Tests |
|-------|------:|
| AuthValidatorsTest | 11 |
| AuthErrorMapperTest | 10 |
| MockAuthRepositoryTest | 8 |
| SessionViewModelTest | 5 |
| **Previos conservados** | 20 |
| **Total** | **54** |

---

## 7. Build / tests / lint

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
```

| Resultado | Valor |
|-----------|-------|
| assembleDebug | **SUCCESS** |
| testDebugUnitTest | **SUCCESS** — 54 tests, 0 failures |
| lintDebug | **SUCCESS** — **0 errors**, 39 warnings, 1 hint |

CI workflow sin secretos (sin cambios de secretos en esta etapa). Ejecución remota no observada en esta sesión.

---

## 8. Checklist manual Supabase (dashboard — no verificado aquí)

Pendiente de verificación humana en el proyecto cloud:

- [ ] Redirect URL incluye `com.comunidapp.app://login-callback`
- [ ] Confirm email ON/OFF alineado al producto
- [ ] Templates (confirm / reset) cargados
- [ ] Política de contraseña remota ≥ 8 (compatible con app)
- [ ] Expiración de links OTP/recovery
- [ ] Rate limits de email aceptables

No se afirma que el dashboard esté correcto sin esa revisión.

---

## 9. Riesgos y deuda para Etapa 3

| Ítem | Notas |
|------|-------|
| UI login/registro | Aún no usa validadores/consent checkboxes (Etapa 3) |
| `AccountTypeDropdown` en RegisterScreen | Ocultar/desactivar en Etapa 3 |
| Reset remoto post-deep-link | Sigue `PASSWORD_RESET_NOT_AVAILABLE` (Etapa 4) |
| Persistencia consentimientos | Sin tabla aún (Etapa 3/4) |
| LoginViewModel | Sigue con `Result` + mensajes; migrar a contratos |
| Enumeración en mensajes legacy de UI | Revisar copy al cablear mapper |
| Política Supabase password en dashboard | Manual |

---

## 10. Checklist de aceptación Etapa 2

- [x] M00 consolidado (`629a6c1`)
- [x] Rama limpia sin GPS/mapas/pagos
- [x] Sin segundo AuthProvider/cliente
- [x] AuthState oficial
- [x] AuthUser sin roles
- [x] SignUpCommand sin AccountType
- [x] Validadores centrales + mínimo 8
- [x] AuthErrorMapper → AppError
- [x] Mock endurecido
- [x] Stub remoto no inventa éxito
- [x] SessionViewModel con transiciones testeadas
- [x] Sin migraciones / Edge / delete / M02
- [x] Tests previos + nuevos en verde
- [x] assemble + lint 0 errors
- [x] Este cierre creado

---

## 11. Parada

**Etapa 2 completa.** No iniciar Etapa 3 ni M02 hasta revisión explícita.
