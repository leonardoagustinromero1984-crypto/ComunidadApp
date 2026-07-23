# M09 — Adopciones (bloque 1: publicaciones)

## Estado inicial encontrado

| Área | Clasificación |
|------|----------------|
| Tabla `public.adoptions` (legacy) | **Legacy / parcial** — sin `pet_id`; estados `AVAILABLE` / `IN_PROCESS` / `ADOPTED`; RLS select amplio y writes directos |
| `AdoptionPost` / `AdoptionStatus` | **Legacy** — denormalizado; estados viejos |
| `AdoptionRepository` + Mock + Supabase | **Parcial** — list/filter/add/update; writes PostgREST directos |
| UI Adoptions / Detail / MyAdoptions / Publish | **Parcial / mock** — sin selección de mascota ni pause/resume/close/adopt coherentes |
| Navegación | **Parcial** — `adoptions`, `adoption_detail/{id}`, `my_adoptions`, `publish_adoption` |
| Postulaciones (`adoption_requests`) | **Fuera de alcance** (bloque siguiente) |
| Migración previa más alta | **036** (M08) |

## Arquitectura reutilizada

- Modelo `AdoptionPost` evolucionado (campos `petId`, `title`, `requirements`, timestamps).
- Capacidades M08 (`_m08_actor_effective_capabilities`, `m08_actor_can_read_pet`) para autorización de publicación.
- `pet_status_history` al marcar adoptada (pet `ACTIVE` → `ARCHIVED`, reason `ADOPTED`).
- Patrón RPC-only (como M08 pets) para escrituras.
- `StateFlow` + estados Loading / Empty / Content / Error / NotFound en ViewModels.
- Foto principal: se reutiliza `pets.photo_url` (sin galería nueva).

## Funcionalidades implementadas

- Publicar (borrador o publicada) ligada a mascota activa administrable.
- Listado público solo `PUBLISHED`.
- Mis publicaciones (todos los estados del publicador/gestor).
- Detalle con loading / not found / error / acciones de propietario.
- Editar (bloqueado si `CLOSED` / `ADOPTED`).
- Pausar / reanudar / cerrar / marcar adoptada (con confirmación UI).
- Idempotencia en mock/RPC para estados ya finales o iguales.

## Reglas de negocio

- Una sola publicación abierta (`DRAFT`/`PUBLISHED`/`PAUSED`) por `pet_id` (índice único parcial).
- Mascota `DECEASED` o `ARCHIVED` (u otro no `ACTIVE`) → `PET_NOT_ADOPTABLE`.
- Solo actores con capacidad de gestión M08 pueden crear/modificar.
- Pausada fuera del listado público.
- Cerrada/adoptada no editable.
- `markAsAdopted` actualiza publicación + pet + historial en una RPC.
- IDs vacíos/inexistentes → `ADOPTION_NOT_FOUND` sin crash.
- Errores mapeados (`M09AdoptionErrorMapper`) sin mensajes técnicos de Supabase.

## Persistencia

Migración nueva: `supabase/migrations/037_m09_adoption_publications.sql`

- Columnas: `pet_id`, `title`, `requirements`, `location_text`, `published_at`, `publisher_organization_id`.
- Estados: `DRAFT`, `PUBLISHED`, `PAUSED`, `ADOPTED`, `CLOSED` (backfill desde legacy).
- Índices: `pet_id`, `status`, `published_at`.
- Unique parcial: `adoptions_one_open_per_pet_uidx`.

**No aplicada remotamente en esta etapa.**

Detalle: `docs/02-arquitectura/M09-persistencia-publicaciones-adopcion.md`

## RLS / RPC

- SELECT: público autenticado solo `PUBLISHED`; dueño/gestor ve propias no públicas.
- INSERT/UPDATE/DELETE directo: denegado (`with check/using false`).
- RPCs: `m09_create_adoption_publication`, `m09_update_adoption_publication`, `m09_set_adoption_status`, `m09_mark_adoption_adopted`, `m09_list_published_adoptions`, `m09_list_my_adoptions`, `m09_get_adoption`.

## Pantallas y rutas

| Ruta | Pantalla |
|------|----------|
| `adoptions` | Listado público |
| `adoption_detail/{adoptionId}` | Detalle |
| `my_adoptions` | Mis publicaciones |
| `adoption_form` | Alta |
| `adoption_form/{adoptionId}` | Edición |
| `publish_adoption` | Alias → formulario M09 |

## ViewModels

- `AdoptionsViewModel` — listado público
- `MyAdoptionsViewModel` — mis publicaciones + acciones
- `AdoptionDetailViewModel` — detalle + pause/resume/close/adopt
- `AdoptionFormViewModel` — crear/editar borrador/publicar

## Tests focalizados

Clases ejecutadas:

- `com.comunidapp.app.viewmodel.M09AdoptionPublicationTest`
- `com.comunidapp.app.viewmodel.MarkPetDeceasedViewModelTest`
- `com.comunidapp.app.viewmodel.M08IntegrationRegressionTest`

Resultado: **PASS** (`testDebugUnitTest` focalizado).

Cobertura M09: listado, vacío, error, create, duplicado, fallecida, archivada, edit, pause/resume/close, mark adopted + pet + historial, forbidden, id vacío/inexistente, doble envío, status desconocido, pausada fuera de público.

## Compilación

`.\gradlew.bat compileLocalDebugKotlin` → **PASS**

## Pendientes del siguiente bloque

- Postulaciones (aceptar/rechazar candidatos)
- Entrevistas
- Carga de documentación
- Seguimiento postadopción
- Reputación del adoptante
- Chat / notificaciones push específicas de adopción
- Estadísticas avanzadas
- Apply remoto de migración 037 + smoke staging / APK
