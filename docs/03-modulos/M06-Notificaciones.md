# LEOVER — M06 Notificaciones

**Módulo:** M06 — Notificaciones  
**Versión:** 1.0  
**Estado:** autorizado únicamente para Etapa 1 — Auditoría y diseño  
**Dependencias:** M00–M05 cerrados a nivel código y calidad local  
**Backend oficial:** Supabase  
**Condición de release heredada:** migraciones `014`–`025` pendientes de validación remota  
**Regla principal:** primero inventariar eventos, mensajes, deep links, preferencias, tokens y canales existentes; no implementar push, correo ni jobs antes de cerrar la auditoría.

---

## 1. Objetivo

M06 será la capa transversal de notificaciones de LeoVer.

Debe permitir, por etapas:

- notificaciones dentro de la app;
- push móvil;
- correo transaccional cuando corresponda;
- preferencias por usuario;
- categorías y prioridad;
- lectura, archivo y eliminación lógica;
- contador de no leídas;
- deep links seguros;
- deduplicación;
- idempotencia;
- reintentos;
- programación y expiración;
- quiet hours;
- auditoría;
- plantillas;
- localización;
- integración con módulos existentes y futuros.

M06 no debe convertirse en un segundo sistema de mensajería.  
M20 será el propietario del chat y las conversaciones.

---

## 2. Dependencias y autoridad

| Área | Módulo propietario |
|---|---|
| Identidad y sesión | M01 |
| Usuarios, permisos y estados | M02 |
| Organizaciones y equipos | M03 |
| Moderación, soporte y verificación | M04 |
| Archivos y adjuntos | M05 |
| Notificaciones | M06 |
| Observabilidad transversal | M07 |
| Mascotas | M08 |
| Adopciones | M14 |
| Mensajería/chat | M20 |

Reglas:

- actor derivado de `auth.uid()`;
- permisos globales desde M02;
- contexto organizacional desde M03;
- M06 entrega avisos, no concede autoridad;
- un deep link vuelve a validar permisos al abrirse;
- AccountType y `active_modules` no conceden acceso;
- una notificación no debe exponer datos que el usuario no puede consultar.

---

## 3. Canales

### 3.1 In-app

Canal obligatorio y fuente de verdad inicial.

Capacidades futuras:

- bandeja;
- no leídas;
- filtros;
- marcar una;
- marcar todas;
- archivar;
- navegación al recurso;
- expiración;
- prioridad;
- agrupación;
- estado de entrega.

### 3.2 Push

Canal móvil futuro.

Debe contemplar:

- token por instalación;
- múltiples dispositivos;
- revocación;
- refresh de token;
- instalación deshabilitada;
- sesión cerrada;
- payload mínimo;
- contenido sensible oculto;
- deep link tipado;
- deduplicación;
- reintento.

La tecnología concreta debe auditarse antes de implementarse.  
No asumir FCM configurado sin evidencia.

### 3.3 Correo

Uso futuro y restringido a casos transaccionales:

- verificación;
- seguridad;
- invitaciones;
- recuperación;
- cambios importantes;
- soporte;
- avisos configurables.

No enviar marketing sin consentimiento explícito.

### 3.4 Local

Notificaciones locales del dispositivo solo para:

- recordatorios creados por el usuario;
- eventos ya sincronizados;
- tareas offline aprobadas.

No usar local como sustituto de eventos server-side.

---

## 4. Categorías iniciales

```text
ACCOUNT
SECURITY
ORGANIZATION
INVITATION
MODERATION
APPEAL
VERIFICATION
SUPPORT
PET
ADOPTION
FOSTER
SHELTER
LOST_FOUND
DONATION
EVENT
SOCIAL
MESSAGE
SERVICE
APPOINTMENT
PAYMENT
MARKETPLACE
SYSTEM
OTHER
```

Cada categoría debe declarar:

- canales permitidos;
- prioridad;
- sensibilidad;
- deep link;
- expiración;
- deduplicación;
- si requiere consentimiento;
- si puede agruparse;
- si puede mostrarse en pantalla bloqueada.

---

## 5. Prioridad

```text
LOW
NORMAL
HIGH
URGENT
```

Reglas:

