# LeoVer — Changelog Documento Maestro v1.0 → v1.1

**Fecha:** 9 de agosto de 2026  
**Documento origen:** LeoVer — Documento Maestro Integral de la Startup v1.0 (14/07/2026)  
**Documento destino:** LeoVer — Documento Maestro Integral de la Startup v1.1 (09/08/2026)  
**Estado:** Aprobado como registro de transición documental  
**Ruta recomendada:** `/docs/00-startup/CHANGELOG-Maestro-v1.0-a-v1.1.md`

## 1. Propósito

Este changelog registra las decisiones que cambian, amplían o sustituyen el Documento Maestro v1.0. No reemplaza al Maestro v1.1: explica cómo se llegó a él y qué documentación derivada debe corregirse.

La regla de precedencia queda así:

1. Documento Maestro v1.1.
2. Decisiones posteriores aprobadas y registradas mediante ADR/registro equivalente.
3. D01 v1.1.
4. Especificaciones de módulos actualizadas.
5. Documentación histórica, sólo como referencia.

Cuando un documento anterior contradice una decisión de v1.1, prevalece v1.1 aunque el archivo anterior todavía no haya sido actualizado.

## 2. Resumen ejecutivo de cambios

| Área | v1.0 | v1.1 | Impacto |
| --- | --- | --- | --- |
| Modelo de plataforma | Plataforma de dos lados / ecosistema amplio | Plataforma multilateral centrada en identidad persistente de la mascota | Reencuadra producto, segmentos, crecimiento y monetización |
| Marca | Leover / uso inconsistente | **LeoVer** como capitalización oficial | Actualizar UI, documentos y assets textuales |
| Entidad central | Mascota importante dentro del ecosistema | Mascota como entidad canónica y persistente | Refuerza M08/M09 y evita duplicación por proceso |
| Roles personales | Roles relativamente estables | Identidad única + capacidades contextuales | Afecta onboarding, perfil y permisos |
| Piloto | Territorio por definir / foco previo más acotado | San Vicente + Almirante Brown | Afecta métricas, geografía y operación |
| Web | Web pública prevista | Next.js + React + TypeScript sobre Cloudflare Workers/OpenNext | Decisión técnica cerrada |
| Backend | Documentos iniciales contemplaban backend propio | Supabase como backend autoritativo; no NestJS/Prisma ni segunda DB | Supersede especificaciones técnicas históricas incompatibles |
| iOS | Futuro | Obligatorio para flujos centrales del piloto | Agrega superficie de entrega y costo Apple |
| Mapas | Pendiente | Google Maps Platform + PostGIS | Proveedor visible + motor geoespacial interno |
| Microchip | Incluido en Pasaporte | Fuera de V1/piloto | Eliminar alcance, búsqueda e integración inicial |
| Veterinaria | Perfil/servicios y conexión de salud | Portal profesional con pacientes y registros operativos; no archivo clínico oficial | Nuevo dominio funcional explícito |
| Publicidad | Promociones futuras | Brand Studio como producto comercial | Nuevo módulo y reglas de riesgo/frecuencia |
| Pagos | Pagos, comisiones, split y marketplace previstos | Suscripciones propias vía Mercado Pago; pagos entre terceros directos | Reduce alcance financiero de V1 |
| Donaciones | Monetización/flujo aún general | A terceros: transferencia directa y 0% comisión; aportes a LeoVer separados | Separa solidaridad de ingresos propios |
| Marketplace | Release previsto | Fuera de V1/piloto; futuro condicionado a demanda | M25 deja de ser transaccional en V1 |
| IA visual | IA futura general | Matching visual temprano con embeddings + pgvector + PostGIS | Puede entrar en piloto/V2 temprana |
| IA comercial | No definida | OpenAI inicial para texto/imagen de Brand Studio | Nueva integración con control de costo |
| Titularidad | Estructura no consolidada | 50% Leonardo / 50% Verónica; COMUNIDAPP explota sin adquirir título | Requiere documentos legales coherentes |
| Sociedad operadora | No consolidada | COMUNIDAPP S.A.S. | LeoVer queda como producto/plataforma |
| Presupuesto | Sin cifra consolidada | Objetivo USD 1.500; colchón hasta USD 2.000 | Habilita planificación de lanzamiento |
| Éxito piloto | Métricas generales | Umbrales cuantitativos iniciales | Define gate de expansión |

## 3. Cambios por sección del Maestro

### 3.1 Resumen ejecutivo e identidad

- Se reemplaza la noción de “plataforma de dos lados” por una plataforma multilateral con capas social, organizacional, profesional, comercial e infraestructura.
- Se formaliza la mascota como entidad persistente y eje de continuidad.
- Se fija la capitalización pública **LeoVer**.
- Se actualizan propósito, misión, visión, promesa e inclusión.
- Se establece la North Star: mascotas únicas con al menos un resultado de ayuda exitoso, confirmado, válido y no revertido por mes.

