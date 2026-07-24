# LeoVer M12 — Matriz funcional final

## Estado

```text
M12 CIERRE TÉCNICO LOCAL COMPLETADO
SMOKE PENDIENTE EXTERNO
CIERRE OFICIAL PENDIENTE
```

**No se declara M12 CERRADO.**

## Matriz por bloque

| Bloque | Capacidad | Fuente / Migración | Estado |
| --- | --- | --- | --- |
| B1 | Modelos, permisos y directorio de veterinarias | `VeterinaryModels.kt` | Local PASS |
| B2 | Perfiles y servicios (persistencia) | Block 2 repos + `046_m12_veterinary_profiles_and_services.sql` | Local PASS |
| B3 | Agenda, disponibilidad y turnos | Appointment repos + `047_m12_veterinary_appointments_and_availability.sql` | Local PASS (smoke PENDIENTE EXTERNO) |
| B4 | Recordatorios preparados, métricas, timeline | `VeterinaryAppointmentModels.kt` (hooks M06) | Local PASS |

## Navegación

| Ruta | Constante |
| --- | --- |
| Directorio | `veterinary_directory` |
| Mis clínicas | `my_veterinary_clinics` |
| Reserva de turno | `veterinary_book_appointment/{clinicId}` |
| Mis turnos | `my_veterinary_appointments` |
| Detalle de turno | `veterinary_appointment_detail/{appointmentId}` |
| Gestión de turno | `veterinary_appointment_management/{appointmentId}` |
| Agenda gestionada | `veterinary_managed_agenda/{clinicId}` |

## Privacidad y seguridad

- `requestNote`: nota privada del solicitante, oculta a la clínica salvo
  autorización; enmascarada cuando el lector no es el solicitante.
- Contacto público solo con opt-in (`publicContactEnabled`).
- Sin pagos ni historia clínica.
- Métricas sin PII.

## Pendiente externo

- Smoke funcional Bloque 3: **PENDIENTE EXTERNO**.
- Push M06 / cron / scheduler: **PENDIENTE EXTERNO**.
