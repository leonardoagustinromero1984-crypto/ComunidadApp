# LeoVer — D01 Mapa de Módulos y Orden de Desarrollo

**Versión:** 1.3
**Fecha:** 15 de agosto de 2026
**Fuente superior:** LeoVer — Documento Maestro Integral de la Startup v1.2
**Estado:** Guía oficial de secuencia, dependencias y numeración Mxx
**Ruta de repositorio:** `/docs/01-producto/D01-Modulos-y-Orden-v1.3.md`

---

## 1. Propósito

Este documento traduce la estrategia del **Documento Maestro v1.2** a módulos técnicos (Mxx), releases (R0–R8), dependencias y superficies — **alineado con la numeración real del repositorio**.

D01 v1.3 **no renumera** módulos. Incorpora el gobierno de **VitaCora (M14)**, grants de servicio, propuestas, custodia, contexto único de organización, **cuentas adolescentes**, **legal/privacidad/tutoriales** y **Compromiso Comunidad LeoVer** como arquitectura transversal. **No** se crean módulos Legal, Teen ni Consent.

**Actualizar D01 no significa reimplementar módulos ya construidos.** Primero se audita lo existente, se conserva lo válido y solo se corrigen incompatibilidades documentales reales. Esta versión es **gobierno**; no implica SQL, Android, Web, iOS ni APK.

---

## 2. Regla de autoridad documental

Orden de precedencia para planificación y resolución de conflictos:

| Prioridad | Fuente |
|-----------|--------|
| 1 | Documento Maestro v1.2 (`docs/00-maestro/LeoVer-Documento-Maestro-v1.2.md`) |
| 2 | ADR aprobados (`docs/adr/`, `docs/02-arquitectura/ADR-0*.md`) — incluye ADR-016 |
| 3 | **D01 v1.3** (este documento) |
| 4 | Inventario real Mxx v1.0 (`docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md`) |
| 4b | Auditoría documental y matriz de vigencia v1.0 (`docs/00-startup/LeoVer-Auditoria-Documental-y-Matriz-de-Vigencia-v1.0.md`) |
| 5 | Especificación vigente del módulo y documentos de cierre |
| 6 | Código, migraciones y pruebas (corroboración) |
| 7 | D01 v1.0, D01 v1.1, D01 v1.2 y documentación histórica — **solo trazabilidad; no autoridad de numeración M09–M16** |

Si una especificación antigua contradice al Maestro v1.2 **y** al inventario real de Mxx, prevalece el **significado técnico ya implementado** salvo decisión formal nueva registrada en ADR.

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
- Reservar IDs libres (**M30+**) mediante ADR. M28 y M29 ya tienen significado estratégico.
- Representar capacidades transversales sin Mxx exclusivo.

---

## 4. Tabla maestra M00–M29

| ID | Nombre oficial D01 v1.3 | Release principal | Migraciones (repo) |
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
| **M14** | **VitaCora** | R1 | 050–052 (históricas; no reescribir en esta etapa) |
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
| M14 | IMPLEMENTADO | `m14/*`; nombre canónico **VitaCora**; artefactos `pasaporte` = histórico/legacy | `M14-cierre-tecnico.md`, ADR-014 (histórico) |
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
       M08 → M14 (VitaCora)
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
| M05 | Storage / media candidato canónico (confirmar en REBASE-03); signed URL ≠ identidad del archivo |
| M06 | Notificaciones centralizadas; dominios emiten intenciones |
| M07 | Auditoría, métricas, correlación; actor_user_id; legal_documents / consent_events / privacy_requests (transversal; no módulo Legal nuevo) |
| M08 | Autoridad de mascota; teen puede ser OWNER/AUTHORIZED; guardian **independiente** de pet owner; PET PERMISSION ≠ AGE CAPABILITY |
| M01/M02 | Identidad PERSON + age/protection + adulto responsable; no AccountType.TEEN; versionado legal y alta sin marketing consent |
| M21 | Reputación y verificaciones sin confundir con popularidad |

### 6.3 Dependencias especiales documentadas

