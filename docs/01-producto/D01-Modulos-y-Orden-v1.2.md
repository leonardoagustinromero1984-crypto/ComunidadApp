# LeoVer — D01 Mapa de Módulos y Orden de Desarrollo

**Versión:** 1.2  
**Fecha:** 9 de agosto de 2026  
**Fuente superior:** LeoVer — Documento Maestro Integral de la Startup v1.1  
**Estado:** Guía oficial de secuencia, dependencias y numeración Mxx  
**Ruta de repositorio:** `/docs/01-producto/D01-Modulos-y-Orden-v1.2.md`

---

## 1. Propósito

Este documento traduce la estrategia del **Documento Maestro v1.1** a módulos técnicos (Mxx), releases (R0–R8), dependencias y superficies — **alineado con la numeración real del repositorio**.

D01 v1.2 corrige las colisiones documentales de D01 v1.1 (M09–M14, M10 geográfico, M11 web) **sin renumerar código, SQL, rutas, paquetes ni migraciones**.

**Actualizar D01 no significa reimplementar módulos ya construidos.** Primero se audita lo existente, se conserva lo válido y solo se corrigen incompatibilidades documentales reales.

---

## 2. Regla de autoridad documental

Orden de precedencia para planificación y resolución de conflictos:

| Prioridad | Fuente |
|-----------|--------|
| 1 | Documento Maestro v1.1 (`docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md`) |
| 2 | ADR aprobados (`docs/adr/`, `docs/02-arquitectura/ADR-0*.md`) |
| 3 | **D01 v1.2** (este documento) |
| 4 | Inventario real Mxx v1.0 (`docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md`) |
| 4b | Auditoría documental y matriz de vigencia v1.0 (`docs/00-startup/LeoVer-Auditoria-Documental-y-Matriz-de-Vigencia-v1.0.md`) — hallazgo D01 v1.1 resuelto por este D01 v1.2 |
| 5 | Especificación vigente del módulo y documentos de cierre |
| 6 | Código, migraciones y pruebas (corroboración) |
| 7 | D01 v1.0, D01 v1.1 y documentación histórica — **solo trazabilidad; no autoridad de numeración M09–M16** |

Si una especificación antigua contradice al Maestro v1.1 **y** al inventario real de Mxx, prevalece el **significado técnico ya implementado** salvo decisión formal nueva registrada en ADR.

---

## 3. Principio de estabilidad Mxx

**La numeración Mxx utilizada por el desarrollo es inmutable.**

Un identificador Mxx ya usado en código, SQL, ADR, migraciones, tests, rutas, paquetes o cierres **conserva ese significado**. D01 se adapta al repositorio; el repositorio **no** se adapta a un mapa documental incorrecto.

**Prohibido:**

- Renumerar módulos implementados para “ordenar” el roadmap.
- Reutilizar un Mxx ocupado para otro dominio.
- Asignar retroactivamente un Mxx histórico a Perdidos/Encontrados o Geoservicios.

**Permitido:**

- Documentar relaciones entre IDs del mismo dominio (M10↔M15, M11↔M16).
- Reservar IDs libres (M28, M29, M30+) mediante ADR.
- Representar capacidades transversales sin Mxx exclusivo.

---

## 4. Tabla maestra M00–M29

