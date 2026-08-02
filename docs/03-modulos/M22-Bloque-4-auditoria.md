# M22 Bloque 4 — Auditoría de paridad

**Fecha:** 2026-08-02  
**Migraciones:** 066 (base), **067 correctiva** (CHECK RADIUS)

## Matriz de paridad (Kotlin / mock / 066 / Supabase / solución)

| # | Función | Kotlin | Mock | 066 | Supabase | Brecha | Solución |
|---|---------|--------|------|-----|----------|--------|----------|
| 1 | Catálogo público | Sí | Sí | Sí | Sí | — | 066 |
| 2 | Filtro categoría/ciudad | Sí | Sí | Sí | Sí | — | 066 |
| 3 | Detalle público sanitizado | Sí | Sí | Sí | Sí | — | 066 |
| 4 | CRUD prestador propio | Sí | Sí | Sí | Sí | — | 066 |
| 5 | Sedes y cobertura | Sí | Sí | Sí | Sí | CHECK RADIUS NULL | **067** |
| 6 | Ofertas y precios | Sí | Sí | Sí | Sí | — | 066 |
| 7 | Publicar / suspender / reactivar | Sí | Sí | vía status RPC | Sí | — | 066 |
| 8 | Archivar idempotente | Sí | Sí | Sí | Sí | — | 066 |
| 9 | Permisos M03 | Sí | Parcial | Sí | Sí | org manage | 066 |
| 10 | Privacidad JSON | Sí | Sí | Sí | Sí | — | 066 |
| 11 | RLS deny direct | N/A | N/A | Sí | Sí | — | 066 |
| 12 | Anon list_catalog | N/A | N/A | Sí | Sí | — | 066 |

## Conclusión 067

**067 REQUERIDA** — el CHECK `m22_branch_coverage_chk` en 066 permitía `coverage_radius_km` NULL en filas RADIUS (evaluación NULL en `BETWEEN`). Corregido con `IS NOT NULL` explícito.

## Staging

- Entorno: `wystsapjfpdtoprlmizz` (no producción)
- 066 aplicada vía `supabase db query --linked`
- 067 aplicada vía `supabase db query --linked`
- `schema_migrations`: 064–067 registradas

## Deuda operativa (no bloqueante)

Migraciones locales **039–052** sin registro remoto en `schema_migrations`. No aplicar en bloque ni `db push` global. Objetos autoritativos **053–067** verificados consistentes. Auditar 039–052 por separado en ventana operativa futura.