| Relación | Regla |
|----------|-------|
| **M10 → M15** | M10 es persistencia autoritativa; M15 consume M10/M08 (ADR-015) |
| **M11 → M16** | M11 ops legacy; M16 perfiles org públicos sobre M03 (053) |
| **lost_found → M13** | M13 enriquece legacy; no reemplaza `lost_found_posts` |
| **M09 ↔ M14** | Adopciones (M09) puede emitir credenciales; VitaCora (M14) no duplica adopción ni identidad M08 |
| **M12 ↔ M28** | M12 = directorio/clínica Android; M28 = portal profesional futuro; hecho clínico autoritativo no se duplica en M14 |
| **M14 composición** | M14 integra/presenta; el dato autoritativo permanece en el dominio de origen (no `vitacora_*` paralelo) |
| **M20 institucional** | Conversación con la entidad; miembros responden; `actor_user_id` auditado; reglas de seguridad para menores |
| **M03 contexto** | Una organización = un ActiveContext; multicapacidad; `organization.type` no es autoridad |
| **Age/protection** | Transversal M01/M02; no es ActiveContext; no es módulo Mxx nuevo |
| **Legal / consent / erasure** | Transversal; no módulo Legal/Consent. LEGAL_* = DEFINED_*_PRELAUNCH; revisión jurídica antes de producción |
| **Tutoriales** | Transversal UX; skippable/reopenable; ≠ consentimiento |
| **Compromiso Comunidad** | Esenciales gratis; no entitlement pago en M08/M09/M10/M11/M16/M17/M19/M20 comunitarios |

---

## 7. Crosswalk — Capacidad Maestro v1.2 ↔ Mxx real

| Capacidad Maestro §7 | Mxx real (autoridad repo) | Notas |
|----------------------|---------------------------|-------|
| §7.1 Identidad y cuentas | M01, M02 | — |
| §7.2 Perfil personal y red | M02, M21 | — |
| §7.3 Perfil mascota y VitaCora | **M08** (identidad/responsabilidad/custodia), **M14** (VitaCora, grants, incorporación) | **M09 ≠ VitaCora**. Sharing no crea M30. Composición, no duplicación de datos. |
| §7.4 Red social | M19 | Privacidad adolescente; no distribución patrocinada UNDER_18 |
| §7.5 Perdidos y encontrados | **Legacy `lost_found_*` + M13** | Sin Mxx único histórico |
| §7.6 Adopciones | **M09** | ADR-014 |
| §7.7 Hogares de tránsito | **M10** (SQL) + **M15** (ops) | ADR-015; custodia temporal M08 |
| §7.8 Organizaciones y refugios | M03, **M11**, **M16** | M11 ops; M16 perfiles org; mascota institucional = org responsable; org multicapacidad; un solo ActiveContext |
| §7.9 Donaciones | M17 | Sin comisión LeoVer |
| §7.10 Eventos | M18 | — |
| §7.11 Veterinarias / salud | **M12** (directorio/clínica), **M28** (propuestas profesionales) | Persona ≠ profesional ≠ establecimiento |
| §7.12–7.13 Prestadores / guardería | M22, M23 | Guardería = categoría M22/M23; no módulo nuevo |
| §7.14 Tiendas | M25 | Sin checkout V1; transaccional V2/FUTURE |
| §7.15 Agenda | M23 | Reservas, estadías y snapshot de instrucciones |
| §7.16 Suscripciones LeoVer | M24 reservado + Mercado Pago Suscripciones (comercial) | ≠ marketplace transaccional |
| §7.17 Búsqueda y geoservicios | **Transversal** (PostGIS + Google Maps) | **M10 ≠ geografía** |
| §7.18 Comunicaciones | M06, **M20** | Primer contacto = conversación con la entidad; actor auditado; minor safety |
| §7.19 Reputación | M21 | Posterior a relación/interacción real |
| §3.5 Identidad y contexto | M01, M02, M03 + ADR-016 | ActiveContext no es identidad; no depende de `account_type`; org aparece una sola vez; age/protection no es contexto |
| §3.18 Cuentas adolescentes | M01, M02 (transversal); M08; M14; M19; M20; M29 | Sin módulo Teen. Guardian ≠ M08. Políticas DEFINED_*_PRELAUNCH / NO_SPONSORED_ADS |
| §3.19 Tutoriales | Transversal | Skippable/reopenable; VitaCora onboarding; ≠ consentimiento |
| §3.20 Legal / privacy requests | Transversal M01/M02/M07 | legal_documents, consent_events, privacy_requests; LEGAL_LAUNCH_GATE |
| §3.21 Compromiso Comunidad | Transversal | Esenciales gratis; donaciones 0%; monetización profesional/comercial separada |
| §3.6 Responsabilidad | **M08** + **M03** | Multi-OWNER personal; `created_by` ≠ autoridad; org = responsable institucional |
| §3.8–3.11 VitaCora grant / proposals | **M14** (composición, grant, visibilidad, integración), **M28** (propuestas clínicas), M22 (propuestas no clínicas) | Scopes de servicio; hecho en dominio autoritativo |
| §3.13 Guardería / consentimiento público | M22, M23, M08, M14 | DEFAULT = NO; revocable; snapshot de instrucciones en M23 |
| §3.15 Eliminar / media | Transversal; **M05** candidato media | Soft-delete; signed URL temporal |
| §3.16 Tiempo / baseline | Transversal; REBASE-03 | `date`/`LocalDate` vs `timestamptz` UTC; staging ≠ producción |
| Archivos / media | **M05** (candidato canónico; confirmar en REBASE-03) | Signed URL ≠ identidad del archivo |
| §10.2 Brand Studio | **M29** (reservado) | NO_SPONSORED_ADS_UNDER_18_V1 |
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