| ID | Nombre oficial D01 v1.2 | Release principal | Migraciones (repo) |
|----|-------------------------|-------------------|---------------------|
| M00 | Fundación técnica y entornos | R0 | 001–012 |
| M01 | Identidad y autenticación | R1 | 004, 014–016 |
| M02 | Usuarios, capacidades y permisos | R1 | 015–018 |
| M03 | Organizaciones y equipos | R3 | 019–021 |
| M04 | Administración, moderación, soporte y verificación | R0 (transversal) | 022–023 |
| M05 | Archivos, medios y documentos | R1 (transversal) | 002, 017, 024–025 |
| M06 | Notificaciones y preferencias | R2 (transversal) | 013, 026–028 |
| M07 | Auditoría, analítica y observabilidad | R0 (transversal) | 029–034 |
| M08 | Mascotas, responsables y custodia | R1 | 003, 035–036 |
| **M09** | **Adopciones y postulaciones** | R3 | 037–039 |
| **M10** | **Hogares de tránsito — persistencia y RPC** | R3 | 040–041 |
| **M11** | **Operación de refugios (Android)** | R3 | 042–045 |
| **M12** | **Veterinarias y directorio clínico** | R5 | 046–047 |
| **M13** | **Avistamientos y coincidencias** | R2 | 048–049 |
| **M14** | **Pasaporte e identidad verificable** | R1 | 050–052 |
| **M15** | **Hogares de tránsito — operación reconciliada** | R3 | — (usa M10) |
| **M16** | **Refugios — perfiles org y gestión pública** | R3 | 053 |
| M17 | Donaciones, necesidades y voluntariado | R4 | 054–057 |
| M18 | Eventos y campañas comunitarias | R4 | 058–059 |
| M19 | Comunidad y contenido social | R4 | 060–061 |
| M20 | Mensajería, conversaciones y contexto | R4 | 062–063 |
| M21 | Reputación, verificaciones y reseñas | R4–R5 | 064–065 |
| M22 | Prestadores y catálogo de servicios | R5 | 066–067 |
| M23 | Agenda, disponibilidad y reservas | R5 | 068–069 |
| M24 | Pagos integrados y conciliación marketplace | **RESERVADO / POSPUESTO** | — |
| M25 | Comercio y catálogo comercial (sin checkout) | R6 | 070–071 |
| M26 | Inteligencia artificial y matching | R2 (slice) / R7 | 072–074 |
| M27 | Integraciones y API/adaptadores | R8 | 075–077 |
| M28 | Portal Veterinario y gestión profesional de salud | R5 | — |
| M29 | Brand Studio y publicidad | R5B | — |
| M30+ | **IDs libres** | Futuro | — |

---

## 5. Estado real de cada módulo

| ID | Estado | Evidencia principal | Último cierre conocido |
|----|--------|---------------------|------------------------|
| M00 | IMPLEMENTADO | CI, Gradle, migraciones base | `M00-cierre-final.md` |
| M01 | IMPLEMENTADO | Auth Supabase, pantallas login | `M01-cierre-final.md` |
| M02 | IMPLEMENTADO | Perfil, roles, onboarding RC1.1 | `M02-cierre-final.md` |
| M03 | IMPLEMENTADO | Organizaciones, equipos, sucursales | `M03-cierre-final.md` |
| M04 | IMPLEMENTADO | Moderación, soporte, verificación | `M04-cierre-final.md` |
| M05 | IMPLEMENTADO | Storage, file assets | `M05-cierre-final.md` |
| M06 | IMPLEMENTADO | FCM, preferencias, deep links | `M06-cierre-final.md` |
| M07 | IMPLEMENTADO | Observabilidad, auditoría | `M07-cierre-final.md` |
| M08 | IMPLEMENTADO | `m08_*` RPC, responsables, custodia | Etapa 7 `M08-mascotas-y-responsables.md` |
| M09 | IMPLEMENTADO | `m09_*`, flujo adopción completo | `M09-adopciones.md`, ADR-014 |
| M10 | IMPLEMENTADO | `m10_*`, tablas foster 040/041 | `M10-hogares-de-transito.md`, ADR-015 |
| M11 | IMPLEMENTADO | `m11_*`, ops refugio `shelter_*` | `M11-cierre-final.md` |
| M12 | IMPLEMENTADO | `m12_*`, directorio veterinario | `M12-cierre-final.md`, ADR-013 |
| M13 | IMPLEMENTADO | `m13/*`, extiende lost_found | `M13-cierre-tecnico.md`, ADR-013 |
| M14 | IMPLEMENTADO | `m14/*`, pasaporte y credenciales | `M14-cierre-tecnico.md`, ADR-014 |
| M15 | IMPLEMENTADO | `m15/*`, adaptadores sobre M10 | `M15-cierre-tecnico.md`, ADR-015 |
| M16 | IMPLEMENTADO | `m16/*`, migración 053 | `M16-cierre-oficial.md` |
| M17 | IMPLEMENTADO | `m17/*`, 054–057 | `M17-cierre-oficial.md` |
| M18 | IMPLEMENTADO | `m18/*`, 058–059 | `M18-cierre-oficial.md` |
| M19 | IMPLEMENTADO | `m19/*`, 060–061 | `M19-cierre-oficial.md` |
| M20 | IMPLEMENTADO | `m20/*` + legacy `chat_*` | `M20-cierre-oficial.md` |
| M21 | IMPLEMENTADO | `m21/*`, 064–065 | `M21-cierre-oficial.md` |
| M22 | IMPLEMENTADO | `m22/*`, 066–067 | `M22-cierre-oficial.md` |
| M23 | IMPLEMENTADO | `m23/*`, 068–069 | `M23-cierre-oficial.md` |
| M24 | **RESERVADO / POSPUESTO** | Sin código; `M24-auditoria-preliminar.md` | Preauditoría 2026-08-02 |
| M25 | IMPLEMENTADO | `m25/*`, 070–071, sin pagos | `M25-cierre-oficial.md` |
| M26 | IMPLEMENTADO | `m26/*`, 072–074 | `M26-cierre-oficial.md` |
| M27 | IMPLEMENTADO | `m27/*`, 075–077 | `M27-cierre-oficial.md` |
| M28 | IMPLEMENTADO (PILOT-MINIMUM) | `m28/*`, 080, Android responsable; web NO_APLICA | `M28-cierre-implementacion.md` |
| M29 | NO_INICIADO | Reserva estratégica Maestro/D01 | — |

