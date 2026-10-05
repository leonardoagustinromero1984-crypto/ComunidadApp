# LeoVer 17B.12 — fixtures de QA en STAGING

Estos datos son solo para STAGING (`tobqbddfcyitwgbkthhy`). No se aplican a producción y no viven en una migración productiva. Este documento no ejecuta el seed.

## Cuentas que se reutilizan

No se crean usuarios nuevos y no se cambian contraseñas. El dataset parte de los actores ya sembrados por `scripts/qa/seed-community-care-actors.ps1`:

| Id | Usuario | Uso en 17B.12 |
| --- | --- | --- |
| QA01 | qa01owner | Dueño de `QA17B12 Mascota Norte`, perdido histórico |
| QA02 | qa02finder | Dueño de `QA17B12 Mascota Sur`, encontrado |
| QA06 | qa06foster | Postulación de tránsito |
| QA07 | qa07shelter | Organización A (administra Refugio Norte) |
| QA10 | qa10vet | Prestador veterinario y propuesta VitaCora |
| QA14 | qa14adopter | Postulación de adopción |

QA09 queda como está en STAGING. No se restaura `NOT_REQUESTED`.

## Organizaciones

| Rol | Slug existente | Nombre |
| --- | --- | --- |
| QA Refugio A | `qa-cc02-shelter-n` | QA - Refugio Norte |
| QA Refugio B | `qa-cc02-shelter-u` | QA - Refugio Sur no verificado |

## Tablas canónicas que lee la app

El seed no usa `m17_donation_campaigns`, `m17_in_kind_needs` ni `m17_volunteer_opportunities`. Esas tablas no forman parte del esquema canónico.

| Pantalla | Lectura canónica | Dato QA17B12 |
| --- | --- | --- |
| Aportar dinero | `canon_list_donation_campaigns` sobre `donation_campaigns` | Campaña A y campaña B |
| Donar cosas | `in_kind_offers` unido a la campaña de esa organización | Alimento Norte, Higiene Sur |
| Voluntariado | `canon_list_events` sobre `community_events` | Paseos Norte, Cupo Norte, Voluntariado Sur |
| Adopciones | `canon_list_adoptions` sobre `adoption_publications` | Adopción Norte (A) y Adopción Sur (B) |
| Perdidos y encontrados | `canon_list_lost_found` sobre `lost_found_alerts` | Histórico abierto, reclamado, en cuidado, encontrado y un resuelto que no se lista |
| Turnos | `service_providers`, `service_offerings`, `bookings` | Consulta de QA10 |
| Tránsito | `foster_care_requests`, `foster_care_applications` | Pedido y postulación |
| Acceso profesional | `leover_verification_requests` | Solo si QA10 no tiene ya un pedido veterinario pendiente |
| Propuestas VitaCora | `vitacora_update_proposals` | Marcador QA17B12 |

`volunteer_offers` no tiene `organization_id` ni un RPC de listado. El voluntariado público de un refugio usa `community_events`, que sí trae la organización. Un acceso general no envía `organizationId` y conserva el listado completo. Desde A solo aparecen filas de A. Desde B solo aparecen filas de B.

Las mascotas son `QA17B12 Mascota Norte` (QA01) y `QA17B12 Mascota Sur` (QA02). El seed no elige una mascota arbitraria de la base.

## Cómo sembrarlo

No está ejecutado. Para revisarlo sin escribir:

```powershell
.\scripts\qa\seed-17b12-staging.ps1
```

Sin `-Apply` no hay escrituras. Con `-Apply` el script solo sigue si el proyecto vinculado es `tobqbddfcyitwgbkthhy`. El SQL aborta si `leover.qa_target` no es ese ref.

## Cómo limpiarlo

Volver a correr el seed borra primero las filas `QA17B12` y las vuelve a insertar. No borra personas, organizaciones ni contraseñas.
