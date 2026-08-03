# RC1 — Deuda histórica migraciones 039–052

**Estado remoto staging:** NO registradas (salto 038 → 053)  
**Acción RC1:** documentar únicamente — **sin aplicar, reparar, borrar ni renumerar**

## Clasificación global

Las migraciones 039–052 cubren objetos de M09–M14 que en staging fueron **introducidos por otro mecanismo** (migraciones 053+ asumen forward-only desde 001–052 local, pero objetos equivalentes parciales pueden existir vía 001–038 legacy). Aplicar 039–052 en staging actual sin reconciliación previa conlleva **riesgo alto** de conflicto DDL.

**Recomendación global segura:** mantener gap documentado; planificar etapa posterior de **reconciliación read-only** (inventario `pg_catalog` vs scripts 039–052) antes de cualquier apply o repair.

## Detalle por migración

| # | Nombre | Módulo | Objetivo principal | Remoto | Clasificación | Riesgo apply | Riesgo no-apply | Recomendación |
|---|--------|--------|-------------------|--------|---------------|--------------|-----------------|---------------|
| 039 | m09_adoption_completion_followup | M09 | entrevistas, docs, acuerdo, follow-up | ✗ | parcialmente representada | Alto | Medio | investigación |
| 040 | m10_foster_homes_core | M10 | hogares tránsito core | ✗ | parcialmente representada | Alto | Medio | investigación |
| 041 | m10_foster_care_management | M10 | gestión placements | ✗ | parcialmente representada | Alto | Medio | investigación |
| 042 | m11_shelter_operations_core | M11 | operaciones refugio | ✗ | parcialmente representada | Alto | Medio | investigación |
| 043 | m11_shelter_campaigns_and_aid | M11 | campañas refugio | ✗ | parcialmente representada | Alto | Medio | investigación |
| 044 | m11_harden_campaign_aid_permissions | M11 | hardening permisos | ✗ | parcialmente representada | Alto | Bajo | investigación |
| 045 | m11_shelter_emergencies_events_reports | M11 | emergencias/eventos | ✗ | parcialmente representada | Alto | Medio | investigación |
| 046 | m12_veterinary_profiles_and_services | M12 | perfiles veterinaria | ✗ | parcialmente representada | Alto | Medio | investigación |
| 047 | m12_veterinary_appointments_and_availability | M12 | turnos vet | ✗ | parcialmente representada | Alto | Medio | investigación |
| 048 | m13_sightings_and_match_candidates | M13 | avistamientos | ✗ | parcialmente representada | Alto | Medio | investigación |
| 049 | m13_match_review_workflow | M13 | workflow matches | ✗ | parcialmente representada | Alto | Medio | investigación |
| 050 | m14_pet_passports_and_credentials | M14 | pasaportes | ✗ | parcialmente representada | Alto | Medio | investigación |
| 051 | m14_revoke_residual_table_privileges | M14 | revocación privilegios | ✗ | obsoleta/pendiente | Medio | Bajo | reconciliación |
| 052 | m14_credential_verification_and_public_access | M14 | verificación credenciales | ✗ | parcialmente representada | Alto | Medio | investigación |

## Objetos creados (resumen)

- **039:** `adoption_interviews`, `adoption_document_requirements`, completion/follow-up tables.
- **040–041:** foster homes, placements, expenses (M10).
- **042–045:** shelter operations, campaigns, emergencies (M11).
- **046–047:** veterinary profiles, appointments (M12).
- **048–049:** sightings, match candidates, review workflow (M13).
- **050–052:** passports, credentials, verification, public access (M14).

## Dependencias actuales

- **053 M16** declara forward-only sobre 001–052 local.
- Validaciones M16–M27 en staging pasaron **sin** 039–052 registradas → objetos necesarios para M16+ existen vía otro camino o son compatibles parcialmente.

## Veredicto

Deuda **documentada**. RC1 **no aplica** 039–052. Próxima etapa: inventario remoto vs local antes de decisión de repair/apply selectivo.