---

## 6. Dependencias

### 6.1 Diagrama de camino crítico

```text
M00 + M07 + M04(base)
        ↓
M01 → M02 → M05
        ↓
       M08 → M14 (Pasaporte)
        ↓
M06 ─────────────────────────────┐
        ↓                        │
  [Perdidos/Encontrados legacy]   │
        ↓                        │
       M13 (avistamientos)       │
        ↓                        │
       M26 (matching slice) ←────┘
        ↓
M03 → M09 (adopciones)
        ↓
M10 (tránsito SQL) → M15 (tránsito ops)
        ↓
M11 (refugios ops) → M16 (refugios org)
        ↓
M17 / M18 / M19 / M20 / M21
        ↓
M12 (vet directorio) → M22 → M23 → M28 (portal vet)
        ↓
M24 (pospuesto) · M25 (catálogo) · M29 (Brand Studio)
        ↓
M27 (integraciones)
```

### 6.2 Dependencias transversales

| Módulo | Rol transversal |
|--------|-----------------|
| M04 | Reportes, verificación, soporte en nuevos dominios |
| M05 | Storage único; ningún dominio crea bucket paralelo |
| M06 | Notificaciones centralizadas; dominios emiten intenciones |
| M07 | Auditoría, métricas, correlación |
| M08 | Autoridad de mascota, responsables y custodia |
| M21 | Reputación y verificaciones sin confundir con popularidad |

### 6.3 Dependencias especiales documentadas

| Relación | Regla |
|----------|-------|
| **M10 → M15** | M10 es persistencia autoritativa; M15 consume M10/M08 (ADR-015) |
| **M11 → M16** | M11 ops legacy; M16 perfiles org públicos sobre M03 (053) |
| **lost_found → M13** | M13 enriquece legacy; no reemplaza `lost_found_posts` |
| **M09 ↔ M14** | Adopciones (M09) puede emitir credenciales; Pasaporte (M14) no duplica adopción |
| **M12 ↔ M28** | M12 = directorio/clínica Android; M28 = portal profesional futuro |

---

## 7. Crosswalk — Capacidad Maestro v1.1 ↔ Mxx real

| Capacidad Maestro §7 | Mxx real (autoridad repo) | Notas |
|----------------------|---------------------------|-------|
| §7.1 Identidad y cuentas | M01, M02 | — |
| §7.2 Perfil personal y red | M02, M21 | — |
| §7.3 Perfil mascota y Pasaporte | **M08** (autoridad), **M14** (Pasaporte) | **M09 ≠ Pasaporte** |
| §7.4 Red social | M19 | — |
| §7.5 Perdidos y encontrados | **Legacy `lost_found_*` + M13** | Sin Mxx único histórico |
| §7.6 Adopciones | **M09** | ADR-014 |
| §7.7 Hogares de tránsito | **M10** (SQL) + **M15** (ops) | ADR-015; una sola fuente SQL |
| §7.8 Organizaciones y refugios | M03, **M11**, **M16** | M11 ops; M16 perfiles org |
| §7.9 Donaciones | M17 | Sin comisión LeoVer |
| §7.10 Eventos | M18 | — |
| §7.11 Veterinarias / salud | **M12** (Android), **M28** (portal futuro) | M12 ≠ perdidos |
| §7.12–7.13 Prestadores | M22, M23 | — |
| §7.14 Tiendas | M25 | Sin checkout V1 |
| §7.15 Agenda | M23 | — |
| §7.16 Suscripciones LeoVer | M24 reservado + Mercado Pago Suscripciones (comercial) | ≠ marketplace transaccional |
| §7.17 Búsqueda y geoservicios | **Transversal** (PostGIS + Google Maps) | **M10 ≠ geografía** |
| §7.18 Comunicaciones | M06, M20 | — |
| §7.19 Reputación | M21 | — |
| §10.2 Brand Studio | **M29** (reservado) | — |
| §18 IA | M26 + contratos desacoplados | — |

