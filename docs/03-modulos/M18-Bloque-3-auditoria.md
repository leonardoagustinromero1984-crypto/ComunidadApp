# M18 Bloque 3 — Auditoría participación y operación

**Fecha:** 2026-08-02  
**Dominio (D01):** M18 Eventos comunitarios  
**Migración 058:** creada, **no aplicada**

## Alcance ya cubierto (B1–B2)

- Modelos `M18CommunityEvent`, `M18EventRegistration`, capacidad agregada
- Mock + Supabase repositorio con `registerForEvent`, `cancelRegistration`, `checkInRegistration`
- RPC 058: `m18_register_for_event`, `m18_cancel_registration`, `m18_check_in_registration`, `m18_get_capacity_summary`, `m18_list_registrations_for_manage`

## Brechas cerradas en Bloque 3

| Área | Implementación |
|------|----------------|
| Servicio dominio | `M18EventOperationsService` |
| Estados extendidos | `ATTENDED`, `REJECTED` (mock; 058 solo hasta `NO_SHOW`) |
| Resumen operativo | `M18EventOperationsSummary` |
| Panel organizador | `M18EventOperationsScreen` + ViewModel |
| UI participante | estados `M18EventParticipationUiState` |
| Promoción waitlist | mock idempotente; remoto vía cancelación (058) |
| Privacidad | alias en panel; sin lista pública de participantes |

## Persistencia adicional

**059 no requerida** para operación mock completa. Brecha remota documentada:

- `ATTENDED` / `REJECTED` no están en CHECK de 058
- Promoción manual sin RPC pública dedicada
- `markNoShow` / `markAttendance` remoto → `M18_*_REMOTE_PENDING`

## Integraciones

- **M10:** `publicLocationText` / `venueName` en detalle público
- **M06:** hooks ampliados; `M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE` no bloquea inscripción
- **M03:** permisos `event.view` / `event.manage` sin roles paralelos

## Riesgos privacidad

- No exponer `userId`, emails ni teléfonos en modelos públicos
- Panel org: alias + estado + acciones autorizadas

## Estado terminal inscripción

`CANCELLED`, `ATTENDED`, `NO_SHOW`, `REJECTED` — no reactivación sin nueva inscripción (salvo cancel/no-show/rejected).
