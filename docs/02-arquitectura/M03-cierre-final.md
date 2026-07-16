# M03 — Cierre final: Organizaciones y Equipos

**Fecha:** 2026-07-16  
**Módulo:** M03  
**Estado código:** **CERRADO** (implementación + calidad local)  
**Estado release:** **BLOQUEADO** hasta validación staging documentada  
**Rama de cierre:** `m03/etapa-5-validacion-cierre`  
**Commit base Etapa 4:** `11abd6b9a68dd15b1c27e2e0295958275cab7dd1`

---

## 1. Resumen ejecutivo

M03 entregó organizaciones como entidad separada del usuario, membresías y roles internos (distintos de M02), invitaciones con token hasheado, transferencia atómica de ownership, sucursales sin GPS/mapas, Storage institucional, contexto organizacional Android y UI de administración.

La validación remota de migraciones **014–021** permanece **PENDIENTE DE VALIDACIÓN REMOTA**.

---

## 2. Capacidades entregadas

### Organización y perfil institucional

- Entidad `organizations` (no reutiliza `shelters` / `service_profiles` como raíz).
- Tipos allowlisted; `FOSTER_HOME` permanece capacidad personal.
- Estados DRAFT…CLOSED/REJECTED; verificación mínima (solicitud → PENDING).
- Perfil público por RPC allowlist; contactos privados por defecto (opt-in).
- OWNER inicial creado atómicamente con `create_organization`.

### Membresías y autorización

- Roles internos OWNER…VIEWER separados de `platform_roles`.
- `has_org_permission` deny-by-default (cuenta M02, org, membresía, permiso).
- AccountType / `active_modules` / roles M02 **no** conceden membresía.
- ADMIN no gestiona OWNER ni se autoeleva a OWNER.

### Invitaciones

- Persistencia solo `token_hash` (SHA-256).
- Expirables, revocables, un solo uso.
- Sin membresía hasta aceptación válida.
- No se invita al rol OWNER.

### Ownership y cierre

- Último OWNER protegido (remove / demote / leave).
- `transfer_organization_ownership` atómica.
- `close_organization` bloquea operación y revoca invitaciones PENDING.
- Sin borrado físico completo (retención diferida).

### Sucursales

- Sin coordenadas / mapas / GPS.
- Teléfono privado por defecto.
- Proyección pública allowlist vía RPC.
- Fix Etapa 5 (`021`): RLS SELECT ya no filtra teléfono privado a ajenos.

### Storage

- Bucket privado `organization-media`.
- Paths `organizations/{orgId}/logo|cover/{file}`.
- Upload/delete con `organization.update`; URLs firmadas temporales.

### Contexto Android

- `OrganizationContextProvider` no reemplaza `AuthState`.
- Refresco de permisos al cambiar organización.
- Limpieza en logout (`SessionViewModel`).
- Acciones privilegiadas vía RPC (no solo estado local).

### Legacy

- `shelters` / `service_profiles` siguen válidos sin org.
- `organization_resource_links` opcionales; sin migración automática.
- `PublishViewModel` no asigna `shelterId` por AccountType.

---

## 3. Migraciones

| Archivo | Contenido |
|---------|-----------|
| 019 | Foundation orgs / memberships / RLS / Storage |
| 020 | Invitaciones / equipos / ownership / branches |
| 021 | Correctiva RLS privacidad sucursales |

014–018 heredadas M01/M02; **no editadas** en M03.

---

## 4. Calidad local

| Control | Resultado (Etapa 5) |
|---------|---------------------|
| Unit tests | **172** / 0 fallos / 0 errores |
| `assembleDebug` | SUCCESS |
| `lintDebug` | SUCCESS |
| Suites SQL documentadas | `M03-pruebas-persistencia-rls-organizaciones.md`, `M03-pruebas-equipos-invitaciones-sucursales.md` |

---

## 5. Validación remota

Ver `docs/04-calidad/M03-reporte-validacion-staging.md`.

| Ítem | Estado |
|------|--------|
| Aplicación 014–021 en staging | **PENDIENTE** |
| Checklist funcional M03 remoto | **PENDIENTE** |
| Producción | **No** desplegada |

**Condición de release:** staging PASS documentado antes de producción.

---

## 6. Deuda aceptada

- Staging no ejecutado (bloquea release, no el cierre de código).
- URLs de media pública pueden requerir signed URL aunque el path figure en proyección pública.
- Transferencia OWNER sin locks de fila explícitos (atómica por RPC).
- Verificación documental avanzada diferida a M04.

---

## 7. Condiciones de release / M04

| Condición | |
|-----------|--|
| Código M03 | Aprobado a nivel repo |
| Staging 014–021 + checklist | Requerido para release |
| Merge a `main` | Decisión de proceso (no hecho en Etapa 5) |
| **M04** | Habilitado solo para **auditoría y diseño** tras aprobar este cierre; **implementación no autorizada** hasta working tree limpio y build/tests/lint verdes en la rama de trabajo de M04 |

---

## 8. Commits de referencia

| Hito | SHA (corto / completo según tip) |
|------|----------------------------------|
| Etapa 3 | `635943c` |
| Etapa 4 | `11abd6b9a68dd15b1c27e2e0295958275cab7dd1` |
| Spec Etapa 5 | incluida en commit de cierre Etapa 5 |
| Cierre Etapa 5 / M03 | (este commit de rama) |

---

## 9. Checklist cierre M03

- [x] Organización, membresías, invitaciones, ownership, sucursales, Storage, contexto  
- [x] Deny-by-default server + Android  
- [x] Calidad local verde  
- [x] Staging pendiente documentado honestamente  
- [x] Release bloqueado sin staging  
- [x] M04 no implementado  
