# LEOVER — M03 Auditoría inicial (Organizaciones y Equipos)

**Módulo:** M03 — Organizaciones y Equipos  
**Etapa:** 1 — Auditoría y diseño  
**Fecha:** 2026-07-15  
**Rama:** `m03/organizaciones-equipos-auditoria`  
**Dependencia:** M02 cerrado a nivel código y calidad local (`M02-cierre-final.md`)  
**Backend oficial:** Supabase (ADR-0001)  
**Alcance:** inventario y diseño; **sin** migraciones, **sin** cambios de RLS, **sin** tablas/repositorios/pantallas nuevas, **sin** roles internos implementados, **sin** M04  

**Documentos de entrada (orden leído):**

1. [`docs/01-producto/D01-Modulos-y-Orden.md`](../01-producto/D01-Modulos-y-Orden.md)  
2. [`docs/02-arquitectura/M00-cierre-final.md`](M00-cierre-final.md)  
3. [`docs/02-arquitectura/M01-cierre-final.md`](M01-cierre-final.md)  
4. [`docs/02-arquitectura/M02-cierre-final.md`](M02-cierre-final.md)  
5. [`docs/03-modulos/M01-Identidad-y-Autenticacion.md`](../03-modulos/M01-Identidad-y-Autenticacion.md)  
6. [`docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`](../03-modulos/M02-Usuarios-Roles-y-Permisos.md)  
7. ADR-0001 … ADR-0005  
8. [`docs/03-modulos/M03-Organizaciones-y-Equipos.md`](../03-modulos/M03-Organizaciones-y-Equipos.md)  

---

## 0. Estado Git y calidad

| Ref | Nota |
|-----|------|
| Base M02 tip | `0e9e2fb` — cierre Etapa 5 SHA record |
| Spec M03 | `213ee70` — `docs: add approved M03 organizations and teams module specification` |
| Audit tip | working tree con `M03-auditoria-inicial.md` (este documento) |
| Rama | `m03/organizaciones-equipos-auditoria` |
| WIP GPS/mapas/pagos | **No** mezclado |
| Merge a `main` | **No** |
| Stash preservado | `stash@{0}: backup/pre-m01-identidad-autenticacion` |

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | SUCCESS |
| `testDebugUnitTest` | **124** tests, **0** failures |
| `lintDebug` | SUCCESS, **0** errors |

**Remoto / staging 014–018:** **PENDIENTE** (bloquea release; no bloquea esta auditoría). No se afirma aplicación remota.

---

## 1. Hallazgo central

**No existe** hoy un modelo de organización multi-miembro.

Lo que hay son **perfiles/listados 1-usuario-owner**:

| Entidad legacy | Tabla | Relación | ¿Es organización M03? |
|----------------|-------|----------|------------------------|
| Refugio | `public.shelters` | `owner_id` → un usuario | **No** — ficha de listing |
| Tránsito | `public.foster_homes` | `host_id` → un usuario | **No** — listing personal |
| Servicio / negocio | `public.service_profiles` | `owner_id` + categoría | **No** — perfil profesional 1:1 |
| Productos tienda | `public.shop_products` | vía `service_id` | Extensión de service, no org |
| Usuario “institucional” | `users.account_type` | SHELTER/VET/SHOP… | Capacidad UI legacy, **no** org |

No hay tablas ni código de:

- `organizations` / membresías / roles internos  
- invitaciones organizacionales  
- sucursales / branches  
- verificación de organización  
- slug / ownership transfer / audit de org  

Búsqueda en repo de `organization`, `membership`, `invitation`, `sucursal`, `slug` (org): **sin matches** relevantes.

---

## 2. Inventario detallado

### 2.1 SQL (migraciones 001–018)

| Recurso | Origen | RLS vigente (repo) | Notas |
|---------|--------|--------------------|-------|
| `shelters` | `006` | SELECT abierto authenticated; write `owner_id = auth.uid()` | Expone `contact_phone` / `contact_email` a cualquier autenticado |
| `foster_homes` | `006` | SELECT abierto; write host | `contact_info` público a authenticated |
| `service_profiles` | `011` | SELECT si `active` o owner; write owner | Contacto/precio visibles si active |
| `service_bookings` | `011` | provider/client | Agenda; pagos **fuera** M03 |
| `shop_products` | `012` | políticas en 012 | Vinculado a service, no a org |
| `friend_connections` | `008` | amistades persona-persona | **No** es equipo org |
| Roles plataforma M02 | `018` | catalog + assignments | USER/MODERATOR/ADMIN/SUPERADMIN — **no** roles internos org |