---

## 8. Relaciones y duplicaciones históricas

### 8.1 M10 ↔ M15 — Hogares de tránsito (un solo dominio, dos capas)

| Capa | ID | Responsabilidad | Fuente de verdad |
|------|-----|-----------------|------------------|
| Persistencia | **M10** | Tablas y RPC `m10_*`; migraciones **040–041**; rutas legacy `foster_*` | **SQL autoritativo** |
| Operación | **M15** | UI `m15/*`, contratos, métricas, reconciliación con M08 custodia | Adaptadores sobre M10 |

**Regla:** No crear segunda persistencia de tránsito. Evoluciones SQL futuras extienden M10 (o migración aprobada explícita), no un Mxx alternativo.

**Referencia:** ADR-015, `M15-cierre-tecnico.md`.

### 8.2 M11 ↔ M16 — Refugios (convivencia documentada)

| Capa | ID | Responsabilidad |
|------|-----|-----------------|
| Operación inicial | **M11** | Perfiles operativos, mascotas bajo refugio, campañas, urgencias, reportes (`042–045`, `shelter_*`) |
| Organización / público | **M16** | Perfiles públicos org, acceso sanitizado, verificación refugio (`053`, `m16/*`) |

**Regla:** No fusionar IDs. M16 depende de M03 (organización) y referencia M11 sin duplicar operación legacy. Tabla `shelters` (006) permanece legacy Sumate.

### 8.3 lost_found ↔ M13 — Perdidos y encontrados

| Componente | Identificador | Contenido |
|------------|---------------|-----------|
| Base legacy | Sin Mxx dedicado | `lost_found_posts` (005), `lost_found_sightings` (012), `LostFoundRepository`, rutas `lost_found*` |
| Enriquecimiento | **M13** | Avistamientos, candidatos, revisión humana (`048–049`, `m13/*`) |
| Matching visual | **M26** | Embeddings + confirmación humana |

**Regla:** No renumerar retroactivamente a M12 u otro ID ocupado. Un módulo unificador futuro requiere **M30+** y ADR.

---

## 9. Capacidades transversales sin Mxx exclusivo

### 9.1 Perdidos / encontrados / red de respuesta

- **Estado:** IMPLEMENTADO (legacy) + PARCIAL (M13, M26).
- **Componentes actuales:** publicación de alertas, mapa (`AlertMapScreen`), privacidad de ubicación (`AlertLocationPrivacy`), avistamientos M13, matching M26.
- **Maestro:** §7.5.
- **Futuro:** si se requiere ID dedicado → **M30+** mediante ADR.

### 9.2 Geoservicios, mapas y proximidad

- **Estado:** PARCIAL / distribuido.
- **Infraestructura:** PostGIS (motor interno), Google Maps Platform (adaptadores), pgvector donde aplique.
- **Implementación actual:** mapas en flujos de alertas y ubicación protegida; **no existe** paquete `m10` geográfico ni RPC `m10_geo_*`.
- **Maestro:** §7.17.
- **Regla:** **M10 = tránsito**, no geografía. No asignar M10 a geoservicios. No crear Mxx solo para corregir error documental; evolucionar como capacidad transversal o reservar M30+ si se modulariza.

### 9.3 Web pública

- **Stack:** Next.js, React, TypeScript, Cloudflare Workers, OpenNext, Supabase.
- **Estado en repo Android:** NO_INICIADO (proyecto web separado).
- **Regla:** **M11 = refugios Android**, no web. Web es **superficie transversal** que consumirá rutas/API de M08, M09, M13, M14, M16, M17, M18, etc.
- **Futuro ID modular web (opcional):** M30+ mediante ADR; no reutilizar M11.

### 9.4 Chat legacy

- Rutas `chat_*` conviven con **M20**. Migración progresiva a M20; no renumerar.

