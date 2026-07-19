# M04 — Cierre Etapa 2: Contratos de moderación, verificación y soporte

**Fecha:** 2026-07-16  
**Rama:** `m04/etapa-2-contratos-moderacion-verificacion-soporte`  
**Módulo:** M04 — Administración, Moderación y Soporte  
**Estado de entrada:** Etapa 1 aprobada y consolidada (`M04-auditoria-inicial.md`)  
**Commit base:** `8985c075959f2c01500d33c9345e081117f6a5f2`  
**Spec:** `docs/03-modulos/M04-Etapa-2-Contratos-Moderacion-Verificacion-y-Soporte.md`

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Etapa 1 consolidada (base) | `8985c075959f2c01500d33c9345e081117f6a5f2` |
| Rama de trabajo | `m04/etapa-2-contratos-moderacion-verificacion-soporte` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** |
| Etapa 3 | **No** iniciada |

---

## 2. Decisiones aplicadas

| Decisión | Aplicación en Etapa 2 |
|----------|------------------------|
| `content_reports` como entrada legacy | Targets `USER`→`USER_PROFILE`, `POST`, `COMMENT`; estados legacy mapeados; `ACTIONED` ≠ medida real |
| Sin tabla duplicada de reportes | Solo contratos Kotlin; sin SQL |
| Reutilizar estados M02/M03 | Medidas tipadas mapean a cuenta/org; sin columnas de estado propias |
| Roles M03 / AccountType / active_modules | `grantsFromAccountTypeOrModulesOrOrgRoles() == false` |
| Deny-by-default | Error de lookup, actor vacío o permiso desconocido → `DENIED_UNKNOWN` / missing |
| Conflicto verificación | Miembro de la org no puede revisar |
| Conflicto apelación | Quien aplicó la medida no puede resolverla |
| Soporte asincrónico | Tickets + mensajes; `usesRealtimeChat() == false` |
| Evidencia lógica | Refs de path/ID; URLs permanentes rechazadas; físico → M05 |
| Permisos | Códigos de dominio alineados a strings M02; seeds → Etapa 3 |

---

## 3. Archivos creados

| Archivo |
|---------|
| `domain/moderation/ModerationReport.kt` |
| `domain/moderation/ModerationCase.kt` |
| `domain/moderation/ModerationAction.kt` |
| `domain/moderation/ModerationAppeal.kt` |
| `domain/moderation/authorization/ModerationAuthorization.kt` |
| `domain/verification/OrganizationVerificationReview.kt` |
| `domain/support/SupportTicket.kt` |
| `data/repository/ModerationRepositories.kt` (interfaces + mocks) |
| `test/.../moderation/ModerationReportRulesTest.kt` |
| `test/.../moderation/ModerationCaseRulesTest.kt` |
| `test/.../moderation/ModerationActionRulesTest.kt` |
| `test/.../moderation/ModerationAppealRulesTest.kt` |
| `test/.../moderation/ModerationLegacyCompatibilityTest.kt` |
| `test/.../moderation/authorization/AdministrativeAuthorizationTest.kt` |
| `test/.../moderation/authorization/AdministrativeConflictRulesTest.kt` |
| `test/.../verification/OrganizationVerificationRulesTest.kt` |
| `test/.../support/SupportTicketRulesTest.kt` |
| `docs/02-arquitectura/M04-etapa-2-cierre.md` (este) |

## 4. Archivos modificados

| Archivo | Cambio |
|---------|--------|
| `DataProvider` | Expone mocks M04 (`moderationRepository`, `organizationVerificationRepository`, `supportRepository`, `administrativeAuditRepository`); **sin** Supabase |

---

## 5. Contratos entregados

### Moderación

- Targets genéricos + compatibilidad legacy `POST` / `USER` / `COMMENT`
- Reportes: estados, prioridades, reason codes, proyección pública sin `reporterId`
- Casos: agrupación, attach sin doble caso activo, cierre con motivo
- Medidas tipadas: temporales con `expiresAt`, permanentes sin expiración, `NO_ACTION` no muta
- Apelaciones: ventana configurable, una activa, conflicto de revisor, sin auto-restore
- Evidencia lógica (`ModerationEvidenceRef`) sin binarios

