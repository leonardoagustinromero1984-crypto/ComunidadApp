# M10 — Hogares de tránsito (bloque 1)

## Auditoría inicial

| Área | Clasificación |
|------|----------------|
| `FosterHomeListing` / Sumate / `PublishFosterScreen` | **Legacy / mock** — listing personal con `contact_info` público |
| `foster_homes` (006) / `foster_requests` (011) | **Incompatible** — sin capacidad/ocupación M10, estados PENDING |
| M08 `TEMPORARY_CUSTODIAN` | **Reutilizable** para custodia temporal al ingreso |
| M03 `organization_memberships` | **Reutilizable** en RPC de permiso sobre mascota |
| M09 adopciones | **Independiente** — no transferir PRINCIPAL ni finalizar adopción desde M10 |
| Storage / coords públicas | **Ausente** en este bloque (zona textual aproximada) |

## Modelos

- `FosterHomeProfile` + proyección pública `FosterHomePublicListing`
- `FosterHomeRequest` (tabla SQL `foster_care_requests`; evita choque con legacy `FosterRequest`)
- `FosterPlacement`

## Reglas clave

- Un perfil abierto por usuario; ocupación calculada (no editable en UI).
- Listado público: solo `ACTIVE`; sin dirección privada ni teléfono.
- Solicitud: hogar activo/disponible, pet compatible, no fallecida, sin tránsito activo.
- Aceptar reserva capacidad; ingreso (`m10_start_foster_placement`) confirma ocupación.
- Ingreso crea `TEMPORARY_CUSTODIAN` M08; **no** cambia PRINCIPAL.

## Persistencia

Migración `040_m10_foster_homes_core.sql` (**pendiente de apply remoto**).

Detalle: `docs/02-arquitectura/M10-persistencia-hogares-transito.md`

## Pantallas / rutas

`foster_homes`, `foster_home_detail`, `my_foster_home`, `foster_home_form`, solicitudes enviadas/recibidas, formulario/detalle de solicitud, alojamientos.

## Tests

`M10FosterHomeCoreTest` (+ regresión M08/M09 focalizada).

## Pendientes (bloques siguientes)

Gastos, comprobantes, donaciones, evolución/galería, pedidos de ayuda, finalización completa del tránsito, calificaciones, reputación, chat/push, estadísticas, apply remoto 040, APK.
