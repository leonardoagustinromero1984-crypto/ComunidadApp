# M09 — Adopciones (bloque 2: postulaciones y selección)

## Auditoría inicial (bloque 2)

| Área | Clasificación |
|------|----------------|
| Modelos/repos M09 publicaciones (037) | **Reutilizable** |
| `AdoptionRequest` / `adoption_requests` (legacy PENDING) | **Legacy / incompatible** — estados y campos insuficientes; RLS select amplio; writes PostgREST |
| Dialogo “Quiero adoptar” en detalle | **Parcial / legacy** — reemplazado por pantalla de postulación |
| Matching sugerido / entrevistas en Mis adopciones | **Fuera de alcance** (siguientes bloques) |
| Tabla `adoption_applications` | **Ausente** → creada en 038 |
| Migraciones 037 / 038 remotas | **Pendientes de apply** |

## Modelo

`AdoptionApplication` + `AdoptionApplicationStatus`:

`SUBMITTED` | `UNDER_REVIEW` | `ACCEPTED` | `REJECTED` | `WITHDRAWN`

Valores desconocidos (p. ej. legacy `PENDING`) → `SUBMITTED` sin crash.

Teléfono: solo visible para postulante o gestor en el detalle (`visibleContactPhone`).

La aceptación **no** finaliza la adopción.

## Reglas de negocio

1. Solo publicaciones `PUBLISHED` aceptan postulaciones.
2. Publicador/gestor no puede postularse a la propia.
3. Una sola postulación activa por usuario y publicación.
4. `REJECTED` / `WITHDRAWN` permiten nueva postulación.
5. Solo el postulante retira.
6. Solo gestor ve recibidas / revisa / acepta / rechaza.
7. Postulante solo ve las propias.
8. Pausada / adoptada / cerrada no aceptan nuevas.
9–10. Transiciones controladas; rechazada no pasa a aceptada directamente.
11. Al aceptar: elegida `ACCEPTED`, demás activas `REJECTED`, publicación `PAUSED`, mascota intacta.
12–16. Idempotencia / IDs vacíos / errores tipados / sin Supabase directo en ViewModels.

## Persistencia

Migración: `supabase/migrations/038_m09_adoption_applications.sql`

Detalle: `docs/02-arquitectura/M09-persistencia-postulaciones.md`

**037 y 038 no aplicadas remotamente en esta etapa.**

## RLS / RPC

- Writes directos denegados; canal `m09_*` SECURITY DEFINER.
- RPCs: submit, withdraw, mark under review, accept, reject, list mine, list received, get.

## Repositorio

`AdoptionApplicationRepository` + `MockAdoptionApplicationRepository` + `SupabaseAdoptionApplicationRepository`.

Errores tipados vía `M09AdoptionErrorMapper` (`APPLICATION_*`, `ADOPTION_NOT_ACCEPTING_APPLICATIONS`, `CANNOT_APPLY_TO_OWN_ADOPTION`, …).

## Pantallas y rutas

| Ruta | Pantalla |
|------|----------|
| `adoption_apply/{adoptionId}` | Formulario de postulación |
| `my_adoption_applications` | Mis postulaciones |
| `received_adoption_applications` | Postulaciones recibidas |
| `adoption_application_detail/{applicationId}` | Detalle + acciones |

Accesos desde: detalle de publicación, Sumate/Adopciones, Mis publicaciones, perfil.

## ViewModels

- `AdoptionApplyViewModel`
- `MyAdoptionApplicationsViewModel`
- `ReceivedAdoptionApplicationsViewModel`
- `AdoptionApplicationDetailViewModel`

`StateFlow` + `SharingStarted.Eagerly` donde aplica; bloqueo de doble envío.

## Tests focalizados

Clases ejecutadas:

- `com.comunidapp.app.viewmodel.M09AdoptionApplicationTest`
- `com.comunidapp.app.viewmodel.M09AdoptionPublicationTest`
- `com.comunidapp.app.viewmodel.M08IntegrationRegressionTest`

Resultado: **PASS** (`testDebugUnitTest` focalizado).

## Compilación

`.\gradlew.bat compileLocalDebugKotlin` → **PASS** (también vía `testDebugUnitTest`)

## Pendientes del próximo bloque

- Agenda de entrevistas
- Videollamadas
- Documentación del adoptante
- Contratos de adopción
- Seguimiento postadopción / marcar adopción finalizada (flujo aparte)
- Reputación
- Chat / notificaciones push específicas
- Estadísticas
- Apply remoto de migraciones 037 + 038 + smoke staging / APK