**Remoto:** existencia/aplicación real de estas tablas **no verificada** en staging en esta etapa.

### 2.2 Android — modelos y repositorios

| Pieza | Rol actual | Reutilizar en M03 |
|-------|------------|-------------------|
| `AccountType` | PERSON, SHELTER, VET, TRAINER, WALKER, SHOP, FOSTER_HOME | Solo legacy / UI mode — **no** como org |
| `active_modules` / `LeoverModule` | Capacidades de producto por tipo | No como membresía |
| `ModulePermissions` / `RolePermissions` | Gates de publish/nav por AccountType | Deprecar gradualmente hacia org + M02 |
| `Shelter` + `ShelterRepository` | CRUD listing | Posible **migración de datos** futura hacia org tipo SHELTER |
| `ServiceProfile` + `ServiceRepository` | Perfil profesional / Mi Negocio | Posible vínculo 1:1 opcional org↔service **después** |
| `CommunityRepository` (foster, events, donations) | Listings comunitarios | No reutilizar como membresía |
| `UserRepository` / `PermissionRepository` / `AuthorizationService` | M01/M02 | **Reutilizar** como base de actor y permisos plataforma |
| `MiNegocioScreen` / `ServiceViewModel` | Panel negocio 1 usuario | No confundir con admin de org |
| `SheltersScreen` / detail | Directorio público | UI pública distinta del admin org |

### 2.3 Storage

| Path / bucket | Uso | Org M03 |
|---------------|-----|---------|
| `profile-avatars` + `users/{uid}/avatar/...` | Avatar persona (M02) | No |
| `leover` bucket genérico (`StoragePaths` pets/posts/adoptions/lost_found) | Contenido usuario | Sin paths org |
| Logo/portada organización | **No existe** | Crear en Etapa 3+ con ownership por `organizations/{id}/...` |

### 2.4 Permisos M02 vs internos M03

| Capacidad | Hoy | Debe ser en M03 |
|-----------|-----|-----------------|
| Moderación plataforma | `moderation.view` (M02) | Sigue M02/M04 |
| Admin plataforma usuarios | `roles.*` / `users.change_status` | Sigue M02 |
| Editar ficha shelter | owner_id | → `organization.update` + membresía |
| Invitar colegas a un refugio | **Imposible** (single owner) | → invitaciones M03 |
| OWNER org = SUPERADMIN | — | **Prohibido** por diseño M03 |

### 2.5 AccountType y active_modules — riesgos

1. **Confusión semántica:** `AccountType.SHELTER` sugiere “organización”, pero es flag en `users`.  
2. **UI nav** (`ComunidappNavGraph`, bottom bar, publish) decide por AccountType.  
3. **PublishViewModel** asigna `shelterId` si `accountType == SHELTER` sin verificar membresía.  
4. **Registro Auth** fuerza `PERSON` en remoto (SupabaseAuth); mock histórica puede simular otros tipos.  
5. M02 ya declaró: AccountType **no** concede permisos de plataforma — M03 debe extender eso a **no** conceder membresía.

---

## 3. Gaps vs diseño M03

| Requisito M03 | Estado actual |
|---------------|---------------|
| Catálogo tipos SHELTER/NGO/VET… | Solo AccountType + ServiceCategory parcial |
| Estados DRAFT/ACTIVE/… | Ausentes en shelters/services |
| Roles OWNER…VIEWER | Ausentes |
| Multi-membresía | Ausente (1 owner) |
| Invitaciones tokenizadas | Ausentes |
| Sucursales | Ausentes |
| Verificación org | Ausente (contrato diferido M04) |
| Perfil público allowlist | Shelters SELECT abierto con PII de contacto |
| Historial status/audit org | Ausente |
| Separación de `shelters` listing vs org legal | No modelada |

---

## 4. Duplicaciones y reutilización

### Reutilizar tal cual

- M01 sesión / `auth.uid()`  
- M02 `PermissionRepository`, `has_permission`, account_status gates  
- `AppResult` / `AppError` / `AppLogger`  
- Providers (ADR-0003), sin Hilt  
- Componentes UI M00  

### Reutilizar con adaptación (no sustituyen org)

- `shelters` / `service_profiles` como **recursos vinculados** o datos a migrar  
- Pantallas de directorio comunitario como consumidores del perfil público org  

### No reutilizar como autoridad

- `AccountType`  
- `active_modules`  
- `LeoverModule.ADMIN`  
- RLS “todo SELECT true” de shelters/foster como proyección pública  

---

## 5. Riesgos de seguridad / producto