- `URGENT` solo para seguridad, bienestar animal o incidentes críticos aprobados;
- prioridad no evita permisos;
- no usar urgencia para aumentar engagement;
- quiet hours pueden exceptuarse solo por política explícita;
- contenido sensible debe seguir oculto.

---

## 6. Sensibilidad

```text
PUBLIC_SUMMARY
PRIVATE
SENSITIVE
SECURITY_CRITICAL
```

Ejemplos:

- invitación a organización: PRIVATE;
- moderación o apelación: SENSITIVE;
- soporte PRIVACY/SAFETY: SENSITIVE;
- inicio de sesión o cambio de contraseña: SECURITY_CRITICAL;
- evento público: PUBLIC_SUMMARY.

Push y pantalla bloqueada deben usar texto genérico para SENSITIVE y SECURITY_CRITICAL.

---

## 7. Modelo conceptual

No implementar antes de la auditoría.

Entidades candidatas:

```text
notifications
notification_recipients
notification_deliveries
notification_preferences
notification_device_installations
notification_templates
notification_outbox
notification_dead_letters
```

### `notifications`

Campos conceptuales:

```text
id
event_key
category
priority
sensitivity
title_key
body_key
payload
resource_type?
resource_id?
deep_link?
deduplication_key?
created_at
expires_at?
created_by?
```

### `notification_recipients`

```text
notification_id
recipient_user_id
state
read_at?
archived_at?
deleted_at?
created_at
```

### `notification_deliveries`

```text
id
notification_id
recipient_user_id
channel
installation_id?
status
attempt_count
last_attempt_at?
delivered_at?
failure_code?
provider_message_id?
```

### `notification_preferences`

```text
user_id
category
in_app_enabled
push_enabled
email_enabled
quiet_hours_start?
quiet_hours_end?
timezone?
updated_at
```

### `notification_device_installations`

```text
id
user_id
platform
push_token_encrypted_or_protected
token_hash
device_label?
app_version?
last_seen_at
revoked_at?
created_at
```

No persistir tokens en logs, auditoría o payloads.

---

## 8. Estados

### Notificación del usuario

```text
UNREAD
READ
ARCHIVED
DELETED
EXPIRED
```

### Entrega

```text
PENDING
PROCESSING
DELIVERED
FAILED_RETRYABLE
FAILED_PERMANENT
SKIPPED_PREFERENCE
SKIPPED_PERMISSION
SKIPPED_EXPIRED
CANCELLED
```

Reglas:

- lectura in-app y entrega push son estados diferentes;
- un push entregado no marca como leído;
- reintentos requieren idempotencia;
- expirado no debe enviarse;
- un token inválido debe revocarse;
- fallo permanente no se reintenta sin cambio de condición.

---

## 9. Eventos candidatos por módulos existentes

### M01 / M02

- verificación de cuenta;
- recuperación;
- cambio de contraseña;
- cuenta suspendida o reactivada;
- cambio de rol o permiso crítico;
- solicitud de eliminación.

### M03

- invitación;
- invitación aceptada/rechazada/expirada;
- cambio de rol interno;
- alta/baja de miembro;
- actualización importante de organización.

### M04

- reporte recibido;
- caso actualizado;
- medida aplicada;
- apelación recibida/resuelta;
- verificación requiere información;
- verificación aprobada/rechazada/revocada;
- ticket de soporte actualizado;
- respuesta visible del soporte.

No notificar notas internas ni adjuntos INTERNAL al solicitante.

### M05

- upload completado;
- upload fallido;
- archivo rechazado;
- documento próximo a expirar;
- evidencia disponible para staff autorizado.

### M08+ futuros

- mascota actualizada;
- adopción;
- tránsito;
- perdidos/encontrados;
- donaciones;
- eventos;
- social;
- mensajes;
- turnos;
- pagos;
- marketplace.

La auditoría debe diferenciar implementado, parcial, mock, futuro y ausente.

---

## 10. Deep links

Modelo tipado:

```text
route
resourceType
resourceId
requiredPermission?
organizationId?
fallbackRoute
```

Reglas:

- no aceptar URL arbitraria;
- allowlist de rutas;
- validar sesión;
- validar recurso;
- validar permisos;
- validar organización;
- recurso inexistente → fallback seguro;
- notificación expirada puede abrir una pantalla informativa;
- push sensible no incluye datos completos;
- logout limpia notificaciones temporales y navegación pendiente;
- no abrir pantallas staff desde AccountType o flags.

