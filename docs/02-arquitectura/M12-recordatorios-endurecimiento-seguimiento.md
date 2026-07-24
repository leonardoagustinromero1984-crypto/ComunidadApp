# M12 Bloque 4 — Recordatorios, endurecimiento y seguimiento

**Producto:** LeoVer · Módulo M12 (Veterinarias) · Bloque 4.
**Estado:** cierre **local** en repo. Sin pagos ni historia clínica. Sin migración nueva.

> No afirma push real de M06. No afirma smoke funcional remoto del Bloque 3.

## 1. Auditoría M06 / infraestructura de notificaciones

| Área | Clasificación | Nota |
|------|---------------|------|
| M06 `notifications` / outbox (026–028) | ADAPTABLE | Existe outbox y hardening de entrega; el canal push real depende de infra externa |
| WorkManager / scheduler cliente | FUERA_DE_ALCANCE | No se usa como garantía de servidor para recordatorios críticos |
| Cron / Edge Function de disparo | REQUIERE_INFRA_EXTERNA | El disparo real (24 h / 2 h) queda pendiente externo |
| Hooks de evento M12 (turno) | REUTILIZABLE | Contratos de evento preparados en Bloque 3 |
| M07 `m07_best_effort_audit` | REUTILIZABLE | Auditoría de transiciones y recordatorios |
| Persistencia adicional de recordatorios | AUSENTE a propósito | Se modela como estado preparado en memoria/outbox, sin tabla nueva |

**Conclusión:** el Bloque 4 se cierra **localmente** con recordatorios en estado *preparado*
(hooks de evento + outbox lógico) sin declarar entrega push. El disparo real es pendiente externo.

## 2. Alcance real de recordatorios

- Eventos de contrato equivalentes:
  - `VETERINARY_APPOINTMENT_REMINDER_24H_DUE`
  - `VETERINARY_APPOINTMENT_REMINDER_2H_DUE`
  - `VETERINARY_APPOINTMENT_REMINDER_CANCELLED`
- Solo turnos `CONFIRMED` son elegibles.
- Nunca se recuerdan turnos `REQUESTED`, `REJECTED`, `CANCELLED_*`, `COMPLETED`, `NO_SHOW` ni `EXPIRED`.
- Estado de recordatorio: `PREPARED` (programado, sin push) → `FIRED_PREPARED` (disparo lógico, `pushClaimed = false`).
- La UI muestra **"Recordatorio preparado (sin push)"**. Nunca declara push enviado.

## 3. Idempotencia

- Clave: turno + tipo de recordatorio (24 h / 2 h).
- Un segundo `prepare` del mismo tipo devuelve `VETERINARY_REMINDER_ALREADY_SCHEDULED`
  o el mismo `schedule` sin duplicar hooks.
- `fireDueReminder` marca `FIRED_PREPARED` una sola vez.

## 4. Zonas horarias

- El `dueAt` de cada recordatorio se calcula en la zona IANA de la clínica
  (`VeterinaryScheduleSettings.timezoneName`).
- Cambios de zona horaria en settings mantienen consistentes los `Instant` de turnos abiertos.
- El cálculo de slots respeta el cambio de horario de verano (DST) usando `ZoneId` real.

## 5. Endurecimiento

- Expiración de `REQUESTED` fuera de horizonte / sin confirmar.
- Confirmación simultánea → conflicto o transición inválida (no doble confirm).
- Doble transición bloqueada (`VETERINARY_APPOINTMENT_INVALID_TRANSITION` / `_ALREADY_FINAL`).
- Cancelación dentro/fuera de ventana (`VETERINARY_APPOINTMENT_CANCELLATION_WINDOW`).
- Servicio/profesional desactivado tras solicitar → rechazo en confirmación.
- Reintentos idempotentes: `retrySafeTransition` evita efectos dobles y expone
  `VETERINARY_APPOINTMENT_RETRY_CONFLICT` cuando el estado ya cambió.
- Sin modificar turnos finales.

## 6. Seguimiento (UI)

Pantallas `my_veterinary_appointments`, `veterinary_appointment_detail/{appointmentId}`,
`veterinary_appointment_management/{appointmentId}`:

- Línea de tiempo de estados (etiquetas en español).
- Próximo paso (`VeterinaryAppointmentFollowUp.nextStep`).
- Indicador de recordatorio preparado (sin push).
- Motivo de rechazo/cancelación solo si existe y el observador está autorizado.
- Botones deshabilitados mientras se envía; reintento seguro ante `RETRY_CONFLICT`.
- Error de conectividad recuperable con botón de reintento (recarga).

No muestra: notas operativas al requester, datos del requester a terceros,
datos internos de clínica, diagnóstico, historia clínica ni pagos.

## 7. Métricas agregadas (sin PII)

Contadores por clínica autorizada y rango de fechas válido, con filtro de servicio
y profesional opcional: `requested`, `confirmed`, `rejected`, `cancelled_by_user`,
`cancelled_by_clinic`, `completed`, `no_show`, `expired`, `occupancy_rate`,
`average_confirmation_minutes`.

- Rango inválido → `VETERINARY_APPOINTMENT_METRICS_INVALID_RANGE`.
- No exporta nombres, emails, teléfonos, notas ni IDs de usuario.

## 8. Errores tipados agregados

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

## 9. Pruebas

- `M12VeterinaryAppointmentHardeningTest` — expiración, concurrencia, DST/zona, timeline,
  privacidad, retry seguro, métricas y liberación de cupo con recordatorios cancelados.
- `M12VeterinaryReminderTest` — elegibilidad 24 h/2 h, idempotencia, zona horaria,
  payload sin PII, M06 disponible/no disponible, `FIRED_PREPARED` sin push, hook de cancelación.
- `M12VeterinaryBlock4StaticGuardsTest` — sin migración 048, migraciones 040–047 presentes,
  sin WorkManager/service_role, sin pagos/HC en fuentes nuevas, hotfix auth preservado,
  rutas presentes, hooks y errores B4 presentes.

Solo fakes (`M12VeterinaryMemoryStore` + mocks). Sin Supabase real.

## 10. Decisión sobre migración 048

No se crea migración 048 en este bloque. El estado *preparado* de recordatorios y las
métricas agregadas se resuelven sobre las tablas existentes (047). Si el disparo real
requiere persistencia adicional imprescindible, se propondrá 048 como **bloque separado**.

## Pendientes externos antes del cierre final de M12

- smoke completo agenda/disponibilidad
- solicitud mascota autorizada
- rechazo mascota ajena
- sobrecupo
- confirmación/rechazo/cancelaciones
- historial
- privacidad requester/gestor
- ausencia pagos e HC
- push M06 real / cron / enqueue M12 (si aplica)
- decisión: sin migración 048 en este bloque

## Estado

```text
M12 BLOQUE 4 CERRADO LOCALMENTE
M12 CIERRE TÉCNICO LOCAL COMPLETADO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
Validación estructural 047: 13/13 PASS
```

Cierre técnico local del módulo: `docs/03-modulos/M12-cierre-final.md`. No se declara `M12 CERRADO`.
