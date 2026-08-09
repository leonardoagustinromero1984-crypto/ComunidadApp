# LeoVer — Inventario Real de Módulos Mxx v1.0

**Versión:** 1.0  
**Fecha:** 9 de agosto de 2026  
**Tipo:** Auditoría documental/técnica **no destructiva**  
**Repositorio:** `C:\Users\Supervielle\StudioProjects\ComunidadApp`  
**Objetivo:** Inventariar los identificadores M00–Mxx **realmente utilizados** en código, migraciones, paquetes, rutas, tests, ADR y cierres — como base para corregir D01 v1.2 **sin renumerar** lo existente.

---

## 0. Metodología y reglas aplicadas

### Fuentes consultadas (orden de precedencia para este inventario)

| Prioridad | Fuente | Ubicación real en repo |
|-----------|--------|------------------------|
| 1 | Documento Maestro v1.1 | `docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md` |
| 2 | Matriz de vigencia v1.0 | **No encontrada** en ruta indicada (`docs/00-startup/LeoVer-Auditoria-Documental-y-Matriz-de-Vigencia-v1.0.md`). Se usaron `docs/01-producto/D01-Modulos-y-Orden.md` (notas técnicas), ADR-013/014/015 y `docs/06-release/RC1-matriz-modulos.md`. |
| 3 | ADR y cierres existentes | `docs/adr/`, `docs/02-arquitectura/ADR-0*.md`, `docs/03-modulos/*-cierre*.md` |
| 4 | Migraciones, código, tests, rutas | `supabase/migrations/`, `app/src/`, `scripts/` |
| 5 | D01 v1.1 | `docs/01-producto/D01-LeoVer-Modulos-y-Orden-v1.1.md` — **solo para detectar colisiones; no es autoridad de numeración** |

### Alcance de búsqueda

- `docs/`, `app/src/`, `supabase/migrations/`, `scripts/`, `.github/`
- Patrones: `Mxx`, `mxx_`, `/mxx/`, migraciones `*_mxx_*`, paquetes `mxx`, ADR, cierres.

### Regla fundamental aplicada

Un identificador Mxx ya utilizado **no se renumera ni se reutiliza** para otro dominio. Este inventario describe la realidad del repositorio, no el mapa ideal de D01 v1.1.

---

## 1. Tabla principal — Inventario M00–M29

