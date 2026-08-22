# M29 — Brand Studio y Publicidad

**Producto:** LeoVer  
**Módulo:** M29 — Brand Studio y Publicidad  
**Versión:** 1.0  
**Fecha:** 2026-08-09  
**Fuente superior:** Documento Maestro Integral v1.1 · D01 v1.2 · Inventario real Mxx v1.0  
**Estado:**

```text
M29 — ESPECIFICADO (DISEÑO)
IMPLEMENTACIÓN — NO INICIADA
SQL / CÓDIGO — SIN CAMBIOS EN ESTA ETAPA
PILOTO COMERCIAL — PUEDE SER GRATUITO SIN CONVERSIÓN AUTOMÁTICA
```

**Ruta de repositorio:** `/docs/03-modulos/M29-brand-studio-y-publicidad.md`

---

## 1. Identificación

| Campo | Valor |
|-------|-------|
| ID | **M29** |
| Nombre | Brand Studio y Publicidad |
| Release D01 | **R5B — Brand Studio** |
| Superficies | **WEB** (Brand Studio anunciante), **ANDROID / iOS** (renderizado patrocinado en Inicio y superficies sociales) |
| Stack web estratégico | Next.js · React · TypeScript · Supabase · Cloudflare Workers · OpenNext |
| Backend | Supabase (PostgreSQL + RLS + RPC + Edge Functions + Storage M05) |
| Proveedor suscripciones inicial | Mercado Pago Suscripciones |
| Proveedor IA inicial (texto/imagen) | OpenAI (arquitectura desacoplada) |

---

## 2. Estado

| Aspecto | Estado |
|---------|--------|
| Código M29 | Ausente |
| Migraciones M29 | Ausentes |
| Portal web Brand Studio | **NO INICIADO** — no existe workspace web oficial en el repo (`package.json` / Next.js ausentes) |
| Feed social base | **IMPLEMENTADO** — `public.posts` (Inicio: REEL, STORY, GENERAL, URGENT, …) + migración 078 |
| Red social M19 | **IMPLEMENTADO** — `m19_social_posts` (060–061), moderación integrada M04 |
| Organizaciones M03 | **IMPLEMENTADO** |
| Veterinarias M12 | **IMPLEMENTADO** (046–047) |
| Comercio M25 | **IMPLEMENTADO** catálogo/promos tienda **sin checkout** (070–071) |
| Moderación M04 | **IMPLEMENTADO** |
| Analytics/auditoría M07 | **IMPLEMENTADO** |
| IA plataforma M26 | **IMPLEMENTADO** matching/asistencia — **distinto** de IA creativa Brand Studio |
| Pagos marketplace M24 | **POSPUESTO** — suscripciones Brand Studio **no activan M24** |
| Portal vet M28 | **PILOT-MINIMUM** (080) — datos clínicos **prohibidos** para targeting M29 |

M29 es un **producto comercial sobre la red social existente**, no una segunda red.

---

## 3. Objetivo

Permitir que marcas y actores comerciales autorizados creen publicidad **útil y contextual** dentro de LeoVer, con un flujo de aproximadamente **2–3 pasos**:

1. **Objetivo** de campaña  
2. **Contenido** (asistido por IA + revisión humana)  
3. **Audiencia permitida** + revisión + publicación  

Reducir la complejidad para veterinarias, tiendas, prestadores, emprendimientos y marcas, **sin convertir Inicio en un feed saturado de anuncios** ni erosionar confianza, bienestar animal o privacidad.

---

## 4. Principios

1. **Una sola red social** — M29 no duplica Post, Story, Reel, feed ni perfiles.  
2. **Patrocinado siempre visible** — nunca publicidad oculta ni disfrazada de recomendación neutral de LeoVer.  
3. **Suscripción, no subasta** — la distribución está incluida en Brand Studio; no CPM/CPC/Boost separado (DEC cerrada).  
4. **Bienestar > ingresos** — seguridad, urgencias y contenido comunitario crítico prevalecen sobre patrocinado.  
5. **Privacidad by design** — el anunciante define criterios; LeoVer evalúa elegibilidad; **no** expone identidades individuales.  
6. **Moderación humana en piloto** — campañas sensibles o todas en Pilot-Minimum requieren aprobación antes de activarse.  
7. **IA acotada** — cuotas/créditos; keys solo backend; humano revisa antes de publicar.  
8. **Pago ≠ confianza** — suscripción Brand Studio no otorga verificación, badge, reputación ni bypass de moderación.  
9. **Reutilizar antes de duplicar** — extender M03, M04, M05, M07, M19, `posts`, M12, M25, M22 según corresponda.  
10. **Deny-by-default** — RLS + RPC para operaciones sensibles; nunca `service_role` en cliente.

---

## 5. Alcance

- Cuenta anunciante / entitlement Brand Studio  
- Campañas asistidas (objetivo → creativo → audiencia → revisión)  
- Formatos patrocinados sobre superficies sociales existentes  
- **Ayuda Concreta** patrocinada  
- Plantillas y onboarding contextual  
- IA generativa desacoplada (texto/imagen) con cuotas  
- Segmentación **no sensible**  
- Motor de elegibilidad, frecuencia y prioridades  
- Moderación integrada M04  
- Analítica agregada anunciante + métricas internas LeoVer  
- Suscripción Mercado Pago Suscripciones (conceptual)  
- Kill switch operativo  
- Contratos Android/iOS para render, CTA, ocultar/reportar  

---

## 6. No objetivos

- Segunda red social, segundo feed o segundo grafo social  
- CPM, CPC, boost pago, subasta de impresiones o compra puntual de alcance  
- Targeting con ubicación exacta, clínica M28, chats, Pasaporte privado o PII  
- Marketplace transaccional, checkout integrado o pagos in-platform (M24 pospuesto)  
- Venta de animales, diagnóstico/tratamiento por IA en publicidad  
- Video generativo ilimitado o proveedor de video definitivo en V1 spec  
- Agencia publicitaria avanzada, billing complejo, atribución perfecta  
- Sustituir promociones de tienda M25 (`m25_promotions`) ni posts orgánicos M19  
- Forzar Brand Studio a organizaciones solidarias no comerciales (M16/M17)  

---

## 7. Relación con módulos existentes

### 7.1 Auditoría de superficies sociales (evidencia repo)

