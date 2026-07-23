# M11 — Aplicación manual de migración 044 (hardening de permisos)

LeoVer. **No aplicar desde Cursor ni desde la app Android.** Apply **manual** en Supabase de **pruebas** primero.

## Orden exacto

```text
043_m11_shelter_campaigns_and_aid.sql              (ya aplicada)
044_m11_harden_campaign_aid_permissions.sql        (esta correctiva)
```

No invertir. No aplicar 044 si 043 no está aplicada. Cualquier persistencia del **Bloque 3** comienza en **045**.

## Objetivo

Correctiva de seguridad **sin cambiar datos ni lógica**:

* Revocar privilegios amplios de cliente sobre las 4 tablas del Bloque 2.
* Conservar solo `SELECT` para `authenticated` (RLS sigue activo).
* Retirar `EXECUTE` de `PUBLIC` y `anon` en las 20 RPC cliente.
* Conceder `EXECUTE` solo a `authenticated` en esas RPC.
* Revocar helpers internos también a `authenticated`.

## Antes de aplicar

* Confirmar proyecto de **pruebas** (no producción).
* Confirmar 043 aplicada y smoke estructural previo.
* **No** promover a producción el Bloque 2 sin 044.

## Ejecución

1. SQL Editor → pegar íntegro `044_m11_harden_campaign_aid_permissions.sql`.
2. Ejecutar completo (`begin` … `commit`).
3. Detener ante cualquier error.
4. Reejecutable: `REVOKE`/`GRANT` son idempotentes.

## Verificación — EXECUTE_PUBLICO_O_ANON = 0

```sql
select
  p.proname as funcion,
  r.rolname as grantee,
  has_function_privilege(r.oid, p.oid, 'EXECUTE') as has_execute
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
cross join pg_roles r
where n.nspname = 'public'
  and r.rolname in ('public', 'anon')
  and p.proname in (
    'm11_create_shelter_campaign',
    'm11_update_shelter_campaign',
    'm11_change_shelter_campaign_status',
    'm11_get_shelter_campaign',
    'm11_list_public_shelter_campaigns',
    'm11_list_shelter_campaigns',
    'm11_add_shelter_campaign_update',
    'm11_list_shelter_campaign_updates',
    'm11_create_supply_request',
    'm11_update_supply_request',
    'm11_cancel_supply_request',
    'm11_get_supply_request',
    'm11_list_public_supply_requests',
    'm11_list_shelter_supply_requests',
    'm11_pledge_supply_contribution',
    'm11_cancel_supply_contribution',
    'm11_confirm_supply_contribution',
    'm11_record_supply_receipt',
    'm11_get_supply_contribution',
    'm11_list_supply_contributions'
  )
  and has_function_privilege(r.oid, p.oid, 'EXECUTE');
```

Esperado: **0 filas** (`EXECUTE_PUBLICO_O_ANON = 0`).

## Verificación — ESCRITURA_DIRECTA_CLIENTE = 0

```sql
select
  table_name,
  grantee,
  privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in (
    'shelter_campaigns',
    'shelter_campaign_updates',
    'shelter_supply_requests',
    'shelter_supply_contributions'
  )
  and grantee in ('anon', 'authenticated', 'PUBLIC')
  and privilege_type in (
    'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'
  )
order by table_name, grantee, privilege_type;
```

Esperado: **0 filas** (`ESCRITURA_DIRECTA_CLIENTE = 0`).

## Verificación — SELECT autenticado

```sql
select table_name, grantee, privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in (
    'shelter_campaigns',
    'shelter_campaign_updates',
    'shelter_supply_requests',
    'shelter_supply_contributions'
  )
  and grantee = 'authenticated'
  and privilege_type = 'SELECT'
order by table_name;
```

Esperado: **4 filas**.

## Verificación — RLS activo

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

Esperado: `relrowsecurity = true` en las cuatro.

## Verificación — helpers sin EXECUTE a authenticated

```sql
select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
join pg_roles r on r.rolname = 'authenticated'
where n.nspname = 'public'
  and p.proname in (
    '_m11_is_safe_evidence_ref',
    '_m11_require_safe_evidence',
    '_m11_recompute_supply_status',
    '_m11_sync_supply_request_totals',
    '_m11_open_supply_request_statuses'
  )
  and has_function_privilege(r.oid, p.oid, 'EXECUTE');
```

Esperado: **0 filas**.

## Resultado PASS

```text
M11 044 HARDENING PASS
EXECUTE_PUBLICO_O_ANON = 0
ESCRITURA_DIRECTA_CLIENTE = 0
SELECT autenticado conservado
RLS activo
Helpers sin EXECUTE a authenticated
```

Recién con este PASS se puede iniciar el Bloque 3 (migración **045**).

## Fuera de alcance

* No aplicar desde Cursor.
* No APK.
* No Bloque 3 hasta PASS.
* No modificar 040–043.
