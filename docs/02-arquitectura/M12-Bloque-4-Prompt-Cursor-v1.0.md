# Cursor — M12 Bloque 4: recordatorios, endurecimiento y cierre funcional

## Gate de inicio obligatorio

Ejecutar únicamente después de confirmar:

```text
AUTH APK MANUAL PASS
M12 BLOQUE 3 REMOTO PASS
047 aplicada en Supabase de pruebas
5 tablas con RLS activo
22 RPC validadas
Sin DML directo del cliente
Helpers internos protegidos
Permisos de agenda y turnos presentes
Smoke de disponibilidad y turnos aprobado
```

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado esperado

- Rama: `main`.
- HEAD mínimo: `304d4e0d81ecddf5a85c56c1cc49b4e65725f6cb`.
- Working tree limpio y alineado con `origin/main`.
- Migraciones 040–047 aplicadas y validadas en Supabase de pruebas.
- Hotfix de autenticación validado manualmente en APK físico.
- M12 Bloques 1–3 cerrados.

## Objetivo

Implementar **M12 Bloque 4 — recordatorios operativos, endurecimiento de agenda y seguimiento de turnos**, sin pagos ni historia clínica.

El bloque debe:

1. Auditar la infraestructura M06 real de notificaciones y recordatorios.
2. Implementar recordatorios idempotentes para turnos confirmados.
3. Endurecer vencimientos, cancelaciones, zonas horarias, concurrencia y reintentos.
4. Mejorar seguimiento y estados visibles para solicitante y clínica.
5. Incorporar métricas operativas agregadas sin PII.
6. Ejecutar regresión focalizada y preparar el cierre final de M12.
7. Crear SQL nuevo solo si la auditoría demuestra que es imprescindible.

