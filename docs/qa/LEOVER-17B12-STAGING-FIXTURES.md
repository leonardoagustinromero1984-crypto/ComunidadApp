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

- Campañas monetarias A y B.
- Necesidades materiales A y B.
- Voluntariado: lugares disponibles, cupo completo, y una convocatoria sin postulantes, repartidas entre A y B.
- Casos públicos LOST (anterior) y FOUND (reciente) del usuario QA07, para el feed histórico.
- Tres prestadores con nombre `QA17B12` (veterinaria, peluquería, paseador).

Adopciones, postulaciones, turnos y accesos VitaCora siguen apoyados en QA14, QA15, QA10, QA11 y QA12. El seed no duplica esas cuentas.

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

El script aborta si el proyecto vinculado no es `tobqbddfcyitwgbkthhy`.

## Cómo limpiarlo

Borrar únicamente filas con el prefijo, en STAGING:

```sql
delete from public.m17_volunteer_opportunities where title like 'QA17B12 %';
delete from public.m17_in_kind_needs where title like 'QA17B12 %';
delete from public.m17_donation_campaigns where title like 'QA17B12 %';
delete from public.service_providers where display_name like 'QA17B12 %';
```

No borra personas, organizaciones ni los casos históricos si ya se usaron en QA físico. Esos casos se identifican por `created_by` de `qa07shelter`.
