# LEOVER — M02 Etapa 3: Perfil, onboarding, privacidad y RLS

**Módulo:** M02 — Usuarios, Roles y Permisos  
**Etapa:** 3 — Persistencia del perfil, onboarding, privacidad y seguridad de datos  
**Estado de entrada:** Etapa 2 aprobada  
**Backend oficial:** Supabase PostgreSQL/RLS + Storage  
**Objetivo:** llevar los contratos de perfil de M02 a la base de datos y a la aplicación, incorporando username, onboarding, proyección pública segura, privacidad y ownership de avatares.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-auditoria-inicial.md`
5. `/docs/02-arquitectura/M02-etapa-2-cierre.md`
6. `/docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`
7. `/docs/03-modulos/M02-Etapa-2-Contratos-Perfil-y-Autorizacion.md`
8. `/docs/adr/ADR-0001-Supabase-como-backend-principal.md`
9. `/docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md`
10. `/docs/adr/ADR-0003-Proveedores-e-inyeccion-de-dependencias.md`
11. `/docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md`
12. `/docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md`
13. Este documento.

Las decisiones de M00, M01 y M02 aprobadas tienen prioridad sobre documentación histórica incompatible.

---

## 2. Decisiones vigentes

1. `public.users` continúa siendo la única tabla de perfil; no crear una segunda tabla `profiles`.
2. `name` permanece temporalmente como compatibilidad y se agrega `display_name`.
3. `username` será nullable durante la migración y obligatorio para completar onboarding.
4. `account_type`, `active_modules`, reputación y estados administrativos no pueden ser modificados por el usuario.
5. Privacidad se almacena en `user_privacy_settings`.
6. Roles y permisos reales se implementan en Etapa 4; durante esta etapa el repositorio remoto sigue negando permisos administrativos.
7. La moderación permanece oculta y bloqueada.
8. Los perfiles ajenos se consultan únicamente mediante proyecciones/RPC allowlist.
9. El avatar nuevo se guarda por path con ownership; `profile_image_url` queda como fallback legacy.
10. No asumir que M01 fue desplegado o validado remotamente.

---

## 3. Protección de Git

Antes de modificar:

1. Ejecutar `git status`.
2. Consolidar M02 Etapa 2 en un commit identificable.
3. Preservar cualquier cambio local.
4. Crear la rama:

```text
m02/etapa-3-perfil-onboarding-privacidad-rls
```

5. Confirmar que no contiene `wip/gps-mapas-pagos`.
6. Registrar SHA base y estado inicial en el cierre.

---

## 4. Alcance autorizado

### 4.1 Migraciones

Auditar el último número de migración disponible. Crear migraciones consecutivas nuevas; no modificar migraciones que puedan haber sido aplicadas.

Separar preferentemente:

```text
NNN_user_profile_foundation.sql
NNN_user_profile_security.sql
NNN_profile_avatar_storage.sql
```

Los números exactos dependen del repositorio al ejecutar.

### 4.2 Extensiones y username

Habilitar `citext` si no existe.

Agregar a `public.users`:

```text
username citext null
display_name text null
avatar_path text null
city text null
province text null
country_code text null
locale text null
timezone text null
onboarding_status text not null default 'NOT_STARTED'
account_status text not null default 'ACTIVE'
```

Restricciones:

- `username` normalizado y único case-insensitive;
- 3 a 30 caracteres;
- regex equivalente a las reglas Android;
- `display_name` entre 2 y 80 caracteres al completar onboarding;
- `country_code` ISO 3166-1 alpha-2 en mayúsculas cuando exista;
- `onboarding_status`: `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `BLOCKED`;
- `account_status`: `ACTIVE`, `RESTRICTED`, `SUSPENDED`, `BANNED`;
- índices por username, onboarding y account status.

Compatibilidad:

- backfill inicial de `display_name = name` cuando `name` no esté vacío;
- no inventar usernames;
- usuarios existentes quedan `IN_PROGRESS` si no poseen username;
- mantener `name` sincronizado temporalmente desde la operación de actualización;
- no eliminar todavía `profile_image_url`, `location_text`, `profile_private` o `phone_public`.

