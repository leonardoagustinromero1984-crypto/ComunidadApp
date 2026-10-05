# LeoVer 17B.12 — fixtures de QA en STAGING

Estos datos son solo para STAGING (`tobqbddfcyitwgbkthhy`). No se aplican a producción y no viven en una migración productiva.

## Cuentas que se reutilizan

No se crean usuarios nuevos. El dataset parte de los actores ya sembrados por `scripts/qa/seed-community-care-actors.ps1`:

| Id | Usuario | Uso en 17B.12 |
| --- | --- | --- |
| QA01 | qa01owner | Dueño, perdidos, VitaCora |
| QA02 | qa02finder | Encontrados |
| QA06 | qa06foster | Tránsito |
| QA07 | qa07shelter | Organización A (administra Refugio Norte) |
| QA10 | qa10vet | Prestador veterinario |
| QA11 | qa11proa | Acceso profesional |
| QA12 | qa12proind | Profesional independiente |
| QA13 | qa13shop | Comercio |
| QA14 | qa14adopter | Postulación de adopción |
| QA15 | qa15adopter2 | Segunda postulación |

QA09 queda como está en STAGING (`PENDING`). No se restaura `NOT_REQUESTED`.

## Organizaciones

| Rol | Slug existente | Nombre |
| --- | --- | --- |
| QA Refugio A | `qa-cc02-shelter-n` | QA - Refugio Norte |
| QA Refugio B | `qa-cc02-shelter-u` | QA - Refugio Sur no verificado |

El contenido nuevo lleva el prefijo `QA17B12` y queda atado a una sola de esas organizaciones. Entrar desde A a voluntariado, donaciones o adopciones no debe devolver filas de B. Eso también lo cubre `OrganizationScope` en los tests de la app.

## Qué agrega el seed

Depende de `seed-community-care-actors.ps1` y de `seed_community_care_02.sql`. Si faltan los refugios, los actores o una mascota previa, el SQL aborta.

Inserta filas `QA17B12` en las tablas que leen los RPC de la app:

- `m17_donation_campaigns`, `m17_in_kind_needs`, `m17_volunteer_opportunities` (A y B).
- `adoption_publications` y `adoption_applications`.
- `lost_found_alerts`: un perdido histórico abierto, un encontrado reciente y un resuelto que el listado público no muestra.
- `service_providers`, `service_offerings` y `bookings`.
- `foster_care_requests` y `foster_care_applications`.
- `leover_verification_requests` solo si QA10 no tiene ya un pedido veterinario pendiente.
- `vitacora_update_proposals`.

No cambia contraseñas.

## Cómo sembrarlo

1. Confirmar que el CLI está vinculado a STAGING.
2. Tener corrido antes `scripts/qa/seed-community-care-actors.ps1` (crea QA01–QA16 y los dos refugios).
3. Revisar sin ejecutar:

```powershell
.\scripts\qa\seed-17b12-staging.ps1
```

4. Ejecutar solo en STAGING:

```powershell
.\scripts\qa\seed-17b12-staging.ps1 -Apply
```

El script aborta si el proyecto vinculado no es `tobqbddfcyitwgbkthhy`. El SQL también aborta si la sesión no tiene `leover.qa_target` igual a ese ref, así que ejecutar el archivo solo no alcanza.

## Cómo limpiarlo

Borrar únicamente filas con el prefijo, en STAGING:

Volver a correr el seed borra primero las filas `QA17B12` (campañas, cosas, voluntariado, perdidos/encontrados por `location_label` o `note`, adopciones, tránsito, turnos, prestadores, propuestas y verificaciones marcadas) y las vuelve a insertar. No borra personas ni organizaciones.
