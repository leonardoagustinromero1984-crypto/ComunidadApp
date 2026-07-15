# LEOVER — M03 Organizaciones y Equipos

**Módulo:** M03 — Organizaciones y Equipos  
**Versión:** 1.0  
**Estado:** Autorizado únicamente para Etapa 1 — Auditoría y diseño  
**Dependencias:** M00, M01 y M02 cerrados a nivel código y calidad local  
**Backend oficial:** Supabase  
**Condición de release heredada:** validación staging de migraciones 014–018 pendiente  
**Regla principal:** auditar y reutilizar lo existente antes de crear modelos, tablas, repositorios o pantallas.

---

## 1. Objetivo

M03 permitirá que una persona usuaria cree, administre o integre organizaciones dentro de Leover sin mezclar:

- roles de plataforma de M02;
- capacidades personales;
- permisos internos de una organización;
- perfiles individuales;
- módulos funcionales futuros.

El módulo deberá establecer una base segura para refugios, asociaciones, veterinarias, comercios, equipos de rescate y prestadores colectivos.

M03 no implementa todavía adopciones, turnos, pagos, marketplace, historias clínicas ni servicios profesionales completos.

---

## 2. Documentos obligatorios

Leer antes de trabajar:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/03-modulos/M01-Identidad-y-Autenticacion.md`
6. `/docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`
7. ADR-0001 a ADR-0005
8. Este documento.

Las decisiones vigentes de M00–M02 tienen prioridad sobre instrucciones históricas incompatibles.

---

## 3. Principios

1. Una organización no reemplaza al usuario.
2. Una persona puede pertenecer a varias organizaciones.
3. Los roles internos de organización no son roles de plataforma.
4. Ninguna organización otorga permisos globales por sí sola.
5. Toda acción se evalúa con usuario, organización y rol interno.
6. Deny-by-default.
7. Las invitaciones deben ser seguras, revocables y con expiración.
8. Una organización no puede auto-verificarse.
9. Los datos públicos se exponen mediante allowlist.
10. No duplicar tablas o repositorios ya existentes sin auditoría.

---

## 4. Tipos iniciales de organización

Catálogo inicial propuesto:

```text
SHELTER
RESCUE_GROUP
NGO
VETERINARY_CLINIC
PET_SHOP
TRAINING_CENTER
WALKER_AGENCY
OTHER
```

Reglas:

- el tipo describe la actividad, no la autoridad;
- no usar `AccountType` como sustituto;
- no habilitar funcionalidades futuras automáticamente;
- el catálogo debe ser extensible;
- `OTHER` requiere descripción;
- tipos sensibles pueden requerir verificación posterior.

---

## 5. Estados de organización

```text
DRAFT
ACTIVE
RESTRICTED
SUSPENDED
CLOSED
REJECTED
```

- `DRAFT`: no publicable.
- `ACTIVE`: operativa.
- `RESTRICTED`: acceso limitado.
- `SUSPENDED`: bloqueada.
- `CLOSED`: cierre solicitado o definitivo.
- `REJECTED`: alta rechazada por verificación.

Los cambios administrativos deben quedar auditados.

---

## 6. Roles internos

Roles iniciales:

```text
OWNER
ADMIN
MANAGER
MEMBER
VIEWER
```

Ejemplo de permisos internos:

```text
organization.view
organization.update
organization.manage_members
organization.invite_members
organization.remove_members
organization.manage_roles
organization.manage_branches
organization.publish
organization.view_private
organization.close
```

Reglas:

- `OWNER` no equivale a `SUPERADMIN`.
- Un `ADMIN` de organización no administra la plataforma.
- El último OWNER no puede eliminarse sin transferencia o cierre.
- Nadie puede autoelevarse.
- Las operaciones privilegiadas se validan server-side.
- La UI no es la seguridad final.

---

## 7. Alcance funcional futuro de M03

### 7.1 Alta de organización

Datos mínimos:

- nombre legal o institucional;
- nombre público;
- slug único;
- tipo;
- descripción;
- email institucional opcional;
- teléfono institucional opcional;
- localidad;
- provincia;
- país;
- logo;
- portada opcional;
- privacidad;
- aceptación de reglas de organizaciones;
- creador.

### 7.2 Perfil público

Debe exponer solo allowlist:

- nombre público;
- slug;
- tipo;
- descripción;
- ubicación general;
- logo;
- portada;
- estado público;
- verificación;
- enlaces públicos aprobados;
- estadísticas no sensibles autorizadas.

No exponer:

- email interno;
- teléfono privado;
- miembros privados;
- notas administrativas;
- documentos de verificación;
- roles internos;
- estados técnicos.

### 7.3 Equipos y membresías

- miembros múltiples;
- usuario en varias organizaciones;
- rol interno;
- estado de membresía;
- fecha de ingreso;
- invitador;
- expiración;
- suspensión;
- salida voluntaria;
- remoción;
- transferencia de ownership.

### 7.4 Invitaciones

Estados:

```text
PENDING
ACCEPTED
DECLINED
REVOKED
EXPIRED
```

Requisitos:

- token seguro;
- expiración;
- un solo uso;
- email o usuario objetivo;
- no enumerar cuentas;
- revocable;
- registrar actor;
- no otorgar privilegios hasta aceptar.

### 7.5 Verificación

Estados:

```text
NOT_REQUESTED
PENDING
VERIFIED
REJECTED
EXPIRED
```

La verificación no se implementará por completo hasta definir documentos y moderación de M04.

M03 solo debe dejar el contrato y flujo base.

### 7.6 Sucursales

Una organización puede tener cero o más sedes.

Datos:

- nombre;
- dirección;
- localidad;
- provincia;
- país;
- coordenadas opcionales;
- teléfono;
- horario;
- estado.

No integrar mapas ni geolocalización en M03.

### 7.7 Cierre de organización

- solo OWNER autorizado;
- confirmación;
- transferencia o resolución de recursos;
- revocación de invitaciones;
- tratamiento de miembros;
- historial;
- no eliminar datos de módulos futuros sin política.

---

## 8. Separación de responsabilidades

| Concepto | Propietario |
|---|---|
| Identidad y sesión | M01 |
| Perfil y permisos plataforma | M02 |
| Organización, equipo y rol interno | M03 |
| Moderación/verificación avanzada | M04 |
| Mascotas | M08 |
| Refugios/casos de rescate | M16 |
| Servicios profesionales | M22 |
| Pagos | M24 |
| Marketplace | M25 |

---

## 9. Modelo técnico propuesto para revisar

No implementar antes de la auditoría.

Posibles tablas:

```text
organizations
organization_types
organization_memberships
organization_roles
organization_permissions
organization_role_permissions
organization_invitations
organization_branches
organization_verification_requests
organization_status_history
organization_audit_log
```

No crear todas automáticamente. La auditoría debe demostrar cuáles son necesarias y cuáles ya tienen equivalentes.

---

## 10. Seguridad

- actor derivado de `auth.uid()`;
- `organization_id` nunca sustituye la identidad del actor;
- RLS por membresía activa;
- deny-by-default;
- ownership protegido;
- último OWNER protegido;
- invitaciones con tokens no almacenados en texto plano cuando sea posible;
- sin service role en Android;
- sin PII en logs;
- perfiles públicos por RPC/vista allowlist;
- acciones administrativas con historial;
- estado de cuenta M02 respetado;
- usuario SUSPENDED/BANNED no administra organizaciones;
- permisos de plataforma y organización evaluados por separado.

---

## 11. Android

Reutilizar:

- `UserRepository`;
- `PermissionRepository`;
- `AuthorizationService`;
- `AppResult`;
- `AppError`;
- `AppLogger`;
- componentes UI de M00;
- gates M01/M02.

Posibles dominios:

```text
domain/organization/
domain/organization/authorization/
```

Posibles repositorios:

```text
OrganizationRepository
OrganizationMembershipRepository
OrganizationInvitationRepository
```

No duplicar repositorios de perfil o autenticación.

---

## 12. Pruebas futuras

### Dominio

- creación válida;
- slug;
- roles internos;
- deny-by-default;
- último OWNER;
- invitaciones;
- expiración;
- transferencia;
- usuario suspendido;
- organización suspendida.

### SQL/RLS

- usuario externo no lee datos privados;
- miembro VIEWER no actualiza;
- MEMBER no invita;
- ADMIN no elimina último OWNER;
- OWNER transfiere;
- token vencido no acepta;
- invitación revocada no acepta;
- rol interno no concede permisos plataforma;
- permisos plataforma no implican membresía;
- historial obligatorio.

### UI

- onboarding organización;
- lista de organizaciones;
- cambio de contexto;
- miembros;
- invitaciones;
- error/retry;
- dark mode;
- accesibilidad.

---

## 13. Fuera de alcance de M03

- adopciones;
- refugios operativos y gestión de casos;
- turnos;
- historias clínicas;
- pagos;
- marketplace;
- mapa;
- GPS;
- chat;
- feed;
- verificación documental avanzada;
- moderación completa;
- M04;
- nuevo backend;
- Hilt;
- Retrofit;
- renombre de paquete.

---

## 14. Etapas de M03

### Etapa 1 — Auditoría y diseño

Crear:

```text
/docs/02-arquitectura/M03-auditoria-inicial.md
```

No modificar funcionalidad.

### Etapa 2 — Contratos de dominio y autorización interna

- organización;
- membresía;
- roles internos;
- permisos;
- invitaciones;
- validadores;
- tests.

### Etapa 3 — Persistencia, RLS y perfil de organización

- migraciones;
- RPC;
- RLS;
- perfil público;
- Storage;
- tests.

### Etapa 4 — Equipos, invitaciones y administración

- membresías;
- invitaciones;
- ownership;
- sucursales;
- UI;
- tests.

### Etapa 5 — Calidad y cierre

- staging;
- seguridad;
- documentación;
- cierre final.

---

## 15. Instrucción vigente: solo Etapa 1

Trabajar en:

```text
m03/organizaciones-equipos-auditoria
```

Auditar:

- tablas de refugios, tiendas, veterinarias, proveedores u organizaciones;
- AccountType y active_modules;
- perfiles institucionales existentes;
- membresías, equipos o colaboradores;
- permisos y gates M02;
- rutas, pantallas y ViewModels;
- repositorios;
- RLS;
- Storage;
- invitaciones;
- branches/sucursales;
- verificaciones;
- reportes/moderación relacionados;
- migraciones 001–018;
- tests;
- documentación legacy.

No hacer:

- migraciones;
- RLS;
- pantallas;
- repositorios nuevos;
- roles internos;
- M04;
- merge a main;
- staging;
- producción.

Verificar:

```text
assembleDebug
testDebugUnitTest
lintDebug
```

Detenerse cuando exista:

```text
/docs/02-arquitectura/M03-auditoria-inicial.md
```

La auditoría debe incluir:

- inventario;
- reutilización;
- duplicaciones;
- riesgos;
- diferencias contra M03;
- propuesta de modelo;
- archivos a crear/modificar;
- plan por etapas;
- decisiones que requieren aprobación;
- estado Git;
- build/tests/lint;
- estado remoto declarado honestamente.
