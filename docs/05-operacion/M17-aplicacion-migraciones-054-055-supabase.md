# Operación — migraciones 054 y 055 (M17 donaciones extendidas)

**LeoVer** · Supabase staging/pruebas · **no producción**.

## Archivos canónicos

```text
supabase/migrations/054_m17_donation_campaigns_and_contributions.sql
supabase/migrations/055_m17_in_kind_volunteering_and_transparency.sql
```

## Estado actual (post Bloque 5 / cierre M17)

```text
054 — APLICADA EN STAGING NO PRODUCTIVO
055 — APLICADA EN STAGING NO PRODUCTIVO
056 — APLICADA EN STAGING (fix moderador)
057 — APLICADA EN STAGING (fix listado voluntariado)
```

Registro en `supabase_migrations.schema_migrations`: 054, 055, 056, 057.

Validación: `scripts/ops/m17_remote_validation_054_055.sql` — **120/120 PASS**.

## Prerrequisitos

- Migraciones 001–053 aplicadas en orden en entorno **no productivo**.
- Permisos M03 `donation.view` / `donation.manage` disponibles (insertados en 054).
- Operador confirma proyecto Supabase de staging/pruebas.

## Procedimiento

1. Confirmar entorno **no productivo** (no producción).
2. Abrir SQL Editor en Supabase Dashboard.
3. Ejecutar **054 completo** una sola vez.
4. Verificar tablas M17 campañas:

```sql
select table_name from information_schema.tables
where table_schema = 'public' and table_name like 'm17_%'
order by 1;
```

5. Ejecutar **055 completo** una sola vez.
6. Verificar tablas extendidas:

```sql
select count(*) from information_schema.tables
where table_schema = 'public' and table_name in (
  'm17_in_kind_needs', 'm17_in_kind_pledges',
  'm17_volunteer_opportunities', 'm17_volunteer_applications',
  'm17_campaign_transparency_reports', 'm17_fund_usage_items',
  'm17_transparency_milestones'
);
-- esperado: 7
```

7. Verificar RPCs públicas:

```sql
select proname from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and proname like 'm17_%public%'
order by 1;
```

8. Smoke RLS (usuario autenticado sin permiso org → fallo esperado en mutación org).
9. Activar `useSupabase=true` en build de prueba y validar listados públicos desde app.

## Orden obligatorio

```text
054 → 055
```

No aplicar 055 antes de 054 (dependencias `_m17_*` helpers).

## Límites

- No crear migración 056 desde esta tarea.
- No resetear base ni borrar datos de producción.
- No usar service role desde Android.
- Pagos reales permanecen en M24.

## Validación remota pendiente

Tras aplicación en staging:

- Listar necesidades publicadas vía `m17_list_public_in_kind_needs`
- Crear pledge autenticado vía `m17_create_in_kind_pledge`
- Postular voluntariado vía `m17_submit_volunteer_application`
- Consultar transparencia vía `m17_get_public_campaign_transparency`
- Confirmar que DRAFT no aparece en superficies públicas

## Rollback

No hay rollback automático. Ante fallo parcial, restaurar desde backup de staging o re-ejecutar en entorno limpio.

## Referencias

- `docs/03-modulos/M17-Bloque-4-auditoria.md`
- `docs/03-modulos/M17-Bloque-4-validacion.md`