## Reglas

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 040–047.
- No aplicar SQL remotamente.
- No iniciar M13.
- No generar APK salvo que el prompt lo solicite expresamente al final; por defecto, no generar.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- No implementar pagos, señas, checkout ni Mercado Pago.
- No implementar diagnóstico, receta, vacunación clínica, laboratorio o historia clínica.
- No afirmar push real si M06 no lo soporta.
- No usar service_role desde Android.
- No usar Supabase real en tests.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
```

Si hay cambios locales ajenos:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Auditoría M06 y agenda

Leer:

```text
docs/03-modulos/M12-veterinarias.md
docs/02-arquitectura/M12-agenda-disponibilidad-turnos.md
docs/05-operacion/M12-aplicacion-migracion-047-supabase.md
docs/05-operacion/Auth-diagnostico-y-hotfix-APK-localDebug.md
```

Auditar:

```text
Notification
Reminder
Outbox
Delivery
Scheduler
Worker
WorkManager
Cron
Edge Function
M06
appointment reminder
expire appointment
idempotency
timezone
retry
```

Clasificar:

```text
REUTILIZABLE
ADAPTABLE
REQUIERE_SQL
REQUIERE_INFRA_EXTERNA
AUSENTE
FUERA_DE_ALCANCE
```

## Paso 3 — Decisión SQL

Por defecto, no crear migración nueva.

Solo si el mecanismo real requiere persistencia adicional imprescindible:

- detener la implementación antes de crear SQL;
- documentar exactamente la necesidad;
- proponer migración 048 como bloque separado;
- no mezclarla silenciosamente con este bloque.

Este Bloque 4 debe poder cerrarse localmente sin inventar infraestructura.

## Paso 4 — Recordatorios

Implementar contratos/eventos equivalentes:

```text
VETERINARY_APPOINTMENT_REMINDER_24H_DUE
VETERINARY_APPOINTMENT_REMINDER_2H_DUE
VETERINARY_APPOINTMENT_REMINDER_CANCELLED
```

Reglas:

- solo turnos CONFIRMED;
- no recordar turnos cancelados, rechazados, completados, no-show o expirados;
- idempotencia por turno + tipo de recordatorio;
- respetar zona horaria de la clínica;
- no incluir notas privadas;
- payload mínimo;
- actor y destinatario resueltos por autoridad real;
- si M06 no entrega push real, registrar outbox/hook o estado preparado y documentarlo como pendiente externo.

No usar WorkManager como garantía de servidor para recordatorios críticos.

## Paso 5 — Endurecimiento

Cubrir:

- cálculo de slots en cambio de horario de verano;
- turnos en límite de día;
- reintentos idempotentes;
- expiración de REQUESTED;
- confirmación simultánea;
- cancelación dentro/fuera de ventana;
- servicio o profesional desactivado después de solicitar;
- cambio de zona horaria de clínica;
- evitar doble notificación;
- evitar doble transición;
- mensajes de error específicos;
- privacidad de notas e identidad.

No modificar turnos finales.

## Paso 6 — Seguimiento UI

Mejorar:

```text
my_veterinary_appointments
veterinary_appointment_detail/{appointmentId}
veterinary_appointment_management/{appointmentId}
```

Mostrar:

- línea de tiempo de estados;
- próximo paso;
- posibilidad de cancelar cuando corresponde;
- motivo de rechazo/cancelación redactado según autoridad;
- indicador de recordatorio programado/preparado;
- estado de conectividad recuperable;
- reintento seguro.

No mostrar:

- datos internos de la clínica;
- notas operativas al requester;
- datos del requester a terceros;
- diagnóstico o historia clínica;
- pagos.

## Paso 7 — Métricas agregadas

Agregar contratos/repositorio para métricas sin PII:

```text
requested
confirmed
rejected
cancelled_by_user
cancelled_by_clinic
completed
no_show
expired
occupancy_rate
average_confirmation_minutes
```

Filtros por:

- clínica autorizada;
- rango de fechas válido;
- servicio;
- profesional opcional.

No exportar nombres, emails, teléfonos, notas ni IDs de usuario.

Si requiere SQL nuevo, aplicar la regla del Paso 3 y detenerse.

## Paso 8 — Errores tipados

Agregar equivalentes:

```text
VETERINARY_REMINDER_NOT_ELIGIBLE
VETERINARY_REMINDER_ALREADY_SCHEDULED
VETERINARY_REMINDER_INFRASTRUCTURE_UNAVAILABLE
VETERINARY_APPOINTMENT_EXPIRED
VETERINARY_APPOINTMENT_RETRY_CONFLICT
VETERINARY_APPOINTMENT_TIMEZONE_CHANGED
VETERINARY_APPOINTMENT_SERVICE_INACTIVE
VETERINARY_APPOINTMENT_PROFESSIONAL_INACTIVE
VETERINARY_APPOINTMENT_METRICS_INVALID_RANGE
```

## Paso 9 — Tests focalizados

Crear:

```text
M12VeterinaryAppointmentHardeningTest
M12VeterinaryReminderTest
M12VeterinaryBlock4StaticGuardsTest
```

Cubrir como mínimo:

1. recordatorio 24 h elegible;
2. recordatorio 2 h elegible;
3. no recordar REQUESTED;
4. no recordar CANCELLED;
5. idempotencia;
6. zona horaria;
7. DST;
8. payload sin PII;
9. M06 disponible;
10. M06 no disponible;
11. no afirmar push real;
12. expiración REQUESTED;
13. confirmación simultánea;
14. doble transición;
15. cancelación fuera de ventana;
16. servicio inactivo;
17. profesional inactivo;
18. timeline;
19. privacidad;
20. retry seguro;
21. métricas agregadas;
22. rango inválido;
23. sin PII;
24. sin pagos;
25. sin historia clínica;
26. sin service_role;
27. migraciones 040–047 intactas;
28. sin migración 048 silenciosa;
29. hotfix auth preservado;
30. rutas existentes.

Ejecutar únicamente suites M12 B3/B4, auth hotfix guard y M06 focalizada real.

## Paso 10 — Compilación

Ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No generar APK, lint, JaCoCo ni assembleDebug.

## Paso 11 — Documentación

Actualizar:

```text
docs/03-modulos/M12-veterinarias.md
docs/02-arquitectura/M12-agenda-disponibilidad-turnos.md
```

Crear:

```text
docs/02-arquitectura/M12-recordatorios-endurecimiento-seguimiento.md
docs/05-operacion/M12-validacion-bloque-4.md
```

Registrar:

- auditoría M06;
- alcance real de recordatorios;
- idempotencia;
- zonas horarias;
- endurecimiento;
- UI;
- métricas;
- pruebas;
- limitaciones externas;
- decisión sobre migración 048;
- plan de cierre final M12.

## Paso 12 — Git

Revisar:

```powershell
git status
git diff --stat
git diff --check
```

Confirmar:

- 040–047 intactas;
- sin SQL nuevo salvo bloqueo documentado;
- sin APK;
- sin secretos;
- sin pagos;
- sin historia clínica;
- sin service_role;
- sin cambios ajenos.

Commit único:

```powershell
git commit -m "feat(m12): harden appointments and reminders"
git push origin main
```

## Entrega final

Informar:

1. Estado inicial.
2. Auditoría M06.
3. Decisión SQL.
4. Recordatorios.
5. Idempotencia.
6. Zonas horarias.
7. Vencimientos.
8. Concurrencia.
9. Cancelaciones.
10. Seguimiento UI.
11. Privacidad.
12. Métricas.
13. Errores.
14. Integración M06/M07.
15. Tests.
16. Cantidad aprobada.
17. Compilación.
18. Documentación.
19. Limitaciones externas.
20. SHA.
21. Push.
22. `git status -sb`.
23. Estado de migraciones.
24. Plan exacto de cierre final M12.

No iniciar M13.
No aplicar SQL.
No implementar pagos ni historia clínica.