### 8.4 VitaCora, grants, propuestas y guardería — sin M30

**Nombre canónico de producto y dominio:** VitaCora. **Módulo:** M14 — VitaCora. Vita = vida; Cora = corazón; también evoca una bitácora. Descriptor: “Su vida. Su historia. Sus cuidados.” No es solamente salud.

Estas capacidades **pertenecen a módulos existentes**. No se crea M30 (ni otro ID) para VitaCora ni para sharing. “Passport/Pasaporte Sharing” es nombre histórico; la autoridad vigente es **M14 — VitaCora**.

| Capacidad Maestro v1.2 | Autoridad Mxx | Qué no hacer |
|------------------------|---------------|--------------|
| Identidad de mascota, OWNER múltiples, organización responsable, `created_by`, autorizado, custodia temporal | **M08** | No duplicar mascota “de refugio”; no tratar `created_by` como autoridad eterna; guardian/adulto responsable **no** crea OWNER |
| Identidad humana, age band, protection, age assurance, adulto responsable | **M01 / M02** | No AccountType.TEEN; no “Usar LeoVer como Adolescente” |
| Membership / ownership organizacional; org multicapacidad; un solo ActiveContext por organización | **M03** | No crear cuenta humana por organización; no una identidad por cada servicio |
| Custodia de tránsito | **M10** persistencia + **M15** operación; M08 custodia | No segunda persistencia |
| VitaCora: composición, grants, visibilidad, integración, proveniencia de integración, actualizaciones pendientes | **M14** | No copiar VitaCora; no `vitacora_vaccination` paralelo; no columna de sharing en cada prestador; no crear `passport_*` en el baseline nuevo |
| Primer contacto y conversación institucional con prestador/org | **M20** | No chat exclusivo de servicios; no conversación privada con el empleado; minor safety; adulto no lee DMs por defecto |
| Reputación posterior a relación real | **M21** | No reputación por popularidad |
| Prestador, categoría guardería, perfil de servicio | **M22** | No módulo Guardería; `organization.type` no limita capacidades |
| Reserva, estadía, check-in/out, huéspedes, snapshot de instrucciones/consentimientos | **M23** | Datos de reserva ≠ grant de VitaCora; no copiar VitaCora completa; chequeo de age capability en acciones sensibles |
| Propuestas profesionales veterinarias | **M28** | Dueño admin no veterinario no firma; hecho aceptado vive en dominio clínico |
| Propuestas no clínicas (paseo, estadía, adiestramiento, transporte) | **M22** origina; **M14** integra si el responsable acepta | No edición directa de VitaCora |
| Archivos / media (candidato) | **M05** | Signed URL ≠ identidad; confirmar autoridad canónica en REBASE-03 |
| Red social / descubrimiento | **M19** | Respeta defaults de privacidad adolescente; sin dark patterns |
| Publicidad / Brand Studio | **M29** | NO_SPONSORED_ADS_UNDER_18_V1; sin targeting sensible |

Si una futura implementación requiere tablas nuevas, la autoridad de dominio sigue siendo la de esta tabla. Una tabla de grants vive bajo **M14**; una de propuestas clínicas bajo **M28** (con vínculo M14); una de estadía/huésped bajo **M23**; la custodia resultante bajo **M08**.

#### Scopes de servicio (M14 grant)

Para SERVICIOS, los alcances visibles son exactamente:

`NONE` · `ESSENTIAL` · `HEALTH` · `ESSENTIAL_AND_HEALTH` · `FULL_SHAREABLE`

Correspondencia de producto: NO COMPARTIR | DATOS ESENCIALES | SALUD | DATOS ESENCIALES + SALUD | VITACORA COMPLETA.

VITACORA COMPLETA = información **funcional compartible** de esa relación. **No** incluye automáticamente Momentos personales privados, mensajes, auditoría, IDs internos, secretos, datos protegidos de terceros ni contenido explícitamente privado.

Salud es dimensión estructural de VitaCora; **no** se comparte de forma obligatoria con todo prestador.

Duración: `UNTIL_DATE` | `INDEFINITE` | `REVOKED`. No hardcodear períodos.

Momentos personales: dimensión de VitaCora; **privados por defecto**.

#### Reserva ≠ grant; snapshot

