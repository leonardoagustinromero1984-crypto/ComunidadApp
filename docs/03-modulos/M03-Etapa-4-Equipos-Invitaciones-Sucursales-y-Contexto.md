# LEOVER — M03 Etapa 4: Equipos, invitaciones, sucursales y contexto organizacional

**Módulo:** M03 — Organizaciones y Equipos  
**Etapa:** 4 — Administración operativa de organizaciones  
**Estado de entrada:** Etapa 3 aprobada y consolidada  
**Commit base:** `635943cd2fa56a401a23fd011835c82e34b23d7b`  
**Backend oficial:** Supabase  
**Objetivo:** completar membresías, invitaciones, transferencia de ownership, sucursales y contexto organizacional en Android sin iniciar M04 ni módulos funcionales futuros.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-auditoria-inicial.md`
6. `/docs/02-arquitectura/M03-etapa-2-cierre.md`
7. `/docs/02-arquitectura/M03-etapa-3-cierre.md`
8. `/docs/03-modulos/M03-Organizaciones-y-Equipos.md`
9. `/docs/03-modulos/M03-Etapa-2-Contratos-Organizaciones-Membresias-y-Autorizacion.md`
10. `/docs/03-modulos/M03-Etapa-3-Persistencia-RLS-Perfil-y-Storage.md`
11. ADR-0001 a ADR-0005
12. Este documento.

---

## 2. Protección Git

Antes de modificar:

1. Confirmar commit base:

```text
635943cd2fa56a401a23fd011835c82e34b23d7b
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m03/etapa-4-equipos-invitaciones-sucursales
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M04.

---

## 3. Condición remota heredada

Las migraciones 014–019 permanecen pendientes de validación staging, salvo que exista evidencia nueva.

Antes de crear la siguiente migración:

- comprobar el historial real disponible;
- no editar migraciones aplicadas;
- si 019 está solo en repo, mantenerla intacta salvo defecto crítico detectado antes del primer deploy;
- si 019 ya fue aplicada, cualquier corrección requiere una migración nueva;
- no desplegar producción;
- no inventar resultados remotos.

---

## 4. Alcance autorizado

### 4.1 Invitaciones persistentes

Crear migración consecutiva para:

```text
organization_invitations
```

Campos mínimos:

```text
id uuid primary key
organization_id uuid not null
invited_email citext null
invited_user_id uuid null
role_code text not null
status text not null default 'PENDING'
token_hash text not null
expires_at timestamptz not null
created_by uuid not null
accepted_by uuid null
accepted_at timestamptz null
revoked_by uuid null
revoked_at timestamptz null
created_at timestamptz not null
updated_at timestamptz not null
```

Reglas:

- debe existir `invited_email` o `invited_user_id`;
- token aleatorio fuerte y almacenamiento únicamente del hash;
- expiración obligatoria;
- un solo uso;
- estados allowlisted: `PENDING`, `ACCEPTED`, `DECLINED`, `REVOKED`, `EXPIRED`;
- no se invita al rol OWNER;
- no se crea membresía hasta aceptación válida;
- revocación e historial obligatorios;
- respuesta genérica para evitar enumeración de cuentas;
- ninguna clave o token completo en logs.

### 4.2 RPC de invitaciones

Crear como mínimo:

```text
invite_organization_member(organization_id, email_or_user, role_code, expires_at)
list_organization_invitations(organization_id)
accept_organization_invitation(token)
decline_organization_invitation(token)
revoke_organization_invitation(invitation_id)
```

Requisitos:

- actor desde `auth.uid()`;
- `organization.invite_members` para invitar;
- `organization.manage_members` para listar y revocar;
- aceptar solo si el usuario autenticado coincide con el destino;
- aceptación transaccional: validar token, expiración, estado y crear/activar membresía;
- `ON CONFLICT` controlado para evitar duplicados;
- no permitir autoelevación;
- `SECURITY DEFINER` con `search_path` fijo;
- grants mínimos;
- auditoría.

### 4.3 Administración de miembros

Crear RPC:

```text
list_organization_members(organization_id)
change_organization_member_role(organization_id, target_user_id, role_code, reason_code)
suspend_organization_member(organization_id, target_user_id, reason_code)
remove_organization_member(organization_id, target_user_id, reason_code)
leave_organization(organization_id)
```

Reglas de jerarquía:

- OWNER administra ADMIN, MANAGER, MEMBER y VIEWER;
- ADMIN administra MANAGER, MEMBER y VIEWER;
- ADMIN no asigna, degrada ni elimina OWNER;
- nadie puede autoelevarse;
- el último OWNER no puede ser removido, degradado ni abandonar;
- una membresía suspendida no concede permisos;
- un usuario M02 SUSPENDED/BANNED no administra organizaciones;
- la organización SUSPENDED/CLOSED/REJECTED bloquea acciones operativas.

