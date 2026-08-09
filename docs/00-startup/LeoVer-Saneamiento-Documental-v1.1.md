# LeoVer — Saneamiento Documental v1.1

**Fecha:** 9 de agosto de 2026  
**Tipo:** Pasada posterior a aprobación de Maestro v1.1, D01 v1.2 e Inventario Mxx v1.0  
**Alcance:** Solo documentación en `docs/` — **sin cambios en código, SQL, migraciones ni tests**

---

## 1. Fuentes de verdad usadas

| Orden | Documento | Ubicación real |
|-------|-----------|----------------|
| 1 | Documento Maestro v1.1 | `docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md` |
| 2 | D01 v1.2 | `docs/01-producto/D01-Modulos-y-Orden-v1.2.md` |
| 3 | Inventario real Mxx v1.0 | `docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md` |
| 4 | Matriz de vigencia v1.0 | **No encontrada** en `docs/00-startup/` |
| 5 | ADR-013, ADR-014, ADR-015 | `docs/02-arquitectura/` |
| 6 | Cierres técnicos M00–M27 | `docs/03-modulos/`, `docs/02-arquitectura/`, `docs/06-release/` |

**Nota:** Las rutas `docs/00-startup/LeoVer-Documento-Maestro-v1.1.md` y `LeoVer-Auditoria-Documental-y-Matriz-de-Vigencia-v1.0.md` indicadas en gobierno no existen; el Maestro vigente está en `docs/00-maestro/`.

---

## 2. Metodología

1. Búsqueda focalizada en `docs/` por patrones: M09–M14, D01, Pasaporte, Geoservicios, Web, iOS, Vercel, microchip, pagos, Brand Studio, piloto, COMUNIDAPP, etc.
2. Clasificación por documento: **VIGENTE**, **ACTUALIZAR**, **HISTÓRICO**, **SUPERSEDIDO**.
3. Modificación **solo** de documentos VIGENTE con contradicciones claras y bajo riesgo.
4. Documentos históricos (cierres, prompts Cursor, ADR, auditorías de bloque): **preservados**; nota de supersesión solo cuando la confusión era alta sin reescribir el contenido original.

---

## 3. Documentos inspeccionados

| Categoría | Cantidad aprox. | Acción |
|-----------|-----------------|--------|
| Total archivos `.md` en `docs/` | **503** | Barrido por patrones |
| Coincidencias M09/M10/M11/M12/M14 incorrectas | **~45 archivos** | Revisión contextual |
| Referencias a `D01-Modulos-y-Orden.md` (v1.0) | **~85 archivos** | Mayoría histórica — no modificados masivamente |
| Documentos vigentes (índices, specs activas, Maestro, RC1 matriz) | **~35** | Lectura detallada |
| ADR y cierres | **9 ADR + ~74 cierres** | Preservados (contenido histórico válido) |

---

## 4. Documentos modificados

| # | Archivo | Clasificación | Corrección aplicada |
|---|---------|---------------|---------------------|
| 1 | `docs/README.md` | VIGENTE → ACTUALIZAR | Índice apunta a D01 v1.2, Maestro v1.1, inventario Mxx; carpeta `00-startup/` |
| 2 | `docs/00-maestro/README.md` | VIGENTE → ACTUALIZAR | Enlaces a Maestro v1.1, D01 v1.2, inventario |
| 3 | `docs/01-producto/D01-LeoVer-Modulos-y-Orden-v1.1.md` | SUPERSEDIDO | Banner de supersesión → D01 v1.2 |
| 4 | `docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md` | VIGENTE → ACTUALIZAR | Referencias D01 v1.1 → v1.2 (3 líneas); plan transición actualizado |
| 5 | `docs/03-modulos/M08-mascotas-y-responsables.md` | VIGENTE → ACTUALIZAR | Tabla “Fuera de alcance” y párrafo fundamento: M09=adopciones, M14=pasaporte, M10=tránsito, lost_found+M13 |
| 6 | `docs/03-modulos/M13-avistamientos-y-coincidencias.md` | VIGENTE → ACTUALIZAR | §2 roadmap alineado a D01 v1.2 |
| 7 | `docs/03-modulos/M12-veterinarias.md` | VIGENTE → ACTUALIZAR | Nota D01 → numeración vigente M12/M13/M28 |
| 8 | `docs/03-modulos/M15-hogares-de-transito.md` | VIGENTE → ACTUALIZAR | Fuente D01 v1.2 |
| 9 | `docs/03-modulos/M13-auditoria-inicial.md` | HISTÓRICO | Fuente canónica → D01 v1.2 |
| 10 | `docs/03-modulos/M16-auditoria-inicial.md` | HISTÓRICO | Nota de vigencia (M10≠geoservicios) sin alterar tabla original |
| 11 | `docs/02-arquitectura/arquitectura-inicial.md` | VIGENTE → ACTUALIZAR | Enlace D01 v1.2 |
| 12 | `docs/02-arquitectura/M00-cierre-final.md` | VIGENTE → ACTUALIZAR | Enlace D01 v1.2 |
| 13 | `docs/02-arquitectura/M17-arquitectura-donaciones.md` | VIGENTE → ACTUALIZAR | Dependencia M10 (ubicación) → geografía transversal |
| 14 | `docs/06-release/RC1-matriz-modulos.md` | VIGENTE → ACTUALIZAR | Nota D01 v1.2; M10 renombrado “Hogares tránsito (persistencia)” |