---

## 11. Preferencias

El usuario podrá configurar por categoría y canal.

Reglas:

- seguridad crítica puede no ser desactivable;
- in-app puede ser obligatoria para eventos legales/administrativos;
- push y email respetan consentimiento;
- quiet hours usan timezone del usuario;
- cambios deben auditarse;
- defaults claros;
- no inferir consentimiento de marketing;
- organización no puede activar canales personales sin permiso del usuario.

---

## 12. Deduplicación e idempotencia

Cada evento debe tener una clave estable:

```text
event_key
recipient_user_id
resource_type
resource_id
event_version
```

Reglas:

- mismo evento no crea notificaciones duplicadas;
- reintento no crea otra notificación;
- agrupación no pierde trazabilidad;
- múltiples dispositivos pueden recibir la misma entrega sin duplicar la bandeja;
- double submit de UI debe ser seguro;
- outbox debe ser idempotente.

---

## 13. Programación, expiración y reintentos

Capacidades futuras:

- entrega inmediata;
- entrega diferida;
- recordatorios;
- expiración;
- reintentos con backoff;
- dead-letter;
- cancelación;
- quiet hours;
- límites por usuario/categoría.

No implementar scheduler en Etapa 1.

La auditoría debe identificar si existen:

- WorkManager;
- AlarmManager;
- jobs Supabase;
- Edge Functions;
- cron;
- triggers SQL;
- colas;
- reintentos ad-hoc.

---

## 14. Seguridad y privacidad

Confirmar o diseñar:

- RLS deny-by-default;
- actor `auth.uid()`;
- recipient server-side;
- no crear notificación para otro usuario desde Android;
- payload allowlisted;
- sin secretos;
- sin tokens;
- sin signed URLs;
- sin contenido INTERNAL;
- sin PII completa en push;
- no almacenar texto sensible innecesario;
- cifrado/protección de push tokens;
- revocación al logout o cambio de cuenta;
- limpieza de token inválido;
- auditoría de eventos críticos;
- rate limiting;
- protección contra spam.

---

## 15. Android

Auditar:

- modelos de notificación;
- badges o contadores;
- rutas;
- deep links;
- `NavRoutes`;
- ViewModels;
- repositorios;
- preferencias;
- WorkManager;
- BroadcastReceiver;
- servicios de Firebase;
- permisos Android 13+;
- canales de notificación Android;
- íconos;
- intents;
- token refresh;
- logout;
- múltiples cuentas;
- tests;
- mocks.

No crear Firebase ni añadir dependencias en Etapa 1.

---

## 16. Supabase

Auditar:

- tablas existentes;
- funciones;
- triggers;
- realtime;
- Edge Functions;
- cron;
- plantillas;
- email Auth;
- RPC;
- políticas;
- eventos de M01–M05;
- posibles outbox existentes;
- duplicaciones.

No asumir que Supabase Auth email equivale a M06 completo.

---

## 17. Observabilidad

Futuro:

- evento generado;
- destinatario calculado;
- preferencia aplicada;
- canal seleccionado;
- intento;
- entrega;
- fallo;
- reintento;
- token revocado;
- deep link abierto.

No registrar:

- token completo;
- contenido sensible;
- payload privado;
- signed URLs;
- secretos.

M07 será propietario de observabilidad transversal; M06 define eventos propios.

---

## 18. Fuera de alcance

- chat o mensajería en tiempo real;
- campañas de marketing;
- publicidad;
- recomendaciones por IA;
- envío masivo comercial;
- WhatsApp/SMS;
- producción;
- nuevo backend;
- cambios en username/auth;
- GPS, mapas o pagos;
- M07;
- implementación de FCM;
- Edge Functions nuevas;
- jobs;
- migraciones;
- pantallas.

---

## 19. Etapas de M06

### Etapa 1 — Auditoría y diseño

Inventario completo sin cambios funcionales.

### Etapa 2 — Contratos y preferencias

- modelos;
- categorías;
- sensibilidad;
- prioridad;
- deep links;
- preferencias;
- repositorios;
- mocks;
- pruebas;
- sin SQL.

