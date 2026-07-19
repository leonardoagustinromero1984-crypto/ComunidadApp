# M04 — Cierre final: Administración, Moderación y Soporte

**Fecha:** 2026-07-16  
**Módulo:** M04  
**Estado código:** **CERRADO** (implementación + calidad local)  
**Estado release:** **BLOQUEADO** hasta validación staging documentada  
**Rama de cierre:** `m04/etapa-5-validacion-cierre`  
**Commit base Etapa 4:** `41e0d65cc366602959bd8b1292701f7633213a29`

---

## 1. Resumen ejecutivo

M04 entregó la base administrativa de LeoVer: reportes endurecidos, casos y medidas tipadas, apelaciones con conflicto de interés, verificación organizacional (revisión), soporte asincrónico, auditoría administrativa de solo lectura, UI operativa con gates deny-by-default y limpieza de estado al logout.

La validación remota de migraciones **014–023** permanece **PENDIENTE DE VALIDACIÓN REMOTA**.

---

## 2. Capacidades entregadas

### Reportes

- `content_reports` como entrada legacy (sin tabla duplicada).
- Políticas abiertas de `012` eliminadas en `022`; SELECT directo staff cerrado en `023` (solo reporter).
- Staff vía RPC; `reporter_id` sensible sin `moderation.view_sensitive`.
- Targets legacy `POST` / `USER` / `COMMENT` compatibles.
- Triage, duplicados, adjunto a caso.

### Casos y medidas

- Casos, asignación, notas internas, medidas tipadas.
- Temporales con vencimiento; permanentes sin.
- Cuenta M02 / org M03 actualizados con historial en RPC.
- Auditoría en la misma transacción.

### Apelaciones

- Presentación por afectado; una activa; conflicto aplicador ≠ revisor.
- Sin auto-restore automático al overturn.

### Verificación de organizaciones

- M03 solicita; M04 revisa.
- Conflictos: miembro ACTIVE no revisa.
- REQUEST_MORE_INFORMATION conserva PENDING.
- Notas/docs sensibles vía proyección allowlist (`023`).

### Soporte

- Tickets asincrónicos (sin chat realtime).
- Mensajes `REQUESTER_VISIBLE` vs `INTERNAL`.
- PRIVACY/SAFETY con `support.view_sensitive` (RLS mensajes corregido en `023`).

### Auditoría y UI

- `administrative_audit_log` no escribible desde Android; lectura con `audit.view`.
- Colas, detalle, hub operativo, Profile gated.
- Deep links denegados sin permiso; loading/error niegan acciones.
- Logout limpia estado administrativo (`SessionViewModel` y `ProfileViewModel`).

### Permisos y autoridad

- Solo permisos efectivos M02 (`has_permission`).
- AccountType / `active_modules` / roles internos M03 **sin** autoridad M04.

### Compatibilidad legacy

- Ruta `admin_moderation` → cola nueva.
- `PlatformAdminScreen` preservado.
- `ACTIONED` ≠ medida real.

---

## 3. Migraciones

| Archivo | Rol |
|---------|-----|
| 014–021 | Heredadas M01–M03 (no editadas por M04) |
| 022 | Foundation M04 (RLS/RPC/colas) |
| 023 | Correctiva proyecciones sensibles / RLS (Etapa 5) |

---

## 4. Calidad local

| Control | Resultado (Etapa 5) |
|---------|---------------------|
| Unit tests | **261** / 0 fallos / 0 errores |
| `assembleDebug` | SUCCESS |
| `lintDebug` | SUCCESS |
| Docs calidad | `M04-pruebas-persistencia-rls-rpc-colas.md`, `M04-pruebas-ui-flujos-operativos.md`, `M04-reporte-validacion-staging.md` |

---

## 5. Validación remota

Ver `docs/04-calidad/M04-reporte-validacion-staging.md`.

| Ítem | Estado |
|------|--------|
| Aplicación 014–023 en staging | **PENDIENTE** |
| Checklist funcional M04 remoto | **PENDIENTE** |
| Producción | **No** desplegada |

**Condición de release:** staging PASS documentado antes de producción.

---

## 6. Deuda aceptada

- Staging no ejecutado (bloquea release, no el cierre de código).
- Storage de evidencia física / bucket administrativo → M05.
- Mock support sin actor server-side (solo `useSupabase = false`).
- Paginación avanzada / polish UI diferidos.

---

## 7. Condiciones de release / M05

| Condición | |
|-----------|--|
| Código M04 | Aprobado a nivel repo |
| Staging 014–023 + checklist | Requerido para release |
| Merge a `main` | Decisión de proceso (no hecho en Etapa 5) |
| **M05** | Habilitado solo para **auditoría y diseño** tras aprobar este cierre; **implementación no autorizada** hasta working tree limpio y build/tests/lint verdes en la rama de trabajo de M05 |

---

## 8. Commits de referencia

| Hito | SHA |
|------|-----|
| Etapa 2 | `9908b8bcb98ddc9ad26743ed4ce17180d7d72422` |
| Etapa 3 | `91deddf69df45a3a6fe40eb045f2ea6210b170d1` |
| Etapa 4 | `41e0d65cc366602959bd8b1292701f7633213a29` |
| Cierre Etapa 5 / M04 | (este commit de rama) |

---

## 9. Checklist cierre M04

- [x] Reportes, casos, medidas, apelaciones, verificación, soporte, auditoría  
- [x] Deny-by-default server + Android  
- [x] Datos sensibles / INTERNAL protegidos (incl. fix `023`)  
- [x] Calidad local verde  
- [x] Staging pendiente documentado honestamente  
- [x] Release bloqueado sin staging  
- [x] M05 no implementado  