“No compartir VitaCora” no oculta qué mascota está reservada. M23 conserva snapshot de instrucciones/consentimientos acordados. VitaCora compartida puede seguir viva.

#### Consentimiento público de guardería

DEFAULT = NO. Revocable. Publicaciones públicas relacionables con el consentimiento. LeoVer retira/oculta contenido público bajo su control si se revoca.

#### Familia y permisos (M08 / M14)

Relaciones conceptuales: OWNER / AUTHORIZED. Autoridad real por permisos granulares (`pet.view`, `pet.edit`, `vitacora.view`, `vitacora.manage`, `vitacora.share`, `health.manage_declared`, `services.authorize`, `privacy.manage`, `responsibility.manage`). Varios OWNER pueden tener capacidad equivalente.

#### Eliminar = no destrucción

Toda operación visual “Eliminar” es ocultar/archivar/soft-delete/desactivar/revocar/anonimizar. No se destruye información de negocio. Los registros autoritativos de terceros no desaparecen porque alguien los quite de su VitaCora.

#### Validación canónica PRE-REBASE-03

- No se crea un módulo de VitaCora separado de M14.
- No se duplica identidad de mascota (M08).
- No se duplica dato autoritativo (composición M14).
- No se crea identidad por cada capacidad de organización (M03 + ADR-016).
- `account_type` no es autoridad. Incorporar menores **no** revive `account_type`.
- No se crea módulo “Teen Accounts”.
- Guardian/adulto responsable no es OWNER automático (M08).
- M14 respeta age capability además del grant.
- M19/M20/M29 respetan privacidad, mensajería y NO_SPONSORED_ADS_UNDER_18_V1.
- PRODUCT DELETE ≠ PRIVACY ERASURE. privacy_requests / legal_documents / consent_events = transversales.
- Políticas DEFINED_*_PRELAUNCH / DEFINED_FOR_V1_PRELAUNCH / NO_SPONSORED_ADS_UNDER_18_V1. Revisión jurídica final antes de producción.
- No módulo Legal, Teen ni Consent.
- Scopes de sharing unificados (ningún modelo DATOS BÁSICOS / PERSONALIZADO / PASAPORTE COMPLETO vigente).
- Passport/Pasaporte no es término canónico vigente.

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

### 9.5 Cuentas adolescentes / seguridad de menores

- **Estado:** gobierno canónico (Maestro §3.18). Sin Mxx dedicado.
- **Identidad:** PERSON + age/protection. No `AccountType.TEEN`.
- **UNDER_13:** no cuenta autónoma.
- **M08:** pet responsibility independiente del adulto responsable.
- **M14:** grants y FULL_SHAREABLE sujetos a age capability además del permiso de mascota.
- **M19:** privacidad adolescente (perfil/descubrimiento); momentos VitaCora ≠ post social.
- **M20:** minor safety (DM desconocidos, institucional, block/report); adulto no lee DMs automáticamente.
- **M29:** `NO_SPONSORED_ADS_UNDER_18_V1`.
- **Legal/audit:** transversal (`legal_documents`, `consent_events`, `privacy_requests`). Políticas DEFINED_*_PRELAUNCH. `LEGAL_LAUNCH_GATE`.
- **Tutorial:** `TEEN_ACCOUNT_TUTORIAL` skippable/reopenable; ≠ consentimiento.
- **Guardian:** no auto-read; no auto-OWNER; verificación de cuenta V1, no DNI por defecto.
- **M22/M23:** consentimientos contextuales y chequeo de age capability en acciones sensibles.

### 9.6 Legal, consentimientos y privacy requests

- **Estado:** gobierno canónico (Maestro §3.20). Sin Mxx Legal/Consent.
- **Autoridad:** transversal M01/M02 + auditoría M07.
- **Soportar conceptualmente:** `legal_documents` (type, version, locale, effective date, content hash, status); `consent_events` (subject, actor, document/version, action, timestamp, source, evidence, withdrawal); `privacy_requests` (ACCESS / RECTIFICATION / UPDATE / ERASURE).
- PRODUCT DELETE ≠ PRIVACY ERASURE. Registros de terceros protegidos. DATA MINIMIZATION.
- Documentos actuales = DRAFT PRE-LAUNCH. `LEGAL_LAUNCH_GATE` antes de registro público.
- **No** marketing consent en el alta. Comunicaciones operativas ≠ marketing. Consentimiento just-in-time.

### 9.7 Tutoriales

