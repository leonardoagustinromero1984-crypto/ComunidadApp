# M04 — Pruebas UI y flujos operativos (Etapa 4)

**Módulo:** M04 — Administración, Moderación y Soporte  
**Alcance:** ViewModels, navegación, presentación sensible, logout  
**Estado remoto:** Migraciones `014`–`022` **PENDIENTE DE VALIDACIÓN REMOTA**  
**Migración 023:** no creada (sin defecto bloqueante documentado de `022`)

---

## 1. Suite unitaria Android

| Prueba | Cobertura mínima |
|--------|------------------|
| `ModerationQueueViewModelTest` | permiso allow/deny, loading niega acciones, filtros, doble envío |
| `ModerationReportDetailViewModelTest` | triage, sensitive reporter, refresh post-mutación |
| `ModerationCaseViewModelTest` | manage_cases, notas, asignación |
| `ModerationActionViewModelTest` | temporal sin expiry, permanente con expiry, confirmación |
| `ModerationAppealViewModelTest` | review_appeals, conflicto aplicador |
| `OrganizationVerificationViewModelTest` | review/revoke, conflicto miembro org |
| `SupportTicketViewModelTest` | tickets propios, INTERNAL oculto al solicitante |
| `SupportAdminViewModelTest` | support.view/manage, sensible |
| `AdministrativeNavigationAuthorizationTest` | deep link / acceso directo denegado sin permiso |
| `SensitiveDataPresentationTest` | reporterId / notas / mensajes internos |
| `LogoutAdministrativeStateTest` | cleanup de estado administrativo al logout |

**Resultado local Etapa 4:** **258** tests, **0** failures, **0** errors (233 previas + nuevas Etapa 4).

---

## 2. Flujos manuales / smoke (mock o staging)

1. Perfil → entradas staff solo con permisos efectivos M02.
2. Moderación → cola / detalle → triage sin SELECT directo a `content_reports`.
3. Casos → crear / asignar / nota interna / medida con confirmación.
4. Apelaciones staff → no revisar acción propia (mensaje seguro).
5. Verificación → APPROVE/REJECT/REQUEST_MORE_INFO; sin upload de archivos.
6. Soporte usuario → crear ticket; no ver mensajes INTERNAL.
7. Soporte staff → cola / nota interna marcada; cierre con reglas de dominio.
8. Auditoría → solo lectura con `audit.view`.
9. Navegación directa a ruta staff sin permiso → AccessDenied / pop.
10. Logout → limpia estado administrativo (`AdministrativeSessionCleanup`).

---

## 3. Seguridad UI

| Regla | Verificación |
|-------|--------------|
| Deny-by-default mientras loading | ViewModels + `AdministrativeAccessGate` |
| UI ≠ autorización server-side | Acciones vía repos/RPC Etapa 3 |
| AccountType / modules / roles M03 | No usados como autoridad |
| Datos sensibles | Solo con `*.view_sensitive` |
| Sin Storage / base64 evidencia | Cumplido |
| Sin bucket `leover` | Cumplido |

---

## 4. Remoto

**PENDIENTE DE VALIDACIÓN REMOTA** para `014`–`022`.  
No se afirma despliegue ni checklist en staging/producción.
