# LeoVer — D01 Mapa de Módulos y Orden de Desarrollo

> **SUPERSEDIDO PARA PLANIFICACIÓN NUEVA.** Consultar [`D01-Modulos-y-Orden-v1.2.md`](D01-Modulos-y-Orden-v1.2.md). Esta versión contiene colisiones de numeración M09–M14 con el repositorio; se conserva por trazabilidad histórica.

**Versión:** 1.1  
**Fecha:** 9 de agosto de 2026  
**Fuente superior:** LeoVer — Documento Maestro Integral de la Startup v1.1  
**Estado:** Guía oficial de secuencia y dependencias  
**Ruta de repositorio:** `/docs/01-producto/D01-Modulos-y-Orden.md`

## 0. Regla de precedencia

Este documento traduce el Maestro v1.1 a módulos técnicos y releases. No reemplaza al Maestro.

Orden de autoridad:

1. Documento Maestro v1.1.
2. Decisiones posteriores aprobadas/ADR.
3. D01 v1.1.
4. Especificación vigente del módulo.
5. Código y pruebas.
6. Documentación histórica.

Si una especificación antigua contradice al Maestro v1.1 o a este D01, debe corregirse antes de seguir desarrollando ese dominio.

**Actualizar D01 no significa reimplementar módulos ya construidos.** Primero se audita lo existente, se conserva lo válido y sólo se corrigen incompatibilidades reales.

## 1. Principio rector

Pensar todo el ecosistema desde el inicio no significa programarlo todo junto. Cada bloque debe dejar un resultado funcional y comprobable sin introducir implementaciones futuras “por conveniencia”.

Cursor puede resolver la implementación técnica, pero no gobierna el alcance del producto ni inventa reglas, roles, estados, permisos, precios o integraciones.

## 2. Superficies oficiales

| Código | Superficie | Finalidad |
| --- | --- | --- |
| AND | Android | Cliente móvil principal en Kotlin + Jetpack Compose |
| IOS | iOS | Cliente Swift + SwiftUI con paridad de flujos centrales del piloto |
| WEB | Web pública | SEO, páginas compartibles, rescate, adopción, perfiles y campañas |
| ORG | Portal organizaciones | Equipos, animales bajo cuidado, casos, tránsito, adopciones y necesidades |
| VET | Portal veterinario | Pacientes, agenda, atenciones, registros y conexión autorizada al Pasaporte |
| PRO | Portal profesional | Servicios, agenda, disponibilidad y reputación |
| BRAND | Brand Studio | Campañas patrocinadas, IA, distribución y analítica |
| ADMIN | Consola administrativa | Configuración, moderación, soporte, verificación, riesgo y auditoría |
| DATA | Supabase/backend | Auth, PostgreSQL, RLS, RPC, Storage, eventos e integraciones |

## 3. Reglas de arquitectura y producto

| ID | Regla | Aplicación |
| --- | --- | --- |
| R01 | Una identidad humana | Una persona no duplica cuentas por rol; activa capacidades y pertenencias |
| R02 | Mascota canónica | Mascota independiente de publicación, pérdida, adopción, organización o atención |
| R03 | Responsable ≠ custodio | Custodia temporal no transfiere responsabilidad automáticamente |
| R04 | Backend autoritativo | Reglas críticas y permisos se validan en Supabase/RLS/RPC, no sólo en UI |
| R05 | Deny-by-default | RLS y operaciones privilegiadas aplican mínimo privilegio |
| R06 | Web compartible | Pérdidas, hallazgos, adopciones, organizaciones, eventos y campañas tienen vista pública segura cuando corresponde |
| R07 | Privacidad por defecto | Teléfono, domicilio y ubicación exacta nunca son públicos por defecto |
| R08 | Estados explícitos | Procesos con estados y transiciones, no inferencias frágiles |
| R09 | Proveniencia | Declarado, profesional, verificado, inferido y sistema se distinguen |
| R10 | Auditoría | Acciones sensibles registran actor, fecha, motivo y contexto necesario |
| R11 | IA asistida | IA sugiere/prioriza; no decide identidad, adopción, diagnóstico, sanción ni otra decisión sensible final |
| R12 | Proveedores desacoplados | Mapas, IA, pagos, email y otras integraciones detrás de contratos/adaptadores |
| R13 | Configurable con límites | Catálogos y parámetros operativos son configurables; reglas fundacionales no |
| R14 | Social esencial gratis | Ayuda social, adopción, tránsito y organizaciones no comerciales no se bloquean por pago |
| R15 | Sin marketplace V1 | Servicios/productos pagan directo entre partes; LeoVer cobra sólo sus productos propios inicialmente |
| R16 | Urgencia sobre publicidad | Seguridad/bienestar/urgencias siempre prevalecen sobre contenido patrocinado |
| R17 | Exportabilidad profesional | LeoVer Vet facilita exportar; el establecimiento conserva externamente lo que deba preservar |
| R18 | No microchip V1 | Microchip fuera de V1/piloto y sin búsqueda/integración inicial |

