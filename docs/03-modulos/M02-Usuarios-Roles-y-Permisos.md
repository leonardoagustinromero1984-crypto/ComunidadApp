# LEOVER — M02 Usuarios, Roles y Permisos

**Módulo:** M02 — Usuarios, Roles y Permisos  
**Versión:** 1.0  
**Estado:** Autorizada únicamente la Etapa 1 — Auditoría y diseño  
**Dependencia:** M01 aprobado a nivel código y documentación  
**Backend:** Supabase  
**Regla:** reutilizar `public.users`, modelos, pantallas y permisos existentes antes de crear nuevas estructuras.

---

## 1. Objetivo

Construir la identidad de producto de cada persona dentro de Leover y una base de autorización segura y extensible.

M02 debe resolver:

- perfil personal básico;
- nombre visible y nombre de usuario único;
- avatar, biografía, localidad y preferencias;
- privacidad del perfil;
- onboarding posterior a autenticación;
- estados de cuenta;
- roles globales de plataforma;
- catálogo central de permisos;
- asignación y revocación segura de roles;
- evaluación de permisos en Android y Supabase/RLS;
- base preparada para capacidades futuras de rescatistas, profesionales, comercios y organizaciones.

M02 no implementa todavía organizaciones, refugios, profesionales, comercios ni mascotas.

---

## 2. Dependencias y documentos

