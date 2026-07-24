# Cursor — M12 Bloque 3: agenda, disponibilidad y solicitudes de turno

## Gate de inicio obligatorio

Ejecutar únicamente después de confirmar:

```text
M12 BLOQUE 2 REMOTO PASS
046 aplicada en Supabase de pruebas
6 tablas con RLS activo
26 RPC validadas
Sin DML directo del cliente
Helpers internos protegidos
Permisos veterinary.* presentes
Smoke de clínica/profesional/servicios/horarios aprobado
Directorio público seguro aprobado
```

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado esperado

- Rama: `main`.
- HEAD mínimo: `ee48ea9d6c69485960d3ff517bed544a003d0014`.
- Migraciones 040–046 aplicadas y validadas en Supabase de pruebas.
- Working tree limpio y alineado con `origin/main`.
- M12 Bloque 2 cerrado local y remotamente.

## Objetivo

Implementar **M12 Bloque 3 — agenda, reglas de disponibilidad y solicitudes de turno sin cobro**.

El bloque debe habilitar:

1. Configuración de agenda por clínica y zona horaria.
2. Reglas recurrentes de disponibilidad por clínica, profesional y servicio.
3. Excepciones por fecha: cierres, feriados o disponibilidad especial.
4. Cálculo remoto de slots disponibles sin pre-generación masiva.
5. Solicitud de turno para una mascota M08 autorizada.
6. Cupos transaccionales y prevención de doble reserva.
7. Confirmación, rechazo, cancelación, finalización y no-show.
8. Historial auditable de cambios de estado.
9. Hooks M06 para recordatorios futuros, sin implementar push real.
10. Repositorios, UI, ViewModels, fakes, pruebas focalizadas y migración 047.

