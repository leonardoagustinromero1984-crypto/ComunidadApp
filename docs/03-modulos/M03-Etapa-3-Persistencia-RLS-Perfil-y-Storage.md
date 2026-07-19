# LEOVER — M03 Etapa 3: Persistencia, RLS, perfil y Storage

**Módulo:** M03 — Organizaciones y Equipos  
**Etapa:** 3 — Persistencia real y perfil institucional  
**Estado de entrada:** Etapa 2 aprobada y consolidada  
**Commit base:** `1789a9ef4c805835530946b6483c092d3df3108d`  
**Backend oficial:** Supabase  
**Objetivo:** implementar la base persistente de organizaciones, membresías, autorización interna, perfil público seguro y archivos institucionales, sin desarrollar todavía invitaciones ni administración completa de equipos.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-auditoria-inicial.md`
6. `/docs/02-arquitectura/M03-etapa-2-cierre.md`
7. `/docs/03-modulos/M03-Organizaciones-y-Equipos.md`
8. `/docs/03-modulos/M03-Etapa-2-Contratos-Organizaciones-Membresias-y-Autorizacion.md`
9. ADR-0001 a ADR-0005
10. Este documento.

---

## 2. Protección Git

Antes de modificar:

1. Confirmar commit base:

```text
1789a9ef4c805835530946b6483c092d3df3108d
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m03/etapa-3-persistencia-perfil-rls
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M04.

---

## 3. Condición remota heredada

Las migraciones 014–018 siguen pendientes de validación staging.

Antes de numerar o aplicar una nueva migración:

- inspeccionar el historial real disponible;
- no editar migraciones aplicadas;
- si no existe acceso remoto, usar el siguiente número disponible en repo y documentarlo como no desplegado;
- no afirmar validación remota sin evidencia;
- no desplegar producción.

M03 puede cerrarse a nivel código con migraciones en repo, pero el release continúa bloqueado hasta completar staging 014 en adelante.

---

## 4. Decisiones vigentes

1. `organizations` es una entidad nueva.
2. `shelters` y `service_profiles` continúan como recursos legacy vinculables.
3. No crear organizaciones desde `AccountType`.
4. `FOSTER_HOME` sigue siendo capacidad personal.
5. Roles internos están separados de M02.
6. Contactos institucionales son privados por defecto.
7. Perfiles públicos usan allowlist.
8. Deny-by-default en servidor y Android.
9. Verificación documental avanzada queda para M04.
10. Invitaciones, equipos y sucursales completas quedan para Etapa 4.

---

## 5. Alcance SQL autorizado

Crear una migración consecutiva, sin asumir ciegamente el número.

### 5.1 `organizations`

Campos mínimos:

```text
id uuid primary key default gen_random_uuid()
slug citext unique not null
legal_name text null
display_name text not null
type text not null
other_type_description text null
description text null
status text not null default 'DRAFT'
verification_status text not null default 'NOT_REQUESTED'
country_code text null
province text null
city text null
contact_email text null
contact_phone text null
contact_email_public boolean not null default false
contact_phone_public boolean not null default false
logo_path text null
cover_path text null
created_by uuid not null references auth.users(id)
created_at timestamptz not null default now()
updated_at timestamptz not null default now()
```

Restricciones:

- tipos allowlisted;
- estados allowlisted;
- verificación allowlisted;
- `OTHER` exige descripción;
- slug normalizado;
- contactos públicos solo cuando el valor existe;
- `created_by` no es autoridad de permisos después de crear membresía OWNER.

### 5.2 `organization_memberships`

```text
id uuid primary key
organization_id uuid not null references organizations(id) on delete cascade
user_id uuid not null references auth.users(id) on delete cascade
role_code text not null
status text not null
joined_at timestamptz null
created_at timestamptz not null
updated_at timestamptz not null
```

Reglas:

- unicidad por organización y usuario para membresía vigente;
- roles allowlisted;
- estados allowlisted;
- OWNER inicial creado atómicamente con la organización;
- usuario SUSPENDED/BANNED en M02 no obtiene permisos efectivos.

### 5.3 Catálogo interno

Crear y seedear de forma idempotente:

```text
organization_roles
organization_permissions
organization_role_permissions
```

Roles:

```text
OWNER
ADMIN
MANAGER
MEMBER
VIEWER
```

Permisos iniciales:

```text
organization.view
organization.update
organization.view_private
organization.manage_members
organization.invite_members
organization.remove_members
organization.manage_roles
organization.manage_branches
organization.publish
organization.request_verification
organization.close
```

No incluir permisos de adopciones, pagos, marketplace, historias clínicas o servicios futuros.

### 5.4 Historial mínimo

Crear:

```text
organization_status_history
organization_audit_log
```

Registrar:

- actor;
- organización;
- acción;
- valor anterior/nuevo cuando corresponda;
- reason code allowlisted;
- fecha del servidor;
- request id opcional.