- **Estado:** gobierno canónico (Maestro §3.19). Sin Mxx dedicado.
- Onboarding común + tutoriales contextuales (VitaCora, Familia, Lost/Found, Adopción, Refugio, Rescatista, Tránsito, Veterinaria, Guardería, Prestadores, Cuenta Adolescente).
- Todos `SKIPPABLE = YES` / `REOPENABLE = YES`. Omitir ≠ consentimiento, permisos ni Términos.
- Estado por tutorial (key, version, viewed, skipped, completed). No mega-estado global. No implementar ahora.
- VitaCora: “Su vida. Su historia. Sus cuidados.” Una vez + Ayuda / ¿Qué es VitaCora?

### 9.8 Compromiso Comunidad LeoVer

- Funciones comunitarias esenciales **gratuitas** para personas, rescatistas, refugios y ONG.
- No subscription/entitlement para identidad, mascota, VitaCora esencial, adopción, lost/found, rescate, tránsito, herramientas esenciales de org ni comunicación comunitaria esencial.
- Monetización profesional/comercial (M28, M29, suscripciones de prestadores) permanece separada.
- Donaciones a terceros: 0% comisión; no checkout obligatorio.
- Rescatista = PERSON + capacidad contextual; no AccountType.

---

## 10. Superficies

| Código | Superficie | Stack | Módulos principales |
|--------|------------|-------|---------------------|
| AND | Android | Kotlin, Jetpack Compose, MVVM, Supabase | M00–M27 implementados |
| IOS | iOS piloto | Swift, SwiftUI, **mismo Supabase**, mismos contratos dominio/backend | Paridad crítica — ver §10.1 |
| WEB | Web pública | Next.js, React, TypeScript, Cloudflare Workers, OpenNext | Transversal; expone dominios según madurez |
| ORG | Portal organizaciones | Web + permisos M03 | M03, M11, M16, M09, M15, M17 |
| VET | Portal veterinario | Web (futuro **M28**) | M12, M28, M14 (propuestas a VitaCora) |
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
- VitaCora básica (**M14**);
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
| **R1 — Identidad** | M01, M02, M05, M08, **M14** | Cuenta + mascota + **VitaCora** utilizables |
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
  → Mascota + VitaCora (M08, M14)
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

**Estado:** IMPLEMENTADO (PILOT-MINIMUM) en Android responsable; portal web completo NO_APLICA en este corte. ID **no** se reasigna.

**Propósito:** Herramienta operativa para centros y profesionales. **No** es historia clínica oficial ni sistema legal primario de custodia clínica. LeoVer **no reemplaza** registros oficiales ni obligaciones de conservación externa del profesional/establecimiento.

**Autoridad de gobierno v1.2:** propuestas profesionales a VitaCora. El responsable acepta, descarta o solicita corrección. El hecho aceptado se persiste en el dominio autoritativo; VitaCora lo integra. Sin autoaceptación V1.

**Alcance previsto / vigente:**

- gestión profesional de pacientes vinculados autorizadamente;
- atenciones, vacunas, controles, procedimientos;
- estudios, documentos, indicaciones, seguimientos, recordatorios;
- equipos, permisos, autor, fecha y procedencia;
- exportación PDF y estructurada;
- propuesta de actualización a **VitaCora (M14)**;
- aprobación / rechazo / corrección por responsable autorizado;
- separación persona humana ≠ profesional veterinario ≠ establecimiento (M03).

Un dueño administrativo no veterinario **no** puede firmar información profesional solo por ser OWNER/ADMIN.

**Depende de:** M03, M05, M08, M14, M21, M22, M23, M07.

**Release:** R5. **Superficies:** VET, WEB, integración móvil autorizada.

**Relación con M12:** M12 = directorio y operación clínica Android; M28 = operación/profesional sanitaria autorizada y propuestas clínicas.

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
- etiqueta **Patrocinado**;
- Cuentas UNDER_18: **sin** distribución patrocinada (`NO_SPONSORED_ADS_UNDER_18_V1`); sin targeting personalizado/sensible; sin VitaCora, salud ni ubicación precisa como señal comercial. Contenido orgánico comunitario permitido.

**Modelo comercial:**

- distribución **incluida** en suscripción Brand Studio;
- **NO** CPM, **NO** CPC, **NO** boost pago separado.

**Depende de:** M04, M05, M07, M19, M21, suscripciones comerciales (Mercado Pago Suscripciones), M26 creativo.

**Release:** R5B.

---

## 15. IDs libres M30+