| ID | Dominio real (repo) | Estado | Código | Migraciones | Documentos | Dependencias | Colisiones D01 v1.1 | Capacidad Maestro v1.1 | Decisión recomendada |
|----|---------------------|--------|--------|-------------|------------|--------------|---------------------|------------------------|----------------------|
| **M00** | Fundación técnica, CI, configuración Android, gobierno docs | IMPLEMENTADO | App base, Gradle, `.github/`, scripts CI | 001–012 (fundación + legacy fases) | `M00-Fundacion-Tecnica.md`, `M00-cierre-final.md` | — | Ninguna | Infraestructura transversal | **Conservar M00** |
| **M01** | Identidad y autenticación (Supabase Auth) | IMPLEMENTADO | `AuthRepository`, `LoginViewModel`, pantallas auth | 004, 014–016 | `M01-Identidad-y-Autenticacion.md`, `M01-cierre-final.md` | M00 | Ninguna | §7.1 Identidad | **Conservar M01** |
| **M02** | Usuarios, perfil, roles, permisos, onboarding | IMPLEMENTADO | `UserProfile*`, `ProfileOnboarding*`, RLS perfil | 015–018 | `M02-Usuarios-Roles-y-Permisos.md`, `M02-cierre-final.md` | M01 | Ninguna | §7.1–7.2 | **Conservar M02** |
| **M03** | Organizaciones, equipos, sucursales, invitaciones | IMPLEMENTADO | `Organization*`, rutas `organization_*` | 019–021 | `M03-Organizaciones-y-Equipos.md`, `M03-cierre-final.md` | M02 | Ninguna | §7.8 Organizaciones | **Conservar M03** |
| **M04** | Administración, moderación, soporte, verificación org | IMPLEMENTADO | `PlatformAdministration*`, `Moderation*`, colas verificación | 022–023 | `M04-Administracion-Moderacion-y-Soporte.md`, `M04-cierre-final.md` | M01, M02 | Ninguna | Moderación / confianza (§9) | **Conservar M04** |
| **M05** | Archivos, medios, Storage Supabase | IMPLEMENTADO | `FileAsset*`, `SupabaseFile*`, `StoragePaths` | 002, 017, 024–025 | `M05-Archivos-Media-y-Documentos.md`, `M05-cierre-final.md` | M00, M01, M02 | Ninguna | Transversal (medios) | **Conservar M05** |
| **M06** | Notificaciones, push FCM, preferencias, deep links | IMPLEMENTADO | `Notification*`, `LeoverFirebaseMessagingService` | 013, 026–028 | `M06-Notificaciones.md`, `M06-cierre-final.md` | M01, M02, M07 | Ninguna | §7.18 Comunicaciones (notif.) | **Conservar M06** |
| **M07** | Auditoría, observabilidad, métricas, health | IMPLEMENTADO | `OperationalObservability*`, pantallas `observability_*` | 029–034 | `M07-Auditoria-Analitica-y-Observabilidad.md`, `M07-cierre-final.md` | M00 | Ninguna | Transversal (observabilidad) | **Conservar M07** |
| **M08** | Mascotas, responsables, custodia, transferencias | IMPLEMENTADO | `data/remote/supabase/m08/`, `LegacyPetRepositoryAdapter`, rutas `pet_*` | 003, 035–036 | `M08-mascotas-y-responsables.md`, etapas 4–7 | M02, M05, M07 | Ninguna | §7.3 Perfil mascota (autoridad) | **Conservar M08** |
| **M09** | **Adopciones y postulaciones** (flujo completo) | IMPLEMENTADO | `data/remote/supabase/m09/`, `Adoption*Screen`, rutas `adoption_*` | 037–039 | `M09-adopciones.md`, ADR-014 | M08, M05, M07 | **SÍ:** D01 v1.1 asigna M09 = Pasaporte | §7.6 Adopciones | **Conservar M09 = Adopciones**; Pasaporte queda en M14 |
| **M10** | **Hogares de tránsito** (persistencia `m10_*`, rutas `foster_*`) | IMPLEMENTADO | `data/remote/supabase/m10/`, `Foster*`, `M10Foster*` | 040–041 | `M10-hogares-de-transito.md`, ADR-015 | M08, M05 | **SÍ:** D01 v1.1 asigna M10 = Geoservicios | §7.7 Hogares tránsito (persistencia base) | **Conservar M10 = Tránsito legacy/SQL**; geoservicios → ID nuevo |
| **M11** | **Operación de refugios** (ShelterProfile, campañas, urgencias) | IMPLEMENTADO | `data/remote/supabase/m11/`, rutas `shelter_*` (ops) | 042–045 | `M11-refugios.md`, `M11-cierre-final.md` | M03, M08, M09, M10 | **SÍ:** D01 v1.1 asigna M11 = Web pública | §7.8 Refugios (capa operativa legacy) | **Conservar M11 = Refugios ops**; web → ID nuevo |
| **M12** | **Veterinarias** (directorio, clínicas, turnos) | IMPLEMENTADO | `data/remote/supabase/m12/`, `Veterinary*`, rutas `veterinary_*` | 046–047 | `M12-cierre-final.md`, ADR-013 (contexto) | M03, M05, M08 | **SÍ:** D01 v1.1 asigna M12 = Perdidos/encontrados | §7.11 Veterinarias (clínicas Android) | **Conservar M12 = Veterinarias**; perdidos → legacy + M13 |
| **M13** | **Avistamientos y coincidencias** (extiende Lost/Found) | IMPLEMENTADO | `data/remote/supabase/m13/`, `ui/screens/m13/`, rutas `m13/*` | 048–049 | `M13-avistamientos-y-coincidencias.md`, ADR-013, `M13-cierre-tecnico.md` | Lost/Found legacy, M08, M05 | Ninguna (dominio alineado) | §7.5 Perdidos (capa avistamientos) | **Conservar M13** |
| **M14** | **Pasaporte e identidad verificable** | IMPLEMENTADO | `data/remote/supabase/m14/`, `ui/screens/m14/`, rutas `m14/*` | 050–052 | `M14-pasaporte-identidad-verificable.md`, ADR-014, `M14-cierre-tecnico.md` | M08, M05, M07, M09, M12 | **SÍ:** D01 v1.1 asigna M14 = Adopciones | §7.3 Pasaporte LeoVer | **Conservar M14 = Pasaporte**; adopciones quedan en M09 |
| **M15** | **Hogares de tránsito** (capa funcional reconciliada sobre M10/M08) | IMPLEMENTADO | `data/remote/supabase/m15/`, `ui/screens/m15/`, rutas `m15/*`; adaptadores sobre RPC M10 | — (sin migración propia; **M10 040/041 autoritativas**) | `M15-hogares-de-transito.md`, ADR-015, `M15-cierre-tecnico.md` | M10, M08, M06 | Ninguna semántica (coincide producto) | §7.7 Hogares tránsito (UI/contratos) | **Conservar M15**; documentar dualidad M10↔M15 |
| **M16** | **Refugios — perfiles públicos org** (track producto D01) | IMPLEMENTADO | `data/remote/supabase/m16/`, `domain/m16/`, `ui/screens/m16/`, rutas `m16/*` | 053 | `M16-auditoria-inicial.md`, `M16-cierre-oficial.md` | M03, M04, M08, M09, M11, M15 | Parcial: D01 v1.1 = "Gestión casos"; repo = perfiles refugio org | §7.8 Organizaciones/refugios | **Conservar M16**; ampliar alcance doc., no renumerar |
| **M17** | Donaciones, campañas solidarias, voluntariado | IMPLEMENTADO | `data/remote/supabase/m17/`, `ui/screens/m17/` | 054–057 | `M17-cierre-oficial.md` | M03, M06, M16 | Ninguna | §7.9 Donaciones | **Conservar M17** |
| **M18** | Eventos comunitarios | IMPLEMENTADO | `data/remote/supabase/m18/`, `domain/m18/`, `ui/screens/m18/` | 058–059 | `M18-cierre-oficial.md` | M03, M06, M10 | Ninguna | §7.10 Eventos | **Conservar M18** |
| **M19** | Red social y contenido | IMPLEMENTADO | `data/remote/supabase/m19/`, `ui/screens/m19/` | 060–061 | `M19-cierre-oficial.md` | M02, M05, M04 | Ninguna | §7.4 Red social | **Conservar M19** |
| **M20** | Mensajería y conversaciones | IMPLEMENTADO | `data/remote/supabase/m20/`, `ui/screens/m20/` | 062–063 | `M20-cierre-oficial.md` | M01, M02, M04–M07 | Ninguna | §7.18 Comunicaciones (chat) | **Conservar M20** (+ legacy `chat_*`) |
| **M21** | Reputación, verificaciones, reseñas | IMPLEMENTADO | `data/remote/supabase/m21/`, `ui/screens/m21/` | 064–065 | `M21-cierre-oficial.md` | M02, M03, M04 | Ninguna | §7.19 Reputación | **Conservar M21** |
| **M22** | Prestadores y catálogo de servicios | IMPLEMENTADO | `data/remote/supabase/m22/`, `ui/screens/m22/` | 066–067 | `M22-cierre-oficial.md` | M02, M03, M05, M21 | Ninguna | §7.12–7.13 Prestadores | **Conservar M22** |
| **M23** | Agenda, disponibilidad y reservas | IMPLEMENTADO | `data/remote/supabase/m23/`, `ui/screens/m23/` | 068–069 | `M23-cierre-oficial.md` | M06, M22, M07 | Ninguna | §7.15 Agenda | **Conservar M23** |
| **M24** | Pagos, suscripciones, cobros LeoVer | DOCUMENTADO / POSPUESTO | Sin paquetes `m24`, sin rutas, sin SQL | — | `M24-auditoria-preliminar.md`, D01 v1.0/v1.1 | M03, M04, M07 (futuro) | Ninguna en código (ID reservado) | §7.16 Suscripciones | **Conservar M24 reservado**; no reasignar |
| **M25** | Marketplace, pedidos, promociones (sin pagos) | IMPLEMENTADO | `data/remote/supabase/m25/`, `ui/screens/m25/` | 070–071 | `M25-cierre-oficial.md` | M03, M05, M21 | Ninguna | §7.14 Comercio | **Conservar M25** |
| **M26** | Inteligencia asistida, matching, duplicados | IMPLEMENTADO | `data/remote/supabase/m26/`, `domain/m26/`, `ui/screens/m26/` | 072–074 | `M26-cierre-oficial.md` | M05, M07, M13 | Ninguna | §18 IA / matching | **Conservar M26** |
| **M27** | Integraciones, webhooks, OAuth, API pública | IMPLEMENTADO | `data/remote/supabase/m27/`, `domain/m27/`, `ui/screens/m27/` | 075–077 | `M27-cierre-oficial.md` | M00, M04, M07 | Ninguna | Integraciones / API | **Conservar M27** |
| **M28** | Portal veterinario (propuesto D01 v1.1) | NO_INICIADO | Sin código ni migraciones | — | Solo D01 v1.1 | — | N/A (nuevo en v1.1) | §7.11 Gestión profesional salud | **Reservar M28** para portal vet; no usar para otro dominio |
| **M29** | Brand Studio y publicidad (propuesto D01 v1.1) | NO_INICIADO | Sin código ni migraciones | — | Solo D01 v1.1 | — | N/A (nuevo en v1.1) | §10.2 Brand Studio | **Reservar M29** para Brand Studio |