### 3.2 Usuarios, onboarding y capacidades

- Se elimina el enfoque de roles personales rígidos como mecanismo principal de onboarding.
- Una persona conserva una identidad única y puede activar capacidades: adopción, tránsito, voluntariado, transporte, difusión, pertenencia organizacional, etc.
- El onboarding pasa a preguntar **“¿Qué querés hacer primero?”** y a priorizar una primera acción útil.
- Organizaciones, refugios, establecimientos y profesionales se modelan como entidades/capacidades diferentes, no como cuentas humanas duplicadas.

### 3.3 Mascota y Pasaporte

- Se refuerza un único registro autoritativo de mascota.
- El Pasaporte se define como vista integrada sobre dominios, no como tabla monolítica ni historia clínica oficial.
- Se separan responsable, autorizado y custodio temporal.
- Se incorpora proveniencia: declarado, profesional, verificado, inferido y sistema.
- **Microchip sale de V1 y del piloto**; queda como identificador externo opcional futuro.

### 3.4 Perdidos, encontrados y respuesta territorial

- El flujo de “Encontré un animal” se formaliza con foto, condiciones, fecha/hora, geolocalización protegida y estado de resguardo.
- LeoVer puede seleccionar hasta 10 respondedores elegibles según proximidad, cobertura, disponibilidad, especie, capacidad y estado operativo.
- La primera aceptación válida debe ser atómica, idempotente y trazable; el resto se cancela.
- Se diferencian coordinador, custodio, punto proveedor y organización responsable.
- El matching visual es sugerencia y nunca prueba automática de identidad.

### 3.5 Adopción, tránsito, organizaciones y voluntariado

- La postulación de adopción se vuelve reutilizable y la decisión final permanece humana.
- La entrega debe transferir responsabilidad sobre la misma identidad de mascota, sin duplicarla.
- Tránsito se modela como capacidad/custodia temporal con disponibilidad y compatibilidad.
- Organizaciones administran equipos, animales bajo cuidado, casos y necesidades.
- Donación y voluntariado se separan conceptualmente.

### 3.6 Veterinaria y salud profesional

- Se amplía la visión de “perfil veterinario” a un **Portal Veterinario / Gestión Profesional de Salud**.
- Incluye pacientes, atenciones, vacunas, controles, procedimientos, estudios, documentos, indicaciones, seguimiento y agenda.
- El registro profesional se mantiene separado del Pasaporte.
- El profesional puede proponer datos seleccionados al Pasaporte; el responsable autorizado interviene según el tipo de dato.
- LeoVer no se presenta como archivo clínico oficial ni custodio legal primario.
- Cada profesional/establecimiento debe exportar y conservar externamente lo que deba preservar.

### 3.7 Monetización, pagos y donaciones

- Las funciones sociales y de ayuda permanecen gratuitas para personas, familias, adoptantes, voluntarios, hogares de tránsito, rescatistas y organizaciones de ayuda animal no comerciales.
- Las donaciones monetarias a terceros se realizan por transferencia directa al destinatario verificado y tienen **0% de comisión de LeoVer**.
- LeoVer puede recibir **aportes voluntarios a LeoVer** para sostener plataforma, infraestructura, operación, crecimiento e iniciativas compatibles con su finalidad.
- No habrá comisiones sobre servicios entre terceros inicialmente.
- Marketplace transaccional y split payments salen de V1/piloto.
- **Mercado Pago Suscripciones** será el proveedor inicial para suscripciones comerciales, Brand Studio y futuros add-ons propios.

### 3.8 Brand Studio y publicidad

Se incorpora Brand Studio como producto comercial independiente/transversal:

- campañas patrocinadas en posts, historias, reels y Ayudas Concretas;
- plantillas por actividad;
- copy, CTA, variantes, imágenes y guiones asistidos por IA;
- segmentación permitida y no sensible;
- moderación y aprobación proporcional al riesgo;
- distribución incluida en la suscripción;
- analítica y tutoriales;
- cuotas/créditos para IA costosa.

Se elimina la idea de un “boost” pago adicional. Pagar más no habilita prioridad sobre urgencias, datos sensibles ni bypass de moderación.

### 3.9 Publicidad: frecuencia y categorías reguladas

- Todo contenido comercial se identifica como **Patrocinado**.
- Frecuencia, cooldown, separación entre anuncios y campañas activas se gestionan por políticas configurables con límites técnicos seguros.
- Crear más campañas no garantiza más exposición.
- Las categorías se clasifican por riesgo; salud, productos veterinarios y afirmaciones terapéuticas requieren mayor control.
- Se prohíben venta de animales, medicamentos ilegales/no autorizados, publicidad oculta y afirmaciones engañosas de curación.