| Rango | Estado | Uso permitido |
|-------|--------|---------------|
| **M30, M31, …** | Libres confirmados en repo | Nuevos dominios, módulo unificador Perdidos/Encontrados, plataforma web modular, geografía modularizada — **solo con ADR**. **No** usar M30 para VitaCora / sharing (autoridad M14) ni para Teen Accounts (transversal). |
| **M28, M29** | Reservados / en uso estratégico | M28 = vet profesional; M29 = Brand Studio — no reasignar |

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
| — | Marketplace transaccional | CERRADO — fuera V1; análisis V2 |
| — | VitaCora / sharing como módulo nuevo | CERRADO — no; autoridad M14 (Passport Sharing = nombre histórico) |
| — | Módulo Teen Accounts | CERRADO — no; transversal M01/M02 + enforcement en M08/M14/M19/M20/M29 |
| PEN-040 | Consentimiento de menores | CERRADO producto — LEGAL_MINOR_CONSENT_POLICY = DEFINED_PRELAUNCH; revisión jurídica final |
| PEN-041 | Publicidad a menores | CERRADO producto — NO_SPONSORED_ADS_UNDER_18_V1 |
| PEN-042 | Verificación adulto responsable | CERRADO V1 producto — DEFINED_FOR_V1_PRELAUNCH; sin DNI por defecto |
| PEN-043 | Privacy erasure | CERRADO producto — DEFINED_PRELAUNCH; ≠ PRODUCT DELETE |
| — | Módulo Legal / Consent / Teen | CERRADO — no; transversal |
| — | Marketing consent en alta | CERRADO — no |
| — | Compromiso Comunidad LeoVer | CERRADO — esenciales gratis |
| — | LEGAL_LAUNCH_GATE | CERRADO criterio — antes de registro público |
| — | Guardería como módulo nuevo | CERRADO — no; categoría M22/M23 |
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
| Numeración Mxx | Inventario v1.0 + este D01 v1.3 + ADR-013/014/015/016 |
| Estrategia producto | Documento Maestro v1.2 |
| Identidad humana y contexto | ADR-016; M01/M02/M03; `account_type` no canónico; org = un ActiveContext; age/protection transversal |
| Adulto responsable / menor | M01/M02 (no M08) |
| Mascota, responsables, custodia | **M08** (multi-OWNER o organización; `created_by` = proveniencia) |
| Ownership / membership org | **M03** (multicapacidad; un ActiveContext por organización) |
| Adopciones | **M09** |
| VitaCora (composición, grants, visibilidad, integración) | **M14** (respeta age capability) |
| Archivos / media | **M05** (candidato canónico; confirmar en REBASE-03) |
| Tránsito SQL | **M10** (040–041) |
| Tránsito operación UI | **M15** (sobre M10) |
| Refugios ops | **M11** |
| Refugios org público | **M16** (053) |
| Veterinarias directorio | **M12** |
| Propuestas profesionales vet | **M28** |
| Avistamientos | **M13** |
| Perdidos/encontrados base | Legacy `lost_found_*` |
| Mensajería / primer contacto | **M20** (conversación con la entidad; `actor_user_id`; minor safety) |
| Reputación | **M21** |
| Prestadores / guardería | **M22** |
| Agenda, reservas, estadías | **M23** (reserva ≠ grant; snapshot de instrucciones) |
| Social / descubrimiento | **M19** (privacidad adolescente) |
| Publicidad | **M29** (`NO_SPONSORED_ADS_UNDER_18_V1`) |
| Consentimiento / audit legal | Transversal: `legal_documents`, `consent_events`, `privacy_requests`. DRAFT PRE-LAUNCH. LEGAL_LAUNCH_GATE |
| Tutoriales | Transversal UX; skippable/reopenable; ≠ consentimiento |
| Compromiso Comunidad | Esenciales gratis; donaciones 0%; no entitlement comunitario |
| Reglas sensibles | Supabase RLS/RPC (incluye age/protection cuando se implemente) |
| Pagos marketplace futuro | **M24** (reservado, pospuesto); checkout M25 fuera de V1 |
| Fechas / tiempo | Transversal: `date`/`LocalDate` vs `timestamptz` UTC; agenda con zona horaria |
| Baseline backend | REBASE-03: reconstrucción canónica; staging ≠ producción |

**Documentos clave:**

- `docs/00-maestro/LeoVer-Documento-Maestro-v1.2.md`
- `docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md` (histórico)
- `docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md`
- `docs/02-arquitectura/ADR-013-M13-track-tecnico-avistamientos-coincidencias.md`
- `docs/02-arquitectura/ADR-014-M14-remapeo-pasaporte.md` (histórico de implementación; nombre de archivo legacy)
- `docs/02-arquitectura/ADR-015-M15-hogares-de-transito.md`
- `docs/02-arquitectura/ADR-016-identidad-capacidades-y-contexto-operativo.md`
- `docs/06-release/RC1-matriz-modulos.md`

---

## 20. Historial de versión

