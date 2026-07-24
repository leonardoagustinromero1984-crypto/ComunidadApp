# M12 — Veterinarias

**Estado del módulo (Bloque 1):** `EN_CURSO` — auditoría, dominio, contratos y directorio público **local/fake**.
**Sin persistencia remota.** No existe migración `046`.

> Nota de numeración: en el catálogo de producto (D01), **M12** es “Mascotas perdidas y encontradas”. Este documento describe el **módulo técnico M12 — Veterinarias** en la secuencia de implementación Android (post-M11 Refugios), alineado a prestadores (catálogo D01 M22) sin reemplazar el M12 de producto.

## Objetivo del Bloque 1

1. Auditar el estado real del repo respecto de veterinarias / servicios / agenda.
2. Definir dominio inicial sin SQL remoto.
3. Contratos de repositorio, errores tipados y fakes en memoria.
4. Directorio público local con filtros y detalle.
5. Gestión mínima de borradores locales vinculados a org M03.
6. Documentación y pruebas focalizadas.

## Auditoría (resumen)

| Hallazgo | Clasificación |
|----------|----------------|
| `ServiceProfile` / `service_profiles` / bookings legacy | **LEGACY_COMPATIBLE** — no eliminar ni migrar destructivamente |
| Organizaciones / sedes / membresías M03 | **REUTILIZABLE** |
| Verificación M04 | **PARCIAL** — contratos preparados; sin casos reales |
| Referencias M05 (`m05://`, `file_asset:`) | **REUTILIZABLE** via `FosterSecureRefValidator` |
| Permisos org + `AccountType` | **REUTILIZABLE** patrón: AccountType no concede autoridad |
| Navegación Sumate / dashboards | **PARCIAL** — entrada nueva al directorio |
| Agenda / turnos / pagos / historia clínica | **FUERA_DE_ALCANCE** / **AUSENTE** en Bloque 1 |
| Modelos `Veterinary*` previos | **AUSENTE** |

Detalle: `docs/02-arquitectura/M12-auditoria-y-contratos-iniciales.md`.

## Decisiones

- Clínica institucional ↔ organización M03 **ACTIVE**.
- Sede opcional reutiliza branch M03 cuando exista.
- Sin sistema paralelo de usuarios/orgs/permisos.
- Matrícula = dato declarado; no verificación oficial sin M04.
- Contactos públicos opt-in; sin direcciones privadas en listados públicos.
- Sin historia clínica, diagnósticos, precios, turnos reales ni pagos.
- Legacy `service_profiles` intacto.

## Modelos

- `VeterinaryClinicProfile` + `VeterinaryPublicListing`
- `VeterinaryProfessional`
- `VeterinaryService` + `AnimalSpecies`
- `VeterinaryOpeningHours`
- `VeterinaryDirectoryFilter`
- `VeterinaryPermissionCodes` (solo registro de dominio)

## Repositorios (fake)

- `VeterinaryClinicRepository` — `createLocalDraft` / `updateLocalDraft` / managed observe
- `VeterinaryProfessionalRepository`
- `VeterinaryDirectoryRepository` — `observePublicClinics(filter)` / `getPublicClinic`
- Store: `M12VeterinaryMemoryStore` (+ seed demo ficticio)

## Errores tipados

`M12VeterinaryException` / `M12VeterinaryErrorMapper` (`VETERINARY_CLINIC_*`, `VETERINARY_PROFESSIONAL_*`, etc.).

## UI / rutas

| Ruta | Pantalla |
|------|----------|
| `veterinary_directory` | Directorio público + filtros |
| `veterinary_clinic_detail/{clinicId}` | Detalle público |
| `my_veterinary_clinics` | Gestión local |
| `veterinary_clinic_draft[/{clinicId}]` | Borrador local |

Entrada: Sumate → Refugios → **Directorio de veterinarias (M12)** (no rompe M11).

## Integraciones

| Módulo | Bloque 1 |
|--------|----------|
| M03 | org/sede canónica; miembros para manage futuro |
| M04 | sin auto-VERIFIED |
| M05 | refs `m05://` / `file_asset:`; rechazo http(s) |
| M06/M07 | hooks/eventos mock (`VETERINARY_CLINIC_DRAFT_CREATED`, …) |

## Pruebas

- `M12VeterinaryFoundationTest`
- `M12VeterinaryStaticGuardsTest`

## Límites del Bloque 1

- Sin migración 046 / sin SQL remoto.
- Sin agenda/turnos reales.
- Sin historia clínica ni pagos.
- Sin APK / sin suite completa.
- Sin Supabase real en M12.

## Plan exacto del Bloque 2

1. Migración SQL nueva (siguiente número disponible; **no 046 si ya quedó descartada por cierre M11** — usar el siguiente libre tras inventario) para perfiles de clínica, profesionales, servicios y horarios.
2. RLS + RPCs; permisos `veterinary.*` en `organization_permissions`.
3. Repos Supabase + mappers; retirar “solo local” de borradores.
4. Flujo de publicación DRAFT→ACTIVE y solicitud de verificación M04.
5. Ampliación UI de gestión (servicios/horarios/profesionales) sin turnos ni pagos.