| Superficie | Autoridad | Tabla / código | Uso actual | Rol M29 |
|------------|-----------|----------------|------------|---------|
| Feed Inicio (social-first) | Transversal + RC1.2 | `public.posts`, `FeedPost`, `HomeViewModel` | GENERAL, REEL, STORY, URGENT, LOST_FOUND, PROMO, … | **EXTENDER** — placement patrocinado en feed principal |
| Red social M19 | M19 | `m19_social_posts` (060–061) | Publicaciones orgánicas org/comunidad, moderación M04 | **EXTENDER** — variantes patrocinadas vinculadas a campaña |
| Stories efímeras | RC1.2 / 078 | `posts.type = STORY`, `expires_at` | Carrusel historias Inicio | **EXTENDER** — story patrocinada con TTL propio |
| Reels | RC1.2 / 078 | `posts.type = REEL` | Tab Reels en Inicio | **EXTENDER** — reel patrocinado (Post-Pilot prioritario si costo alto) |
| Publicar | UI central | `PublishScreen`, `PublishViewModel` | Creación orgánica usuario | **NO duplicar** — Brand Studio es flujo web separado |
| Comunidad (directorio) | D08-08 | `ComunidadScreen` | Servicios, no feed social | **NO** mezclar ads en directorio salvo CTA contextual futuro |

**Decisión de frontera:** M29 gestiona **metadatos de campaña, elegibilidad y creativos**; la **entrega visual** reutiliza filas de `posts` y/o `m19_social_posts` con flags/refs patrocinadas, no tablas paralelas de “post publicitario”.

### 7.2 Organizaciones, comercio y profesionales

| Módulo | Entidad existente | Relación M29 |
|--------|-------------------|--------------|
| **M03** | `organizations`, membresías, permisos | **REUTILIZAR** — anunciante = org + entitlement |
| **M12** | `veterinary_clinic_profiles` | **REUTILIZAR** — clínica puede activar Brand Studio sobre su org |
| **M22** | prestadores / catálogo servicios | **REUTILIZAR** — perfil comercial existente |
| **M25** | `m25_shops`, `m25_promotions` | **REUTILIZAR** tienda; promos tienda ≠ campaña Brand Studio |
| **M16/M17** | refugios, donaciones | Orgánicas gratuitas; patrocinio comercial es otro concepto |

### 7.3 Confianza, datos y pagos

| Módulo | Rol M29 |
|--------|---------|
| **M04** | Cola revisión campañas; reportes/ocultamientos; kill switch admin |
| **M07** | Auditoría acciones campaña; métricas operativas internas |
| **M21** | Reputación orgánica — **independiente** de pago Brand Studio |
| **M06** | Notificaciones campaña aprobada/rechazada/pausada (reutilizar infra) |
| **M05** | Storage creativos (imagen/video subido por anunciante) |
| **M26** | Matching/asistencia plataforma — **no** sustituye providers creativos M29 |
| **M28** | Atención clínica — **prohibido** como input de segmentación |
| **M24** | Pospuesto — suscripciones vía Mercado Pago transversal |

### 7.4 Web

El repositorio **no contiene** workspace web oficial (sin `package.json`, sin app Next.js).  
Brand Studio **depende** del futuro workspace web estratégico (D01 Anexo F). Hasta entonces: especificación y contratos backend/Android compatibles.

---

## 8. Actores

| Actor | Descripción |
|-------|-------------|
| **Anunciante** | Org/comercio/marca con entitlement Brand Studio activo |
| **Operador Brand Studio** | Miembro org con permisos `brand_studio.*` |
| **Usuario LeoVer** | Consume feed; ve etiqueta Patrocinado; oculta/reporta |
| **Moderador M04** | Aprueba/rechaza/pausa campañas y categorías de riesgo |
| **Administrador LeoVer** | Kill switch, políticas, cuotas globales IA |
| **Sistema** | Motor elegibilidad, frecuencia, scheduling, webhooks MP/OpenAI |

---

## 9. Tipos de cuenta

### 9.1 Brand / Advertiser Account (conceptual)

Entidad lógica que vincula:

- `organization_id` (M03) — **REUTILIZAR EXISTENTE**  
- `advertiser_profile` opcional — **NUEVA POSIBLE** (metadatos comerciales, categoría riesgo, billing contact)  
- `brand_studio_entitlement` — **NUEVA POSIBLE** (estado suscripción/piloto)  

### 9.2 Relación con actores existentes

| Actor existente | Activación Brand Studio | Clasificación |
|-----------------|-------------------------|---------------|
| Veterinaria M12 | Org M03 + perfil clínico M12 + add-on Brand Studio | **EXTENDER EXISTENTE** |
| Tienda M25 | Org + `m25_shops` + Brand Studio | **EXTENDER EXISTENTE** |
| Prestador M22 | Org + catálogo M22 + Brand Studio | **EXTENDER EXISTENTE** |
| Marca sin operación local | Org tipo BRAND/MARKETER + Brand Studio | **NUEVA POSIBLE** (tipo org o flag) |
| Refugio / ONG M16 | Publicación solidaria orgánica | **SIN Brand Studio obligatorio** |

**Regla:** no crear `m29_organizations` ni segundo perfil comercial si M03/M12/M25 ya identifican al actor.

---

## 10. Modelo comercial

| Decisión | Valor |
|----------|-------|
| Monetización M29 | **Suscripción Brand Studio** (y/o add-on sobre suscripción operativa existente) |
| Distribución patrocinada | **Incluida** en suscripción — no se vende por impresión |
| CPM / CPC / Boost | **Fuera de alcance** — prohibido en modelo inicial |
| Pagar más | **No** salta relevancia, frecuencia, moderación, urgencias ni privacidad |
| Piloto comercial | Puede ser **gratuito** por período acordado sin conversión automática (Maestro v1.1) |
| Precio / tiers | **PENDIENTE** — no inventar valores en esta spec |

---

## 11. Suscripción

### 11.1 BrandStudioEntitlement (conceptual)

| Campo conceptual | Descripción |
|------------------|-------------|
| `organization_id` | Anunciante |
| `status` | ACTIVE · PAST_DUE · CANCELLED · TRIAL · PILOT · SUSPENDED |
| `provider` | MERCADO_PAGO · MANUAL_TRANSFER · PILOT_GRANT |
| `external_subscription_id` | ID Mercado Pago |
| `valid_from` / `valid_until` | Ventana entitlement |
| `features` | JSON/config — formatos habilitados, cuota IA base |