| Versión | Fecha | Cambio |
|---------|-------|--------|
| 1.0 | 2026-07 (histórico) | Catálogo inicial Maestro v1.0; notas técnicas M11/M12/M09/M14 |
| 1.1 | 2026-08-09 | Traducción Maestro v1.1 — **colisiones M09–M14 con repo**; no usar como autoridad numeración |
| **1.2** | **2026-08-09** | **Reconciliación con inventario real Mxx; preserva IDs históricos; corrige crosswalk; documenta M10↔M15, M11↔M16, lost_found, geoservicios transversales, M24/M28/M29** |
| **1.3** | **2026-08-15** | **Alineación Maestro v1.2: VitaCora, grants, multi-OWNER, org responsable, M20, guardería, REBASE-03, menores, erasure/legal versionado, tutoriales, NO_SPONSORED_ADS_UNDER_18_V1, Compromiso Comunidad. Sin módulos Legal/Teen/Consent. Checkout fuera de V1.** |

**Supersede:**

- `D01-LeoVer-Modulos-y-Orden-v1.1.md` — histórico; **no autoridad numeración**.
- `D01-Modulos-y-Orden.md` v1.0 — histórico; conservado por trazabilidad.
- `D01-Modulos-y-Orden-v1.2.md` — histórico de mapa técnico; **no autoridad para planificación nueva**.

---

## Anexo A — Reglas de arquitectura y producto (vigentes)

| ID | Regla | Aplicación |
|----|-------|------------|
| R01 | Una identidad humana | Capacidades contextuales, no cuentas duplicadas. `account_type` no es canónico. No AccountType.TEEN. ActiveContext no concede permisos ni depende de `account_type` ni de age band. |
| R01c | Age/protection | Dimensión de PERSON. UNDER_13 sin cuenta autónoma. 13–15 / 16–17 protegidas. 18+ adulta. No es ActiveContext. |
| R01d | Adulto responsable ≠ OWNER | Relación persona–persona; M08 se asigna explícitamente |
| R01e | PET PERMISSION ≠ AGE CAPABILITY | Backend evalúa identidad + permiso + age/protection + consentimiento |
| R01b | Organización única en contexto | Una org aparece una sola vez; multicapacidad; `organization.type` no es autoridad |
| R02 | Mascota canónica | Entidad independiente de publicaciones, casos y del actor que la cargó |
| R02b | Creador ≠ autoridad | `created_by_user_id` es proveniencia; no autoridad eterna |
| R02c | Multi-OWNER personal | Pueden existir varios OWNER simultáneos; autoridad por permisos granulares |
| R03 | Responsable ≠ custodio | Tránsito/guardería/servicio no transfiere responsabilidad automáticamente |
| R03b | Mascota institucional | Bajo organización, el responsable operativo es la organización; actor humano auditado |
| R03c | Cambio de responsabilidad | Misma identidad M08 y misma VitaCora; no transferir notas/mensajes/secretos privados |
| R04 | Backend autoritativo | RLS/RPC en Supabase |
| R05 | Deny-by-default | Mínimo privilegio; ningún cliente se autoconcede grant |
| R06 | Web compartible | Páginas públicas seguras donde corresponda |
| R07 | Privacidad por defecto | Ubicación exacta, teléfono y domicilio protegidos; Momentos personales privados por defecto |
| R07b | Privacidad adolescente | Perfil y VitaCora privados por defecto; ubicación precisa nunca pública; sin dark patterns |
| R08 | Estados explícitos | Transiciones definidas |
| R09 | Proveniencia | Declarado, profesional, tercero/prestador, verificado, inferido, sistema |
| R10 | Auditoría | Acciones sensibles trazables (M07); `responsible_entity` + `actor_user_id` |
| R11 | IA asistida | Sugiere; no decide adopción, identidad, sanción ni diagnóstico final |
| R12 | Proveedores desacoplados | Mapas, IA, pagos vía contratos/adaptadores |
| R14 | Social esencial gratis | Ayuda social no bloqueada por pago |
| R15 | Sin marketplace transaccional V1 | Pago directo entre partes; checkout V2/FUTURE |
| R16 | Urgencia sobre publicidad | Bienestar prevalece |
| R17 | Exportabilidad profesional | M28 exporta; conservación externa del profesional |
| R18 | No microchip V1 | Fuera de piloto — ver Anexo B |
| R19 | Lectura ≠ escritura | Grant de VitaCora no autoriza edición directa; FULL_SHAREABLE sujeto a age capability |
| R19b | Composición | VitaCora no duplica datos autoritativos de otros dominios |
| R19c | Scopes de servicio | NONE / ESSENTIAL / HEALTH / ESSENTIAL_AND_HEALTH / FULL_SHAREABLE |
| R19d | Duración de acceso | UNTIL_DATE / INDEFINITE / REVOKED; sin períodos hardcodeados |
| R19e | Reserva ≠ grant | Identificar la mascota reservada no implica compartir VitaCora |
| R20 | El responsable decide | Propuestas PENDING hasta aceptación; hecho aceptado en dominio autoritativo; sin autoaceptación V1 |
| R21 | Primer contacto LeoVer | Mensajería institucional M20; conversación con la entidad |
| R21b | M20 menores | DM de desconocidos restringible; block/report; adulto no lee DMs automáticamente |
| R22 | Guardería | Categoría M22/M23; huéspedes = custodia, no ownership |
| R22b | Consentimiento público | DEFAULT = NO; revocable; publicaciones ligadas al consentimiento |
| R23 | Eliminar no destructivo | PRODUCT DELETE = hide/archive/revoke/deactivate. Distinto de PRIVACY ERASURE |
| R24 | Media canónica | Asset + metadata estable; signed URL temporal; candidato M05 |
| R25 | Modelo temporal | Fecha sin hora = date/LocalDate; hechos = timestamptz UTC; agenda con zona horaria |
| R26 | Sin módulo Teen | Protección adolescente transversal; no M30+ para esto |
| R27 | M29 menores | NO_SPONSORED_ADS_UNDER_18_V1; sin targeting sensible |
| R28 | Tutoriales | Todos skippable/reopenable; ≠ consentimiento; VitaCora en onboarding |
| R29 | Privacy erasure | DEFINED_PRELAUNCH; privacy_requests; registros de terceros protegidos |
| R30 | Consentimiento | Términos ≠ Privacidad ≠ Comunidad ≠ contextual. Just-in-time. Sin marketing en alta |
| R31 | Adulto responsable V1 | PERSON 18+ verificada; vínculo bilateral; UX no afirma tutela legal |
| R32 | Compromiso Comunidad | Esenciales gratis para personas, rescatistas, refugios y ONG |
| R33 | LEGAL_LAUNCH_GATE | DRAFT PRE-LAUNCH hasta entidad, documentos vigentes y revisión jurídica |

