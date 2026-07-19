# LEOVER — M06 Etapa 5: Validación, Staging, Calidad y Cierre Final

**Módulo:** M06 — Notificaciones  
**Etapa:** 5 — Validación integral y cierre final  
**Estado de entrada:** Etapa 4 aprobada y consolidada  
**Commit base:** `5e695d77030d81ebeb47f5e31405164e1245b47b`  
**Rama base:** `m06/etapa-4-push-deep-links-preferencias-instalaciones`  
**Calidad de entrada:** 480 tests, 0 failures, 0 errors; `assembleDebug` y `lintDebug` en SUCCESS  
**Staging heredado:** migraciones `014`–`027` pendientes de validación remota  
**Objetivo:** validar integralmente M06, corregir únicamente defectos reales, documentar staging y cerrar formalmente el módulo sin iniciar M07.  
**Fuera de alcance:** email, campañas, marketing activo, WhatsApp/SMS, nuevo proveedor push, producción, cambios de auth/username y merge a `main`.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/02-arquitectura/M06-auditoria-inicial.md`
2. `/docs/02-arquitectura/M06-etapa-2-cierre.md`
3. `/docs/02-arquitectura/M06-etapa-3-cierre.md`
4. `/docs/02-arquitectura/M06-etapa-4-cierre.md`
5. `/docs/04-calidad/M06-pruebas-persistencia-seguridad-outbox-in-app.md`
6. `/docs/04-calidad/M06-pruebas-push-deep-links-preferencias-instalaciones.md`
7. `/docs/03-modulos/M06-Notificaciones.md`
8. `/docs/03-modulos/M06-Etapa-5-Validacion-Staging-Calidad-y-Cierre-Final.md`
9. ADR-0001 a ADR-0005.

---

## 2. Protección Git

1. Confirmar commit base:

```text
5e695d77030d81ebeb47f5e31405164e1245b47b
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m06/etapa-5-validacion-cierre
```

4. No mezclar GPS, mapas ni pagos.
5. No hacer merge a `main`.
6. No iniciar M07.
7. No usar producción.
8. No afirmar staging sin evidencia.
9. No modificar username, `AuthRepository`, `domain/auth` ni `UsernameValidators`.

---

## 3. Alcance

### 3.1 Auditoría estática integral

Revisar:

- migraciones `026` y `027`;
- que `001`–`025` no hayan sido editadas;
- cierre de INSERT directo;
- `create_notification` self-only y cross-user denegado;
- RLS y grants;
- RPC `SECURITY DEFINER`;
- `search_path`;
- actor `auth.uid()`;
- recipients server-side;
- preferencias;
- quiet hours;
- instalaciones;
- deliveries;
- outbox;
- dead-letter;
- payloads push;
- Edge Function `push`;
- credenciales FCM;
- logs;
- canales Android;
- deep links;
- revalidación de permisos;
- navegación pendiente;
- permiso Android;
- logout/cambio de cuenta;
- wiring M03–M05;
- compatibilidad legacy;
- `NotificationDispatcher`;
- Realtime pendiente;
- errores y PII.

Corregir únicamente defectos reales de M06.

No agregar funcionalidades nuevas.

### 3.2 Calidad local

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Requisitos:

- conservar las 480 pruebas existentes;
- todos los tests aprobados;
- build SUCCESS;
- lint con 0 errores;
- no crear baseline nuevo;
- no usar suppress global;
- documentar total final.

---

## 4. Validación remota

Determinar con evidencia si las migraciones `014`–`027` están aplicadas en un entorno autorizado.

### Si existe acceso autorizado a staging

Aplicar respetando el historial real:

```text
014 → 015 → 016 → 017 → 018 → 019 → 020 → 021 → 022 → 023 → 024 → 025 → 026 → 027
```

Antes de aplicar:

- revisar historial remoto;
- no reejecutar migraciones aplicadas;
- no editar migraciones aplicadas;
- crear backup o punto de recuperación;
- no usar producción;
- registrar cada resultado.

### Si no existe acceso autorizado

No simular despliegue ni resultados.

Marcar:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

Esto permite cerrar código, pero mantiene release bloqueado.

---

## 5. Checklist remoto

### 5.1 Seguridad

- usuario A no crea notificación para B;
- INSERT directo cliente denegado;
- `create_notification` cross-user denegado;
- self-only limitado a `SYSTEM`;
- Android no escribe outbox ni deliveries;
- Android no elige recipient;
- AccountType/modules sin autoridad;
- org A no accede recursos de org B;
- staff sin permiso no abre rutas staff;
- INTERNAL no llega al requester;
- payload con token/signed URL/PII rechazado;
- Edge Function requiere autorización server-side;
- logs sin token, provider ID ni payload sensible.

### 5.2 Inbox

- filas legacy legibles;
- `is_read` sincronizado;
- unread correcto;
- expiradas fuera del contador;
- mark read/all solo propias;
- archive/delete lógico;
- multi-device sin duplicar inbox;
- push entregado no marca READ.

### 5.3 Preferencias

- push por categoría;
- categorías obligatorias in-app no desactivables;
- marketing OFF;
- quiet hours;
- timezone;
- preferencia desactivada → skip;
- quiet hours → defer/skip según política;
- SECURITY_CRITICAL según regla explícita.

### 5.4 Instalaciones

- registro;
- token refresh;
- revoke solo instalación actual;
- otros dispositivos permanecen activos;
- cambio de cuenta desvincula anterior;
- token inválido revoca instalación;
- sin doble registro;
- sin doble push legacy/nuevo;
- raw token no listado.

### 5.5 Push y deliveries

- claim atómico;
- un delivery por instalación;
- copy genérico sensible;
- retry transitorio;
- permanente no reintenta;
- máximo → dead-letter;
- expirado → skip;
- provider failure no simula éxito;
- canal desconocido → `leover_system`.

### 5.6 Deep links

- allowlist;
- sin sesión;
- recurso inexistente;
- permiso denegado;
- organización incorrecta;
- staff sin permiso;
- INTERNAL;
- doble tap;
- reentrega;
- logout limpia navegación;
- cambio de cuenta limpia navegación;
- deny → `SAFE_HOME` o bandeja.

### 5.7 Wiring M03–M05

- invitaciones y membresía;
- cambios de rol/ownership;
- moderación y apelaciones;
- verificación;
- soporte visible;
- INTERNAL solo staff;
- uploads M05;
- documentos de verificación;
- idempotencia;
- deduplicación;
- recipient server-side;
- errores de notificación no abortan mutación de dominio.

---

## 6. Realtime

Validar únicamente si existe entorno autorizado.

Requisitos:

- `notifications` publicada de forma controlada;
- RLS activa;
- solo filas propias;
- sin exponer events/outbox/deliveries;
- una suscripción por sesión;
- cierre en logout/cambio de cuenta;
- sin listeners duplicados;
- fallback refresh/polling.

Si no existe evidencia:

```text
REALTIME PENDIENTE
```

No bloquear cierre local, pero documentar deuda.

---

## 7. Seguridad obligatoria

Confirmar:

- sin service role en Android;
- credenciales FCM solo server-side;
- sin token raw en logs/modelos públicos;
- sin signed URLs en payload;
- sin PII completa;
- copy genérico para sensibles;
- `auth.uid()` como actor;
- recipient server-side;
- RLS deny-by-default;
- grants mínimos;
- INTERNAL staff-only;
- notificación no concede acceso;
- deep link reautoriza;
- logout limpia estado;
- marketing sin consentimiento deshabilitado;
- push denegado no bloquea bandeja in-app.

---

## 8. Regresión

Confirmar:

- M01 sesión/auth sin cambios;
- M02 permisos/estados;
- M03 organizaciones;
- M04 moderación/soporte/verificación;
- M05 archivos;
- bandeja legacy;
- FCM legacy compatible;
- `leover_default`;
- múltiples dispositivos;
- logout;
- cambio de cuenta;
- mocks `useSupabase=false`;
- Supabase `useSupabase=true`;
- WIP GPS/mapas/pagos aislado.

El error de username queda fuera de esta rama.

---

## 9. Correcciones permitidas

Se permiten:

- correcciones Kotlin M06;
- correcciones Edge Function M06;
- correcciones SQL/RLS/RPC de `026`/`027`;
- pruebas;
- documentación;
- migración `028` únicamente si existe defecto real y bloqueante en `026` o `027`.

Si se requiere `028`:

- no editar `026`/`027`;
- alcance mínimo;
- prueba de regresión;
- defecto documentado;
- sin funcionalidad nueva.

No se permite:

- email;
- M07;
- producción;
- nuevo proveedor push;
- cambios auth/username;
- merge a `main`.

---

## 10. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M06-etapa-5-cierre.md
/docs/02-arquitectura/M06-cierre-final.md
/docs/04-calidad/M06-reporte-validacion-staging.md
```