### 11.2 Integración Mercado Pago Suscripciones

- Alta/baja/renovación vía **Edge Function** + webhook idempotente  
- Transferencia bancaria: flujo manual admin para empresas/casos especiales  
- Entitlement vencido → no crear/activar campañas; distribución existente se pausa  

### 11.3 Add-on vs plan base

- Veterinaria/comercio: suscripción operativa existente (futura) **+** add-on Brand Studio  
- Marca pura: cuenta Brand + suscripción Brand Studio  

---

## 12. Formatos publicitarios

Una **AdvertisingCampaign** puede producir una o más **variantes**:

| Formato | Superficie técnica | Pilot-Minimum | Post-Pilot |
|---------|-------------------|---------------|------------|
| **Post patrocinado** | `posts` o `m19_social_posts` + ref campaña | **Sí** | — |
| **Story patrocinada** | `posts.type=STORY` + sponsored ref | Evaluar | **Sí** |
| **Reel patrocinado** | `posts.type=REEL` + sponsored ref | Evaluar (material subido) | **Sí** |
| **Ayuda Concreta patrocinada** | Creativo + CTA beneficio + ref campaña | **Sí** (formato dedicado) | Variantes |

**Regla:** mismo creativo base puede generar variantes multiformato; procedencia y campaña_id unifican analítica.

---

## 13. Ayuda Concreta

Promoción patrocinada con **beneficio claro** para la comunidad, siempre identificada como comercial.

Ejemplos conceptuales:

- Descuento verificable en servicio/producto  
- Jornada de vacunación con beneficio  
- Cupón limitado  
- Evento comunitario patrocinado  
- Beneficio para adoptantes (sin comprar prioridad sobre urgencias)  

**No confundir con:**

| Concepto | Naturaleza |
|----------|------------|
| Donación a tercero (M17) | Directa, 0% comisión LeoVer |
| Aporte voluntario a LeoVer | LeoVer es destinatario |
| Ayuda Concreta Brand Studio | Publicidad/suscripción M29 |

Campos conceptuales: `benefit_type`, `benefit_summary`, `redemption_hint`, `valid_until`, `terms_url`.

---

## 14. Objetivos de campaña

Catálogo configurable (`campaign_objective_catalog` — **NUEVA POSIBLE**).

Códigos estables iniciales (labels configurables):

| Código | Label ejemplo |
|--------|---------------|
| `ACQUIRE_CUSTOMERS` | Conseguir clientes |
| `PROMOTE_SERVICE` | Promocionar servicio |
| `PROMOTE_PRODUCT` | Promocionar producto |
| `DISCOUNT` | Comunicar descuento |
| `ANNOUNCEMENT` | Anunciar novedad |
| `EVENT` | Promover evento |
| `CONCRETE_HELP` | Difundir Ayuda Concreta |
| `AWARENESS` | Awareness de marca |

No hardcodear catálogo exclusivamente en UI Compose/React.

---

## 15. Flujo de creación

### 15.1 Wizard 3 pasos (web Brand Studio)

```text
PASO 1 — ¿Qué querés lograr?
  → objective_code, nombre campaña, fechas tentativas

PASO 2 — ¿Qué querés comunicar?
  → copy, título, CTA, media, plantilla, asistencia IA
  → preview por formato

PASO 3 — ¿A quién y dónde?
  → segmentación permitida, territorio, scheduling
  → revisión final → enviar a moderación
```

### 15.2 Salidas asistidas (paso 2)

- Título, copy principal, CTA, hashtags sugeridos  
- Variantes de tono/longitud  
- Storyboard / ideas creativas (texto)  
- Propuesta visual (imagen IA o plantilla LeoVer)  
- Subtítulos sugeridos para reel  

**Regla:** anunciante **siempre revisa y confirma** antes de submit a revisión.

### 15.3 Estados previos a publicación

DRAFT → READY_FOR_REVIEW → IN_REVIEW → APPROVED → SCHEDULED → ACTIVE  
(rechazo: REJECTED; operación: PAUSED, COMPLETED, CANCELLED)

---

## 16. IA generativa

### 16.1 Proveedores (decisión actual)

| Capacidad | Proveedor inicial | Contrato conceptual |
|-----------|-------------------|---------------------|
| Texto | OpenAI | `GenerativeAIProvider` |
| Imagen | OpenAI | `ImageGenerationProvider` |
| Video generativo | **ABIERTO / FUTURO** | `VideoGenerationProvider` |

### 16.2 Arquitectura

- Implementación en **Edge Functions** — keys nunca en Android/web cliente  
- Dominio referencia `provider`, `model`, `operation_type` — **no** hardcodear modelo como invariante de negocio  
- M26 (matching, asistencia) permanece separado; M29 puede **reutilizar patrones** M07/M26 de cola/revisión, no su lógica de dominio  

### 16.3 Operaciones IA permitidas (piloto)

- Generar/refinar copy  
- Generar imagen creativa  
- Sugerir hashtags/CTA  
- Variantes multiformato (texto)  
- **Prohibido:** diagnóstico, claims terapéuticos automáticos, promesas de cura  

---

## 17. Créditos / cuotas IA

### 17.1 AIUsageLedger (conceptual — **NUEVA POSIBLE**)

| Campo | Descripción |
|-------|-------------|
| `advertiser_id` / `organization_id` | Anunciante |
| `campaign_id` | Campaña (nullable para ops cuenta) |
| `provider` | openai, … |
| `model` | Modelo usado (registro) |
| `operation_type` | TEXT_GENERATION, IMAGE_GENERATION, … |
| `tokens_in` / `tokens_out` | Texto |
| `images_generated` | Conteo |
| `video_seconds` | Futuro |
| `estimated_cost_usd` | Estimación interna |
| `created_at` | Timestamp |
| `idempotency_key` | Anti doble cobro cuota |

### 17.2 Política de cuotas (principios)

| Tipo | Política |
|------|----------|
| Texto | Cuota generosa (costo relativo bajo) |
| Imagen | Cuota controlada por entitlement |
| Video generativo | Muy restrictivo / futuro |
| Reel con material **subido** por anunciante | Mucho más amplio que video generado desde cero |