Leer antes de trabajar:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/03-modulos/M01-Identidad-y-Autenticacion.md`
5. `/docs/adr/ADR-0001-Supabase-como-backend-principal.md`
6. `/docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md`
7. `/docs/adr/ADR-0003-Proveedores-e-inyeccion-de-dependencias.md`
8. `/docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md`
9. `/docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md`
10. Este documento.

M01 mantiene pendientes de staging y release. M02 puede comenzar con auditoría y desarrollo local, pero no debe declarar integración remota completa sin evidencia.

---

## 3. Separación de conceptos

### 3.1 Identidad de autenticación

Propiedad de M01:

- UUID de `auth.users`;
- email;
- credenciales;
- sesión;
- recuperación;
- consentimiento legal;
- eliminación de cuenta.

### 3.2 Perfil de usuario

Propiedad de M02:

- nombre visible;
- username;
- avatar;
- biografía;
- localidad aproximada;
- idioma y zona horaria;
- preferencias;
- privacidad;
- estado de onboarding.

### 3.3 Rol global

Permite operar la plataforma completa:

- `USER`;
- `MODERATOR`;
- `ADMIN`;
- `SUPERADMIN`.

No confundir con el tipo de actividad de una persona.

### 3.4 Capacidades de negocio

Preparar el modelo, pero no activar módulos futuros:

- `RESCUER`;
- `ORGANIZATION_MEMBER`;
- `PROFESSIONAL`;
- `BUSINESS`.

Su verificación y funcionalidad pertenecen a M03 y módulos posteriores. En M02 solo puede existir el contrato extensible y su estado.

---

## 4. Perfil personal

Campos mínimos propuestos:

```text
id uuid = auth.users.id
display_name text
username text
avatar_path text null
bio text null
city text null
province text null
country_code text
locale text
timezone text
profile_private boolean
onboarding_status text
account_status text
created_at timestamptz
updated_at timestamptz
```

Reglas:

- `id` coincide con `auth.users.id`;
- no duplicar email en el perfil salvo necesidad demostrada;
- username único sin distinguir mayúsculas/minúsculas;
- username normalizado y no reutilizable inmediatamente tras abuso, si se implementa reserva futura;
- nombre visible puede repetirse;
- localidad no incluye coordenadas exactas;
- avatar se almacena en Storage mediante ruta, no URL permanente;
- campos sensibles no son públicos por defecto;
- el usuario actualiza únicamente campos permitidos;
- administradores no editan datos personales sin una acción auditada.

### Username

- entre 3 y 30 caracteres;
- letras, números, punto y guion bajo;
- comienza con letra o número;
- sin espacios;
- lista de palabras reservadas;
- comparación case-insensitive;
- disponibilidad validada en servidor;
- evitar enumeración masiva mediante rate limit.

---

## 5. Onboarding

Flujo propuesto:

1. M01 autentica y valida consentimiento vigente.
2. M02 consulta el perfil.
3. Si está incompleto, estado `ProfileSetupRequired`.
4. Solicitar nombre visible y username.
5. Avatar, biografía y localidad son opcionales.
6. Guardar y marcar onboarding completo.
7. Ingresar al flujo principal.

El onboarding no debe preguntar todavía por refugio, profesión, comercio ni mascotas.

Estados:

```text
NotStarted
InProgress
Completed
Blocked
```

---

## 6. Privacidad

Como mínimo:

- perfil público o privado;
- mostrar/ocultar localidad;
- permitir mensajes de cualquiera, seguidores o nadie — solo contrato, mensajería pertenece a M20;
- permitir solicitudes de seguimiento — contrato para M19;
- mostrar actividad — contrato futuro;
- bloquear indexación pública — contrato para M11.

M02 guarda preferencias y provee funciones de consulta. No implementa feed, seguidores ni chat.

---

## 7. Estados de cuenta

```text
ACTIVE
RESTRICTED
SUSPENDED
BANNED
```

Reglas:

- `ACTIVE`: acceso normal;
- `RESTRICTED`: acciones limitadas, lectura permitida según política;
- `SUSPENDED`: acceso temporal bloqueado;
- `BANNED`: bloqueo administrativo grave;
- la eliminación definitiva continúa siendo M01;
- todo cambio administrativo requiere actor, motivo, fecha y auditoría;
- Android no puede autoconcederse estados ni roles.

---

## 8. Roles y permisos

### Roles globales

| Rol | Uso |
|---|---|
| USER | Rol base de toda cuenta activa. |
| MODERATOR | Revisión de reportes y acciones limitadas. |
| ADMIN | Administración operativa de plataforma. |
| SUPERADMIN | Configuración y asignación de roles sensibles. |

### Permisos iniciales

```text
profile.read.public
profile.update.self
profile.privacy.update.self
user.status.read.self
moderation.queue.read
moderation.content.act
user.status.restrict
user.status.suspend
user.status.ban
role.assign.moderator
role.assign.admin
role.assign.superadmin
role.revoke
admin.audit.read
```

Reglas:

- negar por defecto;
- los permisos se evalúan en servidor/RLS, no solo en UI;
- la UI oculta acciones, pero no constituye seguridad;
- `SUPERADMIN` no se asigna desde Android común;
- ninguna persona puede elevar su propio rol;
- las asignaciones tienen `assigned_by`, fecha, motivo y estado;
- roles revocados dejan de surtir efecto inmediatamente o al refrescar claims/sesión según diseño documentado.

---

## 9. Modelo de datos esperado

Auditar primero las tablas existentes.

Posibles estructuras, solo si no hay equivalentes:

```text
platform_roles
permissions
role_permissions
user_role_assignments
user_capabilities
user_privacy_settings
user_status_history
```

### Recomendación inicial

Evitar sobreingeniería. Si el proyecto actual solo necesita cuatro roles globales, se permite:

- catálogo seed de roles y permisos;
- assignments normalizados;
- helper SQL `has_permission(permission_code)`;
- funciones `SECURITY DEFINER` con `search_path` fijo y mínimo privilegio;
- RLS sin recursión.

No guardar roles sensibles únicamente en `raw_user_meta_data`, porque el cliente puede modificar metadata no confiable. Si se utilizan claims, deben derivarse de una fuente administrativa segura.

---

## 10. RLS y seguridad

- lectura pública solo de campos públicos;
- vista o RPC separada para perfil público si la tabla contiene datos privados;
- usuario lee y actualiza su propio perfil;
- username se valida en servidor;
- roles y estados solo mediante funciones administrativas seguras;
- no permitir INSERT/UPDATE directo de role assignments desde Android;
- evitar políticas RLS recursivas;
- funciones privilegiadas con `SECURITY DEFINER`, `search_path` fijo y validación de permiso;
- ningún service role en Android;
- acciones administrativas auditadas;
- no exponer email, teléfono ni datos legales en perfil público.

---

## 11. Android

Reutilizar estructura actual.

Capas esperadas, adaptables:

```text
domain/user/
domain/authorization/
data/repository/UserRepository.kt
data/repository/PermissionRepository.kt
ui/screens/onboarding/
ui/screens/profile/
viewmodel/UserProfileViewModel.kt
viewmodel/OnboardingViewModel.kt
```

Modelos principales:

```text
UserProfile
PublicUserProfile
UserPrivacySettings
AccountStatus
PlatformRole
Permission
UserCapability
AuthorizationState
```

La app debe usar:

- `StateFlow`;
- `AppResult` y `AppError`;
- `AppLogger` sin PII;
- repositorio mock y Supabase con mismo contrato;
- componentes M00;
- AuthUser/UUID de M01.

No introducir Hilt, Retrofit ni otro backend.

---

## 12. Funciones y pantallas previstas

### Etapas futuras del módulo

- completar perfil;
- editar perfil;
- elegir username;
- cambiar avatar;
- privacidad;
- ver perfil público;
- estado de cuenta;
- panel mínimo de roles para administración, solo cuando la seguridad de servidor esté lista.

No crear todavía panel administrativo completo de moderación: M04.

---

## 13. Pruebas

### Unitarias

- username válido/inválido/reservado;
- normalización case-insensitive;
- onboarding transitions;
- privacidad;
- permisos deny-by-default;
- rol USER;
- elevación propia rechazada;
- estado suspendido/banned;
- mappers mock/Supabase;
- ViewModels.

### SQL/RLS

- usuario A actualiza solo A;
- usuario A no lee campos privados de B;
- perfil público expone solo allowlist;
- username único case-insensitive;
- cliente no asigna roles;
- moderador no asigna admin;
- admin no asigna superadmin salvo política;
- historial se registra;
- helpers no generan recursión.

### Manual

- onboarding nuevo;
- usuario existente;
- perfil privado;
- cambio username;
- avatar;
- sesión restringida/suspendida;
- refresco de permisos.

---

## 14. Etapas de desarrollo

### Etapa 1 — Auditoría y diseño

No modificar funcionalidad.

Crear:

```text
/docs/02-arquitectura/M02-auditoria-inicial.md
```

Debe incluir:

- definición actual de `public.users`;
- migraciones y triggers relacionados;
- modelos `User`, `AccountType`, privacidad y estados;
- repositorios mock/Supabase;
- pantallas y ViewModels de perfil;
- navegación y onboarding actual;
- permisos existentes en `domain/`;
- administración/moderación existente;
- Storage de avatares;
- RLS;
- tests;
- datos duplicados con M01;
- diferencias contra esta especificación;
- riesgos;
- propuesta de modelo final;
- archivos a crear/modificar;
- plan por etapas;
- decisiones que requieren aprobación.

### Etapa 2 — Contratos y modelo

- modelos de dominio;
- validadores;
- autorización deny-by-default;
- repositorios;
- tests.

### Etapa 3 — Perfil y onboarding

- migraciones/RLS;
- perfil, username, avatar;
- privacidad;
- UI y pruebas.

### Etapa 4 — Roles, estados y administración segura

- roles/permisos;
- assignments;
- helpers/RPC;
- estados de cuenta;
- auditoría mínima;
- pruebas.

### Etapa 5 — Calidad y cierre

- smoke;
- staging;
- build/tests/lint;
- cierre final.

---

## 15. Criterios finales de M02

- perfil separado de auth;
- onboarding estable;
- username único y seguro;
- privacidad aplicada en servidor;
- roles y permisos centralizados;
- deny-by-default;
- cliente no puede elevar privilegios;
- cambios administrativos auditados;
- mock y Supabase comparten contrato;
- build/tests/lint en verde;
- validaciones remotas documentadas;
- M03 no iniciado;
- `M02-cierre-final.md` creado.

---

## 16. Ejecución autorizada ahora

Ejecutar solamente **Etapa 1 — Auditoría y diseño**.

Reglas:

- rama `m02/usuarios-roles-permisos-auditoria`;
- preservar cambios locales;
- no mezclar GPS/mapas/pagos;
- no aplicar migraciones;
- no modificar RLS;
- no crear roles todavía;
- no desarrollar UI;
- no iniciar M03;
- no declarar M01 remoto validado;
- ejecutar build/tests/lint para estado inicial;
- detenerse al crear:

```text
/docs/02-arquitectura/M02-auditoria-inicial.md
```