## Flujo obligatorio

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 040–046.
- Crear exactamente una migración 047.
- No aplicar SQL remotamente desde Cursor.
- No iniciar Supabase local.
- No ejecutar emulador.
- No generar APK.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- Ejecutar pruebas focalizadas y una compilación Kotlin final.
- No implementar pagos, señas, checkout ni Mercado Pago.
- No implementar historia clínica, diagnóstico, receta, vacuna registrada, laboratorio ni imágenes clínicas.
- No implementar chat, videollamada ni push real.
- No utilizar Supabase real en tests.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
```

Si existen cambios locales ajenos:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Auditoría focalizada

Leer:

```text
docs/03-modulos/M12-veterinarias.md
docs/02-arquitectura/M12-auditoria-y-contratos-iniciales.md
docs/02-arquitectura/M12-persistencia-perfiles-servicios.md
docs/05-operacion/M12-aplicacion-migracion-046-supabase.md
```

Auditar código y SQL relacionado con:

```text
Appointment
Booking
Reservation
Schedule
Availability
Slot
Calendar
Reminder
NoShow
Waitlist
service_profiles
bookings
M08 ownership
M06 notifications
M07 audit
```

Clasificar:

```text
REUTILIZABLE
ADAPTABLE
LEGACY_COMPATIBLE
LEGACY_INCOMPATIBLE
AUSENTE
FUERA_DE_ALCANCE
```

Preservar bookings legacy hasta definir compatibilidad explícita.

## Paso 3 — Dominio

Crear modelos equivalentes a:

```kotlin
data class VeterinaryScheduleSettings(
    val clinicId: String,
    val timezoneName: String,
    val bookingHorizonDays: Int,
    val minimumNoticeMinutes: Int,
    val cancellationNoticeMinutes: Int,
    val defaultSlotDurationMinutes: Int,
    val active: Boolean
)
```

Validaciones:

- zona horaria IANA válida;
- horizonte 1–365 días;
- aviso mínimo 0–10080 minutos;
- cancelación 0–43200 minutos;
- duración 5–480 minutos.

Crear:

```kotlin
data class VeterinaryAvailabilityRule(
    val id: String,
    val clinicId: String,
    val professionalId: String?,
    val serviceId: String?,
    val dayOfWeek: DayOfWeek,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val slotDurationMinutes: Int,
    val capacityPerSlot: Int,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val active: Boolean
)
```

Reglas:

- `endsAt > startsAt`;
- capacidad 1–50;
- duración divide razonablemente la ventana;
- profesional y servicio, si existen, pertenecen a la clínica;
- servicio activo;
- no crear solapamientos incompatibles para la misma combinación.

Crear:

```kotlin
data class VeterinaryAvailabilityException(
    val id: String,
    val clinicId: String,
    val ruleId: String?,
    val exceptionDate: LocalDate,
    val type: VeterinaryAvailabilityExceptionType,
    val startsAt: LocalTime?,
    val endsAt: LocalTime?,
    val reason: String?,
    val active: Boolean
)
```

Tipos:

```text
CLOSED
CUSTOM_HOURS
CAPACITY_OVERRIDE
```

Crear:

```kotlin
data class VeterinaryAppointmentSlot(
    val clinicId: String,
    val professionalId: String?,
    val serviceId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val capacity: Int,
    val reserved: Int,
    val available: Int
)
```

Este objeto es una proyección calculada; no debe requerir una tabla de slots pre-generados.

Crear:

```kotlin
data class VeterinaryAppointment(
    val id: String,
    val clinicId: String,
    val professionalId: String?,
    val serviceId: String,
    val petId: String,
    val requesterUserId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: VeterinaryAppointmentStatus,
    val requestNote: String?,
    val clinicOperationalNote: String?,
    val rejectionReason: String?,
    val cancellationReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

Estados:

```text
REQUESTED
CONFIRMED
REJECTED
CANCELLED_BY_USER
CANCELLED_BY_CLINIC
COMPLETED
NO_SHOW
EXPIRED
```

Crear historial:

```kotlin
data class VeterinaryAppointmentStatusHistory(
    val id: String,
    val appointmentId: String,
    val fromStatus: VeterinaryAppointmentStatus?,
    val toStatus: VeterinaryAppointmentStatus,
    val changedBy: String,
    val reason: String?,
    val changedAt: Instant
)
```

## Paso 4 — Privacidad y datos médicos

`requestNote` y notas operativas:

- son privadas;
- no forman parte de listados públicos;
- deben tener límite de longitud;
- no constituyen historia clínica;
- no deben reutilizarse como diagnóstico.

No almacenar:

- diagnóstico;
- receta;
- dosis;
- tratamiento;
- resultado de laboratorio;
- imágenes diagnósticas;
- ficha clínica;
- datos de pago.

## Paso 5 — Autoridad M08

Auditar la autoridad real de M08 para solicitar un turno de una mascota.

La RPC debe validar que el actor:

- sea responsable principal;
- o corresponsable activo con permiso suficiente;
- o represente a una organización con custodia válida según el modelo real.

No confiar en un `requester_user_id` recibido del cliente.

No permitir reservar para mascotas ajenas.

## Paso 6 — Migración 047

Crear exactamente:

```text
supabase/migrations/047_m12_veterinary_appointments_and_availability.sql
```

Crear tablas equivalentes:

```text
veterinary_schedule_settings
veterinary_availability_rules
veterinary_availability_exceptions
veterinary_appointments
veterinary_appointment_status_history
```

No modificar 046 ni bookings legacy.

Usar:

- UUID PK;
- FK;
- timestamps;
- checks;
- índices;
- unicidades parciales;
- RLS;
- RPC;
- grants mínimos;
- comentarios.

Idempotencia:

- `CREATE TABLE IF NOT EXISTS`;
- `CREATE INDEX IF NOT EXISTS`;
- `CREATE OR REPLACE FUNCTION`;
- `DROP POLICY IF EXISTS` antes de `CREATE POLICY`;
- sin `DROP TABLE`;
- sin `TRUNCATE`;
- sin borrado destructivo;
- ningún parámetro obligatorio después de `DEFAULT`.

## Paso 7 — Disponibilidad y concurrencia

Los slots se calculan por RPC combinando:

- configuración de agenda;
- reglas recurrentes;
- excepciones;
- zona horaria;
- servicios activos;
- profesionales activos/vinculados;
- citas que consumen cupo.

Estados que consumen cupo:

```text
REQUESTED
CONFIRMED
```

Estados que liberan cupo:

```text
REJECTED
CANCELLED_BY_USER
CANCELLED_BY_CLINIC
EXPIRED
```

`COMPLETED` y `NO_SHOW` mantienen historial, pero ya no afectan disponibilidad futura.

La creación de una solicitud debe ser transaccional y evitar carreras:

- bloquear una clave estable de clínica/servicio/profesional/inicio;
- o usar una fila canónica con `FOR UPDATE`;
- contar cupo dentro de la misma transacción;
- insertar solo si queda capacidad;
- no confiar en el valor de disponibilidad enviado por Android.

No crear una tabla de slot por cada intervalo futuro salvo necesidad real documentada.

## Paso 8 — Permisos

Agregar o reutilizar:

```text
veterinary.schedule.read
veterinary.schedule.manage
veterinary.appointment.read
veterinary.appointment.request
veterinary.appointment.manage
```

Reglas:

- gestión por organización M03 y permisos;
- solicitud por usuario autenticado con autoridad M08 sobre la mascota;
- profesional vinculado no recibe gestión automática;
- requester solo ve sus propias citas;
- gestores ven citas de clínicas autorizadas;
- deny-by-default.

## Paso 9 — RLS y grants

Habilitar RLS en las cinco tablas.

- Sin DML directo para anon/authenticated.
- RPC cliente solo para authenticated.
- Helpers `_m12_*` revocados a PUBLIC, anon y authenticated.
- `SECURITY DEFINER`.
- `SET search_path = public`.
- Actor desde `auth.uid()`.
- Sin service_role Android.

## Paso 10 — RPC

Crear funciones equivalentes.

### Configuración y disponibilidad

```text
m12_upsert_veterinary_schedule_settings
m12_get_veterinary_schedule_settings
m12_create_veterinary_availability_rule
m12_update_veterinary_availability_rule
m12_change_veterinary_availability_rule_status
m12_create_veterinary_availability_exception
m12_update_veterinary_availability_exception
m12_change_veterinary_availability_exception_status
m12_list_managed_veterinary_availability
m12_list_available_veterinary_appointment_slots
```

### Turnos

```text
m12_request_veterinary_appointment
m12_get_veterinary_appointment
m12_list_my_veterinary_appointments
m12_list_managed_veterinary_appointments
m12_confirm_veterinary_appointment
m12_reject_veterinary_appointment
m12_cancel_my_veterinary_appointment
m12_cancel_managed_veterinary_appointment
m12_complete_veterinary_appointment
m12_mark_veterinary_appointment_no_show
m12_expire_veterinary_appointment
m12_list_veterinary_appointment_history
```

Total orientativo: 22 RPC cliente.

No crear RPC de diagnóstico ni notas clínicas.

## Paso 11 — Transiciones

Permitir:

```text
REQUESTED → CONFIRMED
REQUESTED → REJECTED
REQUESTED → CANCELLED_BY_USER
REQUESTED → CANCELLED_BY_CLINIC
REQUESTED → EXPIRED

CONFIRMED → CANCELLED_BY_USER
CONFIRMED → CANCELLED_BY_CLINIC
CONFIRMED → COMPLETED
CONFIRMED → NO_SHOW
```

No permitir:

- confirmar turno pasado;
- completar turno futuro;
- cancelar después de COMPLETED/NO_SHOW;
- requester marcando COMPLETED/NO_SHOW;
- gestor confirmando sin cupo;
- cambio de mascota, clínica, servicio o slot después de crear; cancelar y crear otra solicitud.

Todas las transiciones generan historial y auditoría M07.

## Paso 12 — Recordatorios M06

Preparar hooks:

```text
VETERINARY_APPOINTMENT_REQUESTED
VETERINARY_APPOINTMENT_CONFIRMED
VETERINARY_APPOINTMENT_REJECTED
VETERINARY_APPOINTMENT_CANCELLED
VETERINARY_APPOINTMENT_REMINDER_DUE
VETERINARY_APPOINTMENT_COMPLETED
```

No implementar push real ni WorkManager.

El hook de recordatorio debe quedar como contrato/evento, no como afirmación de entrega.

## Paso 13 — Repositorios

Crear:

```text
VeterinaryScheduleRepository
VeterinaryAppointmentRepository
```

Implementaciones:

```text
SupabaseVeterinaryScheduleRepository
SupabaseVeterinaryAppointmentRepository
```

Métodos equivalentes:

```text
getSettings
saveSettings
observeManagedAvailability
createRule
updateRule
changeRuleStatus
createException
updateException
changeExceptionStatus
observeAvailableSlots

requestAppointment
getAppointment
observeMyAppointments
observeManagedAppointments
confirmAppointment
rejectAppointment
cancelMyAppointment
cancelManagedAppointment
completeAppointment
markNoShow
observeAppointmentHistory
```

Requisitos:

- RPC-only;
- errores tipados;
- mapeo snake_case;
- enums desconocidos tolerados;
- sin Supabase en ViewModels;
- fakes persistentes;
- sin Supabase real en tests.

## Paso 14 — UI y navegación

Rutas:

```text
veterinary_book_appointment/{clinicId}
my_veterinary_appointments
veterinary_appointment_detail/{appointmentId}

veterinary_managed_agenda/{clinicId}
veterinary_schedule_settings/{clinicId}
veterinary_availability_rules/{clinicId}
veterinary_appointment_management/{appointmentId}
```

### Reserva

Flujo:

1. seleccionar mascota autorizada;
2. seleccionar servicio;
3. seleccionar profesional opcional;
4. seleccionar fecha;
5. consultar slots remotos;
6. seleccionar slot;
7. agregar nota privada opcional;
8. confirmar solicitud;
9. mostrar estado REQUESTED.

No mostrar disponibilidad ficticia.

### Mis turnos

Mostrar:

- clínica;
- mascota;
- servicio;
- profesional si existe;
- fecha/hora en zona local;
- estado;
- acciones permitidas.

### Gestión

Permitir:

- configurar agenda;
- reglas y excepciones;
- ver solicitudes;
- confirmar/rechazar;
- cancelar;
- completar/no-show;
- ver historial.

Prevenir doble envío.

## Paso 15 — Errores tipados

Agregar equivalentes:

```text
VETERINARY_SCHEDULE_SETTINGS_INVALID
VETERINARY_TIMEZONE_INVALID
VETERINARY_AVAILABILITY_RULE_INVALID
VETERINARY_AVAILABILITY_RULE_OVERLAP
VETERINARY_AVAILABILITY_EXCEPTION_INVALID
VETERINARY_SLOT_NOT_AVAILABLE
VETERINARY_SLOT_CAPACITY_EXHAUSTED
VETERINARY_APPOINTMENT_NOT_FOUND
VETERINARY_APPOINTMENT_FORBIDDEN
VETERINARY_APPOINTMENT_PET_FORBIDDEN
VETERINARY_APPOINTMENT_INVALID_TRANSITION
VETERINARY_APPOINTMENT_PAST_SLOT
VETERINARY_APPOINTMENT_CANCELLATION_WINDOW
VETERINARY_APPOINTMENT_ALREADY_FINAL
```

## Paso 16 — Fakes

Los fakes deben:

- persistir settings;
- persistir reglas y excepciones;
- calcular slots;
- considerar zona horaria;
- descontar REQUESTED/CONFIRMED;
- impedir sobrecupo;
- validar mascota autorizada;
- preservar historial;
- actualizar flows;
- soportar errores forzados;
- no conectar Supabase.

## Paso 17 — Tests focalizados

Crear:

```text
app/src/test/java/com/comunidapp/app/viewmodel/M12VeterinaryAppointmentsTest.kt
app/src/test/java/com/comunidapp/app/viewmodel/M12VeterinaryAppointmentMappingTest.kt
app/src/test/java/com/comunidapp/app/viewmodel/M12VeterinaryAppointmentMigrationGuardsTest.kt
```

Cubrir como mínimo:

1. settings válidos;
2. timezone inválida;
3. horizonte inválido;
4. regla válida;
5. regla solapada;
6. excepción closed;
7. excepción custom hours;
8. cálculo de slots;
9. excepción elimina slots;
10. capacidad uno;
11. capacidad múltiple;
12. REQUESTED consume cupo;
13. CONFIRMED consume cupo;
14. cancelación libera cupo;
15. concurrencia impide sobrecupo;
16. mascota propia permitida;
17. mascota ajena rechazada;
18. custodia organizacional válida;
19. servicio inactivo rechazado;
20. profesional no vinculado rechazado;
21. slot pasado rechazado;
22. solicitud exitosa;
23. doble envío;
24. listar mis turnos;
25. privacidad entre usuarios;
26. gestor autorizado;
27. confirmar;
28. rechazar;
29. cancelar usuario;
30. cancelar clínica;
31. completar;
32. no-show;
33. transición inválida;
34. historial;
35. notas privadas no públicas;
36. hook M06 preparado;
37. auditoría M07;
38. mapeo snake_case;
39. enum desconocido;
40. error RPC tipado;
41. migración 047 presente;
42. migraciones 040–046 intactas;
43. cinco tablas;
44. RLS;
45. RPC grants;
46. helpers revocados;
47. SECURITY DEFINER;
48. search_path;
49. auth.uid;
50. sin obligatorio después de DEFAULT;
51. sin DROP TABLE/TRUNCATE;
52. sin slots pre-generados masivos;
53. sin pagos;
54. sin historia clínica;
55. sin service_role Android;
56. legacy bookings intacto.

Ejecutar únicamente:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "*M12VeterinaryAppointmentsTest" `
  --tests "*M12VeterinaryAppointmentMappingTest" `
  --tests "*M12VeterinaryAppointmentMigrationGuardsTest" `
  --tests "*M12VeterinaryPersistenceTest" `
  --tests "*M12VeterinaryMigrationStaticGuardsTest" `
  --tests "*M08IntegrationRegressionTest"
```

No ejecutar toda la suite.

## Paso 18 — Compilación

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No ejecutar APK, lint, JaCoCo ni assembleDebug.

## Paso 19 — Documentación

Actualizar:

```text
docs/03-modulos/M12-veterinarias.md
```

Crear:

```text
docs/02-arquitectura/M12-agenda-disponibilidad-turnos.md
docs/05-operacion/M12-aplicacion-migracion-047-supabase.md
```

Documentar:

- auditoría;
- compatibilidad legacy;
- modelos;
- privacidad;
- autoridad M08;
- cupos y concurrencia;
- RPC;
- RLS;
- permisos;
- UI;
- tests;
- smoke posterior;
- ausencia de pagos e historia clínica.

No afirmar que 047 está aplicada.

## Paso 20 — Git

Revisar:

```powershell
git status
git diff --stat
git diff --check
```

Verificar:

- 040–046 intactas;
- nueva migración exactamente 047;
- sin APK;
- sin secretos;
- sin pagos;
- sin historia clínica;
- sin service_role;
- bookings legacy preservado.

Agregar:

```powershell
git add app supabase docs
git diff --cached --stat
git diff --cached --check
```

Commit único:

```powershell
git commit -m "feat(m12): add veterinary appointments and availability"
git push origin main
```

## Entrega final

Informar:

1. Estado inicial.
2. Auditoría.
3. Legacy.
4. Dominio.
5. Persistencia 047.
6. Tablas.
7. Constraints/índices.
8. RLS/grants.
9. Permisos.
10. RPC.
11. Disponibilidad.
12. Cupos/concurrencia.
13. Turnos/transiciones.
14. Autoridad M08.
15. Privacidad.
16. M06/M07.
17. Repositorios.
18. UI/nav.
19. Errores.
20. Tests.
21. Cantidad aprobada.
22. Compilación.
23. Docs.
24. SHA.
25. Push.
26. `git status -sb`.
27. Orden manual para aplicar 047.
28. Consultas de validación.
29. Smoke.
30. Límites.
31. Plan del Bloque 4.

No aplicar 047.
No comenzar Bloque 4.
No generar APK.
No implementar pagos ni historia clínica.