---

## Anexo B — Microchip

**Fuera de V1 y piloto.** No obligatorio. Sin búsqueda por chip, integración externa inicial ni dependencia de VitaCora. Solo extensión futura opcional mediante ADR.

---

## Anexo C — Marketplace y pagos (V1)

**Marketplace transaccional:** fuera de V1 y piloto.

**Sí en V1:**

- búsqueda, contacto, agenda, reservas, contratación (M22, M23);
- VitaCora compartida opcional y mensajería LeoVer (M14, M20);
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
1. docs/00-maestro/LeoVer-Documento-Maestro-v1.2.md
2. docs/01-producto/D01-Modulos-y-Orden-v1.3.md  ← este documento
3. docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md
4. ADR y especificación vigente del módulo (incl. ADR-016)

ANTES DE MODIFICAR
- Auditar repo y reutilizar lo existente
- No renumerar Mxx ni inventar IDs
- No crear M30 para VitaCora sharing, Guardería, Teen Accounts, Legal ni Consent
- No crear `passport_*` en el baseline nuevo
- No revivir `account_type` / AccountType.TEEN
- No checkbox de marketing en el alta
- Señalar contradicciones doc ↔ código

DURANTE
- Supabase autoritativo; RLS/RPC para reglas sensibles
- No programar módulos futuros por conveniencia
- No desarrollar Tienda/checkout en esta etapa

AL CERRAR
- Validación razonable; documentación afectada actualizada
```

---

## Addendum REBASE-01 (no reescribe el historial)

La identidad humana y el contexto operativo quedaron reconciliados en **ADR-016** y se elevan a gobierno en **Maestro v1.2**. `AccountType` / `account_type` no es autoridad de producto; **no** se crea `AccountType.TEEN`. Age/protection es dimensión de PERSON, no ActiveContext. M25 checkout permanece fuera de V1. VitaCora sharing y Guardería se implementarán sobre M14 / M22 / M23 / M08 / M20 / M28. Menores, legal/consent/erasure y tutoriales son transversales; **no** hay módulos Teen, Legal ni Consent. Políticas DEFINED_*_PRELAUNCH / NO_SPONSORED_ADS_UNDER_18_V1 alimentan REBASE-03B. Compromiso Comunidad LeoVer: esenciales gratis. Documentos legales = DRAFT PRE-LAUNCH.

---

## Aprobación

Al aprobarse, **D01 v1.3** sustituye a D01 v1.2, D01 v1.1 y D01 v1.0 como mapa oficial de módulos y orden de desarrollo para planificación nueva. Las versiones anteriores se conservan como histórico.