### 4.3 Palabras reservadas

Crear una fuente central de usernames reservados.

Puede ser tabla:

```text
public.reserved_usernames
- username citext primary key
- reason text null
- active boolean not null default true
```

Seed mínimo:

```text
admin
administrator
moderator
support
soporte
leover
system
sistema
root
api
www
```

No agregar marcas de terceros sin necesidad.

### 4.4 Privacidad

Crear:

```text
public.user_privacy_settings
- user_id uuid primary key references auth.users(id) on delete cascade
- profile_visibility text not null
- show_location boolean not null default true
- show_phone boolean not null default false
- allow_friend_requests boolean not null default true
- created_at timestamptz not null
- updated_at timestamptz not null
```

Valores de `profile_visibility`:

```text
PUBLIC
FRIENDS
PRIVATE
```

Backfill:

- `profile_private=true` → `PRIVATE`;
- `profile_private=false` → `PUBLIC`;
- `show_phone` desde `phone_public`;
- no inventar `FRIENDS`.

El usuario solo puede leer y actualizar su propia configuración.

### 4.5 Funciones de perfil

Crear funciones con `search_path` fijo y validación explícita.

#### `is_username_available(p_username text)`

- normaliza;
- valida formato;
- revisa reservados;
- revisa unicidad;
- no revela información adicional.

#### `complete_profile_onboarding(...)`

Operación atómica que:

- usa `auth.uid()`;
- no acepta un `user_id` como autoridad;
- valida username, display name y campos opcionales;
- comprueba reserva/unicidad;
- actualiza campos permitidos;
- sincroniza `name = display_name` por compatibilidad;
- crea/actualiza privacidad;
- establece `onboarding_status='COMPLETED'`;
- no modifica `account_type`, `active_modules`, reputación o account status;
- devuelve el perfil actualizado sin PII innecesaria.

#### `update_my_profile(...)`

- usa `auth.uid()`;
- permite únicamente display name, bio, city, province, country, locale, timezone y avatar path;
- no modifica username en esta etapa después de onboarding;
- no modifica datos administrativos.

#### `get_public_user_profile(p_user_id uuid)`

Devuelve allowlist:

```text
id
username
display_name
avatar_path
bio
city/province/country según privacidad
created_at
```

Nunca devuelve:

```text
email
phone salvo regla explícita y autorizada
active_modules
account_type
reputation_score interno
account_status
onboarding_status
tokens
```

Reglas de visibilidad:

- SELF: permitido;
- PUBLIC: permitido a autenticados;
- FRIENDS: solo amistad aceptada o self;
- PRIVATE: solo self;
- cuentas SUSPENDED/BANNED no se muestran a terceros;
- verificar el esquema real de `friendships` antes de escribir la consulta.

#### `search_public_user_profiles(p_query text, p_limit int, p_offset int)`

- query normalizada;
- límite máximo 50;
- solo onboarding `COMPLETED`;
- solo cuentas visibles y activas;
- allowlist;
- no buscar por email o teléfono;
- orden estable;
- proteger contra consultas vacías abusivas.

### 4.6 RLS y privilegios en `public.users`

Objetivo: deny-by-default.

Políticas:

- SELECT directo: solo la propia fila completa;
- perfiles ajenos: solo mediante las funciones allowlist;
- INSERT directo desde cliente: revocado, salvo necesidad demostrada del trigger;
- UPDATE directo desde cliente: revocado;
- DELETE directo: revocado;
- actualización propia: únicamente RPC allowlist;
- service role conserva acceso administrativo.

No confiar solo en RLS para restringir columnas: revocar escrituras directas y usar RPC.

Revisar el trigger `handle_new_user` para asegurar que sigue creando `public.users` y privacidad inicial sin depender de permisos cliente.

### 4.7 Repositorios Android

Extender `UserRepository`; no crear repositorio paralelo.

Agregar contratos para:

- obtener perfil propio;
- observar perfil propio;
- verificar username;
- completar onboarding;
- actualizar perfil;
- obtener perfil público;
- buscar perfiles públicos;
- obtener/actualizar privacidad.

Usar:

- `UserProfile`;
- `PublicUserProfile`;
- `UserPrivacySettings`;
- `AppResult`/`AppError`;
- `AppLogger` sin PII.

Mantener `User` legacy como bridge temporal y documentar mapeo.

### 4.8 Gate de sesión y onboarding

Después de:

1. Auth válido;
2. consentimiento legal resuelto;

evaluar:

```text
account_status
onboarding_status
```

Estados requeridos:

```text
ProfileSetupRequired
AccountRestricted
AccountSuspended
AccountBanned
ProfileReady
```

Comportamiento:

- `NOT_STARTED` o `IN_PROGRESS` → onboarding;
- `COMPLETED` + `ACTIVE` → app principal;
- `RESTRICTED` → app con estado informativo, sin inventar restricciones de módulos futuros;
- `SUSPENDED`/`BANNED` → pantalla de acceso bloqueado;
- `BLOCKED` onboarding → estado recuperable/soporte;
- no navegar dos veces por recomposición.

### 4.9 Onboarding UI

Crear flujo breve, máximo 3 pasos:

#### Paso 1 — Identidad pública

- display name;
- username;
- validación y disponibilidad con debounce;
- explicación de uso del username.

#### Paso 2 — Ubicación y privacidad

- ciudad;
- provincia;
- país;
- visibilidad PUBLIC/FRIENDS/PRIVATE;
- mostrar ubicación;
- mostrar teléfono;
- solicitudes de amistad.

#### Paso 3 — Avatar y resumen

- avatar opcional;
- biografía opcional;
- resumen;
- completar.

Reglas:

- username no cambia durante esta etapa después de completar;
- avatar no bloquea completar;
- permitir volver entre pasos;
- conservar estado ante recomposición;
- no guardar contraseña, email ni datos de Auth en el formulario;
- UI accesible, Material 3, tema claro/oscuro;
- errores inline;
- no mostrar AccountType.

### 4.10 Avatar y Storage

Crear bucket privado dedicado si no existe, recomendado:

```text
profile-avatars
```

Path:

```text
users/{auth.uid()}/avatar/{filename}
```

Políticas:

- INSERT/UPDATE/DELETE: solo owner derivado del path;
- SELECT: owner o usuario autorizado a ver el perfil según la función de privacidad;
- tamaño y MIME allowlist;
- solo imágenes;
- no permitir path traversal;
- no sobrescribir objetos de terceros.

Android:

- guardar `avatar_path`, no URL permanente;
- obtener URL firmada temporal;
- cachear de forma segura;
- eliminar avatar anterior al reemplazar, con manejo de fallos;
- mantener `profile_image_url` como fallback legacy;
- no migrar URLs imposibles de resolver automáticamente.

### 4.11 Seguridad inmediata

Mantener:

- moderación oculta;
- ruta de moderación bloqueada;
- PermissionRepository remoto sin elevación;
- AccountType y active_modules sin autoridad.

No implementar roles reales hasta Etapa 4.

---

## 5. Fuera de alcance

No implementar:

- tablas reales de roles/permisos;
- assignments;
- RPC `has_permission`;
- administración de estados;
- historial de sanciones;
- M03 organizaciones;
- cambio de username posterior;
- perfiles públicos anónimos web;
- GPS, mapas o pagos;
- cambios de Auth M01;
- renombre de paquete;
- Hilt, Retrofit, NestJS u otro backend.

---

## 6. Pruebas requeridas

### 6.1 Unitarias

- mapeo `name` → `displayName`;
- username y disponibilidad;
- onboarding state;
- account state;
- privacidad;
- Gate de sesión;
- onboarding ViewModel;
- update profile allowlist;
- avatar path;
- public profile sin PII;
- búsqueda sin email/teléfono.

### 6.2 SQL/RLS

Crear:

```text
/docs/04-calidad/M02-pruebas-perfil-privacidad-rls.md
```

Cubrir:

