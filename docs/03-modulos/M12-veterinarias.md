# M12 — Veterinarias

**Estado del módulo:** Bloques 1–4 cerrados localmente · **cierre técnico local COMPLETADO** · **cierre oficial PENDIENTE**.
**Migración 046:** aplicada y validada en Supabase de pruebas (Bloque 2).
**Migración 047:** aplicada en Supabase de pruebas · validación **estructural** remota **13/13 PASS**.
**Smoke funcional Bloque 3:** **pendiente externo** (diferido por decisión del usuario). No se afirma smoke aprobado.

```text
M12 CIERRE TÉCNICO LOCAL COMPLETADO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
Smoke funcional de agenda y turnos: PENDIENTE EXTERNO
No se declara M12 CERRADO
Validación estructural 047: 13/13 PASS
Riesgo aceptado: validación funcional remota diferida
```

> Estado global: **M12 no está cerrado todavía**. El cierre técnico local está completo; falta el
> smoke funcional remoto del Bloque 3 para el cierre oficial.

> **Cierre técnico local:** ver `docs/03-modulos/M12-cierre-final.md`,
> `docs/05-operacion/M12-validacion-final.md` y
> `docs/02-arquitectura/M12-matriz-funcional-final.md`. El smoke pendiente vive en
> `docs/05-operacion/M12-smoke-funcional-pendiente-cierre.md`.

> Nota D01: M12 técnico (Veterinarias) ≠ M12 producto (mascotas perdidas). Ver nota en D01.

## Bloque 1 — dominio y directorio local

Modelos, fakes, directorio público, errores tipados, permisos `veterinary.*` (IDs).

## Bloque 2 — persistencia de perfiles y servicios

- SQL: `046_m12_veterinary_profiles_and_services.sql` (6 tablas, RLS, 26 RPC).
- Android: mocks B2 + `SupabaseVeterinary*` (RPC).
- UI: hub de gestión, profesionales, servicios, horarios.
- Docs: `M12-persistencia-perfiles-servicios.md`, `M12-aplicacion-migracion-046-supabase.md`.

## Bloque 3 — agenda, disponibilidad y solicitudes de turno

- SQL: `047_m12_veterinary_appointments_and_availability.sql` (5 tablas, RLS, 22 RPC cliente, permisos schedule/appointment).
- Slots calculados (sin pre-generación masiva); cupo transaccional; autoridad M08.
- Android: `VeterinaryScheduleRepository` / `VeterinaryAppointmentRepository` (Mock + Supabase).
- UI: reserva, mis turnos, agenda gestionada, settings, reglas.
- Docs: `M12-agenda-disponibilidad-turnos.md`, `M12-aplicacion-migracion-047-supabase.md`.
- Sin pagos ni historia clínica. Hooks M06 preparados (sin push).

## Integraciones

| Módulo | Uso |
|--------|-----|
| M03 | org ACTIVE, sede, `has_org_permission` |
| M04 | `organizations.review_verification` en `m12_review_*` (B2) |
| M05 | logo/cover/avatar |
| M06 | hooks de turno/recordatorio (contrato, sin push) |
| M07 | `m07_best_effort_audit` |
| M08 | autoridad de mascota para solicitar turno |

## Límites

Sin pagos, señas, checkout, Mercado Pago, historia clínica, diagnóstico, recetas, laboratorio, chat, video ni push real. Legacy `service_profiles` / `bookings` intactos.

## Bloque 4 — recordatorios, endurecimiento y seguimiento (cerrado localmente)

- Recordatorios idempotentes para turnos `CONFIRMED` en estado **preparado** (sin push):
  `REMINDER_24H_DUE`, `REMINDER_2H_DUE`, `REMINDER_CANCELLED`. Nunca afirma push enviado.
- Endurecimiento: expiración de `REQUESTED`, confirmación simultánea, doble transición,
  ventana de cancelación, servicio/profesional inactivo al confirmar, DST/zona horaria,
  reintento seguro (`retrySafeTransition` → `VETERINARY_APPOINTMENT_RETRY_CONFLICT`).
- Seguimiento en UI: línea de tiempo, próximo paso, indicador de recordatorio preparado,
  motivo de rechazo/cancelación según autoridad, reintento y recuperación de conectividad.
- Métricas agregadas sin PII por clínica/rango/servicio/profesional.
- **Sin migración 048** en este bloque (ver arquitectura).
- Docs: `M12-recordatorios-endurecimiento-seguimiento.md`, `M12-validacion-bloque-4.md`.

### Estado

```text
M12 CIERRE TÉCNICO LOCAL COMPLETADO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
Validación estructural 047: 13/13 PASS
```

No se declara `M12 CERRADO`: falta el smoke funcional remoto del Bloque 3.

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