### 1.1 Dominio sin identificador Mxx dedicado (legacy)

| Capacidad | Evidencia en repo | Relación con Mxx | Estado |
|-----------|-------------------|------------------|--------|
| **Perdidos / encontrados / alertas** | Tablas `lost_found_posts` (005), `lost_found_sightings` (012); `LostFoundRepository`; rutas `lost_found*`, `AlertMapScreen`; sin paquete `m12` para este dominio | Base funcional para **M13**; producto histórico = M12 catálogo v1.0 | IMPLEMENTADO (legacy) + PARCIAL (M13 enriquece) |
| **Geoservicios / mapas / PostGIS** | `AlertMapScreen`, `AlertLocationPrivacy`, `lost_found_map`; PostGIS mencionado en M00 docs; **sin** paquete `m10` geográfico ni RPC `m10_geo_*` | Capacidad Maestro §7.17 **sin ID técnico asignado** | PARCIAL / DOCUMENTADO |
| **Web pública Next.js** | No hay código web en este repo Android | Producto histórico M11 v1.0 | NO_INICIADO en repo |
| **Chat legacy** | Rutas `chat_*`, `CHAT` conviven con **M20** | Duplicación intencional transitoria | LEGACY + M20 |

---

## 2. Detalle por módulo (evidencia mínima)