---

## 10. Superficies

| Código | Superficie | Stack | Módulos principales |
|--------|------------|-------|---------------------|
| AND | Android | Kotlin, Jetpack Compose, MVVM, Supabase | M00–M27 implementados |
| IOS | iOS piloto | Swift, SwiftUI, **mismo Supabase**, mismos contratos dominio/backend | Paridad crítica — ver §10.1 |
| WEB | Web pública | Next.js, React, TypeScript, Cloudflare Workers, OpenNext | Transversal; expone dominios según madurez |
| ORG | Portal organizaciones | Web + permisos M03 | M03, M11, M16, M09, M15, M17 |
| VET | Portal veterinario | Web (futuro **M28**) | M12, M28, M14 (propuestas Pasaporte) |
| PRO | Portal profesional | Web | M22, M23, M21 |
| BRAND | Brand Studio | Web (**M29**) | M29, M19 placements |
| ADMIN | Consola administrativa | Web + Android interno | M04, M07 |
| DATA | Backend | Supabase Auth, PostgreSQL, RLS, RPC, Storage, PostGIS, pgvector | Todos los Mxx con SQL |

### 10.1 iOS — obligatoriedad piloto público

iOS es **obligatorio** para el piloto público en flujos críticos. No se exige paridad total inicial con Android.

**Paridad crítica mínima:**

- cuenta / login;
- onboarding;
- mascota;
- Pasaporte básico (**M14**);
- perdido / encontrado (legacy + **M13**);
- adopción (**M09**);
- tránsito (**M10/M15**);
- organizaciones / casos (**M03**, **M11**, **M16**);
- comunidad básica (**M19**);
- notificaciones esenciales (**M06**);
- mapas / localización (transversal);
- privacidad / permisos.

Brand Studio, administración avanzada y gestión veterinaria completa pueden permanecer web-first si el recorrido público no queda bloqueado.

---

## 11. Roadmap R0–R8

> **Nota:** El número de release **no** equivale al número Mxx. Cada fila agrupa módulos **reales** por valor de salida estratégico.

| Release | Módulos / cortes principales | Resultado de salida |
|---------|------------------------------|---------------------|
| **R0 — Fundación** | M00, M04 base, M07 | Entornos, CI, administración base, observabilidad |
| **R1 — Identidad** | M01, M02, M05, M08, **M14** | Cuenta + mascota + **Pasaporte** utilizables |
| **R2 — Rescate y superficies públicas** | M06, **M13**, M26 (slice), geoservicios transversales, web progresiva | Pérdidas/hallazgos, avistamientos, respuesta local, matching asistido |
| **R3 — Adopción / tránsito / organizaciones** | M03, **M09**, **M10**, **M15**, **M11**, **M16** | Adopción, tránsito, operación institucional |
| **R4 — Comunidad y colaboración** | M17, M18, M19, M20, M21 | Donaciones, eventos, contenido, mensajería, reputación |
| **R5 — Servicios confiables y comercial** | **M12**, M22, M23, **M28**, suscripciones Mercado Pago | Veterinarias, prestadores, agenda, portal vet, planes LeoVer |
| **R5B — Brand Studio** | **M29** + IA creativa M26 | Publicidad asistida, distribución incluida, analítica |
| **R6 — Comercio futuro** | M25 | Catálogo comercial ampliado; transaccional solo si se aprueba |
| **R7 — IA avanzada** | M26 avanzado | Matching, duplicados, recomendaciones de bajo riesgo |
| **R8 — Integraciones y expansión** | M27 | Webhooks, OAuth, API pública, sandbox |

**Fuera del camino crítico del piloto:** M25 transaccional, **M24** (pospuesto), M27 completo.

---

## 12. Camino crítico al piloto

```text
Fundación (M00, M07, M04)
  → Identidad (M01, M02, M05)
  → Mascota + Pasaporte (M08, M14)
  → Rescate (lost_found legacy, M13, M06, geografía transversal)
  → Adopción y tránsito (M09, M10, M15)
  → Organizaciones (M03, M11, M16)
  → Comunidad mínima (M19, M20 esencial, M06)
  → iOS paridad crítica + web pública progresiva
```

**Territorio piloto:** Partido de San Vicente + Partido de Almirante Brown, Provincia de Buenos Aires.

- Activación progresiva permitida.
- Métricas analizables **por partido**.
- Algoritmos territoriales pueden cruzar límites administrativos cuando la proximidad real lo justifique.