Cuotas exactas: **PENDIENTE** — configurables por entitlement/plan piloto.

No almacenar prompts completos si no es necesario para auditoría; preferir hash + metadata.

---

## 18. Creativos

### 18.1 Tipos de procedencia

1. **UPLOADED** — material aportado por anunciante (M05)  
2. **AI_GENERATED** — generado por provider  
3. **AI_ASSISTED** — edición/asistencia sobre base humana  
4. **TEMPLATE** — plantilla LeoVer parametrizada  

### 18.2 CampaignCreative (conceptual — **NUEVA POSIBLE**)

- `campaign_id`, `format`, `provenance`, `media_asset_ids`, `copy_json`, `cta`, `alt_text`, `version`, `supersedes_creative_id`  
- Regeneración IA crea nueva versión; conserva trazabilidad  

---

## 19. Segmentación

### 19.1 Señales permitidas (conceptual)

- Zona aproximada / territorio / radio permitido (PostGIS agregado — transversal)  
- Especie mascota (declarada)  
- Etapa de vida (cachorro/adulto/senior — catálogo)  
- Interés declarado en adopción  
- Adopción reciente (si política de retención lo permite)  
- Perfiles familiares / voluntarios / tránsito (flags no sensibles M02/M08)  
- Organizaciones / profesionales (tipo cuenta, no individuo)  
- Intereses declarados no sensibles  

### 19.2 Prohibido

- Ubicación exacta, dirección, teléfono, email como targeting  
- Datos clínicos M28, notas veterinarias, vacunas clínicas  
- Chats M20, contenido moderación M04, reportes  
- Pasaporte M14 privado, microchip como vector publicitario  
- Inferencias sensibles o discriminación indebida  

### 19.3 CampaignAudience (conceptual — **NUEVA POSIBLE**)

Almacena **criterios**, no listas de usuarios. Motor interno resuelve elegibilidad.

---

## 20. Privacidad

- Anunciante ve **tamaño estimado** de audiencia agregada, no identidades  
- No exportar listas de usuarios elegibles  
- Impresiones/interacciones registradas como eventos agregados o seudonimizados  
- Cumplir principios M08 (responsables), M05 (media), políticas Maestro §9  

Ejemplo permitido: “familias con perros en zona aproximada X (~N usuarios elegibles)”.  
Ejemplo prohibido: CSV de user_ids, mapa de domicilios.

---

## 21. Distribución

### 21.1 Motor de elegibilidad (conceptual)

Una campaña **ACTIVE** solo se muestra si cumple **todas**:

```text
entitlement ACTIVE
AND moderation APPROVED
AND schedule window OK
AND territory match
AND audience rules match (internal)
AND relevance score >= threshold
AND frequency caps OK
AND inventory slot available
AND not kill-switched
```

### 21.2 Inventario

“Inventory slot” = oportunidad de placement patrocinado en feed/story/reel según reglas de frecuencia — **no** garantía de N impresiones por suscripción.

### 21.3 Resiliencia

Fallo del motor M29 **no debe romper** carga del feed orgánico (ver §46).

---

## 22. Frecuencia

Principio cerrado: **la publicidad nunca domina el feed**.

Parámetros **configurables** (sin valores numéricos definitivos en spec):

| Parámetro | Descripción |
|-----------|-------------|
| `max_sponsored_per_session` | Tope por sesión Inicio |
| `max_sponsored_per_day` | Tope diario por usuario |
| `max_sponsored_per_week` | Tope semanal |
| `min_gap_between_sponsored` | Separación mínima entre patrocinados |
| `campaign_cooldown` | Cooldown por campaña/usuario |
| `creative_fatigue_threshold` | Rotación creativo |
| `max_active_campaigns_per_advertiser` | Tope campañas simultáneas |

Calibración en piloto con métricas §47.

---

## 23. Prioridades del feed

Jerarquía conceptual (no fórmula matemática cerrada):

```text
1. Seguridad / bienestar / urgencia
   (URGENT, LOST_FOUND crítico, alertas, notificaciones críticas)
2. Contenido orgánico relevante de la red
3. Contenido patrocinado elegible (etiquetado Patrocinado)
```

Una campaña paga **no desplaza** alertas críticas ni casos urgentes de perdidos/encontrados.

Implementación: pipeline de ranking Inicio consulta capa M29 **después** de ensamblar bloque prioritario orgánico.

---

## 24. Patrocinado (label)

- Etiqueta obligatoria: **“Patrocinado”** (o copy aprobado equivalente)  
- Visible en post, story, reel, Ayuda Concreta  
- Accesible: no depender solo del color (§45)  
- CTA distinguible de acciones orgánicas  
- Suscripción comercial ≠ verificación M21 ≠ recomendación LeoVer  

---

## 25. Moderación

### 25.1 Integración M04

**REUTILIZAR/EXTENDER M04** — no crear cola paralela incompatible.

- Tipos reporte existentes POST / ORGANIZATION extensibles a `SPONSORED_CAMPAIGN` si hace falta  
- Estados alineados a `moderation_status` pattern (APPROVED, PENDING, BLOCKED, HIDDEN) donde aplique a creativos publicados  

### 25.2 CampaignModerationReview (conceptual — **NUEVA POSIBLE**)

| Campo | Descripción |
|-------|-------------|
| `campaign_id` | Campaña |
| `status` | IN_REVIEW, APPROVED, REJECTED |
| `risk_category` | Del catálogo §26 |
| `reviewer_id` | Moderador M04 |
| `decision_reason_code` | Código público-safe |
| `internal_note` | Solo admin |
| `decided_at` | Timestamp |

Campaña con `requires_review=true` **no** pasa a ACTIVE sin APPROVED.

---

## 26. Categorías de riesgo

Catálogo configurable (`advertiser_risk_catalog` — **NUEVA POSIBLE**):

| Nivel | Ejemplos | Flujo |
|-------|----------|-------|
| **LOW** | Accesorios, servicios generales, eventos | Revisión estándar o auto tras reglas |
| **MEDIUM** | Servicios veterinarios promocionales, suplementos, claims bienestar | Control adicional + documentación opcional |
| **HIGH** | Medicamentos, productos registrables, afirmaciones terapéuticas | Revisión reforzada manual obligatoria |

