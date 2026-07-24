# M11 — Urgencias, eventos y reportes operativos (arquitectura bloque 3)

LeoVer M11 Bloque 3: urgencias operativas, eventos institucionales (inscripciones gratuitas) y reportes/estadísticas agregadas del refugio. Sin venta de entradas, pagos, CBU ni datos sensibles en exportaciones.

**Gate:** iniciar solo tras **044 PASS** en Supabase de pruebas. Bloque 3 no modifica migraciones 040–044.

## Integración

| Módulo | Uso |
|--------|-----|
| M03 | Permisos `shelter.emergency.*`, `shelter.event.*`, `shelter.report.*` |
| M05 | Evidencia/portada `m05://` y `file_asset:` únicamente |
| M06 | Hooks `EMERGENCY_CRITICAL_ACTIVATED`, `EVENT_PUBLISHED`, `EVENT_CANCELLED`, `EVENT_WAITLISTED`; **sin push** |
| M07 | Auditoría: creación, cambios de estado, resolución, registro, cancelación, asistencia, exportación |
| Bloques 1–2 M11 | Métricas coherentes con placements, voluntarios, campañas e insumos |

## Modelos

- `ShelterEmergency`, `ShelterEmergencyPublicListing`
- `ShelterEvent`, `ShelterEventPublicListing`, `ShelterEventRegistration`
- Métricas: `ShelterCapacityMetrics`, `ShelterPetMetrics`, `ShelterVolunteerMetrics`, `ShelterCampaignMetrics`, `ShelterSupplyMetrics`, `ShelterEmergencyMetrics`, `ShelterEventMetrics`
- `ShelterOperationalSummary`, `ShelterReportExport`

## Estados — urgencia

```text
DRAFT → ACTIVE → RESOLVED | EXPIRED | CANCELLED
```

- Activar exige refugio `ACTIVE`; `CRITICAL` + `ACTIVE` prepara hook M06.
- Resolver solo vía `resolveEmergency` con notas obligatorias (no `changeStatus(RESOLVED)`).
- Expiración automática cuando `expires_at <= now` y status `ACTIVE`.
- Proyección pública sin `resolutionNotes`, `evidenceRef` ni datos internos.

## Estados — evento

```text
DRAFT → PUBLISHED ↔ FULL → COMPLETED | CANCELLED
```

- Publicar exige refugio `ACTIVE`.
- Cupo: `registered_count` sincronizado; `FULL` cuando cupo alcanzado.
- Inscripción gratuita: `REGISTERED` o `WAITLISTED` si cupo lleno.
- Cancelar evento cancela inscripciones abiertas preservando historial.
- Listado público sin `privateLocationText`.

## Reportes

- RPC/jsonb agregados sin PII (conteos, rangos, capacidad).
- Export CSV cliente: métricas agregadas + `shelter_id`; requiere `shelter.report.export` para auditoría M07.
- Rango inválido (`to <= from`) → `SHELTER_REPORT_INVALID_RANGE`.

## Repositorios (Android)

| Interfaz | Mock |
|----------|------|
| `ShelterEmergencyRepository` | `MockShelterEmergencyRepository` |
| `ShelterEventRepository` | `MockShelterEventRepository` |
| `ShelterReportRepository` | `MockShelterReportRepository` |

Store: `M11ShelterMemoryStore` (`emergencies`, `events`, `eventRegistrations`, `auditEvents`, `m06Hooks`).

ViewModels: `ShelterEmergenciesEventsReportsViewModels.kt`. Rutas en `NavRoutes` / `ComunidappNavGraph`. Dashboard: Urgencias, Eventos, Reportes.

## Migración 045

Archivo: `supabase/migrations/045_m11_shelter_emergencies_events_reports.sql`

Tablas:

- `shelter_emergencies`
- `shelter_events`
- `shelter_event_registrations`

RLS: `enable row level security`; `drop policy if exists`; select acotado; writes directos denegados.

RPC `m11_*` (25 funciones cliente): urgencias, eventos/inscripciones, métricas y resumen operativo.

Helpers internos revocados de `authenticated`: `_m11_sync_event_registration_counts`, `_m11_expire_shelter_emergencies`, `_m11_require_shelter_report_range`, `_m11_require_shelter_report_read`, `_m11_validate_emergency_pet`.

**Forward-only** sobre 001–044. **LOCAL ONLY** hasta apply remoto autorizado.

## Errores de dominio (selección)

```text
SHELTER_EMERGENCY_NOT_FOUND | FORBIDDEN | INVALID_TRANSITION | RESOLUTION_REQUIRED | EXPIRED
SHELTER_EVENT_NOT_FOUND | FORBIDDEN | INVALID | ALREADY_REGISTERED | REGISTRATION_NOT_FOUND | ATTENDANCE_FORBIDDEN
SHELTER_REPORT_FORBIDDEN | INVALID_RANGE | EXPORT_FAILED
SHELTER_EVIDENCE_REF_INVALID
```

## Pantallas

```text
shelter_emergencies/{shelterId}
shelter_emergency_detail/{emergencyId}
shelter_emergency_form/{shelterId}[/{emergencyId}]

shelter_events/{shelterId}
shelter_event_detail/{eventId}
shelter_event_form/{shelterId}[/{eventId}]
shelter_event_registrations/{eventId}

shelter_reports/{shelterId}
```

## Tests

| Archivo | Alcance |
|---------|---------|
| `M11ShelterEmergenciesEventsReportsTest` | 36 escenarios dominio mock (34 del prompt + extras) |
| `M11Block3MigrationStaticGuardsTest` | 18 guardas estáticas SQL 045 |

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "*M11ShelterEmergenciesEventsReportsTest" `
  --tests "*M11Block3MigrationStaticGuardsTest"
```

## Limitaciones

- Sin pagos, entradas, chat, push real, reputación.
- Apply remoto 045 pendiente; smoke manual post-apply.
- Cierre final M11 **no iniciado**.

## Operación

Ver `docs/05-operacion/M11-aplicacion-migracion-045-supabase.md`.