### M00 — Fundación técnica

| Campo | Valor |
|-------|-------|
| Primera evidencia | `supabase/migrations/001_initial_schema.sql`; estructura Android inicial |
| Documentos | `docs/03-modulos/M00-Fundacion-Tecnica.md`, `docs/02-arquitectura/M00-cierre-final.md` |
| Código | Gradle, manifest, tema, navegación base, CI scripts |
| Rutas | N/A (transversal) |
| RPC/tablas | 001–012 (fundación, fases legacy, auth base) |
| Tests | CI base, guards varios |
| Último cierre | `M00-cierre-final.md` |
| Comparte dominio | — |

### M01 — Identidad y autenticación

| Campo | Valor |
|-------|-------|
| Primera evidencia | Migraciones auth/perfil tempranas; `SupabaseAuthRepository` |
| Migraciones | 004, 014–016 |
| Rutas | `login`, `register`, `forgot_password`, `account_security`, etc. |
| Último cierre | `M01-cierre-final.md` |

### M02 — Usuarios, roles y permisos

| Campo | Valor |
|-------|-------|
| Primera evidencia | `018_platform_roles_permissions.sql` |
| Migraciones | 015–018 |
| Rutas | `profile_onboarding`, `edit_profile`, `user_profile/{userId}` |
| Último cierre | `M02-cierre-final.md` |

### M03 — Organizaciones y equipos

| Campo | Valor |
|-------|-------|
| Migraciones | 019–021 |
| Rutas | `my_organizations`, `manage_organization/*`, `organization_team/*` |
| Último cierre | `M03-cierre-final.md` |

### M04 — Administración y moderación

| Campo | Valor |
|-------|-------|
| Migraciones | 022–023 |
| Rutas | `admin_moderation`, `moderation_*`, `support_*`, `org_verification_*` |
| Último cierre | `M04-cierre-final.md` |

### M05 — Archivos y medios

| Campo | Valor |
|-------|-------|
| Migraciones | 002, 017, 024–025 |
| Prefijos | Storage paths `users/`, `orgs/`, `pets/`; refs `m05://`, `file_asset:` |
| Último cierre | `M05-cierre-final.md` |

### M06 — Notificaciones

| Campo | Valor |
|-------|-------|
| Migraciones | 013, 026–028 |
| Rutas | `notifications`, `notification_preferences` |
| Último cierre | `M06-cierre-final.md` |

### M07 — Auditoría y observabilidad

| Campo | Valor |
|-------|-------|
| Migraciones | 029–034 |
| Rutas | `observability_*` |
| Tests | `M07Stage*Test`, `M07DataProviderWiringTest` |
| Último cierre | `M07-cierre-final.md` |

### M08 — Mascotas y responsables

| Campo | Valor |
|-------|-------|
| Primera evidencia | `003` pets; comentarios en código desde etapas tempranas |
| Migraciones | 003, 035–036 |
| Paquete | `data/remote/supabase/m08/` |
| RPC | `m08_list_accessible_pets`, `m08_create_pet_with_principal`, `m08_*` (20+ funciones) |
| Rutas | `pet_detail/*`, `pet_responsibilities/*`, `pet_transfers/*`, `pet_status_history/*` |
| Tests | `M08Stage*`, `PetDetailSmokeRegressionTest`, `LegacyPetRepositoryAdapterTest` |
| Último cierre | Etapa 7 en `M08-mascotas-y-responsables.md` (smoke integral pendiente) |

### M09 — Adopciones ⚠️ colisión D01 v1.1

| Campo | Valor |
|-------|-------|
| Primera evidencia | `037_m09_adoption_publications.sql`; `AdoptionFormScreen.kt` comenta "M09" |
| Paquete | `data/remote/supabase/m09/` |
| Migraciones | 037–039 |
| RPC | `m09_*` (publicaciones, postulaciones, finalización, follow-up) |
| Rutas | `adoptions`, `adoption_*` (sin prefijo `/m09/`) |
| Tests | `M09MigrationStaticGuardsTest`, `M09Adoption*Test` |
| ADR | ADR-014: producto M14 Adopciones → **M09 técnico** |
| Último cierre | Integración completa documentada en `M09-adopciones.md` |

### M10 — Hogares de tránsito (persistencia) ⚠️ colisión D01 v1.1

| Campo | Valor |
|-------|-------|
| Primera evidencia | `040_m10_foster_homes_core.sql` |
| Paquete | `data/remote/supabase/m10/` |
| Migraciones | 040–041 |
| RPC/tablas | `m10_*`, tablas foster en SQL |
| Rutas | `foster_*` (legacy naming, comentario NavRoutes: "M10 — hogares de tránsito") |
| Tests | `M10FosterHomeCoreTest`, `M10MigrationStaticGuardsTest` |
| Relación M15 | ADR-015: **M10 tablas 040/041 autoritativas**; M15 adapta encima |
| Último cierre | `M10-hogares-de-transito.md` (completo técnicamente) |