### 4.4 Transferencia de ownership

Crear RPC atómica:

```text
transfer_organization_ownership(organization_id, target_user_id, reason_code)
```

Debe:

1. validar OWNER actor;
2. validar membresía ACTIVE del destino;
3. convertir destino a OWNER;
4. convertir actor a ADMIN o rol explícitamente elegido permitido;
5. asegurar al menos un OWNER;
6. registrar historial y auditoría;
7. impedir transferencia a usuario bloqueado;
8. ser idempotente ante reintento seguro.

No permitir transferencia por actualización directa.

### 4.5 Sucursales

Crear:

```text
organization_branches
```

Campos mínimos:

```text
id uuid primary key
organization_id uuid not null
name text not null
address_line text null
city text null
province text null
country_code text null
postal_code text null
contact_phone text null
contact_phone_public boolean not null default false
opening_hours jsonb null
status text not null default 'ACTIVE'
created_at timestamptz not null
updated_at timestamptz not null
```

Reglas:

- no usar coordenadas ni mapas en esta etapa;
- teléfono privado por defecto;
- estados `ACTIVE`, `INACTIVE`, `CLOSED`;
- CRUD mediante RPC;
- `organization.manage_branches` obligatorio;
- perfil público devuelve solo allowlist;
- no eliminar físicamente una sucursal con dependencias futuras: usar estado cuando corresponda.

RPC mínimas:

```text
create_organization_branch(...)
update_organization_branch(branch_id, ...)
set_organization_branch_status(branch_id, status)
list_organization_branches(organization_id, include_private)
```

### 4.6 Cierre y salida

En esta etapa se permite implementar únicamente la preparación segura:

- `leave_organization` para no-OWNER o OWNER no último;
- revocar invitaciones pendientes al cerrar;
- estado `CLOSED` mediante permiso `organization.close`;
- bloquear publicación y administración operativa tras cierre;
- conservar datos hasta que cada módulo futuro defina retención.

No implementar borrado físico completo de organizaciones.

### 4.7 Historial

Extender `organization_audit_log` para registrar:

- invitación creada/revocada/aceptada/declinada/expirada;
- miembro incorporado/suspendido/removido;
- rol cambiado;
- ownership transferido;
- sucursal creada/modificada/cerrada;
- organización cerrada.

Usar códigos allowlisted y fecha del servidor.

---

## 5. RLS y seguridad

- Sin INSERT/UPDATE/DELETE directo desde Android en invitaciones, membresías o sucursales.
- Usuario puede leer sus propias invitaciones pendientes sin conocer datos de otros.
- Miembros con permiso pueden leer datos internos de su organización.
- Perfiles públicos de sucursales mediante RPC allowlist.
- Tokens comparados por hash.
- No registrar token, email completo innecesario ni PII sensible.
- Actor siempre derivado de `auth.uid()`.
- `organization_id` nunca sustituye la identidad del actor.
- Helpers `SECURITY DEFINER` pequeños, auditados y con `search_path` fijo.
- Deny-by-default ante error.
- Evitar RLS recursiva.

---

## 6. Android

### 6.1 Repositorios

Implementar Supabase para:

- `OrganizationInvitationRepository`;
- operaciones avanzadas de `OrganizationMembershipRepository`;
- sucursales dentro de `OrganizationRepository` o repositorio separado si el contrato lo justifica.

No duplicar repositorios sin necesidad.

### 6.2 Contexto organizacional

Crear un contexto de sesión, por ejemplo:

```text
OrganizationContext
OrganizationContextProvider
```

Debe contener solamente:

- organizationId seleccionada;
- resumen público/interno mínimo;
- rol y permisos vigentes;
- estado de carga/error.

Reglas:

- no reemplaza `AuthState`;
- no persiste permisos como autoridad;
- refresca permisos al cambiar organización;
- se limpia en logout;
- si pierde membresía, vuelve al contexto personal;
- ninguna acción usa solo el contexto local sin validar repositorio/RPC.

### 6.3 UI autorizada

1. Selector “Perfil personal / Organización”.
2. Panel básico de organización.
3. Lista de miembros.
4. Invitar miembro.
5. Invitaciones pendientes.
6. Cambiar rol, suspender o remover según permiso.
7. Transferir ownership.
8. Lista y edición de sucursales.
9. Salir de organización.
10. Cerrar organización con confirmación.

Reglas UX:

- acciones destructivas con confirmación;
- mostrar claramente el contexto activo;
- no usar AccountType para seleccionar organización;
- loading/error/retry;
- accesibilidad y Material 3;
- no mostrar controles no permitidos;
- la ruta directa también debe validar permisos.

---