No guardar secretos, tokens ni PII innecesaria.

### 5.5 Vinculación legacy

Crear:

```text
organization_resource_links
```

Campos mínimos:

```text
organization_id
resource_type
resource_id
linked_by
created_at
```

Tipos:

```text
SHELTER_LISTING
SERVICE_PROFILE
```

Reglas:

- shelter sin organización sigue válido;
- service profile puede seguir siendo personal;
- un recurso no puede quedar vinculado a dos organizaciones activas;
- AccountType no crea vínculo;
- no migrar datos automáticamente;
- no modificar ownership legacy en esta etapa.

---

## 6. Funciones server-side

Crear como mínimo:

```text
create_organization(...)
update_my_organization(organization_id, ...)
get_my_organizations()
get_public_organization_by_slug(slug)
search_public_organizations(query, type, city)
has_org_permission(organization_id, permission_code)
get_my_org_permissions(organization_id)
request_organization_verification(organization_id)
link_organization_resource(organization_id, resource_type, resource_id)
unlink_organization_resource(organization_id, resource_type, resource_id)
```

### Reglas comunes

- actor desde `auth.uid()`;
- no aceptar actor libre;
- cuenta M02 debe estar habilitada;
- organización y membresía deben estar habilitadas;
- `SECURITY DEFINER`;
- `search_path` fijo;
- grants mínimos;
- deny ante error o ausencia;
- historial para acciones privilegiadas;
- errores seguros;
- sin service role en Android.

### `create_organization`

Debe ser transaccional:

1. validar nombre, slug y tipo;
2. crear organización DRAFT;
3. crear membresía OWNER ACTIVE para `auth.uid()`;
4. registrar auditoría;
5. no permitir un estado inicial privilegiado distinto;
6. no crear listing legacy.

### `has_org_permission`

Debe comprobar:

- cuenta M02 ACTIVE o estado permitido;
- organización no SUSPENDED/CLOSED/REJECTED;
- membresía ACTIVE;
- rol vigente;
- permiso explícito;
- false ante código desconocido o error.

### Perfil público

Solo puede devolver:

- id público;
- slug;
- display name;
- tipo;
- descripción;
- ubicación general;
- logo/cover;
- estado público permitido;
- verificación;
- contacto únicamente si el opt-in correspondiente es true.

Nunca devolver:

- legal name si se define como privado;
- miembros;
- roles;
- invitaciones;
- documentos;
- auditoría;
- datos personales del creador;
- contacto privado;
- campos técnicos internos.

---

## 7. RLS y privilegios

### `organizations`

- propia/membresía: lectura privada controlada;
- ajenas: no SELECT directo de fila completa;
- perfil público mediante RPC o vista allowlist;
- sin INSERT/UPDATE/DELETE directo desde Android;
- escritura por RPC.

### Membresías y permisos

- usuario puede consultar sus propias membresías activas;
- catálogo puede ser legible autenticado si no expone datos sensibles;
- sin escritura directa;
- administración por funciones.

### Historial

- lectura solo con permiso interno apropiado o permiso plataforma futuro;
- sin escritura directa desde cliente.

### Resource links

- lectura/escritura solo mediante permisos internos y RPC.

Evitar recursión RLS. Cuando se necesite consultar membresía desde políticas, usar helpers `SECURITY DEFINER` pequeños, auditados y con `search_path` fijo.

---

## 8. Storage institucional

Crear bucket privado, por ejemplo:

```text
organization-media
```

Paths oficiales:

```text
organizations/{organizationId}/logo/{filename}
organizations/{organizationId}/cover/{filename}
```

Reglas:

- lectura pública solo mediante URL firmada o mecanismo aprobado;
- upload/update/delete solo con permiso `organization.update`;
- no confiar únicamente en el path enviado;
- validar UUID de organización;
- no mezclar con `profile-avatars`;
- no usar bucket genérico público;
- limpiar reemplazos anteriores cuando corresponda;
- no borrar archivos de otra organización.

Android:

- `OrganizationMediaStorageService`;
- devuelve path persistente, no URL eterna;
- genera URL firmada temporal para UI;
- logging sanitizado.

---

## 9. Android autorizado

Implementar Supabase para:

- `OrganizationRepository`;
- `OrganizationMembershipRepository`;
- `OrganizationPermissionRepository`.

Mantener `OrganizationInvitationRepository` en mock/stub hasta Etapa 4.

### UI mínima autorizada

1. Lista “Mis organizaciones”.
2. Crear organización.
3. Editar perfil institucional.
4. Perfil público por slug.
5. Selector básico de organización para abrir detalle.

No implementar todavía:

- gestión completa de miembros;
- invitaciones;
- transferencia de ownership;
- sucursales;
- administración avanzada;
- contexto global “actuando como organización” en toda la app.

### Gates

