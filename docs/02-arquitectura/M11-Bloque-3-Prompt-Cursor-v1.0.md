# Cursor — M11 Bloque 3: urgencias, eventos y reportes operativos

## Gate de inicio

Ejecutar este bloque solamente después de confirmar:

```text
M11 BLOQUE 2 REMOTO PASS
043 aplicada en Supabase de pruebas
044 hardening aplicada y validada en Supabase de pruebas (PASS)
4 tablas con RLS activo
Smoke funcional aprobado
```

**No iniciar Bloque 3 antes de 044 PASS.** La migración `044` está reservada para hardening de permisos de campañas/aportes; Bloque 3 no debe crear ni modificar `044`.

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado esperado

- Rama: `main`
- Base mínima: commit `8ba4326f79e3d74e2f7fdf5ccbb9bd6a18c01bcc` (HEAD puede incluir el commit de hardening `fix(m11)` posterior a esa base).
- Migraciones 040–044 aplicadas e inmutables al iniciar Bloque 3.
- `044` reservada y ya aplicada: `supabase/migrations/044_m11_harden_campaign_aid_permissions.sql` — Bloque 3 **no** la crea ni la modifica.
- Working tree limpio y alineado con `origin/main`.

## Objetivo

Implementar **M11 Bloque 3 — urgencias operativas, eventos institucionales y reportes/estadísticas de refugios**, preparando el cierre funcional del módulo.

El bloque debe cubrir:

1. Urgencias del refugio con severidad, vigencia, visibilidad, resolución e historial.
2. Eventos institucionales y actividades de voluntariado sin venta de entradas ni pagos.
3. Inscripciones gratuitas o expresiones de interés con cupos.
4. Reportes y estadísticas agregadas del refugio.
5. Exportación segura de reportes sin datos sensibles.
6. Integración con M03, M06, M07, M08, M10 y M11 Bloques 1–2.
7. Migración 045, RLS, RPC, repositorios, UI, ViewModels, fakes y pruebas focalizadas.

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 040–044.
- La nueva persistencia debe comenzar en `045`.
- No crear ni modificar `044` (`044_m11_harden_campaign_aid_permissions.sql` está reservada).
- No aplicar SQL remoto.
- No generar APK.
- No iniciar emulador ni Supabase local.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- Ejecutar pruebas focalizadas y `compileLocalDebugKotlin` al final.
- No implementar pagos, venta de entradas, chat, push, IA ni reputación.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
```

Si hay cambios locales ajenos, no usar `reset`, `restore` ni `clean`; informar y detenerse.

## Paso 2 — Auditoría

Buscar:

```text
ShelterEmergency
Emergency
Urgency
ShelterEvent
VolunteerEvent
EventRegistration
ShelterReport
ShelterStatistics
ReportExport
emergencies
events
registrations
reports
analytics
```

Revisar:

- M03 organizaciones, membresías, sedes y permisos.
- M06 hooks de notificación.
- M07 auditoría/observabilidad.
- M08 mascotas.
- M10 hogares de tránsito.
- M11 Bloques 1 y 2.
- Eventos legacy, reportes legacy y rutas existentes.
- Repositorios, DataProvider, navegación, fakes y mocks.

Clasificar como reutilizable, parcial, legacy, incompatible o ausente.

## Paso 3 — Dominio de urgencias

Crear modelos equivalentes a:

```kotlin
data class ShelterEmergency(
    val id: String,
    val shelterProfileId: String,
    val petId: String?,
    val title: String,
    val description: String,
    val category: ShelterEmergencyCategory,
    val severity: ShelterEmergencySeverity,
    val visibility: ShelterEmergencyVisibility,
    val status: ShelterEmergencyStatus,
    val startsAt: Instant,
    val expiresAt: Instant?,
    val resolvedAt: Instant?,
    val resolutionNotes: String?,
    val evidenceRef: String?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

Categorías mínimas:

```text
MEDICAL
FOOD
MEDICATION
CAPACITY
TRANSPORT
INFRASTRUCTURE
RESCUE
OTHER
```

Severidad:

```text
LOW
MEDIUM
HIGH
CRITICAL
```

Visibilidad:

```text
PUBLIC
INTERNAL
```

Estados:

```text
DRAFT
ACTIVE
RESOLVED
EXPIRED
CANCELLED
```

Reglas:

- solo refugios ACTIVE pueden activar urgencias;
- CRITICAL prepara hook M06, sin implementar push;
- evidencia solo M05 segura;
- una urgencia pública no muestra dirección ni notas internas;
- resolver exige notas;
- no hard-delete;
- expiración automática derivada por tiempo o helper central;
- si refiere a mascota, debe existir y ser accesible por el refugio.

## Paso 4 — Dominio de eventos

Crear:

```kotlin
data class ShelterEvent(
    val id: String,
    val shelterProfileId: String,
    val title: String,
    val description: String,
    val eventType: ShelterEventType,
    val visibility: ShelterEventVisibility,
    val status: ShelterEventStatus,
    val startsAt: Instant,
    val endsAt: Instant,
    val capacity: Int?,
    val registeredCount: Int,
    val publicLocationText: String?,
    val privateLocationText: String?,
    val coverAssetRef: String?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

Tipos:

```text
VOLUNTEERING
ADOPTION_DAY
COLLECTION
TRAINING
OPEN_HOUSE
COMMUNITY
OTHER
```

Estados:

```text
DRAFT
PUBLISHED
FULL
COMPLETED
CANCELLED
```

Crear:

```kotlin
data class ShelterEventRegistration(
    val id: String,
    val eventId: String,
    val userId: String,
    val status: ShelterEventRegistrationStatus,
    val notes: String?,
    val registeredAt: Instant,
    val cancelledAt: Instant?
)
```

Estados:

```text
REGISTERED
WAITLISTED
ATTENDED
NO_SHOW
CANCELLED
```

Reglas:

- sin entradas pagas ni checkout;
- `endsAt > startsAt`;
- capacidad opcional; si se alcanza, nuevas inscripciones pasan a WAITLISTED;
- un usuario no puede tener dos registros activos en el mismo evento;
- dirección privada solo para autorizados o registrados según regla existente;
- cancelar evento cancela registros activos preservando historial;
- asistencia solo la registra un gestor autorizado;
- no datos financieros.

## Paso 5 — Reportes y estadísticas

Crear modelos agregados equivalentes a:

```text
ShelterOperationalSummary
ShelterCapacityMetrics
ShelterPetMetrics
ShelterVolunteerMetrics
ShelterCampaignMetrics
ShelterSupplyMetrics
ShelterEmergencyMetrics
ShelterEventMetrics
```

Métricas mínimas:

- capacidad total, ocupación, reservas y disponibilidad;
- mascotas activas, cuarentena, atención médica, egresos y adopciones;
- voluntarios activos, pausados y finalizados;
- campañas activas/completadas;
- pedidos abiertos/cumplidos y cantidades recibidas;
- urgencias activas/críticas/resueltas;
- eventos próximos/completados e inscripciones;
- rangos de fechas;
- sin PII en agregados.

Exportación:

- CSV o formato ya existente en el proyecto;
- nombres seguros;
- no incluir dirección privada, teléfonos, notas internas ni datos de aportantes;
- no generar PDF en este bloque salvo que exista infraestructura reutilizable y pruebas focalizadas;
- exportación desde datos ya autorizados.

## Paso 6 — Permisos

Agregar/reutilizar:

```text
shelter.emergency.read
shelter.emergency.manage
shelter.event.read
shelter.event.manage
shelter.report.read
shelter.report.export
```

Autoridad exclusivamente por M03/M11.

## Paso 7 — Migración 045

Crear exactamente:

```text
supabase/migrations/045_m11_shelter_emergencies_events_reports.sql
```

No crear ni modificar `supabase/migrations/044_m11_harden_campaign_aid_permissions.sql` (reservada para hardening; debe estar ya aplicada con PASS).

Tablas equivalentes:

```text
shelter_emergencies
shelter_events
shelter_event_registrations
```

Los reportes deben preferir funciones/vistas seguras sobre duplicar datos agregados. Crear tabla de snapshots solo si existe una necesidad real y documentada.

Agregar:

- FK, timestamps, checks, índices y unicidades parciales;
- RLS deny-by-default;
- RPC;
- grants mínimos;
- comentarios;
- `CREATE TABLE IF NOT EXISTS`;
- `CREATE INDEX IF NOT EXISTS`;
- `CREATE OR REPLACE FUNCTION`;
- `DROP POLICY IF EXISTS` antes de `CREATE POLICY`;
- `ENABLE ROW LEVEL SECURITY`;
- sin `DROP TABLE`;
- ninguna firma con obligatorio después de `DEFAULT`.

## Paso 8 — RPC

Urgencias:

```text
m11_create_shelter_emergency
m11_update_shelter_emergency
m11_change_shelter_emergency_status
m11_resolve_shelter_emergency
m11_get_shelter_emergency
m11_list_public_shelter_emergencies
m11_list_shelter_emergencies
```

Eventos:

```text
m11_create_shelter_event
m11_update_shelter_event
m11_change_shelter_event_status
m11_get_shelter_event
m11_list_public_shelter_events
m11_list_shelter_events
m11_register_shelter_event
m11_cancel_shelter_event_registration
m11_mark_shelter_event_attendance
m11_list_shelter_event_registrations
```

Reportes:

```text
m11_get_shelter_operational_summary
m11_get_shelter_capacity_metrics
m11_get_shelter_pet_metrics
m11_get_shelter_volunteer_metrics
m11_get_shelter_campaign_metrics
m11_get_shelter_supply_metrics
m11_get_shelter_emergency_metrics
m11_get_shelter_event_metrics
```

Todas con:

- `auth.uid()`;
- `SECURITY DEFINER`;
- `SET search_path = public`;
- permisos M03/M11;
- helpers internos revocados;
- proyecciones públicas limitadas;
- sin service_role desde Android.

## Paso 9 — Integraciones

M05:

- evidencia y portada solo `m05://` o `file_asset:`;
- rechazar URLs públicas directas.

M06:

- hooks para urgencia crítica, evento publicado/cancelado y cambio de lista de espera;
- no implementar push.

M07:

- eventos auditables para creación, cambios de estado, resolución, registro, cancelación, asistencia y exportación.

M08/M09/M10/M11:

- métricas coherentes con mascotas, adopciones, tránsitos, alojamientos, campañas y aportes;
- no cerrar procesos ajenos;
- no duplicar datos canónicos.

## Paso 10 — Repositorios

Crear:

```text
ShelterEmergencyRepository
ShelterEventRepository
ShelterReportRepository
```

Con flujos, errores tipados, resolución por ID, mapeo técnico y sin acceso Supabase desde ViewModels.

## Paso 11 — Errores de dominio

Agregar equivalentes:

```text
SHELTER_EMERGENCY_NOT_FOUND
SHELTER_EMERGENCY_FORBIDDEN
SHELTER_EMERGENCY_INVALID_TRANSITION
SHELTER_EMERGENCY_RESOLUTION_REQUIRED
SHELTER_EMERGENCY_EXPIRED

SHELTER_EVENT_NOT_FOUND
SHELTER_EVENT_FORBIDDEN
SHELTER_EVENT_INVALID
SHELTER_EVENT_FULL
SHELTER_EVENT_ALREADY_REGISTERED
SHELTER_EVENT_REGISTRATION_NOT_FOUND
SHELTER_EVENT_ATTENDANCE_FORBIDDEN

SHELTER_REPORT_FORBIDDEN
SHELTER_REPORT_INVALID_RANGE
SHELTER_REPORT_EXPORT_FAILED
```

## Paso 12 — UI y navegación

Rutas equivalentes:

```text
shelter_emergencies/{shelterId}
shelter_emergency_detail/{emergencyId}
shelter_emergency_form/{shelterId}
shelter_emergency_form/{shelterId}/{emergencyId}

shelter_events/{shelterId}
shelter_event_detail/{eventId}
shelter_event_form/{shelterId}
shelter_event_form/{shelterId}/{eventId}
shelter_event_registrations/{eventId}

shelter_reports/{shelterId}
```

Agregar accesos desde dashboard.

Estados explícitos: loading, vacío, contenido, error y éxito. Prevenir doble envío.

## Paso 13 — Fakes

Persistentes, mutables y coherentes:

- urgencias y resolución;
- eventos, cupos, waitlist e inscripciones;
- asistencia;
- métricas derivadas;
- exportación segura;
- permisos;
- evidencia M05;
- hooks M06 sin push;
- auditoría M07.

## Paso 14 — Tests focalizados

Crear:

```text
app/src/test/java/com/comunidapp/app/viewmodel/M11ShelterEmergenciesEventsReportsTest.kt
app/src/test/java/com/comunidapp/app/viewmodel/M11Block3MigrationStaticGuardsTest.kt
```

Cubrir como mínimo:

1. crear urgencia draft;
2. activar urgencia;
3. urgencia crítica prepara hook;
4. público no ve datos privados;
5. evidencia insegura rechazada;
6. resolver exige notas;
7. resolver urgencia;
8. expirar;
9. cancelar;
10. crear evento;
11. fechas válidas;
12. publicar;
13. inscripción;
14. duplicado rechazado;
15. cupo completo → waitlist;
16. cancelación de registro;
17. marcar asistencia;
18. cancelar evento;
19. historial preservado;
20. métricas de capacidad;
21. métricas de mascotas;
22. métricas de voluntarios;
23. métricas de campañas/pedidos;
24. métricas de urgencias/eventos;
25. rango inválido;
26. exportación sin PII;
27. usuario sin permiso;
28. voluntario sin autoridad automática;
29. enum desconocido;
30. ID vacío/inexistente;
31. error de repositorio;
32. doble envío;
33. loading inicial;
34. no Supabase real;
35. migración 045;
36. RLS/policies/search_path/defaults;
37. sin pagos;
38. migraciones 040–044 intactas.

Ejecutar únicamente suites focalizadas:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "*M11ShelterEmergenciesEventsReportsTest" `
  --tests "*M11Block3MigrationStaticGuardsTest" `
  --tests "*M11ShelterCampaignsAndAidTest" `
  --tests "*M11ShelterOperationsCoreTest" `
  --tests "*M10FosterCareManagementTest"
```

## Paso 15 — Compilación

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No ejecutar lint, JaCoCo, assembleDebug ni APK.

## Paso 16 — Documentación

Actualizar:

```text
docs/03-modulos/M11-refugios.md
```

Crear:

```text
docs/02-arquitectura/M11-urgencias-eventos-reportes.md
docs/05-operacion/M11-aplicacion-migracion-045-supabase.md
```

Registrar arquitectura, seguridad, RPC, rutas, pruebas, exportación, métricas y smoke posterior.

## Paso 17 — Git

Revisar:

```powershell
git status
git diff --stat
git diff --check
```

Agregar:

```powershell
git add app supabase docs
git diff --cached --stat
git diff --cached --check
```

Commit único:

```powershell
git commit -m "feat(m11): add shelter emergencies events and reports"
git push origin main
```

## Entrega final

Informar:

1. Estado inicial.
2. Auditoría.
3. Implementación.
4. Archivos.
5. Migración 045.
6. Tablas/RLS/índices/constraints.
7. RPC/permisos.
8. Urgencias.
9. Eventos.
10. Inscripciones/cupos.
11. Reportes/métricas/exportación.
12. Integraciones.
13. UI/rutas.
14. Tests.
15. Compilación.
16. Docs.
17. SHA.
18. Push.
19. `git status -sb`.
20. Orden manual para aplicar 045.
21. Pendientes del cierre final M11.

No aplicar 045 remotamente.
No comenzar el cierre final.
No generar APK.