| ID | Riesgo | Severidad | Mitigación propuesta (etapas futuras) |
|----|--------|-----------|----------------------------------------|
| R1 | Contacto de shelters legible por cualquier authenticated | Media | RPC allowlist; ocultar phone/email internos |
| R2 | Single-owner impide equipo real | Alta (funcional) | Membresías + roles internos |
| R3 | AccountType como proxy de capacidades org | Alta | Separar org context; deprecar gates |
| R4 | Convivencia listings vs org sin plan de migración | Media | Mapping `organization_id` opcional en shelters/services |
| R5 | Staging 014–018 pendiente | Release | Heredado M02; no inventar remoto |
| R6 | Pagos/bookings en service_bookings | Fuera M03 | No tocar M24 en M03 |

---

## 6. Propuesta de modelo (solo diseño — **no implementar en Etapa 1**)

Tablas candidatas (confirmar en Etapa 2–3; no crear todas a ciegas):

```text
organizations
organization_memberships
organization_role_permissions   -- o matrix seed + assignments
organization_invitations
organization_branches           -- Etapa 4
organization_status_history
organization_audit_log
```

**Decisión recomendada:**

1. **Nueva** entidad `organizations` (no reutilizar `shelters` como raíz).  
2. Mantener `shelters` / `service_profiles` como listings/recursos con `organization_id` nullable en migración futura.  
3. Roles internos **separados** de `platform_roles` (018).  
4. Evaluación: `has_org_permission(org_id, code)` además de `has_permission` de plataforma.  
5. Usuario SUSPENDED/BANNED (M02) no administra org.  
6. Org SUSPENDIDA no publica.  

**Tipos iniciales:** mapa AccountType legacy → tipo org sugerido (solo migración de datos, no autoridad):

| AccountType legacy | organization type propuesto |
|--------------------|----------------------------|
| SHELTER | SHELTER |
| FOSTER_HOME | RESCUE_GROUP u OTHER (decidir) |
| VET | VETERINARY_CLINIC |
| SHOP | PET_SHOP |
| TRAINER | TRAINING_CENTER |
| WALKER | WALKER_AGENCY |
| PERSON | (sin org automática) |

---

## 7. Archivos a crear/modificar (plan futuro; **no en Etapa 1**)

### Etapa 2 — Contratos

- `domain/organization/*`  
- `domain/organization/authorization/*`  
- Validadores slug/invitación  
- Tests unitarios  

### Etapa 3 — Persistencia

- Migración siguiente al último número remoto-verificado (hoy tip repo `018`; **no editar** 014–018)  
- RPC + RLS membresía  
- Storage org logo/cover  
- Tests SQL documentados  

### Etapa 4 — Equipos / UI

- `OrganizationRepository` (+ membership/invitation si hace falta)  
- Onboarding org, miembros, invitaciones, sucursales  
- Contexto “actuando como org” en nav  

### Etapa 5 — Calidad / staging

- Checklist remoto  
- Cierre M03  

---

## 8. Decisiones que requieren aprobación

1. ¿`shelters` permanece listing independiente con FK a org, o se fusiona?  
2. ¿Cada `service_profiles` debe pertenecer siempre a una org, o persona+org opcionales?  
3. ¿Migración automática AccountType→org draft al primer login post-M03?  
4. ¿FOSTER_HOME es org o capacidad personal?  
5. ¿Verificación mínima en M03 o 100% diferida a M04?  
6. ¿Política de PII de contacto en directorio público (allowlist estricta vs legado)?  

---

## 9. Plan por etapas (resumen)

| Etapa | Entrega | Bloqueos |
|-------|---------|----------|
| **1 (esta)** | Auditoría | — |
| 2 | Dominio + authz interna (sin SQL) | Aprobación decisiones §8 |
| 3 | SQL/RLS/RPC/Storage perfil org | Preferible staging 014–018 aplicado |
| 4 | Equipos, invitaciones, UI | — |
| 5 | Staging M03 + cierre | Sin producción |

---

## 10. Checklist Etapa 1

- [x] Inventario shelters/services/AccountType/modules  
- [x] Confirmación: sin org/membresías/invitaciones/sucursales  
- [x] Relación con permisos M02 documentada  
- [x] Storage sin paths org  
- [x] RLS legacy inventariada (riesgo PII en SELECT abierto)  
- [x] Propuesta de modelo sin implementar  
- [x] Build / tests / lint ejecutados  
- [x] Staging remoto declarado pendiente  
- [x] Sin migraciones / RLS / pantallas / M04  

---

## 11. Parada

**No** se inicia Etapa 2 ni M04.  
**No** merge a `main`.  
Entregable: este documento.