## 4. Stack técnico aprobado

| Capa | Decisión |
| --- | --- |
| Android | Kotlin + Jetpack Compose + Material 3 + Navigation + MVVM/Flow + repositorios |
| iOS | Swift + SwiftUI |
| Web | Next.js + React + TypeScript |
| Hosting web | Cloudflare Workers + OpenNext; GitHub/Workers Builds + previews |
| Backend | Supabase Auth + PostgreSQL + RLS + RPC + Storage + Edge Functions; Realtime cuando aporte valor |
| Geoespacial | PostGIS |
| Mapas | Google Maps Platform mediante adaptadores |
| Vector search | pgvector |
| Push | Firebase Cloud Messaging, preferentemente con patrón outbox/eventos |
| Suscripciones propias | Mercado Pago Suscripciones |
| IA visual | Google multimodal embeddings iniciales + pgvector/PostGIS |
| IA Brand Studio | OpenAI inicial para texto/imagen; proveedor de video abierto |

**Supersedido:** cualquier instrucción histórica que obligue a NestJS/Prisma, una segunda base de datos o Docker/Supabase local como requisito permanente del desarrollo cotidiano.

## 5. Catálogo oficial de módulos

Los identificadores M00–M27 se preservan para no romper documentación ni código existente. Se agregan M28 y M29 para capacidades que v1.0 no separaba correctamente.

### M00 — Fundación técnica y entornos

**Propósito:** base Android/iOS/web/backend, configuración, entornos, secretos, CI/CD, errores y feature flags.  
**Dependencias:** ninguna.  
**Superficies:** AND, IOS, WEB, DATA, ADMIN.  
**Activación estratégica:** R0.  
**Regla v1.1:** reutilizar lo existente; Supabase es backend oficial.

### M01 — Identidad y autenticación

Registro, username público obligatorio/único, login, verificación, recuperación, sesiones, consentimientos y eliminación segura.  
**Depende de:** M00.  
**Superficies:** AND, IOS, WEB, DATA.  
**Release:** R1.

### M02 — Usuarios, capacidades y permisos

Perfil, preferencias, privacidad, capacidades contextuales, pertenencias, roles técnicos/organizacionales y permisos por recurso.  
**Depende de:** M01.  
**Superficies:** AND, IOS, WEB, ORG, VET, PRO, BRAND, ADMIN, DATA.  
**Release:** R1.

### M03 — Organizaciones y equipos

Organizaciones, refugios, sedes, membresías, invitaciones, roles internos, verificación y contexto organizacional.  
**Depende de:** M02.  
**Superficies:** AND, IOS, ORG, ADMIN, DATA.  
**Release de activación:** R3. La fundación técnica puede existir antes.

### M04 — Administración, moderación, soporte y verificación

Reportes, casos, medidas, apelaciones, soporte, verificación, evidencia, políticas administrativas y auditoría operativa.  
**Depende de:** M01, M02; evoluciona con todos los módulos.  
**Superficies:** ADMIN, DATA.  
**Release:** R0 transversal.