### 26.1 Prohibiciones no desactivables

- Medicamentos ilegales/no autorizados  
- Promesas falsas de cura / diagnóstico disfrazado  
- Venta de animales  
- Maltrato/explotación  
- Productos ilegales  
- Publicidad engañosa u oculta  
- Manipulación de reseñas M21  

---

## 27. Veterinarias / productos regulados

- M12/M28 clientes potenciales de M29 para **promoción comercial**, no para explotar historial clínico  
- Mecanismo: categoría producto/servicio + riesgo + documentación respaldo + validación manual  
- Definición legal SENASA/regulatoria: **PENDIENTE** asesoramiento profesional antes de categorías reguladas en producción  
- Publicidad de medicamentos: flujo HIGH risk siempre  

---

## 28. Analytics anunciante

Métricas agregadas (sin PII individual):

| Métrica | Descripción |
|---------|-------------|
| `reach` | Usuarios únicos estimados |
| `impressions` | Impresiones patrocinadas |
| `views` | Visualizaciones (reel/story) |
| `cta_clicks` | Clicks CTA |
| `saves` | Guardados si aplica |
| `engagements` | Reacciones permitidas |
| `profile_visits` | Visitas atribuibles al perfil anunciante |
| `bookings` / `contacts` | Señal débil M23/M22 si existe |
| `ai_credits_used` | Consumo cuota IA |

**No prometer** atribución perfecta ni funnels cerrados en piloto.

`AdvertiserAnalyticsAggregate` — **NUEVA POSIBLE** (rollups diarios por campaña).

---

## 29. Analytics interno LeoVer

- Campañas activas / pausadas  
- Tasa aprobación / rechazo por categoría  
- Frecuencia media servida  
- Saturación / ocultamientos / reportes patrocinados  
- Costo IA agregado por anunciante  
- Retención anunciantes Brand Studio  
- Conversión trial/piloto → suscripción (futuro)  

Integrar M07 observabilidad — **no** mezclar con auditoría legal sensible sin permiso.

---

## 30. Reportes / feedback usuario

**REUTILIZAR** infraestructura reporte M04 + acciones feed existentes:

- Ocultar anuncio  
- Reportar (motivo codificado)  
- Marcar irrelevante (si existe patrón en feed)  
- Bloquear anunciante (si política global lo permite)  

Eventos alimentan moderación y fatiga de campaña (posible auto-pause).

---

## 31. Tutoriales

Onboarding contextual web Brand Studio (no tutorial largo obligatorio):

- Primera campaña guiada  
- Explicación objetivos y Patrocinado  
- Buenas prácticas de imagen  
- Políticas de segmentación y prohibiciones  
- Ayuda Concreta vs donación  

Estado completitud por usuario/org — **NUEVA POSIBLE** flag en entitlement o perfil.

---

## 32. Plantillas

Biblioteca configurable (`campaign_template_catalog` — **NUEVA POSIBLE**):

| Template code | Uso |
|---------------|-----|
| `SERVICE_PROMO` | Promo servicio |
| `DISCOUNT` | Descuento |
| `EVENT` | Evento |
| `VACCINATION_DAY` | Jornada vacunación |
| `NEWS` | Novedad |
| `CONCRETE_HELP` | Ayuda Concreta |
| `ADOPTION_SPONSORED` | Solo si reglas bienestar lo permiten — sin prioridad sobre urgencias |

Plantillas parametrizan copy, CTA, layout y riesgo default.

---

## 33. Relación veterinarias (M12 / M28)

| Capa | Uso M29 |
|------|---------|
| Perfil directorio M12 | Identidad pública anunciante, CTA reserva M12 |
| Portal profesional M28 | Operación clínica — **aislada** |
| Brand Studio | Campañas comerciales sobre perfil/org |

**Prohibido:** usar atenciones, vacunas clínicas M28, grants o notas para segmentación.

Suscripción operativa vet (futura) + add-on Brand Studio — conceptos separados en billing.

---

## 34. Relación comercio / prestadores (M25 / M22)

- **M25** `m25_shops` / promociones tienda: descuentos de catálogo **informativos**, sin checkout  
- **M29** campañas patrocinadas: distribución en feed social con moderación y frecuencia  
- **M22** prestadores: CTA puede enlazar reserva M23 o perfil M22  

No duplicar tienda ni catálogo; campaña referencia `shop_id` o `provider_id` existente.

---

## 35. Relación organizaciones de ayuda (M16 / M17)

- Publicaciones solidarias orgánicas: **gratuitas**, sin Brand Studio  
- Campaña patrocinada comercial: producto M29 distinto  
- Donaciones M17: flujo directo 0% comisión — ver §36  

---

## 36. Donaciones / aportes / campañas comerciales

| # | Concepto | Módulo | Comisión LeoVer |
|---|----------|--------|-----------------|
| 1 | Donación a tercero | M17 | 0% — directa |
| 2 | Aporte voluntario a LeoVer | Comercial transversal | LeoVer destinatario |
| 3 | Campaña Brand Studio | M29 | Suscripción publicitaria |

UI, contabilidad y copy **no deben mezclarse**.

---

## 37. Web Brand Studio

### 37.1 Prerrequisito

Workspace web oficial **ausente** en repo. Brand Studio se diseña como módulo del **futuro** portal web (D01 Anexo F).

### 37.2 Superficies web mínimas (Pilot)

- Dashboard anunciante  
- Wizard campaña 3 pasos  
- Biblioteca creativos / plantillas  
- Cola estado moderación  
- Analytics básica  
- Billing/entitlement (estado suscripción)  
- Configuración equipo (si M03 ya soporta roles)  

### 37.3 Stack

Next.js + React + TypeScript + Supabase client + OpenNext/Cloudflare Workers.

---

## 38. Android / iOS

### 38.1 Fuera de alcance inicial (creación)

Creación/gestión campañas **web-first** — no obligatorio en apps nativas en piloto.

### 38.2 En alcance (renderizado)

Contratos cliente para:

- Insertar item patrocinado en feed Inicio (`FeedPost` / equivalente iOS)  
- Render Story/Reel patrocinado con label  
- CTA deep link (perfil, reserva, Ayuda Concreta)  
- Ocultar / reportar  
- Registrar impresión/interacción permitida (batch/idempotente)  
- Respetar frecuencia server-side (cliente no es autoridad)  