- AccountType no habilita pantallas organizacionales privilegiadas;
- la existencia de una organización no implica permiso;
- cada acción usa `hasOrgPermission`;
- ante loading/error, negar acciones privilegiadas;
- usuario SUSPENDED/BANNED no administra;
- organización SUSPENDED/CLOSED/REJECTED no publica.

---

## 10. Verificación mínima

Permitir que un OWNER/ADMIN con permiso solicite verificación:

```text
NOT_REQUESTED → PENDING
```

No permitir:

- autoaprobar;
- marcar VERIFIED desde Android;
- subir documentos avanzados;
- decidir rechazo.

La resolución pertenece a M04.

---

## 11. Pruebas requeridas

### 11.1 Unitarias Android

- creación válida;
- slug duplicado;
- OTHER sin descripción;
- repositorio remoto mapea errores;
- deny ante error;
- usuario sin membresía;
- VIEWER no actualiza;
- OWNER actualiza;
- organización suspendida bloquea;
- cuenta M02 suspendida bloquea;
- proyección pública sin PII;
- contacto opt-in;
- Storage path;
- link legacy sin AccountType;
- request verification solo PENDING.

### 11.2 SQL/RLS

Crear:

```text
/docs/04-calidad/M03-pruebas-persistencia-rls-organizaciones.md
```

Casos mínimos:

- create crea OWNER atómicamente;
- slug único case-insensitive;
- usuario externo no lee fila privada;
- perfil público allowlist;
- email/teléfono privados por defecto;
- OWNER actualiza;
- MEMBER/VIEWER no actualizan;
- AccountType no concede permiso;
- platform role M02 no concede membresía;
- estado de cuenta bloquea;
- estado de organización bloquea;
- grants directos denegados;
- `search_path` fijo;
- RLS no recursiva;
- resource link único;
- service personal sigue válido;
- Storage ownership entre organizaciones.

### 11.3 Calidad local

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

No ocultar errores con suppress global ni eliminar tests.

### 11.4 Staging

Si no existe acceso autorizado:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

No inventar resultados.

---

## 12. Orden de implementación

### Bloque 0 — Git y auditoría SQL

- consolidar Etapa 2;
- crear rama;
- confirmar número de migración;
- revisar dependencias 014–018.

### Bloque 1 — Tablas y seeds

- organizations;
- memberships;
- roles/permisos;
- historial;
- resource links;
- tests SQL iniciales.

### Bloque 2 — RPC y RLS

- create/update/get/search;
- has_org_permission;
- verification request;
- resource links;
- grants;
- tests.

### Bloque 3 — Storage

- bucket;
- policies;
- Android service;
- tests.

### Bloque 4 — Repositorios y UI mínima

- repos Supabase;
- mis organizaciones;
- crear/editar;
- perfil público;
- gates;
- tests.

### Bloque 5 — Calidad y cierre

- build;
- tests;
- lint;
- documentación;
- cierre.

---

## 13. Fuera de alcance

- Invitaciones reales.
- Gestión completa de miembros.
- Transferencia de OWNER.
- Sucursales persistidas.
- Verificación documental avanzada.
- M04.
- Adopciones, refugios operativos o casos.
- Servicios, turnos, pagos, marketplace.
- GPS o mapas.
- Nuevo backend.
- Hilt o Retrofit.
- Merge a `main`.
- Producción.

---

## 14. Criterios de aceptación

- [ ] Commit base verificado.
- [ ] Rama limpia sin WIP.
- [ ] Migración nueva sin editar aplicadas.
- [ ] `organizations` creada como entidad nueva.
- [ ] OWNER inicial transaccional.
- [ ] Membresías y permisos normalizados.
- [ ] Roles internos separados de M02.
- [ ] `has_org_permission` deny-by-default.
- [ ] Perfil público allowlist.
- [ ] Contactos privados por defecto.
- [ ] Storage privado con ownership.
- [ ] AccountType no concede permiso.
- [ ] PlatformRole no concede membresía.
- [ ] Resource links sin migración automática.
- [ ] Verificación solo puede solicitarse.
- [ ] UI mínima implementada.
- [ ] Invitaciones/equipos completos no iniciados.
- [ ] Tests anteriores conservados.
- [ ] Nuevos tests aprobados.
- [ ] assembleDebug aprobado.
- [ ] lintDebug con 0 errores.
- [ ] Remoto declarado honestamente.
- [ ] No se inició M04.
- [ ] Cierre creado.

---

## 15. Entregable y parada

Crear exactamente:

```text
/docs/02-arquitectura/M03-etapa-3-cierre.md
```

Debe incluir:

- rama y commits;
- migración;
- tablas;
- seeds;
- RPC;
- RLS;
- Storage;
- repositorios;
- UI;
- tests;
- build/lint;
- estado remoto;
- riesgos;
- checklist.

No iniciar Etapa 4 ni M04.

Detenerse al crear:

```text
/docs/02-arquitectura/M03-etapa-3-cierre.md
```