**Objetivos iniciales de éxito (umbrales orientativos):**

| Indicador | Umbral |
|-----------|--------|
| Mascotas activas registradas | ≥ 500 |
| Usuarios activos mensuales (MAU) | ≥ 300 |
| Organizaciones / refugios / rescatistas activos | ≥ 10 |
| Actores profesionales / comerciales | ≥ 10 |
| Casos reales de ayuda procesados | ≥ 50 |
| Casos urgentes con respuesta válida | ≥ 60% |
| Resultados exitosos confirmados | ≥ 20 |
| Retención mensual usuarios activos | ≥ 30% |

Debe existir actividad significativa en **ambos** partidos.

---

## 13. M28 — Portal Veterinario y Gestión Profesional de Salud

**Estado:** NO_INICIADO (ID reservado; sin código ni migraciones en repo).

**Propósito:** Herramienta operativa para centros y profesionales. **No** es historia clínica oficial ni sistema legal primario de custodia clínica. LeoVer **no reemplaza** registros oficiales ni obligaciones de conservación externa del profesional/establecimiento.

**Alcance previsto:**

- gestión profesional de pacientes vinculados autorizadamente;
- atenciones, vacunas, controles, procedimientos;
- estudios, documentos, indicaciones, seguimientos, recordatorios;
- equipos, permisos, autor, fecha y procedencia;
- exportación PDF y estructurada;
- propuesta de actualización al **Pasaporte (M14)**;
- aprobación / rechazo / corrección por responsable autorizado.

**Depende de:** M03, M05, M08, M14, M21, M22, M23, M07.

**Release:** R5. **Superficies:** VET, WEB, integración móvil autorizada.

**Relación con M12:** M12 = directorio y operación clínica Android implementada; M28 = portal profesional web completo.

---

## 14. M29 — Brand Studio y Publicidad

**Estado:** NO_INICIADO (ID reservado).

**Alcance previsto:**

- cuenta Brand / Advertiser;
- campañas asistidas;
- posts, stories y reels patrocinados con material aportado;
- Ayudas Concretas;
- plantillas;
- IA generativa (texto/imagen) con arquitectura desacoplada;
- copy, títulos, CTA, hashtags;
- generación de imágenes con cuota;
- variantes multiformato;
- segmentación no sensible;
- moderación / aprobación;
- programación / publicación;
- analítica, tutoriales, recomendaciones;
- límites de frecuencia;
- etiqueta **Patrocinado**.

**Modelo comercial:**

- distribución **incluida** en suscripción Brand Studio;
- **NO** CPM, **NO** CPC, **NO** boost pago separado.

**Depende de:** M04, M05, M07, M19, M21, suscripciones comerciales (Mercado Pago Suscripciones), M26 creativo.

**Release:** R5B.

---

## 15. IDs libres M30+

| Rango | Estado | Uso permitido |
|-------|--------|---------------|
| **M30, M31, …** | Libres confirmados en repo | Nuevos dominios, módulo unificador Perdidos/Encontrados, plataforma web modular, geografía modularizada — **solo con ADR** |
| **M28, M29** | Reservados estratégicamente (sin código) | Portal Vet y Brand Studio — no reasignar |

**Primer ID libre sin reserva previa en código:** **M30**.

---

## 16. Módulos reservados / pospuestos

### M24 — Pagos integrados y conciliación marketplace

| Campo | Valor |
|-------|-------|
| **Estado** | **RESERVADO / POSPUESTO** |
| Implementación | No iniciada — sin paquetes, rutas ni SQL |
| Documentación | `docs/03-modulos/M24-auditoria-preliminar.md` |
| Alcance futuro | Pagos in-platform, split, reembolsos, conciliación marketplace transaccional |

**Distinciones obligatorias:**

| Concepto | Estado actual | Módulo |
|----------|---------------|--------|
| Suscripciones comerciales LeoVer / Brand Studio | Decisión estratégica: Mercado Pago Suscripciones | Comercial transversal; **no activa M24** |
| Donaciones a terceros | Transferencia directa, **0% comisión** | M17 |
| Marketplace transaccional | **Fuera de V1 y piloto** | M25 catálogo sin checkout; M24 pospuesto |
| Pago servicio prestador | Directo entre partes | M22/M23 sin cobro integrado |

---