### 3.10 Territorio y piloto

- El piloto queda fijado en **Partido de San Vicente + Partido de Almirante Brown, Provincia de Buenos Aires**.
- Las métricas se separan por partido.
- Los algoritmos de proximidad no se cortan artificialmente en fronteras administrativas.
- El piloto comercial puede tener 90 días sin costo, sin conversión automática a pago.

### 3.11 Marca e identidad visual

- LeoVer queda como nombre público oficial.
- Se consolida manual de marca, paleta, slogan e isotipo.
- La marca denominativa se considera tramitada/presentada en clases 9, 42 y 45 según la evidencia del proyecto; el estado registral se verifica antes de afirmaciones públicas.
- La marca mixta/logo queda como opción futura y no bloquea el piloto.

### 3.12 Estructura legal e IP

- Producto/plataforma: LeoVer.
- Sociedad operadora: **COMUNIDAPP S.A.S.**
- Titularidad patrimonial prevista de LeoVer: **50% Leonardo Agustín Romero / 50% Verónica Luján Obregón**.
- COMUNIDAPP recibe por contrato facultades amplias de uso y explotación económica, sin adquirir la titularidad por ese solo acto.
- Se corrige cualquier referencia histórica que sugiera titularidad patrimonial 100% de una sola persona o cesión total de propiedad a la sociedad.
- DNDA se realizará cerca del freeze de lanzamiento con release identificable.

### 3.13 Privacidad y retención

- Se adopta minimización, finalidad, privacidad por diseño/default y acceso por propósito.
- Teléfono, domicilio y ubicación exacta nunca son públicos por defecto.
- Retención se definirá mediante matriz por categorías.
- Ubicación exacta tendrá retención especialmente corta.
- Los plazos numéricos se validarán antes del piloto público.

### 3.14 Tecnología

Se sustituyen decisiones técnicas antiguas incompatibles:

- Backend autoritativo: **Supabase** (Auth, PostgreSQL, RLS, RPC, Storage, Edge Functions y Realtime cuando aporte valor).
- No se incorpora NestJS/Prisma/segunda base como backend paralelo en esta etapa.
- Android: Kotlin + Jetpack Compose.
- iOS: Swift + SwiftUI para flujos centrales del piloto.
- Web: Next.js + React + TypeScript.
- Hosting web: **Cloudflare Workers + OpenNext**.
- Geoespacial: **PostGIS**.
- Mapas/geocodificación/rutas: **Google Maps Platform** mediante adaptadores.
- Vector search: **pgvector**.
- IA visual inicial: proveedor Google multimodal embeddings.
- IA Brand Studio inicial: OpenAI para texto e imagen, desacoplado.

### 3.15 Roadmap

Se reemplaza el roadmap anterior por:

| Release | Resultado estratégico |
| --- | --- |
| R0 | Fundación, seguridad, administración base y observabilidad |
| R1 | Identidad humana, mascota y Pasaporte base |
| R2 | Rescate, geoservicios y web pública |
| R3 | Adopción, tránsito y organizaciones |
| R4 | Comunidad y colaboración |
| R5 | Servicios confiables, agenda, veterinaria y suscripción comercial |
| R5B | Brand Studio |
| R6 | Comercio futuro, sin obligación de marketplace transaccional |
| R7 | IA/V2 avanzada |
| R8 | Integraciones y expansión regional |

### 3.16 Métricas y presupuesto

Se incorporan umbrales iniciales de piloto:

- ≥ 500 mascotas activas.
- ≥ 300 usuarios activos mensuales.
- ≥ 10 organizaciones/refugios/rescatistas activos.
- ≥ 10 actores profesionales/comerciales participantes.
- ≥ 50 casos reales de ayuda.
- ≥ 60% de casos urgentes con respuesta válida.
- ≥ 20 resultados exitosos confirmados.
- ≥ 30% de retención mensual.
- actividad real en ambos partidos.

Presupuesto objetivo de lanzamiento: **USD 1.500 equivalentes**, con colchón prudente hasta **USD 2.000**, sujeto a precios vigentes y cotizaciones profesionales.

## 4. Decisiones PEN-001 a PEN-025

