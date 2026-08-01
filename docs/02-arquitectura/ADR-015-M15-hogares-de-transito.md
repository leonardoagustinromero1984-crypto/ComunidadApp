# ADR-015 — M15 Hogares de tránsito (track técnico)

## Estado

```text
APROBADO
```

## Contexto

El catálogo de producto D01 define:

```text
M15 — Hogares de tránsito: disponibilidad, solicitudes, alojamiento, evolución y egreso
```

En el repositorio existe implementación técnica previa bajo numeración **M10** (`Foster*`, migraciones 040/041, UI `foster/*`). Esa implementación está **completa técnicamente** según `docs/03-modulos/M10-hogares-de-transito.md`, pero la numeración de producto espera **M15**.

A diferencia de M14 (producto Adopciones ≠ pasaporte técnico), **M15 producto y M15 técnico comparten el mismo alcance funcional**.

## Decisión

El módulo del track técnico Android será:

```text
M15 — Hogares de tránsito
```

Equivalencia:

```text
Producto M15 Hogares de tránsito = M15 técnico
Legacy M10 = preservado; migración progresiva hacia prefijo M15 en bloques posteriores
```

Bloque 1 establece dominio, fakes, UI `m15/*` y contratos locales **sin SQL** ni Supabase real.

## Consecuencias

- Se alinea numeración con D01.
- No se elimina ni reescribe M10/Foster legacy en Bloque 1.
- Bloque 2+ podrá unificar persistencia (053+) y reconciliar con 040/041 cuando se apruebe.

## Restricciones Bloque 1

- Sin migración 053.
- Sin pagos, chat, reputación ni IA.
- Dirección privada nunca en listados públicos.
- M08 conserva autoridad de responsabilidad; tránsito otorga custodia temporal en bloques posteriores.

## Fuente canónica

```text
docs/03-modulos/M15-hogares-de-transito.md
```