### M05 — Archivos, medios y documentos

Fotos, videos, documentos, metadata, validaciones, permisos, borrado, variantes y acceso seguro mediante Supabase Storage.  
**Depende de:** M00, M01, M02.  
**Superficies:** todas las que manejan contenido.  
**Release:** R1 transversal.

### M06 — Notificaciones y preferencias

Push, email, internas, plantillas, preferencias, deduplicación, quiet hours, reintentos y deep links.  
**Depende de:** M01, M02, M07.  
**Superficies:** AND, IOS, WEB, DATA, ADMIN.  
**Release:** R2 transversal.

### M07 — Auditoría, analítica y observabilidad

Eventos de producto, logs, métricas, crash reporting, auditoría, correlación, retención y tableros.  
**Depende de:** M00.  
**Superficies:** todas.  
**Release:** R0 transversal.

### M08 — Mascotas, responsables y custodia

Identidad canónica, fotos/características, responsables, autorizados, transferencias, custodia temporal, duplicados y estados de ciclo de vida.  
**Depende de:** M02, M05, M07.  
**Superficies:** AND, IOS, WEB, ORG, VET, DATA.  
**Release:** R1.

### M09 — Pasaporte LeoVer

Vista integrada de identidad e información relevante, salud declarada, documentos, QR, permisos y proveniencia.  
**Depende de:** M08, M05, M07.  
**Superficies:** AND, IOS, WEB, ORG, VET, DATA.  
**Release:** R1.  
**Fuera de alcance:** microchip en V1; historia clínica oficial.

### M10 — Búsqueda, ubicación y geoservicios

Direcciones, coordenadas protegidas, radios, proximidad, filtros, PostGIS, geocodificación/rutas con Google Maps y ranking territorial.  
**Depende de:** M00, M02, M07.  
**Superficies:** AND, IOS, WEB, ORG, VET, PRO, DATA.  
**Release:** R2.

### M11 — Plataforma web pública y enlaces compartibles

Shell web Next.js, páginas públicas, SEO, metadata social, deep links, QR, continuidad con app y bases reutilizables para portales posteriores.  
**Depende de:** M00, M05, M08, M09, M10.  
**Superficies:** WEB.  
**Release:** R2.  
**Hosting:** Cloudflare Workers/OpenNext.

### M12 — Perdidos, encontrados y red de respuesta

Alertas, pérdida/hallazgo, zona protegida, difusión, estados, selección de hasta 10 respondedores elegibles, aceptación atómica, coordinación y resultado.  
**Depende de:** M06, M08, M10, M11, M04.  
**Superficies:** AND, IOS, WEB, ORG, ADMIN, DATA.  
**Release:** R2.

### M13 — Avistamientos y coincidencias

Pistas, fotos, ubicación protegida, descartes, candidatos, confirmación humana y conexión con matching visual.  
**Depende de:** M12, M05, M10, M06, M26.  
**Superficies:** AND, IOS, WEB, DATA.  
**Release:** R2.

### M14 — Adopciones y postulaciones

Publicación, perfil reutilizable, requisitos, postulación, entrevistas, decisión humana, entrega, transferencia trazable y seguimiento.  
**Depende de:** M03, M05, M06, M08, M11, M04, M07.  
**Superficies:** AND, IOS, WEB, ORG, ADMIN, DATA.  
**Release:** R3.

### M15 — Hogares de tránsito

Capacidad, disponibilidad, compatibilidad, solicitudes, custodia temporal, gastos registrables, seguimiento y egreso.  
**Depende de:** M02, M06, M08, M10, M16.  
**Superficies:** AND, IOS, WEB, ORG, DATA.  
**Release:** R3.

### M16 — Gestión de casos y animales bajo cuidado

Casos, animales bajo cuidado, coordinadores, custodios, necesidades, tareas, estados, resultados y operación de organizaciones/rescatistas.  
**Depende de:** M03, M06, M08, M10, M04, M07.  
**Superficies:** ORG, AND, IOS, ADMIN, DATA.  
**Release:** R3.

