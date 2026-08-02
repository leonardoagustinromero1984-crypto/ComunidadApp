# Operación — migración 064 (M21 reputación)

**LeoVer** · Supabase staging/pruebas · **no producción**.

## Archivo

```text
supabase/migrations/064_m21_reputation_reviews_and_verifications.sql
```

## Estado

```text
064 — CREADA, NO APLICADA
```

## Prerrequisitos

Migraciones 001–063 aplicadas en orden.

## Procedimiento (cuando se autorice)

1. Confirmar entorno no productivo.
2. Aplicar 064 vía `supabase db query --linked -f supabase/migrations/064_m21_reputation_reviews_and_verifications.sql`
3. `supabase migration repair 064 --status applied --linked`

## Referencias

- `docs/03-modulos/M21-Bloque-2-auditoria.md`