### Verificación (M04 revisa / M03 solicita)

- Decisiones: APPROVE / REJECT / REQUEST_MORE_INFORMATION / REVOKE / MARK_EXPIRED
- `REQUEST_MORE_INFORMATION` no marca `VERIFIED`
- Conflicto: org no auto-revisa
- Document refs lógicos (sin URL permanente)

### Soporte

- Tickets + mensajes asincrónicos
- Visibilidad `REQUESTER_VISIBLE` vs `INTERNAL`
- Categorías sensibles PRIVACY / SAFETY
- Sin chat realtime

### Autorización administrativa

- `AdministrativePermissionCode` (strings M02 + candidatos nuevos)
- `AdministrativeDecision` explícito (no solo boolean)
- Deny-by-default; lectura ≠ mutación; moderación ≠ soporte ≠ verificación
- Conflictos: org propia, apelación por quien aplicó, acceso a `reporterId` sensible

### Repositorios

- `ModerationRepository`, `OrganizationVerificationRepository`, `SupportRepository`, `AdministrativeAuditRepository`
- Mocks deterministas con `AppResult`
- Sin implementaciones Supabase

---

## 6. Fuera de alcance (respetado)

- Migraciones SQL / RLS / grants / RPC
- Corrección RLS de `content_reports` (queda Etapa 3)
- Pantallas / navegación
- Storage / buckets
- Sanciones reales aplicadas
- Seeds de permisos nuevos en matriz SQL
- Staging / producción
- GPS / mapas / pagos
- M05
- Merge a `main`

---

## 7. Pruebas y calidad

| Control | Resultado |
|---------|-----------|
| Suite unitaria | **224** tests, **0** failures, **0** errors (172 previas + cobertura M04 Etapa 2) |
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **SUCCESS** |
| `lintDebug` | **SUCCESS** |

Pruebas nuevas:

- `ModerationReportRulesTest`
- `ModerationCaseRulesTest`
- `ModerationActionRulesTest`
- `ModerationAppealRulesTest`
- `OrganizationVerificationRulesTest`
- `SupportTicketRulesTest`
- `AdministrativeAuthorizationTest`
- `AdministrativeConflictRulesTest`
- `ModerationLegacyCompatibilityTest`

---

## 8. Riesgos y deuda

| Ítem | Estado |
|------|--------|
| RLS `content_reports` SELECT/UPDATE `using (true)` (migración `012`) | **Pendiente Etapa 3** — no corregido en Etapa 2 (por diseño) |
| Staging migraciones **014–021** | **PENDIENTE DE VALIDACIÓN REMOTA** (bloquea release; no bloquea cierre local Etapa 2) |
| Seeds de permisos M04 nuevos | Etapa 3 |
| Persistencia Supabase / UI admin | Etapas posteriores |
| Evidencia física (Storage) | Alinear con M05 |

---

## 9. Checklist Etapa 2

- [x] Working tree inicial limpio / rama correcta desde commit base
- [x] Sin SQL, RLS o migraciones
- [x] Sin pantallas o navegación
- [x] `content_reports` como entrada compatible (sin tabla duplicada)
- [x] Targets legacy mapeados
- [x] Contratos reportes / casos / medidas / apelaciones
- [x] Contratos verificación y soporte
- [x] Repositorios dedicados + mocks
- [x] Permisos como códigos de dominio; deny-by-default
- [x] Conflictos de interés denegados
- [x] Roles M03 / AccountType / active_modules sin autoridad global
- [x] Datos sensibles separados (`reporterId`, notas, evidencia)
- [x] Pruebas / build / lint verdes
- [x] Cierre creado
- [x] Sin M05 / sin merge a `main` / sin Etapa 3

---

## 10. Parada

Etapa 2 **cerrada a nivel de contratos locales**.

**No** iniciar Etapa 3.  
**No** corregir todavía `content_reports` mediante SQL.  
**No** merge a `main`.
