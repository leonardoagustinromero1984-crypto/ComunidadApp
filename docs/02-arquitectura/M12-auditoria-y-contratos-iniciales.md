# M12 — Auditoría y contratos iniciales (Bloque 1)

**Fecha:** 2026-07-23
**HEAD base:** `ec03064577d6bf936f29411c48af2c93835da0f1`
**Persistencia remota:** migración `046` creada en repo; **no aplicada** en este documento operativo hasta apply autorizado.

## Bloque 2

Ver `docs/02-arquitectura/M12-persistencia-perfiles-servicios.md` y `docs/05-operacion/M12-aplicacion-migracion-046-supabase.md`.


## Estado inicial

- Rama `main` alineada con `origin/main`.
- M11 Refugios: `CERRADO`.
- Migraciones 040–045 aplicadas en Supabase de pruebas (intactas; no modificadas en este bloque).

## Documentos de entrada leídos

| Documento | Existe |
|-----------|--------|
| `docs/01-producto/D01-Modulos-y-Orden.md` | Sí |
| `docs/03-modulos/M03-Organizaciones-y-Equipos.md` | Revisado vía referencias M03 |
| `docs/03-modulos/M04-*` | Parcial / vía patrones |
| `docs/03-modulos/M05-*` / auditoría M05 | Sí (refs media) |
| `docs/03-modulos/M08-perfiles-mascotas.md` | Referenciado |
| `docs/03-modulos/M11-refugios.md` | Sí |
| `docs/03-modulos/M11-cierre-final.md` | Sí |
| `docs/02-arquitectura/M11-matriz-funcional-final.md` | Sí |

## Matriz de auditoría

| Ítem | Clasificación | Notas |
|------|---------------|-------|
| `ServiceProfile` / `service_profiles` / `ServiceBooking` | LEGACY_COMPATIBLE | Categoría `VET`; bookings/pagos legacy — no migrar destructivamente |
| `CommunityListing` / Sumate servicios | LEGACY_COMPATIBLE | Listado comunidad, no operación veterinaria M12 |
| Organizaciones / branches / memberships M03 | REUTILIZABLE | Autoridad canónica |
| `has_org_permission` / org permissions | REUTILIZABLE | Seeds `veterinary.*` diferidos a Bloque 2 |
| Verificación organizaciones/profesionales M04 | PARCIAL | Sin casos VERIFIED automáticos |
| Media M05 + `FosterSecureRefValidator` | REUTILIZABLE | Política m05/file_asset |
| Filtros por zona (texto) | PARCIAL | Zona pública textual; sin GPS/mapas nuevos |
| Contactos / horarios legacy (`scheduleText`) | LEGACY_INCOMPATIBLE | Texto libre; M12 usa `VeterinaryOpeningHours` |
| Appointment / Booking / PaymentStatus | FUERA_DE_ALCANCE | No en Bloque 1 |
| HealthRecord / MedicalRecord / Vaccination clínica | AUSENTE / FUERA_DE_ALCANCE | No implementar |
| `Veterinary*` dominio previo | AUSENTE | Creado en Bloque 1 |
| GPS/mapas dedicados veterinaria | AUSENTE | Fuera de alcance |
| Pagos | FUERA_DE_ALCANCE | |

## Decisiones arquitectónicas

1. Clínica = proyección operativa de org M03 ACTIVE (+ branch opcional).
2. No duplicar usuarios, orgs ni matrices de permisos.
3. `AccountType` / `active_modules` no otorgan `veterinary.*.manage`.
4. Perfil profesional no implica admin de clínica.
5. Matrícula declarada; verificación oficial = M04 futuro.
6. Contacto público opt-in y redacción en proyección.
7. Listados públicos sin dirección privada ni datos médicos.
8. Sin precios, turnos, historia clínica ni diagnósticos.

## Contratos

### Repositorios

```text
VeterinaryClinicRepository
VeterinaryProfessionalRepository
VeterinaryDirectoryRepository
```

Métodos: `observePublicClinics`, `getPublicClinic`, `observeManagedClinics`, `getManagedClinic`, `createLocalDraft`, `updateLocalDraft`, `observeClinicProfessionals|Services|OpeningHours`.

### Errores

```text
VETERINARY_CLINIC_NOT_FOUND | FORBIDDEN | INVALID | INACTIVE | UNVERIFIED
VETERINARY_PROFESSIONAL_NOT_FOUND | INVALID | UNVERIFIED
VETERINARY_SERVICE_NOT_FOUND
VETERINARY_OPENING_HOURS_INVALID
VETERINARY_PUBLIC_CONTACT_DISABLED
VETERINARY_MEDIA_REF_INVALID
VETERINARY_REPOSITORY_FAILURE
```

### Permisos (solo IDs de dominio)

```text
veterinary.profile.read|manage
veterinary.professional.read|manage
veterinary.service.read|manage
```

Sin seeds SQL en Bloque 1.

### Hooks M06/M07 (mock)

```text
VETERINARY_CLINIC_DRAFT_CREATED
VETERINARY_CLINIC_PROFILE_UPDATED
VETERINARY_PROFESSIONAL_LINKED
VETERINARY_SERVICE_UPDATED
```

## Fakes

`M12VeterinaryMemoryStore` con clínicas, profesionales, servicios, horarios, org managers/viewers, audit/m06, `forceFailure`, seed demo **ficticio**.

## UI

Directorio (LOADING/CONTENT/EMPTY/ERROR), detalle público, mis clínicas, borrador local con aviso de persistencia remota en Bloque 2.

## Pruebas y compilación

- Focalizadas: `M12VeterinaryFoundationTest`, `M12VeterinaryStaticGuardsTest`, más regresión `M11FinalClosureGuardsTest` y mapping M05 existente si aplica.
- Una sola `compileLocalDebugKotlin`.

## Límites

- No migración 046.
- No SQL remoto.
- No agenda/turnos reales.
- No historia clínica.
- No APK.
- No otro módulo.

## Plan Bloque 2

Persistencia remota (migración nueva), RLS/RPC, publicación y verificación, gestión de servicios/horarios/profesionales; **sin** turnos ni pagos.