## 17. Pendientes abiertos (no inventar valores)

| ID | Tema | Estado |
|----|------|--------|
| PEN-008 | Precio definitivo suscripción comercial | OPEN |
| PEN-009 | Estructura futura de planes | OPEN |
| PEN-010 | Precio Brand Studio (M29) | OPEN |
| — | Períodos numéricos definitivos de retención | OPEN |
| — | Proveedor de video generativo (`VideoGenerationProvider`) | OPEN / FUTURO |
| — | Decisión pagos in-platform (M24) | OPEN — pospuesto |
| — | Marketplace transaccional | OPEN — fuera V1 |
| — | ID modular web unificado (M30+?) | OPEN — requiere ADR |

No introducir cifras inventadas en especificaciones ni código hasta decisión de negocio/legal.

---

## 18. Reglas para asignar futuros Mxx

1. Consultar inventario Mxx y este D01 antes de proponer un ID.
2. Verificar ausencia en código, SQL, ADR, migraciones, tests y rutas.
3. Preferir **M30+** para dominios nuevos no reservados.
4. Registrar decisión en **ADR** con alcance, dependencias y superficies.
5. **Nunca** reutilizar M00–M27 ni M28/M29 reservados para otro significado.
6. Si una capacidad Maestro no tiene Mxx, documentarla como transversal hasta ADR de modularización.
7. Cursor y herramientas IA proponen implementación; **no** asignan IDs ni reglas de producto sin aprobación.

---

## 19. Fuentes de verdad

| Dominio | Autoridad |
|---------|-----------|
| Numeración Mxx | Inventario v1.0 + este D01 v1.2 + ADR-013/014/015 |
| Estrategia producto | Documento Maestro v1.1 |
| Mascota y responsables | **M08** |
| Adopciones | **M09** |
| Pasaporte | **M14** |
| Tránsito SQL | **M10** (040–041) |
| Tránsito operación UI | **M15** (sobre M10) |
| Refugios ops | **M11** |
| Refugios org público | **M16** (053) |
| Veterinarias Android | **M12** |
| Avistamientos | **M13** |
| Perdidos/encontrados base | Legacy `lost_found_*` |
| Reglas sensibles | Supabase RLS/RPC |
| Pagos marketplace futuro | **M24** (reservado, pospuesto) |

**Documentos clave:**

- `docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md`
- `docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md`
- `docs/02-arquitectura/ADR-013-M13-track-tecnico-avistamientos-coincidencias.md`
- `docs/02-arquitectura/ADR-014-M14-remapeo-pasaporte.md`
- `docs/02-arquitectura/ADR-015-M15-hogares-de-transito.md`
- `docs/06-release/RC1-matriz-modulos.md`

---

## 20. Historial de versión

| Versión | Fecha | Cambio |
|---------|-------|--------|
| 1.0 | 2026-07 (histórico) | Catálogo inicial Maestro v1.0; notas técnicas M11/M12/M09/M14 |
| 1.1 | 2026-08-09 | Traducción Maestro v1.1 — **colisiones M09–M14 con repo**; no usar como autoridad numeración |
| **1.2** | **2026-08-09** | **Reconciliación con inventario real Mxx; preserva IDs históricos; corrige crosswalk; documenta M10↔M15, M11↔M16, lost_found, geoservicios transversales, M24/M28/M29** |

**Supersede:**

- `D01-LeoVer-Modulos-y-Orden-v1.1.md` — histórico; **no autoridad numeración**.
- `D01-Modulos-y-Orden.md` v1.0 — histórico; conservado por trazabilidad.

---

## Anexo A — Reglas de arquitectura y producto (vigentes)

| ID | Regla | Aplicación |
|----|-------|------------|
| R01 | Una identidad humana | Capacidades contextuales, no cuentas duplicadas por rol |
| R02 | Mascota canónica | Entidad independiente de publicaciones y casos |
| R03 | Responsable ≠ custodio | Tránsito no transfiere responsabilidad automáticamente |
| R04 | Backend autoritativo | RLS/RPC en Supabase |
| R05 | Deny-by-default | Mínimo privilegio |
| R06 | Web compartible | Páginas públicas seguras donde corresponda |
| R07 | Privacidad por defecto | Ubicación exacta, teléfono y domicilio protegidos |
| R08 | Estados explícitos | Transiciones definidas |
| R09 | Proveniencia | Declarado, profesional, verificado, inferido, sistema |
| R10 | Auditoría | Acciones sensibles trazables (M07) |
| R11 | IA asistida | Sugiere; no decide adopción, identidad, sanción ni diagnóstico final |
| R12 | Proveedores desacoplados | Mapas, IA, pagos vía contratos/adaptadores |
| R14 | Social esencial gratis | Ayuda social no bloqueada por pago |
| R15 | Sin marketplace transaccional V1 | Pago directo entre partes |
| R16 | Urgencia sobre publicidad | Bienestar prevalece |
| R17 | Exportabilidad profesional | M28 exporta; conservación externa del profesional |
| R18 | No microchip V1 | Fuera de piloto — ver Anexo B |