### M11 — Refugios (operación) ⚠️ colisión D01 v1.1

| Campo | Valor |
|-------|-------|
| Primera evidencia | `042_m11_shelter_operations_core.sql` |
| Paquete | `data/remote/supabase/m11/` |
| Migraciones | 042–045 |
| Rutas | `shelter_*`, `my_shelters`, `SHELTER_DETAIL` (Sumate legacy) |
| Tests | `M11Shelter*Test`, `M11FinalClosureGuardsTest` |
| Duplicación | Convive con **M16** (perfiles org) y tabla legacy `shelters` (006) |
| Último cierre | `M11-cierre-final.md` |

### M12 — Veterinarias ⚠️ colisión D01 v1.1

| Campo | Valor |
|-------|-------|
| Primera evidencia | `046_m12_veterinary_profiles_and_services.sql` |
| Paquete | `data/remote/supabase/m12/` |
| Migraciones | 046–047 |
| Rutas | `veterinary_*` |
| Tests | `M12Veterinary*Test`, `M12FinalClosureGuardsTest` |
| ADR | ADR-013: M12 técnico = Veterinarias; Lost/Found ≠ M12 |
| Último cierre | `M12-cierre-final.md` (smoke externo pendiente histórico) |

### M13 — Avistamientos y coincidencias

| Campo | Valor |
|-------|-------|
| Primera evidencia | `048_m13_sightings_and_match_candidates.sql` |
| Paquete | `data/remote/supabase/m13/`, `ui/screens/m13/` |
| Migraciones | 048–049 |
| Tablas | `lost_found_sighting_details`, `lost_found_match_*` (extiende legacy) |
| Rutas | `m13/sightings`, `m13/cases/*/matches`, etc. |
| Tests | `M13FoundationTest`, `M13Block*`, `M13Migration049StaticGuardsTest` |
| Último cierre | `M13-cierre-tecnico.md` |

### M14 — Pasaporte ⚠️ colisión D01 v1.1

| Campo | Valor |
|-------|-------|
| Primera evidencia | `050_m14_pet_passports_and_credentials.sql` |
| Paquete | `data/remote/supabase/m14/`, `ui/screens/m14/` |
| Migraciones | 050–052 |
| RPC | `m14_create_pet_passport`, `m14_*` (30+ operaciones) |
| Rutas | `m14/passports`, `m14/pets/{petId}/passport`, `m14/public/{code}` |
| Tests | `M14FoundationTest`, `M14PassportCreateFromPetTest`, `M14Migration052StaticGuardsTest` |
| ADR | ADR-014 |
| Último cierre | `M14-cierre-tecnico.md` |

### M15 — Hogares de tránsito (capa reconciliada)

| Campo | Valor |
|-------|-------|
| Primera evidencia | Bloque 1 local post-M10; `SupabaseM15FosterRepositories` |
| Paquete | `data/remote/supabase/m15/`, `ui/screens/m15/` |
| Migraciones | **Ninguna propia** (053+ no creada para M15) |
| Rutas | `m15/hub`, `m15/homes`, `m15/placements/*`, `m15/operations/*` |
| Persistencia | Delegada a **M10** 040/041 vía adaptadores |
| Tests | Guards M15 en `viewmodel/` (bloques 1–4) |
| ADR | ADR-015 |
| Último cierre | `M15-cierre-tecnico.md` (oficial 2026-08-01) |

### M16 — Refugios org (iniciado y cerrado)

| Campo | Valor |
|-------|-------|
| Primera evidencia | Auditoría inicial post-M15 (`M16-auditoria-inicial.md`) |
| Paquete | `data/remote/supabase/m16/`, `domain/m16/`, `ui/screens/m16/` |
| Migraciones | **053** |
| Rutas | `m16/shelters`, `m16/shelters/{id}`, `m16_shelter_verification/*` |
| Tests | `M16ShelterOperationsServiceTest` |
| Comparte dominio | **M11** (operación refugio legacy), **M03** (org) |
| Último cierre | `M16-cierre-oficial.md` (2026-08-01) |

### M17 — Donaciones

| Migraciones | 054–057 | Cierre | `M17-cierre-oficial.md` |

### M18 — Eventos

| Migraciones | 058–059 | Cierre | `M18-cierre-oficial.md` |

### M19 — Red social

| Migraciones | 060–061 | Cierre | `M19-cierre-oficial.md` |

### M20 — Mensajería

| Migraciones | 062–063 | Rutas | `m20/*` + legacy `chat_*` | Cierre | `M20-cierre-oficial.md` |

### M21 — Reputación

| Migraciones | 064–065 | Cierre | `M21-cierre-oficial.md` |

### M22 — Prestadores

| Migraciones | 066–067 | Cierre | `M22-cierre-oficial.md` |

### M23 — Agenda y reservas

| Migraciones | 068–069 | Cierre | `M23-cierre-oficial.md` |

### M24 — Pagos (reservado)

| Evidencia | `M24-auditoria-preliminar.md`; menciones en M17/M25/M27 "sin pagos" |
| Código | **Ausente** |
| Estado | POSPUESTO por producto |

