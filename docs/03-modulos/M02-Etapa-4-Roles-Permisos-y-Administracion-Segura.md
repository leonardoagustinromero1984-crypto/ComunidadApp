# LEOVER — M02 Etapa 4: Roles, permisos y administración segura

**Módulo:** M02 — Usuarios, Roles y Permisos  
**Etapa:** 4 — Roles reales, permisos, estados de cuenta y administración  
**Estado de entrada:** Etapa 3 aprobada  
**Backend oficial:** Supabase  
**Objetivo:** implementar autorización real de plataforma y reemplazar los stubs locales por controles server-side deny-by-default.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-auditoria-inicial.md`
5. `/docs/02-arquitectura/M02-etapa-2-cierre.md`
6. `/docs/02-arquitectura/M02-etapa-3-cierre.md`
7. `/docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`
8. `/docs/03-modulos/M02-Etapa-2-Contratos-Perfil-y-Autorizacion.md`
9. `/docs/03-modulos/M02-Etapa-3-Perfil-Onboarding-Privacidad-y-RLS.md`
10. ADR-0001 a ADR-0005
11. Este documento.

---

## 2. Condición previa obligatoria

Las migraciones `015_user_profile_foundation.sql`, `016_user_profile_security.sql` y `017_profile_avatar_storage.sql` existen en el repositorio, pero no fueron verificadas en Supabase remoto.

Antes de crear o aplicar una migración nueva:

1. comprobar si 015–017 fueron aplicadas en algún entorno compartido;
2. no editar una migración ya aplicada;
3. si están solo en repo, se permite corregirlas antes del primer deploy;
4. si ya están aplicadas, crear migración correctiva consecutiva;
5. ejecutar checklist SQL/RLS/Storage en staging;
6. documentar con honestidad todo lo que no pueda verificarse.

No desplegar producción en esta etapa.

---

## 3. Protección Git

1. Consolidar Etapa 3 en un commit identificable.
2. Preservar cambios locales.
3. Crear rama:

```text
m02/etapa-4-roles-permisos-administracion
```

4. Confirmar que no contiene `wip/gps-mapas-pagos`.
5. Registrar SHA base en el cierre.

---

## 4. Decisiones vigentes

### D-M02-11 — Roles normalizados

Crear y utilizar:

```text
platform_roles
permissions
role_permissions
user_role_assignments
```

Roles iniciales:

```text
USER
MODERATOR
ADMIN
SUPERADMIN
```

No usar:

- `account_type`;
- `active_modules`;
- `LeoverModule.ADMIN`;
- metadata de Auth;
- flags locales;

como autoridad de permisos.

### D-M02-12 — Deny-by-default en servidor

Toda comprobación de autorización debe negar cuando:

- no existe usuario;
- la cuenta no está ACTIVE;
- no existe rol;
- no existe permiso;
- la asignación expiró o fue revocada;
- ocurre un error de consulta.

La UI nunca es la seguridad final.

### D-M02-13 — RPC `has_permission`

Crear función server-side:

```text
has_permission(permission_code text)
```

Debe:

- usar `auth.uid()`;
- no aceptar UID libre como autoridad;
- comprobar cuenta ACTIVE;
- comprobar asignación vigente;
- comprobar permiso por rol;
- usar `SECURITY DEFINER`;
- fijar `search_path`;
- no exponer información de otros usuarios;
- devolver false ante ausencia;
- tener pruebas SQL.

### D-M02-14 — Asignación privilegiada

Solo SUPERADMIN puede:

- asignar ADMIN;
- asignar SUPERADMIN;
- revocar roles administrativos.

ADMIN puede asignar/revocar MODERATOR únicamente si posee permisos explícitos.

Nadie puede:

- asignarse roles;
- elevar su propio rol;
- modificar directamente las tablas de asignaciones desde Android.

Las operaciones se realizan mediante RPC o Edge Function server-side.

### D-M02-15 — Bootstrap inicial

El primer SUPERADMIN no se crea desde la aplicación.

Debe provisionarse mediante:

- migración controlada con UUID explícito configurado fuera del repositorio, o
- script administrativo documentado, o
- operación manual auditada en Supabase.

No versionar email, UUID real ni secretos.

### D-M02-16 — Estados de cuenta

Estados:

```text
ACTIVE
RESTRICTED
SUSPENDED
BANNED
```

Reglas:

- USER no puede modificarlos;
- MODERATOR no puede suspender administradores;
- ADMIN no puede modificar SUPERADMIN;
- SUPERADMIN puede modificar estados salvo bloquearse a sí mismo si eso deja la plataforma sin administración;
- toda modificación crea historial;
- BANNED y SUSPENDED bloquean acceso;
- RESTRICTED mantiene acceso limitado según permisos.

### D-M02-17 — Historial y auditoría

Crear:

```text
user_status_history
role_assignment_history
```

Registrar:

- actor;
- objetivo;
- acción;
- valor anterior/nuevo;
- código de razón;
- fecha servidor;
- request/correlation ID cuando esté disponible.

No guardar texto libre sensible como motivo principal. Usar códigos allowlisted y una nota opcional limitada.

### D-M02-18 — Android consulta repositorio remoto

`PermissionRepository` deja de ser stub y consulta las funciones server-side.

Debe:

- cachear por sesión de manera breve;
- invalidar al cambiar sesión;
- negar ante error;
- no confiar en permisos almacenados localmente;
- no usar claims JWT todavía.

---

## 5. Alcance autorizado

### 5.1 Esquema SQL

Crear migración consecutiva, sin asumir número fijo.

Tablas mínimas:

#### `platform_roles`

```text
id uuid primary key
code text unique not null
name text not null
description text null
is_system boolean not null default true
created_at timestamptz not null
```

#### `permissions`

```text
id uuid primary key
code text unique not null
description text null
created_at timestamptz not null
```

#### `role_permissions`

```text
role_id uuid not null
permission_id uuid not null
primary key (role_id, permission_id)
```

#### `user_role_assignments`

```text
id uuid primary key
user_id uuid not null references auth.users(id) on delete cascade
role_id uuid not null
assigned_by uuid null references auth.users(id)
assigned_at timestamptz not null
expires_at timestamptz null
revoked_at timestamptz null
revoked_by uuid null references auth.users(id)
```

#### `user_status_history`

```text
id uuid primary key
user_id uuid not null
previous_status text null
new_status text not null
reason_code text not null
changed_by uuid not null
changed_at timestamptz not null
request_id text null
```

#### `role_assignment_history`

Registrar asignación, revocación y expiración.

### 5.2 Seeds

Seed idempotente:

Roles:

- USER
- MODERATOR
- ADMIN
- SUPERADMIN

Permisos mínimos:

```text
profile.read.own
profile.update.own
profile.read.public
moderation.view
moderation.manage_reports
users.view_private
users.change_status
roles.view
roles.assign
roles.revoke
audit.view
```

Asignaciones iniciales sugeridas:

- USER: perfil propio/público.
- MODERATOR: USER + moderación.
- ADMIN: MODERATOR + estados y roles limitados.
- SUPERADMIN: todos los permisos.

No otorgar permisos de módulos futuros.

### 5.3 Funciones server-side

Crear, como mínimo:

```text
has_permission(permission_code text)
get_my_permissions()
get_my_platform_roles()
assign_platform_role(target_user_id, role_code, expires_at, reason_code)
revoke_platform_role(target_user_id, role_code, reason_code)
change_user_account_status(target_user_id, new_status, reason_code)
```

Reglas:

- UID actor siempre desde `auth.uid()`;
- validación de jerarquía;
- evitar autoelevación;
- transacciones;
- historial;
- códigos allowlisted;
- errores técnicos seguros;
- `SECURITY DEFINER` + `search_path` fijo;
- permisos EXECUTE mínimos.

### 5.4 RLS

Tablas de catálogo:

- lectura autenticada controlada;
- escritura solo por funciones privilegiadas.

Assignments e historiales:

- usuario puede leer sus roles vigentes;
- MODERATOR/ADMIN/SUPERADMIN solo según permisos;
- sin INSERT/UPDATE/DELETE directos desde cliente;
- administración solo por RPC.

### 5.5 `account_status`

Conectar el campo de `public.users` creado en Etapa 3:

- lectura propia;
- cambios solo por RPC;
- SessionViewModel refresca estado;
- gates existentes:
  - ACTIVE → acceso normal;
  - RESTRICTED → acceso limitado;
  - SUSPENDED → bloqueado;
  - BANNED → bloqueado.

No permitir que `update_my_profile` modifique `account_status`.

### 5.6 Android

Implementar versión Supabase de `PermissionRepository`.

Debe exponer:

- roles propios;
- permisos propios;
- `hasPermission(code)`;
- refresh;
- invalidación al logout.

Crear repositorio administrativo separado solo si es necesario, por ejemplo:

```text
PlatformAdministrationRepository
```

No mezclar administración con `UserRepository`.

### 5.7 Gate de navegación

Aplicar permiso real en:

- botón de moderación;
- ruta directa de moderación;
- acciones de reportes;
- pantalla de administración;
- cambio de estado;
- asignación de roles.

Deny-by-default mientras carga o ante error.

### 5.8 UI administrativa mínima

Pantallas autorizadas:

1. Lista/búsqueda de usuarios para administración.
2. Detalle administrativo de usuario.
3. Estado de cuenta.
4. Roles vigentes.
5. Asignar/revocar roles según permiso.
6. Historial básico.

Reglas:

- no exponer password, token o metadata sensible;
- email/teléfono solo con `users.view_private`;
- confirmar acciones destructivas;
- mostrar actor, fecha y motivo codificado;
- impedir acciones no autorizadas desde UI y servidor.

No crear un panel empresarial complejo.

---

## 6. Fuera de alcance

- Organizaciones y miembros M03.
- Roles internos de refugios/comercios.
- Capacidades RESCUER/ORG/PRO/BUSINESS.
- Marketplace, pagos, GPS o mapas.
- OAuth/MFA.
- Claims JWT de permisos.
- Nuevo backend.
- Hilt/Retrofit.
- Producción.
- Moderación avanzada de M04 fuera de los gates mínimos.

---

## 7. Pruebas requeridas

### 7.1 Dominio/Android

- USER denegado en moderación.
- MODERATOR permitido.
- ADMIN limitado por jerarquía.
- SUPERADMIN permitido.
- error remoto → deny.
- cache invalidada al logout.
- cuenta SUSPENDED/BANNED bloqueada.
- RESTRICTED conserva gate correspondiente.
- navegación directa rechazada.
- datos privados ocultos sin permiso.
- ViewModels de asignación y estado.

### 7.2 SQL/RLS

Crear:

```text
/docs/04-calidad/M02-pruebas-roles-permisos-administracion.md
```

Casos mínimos:

- seed idempotente;
- USER no autoasigna;
- MODERATOR no asigna ADMIN;
- ADMIN no asigna SUPERADMIN;
- SUPERADMIN asigna/revoca;
- actor desde `auth.uid()`;
- assignments revocados/expirados no otorgan permisos;
- cuenta no ACTIVE no obtiene permisos;
- historial obligatorio;
- UPDATE directo denegado;
- usuario A no lee historial B;
- `has_permission` false ante código desconocido;
- `search_path` seguro;
- RLS no recursiva.

### 7.3 Staging

Validar en orden:

1. 014 de M01 si corresponde.
2. 015–017.
3. Nueva migración de Etapa 4.
4. Seed.
5. RPC y RLS.
6. Storage.
7. Android con cuentas de prueba USER/MODERATOR/ADMIN/SUPERADMIN.

No usar producción ni cuentas reales.

---

## 8. Orden de implementación

### Bloque 0 — Git y staging plan

- consolidar Etapa 3;
- rama limpia;
- verificar estado de migraciones 014–017;
- documentar plan de aplicación.

### Bloque 1 — SQL catálogo y seed

- roles;
- permisos;
- relaciones;
- assignments;
- historiales;
- tests SQL.

### Bloque 2 — RPC y RLS

- `has_permission`;
- roles propios;
- asignar/revocar;
- cambio de estado;
- jerarquía;
- historial;
- tests.

### Bloque 3 — Android repositorios y gates

- PermissionRepository Supabase;
- administración;
- navegación;
- SessionViewModel;
- tests.

### Bloque 4 — UI administrativa mínima

- usuarios;
- roles;
- estado;
- historial;
- tests.

### Bloque 5 — Calidad y cierre

- build;
- unit tests;
- lint;
- staging documentado;
- cierre Etapa 4.

Crear:

```text
/docs/02-arquitectura/M02-etapa-4-cierre.md
```

No cerrar M02 todavía hasta una Etapa 5 de calidad y validación final.

---

## 9. Criterios de aceptación

- [ ] Etapa 3 consolidada.
- [ ] Rama limpia.
- [ ] Estado de migraciones 014–017 documentado.
- [ ] Roles normalizados.
- [ ] Permisos normalizados.
- [ ] Seeds idempotentes.
- [ ] Sin autoridad basada en AccountType/active_modules.
- [ ] `has_permission` server-side.
- [ ] Actor derivado de `auth.uid()`.
- [ ] Autoelevación bloqueada.
- [ ] Jerarquía de roles aplicada.
- [ ] Assignments revocados/expirados no autorizan.
- [ ] Account status solo cambia por RPC.
- [ ] Historial obligatorio.
- [ ] PermissionRepository remoto niega ante error.
- [ ] Moderación usa permiso real.
- [ ] Ruta directa protegida.
- [ ] Datos privados requieren permiso.
- [ ] No se inició M03.
- [ ] Tests anteriores conservados.
- [ ] Nuevos tests aprobados.
- [ ] assembleDebug aprobado.
- [ ] lintDebug con 0 errores.
- [ ] Pruebas remotas no afirmadas sin evidencia.
- [ ] Cierre creado.

---

## 10. Parada

No iniciar Etapa 5 ni M03.

Detenerse al crear:

```text
/docs/02-arquitectura/M02-etapa-4-cierre.md
```
