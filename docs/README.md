# Documentación Leover

Índice oficial de la documentación del repositorio.  
**Fuente de orden de módulos:** [D01 — Módulos y Orden](01-producto/D01-Modulos-y-Orden.md).

## Estructura oficial

```text
docs/
├── 00-maestro/        # Documento maestro / visión (cuando exista)
├── 01-producto/       # Producto, mapa de módulos, releases
├── 02-arquitectura/   # Arquitectura real, auditorías, cierres de etapa
├── 03-modulos/        # Especificaciones operativas por módulo (M00, M01…)
├── 04-calidad/        # Planes de lint, pruebas, CI
├── adr/               # Architecture Decision Records
└── 99-legacy/         # Inventario y referencias obsoletas (sin borrar a ciegas)
```

## Carpetas

| Carpeta | Propósito |
|---------|-----------|
| `00-maestro/` | Documento maestro integral y visión de plataforma. |
| `01-producto/` | Decisiones de producto, catálogo de módulos, orden de desarrollo. |
| `02-arquitectura/` | Cómo está construido el sistema hoy; auditorías y cierres. |
| `03-modulos/` | Especs aprobadas por módulo (alcance, DoD, backlog). |
| `04-calidad/` | Calidad, lint, pruebas, criterios de baseline. |
| `adr/` | Decisiones técnicas aceptadas; no sustituyen las specs de módulo. |
| `99-legacy/` | Inventario de material histórico; ver antes de eliminar. |

## Documentos vigentes

| Documento | Ubicación |
|-----------|-----------|
| D01 Mapa de módulos | [01-producto/D01-Modulos-y-Orden.md](01-producto/D01-Modulos-y-Orden.md) |
| M00 Fundación técnica | [03-modulos/M00-Fundacion-Tecnica.md](03-modulos/M00-Fundacion-Tecnica.md) |
| M00 Etapa 2 (gobierno) | [03-modulos/M00-Etapa-2-Documentacion-y-Gobierno.md](03-modulos/M00-Etapa-2-Documentacion-y-Gobierno.md) |
| Auditoría inicial M00 | [02-arquitectura/M00-auditoria-inicial.md](02-arquitectura/M00-auditoria-inicial.md) |
| Arquitectura inicial | [02-arquitectura/arquitectura-inicial.md](02-arquitectura/arquitectura-inicial.md) |
| ADRs | [adr/](adr/) |
| Plan de calidad M00 | [04-calidad/M00-plan-de-calidad.md](04-calidad/M00-plan-de-calidad.md) |
| Inventario legacy | [99-legacy/INVENTARIO.md](99-legacy/INVENTARIO.md) |
| Setup Supabase (operativo) | [supabase-setup.md](supabase-setup.md) |
| Push FCM | [leover-push-fcm-setup.md](leover-push-fcm-setup.md) |

## Documentos históricos o reemplazados

Permanecen en la raíz de `docs/` hasta un movimiento planificado. Ver [99-legacy/INVENTARIO.md](99-legacy/INVENTARIO.md).

| Documento | Notas |
|-----------|-------|
| `leover-roadmap-implementacion.md` | Roadmap de implementación previo a D01; usar D01 + specs de módulo. |
| `leover-documento-funcional.md` | Funcional v1.0; superado parcialmente por Documento Maestro / D01. |
| `leover-requirements.md` | Historias US-*; mantener hasta mapear a módulos. |
| `leover-phase0-implementation.md` | Plan fase 0 Firebase; **reemplazado** por Supabase. |
| `leover-firestore-model.md` | Modelo Firestore; **legado**. |
| `firebase-setup.md` | Setup Firebase backend; **legado** (FCM sí sigue vigente vía Google Services). |
| `qa-checklist-phase0.md` | QA manual fase 0; histórico útil. |
| `leover-public-api-stub.md` | Stub API pública; futuro M27. |

## Regla anti-duplicados

1. **Producto / orden:** solo `01-producto/` + Documento Maestro (cuando exista).
2. **Arquitectura vigente:** solo `02-arquitectura/` + `adr/`.
3. **Especificación de módulo:** un archivo en `03-modulos/` por módulo; no reinventar alcance en roadmaps sueltos.
4. Si un doc de raíz contradice un ADR o una spec Mxx, **gana el ADR/spec** y el doc viejo se marca en `99-legacy`.
5. **No mover** archivos masivamente hasta actualizar todos los enlaces relativos (proponer movimiento en inventario).

## Setup de desarrollo (fuera de `docs/`)

- [README del repo](../README.md)
- [CONTRIBUTING](../CONTRIBUTING.md)
- [local.properties.example](../local.properties.example)
- [supabase/.env.example](../supabase/.env.example) — secrets de Edge Functions