| ID | Estado al cerrar v1.1 | Resultado resumido |
| --- | --- | --- |
| PEN-001 | CERRADO | COMUNIDAPP S.A.S. operadora |
| PEN-002 | CERRADO | Titularidad LeoVer 50/50 |
| PEN-003 | CERRADO | Explotación amplia por COMUNIDAPP sin transferencia de título |
| PEN-004 | CERRADO etapa actual | Marca denominativa; logo opcional futuro |
| PEN-005 | CERRADO | San Vicente + Almirante Brown |
| PEN-006 | CERRADO | Google Maps + PostGIS |
| PEN-007 | CERRADO | Cloudflare Workers/OpenNext |
| PEN-008 | ABIERTO | Precio suscripción comercial |
| PEN-009 | ABIERTO | Estructura futura de planes |
| PEN-010 | ABIERTO | Precio Brand Studio |
| PEN-011 | CERRADO | Alcance Brand Studio |
| PEN-012 | CERRADO | Distribución incluida; sin boost extra |
| PEN-013 | CERRADO criterio | Frecuencia configurable dentro de límites seguros |
| PEN-014 | CERRADO | Categorías publicitarias por riesgo |
| PEN-015 | CERRADO | Mercado Pago Suscripciones |
| PEN-016 | CERRADO criterio | Matriz de retención; plazos numéricos pendientes |
| PEN-017 | CERRADO | Vet operativo; exportación y custodia externa profesional |
| PEN-018 | CERRADO estrategia | Paquete legal + revisión profesional |
| PEN-019 | CERRADO | Presupuesto objetivo USD 1.500 / colchón USD 2.000 |
| PEN-020 | CERRADO | Umbrales iniciales del piloto |
| PEN-021 | CERRADO | iOS obligatorio en flujos centrales del piloto |
| PEN-022 | CERRADO | Microchip fuera de V1/piloto |
| PEN-023 | CERRADO | Matching visual Google + pgvector + PostGIS |
| PEN-024 | CERRADO parcial | OpenAI texto/imagen; video abierto |
| PEN-025 | CERRADO | Marketplace transaccional fuera de V1/piloto |

## 5. Decisiones históricas explícitamente supersedidas

Las siguientes ideas no deben seguir tratándose como vigentes:

- Backend NestJS/Prisma paralelo a Supabase.
- Microchip dentro del Pasaporte V1.
- Marketplace transaccional obligatorio en el roadmap inicial.
- Split payments/comisiones por servicios en V1.
- Vercel como hosting web inicial.
- iOS sólo como futuro no vinculado al piloto.
- Donaciones de terceros ingresando a caja de LeoVer o sujetas a comisión.
- Publicidad basada en boost pago separado.
- Roles personales rígidos como puerta principal de onboarding.
- LeoVer Vet como archivo clínico oficial/custodio legal permanente.
- Titularidad 100% de LeoVer por una sola persona, si aparece en documentación histórica.
- Cesión de propiedad de LeoVer a COMUNIDAPP como requisito para operar.

## 6. Documentos derivados que requieren revisión

| Documento/tipo | Acción |
| --- | --- |
| `/docs/01-producto/D01-Modulos-y-Orden.md` | **Actualizar a v1.1 inmediatamente** |
| M00 y ADR técnicos que aún mencionen NestJS/Prisma/Docker obligatorio | Marcar supersedido o corregir según implementación real |
| M01/M02 | Revisar username obligatorio, onboarding por intención y capacidades vs roles rígidos |
| M03 | Mantener organización/equipos; revisar nomenclatura refugio/establecimiento/profesional |
| M04 | Mantener moderación/soporte; incorporar publicidad/regulados cuando corresponda |
| M08/M09 | Quitar microchip V1 y reforzar responsable/custodia/proveniencia |
| M10 | Alinear Google Maps + PostGIS y ubicación protegida |
| M11 | Alinear Next.js + Cloudflare Workers/OpenNext |
| M12/M13 | Incorporar selección de respondedores y matching progresivo |
| M17 | Separar donaciones a terceros, aportes a LeoVer y voluntariado |
| M22/M23/M24 | Separar servicios/agenda de cobros propios de LeoVer |
| M25 | Quitar marketplace transaccional de V1; mantener comercio no transaccional/futuro |
| M26 | Dividir IA temprana de matching y capacidades avanzadas posteriores |
| Documentación nueva | Crear Portal Veterinario, Brand Studio y Arquitectura Web antes de implementarlos |
| Legal | Crear paquete de documentos y contrato de explotación; revisión profesional antes del piloto |

## 7. Regla de migración documental

No se debe reescribir todo el repositorio de documentación en una sola operación. El orden aprobado es:

1. adoptar Maestro v1.1;
2. registrar este changelog;
3. actualizar D01;
4. auditar documentos existentes;
5. actualizar sólo los módulos afectados antes de volver a trabajarlos;
6. crear nuevas especificaciones justo antes de implementar el dominio correspondiente.

## 8. Cierre

Con este changelog queda trazada la transición de v1.0 a v1.1. El próximo documento de ejecución es **D01 — Mapa de Módulos y Orden de Desarrollo v1.1**.
