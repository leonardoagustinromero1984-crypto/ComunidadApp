# Operación — migración 058 (M18 eventos comunitarios)

**LeoVer** · Supabase staging/pruebas · **no producción**.

> **Nota:** La operación completa incluye también **059**. Ver `M18-aplicacion-migraciones-058-059-supabase.md`.

## Archivo canónico

```text
supabase/migrations/058_m18_community_events_and_registrations.sql
```

## Estado actual (post cierre M18)

```text
058 — APLICADA EN STAGING
059 — APLICADA EN STAGING (requerida para operaciones/atendencia)
Validación remota — 110/110 PASS
```

## Script de validación

```text
scripts/ops/m18_remote_validation_058_059.sql
```

## Orden obligatorio

```text
001–057 → 058 → 059
```

## Referencias

- `docs/03-modulos/M18-cierre-oficial.md`
- `docs/05-operacion/M18-aplicacion-migraciones-058-059-supabase.md`