### M25 — Marketplace

| Migraciones | 070–071 | Cierre | `M25-cierre-oficial.md` |

### M26 — IA asistida

| Migraciones | 072–074 | Tablas | `m26_*` | Cierre | `M26-cierre-oficial.md` |

### M27 — Integraciones

| Migraciones | 075–077 | Cierre | `M27-cierre-oficial.md` |

### M28 / M29 — Reservados en D01 v1.1 solamente

Sin paquetes, migraciones, rutas ni tests. Propuestos en D01 v1.1 para Portal Veterinario y Brand Studio.

---

## A. IDs ocupados (M00–M27 en repo)

```
M00, M01, M02, M03, M04, M05, M06, M07, M08,
M09, M10, M11, M12, M13, M14, M15, M16, M17,
M18, M19, M20, M21, M22, M23, M24*, M25, M26, M27
```

\* **M24:** ocupado como identificador **reservado/documentado** (pagos pospuestos), no como implementación.

**Total IDs con uso real o reserva documentada en rango M00–M27:** **28** (todos los IDs del rango histórico).

---

## B. IDs reservados/documentados pero no implementados

| ID | Reserva | Evidencia |
|----|---------|-----------|
| **M24** | Pagos, suscripciones, Mercado Pago | `M24-auditoria-preliminar.md`, D01 v1.0/v1.1, RC1 matriz |
| **M28** | Portal Veterinario (propuesto) | Solo `D01-LeoVer-Modulos-y-Orden-v1.1.md` |
| **M29** | Brand Studio (propuesto) | Solo `D01-LeoVer-Modulos-y-Orden-v1.1.md` |

---

## C. IDs realmente libres

| Rango | Observación |
|-------|-------------|
| **M30 en adelante** | Sin ninguna evidencia en repo |
| **M28, M29** | Libres **en código/SQL/tests**; D01 v1.1 ya les asignó significado propuesto — tratarlos como **reserva producto pendiente**, no reutilizar para otro dominio ya implementado |

**Primer ID técnicamente libre sin implementación ni migración:** **M30** (si se acepta reservar M28/M29 según D01 v1.1).

**Primer ID libre para capacidad sin ID técnico hoy (geoservicios / web):** **M30** o formalizar **M28/M29** según decisión de producto; **no** reutilizar M09–M14.

---

## D. Remapeos históricos documentados (ADR y notas D01 v1.0)

| ADR / nota | Remapeo oficial | Estado |
|------------|-----------------|--------|
| **ADR-014** | Producto M14 Adopciones → **M09 técnico**; Pasaporte producto → **M14 técnico** | APROBADO |
| **ADR-015** | Producto M15 Hogares tránsito = **M15 técnico**; legacy **M10** preservado; SQL 040/041 autoritativo | APROBADO |
| **ADR-013** | Producto M13 Avistamientos = **M13 técnico**; **M12 técnico** = Veterinarias (no renumerar); Lost/Found legacy = base | APROBADO |
| **D01 v1.0 § Nota técnica** | M11 técnico = Refugios (≠ M11 producto Web); M12 técnico = Veterinarias (≠ M12 producto Perdidos) | Vigente como contexto histórico |

**No se encontraron ADR** que autoricen renumerar M09–M16 para alinearlos con D01 v1.1.

---

## E. Duplicaciones intencionales o accidentales

| Caso | Tipo | IDs | Descripción | Acción recomendada |
|------|------|-----|-------------|-------------------|
| Hogares de tránsito | **Intencional** | M10 + M15 | M10 = SQL/RPC; M15 = UI/contratos/adaptadores (ADR-015) | Documentar en D01; no fusionar IDs |
| Refugios | **Intencional transitoria** | M11 + M16 | M11 = ops legacy `shelter_*`; M16 = perfiles org `m16/*` + 053 | Convivencia documentada; no renumerar |
| Adopciones vs Pasaporte | **Remapeo ADR** | M09 + M14 | Dominios distintos; D01 v1.1 los invirtió | Corregir D01 v1.2 |
| Perdidos vs Veterinarias | **Divergencia track/producto** | Legacy + M13 vs M12 | Perdidos sin Mxx; M12 = vet; M13 extiende Lost/Found | Asignar capacidad perdidos a legacy+M13 en D01 |
| Chat | **Legacy** | `chat_*` + M20 | Dos superficies mensajería | Migración progresiva a M20 (fuera de este inventario) |
| Foster rutas | **Legacy naming** | M10 SQL + rutas `foster_*` + M15 `m15/*` | Misma persistencia, dos prefijos UI | Mantener; crosswalk en D01 |
| Tabla `shelters` (006) | **Legacy** | Pre-M11 | Sumate/mock | No reasignar ID |

---

## F. Propuesta de crosswalk para D01 v1.2

Crosswalk: **ID técnico repo (autoridad)** → **capacidad Maestro v1.1** → **nombre D01 v1.2 propuesto**