### M17 — Donaciones, necesidades y voluntariado

Necesidades de dinero/bienes/tiempo/transporte/servicios, voluntariado, trazabilidad y resultados.  
**Depende de:** M03, M06, M11, M16, M04.  
**Superficies:** AND, IOS, WEB, ORG, DATA.  
**Release:** R4.  
**Regla monetaria:** dinero a terceros por transferencia directa verificada; 0% comisión. Los aportes a LeoVer son un flujo separado.

### M18 — Eventos y campañas comunitarias

Eventos, cupos, inscripción, recordatorios, check-in, páginas compartibles y resultados.  
**Depende de:** M03, M06, M10, M11, M05.  
**Superficies:** AND, IOS, WEB, ORG, DATA.  
**Release:** R4.

### M19 — Comunidad y contenido social

Feed, posts, historias, reels, fotos/videos, comentarios, reacciones, ayuda, educación, humor/ternura, reportes y ranking responsable.  
**Depende de:** M02, M05, M06, M04, M07.  
**Superficies:** AND, IOS, WEB, DATA.  
**Release:** R4.

### M20 — Mensajería, conversaciones y contexto de caso

Chat, adjuntos, bloqueos, reportes, retención, notificaciones y conversación asociada a casos/servicios sin mezclar todos los conceptos.  
**Depende de:** M01, M02, M04, M05, M06, M07.  
**Superficies:** AND, IOS, WEB, ORG, VET, PRO, DATA.  
**Release:** R4.

### M21 — Reputación, verificaciones y reseñas

Hechos verificables, reseñas ligadas a interacciones reales, distintivos, matrículas/verificaciones cuando corresponda, sanciones y apelaciones.  
**Depende de:** M02, M03, M04, M07.  
**Superficies:** AND, IOS, WEB, ORG, VET, PRO, BRAND, ADMIN, DATA.  
**Release:** R4–R5 transversal.

### M22 — Prestadores y catálogo de servicios

Veterinarias como establecimientos/oferta pública, paseadores, cuidadores, guarderías, transporte, educadores y otros prestadores; perfiles, sedes, cobertura, servicios y condiciones.  
**Depende de:** M02, M03, M05, M10, M21.  
**Superficies:** AND, IOS, WEB, PRO, VET, DATA.  
**Release:** R5.

### M23 — Agenda, disponibilidad y reservas

Franjas, capacidad, agenda, reserva, confirmación, reprogramación, cancelación, asistencia, recordatorios, idempotencia y prevención de doble booking.  
**Depende de:** M06, M22, M07.  
**Superficies:** AND, IOS, WEB, PRO, VET, DATA.  
**Release:** R5.

### M24 — Suscripciones comerciales y cobros propios de LeoVer

Planes/entitlements, períodos, estados de suscripción, Mercado Pago, webhooks, conciliación, facturación operativa y transferencia manual alternativa.  
**Depende de:** M03, M04, M07.  
**Superficies:** PRO, VET, BRAND, ADMIN, DATA.  
**Release:** R5.  
**Fuera de alcance:** split payments entre terceros, comisión por servicios y donaciones de terceros.

### M25 — Comercio y catálogo comercial

Tiendas/emprendimientos, productos, promociones, CTA/contacto y presencia comercial.  
**Depende de:** M03, M05, M10, M21.  
**Superficies:** AND, IOS, WEB, PRO, DATA.  
**Release:** R6.  
**Fuera de V1/piloto:** carrito, checkout, settlement, marketplace transaccional y venta de animales.

### M26 — Inteligencia artificial y matching

Matching visual perdido/encontrado, embeddings, duplicados, asistencia de bajo riesgo, evaluación, límites de costo y proveedores desacoplados.  
**Depende de:** datos maduros de cada dominio; M05, M07, M10.  
**Superficies:** transversal.  
**Activación:** slice de matching en R2; capacidades avanzadas en R7.  
**Proveedor visual inicial:** Google multimodal embeddings + pgvector/PostGIS.

