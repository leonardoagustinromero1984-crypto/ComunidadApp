# M11 — Aplicación manual de migración 043 en Supabase

LeoVer. **No aplicar desde Cursor ni desde la app Android.** Apply **manual** únicamente, en proyecto de **pruebas** primero. **LOCAL ONLY** hasta autorización explícita para remoto.

## Orden exacto

```text
042_m11_shelter_operations_core.sql   (bloque 1 — prerequisito)
043_m11_shelter_campaigns_and_aid.sql (bloque 2)
```

Ejecutar cada archivo **completo**, en ese orden. No invertir. No aplicar 043 si 042 no está aplicada.

## Antes de aplicar

* Confirmar proyecto Supabase de **pruebas** (no producción).
* Confirmar que 042 (perfiles, placements, voluntarios M11) ya está aplicada.
* Baseline 001–041 consistente según entorno.
* Backup si hay datos relevantes.
* **No** ejecutar apply automático desde CI, Cursor o scripts no revisados.

## Ejecución

1. Abrir SQL Editor del proyecto de pruebas.
2. Pegar/ejecutar íntegro `042_m11_shelter_operations_core.sql` si aún no aplicada.
3. Verificar éxito sin error.
4. Pegar/ejecutar íntegro `043_m11_shelter_campaigns_and_aid.sql`.
5. Detener si cualquier paso falla.

## Verificación — tablas bloque 2

```sql
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in (
    'shelter_campaigns',
    'shelter_campaign_updates',
    'shelter_supply_requests',
    'shelter_supply_contributions'
  )
order by table_name;
```

Esperado: **4 filas**.

## Verificación — RLS

```sql
select c.relname, c.relrowsecurity
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind = 'r'
  and c.relname in (
    'shelter_campaigns',
    'shelter_campaign_updates',
    'shelter_supply_requests',
    'shelter_supply_contributions'
  )
order by c.relname;
```

Esperado: `relrowsecurity = true` en las cuatro tablas.

## Verificación — políticas

```sql
select tablename, policyname, cmd
from pg_policies
where schemaname = 'public'
  and tablename in (
    'shelter_campaigns',
    'shelter_campaign_updates',
    'shelter_supply_requests',
    'shelter_supply_contributions'
  )
order by tablename, policyname;
```

Esperado: políticas `*_select_m11` y writes denegados (`ins/upd/del` con `false`).

## Verificación — constraints e índices

```sql
select conname, conrelid::regclass as table_name
from pg_constraint
where connamespace = 'public'::regnamespace
  and conrelid::regclass::text in (
    'shelter_campaigns',
    'shelter_campaign_updates',
    'shelter_supply_requests',
    'shelter_supply_contributions'
  )
order by 2, 1;

select indexname, tablename
from pg_indexes
where schemaname = 'public'
  and tablename in (
    'shelter_campaigns',
    'shelter_campaign_updates',
    'shelter_supply_requests',
    'shelter_supply_contributions'
  )
order by tablename, indexname;
```

Comprobar checks de categoría, visibilidad, status, cantidades > 0, refs `m05://|file_asset:`.

## Verificación — funciones `m11_*`

```sql
select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname like 'm11_%'
order by p.proname;
```

Esperado (entre otras helpers internas):

- `m11_create_shelter_campaign`, `m11_update_shelter_campaign`, `m11_change_shelter_campaign_status`
- `m11_get_shelter_campaign`, `m11_list_public_shelter_campaigns`, `m11_list_shelter_campaigns`
- `m11_add_shelter_campaign_update`, `m11_list_shelter_campaign_updates`
- `m11_create_supply_request`, `m11_update_supply_request`, `m11_cancel_supply_request`
- `m11_get_supply_request`, `m11_list_public_supply_requests`, `m11_list_shelter_supply_requests`
- `m11_pledge_supply_contribution`, `m11_cancel_supply_contribution`, `m11_confirm_supply_contribution`
- `m11_record_supply_receipt`, `m11_get_supply_contribution`, `m11_list_supply_contributions`

## Verificación — permisos M03 `shelter.*`

```sql
select code, description
from public.organization_permissions
where code like 'shelter.%'
order by code;
```

Esperado incluir: `shelter.campaign.read|manage`, `shelter.supply.read|manage`, `shelter.contribution.read|manage`.

```sql
select r.code as role_code, p.code as permission_code
from public.organization_role_permissions rp
join public.organization_roles r on r.id = rp.role_id
join public.organization_permissions p on p.id = rp.permission_id
where p.code like 'shelter.%'
order by r.code, p.code;
```

## Smoke manual (post-apply, no automatizado)

Requiere org M03, refugio `ACTIVE` y usuario con permisos manage.

1. **Campaña** — `m11_create_shelter_campaign` (draft) → `m11_change_shelter_campaign_status` → `ACTIVE`.
2. **Pedido** — `m11_create_supply_request` con `p_open = true`, cantidad y unidad válidas.
3. **Pledge** — otro usuario autenticado: `m11_pledge_supply_contribution` (parcial o total).
4. **Confirmación** — manager: `m11_confirm_supply_contribution` (opcional según flujo).
5. **Recepción** — manager: `m11_record_supply_receipt` con ref `m05://` o `file_asset:`.
6. **Fulfill** — verificar pedido en status `FULFILLED` cuando `quantity_received >= quantity_requested`.
7. **Cancelación** — escenario aparte: cancelar aporte antes de recepción; cancelar campaña con pedidos abiertos.

No ejecutar este smoke desde Cursor ni CI.

## Problemas por reejecución

Las políticas usan `drop policy if exists` antes de `create policy`. Reejecución parcial de políticas no debería fallar por duplicado. **No** garantiza idempotencia completa con datos reales.

## Pendientes

* Apply remoto 043 en pruebas tras 042 validada.
* Smoke manual remoto.
* Push M06 cuando se autorice.