| ID repo | Dominio real (NO cambiar) | Capacidad Maestro §7 | Entrada D01 v1.2 propuesta |
|---------|---------------------------|----------------------|----------------------------|
| M00 | Fundación técnica | Infraestructura | M00 — Fundación técnica y entornos |
| M01 | Identidad y auth | §7.1 | M01 — Identidad y autenticación |
| M02 | Usuarios y permisos | §7.1–7.2 | M02 — Usuarios, capacidades y permisos |
| M03 | Organizaciones | §7.8 | M03 — Organizaciones y equipos |
| M04 | Admin/moderación | §9 | M04 — Administración, moderación y soporte |
| M05 | Archivos/medios | Transversal | M05 — Archivos, medios y documentos |
| M06 | Notificaciones | §7.18 | M06 — Notificaciones y preferencias |
| M07 | Observabilidad | Transversal | M07 — Auditoría, analítica y observabilidad |
| M08 | Mascotas | §7.3 (autoridad) | M08 — Mascotas, responsables y custodia |
| **M09** | **Adopciones** | §7.6 | **M09 — Adopciones y postulaciones** |
| **M10** | **Hogares tránsito (SQL legacy)** | §7.7 (persistencia) | **M10 — Hogares de tránsito (persistencia y RPC)** |
| **M11** | **Refugios operativos** | §7.8 | **M11 — Operación de refugios (legacy Android)** |
| **M12** | **Veterinarias** | §7.11 | **M12 — Veterinarias y directorio clínico** |
| M13 | Avistamientos | §7.5 | M13 — Avistamientos y coincidencias |
| **M14** | **Pasaporte** | §7.3 | **M14 — Pasaporte e identidad verificable** |
| M15 | Hogares tránsito (UI) | §7.7 | M15 — Hogares de tránsito (operación reconciliada) |
| M16 | Refugios org | §7.8 | M16 — Perfiles públicos de refugio y gestión org |
| M17 | Donaciones | §7.9 | M17 — Donaciones y voluntariado |
| M18 | Eventos | §7.10 | M18 — Eventos comunitarios |
| M19 | Red social | §7.4 | M19 — Comunidad y contenido social |
| M20 | Mensajería | §7.18 | M20 — Mensajería y conversaciones |
| M21 | Reputación | §7.19 | M21 — Reputación y verificaciones |
| M22 | Prestadores | §7.12–7.13 | M22 — Prestadores y catálogo de servicios |
| M23 | Agenda | §7.15 | M23 — Agenda, disponibilidad y reservas |
| M24 | Pagos (reservado) | §7.16 | M24 — Suscripciones comerciales (POSPUESTO) |
| M25 | Marketplace | §7.14 | M25 — Comercio y catálogo comercial |
| M26 | IA asistida | §18 | M26 — Inteligencia artificial y matching |
| M27 | Integraciones | API/adaptadores | M27 — Integraciones y API pública |
| *Legacy* | Perdidos/encontrados | §7.5 | **Nota D01:** base `lost_found_*` + M13; no reasignar M12 |
| *Sin ID* | Geoservicios | §7.17 | **M30 propuesto** o submódulo futuro; no reutilizar M10 |
| *Sin ID* | Web pública | Ecosistema §6 | **M31 propuesto** o redefinir M28; no reutilizar M11 |
| M28 | Portal vet (propuesto) | §7.11 | M28 — Portal Veterinario (sin iniciar) |
| M29 | Brand Studio (propuesto) | §10.2 | M29 — Brand Studio (sin iniciar) |

---

## G. Riesgos de renumeración

| Si se renumerara… | Riesgo | Magnitud |
|-------------------|--------|----------|
| M09 → Pasaporte | Rompe migraciones 037–039, paquete `m09`, tests, ADR-014 | **CRÍTICO** |
| M10 → Geoservicios | Rompe 040–041, foster SQL, M15 adaptadores, ADR-015 | **CRÍTICO** |
| M11 → Web | Rompe 042–045, paquete `m11`, cierre M11 | **CRÍTICO** |
| M12 → Perdidos | Rompe 046–047, veterinaria completa, ADR-013 | **CRÍTICO** |
| M14 → Adopciones | Duplicaría M09; rompe 050–052, pasaporte, ADR-014 | **CRÍTICO** |
| M15 → otro dominio | Rompe cierre oficial, rutas `m15/*`, reconciliación M10 | **ALTO** |
| M16 → otro dominio | Rompe 053 staging aplicada, rutas `m16/*` | **ALTO** |
| Reutilizar M24 | Confundiría reserva de pagos documentada | **MEDIO** |
| Ignorar dual M10/M15 | Deuda documental, no técnica inmediata | **BAJO** |

**Conclusión:** La corrección de D01 debe ser **documental (crosswalk)**, no renumeración de código.

---

## H. Lista exacta de cambios que debería tener D01 v1.2