### M27 — Integraciones y API/adaptadores

Webhooks, adaptadores externos, límites, claves, sandbox, interoperabilidad futura y API pública cuando dominios estén estables.  
**Depende de:** M00, M04, M07 y dominio específico.  
**Superficies:** DATA/ADMIN.  
**Release:** R8, aunque adaptadores internos se usan desde etapas previas.

### M28 — Portal Veterinario y Gestión Profesional de Salud

Pacientes, atenciones, vacunas, controles, procedimientos, estudios, documentos, indicaciones, seguimientos, recordatorios, equipo profesional, autoría y correcciones trazables; propuestas seleccionadas al Pasaporte y exportación.  
**Depende de:** M03, M05, M08, M09, M21, M22, M23, M07.  
**Superficies:** VET, WEB, DATA; integración móvil autorizada.  
**Release:** R5.  
**Regla:** herramienta operativa, no archivo clínico oficial ni custodio legal primario.

### M29 — Brand Studio y Publicidad

Cuenta publicitaria, campañas, creatividades, plantillas, posts/historias/reels patrocinados, Ayudas Concretas, segmentación permitida, moderación por riesgo, distribución, frecuencia, analítica, tutoriales y créditos de IA.  
**Depende de:** M04, M05, M07, M19, M21, M24, M26.  
**Superficies:** BRAND, WEB, ADMIN, DATA; placements en AND/IOS/WEB.  
**Release:** R5B.  
**Regla:** distribución incluida; sin boost pago adicional.

## 6. Releases estratégicos y gate de habilitación

| Release | Módulos/cortes principales | Resultado de salida |
| --- | --- | --- |
| R0 — Fundación | M00, M04 base, M07 | Entornos, seguridad, administración base, observabilidad |
| R1 — Identidad | M01, M02, M05, M08, M09 | Cuenta + mascota + Pasaporte base utilizables |
| R2 — Rescate + web | M06, M10, M11, M12, M13, M26 slice | Pérdidas/hallazgos compartibles, geografía y respuesta local |
| R3 — Adopción/organizaciones | M03 activación, M14, M15, M16 | Operación institucional, adopción y tránsito |
| R4 — Comunidad | M17, M18, M19, M20, M21 | Colaboración, contenido, eventos, reputación y mensajería |
| R5 — Servicios confiables | M22, M23, M24, M28 | Prestadores, agenda, veterinaria y suscripción comercial |
| R5B — Brand Studio | M29 + M26 creativo | Publicidad asistida, distribución y analítica |
| R6 — Comercio futuro | M25 | Catálogo comercial ampliado; transaccional sólo si se aprueba después |
| R7 — IA/V2 | M26 avanzado | Matching/automatizaciones y recomendaciones de bajo riesgo |
| R8 — Integraciones/regional | M27 | Interoperabilidad y expansión |

### 6.1 Gate iOS del piloto

El piloto público no se considera listo sólo con Android. Deben existir en iOS, como mínimo:

- registro/login y onboarding;
- perfil de mascota y Pasaporte básico;
- perdidos/encontrados;
- adopción y tránsito;
- organizaciones/casos esenciales;
- comunidad básica;
- notificaciones esenciales;
- mapas/ubicación cuando corresponda;
- privacidad, permisos y deep links necesarios.

Brand Studio, administración avanzada y gestión profesional completa pueden permanecer web-first durante el piloto si el recorrido público/usuario no queda bloqueado.

## 7. Dependencias críticas

```text
M00 + M07 + M04(base)
        ↓
M01 → M02 → M05
        ↓
       M08 → M09
        ↓
M10 → M11 → M12 → M13
        │              ↑
        │            M26(slice)
        ↓
M03 → M16 → M14/M15
        ↓
M17/M18/M19/M20/M21
        ↓
M22 → M23 → M28
        ↓
       M24
        ↓
       M29

M25 y M27 quedan fuera del camino crítico del piloto.
```