- backfill display_name;
- username único case-insensitive;
- reservados;
- complete onboarding atómico;
- usuario A no actualiza B;
- update directo sensible rechazado;
- propio perfil completo;
- ajeno solo RPC allowlist;
- privacidad PUBLIC/FRIENDS/PRIVATE;
- account status;
- search;
- trigger de alta;
- convivencia con M01 consentimientos;
- no ejecutar destructivos en producción.

### 6.3 Storage

Probar:

- owner sube/reemplaza/elimina;
- otro usuario no escribe;
- visibilidad de lectura;
- MIME inválido;
- tamaño excedido;
- path ajeno;
- signed URL;
- reemplazo con fallo parcial.

### 6.4 Manual/staging

- usuario nuevo pasa Auth/legal → onboarding;
- username ocupado/reservado;
- completar y restaurar sesión;
- perfil privado/público;
- búsqueda;
- avatar;
- account suspended/banned de prueba;
- no acceso a moderación;
- no afirmar remoto sin evidencia.

---

## 7. Orden de implementación

### Bloque 0 — Git y auditoría final

- consolidar Etapa 2;
- crear rama limpia;
- revisar migraciones disponibles;
- revisar esquema de friendships y Storage;
- listar archivos.

### Bloque 1 — SQL de perfil y privacidad

- columnas;
- reservados;
- privacy settings;
- backfill;
- funciones;
- RLS;
- pruebas SQL;
- build/tests/lint.

### Bloque 2 — Data y repositorios

- mappers;
- UserRepository;
- RPC;
- mock;
- pruebas;
- build/tests/lint.

### Bloque 3 — Gate y onboarding

- estados;
- navegación;
- UI;
- ViewModels;
- pruebas;
- build/tests/lint.

### Bloque 4 — Avatar y perfil

- bucket/policies;
- upload/signed URL;
- editar perfil;
- perfil público/search;
- pruebas;
- build/tests/lint.

### Bloque 5 — Calidad y cierre

- smoke documentado;
- revisar ausencia de PII;
- actualizar arquitectura;
- validación final;
- crear cierre.

---

## 8. Criterios de aceptación

- [ ] Etapa 2 consolidada.
- [ ] Rama limpia sin WIP.
- [ ] No existe segunda tabla de perfil.
- [ ] `display_name` agregado y `name` preservado.
- [ ] Username único case-insensitive.
- [ ] Username reservado bloqueado.
- [ ] Onboarding status y account status persistidos.
- [ ] Privacidad en tabla separada.
- [ ] UPDATE directo sensible revocado.
- [ ] Propio perfil completo accesible.
- [ ] Perfiles ajenos solo por allowlist.
- [ ] Email/phone/modules no se filtran.
- [ ] PUBLIC/FRIENDS/PRIVATE funcionan.
- [ ] Storage valida owner por path.
- [ ] Avatar usa path y URL firmada.
- [ ] Gate Auth → Legal → Profile funciona.
- [ ] Onboarding se completa de forma atómica.
- [ ] AccountType no aparece ni concede permisos.
- [ ] Moderación continúa bloqueada.
- [ ] No se implementaron roles reales ni M03.
- [ ] Tests anteriores siguen aprobados.
- [ ] Nuevos tests aprobados.
- [ ] `assembleDebug` aprobado.
- [ ] `lintDebug` con 0 errores.
- [ ] Pruebas remotas no verificadas se declaran honestamente.
- [ ] Cierre creado.

---

## 9. Entregable de cierre

Crear exactamente:

```text
/docs/02-arquitectura/M02-etapa-3-cierre.md
```

Debe incluir:

- rama y commits;
- migraciones;
- backfills;
- RLS y funciones;
- Storage;
- repositorios;
- onboarding;
- gates;
- UI;
- pruebas;
- resultados build/tests/lint;
- pruebas remotas;
- riesgos y deuda Etapa 4;
- checklist.

---

## 10. Parada

No iniciar M02 Etapa 4 ni M03.

Detenerse cuando exista:

```text
/docs/02-arquitectura/M02-etapa-3-cierre.md
```