1. **Revertir/asignar M09** a **Adopciones y postulaciones** (como en repo y ADR-014), no Pasaporte.
2. **Revertir/asignar M14** a **Pasaporte e identidad verificable** (como en repo y ADR-014), no Adopciones.
3. **Revertir/asignar M10** a **Hogares de tránsito — persistencia (040/041)** o renombrar explícitamente como subcapa de M15; **no** Geoservicios.
4. **Revertir/asignar M11** a **Operación de refugios Android (042–045)**; reservar **nuevo ID** (p. ej. M31) para Web pública Next.js.
5. **Revertir/asignar M12** a **Veterinarias (046–047)**; documentar **Perdidos/encontrados** como legacy `lost_found_*` + **M13**, no M12.
6. **Documentar relación M10 ↔ M15** con referencia a ADR-015 (persistencia M10, operación M15).
7. **Documentar relación M11 ↔ M16** (ops legacy vs perfiles org 053).
8. **Incorporar ADR-013, ADR-014, ADR-015** en sección de precedencia de D01.
9. **M24:** mantener POSPUESTO; no reasignar ID.
10. **M28/M29:** mantener propuesta Maestro v1.1; marcar NO_INICIADO en repo.
11. **Agregar nota explícita** para capacidad **§7.17 Geoservicios** sin ID técnico: proponer **M30+** nuevo, sin tocar M10.
12. **Agregar nota** para **Perdidos/encontrados** sin prefijo Mxx: enlace a tablas 005/012 y extensión M13.
13. **Eliminar dependencias incorrectas** en D01 v1.1 (p. ej. M09 Pasaporte en R1, M10 geografía en R2 si contradice repo).
14. **Actualizar diagrama de dependencias** §7 de D01 según crosswalk §F.
15. **Marcar D01 v1.1** como **SUPERSEDIDO por v1.2** una vez aprobado; no usar v1.1 para resolver numeración.

---

## 3. Casos especiales investigados

| # | Pregunta | Hallazgo |
|---|----------|----------|
| 1 | M09 vs Pasaporte/Adopciones | **M09 = Adopciones** en repo; Pasaporte = **M14** (ADR-014) |
| 2 | M10 vs Geoservicios/Tránsito | **M10 = Hogares tránsito SQL**; geoservicios **sin ID**; mapas en Lost/Found legacy |
| 3 | M11 vs Web/Refugios | **M11 = Refugios ops**; Web **no iniciada** en repo |
| 4 | M12 vs Perdidos/Veterinarias | **M12 = Veterinarias**; Perdidos = legacy + **M13** |
| 5 | M14 vs Adopciones/Pasaporte | **M14 = Pasaporte**; Adopciones = **M09** |
| 6 | M10 ↔ M15 | Dualidad **intencional**; M10 persistencia, M15 capa funcional (ADR-015) |
| 7 | ¿M16 iniciado? | **Sí** — migración 053, paquetes, cierre oficial 2026-08-01 |
| 8 | M16+ con uso real | M17–M23, M25–M27 implementados; **M24** reservado; **M28/M29** solo D01 v1.1 |
| 9 | Primeros IDs libres | **M30+** en repo; M28/M29 reserva producto sin código |
| 10 | Remapeos ADR | **ADR-013, ADR-014, ADR-015** — ver sección D |

---

## 4. Mapa migraciones ↔ módulos (001–077)

| Migraciones | Módulo |
|-------------|--------|
| 001–012 | M00 (+ legacy fases: posts, lost_found, shelters, etc.) |
| 013 | M06 |
| 014–018 | M01, M02 |
| 019–021 | M03 |
| 022–023 | M04 |
| 024–025 | M05 |
| 026–028 | M06 |
| 029–034 | M07 |
| 003, 035–036 | M08 |
| 037–039 | M09 |
| 040–041 | M10 |
| 042–045 | M11 |
| 046–047 | M12 |
| 048–049 | M13 |
| 050–052 | M14 |
| — | M15 (sin SQL propio) |
| 053 | M16 |
| 054–057 | M17 |
| 058–059 | M18 |
| 060–061 | M19 |
| 062–063 | M20 |
| 064–065 | M21 |
| 066–067 | M22 |
| 068–069 | M23 |
| — | M24 |
| 070–071 | M25 |
| 072–074 | M26 |
| 075–077 | M27 |

**Gap staging documentado:** 039–052 locales existentes; ver `RC1-deuda-migraciones-039-052.md`.

---

## 5. Referencias clave

- `docs/06-release/RC1-matriz-modulos.md`
- `docs/06-release/RC1-matriz-integraciones-M00-M27.md`
- `docs/01-producto/D01-Modulos-y-Orden.md` (notas técnicas y remapeos)
- `docs/02-arquitectura/ADR-013-M13-track-tecnico-avistamientos-coincidencias.md`
- `docs/02-arquitectura/ADR-014-M14-remapeo-pasaporte.md`
- `docs/02-arquitectura/ADR-015-M15-hogares-de-transito.md`
- `app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt`

---

## 6. Control de versión de este documento

| Campo | Valor |
|-------|-------|
| Versión | 1.0 |
| Fecha | 2026-08-09 |
| Alcance | Inventario read-only |
| Próximo paso | Redactar **D01 v1.2** usando crosswalk §F y cambios §H |
| Código modificado | **NO** |
| SQL modificado | **NO** |