### 7.1 Dependencias transversales

- M04 acompaña nuevos tipos de reporte, verificación, publicidad y soporte.
- M05 centraliza archivos; ningún dominio crea storage paralelo.
- M06 centraliza notificaciones; dominios emiten eventos/intenciones.
- M07 separa analítica, auditoría y observabilidad.
- M10 centraliza geografía/proximidad.
- M21 centraliza reputación/verificación, sin confundir popularidad con confianza.
- M26 expone capacidades de IA por contratos; ningún dominio queda atado a un proveedor concreto.
- M24 sólo gestiona dinero que LeoVer cobra por productos propios.

## 8. Definición mínima de especificación de módulo

Antes de implementar un módulo/corte nuevo, su especificación debe contener:

1. objetivo y valor;
2. alcance incluido/excluido;
3. actores, capacidades y permisos;
4. flujos principal/alternativos/errores/recuperación;
5. estados, invariantes y reglas numeradas;
6. pantallas/navegación/accesibilidad;
7. modelo de datos, migraciones e índices;
8. contratos RPC/Edge/eventos/adaptadores;
9. seguridad, privacidad, abuso, moderación y auditoría;
10. métricas/telemetría;
11. pruebas focalizadas y aceptación;
12. backlog para Cursor;
13. Definition of Done;
14. impactos en Android/iOS/Web/portales;
15. migración o compatibilidad con código existente.

## 9. Contrato de trabajo actual con Cursor

### 9.1 Antes de modificar

- Leer Maestro v1.1, D01 v1.1 y la especificación vigente del módulo.
- Auditar el repositorio y reutilizar lo existente.
- Detectar contradicciones documentales antes de programar.
- Enumerar archivos a crear/modificar, dependencias y riesgos.
- No inventar campos, roles, estados, endpoints/RPC, permisos ni proveedores.

### 9.2 Durante el desarrollo

- Trabajar sobre la rama activa acordada; por defecto no crear una rama por etapa ni commits intermedios innecesarios.
- Hacer cambios pequeños internamente, pero consolidar el bloque completo antes del commit/push final.
- Usar pruebas focalizadas durante el desarrollo.
- Evitar emulador, Supabase local y quality gates pesados cuando no aporten a la tarea.
- No repetir lint/JaCoCo/build completo después de cada cambio menor.
- No generar APK salvo que se solicite o sea necesaria para una validación concreta.
- Mantener RLS/RPC como autoridad de reglas sensibles.
- No mezclar módulos futuros por conveniencia.

### 9.3 Cierre del bloque

- Ejecutar una validación final razonable del bloque completo.
- Enumerar archivos modificados y decisiones tomadas.
- Registrar deuda/pending real sin inventar “completado”.
- Actualizar documentación afectada.
- Realizar un único commit/push del bloque cuando corresponda al flujo acordado.

## 10. Compatibilidad con módulos ya implementados

M00–M04 y cualquier otro código existente no se invalidan por este D01. Antes de reabrirlos:

1. comparar implementación real contra v1.1;
2. conservar contratos válidos;
3. identificar únicamente incompatibilidades concretas;
4. corregirlas en el módulo donde corresponda;
5. no reescribir arquitectura estable sólo para coincidir con una descripción ideal.

Ejemplos de incompatibilidades documentales a vigilar: NestJS/Prisma histórico, microchip V1, roles personales rígidos, split payments/marketplace obligatorio, Vercel inicial, ausencia de iOS de piloto y nomenclatura de Brand Studio/Vet Portal.

## 11. Pendientes que NO bloquean el desarrollo actual

- PEN-008: precio definitivo de suscripción comercial.
- PEN-009: futura estructura de planes.
- PEN-010: precio definitivo de Brand Studio.
- Valores numéricos finales de frecuencia publicitaria.
- Plazos numéricos definitivos de retención.
- Proveedor de video generativo.
- Marketplace transaccional futuro.