---

## Anexo B — Microchip

**Fuera de V1 y piloto.** No obligatorio. Sin búsqueda por chip, integración externa inicial ni dependencia del Pasaporte. Solo extensión futura opcional mediante ADR.

---

## Anexo C — Marketplace y pagos (V1)

**Marketplace transaccional:** fuera de V1 y piloto.

**Sí en V1:**

- búsqueda, contacto, agenda, reservas, contratación (M22, M23);
- catálogo comercial M25 sin checkout.

**No en V1:**

- cobro integrado usuario→prestador;
- checkout marketplace;
- liquidaciones;
- comisión por operación entre terceros.

**Suscripciones LeoVer / Brand Studio:** Mercado Pago Suscripciones (productos propios LeoVer).

**Donaciones a terceros:** transferencia directa verificada; **0% comisión** LeoVer.

---

## Anexo D — IA visual (matching perdido/encontrado)

| Componente | Decisión |
|------------|----------|
| Proveedor inicial | Google |
| Modelo | Gemini Embedding 2 o equivalente multimodal estable al implementar |
| Vectores | Supabase pgvector |
| Geografía | PostGIS |
| Matching | imagen + atributos + tiempo + geografía |
| Direccionalidad | PERDIDO → ENCONTRADOS ACTIVOS; ENCONTRADO → PERDIDOS ACTIVOS |
| Confirmación | **Humana obligatoria** — no identificación automática definitiva |
| Contrato | `VisualMatchingProvider` (desacoplado) |
| Módulo | M26 (+ integración M13) |

---

## Anexo E — IA Brand Studio (M29)

| Componente | Decisión |
|------------|----------|
| Texto / imagen generativa inicial | OpenAI (arquitectura desacoplada) |
| Contratos conceptuales | `GenerativeAIProvider`, `ImageGenerationProvider` |
| Video | `VideoGenerationProvider` — **ABIERTO / FUTURO** |
| Costo | Cuotas / créditos por anunciante / campaña |
| Hardcode | No fijar modelo específico como requisito permanente |

---

## Anexo F — Stack técnico aprobado

| Capa | Decisión |
|------|----------|
| Android | Kotlin + Jetpack Compose + MVVM/Flow |
| iOS | Swift + SwiftUI + Supabase |
| Web | Next.js + React + TypeScript + Cloudflare Workers + OpenNext |
| Backend | Supabase Auth + PostgreSQL + RLS + RPC + Storage |
| Geoespacial | PostGIS (transversal) |
| Mapas | Google Maps Platform (adaptadores) |
| Vectores | pgvector |
| Push | FCM + outbox/eventos |
| Suscripciones propias | Mercado Pago Suscripciones |

**Supersedido:** NestJS/Prisma obligatorio, Docker/Supabase local como requisito cotidiano permanente.

---

## Anexo G — Contrato de trabajo con Cursor

```text
FUENTES OBLIGATORIAS
1. docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md
2. docs/01-producto/D01-Modulos-y-Orden-v1.2.md  ← este documento
3. docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md
4. ADR y especificación vigente del módulo

ANTES DE MODIFICAR
- Auditar repo y reutilizar lo existente
- No renumerar Mxx ni inventar IDs
- Señalar contradicciones doc ↔ código

DURANTE
- Supabase autoritativo; RLS/RPC para reglas sensibles
- No programar módulos futuros por conveniencia

AL CERRAR
- Validación razonable; documentación afectada actualizada
```

---

## Aprobación

Al aprobarse, **D01 v1.2** sustituye a D01 v1.1 y a D01 v1.0 como mapa oficial de módulos y orden de desarrollo para planificación nueva. Las versiones anteriores se conservan como histórico.
