# LEOVER — M03 Etapa 2: Contratos de organizaciones, membresías y autorización

**Módulo:** M03 — Organizaciones y Equipos  
**Etapa:** 2 — Dominio, validaciones y autorización interna  
**Estado de entrada:** Auditoría inicial aprobada  
**Backend oficial:** Supabase  
**Objetivo:** definir contratos sólidos de organización, membresía, roles internos, invitaciones y permisos, sin crear todavía migraciones, RLS, repositorios remotos ni pantallas completas.

---

## 1. Documentos obligatorios

Leer:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-auditoria-inicial.md`
6. `/docs/03-modulos/M03-Organizaciones-y-Equipos.md`
7. ADR-0001 a ADR-0005
8. Este documento.

---

## 2. Decisiones aprobadas

### D-M03-01 — `organizations` será una entidad nueva

No reutilizar `shelters` ni `service_profiles` como tabla raíz de organización.

- `organizations` representará identidad institucional, equipo y permisos.
- `shelters` seguirá siendo un listing/recurso funcional.
- En Etapa 3 podrá agregarse `organization_id` nullable a `shelters`.
- No fusionar ni eliminar datos legacy.

### D-M03-02 — Servicios personales u organizacionales

`service_profiles` podrá pertenecer:

- a una persona;
- a una organización;
- nunca a ambas como propietarios principales simultáneos.

La propiedad exacta se resolverá en persistencia mediante una restricción coherente.

No obligar a todo profesional independiente a crear una organización.

### D-M03-03 — Sin migración automática desde AccountType

No crear organizaciones automáticamente por `AccountType`.

Motivos:

- puede generar organizaciones falsas o duplicadas;
- AccountType no demuestra identidad institucional;
- muchos datos legacy están incompletos.

En etapas posteriores se podrá ofrecer un asistente explícito:

```text
Convertir mi perfil/listing en una organización
```

Debe requerir confirmación y revisión de datos.

### D-M03-04 — Foster home es capacidad personal

`FOSTER_HOME` se mantiene como capacidad/listing personal.

No crear una organización automáticamente.

Un equipo formal de tránsito o rescate puede crear una organización tipo:

```text
RESCUE_GROUP
```

### D-M03-05 — Verificación mínima en M03

M03 implementará solamente:

- estado de verificación;
- solicitud básica;
- historial;
- bloqueo de auto-verificación.

La carga y revisión documental avanzada pertenece a M04.

### D-M03-06 — Contacto público opt-in

Los perfiles públicos usarán allowlist estricta.

Email y teléfono institucional:

- privados por defecto;
- públicos solo mediante consentimiento explícito;
- separados de los datos personales del usuario;
- nunca derivados automáticamente del email de Auth.

### D-M03-07 — Roles internos separados

Los roles internos:

```text
OWNER
ADMIN
MANAGER
MEMBER
VIEWER
```

no son roles de plataforma M02.

Ningún rol de organización concede:

- MODERATOR;
- ADMIN de plataforma;
- SUPERADMIN;
- permisos globales.

### D-M03-08 — Deny-by-default

Toda autorización interna debe evaluar:

- usuario autenticado;
- cuenta M02 habilitada;
- organización habilitada;
- membresía activa;
- rol interno vigente;
- permiso interno explícito.

Ante error o ausencia, negar.

---

## 3. Protección Git

Antes de modificar:

1. Consolidar auditoría M03 en commit.
2. Preservar cambios locales.
3. Crear rama:

```text
m03/etapa-2-contratos-organizaciones
```

4. No mezclar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. Registrar SHA base.

---

## 4. Alcance autorizado

### 4.1 Dominio organización

Crear en:

```text
domain/organization/
```

Modelos mínimos:

- `Organization`
- `PublicOrganization`
- `OrganizationId`
- `OrganizationSlug`
- `OrganizationType`
- `OrganizationStatus`
- `OrganizationVerificationStatus`
- `OrganizationContactVisibility`
- `OrganizationBranch`
- `OrganizationValidationError`

Tipos:

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

Estados:

```text
DRAFT
ACTIVE
RESTRICTED
SUSPENDED
CLOSED
REJECTED
```

Verificación:

```text
NOT_REQUESTED
PENDING
VERIFIED
REJECTED
EXPIRED
```

### 4.2 Slug

Reglas iniciales:

- 3 a 50 caracteres;
- minúsculas;
- letras a-z, números y guion;
- comenzar y terminar con letra o número;
- sin guiones consecutivos;
- sin espacios;
- normalización central;
- palabras reservadas configurables;
- único case-insensitive en persistencia futura.

No consultar disponibilidad remota todavía.

### 4.3 Membresías

Crear:

- `OrganizationMembership`
- `OrganizationMembershipStatus`
- `OrganizationRoleCode`
- `OrganizationPermissionCode`
- `OrganizationAuthorizationContext`
- `OrganizationAuthorizationDecision`
- `OrganizationAuthorizationService`

Estados de membresía:

```text
ACTIVE
INVITED
SUSPENDED
LEFT
REMOVED
```

Roles:

```text
OWNER
ADMIN
MANAGER
MEMBER
VIEWER
```

### 4.4 Permisos internos

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

No agregar permisos de adopciones, servicios, pagos o marketplace.

### 4.5 Reglas de jerarquía

- OWNER puede administrar todo dentro de la organización.
- ADMIN no puede remover ni degradar al último OWNER.
- ADMIN no puede elevarse a OWNER.
- MANAGER administra operación autorizada, no ownership.
- MEMBER participa sin administrar miembros.
- VIEWER solo lectura autorizada.
- El último OWNER está protegido.
- Transferir ownership requiere confirmación explícita.
- Usuario SUSPENDED/BANNED en M02 no administra organizaciones.
- Organización SUSPENDED/CLOSED no publica.

### 4.6 Invitaciones

Crear contratos:

- `OrganizationInvitation`
- `OrganizationInvitationStatus`
- `OrganizationInvitationToken`
- `OrganizationInvitationCommand`

Estados:

```text
PENDING
ACCEPTED
DECLINED
REVOKED
EXPIRED
```

Reglas:

- expiración obligatoria;
- un solo uso;
- revocable;
- no otorgar membresía antes de aceptar;
- email objetivo opcional o userId objetivo;
- evitar enumeración de cuentas;
- token no expuesto en logs;
- no almacenar token plano en persistencia futura cuando sea posible.

### 4.7 Relación con listings legacy

Crear solo contratos de vinculación:

- `OrganizationResourceLink`
- `OrganizationResourceType`

Tipos iniciales:

```text
SHELTER_LISTING
SERVICE_PROFILE
```

No modificar todavía tablas ni repositorios legacy.

Reglas:

- un shelter listing puede quedar sin organización;
- un service profile puede ser personal u organizacional;
- AccountType no crea vínculo;
- ownership legacy se conserva hasta migración explícita.

### 4.8 Repositorios

Crear únicamente interfaces y mocks:

- `OrganizationRepository`
- `OrganizationMembershipRepository`
- `OrganizationInvitationRepository`
- `OrganizationPermissionRepository`

Mocks deterministas para tests.

No implementar Supabase ni SQL todavía.

### 4.9 Contención UI inmediata

Revisar gates legacy para asegurar:

- AccountType no simula membresía organizacional;
- active_modules no simula rol interno;
- PublishViewModel no debe asumir shelterId solo por AccountType;
- cuando falte organización/membresía real, denegar acciones organizacionales.

Se permiten correcciones pequeñas de seguridad sin crear UI completa.

---

## 5. Fuera de alcance

No realizar:

- migraciones SQL;
- tablas `organizations`;
- RLS;
- Storage de logos;
- UI de creación completa;
- invitaciones reales;
- sucursales persistidas;
- vinculación real con shelters/services;
- verificación documental;
- M04;
- mapas, GPS o pagos;
- nuevo backend;
- Hilt/Retrofit;
- merge a main.

---

## 6. Pruebas mínimas

### Organización y slug

- nombre válido/inválido;
- tipo OTHER con descripción requerida;
- slug normalizado;
- slug corto/largo;
- slug reservado;
- guiones consecutivos;
- estados válidos.

### Autorización interna

- deny-by-default;
- OWNER permitido;
- ADMIN limitado;
- MANAGER sin roles;
- MEMBER sin administración;
- VIEWER solo lectura;
- último OWNER protegido;
- usuario M02 suspendido/baneado denegado;
- organización suspendida/cerrada denegada;
- AccountType no concede permiso;
- active_modules no concede permiso;
- rol M02 no implica membresía.

### Invitaciones

- expiración;
- aceptación única;
- revocación;
- token inválido;
- invitación ya aceptada;
- rol invitado permitido;
- no autoelevación.

### Listados legacy

- shelter sin organization sigue válido;
- service personal válido;
- service organizacional válido;
- propiedad dual inválida;
- AccountType no genera vínculo.

---

## 7. Orden de implementación

### Bloque 0

- consolidar auditoría;
- crear rama;
- confirmar WIP excluido.

### Bloque 1

- modelos de organización;
- tipos, estados, slug;
- validadores;
- tests.

### Bloque 2

- membresías;
- roles y permisos;
- autorización deny-by-default;
- tests.

### Bloque 3

- invitaciones;
- jerarquía;
- último OWNER;
- tests.

### Bloque 4

- contratos de vinculación legacy;
- interfaces/mocks;
- contención de gates peligrosos;
- tests.

### Bloque 5

- build;
- tests;
- lint;
- documentación.

Crear:

```text
/docs/02-arquitectura/M03-etapa-2-cierre.md
```

---

## 8. Criterios de aceptación

- [ ] Auditoría consolidada.
- [ ] Rama limpia.
- [ ] Organization separado de UserProfile.
- [ ] Tipos y estados tipados.
- [ ] Slug central y testeado.
- [ ] Roles internos separados de M02.
- [ ] Permisos internos tipados.
- [ ] Deny-by-default.
- [ ] Último OWNER protegido.
- [ ] AccountType no concede membresía.
- [ ] active_modules no concede membresía.
- [ ] Invitaciones tipadas y expirables.
- [ ] Foster home permanece capacidad personal.
- [ ] Contratos de link legacy sin migración automática.
- [ ] No se crearon migraciones.
- [ ] No se inició M04.
- [ ] Tests anteriores conservados.
- [ ] Nuevos tests aprobados.
- [ ] assembleDebug aprobado.
- [ ] lintDebug con 0 errores.
- [ ] Cierre creado.

---

## 9. Parada

No iniciar Etapa 3 ni M04.

Detenerse al crear:

```text
/docs/02-arquitectura/M03-etapa-2-cierre.md
```