Estos puntos deben resolverse antes del momento de negocio/legal correspondiente, no anticiparse con valores inventados en código.

## 12. Pendientes que SÍ bloquean un piloto público

- paquete legal revisado profesionalmente;
- Política de Privacidad/Términos/versionado de consentimientos;
- matriz de retención con plazos aplicables;
- controles de seguridad y permisos validados;
- iOS con paridad de flujos centrales;
- web pública y deep links críticos;
- staging/producción configurados y observables;
- procesos de moderación/soporte mínimos;
- condiciones del piloto y responsabilidades comunicadas;
- presupuesto/medios operativos suficientes para sostener el período de prueba.

## 13. Métricas gate del piloto

Se usarán como umbrales iniciales orientativos para decidir expansión:

| Indicador | Umbral |
| --- | --- |
| Mascotas activas | ≥ 500 |
| Usuarios activos mensuales | ≥ 300 |
| Organizaciones/refugios/rescatistas activos | ≥ 10 |
| Profesionales/comerciales participantes | ≥ 10 |
| Casos reales de ayuda | ≥ 50 |
| Casos urgentes con respuesta válida | ≥ 60% |
| Resultados exitosos confirmados | ≥ 20 |
| Retención mensual | ≥ 30% |
| Territorio | Actividad real en San Vicente y Almirante Brown, separada por partido |

## 14. Próxima acción documental

Después de aprobar este D01 v1.1:

1. auditar los documentos existentes y marcarlos como **VIGENTE / ACTUALIZAR / SUPERSEDIDO / HISTÓRICO**;
2. actualizar sólo las especificaciones que afecten el siguiente bloque real de desarrollo;
3. crear antes de su implementación las especificaciones separadas de **Arquitectura Web**, **Portal Veterinario** y **Brand Studio**;
4. no generar documentación masiva preventiva sin necesidad inmediata.

## 15. Prompt maestro actualizado para Cursor

```text
Actuá como arquitecto y desarrollador senior del proyecto LeoVer.

FUENTES OBLIGATORIAS
1. Leé /docs/00-startup/LeoVer-Documento-Maestro-v1.1.md.
2. Leé /docs/01-producto/D01-Modulos-y-Orden.md.
3. Leé la especificación vigente del módulo/bloque indicado.
4. Revisá ADR y cierres del módulo que sigan vigentes.

ANTES DE MODIFICAR
- Auditá el repositorio y reutilizá lo existente.
- Indicá alcance, dependencias, riesgos y archivos a tocar.
- Señalá contradicciones entre documentación y código antes de resolverlas.
- No inventes reglas de producto, campos, estados, permisos, roles ni proveedores.

DURANTE EL DESARROLLO
- Respetá Supabase como backend autoritativo y RLS/RPC para reglas sensibles.
- No introduzcas NestJS/Prisma/otra base/backend paralelo sin una decisión nueva aprobada.
- Trabajá sobre la rama activa acordada; no crees ramas por etapa ni commits intermedios salvo indicación expresa.
- Usá pruebas focalizadas mientras desarrollás.
- Evitá emulador, Supabase local y quality gates completos repetidos cuando no sean necesarios.
- No generes APK salvo pedido o validación específica.
- No programes módulos futuros por conveniencia.

AL CERRAR EL BLOQUE
- Ejecutá una validación final razonable.
- Enumerá archivos modificados.
- Explicá decisiones y deuda pendiente real.
- Actualizá documentación afectada.
- Prepará un único commit/push del bloque cuando corresponda.

MÓDULO/BLOQUE ACTUAL: [CÓDIGO Y NOMBRE]
OBJETIVO: [OBJETIVO CONCRETO]

No avances a otro módulo sin cerrar primero el bloque actual o recibir una indicación explícita.
```

## Aprobación

Al aprobarse, esta versión sustituye D01 v1.0 como mapa de módulos y orden de desarrollo. D01 v1.0 se conserva como histórico y no debe utilizarse para resolver contradicciones actuales.