---

## 39. Modelo conceptual de datos

| Entidad | Clasificación | Notas |
|---------|---------------|-------|
| `Organization` (M03) | **REUTILIZAR** | Base anunciante |
| `VeterinaryClinicProfile` (M12) | **REUTILIZAR** | Link org |
| `M25Shop` | **REUTILIZAR** | Link org |
| `ServiceProvider` (M22) | **REUTILIZAR** | Link org |
| `posts` / `m19_social_posts` | **EXTENDER** | `sponsored_campaign_id`, `is_sponsored`, placement |
| `AdvertiserAccount` | **NUEVA POSIBLE** | Metadatos comerciales org |
| `BrandStudioEntitlement` | **NUEVA POSIBLE** | Suscripción/piloto |
| `AdvertisingCampaign` | **NUEVA POSIBLE** | Ciclo vida campaña |
| `CampaignObjective` | **NUEVA POSIBLE** | Catálogo o FK |
| `CampaignCreative` | **NUEVA POSIBLE** | Variantes formato |
| `CampaignAudience` | **NUEVA POSIBLE** | Criterios segmentación |
| `CampaignSchedule` | **NUEVA POSIBLE** | Ventanas publicación |
| `CampaignModerationReview` | **NUEVA POSIBLE** | Decisión M04 |
| `CampaignDeliveryRule` | **NUEVA POSIBLE** | Frecuencia overrides campaña |
| `SponsoredImpression` | **NUEVA POSIBLE** | Evento agregado/batch |
| `SponsoredInteraction` | **NUEVA POSIBLE** | CTA click, hide, report |
| `AIUsageLedger` | **NUEVA POSIBLE** | Cuotas IA |
| `AdvertiserAnalyticsAggregate` | **NUEVA POSIBLE** | Rollups |
| `Post` (social) | **NO duplicar** | Extender |
| `Story` / `Reel` (tablas) | **NO crear** | Usar `posts.type` |
| `ModerationCase` (M04) | **EXTENDER** | Tipos campaña |
| `Promotion` (M25) | **NO confundir** | Promos tienda ≠ ads feed |

---

## 40. Contratos backend

### 40.1 Repositories (conceptual)

- `BrandStudioEntitlementRepository`  
- `AdvertisingCampaignRepository`  
- `CampaignCreativeRepository`  
- `CampaignModerationRepository`  
- `SponsoredDeliveryRepository`  
- `AdvertiserAnalyticsRepository`  
- `AIUsageRepository`  

Implementación Supabase: RemoteDataSource + RPC + mappers (patrón M19/M28).

### 40.2 RPC sensibles (conceptual)

| RPC | Propósito |
|-----|-----------|
| `m29_create_campaign_draft` | Crear borrador idempotente |
| `m29_update_campaign_draft` | Editar borrador |
| `m29_submit_campaign_for_review` | Submit → IN_REVIEW |
| `m29_moderate_campaign` | Aprobar/rechazar (M04 perm) |
| `m29_schedule_campaign` | Programar |
| `m29_activate_campaign` / `m29_pause_campaign` | Ciclo vida |
| `m29_resolve_feed_sponsored` | Resolver placements elegibles (server) |
| `m29_record_impressions_batch` | Impresiones idempotentes |
| `m29_record_interaction` | CTA/hide/report |
| `m29_consume_ai_quota` | Debitar cuota post-Edge Function |
| `m29_apply_entitlement_webhook` | Mercado Pago idempotente |

### 40.3 Edge Functions (conceptual)

| Function | Propósito |
|----------|-----------|
| `m29-generate-copy` | OpenAI texto |
| `m29-generate-image` | OpenAI imagen |
| `m29-mp-subscription-webhook` | Mercado Pago |
| `m29-aggregate-analytics` | Job rollup (opcional) |

---

## 41. RLS / seguridad

- Tablas M29: RLS **deny-by-default** para `authenticated`  
- Anunciante accede solo a sus campañas/org vía membership M03 + permiso `brand_studio.*`  
- Usuarios finales **no** leen tablas de campaña; solo reciben proyección feed  
- Moderadores M04: políticas específicas lectura cola  
- Analytics anunciante: agregados scoped por `organization_id`  
- `service_role`: solo server/Edge — nunca cliente  

Permisos propuestos (DEC abierta hasta implementación — seguir convención M03):

- `brand_studio.campaign.read`  
- `brand_studio.campaign.write`  
- `brand_studio.campaign.publish`  
- `brand_studio.analytics.read`  
- `brand_studio.entitlement.manage`  
- `brand_studio.moderate` (rol M04/platform)  

---

## 42. Edge Functions (detalle)

- Secretos OpenAI y Mercado Pago **solo** en Edge / server  
- Validar entitlement + cuota **antes** de llamar OpenAI  
- Timeout y fallback: error amigable, no consumir cuota en fallo previo validación  
- Logging M07 sin prompts completos por defecto  

---

## 43. Idempotencia

Proteger contra:

| Operación | Clave |
|-----------|-------|
| Crear campaña | `client_request_id` |
| Submit revisión | `campaign_id + version` |
| Activar campaña | estado + idempotency |
| Consumo crédito IA | `operation_id` |
| Impresión | `user_id + campaign_id + placement_id + window` |
| Webhook MP | `event_id` |
| Publicar creativo | `campaign_id + creative_version` |

---

## 44. Kill switch

Capacidades admin (M04/platform):

- Pausar **todas** las campañas patrocinadas (global)  
- Pausar categoría de riesgo  
- Pausar anunciante/org  
- Pausar campaña individual  
- Detener distribución sin apagar feed orgánico  

Auditoría M07 obligatoria en activaciones kill switch.

---

## 45. Estados / errores