**Total modificados:** **14**

---

## 5. Documentos no modificados (históricos preservados)

| Grupo | Ejemplos | Motivo |
|-------|----------|--------|
| D01 v1.0 | `D01-Modulos-y-Orden.md` | Ya tiene banner SUPERSEDIDO; cuerpo histórico conservado |
| D01 v1.1 | `D01-LeoVer-Modulos-y-Orden-v1.1.md` | Superseded; contenido colisionado conservado como evidencia |
| ADR | ADR-013/014/015 | Decisiones históricas **correctas**; no reescribir |
| Prompts Cursor M12–M15 | `M14-Bloque-*-Prompt*.md`, etc. | Contexto de ejecución pasada; “Producto M14 Adopciones = M09” es válido en ADR |
| Cierres oficiales | `M16-cierre-oficial.md`, `M27-cierre-oficial.md`, etc. | Registro factual de cierre |
| Roadmap legacy | `leover-roadmap-implementacion.md` | Marcado histórico en `docs/README.md` |
| Fase 0 / Firebase | `leover-phase0-implementation.md`, `firebase-setup.md` | Legado explícito |
| Specs M14 pasaporte | `M14-pasaporte-identidad-verificable.md` | Ya documenta remapeo ADR-014 correctamente |
| Legal borradores | `DLEG-*` | Requieren revisión jurídica humana; no auto-editar |
| ~70 docs con link a D01 v1.0 en “leer antes” | Etapas M01–M04, prompts M13/M14 | Referencias operativas históricas; actualización masiva diferida |

**Total preservados intencionalmente:** **~489** (503 − 14 modificados)

---

## 6. Contradicciones encontradas

| ID | Contradicción | Documentos afectados | Severidad |
|----|---------------|----------------------|-----------|
| C01 | M09 = Pasaporte (v1.0/v1.1) vs repo = Adopciones | D01 v1.0, D01 v1.1, M08 spec | **Alta** — corregida en vigentes |
| C02 | M10 = Geoservicios vs repo = Tránsito SQL | D01 v1.0/v1.1, M08 spec, RC1 matriz, M17 arq. | **Alta** — corregida en vigentes |
| C03 | M11 = Web vs repo = Refugios ops | D01 v1.0/v1.1 | **Alta** — D01 v1.2 ya corrige; v1.0/v1.1 preservados |
| C04 | M12 = Perdidos vs repo = Veterinarias | D01 v1.0/v1.1, M13 §2 antiguo | **Alta** — corregida en vigentes |
| C05 | M14 = Adopciones vs repo = Pasaporte | D01 v1.0/v1.1, M08 spec | **Alta** — corregida en vigentes |
| C06 | Índice `docs/README.md` apuntaba a D01 v1.0 | README | **Media** — corregida |
| C07 | Maestro v1.1 decía “actualizar D01 v1.1” | Maestro §plan | **Media** — corregida |
| C08 | M10 “ubicación” en dependencias M17 | M17 arquitectura | **Baja** — corregida |
| C09 | RC1 matriz “Ubicación/foster base” | RC1 matriz | **Baja** — corregida |
| C10 | ~85 refs operativas a D01 v1.0 en specs/prompts | M01–M27 etapas | **Baja** — diferida (histórico) |

**Decisiones estratégicas verificadas en Maestro v1.1 / D01 v1.2 (sin contradicción vigente):**

| Tema | Estado en fuentes vigentes |
|------|---------------------------|
| Piloto San Vicente + Almirante Brown | OK en Maestro v1.1 y D01 v1.2 |
| iOS obligatorio piloto (paridad crítica) | OK en Maestro v1.1 y D01 v1.2 |
| Web: Cloudflare Workers + OpenNext (no Vercel inicial) | OK en Maestro v1.1 §16 |
| Mapas: Google + PostGIS transversal | OK en D01 v1.2 Anexo D/F |
| Microchip fuera V1/piloto (producto) | OK en Maestro/D01 v1.2; **tensión** con campo técnico M08 SQL — ver §8 |
| IA visual: Google + pgvector + PostGIS | OK en D01 v1.2 Anexo D |
| Brand Studio M29: OpenAI, sin CPM/CPC/boost | OK en D01 v1.2 §14 y Anexo E |
| Pagos: Mercado Pago Suscripciones; M24 pospuesto | OK en D01 v1.2 §16 |
| Donaciones 0% comisión; aportes LeoVer separados | OK en Maestro §7.9 y D01 v1.2 |
| Marketplace fuera V1 | OK en Maestro y D01 v1.2 |
| M28 portal vet (no historia clínica oficial) | OK en D01 v1.2 §13 |
| COMUNIDAPP / 50-50 cofundadores | Parcial en DLEG borradores — revisión humana |

