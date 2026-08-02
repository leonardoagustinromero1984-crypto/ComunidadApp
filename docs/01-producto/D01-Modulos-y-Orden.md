# LEOVER — D01 Mapa de Módulos y Orden de Desarrollo

**Versión:** 1.0  
**Fuente superior:** Documento Maestro Integral de Leover v1.0  
**Objetivo:** indicar qué construir, en qué orden y con qué dependencias.

## Regla principal

Cursor puede resolver la implementación técnica, pero no debe decidir por sí solo el alcance del producto. Cada módulo se desarrolla con una especificación aprobada, tareas pequeñas, pruebas y criterios de aceptación.

## Superficies

- **APP:** aplicación Android.
- **WEB:** web pública responsive.
- **ORG:** portal de refugios y organizaciones.
- **PRO:** portal profesional y comercial.
- **ADMIN:** consola administrativa.
- **API:** backend y APIs.

## Reglas

1. Una sola identidad humana con múltiples roles y pertenencias.
2. La mascota es una entidad independiente de su responsable y de las publicaciones.
3. Permisos por recurso, organización y acción.
4. Backend como fuente de verdad para reglas críticas.
5. Monolito modular antes que microservicios prematuros.
6. Alertas, adopciones, campañas y eventos tienen enlace web compartible.
7. Privacidad por defecto y auditoría de cambios sensibles.
8. Procesos con estados explícitos.
9. Observabilidad, pruebas y documentación desde el comienzo.
10. IA asistida, nunca decisoria en salud, adopción o sanciones.

## Catálogo y releases

### R0 — Fundación
- **M00 Fundación técnica:** arquitectura, entornos, CI/CD, secretos, errores, feature flags.
- **M07 Auditoría, analítica y observabilidad:** eventos, logs, métricas, trazas y crash reporting.

### R1 — Acceso seguro
- **M01 Identidad y autenticación:** registro, acceso, recuperación, verificación y sesiones.
- **M02 Usuarios, roles y permisos:** perfil, preferencias, roles, pertenencias y autorización.
- **M04 Administración, moderación y soporte base:** búsqueda, suspensión, reportes y auditoría.
- **M05 Archivos, medios y documentos:** carga, variantes, permisos y borrado seguro.

### R2 — Identidad Leover
- **M03 Organizaciones y equipos:** refugios, empresas, sedes, miembros y roles internos.
- **M06 Notificaciones y preferencias:** push, email, internas, plantillas y deep links.
- **M08 Mascotas y responsables:** identidad animal, responsables, autorizados y transferencias.
- **M09 Pasaporte Leover:** identificación, salud declarada, vacunas, documentos, QR y visibilidad.
- **M10 Búsqueda, ubicación y geoservicios:** direcciones, radios, proximidad, filtros e índices.
- **M11 Web pública y enlaces compartibles:** páginas públicas, deep links y metadatos sociales.

### R3 — Reencuentro
- **M12 Mascotas perdidas y encontradas:** alertas, mapa, difusión, estados y reencuentro.
- **M13 Avistamientos y coincidencias:** pistas, fotos, ubicación, matching y confirmación.

### R4 — Adopción y rescate
- **M14 Adopciones y postulaciones:** publicación, evaluación, entrevista, entrega y seguimiento.
- **M15 Hogares de tránsito:** disponibilidad, solicitudes, alojamiento, evolución y egreso — **CIERRE OFICIAL COMPLETADO** (2026-08-01).
- **M16 Refugios y gestión de casos:** … **M17 Donaciones cerrado oficialmente** (2026-08-02); **M18 Eventos cerrado oficialmente** (2026-08-02).

### R5 — Comunidad
- **M17 Donaciones y voluntariado:** campañas, bienes, dinero, tiempo y trazabilidad — **CIERRE OFICIAL COMPLETADO** (staging `wyst****mizz`; migraciones 054–057); pagos reales **M24**.
- **M18 Eventos:** creación, cupos, inscripción, recordatorios y check-in — **CIERRE OFICIAL COMPLETADO** (staging `wyst****mizz`; migraciones 058–059 aplicadas; validación 110/110 PASS).
- **M19 Red social y contenido:** publicaciones, comentarios, reacciones, feed y reportes — **CIERRE OFICIAL COMPLETADO** (staging `wyst****mizz`; migraciones 060–061; validación 105/105 PASS).
- **M20 Mensajería:** conversaciones, adjuntos, bloqueos, contexto — **cerrado** (Bloques 1–4; 062+063 staging; validación 125/125 + smoke 25/25).
- **M21 Reputación, verificaciones y reseñas:** identidad, matrículas, reseñas transaccionales y apelaciones — **CIERRE OFICIAL COMPLETADO** (staging `wyst****mizz`; migraciones 064–065; validación 130/130 + smoke 25/25).

