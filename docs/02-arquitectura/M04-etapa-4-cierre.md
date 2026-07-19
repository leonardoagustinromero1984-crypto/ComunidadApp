# M04 — Cierre Etapa 4: UI y flujos operativos

**Fecha:** 2026-07-16  
**Rama:** `m04/etapa-4-ui-flujos-operativos`  
**Módulo:** M04 — Administración, Moderación y Soporte  
**Estado de entrada:** Etapa 3 consolidada (`91deddf69df45a3a6fe40eb045f2ea6210b170d1`)  
**Spec:** `docs/03-modulos/M04-Etapa-4-UI-y-Flujos-Operativos.md`

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Commit base Etapa 3 | `91deddf69df45a3a6fe40eb045f2ea6210b170d1` |
| Rama de trabajo | `m04/etapa-4-ui-flujos-operativos` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** |
| Etapa 5 / M05 | **No** iniciados |
| Migración `023` | **No** (sin defecto bloqueante de `022`) |

---

## 2. Inventario

### Rutas (`NavRoutes`)

| Ruta | Pantalla |
|------|----------|
| `admin_moderation` (legacy) | `ModerationQueueScreen` |
| `moderation_report/{reportId}` | Detalle reporte |
| `moderation_cases` / `moderation_case/{caseId}` | Cola / detalle casos |
| `moderation_appeals` / `moderation_appeal/{appealId}` | Apelaciones staff |
| `my_moderation_appeals` | Apelaciones usuario |
| `org_verification_queue` / `org_verification_review/{reviewId}` | Verificación |
| `my_support_tickets` / `create_support_ticket` / `support_ticket/{ticketId}` | Soporte usuario |
| `support_admin_queue` / `support_admin_ticket/{ticketId}` | Soporte staff |
| `administrative_audit` | Auditoría solo lectura |
| `administrative_ops_hub` | Hub operaciones (tiles por permiso) |
| `platform_admin` | Sin cambios de contrato |

### ViewModels

- Moderación: queue, report detail, case queue/detail, action, appeals (staff + my)
- Verificación: queue + review
- Soporte: my tickets, create, detail, admin queue/detail
- Auditoría: `AdministrativeAuditViewModel`
- Helpers: `AdministrativeScreenPhase`, `AdministrativeAccessGate`, `SensitiveDataPresentation`, `AdministrativeSessionCleanup`
- Legacy: `AdminModerationViewModel` migrado a `ModerationRepository`

### Adaptación repositorios (sin SQL nuevo)

Extensiones sobre contratos Etapa 3 / RPCs `022`:

- `triageReport`, `markReportDuplicate`, `listCases`, `getCase`, `addInternalNote`
- `SupportTicketDetail` / `getTicketDetail` (proyección con mensajes según rol)

Mocks + Supabase actualizados.

---

## 3. Flujos implementados

- Bandeja/filtros/detalle de reportes (RPC; sin SELECT/UPDATE directo inseguro)
- Casos: crear, asignar, estado, notas internas, medidas con confirmación
- Apelaciones usuario/staff; conflicto de revisor
- Verificación org staff; sin upload físico (M05)
- Soporte usuario/staff; INTERNAL oculto al solicitante
- Auditoría administrativa de solo lectura
- Entradas Profile gated por permisos M02; deep link denegado en ViewModel
- Logout limpia estado administrativo

---

## 4. Permisos y datos sensibles

- Autoridad solo vía permisos efectivos M02 (no AccountType / modules / roles M03)
- Deny-by-default en loading / error / permiso desconocido
- `reporterId` / notas / evidencia solo con `moderation.view_sensitive` (o equivalente soporte)
- Mensajes INTERNAL nunca al solicitante
- UI no sustituye autorización server-side

---

## 5. Calidad

| Control | Resultado |
|---------|-----------|
| Doc pruebas | `docs/04-calidad/M04-pruebas-ui-flujos-operativos.md` |
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **258** tests, **0** failures, **0** errors |
| `lintDebug` | **SUCCESS** |
| Migraciones editadas `001`–`022` | **No** |

---

## 6. Staging y deuda

| Ítem | Estado |
|------|--------|
| Staging `014`–`022` | **PENDIENTE DE VALIDACIÓN REMOTA** |
| Storage evidencia física | M05 |
| UI polish / paginación avanzada | Mejora futura |
| Etapa 5 (validación/cierre módulo) | **No** iniciada |

---

## 7. Checklist Etapa 4

- [x] Rama desde commit base; sin merge a `main`
- [x] Sin M05 / Storage admin / bucket `leover` / base64
- [x] Moderación vía repos/RPC Etapa 3
- [x] Reportes, casos, medidas, apelaciones, verificación, soporte, auditoría
- [x] Permisos + sensibles + INTERNAL ocultos
- [x] Deep links denegados; logout limpia
- [x] Legacy `admin_moderation` preservado (ahora cola nueva)
- [x] Pruebas / build / lint verdes
- [x] Staging declarado honestamente
- [x] Documentos de salida creados
- [x] Sin Etapa 5

---

## 8. Parada

Etapa 4 **cerrada a nivel código y calidad local**.

**No** iniciar M04 Etapa 5.  
**No** iniciar M05.  
**No** merge a `main`.
