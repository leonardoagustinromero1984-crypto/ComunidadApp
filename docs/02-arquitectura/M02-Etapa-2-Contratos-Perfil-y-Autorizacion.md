# LEOVER — M02 Etapa 2: Contratos de perfil y autorización

**Módulo:** M02 — Usuarios, Roles y Permisos  
**Etapa:** 2 — Dominio, validaciones y autorización deny-by-default  
**Estado de entrada:** Auditoría aprobada  
**Backend:** Supabase  
**Objetivo:** crear contratos de perfil, username, privacidad y permisos sin aplicar todavía migraciones ni construir onboarding completo.

---

## 1. Documentos obligatorios

Leer:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-auditoria-inicial.md`
5. `/docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`
6. ADR-0001 a ADR-0005
7. Este documento.

---

## 2. Decisiones aprobadas

### D-M02-01 — Nombre visible

- Mantener `name` temporalmente por compatibilidad.
- Agregar concepto de dominio `displayName`.
- En Etapa 3 se agregará columna `display_name`.
- Durante transición, mappers podrán usar `display_name ?: name`.
- No eliminar `name` todavía.

### D-M02-02 — Username

- `username` será nullable en base durante migración.
- Será obligatorio para completar onboarding.
- Solo perfiles con onboarding completado podrán entrar al flujo principal.
- Debe ser único, case-insensitive y normalizado.

### D-M02-03 — AccountType

- `account_type` deja de ser editable por el usuario.
- Queda read-only como dato legacy/capacidad.
- No puede otorgar permisos administrativos.
- La UI de edición debe dejar de mostrarlo.
- Su evolución a capacidades se hará en M03.

### D-M02-04 — Privacidad

Crear una tabla separada `user_privacy_settings`.

Motivo:

- evita seguir agregando columnas sensibles a `public.users`;
- permite evolucionar reglas por categoría;
- separa perfil de política de visibilidad.

### D-M02-05 — Roles normalizados

Desde el inicio se utilizarán:

- `platform_roles`
- `permissions`
- `role_permissions`
- `user_role_assignments`

No usar una sola columna `platform_role` en `users`.

### D-M02-06 — RLS

El endurecimiento de RLS se hará en Etapa 3, junto con la proyección pública.

- fila propia: lectura completa;
- filas ajenas: solo proyección allowlist;
- email, phone, active_modules, reputation_score y campos internos no se exponen;
- actualización propia limitada a columnas permitidas.

### D-M02-07 — Storage

Ownership por path se implementará en la misma Etapa 3 que `avatar_path`.

Formato oficial:

```text
users/{auth.uid()}/avatar/{filename}
```

### D-M02-08 — Moderación

La entrada de moderación se ocultará inmediatamente en Etapa 2 si no existe permiso real.

Hasta M02 Etapa 4:

- ningún usuario común puede verla;
- `active_modules` no habilita moderación;
- UI no es seguridad, pero debe negar por defecto.

### D-M02-09 — Consentimientos M01

M02 no asumirá que migración 014 está desplegada remotamente.

Los gates M02 deben funcionar sin afirmar validación remota de M01.

### D-M02-10 — Permisos en Android

En esta etapa los permisos se consultarán desde repositorio/tablas, no desde claims JWT.

Los claims podrán evaluarse más adelante cuando exista estrategia de refresco segura.

---

## 3. Protección Git

Antes de modificar:

1. Consolidar M01 en un commit identificable.
2. Preservar cambios locales.
3. Crear rama:

```text
m02/etapa-2-contratos-perfil-autorizacion
```

4. No incorporar `wip/gps-mapas-pagos`.
5. Registrar SHA base en el cierre.

---

## 4. Alcance autorizado

### 4.1 Dominio de usuario

Crear en `domain/user/`:

- `UserProfile`
- `PublicUserProfile`
- `ProfileSetupStatus`
- `AccountStatus`
- `Username`
- `UserPrivacySettings`
- validadores

Estados:

```text
NOT_STARTED
IN_PROGRESS
COMPLETED
BLOCKED
```

Cuenta:

```text
ACTIVE
RESTRICTED
SUSPENDED
BANNED
```

### 4.2 Username

Reglas iniciales:

- obligatorio al completar onboarding;
- 3 a 30 caracteres;
- minúsculas;
- letras a-z, números, punto y guion bajo;
- comenzar con letra o número;
- no terminar con punto;
- sin espacios;
- sin dobles puntos consecutivos;
- palabras reservadas configurables;
- normalización central;
- error tipado.

No implementar todavía consulta remota de disponibilidad.

### 4.3 Autorización

Crear en `domain/authorization/`:

- `PlatformRoleCode`
- `PermissionCode`
- `AuthorizationContext`
- `AuthorizationDecision`
- `AuthorizationService`

Principio:

```text
deny by default
```

Roles iniciales:

```text
USER
MODERATOR
ADMIN
SUPERADMIN
```

Permisos mínimos:

```text
profile.read.own
profile.update.own
profile.read.public
moderation.view
moderation.manage_reports
users.view_private
users.change_status
roles.assign
roles.revoke
```

No inventar permisos de módulos futuros.

### 4.4 Repositorios

Extender sin duplicar:

- `UserRepository`
- crear `PermissionRepository` solo como contrato
- implementaciones mock para pruebas

No crear todavía tablas SQL ni llamadas remotas reales de roles.

### 4.5 Seguridad inmediata

Modificar UI para que:

- moderación esté oculta si no existe `moderation.view`;
- ante ausencia de permisos, denegar;
- `AccountType`, `active_modules` y `LeoverModule.ADMIN` no otorguen permisos;
- navegación directa a moderación también sea rechazada.

Esto es una contención temporal hasta RLS/RPC de Etapa 4.

### 4.6 Tests

Agregar pruebas para:

- username válido/inválido;
- normalización;
- palabras reservadas;
- estado de onboarding;
- account status;
- deny-by-default;
- roles y permisos;
- moderación negada a USER;
- moderación permitida a MODERATOR;
- `AccountType` no concede permisos;
- `active_modules` no concede permisos;
- navegación ungated rechazada.

---

## 5. Fuera de alcance

No hacer todavía:

- migraciones SQL de perfil;
- username en base;
- onboarding UI completo;
- RLS definitivo;
- tabla de privacidad;
- tablas reales de roles;
- RPC `has_permission`;
- asignación administrativa;
- M03;
- organizaciones;
- GPS, mapas o pagos;
- cambios Auth M01;
- renombre del paquete;
- Hilt, Retrofit o nuevo backend.

---

## 6. Orden de implementación

### Bloque 0

- consolidar M01;
- crear rama limpia;
- inventariar archivos a tocar.

### Bloque 1 — Dominio perfil

- UserProfile;
- PublicUserProfile;
- estados;
- username;
- validadores;
- tests.

### Bloque 2 — Dominio autorización

- roles;
- permisos;
- decisiones;
- deny-by-default;
- tests.

### Bloque 3 — Repositorios mock

- extender UserRepository;
- contrato PermissionRepository;
- mock determinista;
- tests.

### Bloque 4 — Contención UI

- ocultar moderación;
- bloquear ruta directa;
- retirar AccountType de edición;
- tests.

### Bloque 5 — Cierre

- build;
- tests;
- lint;
- documentación.

Crear:

```text
/docs/02-arquitectura/M02-etapa-2-cierre.md
```

---

## 7. Criterios de aceptación

- [ ] M01 consolidado.
- [ ] Rama limpia.
- [ ] UserProfile separado de AuthUser.
- [ ] PublicUserProfile no contiene PII.
- [ ] Username central y testeado.
- [ ] ProfileSetupStatus implementado.
- [ ] AccountStatus implementado.
- [ ] Roles y permisos tipados.
- [ ] Deny-by-default.
- [ ] AccountType no concede permisos.
- [ ] active_modules no concede permisos.
- [ ] Moderación oculta y bloqueada sin permiso.
- [ ] No se crearon migraciones.
- [ ] No se inició M03.
- [ ] Tests previos conservados.
- [ ] Nuevos tests aprobados.
- [ ] assembleDebug aprobado.
- [ ] lintDebug con 0 errores.
- [ ] Cierre creado.

---

## 8. Parada

No iniciar Etapa 3.

Detenerse al crear:

```text
/docs/02-arquitectura/M02-etapa-2-cierre.md
```