### Etapa 3 — Persistencia y entrega in-app

- tablas;
- RLS;
- RPC;
- outbox;
- bandeja;
- unread;
- integración M01–M05.

### Etapa 4 — Push y UI operativa

- instalaciones/tokens;
- permisos Android;
- push;
- canales Android;
- deep links;
- preferencias UI;
- reintentos.

### Etapa 5 — Calidad y cierre

- seguridad;
- staging;
- regresión;
- observabilidad;
- cierre final.

---

## 20. Ejecución autorizada: solo Etapa 1

### Rama

```text
m06/notificaciones-auditoria
```

### Crear únicamente

```text
/docs/02-arquitectura/M06-auditoria-inicial.md
```

### Auditar

#### Git y documentación

- commit base M05;
- working tree;
- ramas;
- documentos M01–M05;
- ADR.

#### Android

- dependencias Firebase;
- servicios push;
- WorkManager;
- AlarmManager;
- receivers;
- permisos POST_NOTIFICATIONS;
- notification channels;
- tokens;
- deep links;
- badges;
- pantallas;
- repositorios;
- mocks;
- logout;
- logs.

#### Supabase

- migraciones `001`–`025`;
- tablas;
- triggers;
- RPC;
- realtime;
- Auth emails;
- Edge Functions;
- cron;
- outbox;
- preferencias;
- tokens;
- deliveries;
- RLS.

#### Eventos

Inventariar todos los eventos posibles de M01–M05 y clasificarlos:

```text
IMPLEMENTADO
PARCIAL
MOCK
FUTURO
AUSENTE
RIESGO
```

#### Seguridad

- recipient calculado server-side;
- contenido sensible;
- INTERNAL;
- deep links;
- tokens;
- preferencias;
- spam;
- dedup;
- idempotencia;
- retry;
- logout;
- múltiples dispositivos;
- múltiples cuentas.

### No hacer

- no modificar funcionalidad;
- no crear migraciones;
- no agregar Firebase;
- no crear Edge Functions;
- no crear jobs;
- no crear tablas;
- no crear repositorios;
- no crear pantallas;
- no tocar username/auth;
- no iniciar M07;
- no aplicar staging;
- no usar producción;
- no hacer merge a `main`.

### Calidad

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

### Contenido obligatorio del informe

- estado Git;
- inventario Android;
- inventario Supabase;
- inventario de canales;
- inventario de deep links;
- mapa de eventos M01–M05;
- categorías;
- sensibilidad;
- prioridades;
- preferencias;
- riesgos;
- duplicaciones;
- gaps;
- propuesta de modelo;
- propuesta de outbox;
- propuesta de entrega in-app;
- propuesta push;
- propuesta de tokens/instalaciones;
- propuesta de permisos;
- deduplicación;
- idempotencia;
- retry/dead-letter;
- quiet hours;
- observabilidad;
- archivos futuros;
- decisiones que requieren aprobación;
- plan por etapas;
- calidad;
- staging;
- parada.

---

## 21. Criterios de aceptación de Etapa 1

- [ ] Commit base confirmado.
- [ ] Working tree limpio.
- [ ] Rama correcta.
- [ ] Sin cambios funcionales.
- [ ] Sin migraciones, Firebase, jobs, Edge Functions o tablas.
- [ ] Inventario Android completo.
- [ ] Inventario Supabase completo.
- [ ] Eventos M01–M05 clasificados.
- [ ] Canales y sensibilidad diferenciados.
- [ ] Deep links auditados.
- [ ] Preferencias y quiet hours diseñadas.
- [ ] Tokens y múltiples dispositivos analizados.
- [ ] Deduplicación e idempotencia analizadas.
- [ ] Retry y dead-letter analizados.
- [ ] INTERNAL y datos sensibles protegidos.
- [ ] Username/auth sin cambios.
- [ ] Build aprobado.
- [ ] Tests aprobados.
- [ ] Lint aprobado.
- [ ] Staging declarado honestamente.
- [ ] Auditoría creada.
- [ ] Sin M07.
- [ ] Sin merge a main.

---

## 22. Parada

No iniciar Etapa 2.

No iniciar M07.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M06-auditoria-inicial.md
```