## 7. Relación con listings legacy

En esta etapa se permite mostrar y administrar enlaces ya creados por Etapa 3.

No realizar migración automática.

- shelter listing puede seguir personal o quedar vinculado;
- service profile puede seguir personal u organizacional;
- el contexto organizacional no modifica ownership legacy por sí solo;
- publicar como organización solo se habilitará en módulos futuros cuando el recurso y permiso estén definidos.

---

## 8. Pruebas requeridas

### 8.1 Unitarias Android

- invitación válida;
- token inválido/expirado/revocado;
- aceptación única;
- destino incorrecto;
- ADMIN no invita OWNER;
- VIEWER no invita;
- cambio de rol permitido/denegado;
- último OWNER protegido;
- transferencia válida e inválida;
- leave organization;
- sucursal privada/pública;
- contexto cambia de organización;
- contexto se limpia al logout;
- error remoto niega;
- ruta directa protegida.

### 8.2 SQL/RLS

Crear:

```text
/docs/04-calidad/M03-pruebas-equipos-invitaciones-sucursales.md
```

Casos mínimos:

- token guardado como hash;
- usuario externo no lista invitaciones;
- invitación expirada no crea membresía;
- aceptación transaccional;
- doble aceptación no duplica;
- último OWNER no se elimina;
- ADMIN no gestiona OWNER;
- transferencia conserva OWNER;
- suspensión revoca permisos efectivos;
- usuario bloqueado M02 no administra;
- organización cerrada no opera;
- sucursales privadas no filtran contacto;
- escritura directa denegada;
- RLS no recursiva;
- auditoría obligatoria.

### 8.3 Calidad local

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Informar total de tests, fallos y errores.

### 8.4 Remoto

Si no existe acceso autorizado:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

No inventar resultados.

---

## 9. Orden de implementación

### Bloque 0 — Git y auditoría

- consolidar Etapa 3;
- crear rama;
- verificar migración 019 y numeración siguiente;
- confirmar shell y calidad base.

### Bloque 1 — SQL invitaciones y miembros

- tabla invitaciones;
- RPC invite/accept/decline/revoke;
- gestión de miembros;
- historial;
- tests.

### Bloque 2 — Ownership y cierre

- cambio de roles;
- transferencia OWNER;
- leave;
- close;
- tests.

### Bloque 3 — Sucursales

- tabla;
- RPC;
- RLS;
- proyección pública;
- tests.

### Bloque 4 — Android y contexto

- repositorios Supabase;
- OrganizationContext;
- UI de equipos/invitaciones/sucursales;
- gates;
- tests.

### Bloque 5 — Calidad y cierre

- build;
- tests;
- lint;
- documentación;
- cierre.

---

## 10. Fuera de alcance

- Verificación documental avanzada.
- Moderación M04.
- Adopciones y gestión operativa de refugios.
- Servicios, turnos, pagos y marketplace.
- Feed, chat o notificaciones completas.
- GPS o mapas.
- Borrado físico completo de organizaciones.
- Hilt, Retrofit o nuevo backend.
- Merge a `main`.
- Producción.

---

## 11. Criterios de aceptación

- [ ] Commit base confirmado.
- [ ] Rama limpia sin WIP.
- [ ] Migración nueva sin editar aplicadas.
- [ ] Invitaciones persistentes con token hash.
- [ ] Invitaciones expirables, revocables y un solo uso.
- [ ] Aceptación crea membresía atómicamente.
- [ ] Gestión de miembros con jerarquía.
- [ ] Último OWNER protegido.
- [ ] Transferencia de ownership atómica.
- [ ] Sucursales persistidas sin mapas.
- [ ] Contactos privados por defecto.
- [ ] Historial obligatorio.
- [ ] Escritura directa sensible revocada.
- [ ] Contexto organizacional no reemplaza AuthState.
- [ ] Permisos refrescados desde servidor.
- [ ] AccountType no concede membresía.
- [ ] UI y rutas protegidas.
- [ ] No se inició M04.
- [ ] Tests anteriores conservados.
- [ ] Nuevos tests aprobados.
- [ ] assembleDebug aprobado.
- [ ] lintDebug con 0 errores.
- [ ] Remoto declarado honestamente.
- [ ] Cierre creado.

---

## 12. Entregable y parada

Crear exactamente:

```text
/docs/02-arquitectura/M03-etapa-4-cierre.md
```

Debe incluir:

- rama y commits;
- migración;
- invitaciones;
- membresías;
- ownership;
- sucursales;
- contexto Android;
- UI;
- pruebas;
- build/lint;
- estado remoto;
- riesgos;
- checklist.

No iniciar Etapa 5 ni M04.

Detenerse al crear:

```text
/docs/02-arquitectura/M03-etapa-4-cierre.md
```