| Código conceptual | Situación |
|-------------------|-----------|
| `M29_ENTITLEMENT_INACTIVE` | Sin suscripción |
| `M29_ENTITLEMENT_PAST_DUE` | Pago vencido |
| `M29_AI_QUOTA_EXCEEDED` | Cuota agotada |
| `M29_AI_PROVIDER_FAILURE` | OpenAI caído |
| `M29_CAMPAIGN_REJECTED` | Moderación |
| `M29_SEGMENT_NOT_ALLOWED` | Criterio prohibido |
| `M29_AUDIENCE_EMPTY` | Sin elegibles |
| `M29_SCHEDULE_INVALID` | Fechas inválidas |
| `M29_CREATIVE_INVALID` | Media/copy inválido |
| `M29_ADVERTISER_SUSPENDED` | Cuenta suspendida |
| `M29_CAMPAIGN_PAUSED_ADMIN` | Kill switch |
| `M29_WEBHOOK_DUPLICATE` | MP idempotente |

Mapear a UX anunciante (mensaje claro) vs admin (detalle interno).

---

## 46. Rendimiento

- Feed orgánico se ensambla **primero**  
- Resolución patrocinada: consulta acotada (top-K campañas elegibles) con cache TTL corto  
- Fallo M29 → feed orgánico normal sin bloqueo  
- IA **nunca** en hot path de render feed  
- Impresiones: batch/async donde sea posible  

---

## 47. Métricas de éxito M29

| Métrica | Objetivo piloto |
|---------|-----------------|
| Anunciantes activos | Adopción producto |
| Campañas creadas → publicadas | Fricción wizard |
| Tiempo creación → publicación | < objetivo producto (TBD) |
| Uso asistencia IA | Valor percibido |
| Costo IA / anunciante | Control economía |
| Tasa aprobación moderación | Calidad entrada |
| Interacción patrocinada vs ocultamientos | Relevancia |
| Retención anunciante mes+1 | Product-market fit |
| Saturación percibida (encuesta/ proxy) | UX social |
| Conversión piloto → pago | Futuro — sin auto-cobro piloto |

No optimizar exclusivamente CTR si perjudica bienestar.

---

## 48. Pilot Minimum

Alcance mínimo recomendado **vendible y usable**:

| # | Capacidad |
|---|-----------|
| 1 | Cuenta anunciante ligada a org M03 |
| 2 | Entitlement piloto/trial (manual o MP) |
| 3 | Wizard 3 pasos web (depende workspace web) |
| 4 | Objetivo campaña (catálogo) |
| 5 | Copy asistido OpenAI + revisión |
| 6 | Imagen asistida/generada + upload M05 |
| 7 | **Post patrocinado** en feed Inicio (`posts` extendido) |
| 8 | **Ayuda Concreta** patrocinada (formato dedicado) |
| 9 | Segmentación básica no sensible + territorio |
| 10 | Preview antes de submit |
| 11 | Cola moderación M04 — aprobación humana |
| 12 | Scheduling simple |
| 13 | Distribución limitada + frecuencia configurable |
| 14 | Label **Patrocinado** Android |
| 15 | Ocultar/reportar |
| 16 | Analítica básica agregada |
| 17 | Cuota IA + ledger |
| 18 | Kill switch campaña/anunciante |
| 19 | Auditoría M07 eventos core |

**Fuera Pilot-Minimum:** stories/reels patrocinados, video IA, A/B, agencias, analytics avanzado.

---

## 49. Post-Pilot

Evaluar según datos piloto:

- Stories y reels patrocinados  
- Variantes automáticas multiformato  
- A/B creativo  
- Recomendaciones inteligentes (sin targeting sensible)  
- Más plantillas verticales  
- Analytics avanzado / embudos  
- Video generativo (`VideoGenerationProvider`)  
- Atribución reservas M23  
- Campañas multi-territorio  
- Equipos/agencias con roles granulares  
- Billing avanzado / planes múltiples (PEN-009)  

---

## 50. Fuera de alcance M29 inicial

Explicitamente excluido del diseño implementable inicial:

- CPM/CPC/boost/subasta  
- Marketplace checkout M24  
- Venta de animales  
- Targeting sensible / ubicación exacta  
- Datos clínicos M28 en segmentación  
- Publicidad oculta  
- Video IA ilimitado  
- Diagnóstico o claims terapéuticos por IA  
- Pagos por impresión/click  
- Agencia enterprise completa  

---

## 51. Dependencias reales (D01 v1.2 + repo)

| Módulo | Dependencia M29 | Tipo |
|--------|-----------------|------|
| **M03** | Org, membresías, permisos | Hard |
| **M04** | Moderación, reportes, admin | Hard |
| **M05** | Media creativos | Hard |
| **M07** | Auditoría, observabilidad | Hard |
| **M19** | Placements org/comunidad | Soft (fase 2 post) |
| **`posts` + 078** | Feed Inicio REEL/STORY | Hard (piloto post patrocinado) |
| **M06** | Notificaciones estado campaña | Soft |
| **M12** | Anunciantes veterinaria | Soft |
| **M22** | Prestadores | Soft |
| **M25** | Tiendas — referencia CTA | Soft |
| **M21** | Reputación — independiente | No blocking |
| **M23** | Atribución reservas futura | Optional |
| **M26** | Patrones IA — no dominio creativo | Optional |
| **M28** | Frontera clínica — prohibición targeting | Constraint |
| **M17** | Separación donaciones | Constraint |
| **Mercado Pago Suscripciones** | Entitlement | Hard (comercial) |
| **Workspace web** | Brand Studio UI | Hard (prerrequisito) |

**No depende de:** M24 (pospuesto), duplicación social.

---

## 52. Secuencia de implementación futura

Un bloque cohesivo en **5 subfases**:

```text
A. Contratos + persistencia + permisos + entitlement
   → tablas campaña/creativo/audience/ledger
   → RLS deny-default + RPC ciclo vida borrador

B. Edge Functions IA + cuotas + plantillas
   → OpenAI texto/imagen
   → AIUsageLedger + idempotencia

C. Portal web Brand Studio (prerrequisito workspace)
   → wizard 3 pasos + preview + analytics básica

D. Moderación M04 + distribución feed Android
   → extend posts sponsored
   → motor elegibilidad + frecuencia
   → label Patrocinado + hide/report

E. Mercado Pago + analytics rollup + cierre piloto
   → webhooks
   → métricas internas
   → kill switch operativo
   → tests + docs cierre
```

No iniciar C antes de existir workspace web oficial o ADR de ubicación.

---

## 53. Criterios de aceptación

Verificables para implementación futura:

1. Org sin entitlement **no** publica campaña patrocinada.  
2. Campaña REJECTED **no** aparece en feed.  
3. Usuario ve **Patrocinado** en todo placement M29.  
4. Segmentación con criterio prohibido falla validación server-side.  
5. Anunciante **no** obtiene lista de user_ids elegibles.  
6. Cuota IA agotada bloquea generación sin debitar doble.  
7. OpenAI key ausente en cliente Android/web (grep guard).  
8. Feed carga orgánico si RPC `m29_resolve_feed_sponsored` falla.  
9. Frecuencia: usuario no recibe más patrocinados que `max_sponsored_per_session` configurado.  
10. URGENT/LOST_FOUND crítico aparece antes que patrocinado en ranking.  
11. Doble webhook MP **no** duplica entitlement.  
12. Moderador puede pausar campaña; desaparece de feed en próxima resolución.  
13. M28 clinical notes **no** aparecen en ningún RPC M29 (grep/SQL guard).  
14. Donación M17 UI **no** muestra flujos Brand Studio.  
15. Impresiones duplicadas misma ventana **no** inflan analytics (idempotencia).  

---

## 54. Definition of Done (M29)

M29 se considerará implementado (futuro) cuando:

- [ ] No duplica red social (`posts`/M19 extendidos, no parallel feed)  
- [ ] No targeting sensible ni datos M28  
- [ ] Label Patrocinado siempre visible  
- [ ] Moderación M04 integrada antes de ACTIVE  
- [ ] Cuotas IA operativas + ledger  
- [ ] Keys IA/MP solo backend  
- [ ] Distribución desacoplada del render orgánico  
- [ ] Frecuencia configurable aplicada  
- [ ] Auditoría M07 en eventos core  
- [ ] Analytics agregados sin PII  
- [ ] Kill switch probado  
- [ ] Sin CPM/CPC/Boost  
- [ ] Tests focalizados + compilación  
- [ ] `M29-cierre-implementacion.md` (futuro)  
- [ ] D01 estado actualizado  

---

## 55. Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Saturación feed | Frecuencia configurable + métricas ocultamiento |
| Pérdida confianza | Patrocinado visible; moderación; kill switch |
| Publicidad engañosa | Catálogo riesgo + revisión humana |
| Productos regulados | Flujo HIGH + asesoría legal previa |
| Fuga PII | Audiencia agregada; deny lists en RPC |
| Abuso IA / costos | Cuotas + rate limit + ledger |
| Fraude publicitario | Entitlement + moderación + suspensión anunciante |
| Dependencia OpenAI | Provider abstraído; degradación graceful |
| Contenido IA incorrecto | Humano revisa antes de publicar |
| Conflicto bienestar vs ingresos | Prioridad feed §23; métricas no solo CTR |
| Dual feed posts vs M19 | ADR placement: Inicio=`posts` piloto; M19 fase 2 |
| Sin workspace web | Prerrequisito explícito; no improvisar segundo frontend |

---

## 56. Decisiones abiertas

| ID | Tema | Estado |
|----|------|--------|
| PEN-010 | Precio Brand Studio | **ABIERTO** |
| PEN-008 | Precio suscripción comercial general | **ABIERTO** |
| PEN-009 | Estructura futura planes | **ABIERTO** |
| DEC-M29-01 | Valores numéricos frecuencia (session/day/week) | **ABIERTO** — configurables en piloto |
| DEC-M29-02 | Cuotas exactas IA texto/imagen | **ABIERTO** |
| DEC-M29-03 | Proveedor video generativo | **ABIERTO / FUTURO** |
| DEC-M29-04 | Categorías legales productos veterinarios regulados | **ABIERTO** — requiere asesoramiento |
| DEC-M29-05 | Placement piloto: solo `posts` vs también M19 en v1 | **ABIERTO** — recomendado `posts` primero |
| DEC-M29-06 | Namespace permisos `brand_studio.*` vs `advertising.*` | **ABIERTO** — preferencia `brand_studio.*` |
| DEC-M29-07 | Ubicación workspace web oficial | **ABIERTO** — repo sin Next.js hoy |

### Decisiones cerradas (heredadas Maestro/D01)

| Tema | Decisión |
|------|----------|
| Modelo económico | Suscripción incluye distribución; **no** CPM/CPC/Boost |
| IA texto/imagen inicial | OpenAI vía providers desacoplados |
| Video generativo | Abierto/futuro |
| Pago ≠ confianza | Cerrado |
| Segmentación sensible | Prohibida |
| M28 → targeting | Prohibido |

---

## 57. Fuentes de verdad

| # | Documento | Ruta |
|---|-----------|------|
| 1 | Documento Maestro v1.1 | `docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md` |
| 2 | D01 v1.2 | `docs/01-producto/D01-Modulos-y-Orden-v1.2.md` |
| 3 | Inventario Mxx | `docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md` |
| 4 | Saneamiento documental | `docs/00-startup/LeoVer-Saneamiento-Documental-v1.1.md` |
| 5 | Cierre saneamiento | `docs/00-startup/LeoVer-Cierre-Saneamiento-Documental-v1.0.md` |
| 6 | M19 cierre | `docs/03-modulos/M19-cierre-oficial.md` |
| 7 | M04 moderación | `docs/03-modulos/M04-Administracion-Moderacion-y-Soporte.md` |
| 8 | M25 cierre | `docs/03-modulos/M25-cierre-oficial.md` |
| 9 | M28 spec/cierre | `docs/03-modulos/M28-portal-veterinario-y-gestion-profesional-salud.md`, `M28-cierre-implementacion.md` |
| 10 | UX social-first | `docs/08-marca/D08-08-Arquitectura-UX-Social-First.md` |
| 11 | Migración stories/reels | `supabase/migrations/078_social_reels_stories_expires_at.sql` |
| 12 | Migración M19 | `supabase/migrations/060_m19_social_posts_and_engagement.sql` |
| 13 | Feed Android | `app/.../FeedPost.kt`, `HomeViewModel.kt` |

---

## 58. Historial

| Versión | Fecha | Cambios |
|---------|-------|---------|
| 1.0 | 2026-08-09 | Especificación inicial M29 Brand Studio y Publicidad — diseño only, sin implementación |

---

**Próximo paso recomendado (fuera de este documento):** ADR ubicación workspace web + subfase A persistencia cuando se apruebe implementación.