### `M06-etapa-5-cierre.md`

Debe incluir:

- rama y commits;
- estado Git;
- archivos;
- defectos encontrados;
- correcciones;
- migración correctiva, si existe;
- Edge Function;
- comandos;
- total de tests;
- build/lint;
- seguridad;
- regresión;
- Realtime;
- staging;
- riesgos;
- checklist.

### `M06-reporte-validacion-staging.md`

Registrar:

```text
caso
entorno
fecha
actor técnico
resultado
evidencia
observaciones
```

Si no hubo acceso, usar `NO EJECUTADO`.

### `M06-cierre-final.md`

Resumir:

- arquitectura;
- inbox;
- eventos;
- recipients;
- preferencias;
- instalaciones;
- deliveries;
- outbox;
- dead-letter;
- seguridad cross-user;
- push;
- Edge Function;
- canales Android;
- deep links;
- permiso Android;
- wiring M03–M05;
- compatibilidad legacy;
- Realtime;
- calidad;
- staging;
- deuda aceptada;
- condiciones de release;
- M07;
- auth/username fuera de alcance.

---

## 11. Criterios de aceptación

- [ ] Commit base correcto.
- [ ] Rama limpia.
- [ ] `001`–`025` sin ediciones.
- [ ] `026` y `027` auditadas.
- [ ] Cross-user cerrado.
- [ ] INSERT cliente cerrado.
- [ ] Android sin outbox/deliveries.
- [ ] Recipient server-side.
- [ ] Payload seguro.
- [ ] Edge Function segura.
- [ ] Inbox legacy compatible.
- [ ] Preferencias correctas.
- [ ] Instalaciones multi-device.
- [ ] Push no marca READ.
- [ ] Deep links reautorizados.
- [ ] INTERNAL protegido.
- [ ] Wiring M03–M05.
- [ ] Realtime validado o pendiente honestamente.
- [ ] Build aprobado.
- [ ] Tests aprobados.
- [ ] Lint aprobado.
- [ ] Staging validado o pendiente.
- [ ] Release bloqueado sin staging PASS.
- [ ] Tres documentos creados.
- [ ] Auth/username intactos.
- [ ] Sin M07.
- [ ] Sin merge a main.

---

## 12. Parada

No iniciar M07.

No implementar email.

No corregir username/auth en esta rama.

No hacer merge a `main`.

Detenerse al crear:

```text
/docs/02-arquitectura/M06-etapa-5-cierre.md
/docs/02-arquitectura/M06-cierre-final.md
/docs/04-calidad/M06-reporte-validacion-staging.md
```

No hacer commit hasta revisión.