---

## 7. Corrección aplicada (resumen)

- **Índices y gobierno:** `docs/README.md`, `00-maestro/README.md` → D01 v1.2 como mapa técnico único vigente.
- **Supersesión explícita:** D01 v1.1 banner; D01 v1.0 ya tenía banner previo.
- **Maestro v1.1:** referencias actualizadas a D01 v1.2 aprobado (sin reescribir estrategia).
- **Specs activas M08/M12/M13/M15:** numeración M09–M16 alineada al repo.
- **Arquitectura vigente:** enlaces D01 v1.2; M17 sin M10 geográfico.
- **RC1 matriz:** nomenclatura M10 corregida + nota de autoridad.

**No se realizó:** reemplazo masivo de `D01-Modulos-y-Orden.md` en ~85 prompts/etapas históricas.

---

## 8. Archivos que requieren revisión humana

| Prioridad | Documento / tema | Motivo |
|-----------|------------------|--------|
| Alta | `docs/01-producto/D01-Modulos-y-Orden.md` (cuerpo v1.0) | Banner superseded pero catálogo R2–R4 aún muestra M09 Pasaporte, M10 geo, etc. — ¿archivar a `99-legacy/`? |
| Alta | ~85 specs/prompts con “Leé D01-Modulos-y-Orden.md” | Actualización masiva de referencias a v1.2 en lote controlado |
| Media | `docs/07-legal/DLEG-*` | Formalizar **COMUNIDAPP S.A.S.** operadora vs titularidad 50/50 LeoVer; contrato explotación |
| Media | `docs/03-modulos/M08-mascotas-y-responsables.md` §microchip | Campo SQL/implementado vs política Maestro “microchip fuera V1” — aclarar alcance técnico vs producto |
| Media | `docs/leover-roadmap-implementacion.md` | Roadmap jul-2026 pre-D01; considerar mover a `99-legacy/` |
| Baja | Crear `LeoVer-Auditoria-Documental-y-Matriz-de-Vigencia-v1.0.md` | Referenciado en gobierno pero ausente |
| Baja | Symlink o copia Maestro en `00-startup/` | Rutas de gobierno vs rutas reales |
| Baja | Especificaciones M28/M29 | Pendientes de redacción (fuera de alcance saneamiento) |

---

## 9. Pendientes que siguen abiertos

| ID | Tema | Estado |
|----|------|--------|
| PEN-008 | Precio suscripción comercial | OPEN |
| PEN-009 | Estructura planes | OPEN |
| PEN-010 | Precio Brand Studio | OPEN |
| — | Retención numérica definitiva | OPEN |
| — | Proveedor video generativo | OPEN |
| — | M24 pagos in-platform | POSPUESTO |
| — | Matriz vigencia documental v1.0 | NO EXISTE |
| — | Actualización masiva refs D01 v1.0 → v1.2 en prompts | DIFERIDO |

---

## 10. Validación final

| Criterio | Resultado |
|----------|-----------|
| D01 v1.2 es el único mapa técnico vigente en índices | **SÍ** |
| Documentos vigentes corregidos no reasignan M09–M14 incorrectamente | **SÍ** (en los 14 modificados) |
| Documentos históricos conservados | **SÍ** |
| ADR históricos no reescritos | **SÍ** |
| iOS obligatorio piloto (Maestro/D01 v1.2) | **SÍ** |
| Cloudflare hosting inicial (Maestro) | **SÍ** |
| M28/M29 reservados (D01 v1.2) | **SÍ** |
| Marketplace fuera V1 | **SÍ** |
| Microchip fuera V1 (producto) | **SÍ** en Maestro/D01; tensión M08 SQL pendiente humano |
| Brand Studio sin Boost/CPM/CPC | **SÍ** |
| Donaciones terceros 0% | **SÍ** |
| Portal Vet no = historia clínica oficial | **SÍ** |
| Código modificado | **NO** |
| SQL modificado | **NO** |
| Tests ejecutados | **NO** |
| Commit / push | **NO** |

---

## 11. Control

| Campo | Valor |
|-------|-------|
| Versión informe | 1.1 |
| Fecha | 2026-08-09 |
| Autor | Pasada saneamiento post-D01 v1.2 |
| Próximo paso sugerido | Lote 2: actualizar refs D01 en specs activas; revisión legal COMUNIDAPP |