### R6 — Servicios
- **M22 Prestadores y catálogo de servicios:** perfil, sedes, categorías, cobertura y precios — **CIERRE OFICIAL COMPLETADO** (staging `wyst****mizz`; migraciones 066–067; validación 75/75 + smoke 25/25).
- **M23 Agenda y reservas:** disponibilidad, reserva, confirmación, cambios y asistencia — **CIERRE OFICIAL COMPLETADO** (staging `wyst****mizz`; migraciones 068–069; validación 110/110 + smoke 25/25; SHA cierre `f293c97`).
- **M24 Pagos, comisiones y suscripciones:** pago, split, reembolso, conciliación y planes — **POSPUESTO por decisión de producto**; preauditoría documentada; implementación **no iniciada**; pagos no definidos.

### R7 — Comercio
- **M25 Marketplace, pedidos y promociones:** catálogo, stock, carrito, pedido, envío y devolución — **CIERRE OFICIAL COMPLETADO** (staging `wyst****mizz`; migraciones 070–071; validación 120/120 + smoke 25/25; sin pagos; M24 pospuesto).

### R8 — Escala inteligente
- **M26 Inteligencia artificial:** matching, duplicados, asistencia stub, recomendaciones, jobs, revisión humana — **cerrado oficialmente**; migraciones 072–074 aplicadas en staging; validación 125/125 + smoke 25/25; sin pagos ni motor externo.
- **M27 Integraciones y API pública:** webhooks, OAuth, límites, sandbox y contratos versionados — **siguiente módulo; no iniciado**.

## Camino crítico

M00/M07 → M01/M02 → M03/M05 → M08 → M09 → M10/M11 → M12/M13 → M14/M15/M16 → M17–M21 → M22–M23 → **M25** → **M26** → M27. **M24 pospuesto** (pagos no definidos).

## Contenido obligatorio de cada especificación de módulo

1. Objetivo y valor.
2. Alcance incluido y excluido.
3. Actores y permisos.
4. Flujos principales, alternativos y errores.
5. Estados y reglas numeradas.
6. Pantallas y navegación.
7. Modelo de datos y migraciones.
8. API, eventos y errores.
9. Seguridad, privacidad, moderación y auditoría.
10. Métricas y telemetría.
11. Pruebas.
12. Backlog pequeño y ordenado para Cursor.
13. Definition of Done.

## Contrato de trabajo con Cursor

- Leer D01 y la especificación del módulo antes de modificar código.
- Analizar y reutilizar patrones existentes.
- No implementar módulos futuros ni inventar reglas.
- Proponer plan y archivos antes de programar.
- Trabajar en cambios pequeños, compilables y testeables.
- Validar permisos y reglas críticas en backend.
- Agregar pruebas, auditoría y telemetría en el mismo cambio.
- No introducir dependencias sin justificarlo.
- Detenerse ante contradicciones y registrar una pregunta o ADR.

## Próximo módulo

**M26 — Inteligencia artificial.** Matching visual, duplicados, asistencia y recomendaciones evaluadas **sin cobros reales** (M24 pospuesto). Bloque 3 no iniciado.

## Módulos posteriores pendientes de definición operativa

- **M24 Pagos** — pospuesto; preauditoría en `M24-auditoria-preliminar.md`.
- **M27 Integraciones y API pública** — catálogo D01; no iniciado.

## Nota de implementación técnica (LeoVer)

El catálogo de producto de este documento (M00–M27) se preserva. En el track Android/arquitectura del repo:

- **M11 técnico (Refugios)** ya cerrado — no altera el significado de producto de **M11 Web pública** ni de **M16 Refugios**.
- **M12 técnico (Veterinarias)** — Bloque 1 local/fake — prepara dominio de clínicas/profesionales previo a prestadores (**M22**); **no reemplaza** el **M12 Mascotas perdidas y encontradas** del catálogo de producto.
- **M13 técnico (Avistamientos y coincidencias)** — Bloques 1–4 **CERRADOS LOCALMENTE**; cierre técnico local completado. 048/049 aplicadas (estructural 13/13 y 14/14 PASS). Smoke funcional M13 **PENDIENTE EXTERNO**. Cierre oficial M13 **PENDIENTE**. M12 smoke **PENDIENTE EXTERNO**; M12 cierre oficial **PENDIENTE**.
- **Producto M14 Adopciones** = cubierto por **M09 técnico**. **M14 técnico** = Pasaporte e identidad verificable. **M14 BLOQUE 1 CERRADO LOCALMENTE**. **M14 BLOQUE 2 CERRADO LOCALMENTE** (050 y 051 **aplicadas remotamente**; validación estructural **18/18 PASS**). **M14 BLOQUE 3 CERRADO LOCALMENTE**. **M14 BLOQUE 4 CERRADO LOCALMENTE**; **M14 CIERRE TÉCNICO LOCAL COMPLETADO** (migración 052 creada, **PENDIENTE DE APLICACIÓN REMOTA**; validación estructural 052 **PENDIENTE**; smoke M14 **PENDIENTE EXTERNO**; cierre oficial M14 **PENDIENTE**; GitHub Android CI **PENDIENTE**). **M15 BLOQUE 4 CERRADO LOCALMENTE**; **M15 CIERRE TÉCNICO LOCAL COMPLETADO**; **M15 VALIDACIÓN FUNCIONAL MANUAL PASS**; **M15 SMOKE FUNCIONAL REMOTO PASS**; **M15 CIERRE OFICIAL COMPLETADO** (2026-08-01) — métricas agregadas sin PII, privacidad final, M06 fallback honesto, dashboard `m15/operations`; **M10 ES LA PERSISTENCIA AUTORITATIVA DE M15**; **M08 CONSERVA RESPONSABILIDAD PRINCIPAL**; compilación Kotlin PASS (reutilizada `0cbf73d`); pruebas automáticas **NO EJECUTADAS**; legacy M10 **preservado**; **M16 CIERRE OFICIAL COMPLETADO** (2026-08-01); migración 053 aplicada staging; **M17 BLOQUE 1 IMPLEMENTADO LOCALMENTE** (2026-08-01): campañas solidarias mock, `M17PrivacySanitizer`, rutas `m17/campaigns/*`, separación pagos M24; migración 054 **pendiente Bloque 2**; SQL **no aplicado**; pagos reales **no habilitados**; tests `M17DonationFoundationTest` PASS; **M18 BLOQUE 1 IMPLEMENTADO LOCALMENTE**; **M18 BLOQUE 2 IMPLEMENTADO** (migración 058 creada, **no aplicada**; `SupabaseM18EventRepository`; mock conservado); **M18 Bloque 3 no iniciado**.

## Prompt base

```text
Actuá como arquitecto y desarrollador senior del proyecto Leover.

Antes de modificar código:
1. Leé /docs/01-producto/D01-Modulos-y-Orden.md.
2. Leé la especificación del módulo que te indique.
3. Analizá la estructura y patrones existentes del repositorio.
4. Indicá dependencias, riesgos, archivos a crear/modificar y plan por etapas.
5. No programes módulos futuros ni inventes reglas ausentes.

Durante la implementación:
- Trabajá en cambios pequeños, compilables y testeables.
- Respetá las capas, contratos y límites de dominio.
- Validá permisos y reglas críticas en backend.
- Agregá estados de carga, vacío, error y éxito.
- Agregá pruebas y telemetría en el mismo cambio.
- No agregues dependencias o servicios sin justificarlo.

Al terminar cada etapa:
- Ejecutá compilación y pruebas.
- Enumerá archivos modificados.
- Explicá decisiones y deuda pendiente.
- Confirmá qué criterios de aceptación quedaron cumplidos.

MÓDULO ACTUAL: [CÓDIGO Y NOMBRE]
TAREA ACTUAL: [TAREA ESPECÍFICA]
No avances al siguiente módulo hasta recibir indicación.
```
