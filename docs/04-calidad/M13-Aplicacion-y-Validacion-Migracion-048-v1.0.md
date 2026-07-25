# M13 — Aplicación y validación de la migración 048

## Estado previo

```text
M13 BLOQUE 2 CERRADO LOCALMENTE
MIGRACIÓN 048 PENDIENTE DE APLICACIÓN REMOTA
SHA: 34a551a52aa1f2b14bc54201826544d9385637f5
```

## 1. Aplicación manual

Abrir:

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp\supabase\migrations\048_m13_sightings_and_match_candidates.sql
```

Copiar todo el archivo y ejecutarlo una sola vez en el SQL Editor del Supabase de pruebas.

Reglas:

- si devuelve `Success`, no volver a ejecutarlo;
- no modificar 048 después de aplicada;
- ante error, guardar el mensaje exacto y detenerse;
- una corrección SQL posterior debe comenzar en 049.

## 2. Validación estructural consolidada

Ejecutar después de aplicar 048:

```sql
with expected_tables(table_name) as (
    values
        ('lost_found_sighting_details'),
        ('lost_found_match_candidates'),
        ('lost_found_match_decisions'),
        ('lost_found_match_status_history')
),
expected_rpcs(function_name) as (
    values
        ('m13_create_sighting'),
        ('m13_update_my_sighting'),
        ('m13_withdraw_my_sighting'),
        ('m13_get_sighting'),
        ('m13_list_public_sightings'),
        ('m13_list_my_sightings'),
        ('m13_list_managed_sightings'),
        ('m13_generate_match_candidates_for_sighting'),
        ('m13_generate_match_candidates_for_case'),
        ('m13_list_case_match_candidates'),
        ('m13_list_sighting_match_candidates'),
        ('m13_get_match_candidate'),
        ('m13_recalculate_match_candidate')
),
rpc_catalog as (
    select
        p.oid,
        p.proname,
        p.prosecdef,
        p.proconfig
    from pg_proc p
    join pg_namespace n
      on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
          select function_name
          from expected_rpcs
      )
),
checks as (
    select
        1 as orden,
        'TABLAS_PRESENTES' as verificacion,
        4::bigint as esperado,
        count(*)::bigint as obtenido
    from expected_tables t
    where to_regclass('public.' || t.table_name) is not null

    union all

    select
        2,
        'RLS_ACTIVO',
        4,
        count(*)
    from expected_tables t
    join pg_class c
      on c.relname = t.table_name
    join pg_namespace n
      on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relkind in ('r', 'p')
      and c.relrowsecurity = true

    union all

    select
        3,
        'TABLAS_CON_POLICIES',
        4,
        count(*)
    from expected_tables t
    where exists (
        select 1
        from pg_policies p
        where p.schemaname = 'public'
          and p.tablename = t.table_name
    )

    union all

    select
        4,
        'PRIMARY_KEYS',
        4,
        count(*)
    from expected_tables t
    where exists (
        select 1
        from pg_constraint c
        where c.conrelid = to_regclass('public.' || t.table_name)
          and c.contype = 'p'
    )

    union all

    select
        5,
        'RPC_PRESENTES',
        13,
        count(distinct proname)
    from rpc_catalog

    union all

    select
        6,
        'RPC_SECURITY_DEFINER',
        13,
        count(distinct proname)
    from rpc_catalog
    where prosecdef = true

    union all

    select
        7,
        'RPC_SEARCH_PATH_SEGURO',
        13,
        count(distinct r.proname)
    from rpc_catalog r
    where exists (
        select 1
        from unnest(coalesce(r.proconfig, array[]::text[])) as cfg
        where replace(cfg, ' ', '') = 'search_path=public'
    )

    union all

    select
        8,
        'RPC_AUTHENTICATED',
        13,
        count(distinct rp.routine_name)
    from information_schema.routine_privileges rp
    where rp.specific_schema = 'public'
      and rp.routine_name in (
          select function_name
          from expected_rpcs
      )
      and rp.grantee = 'authenticated'
      and rp.privilege_type = 'EXECUTE'

    union all

    select
        9,
        'EXECUTE_RPC_PUBLICO_O_ANON',
        0,
        count(*)
    from information_schema.routine_privileges rp
    where rp.specific_schema = 'public'
      and rp.routine_name in (
          select function_name
          from expected_rpcs
      )
      and rp.grantee in ('PUBLIC', 'anon')
      and rp.privilege_type = 'EXECUTE'

    union all

    select
        10,
        'DML_DIRECTO_AUTHENTICATED',
        0,
        count(*)
    from information_schema.role_table_grants g
    where g.table_schema = 'public'
      and g.table_name in (
          select table_name
          from expected_tables
      )
      and g.grantee = 'authenticated'
      and g.privilege_type in (
          'INSERT',
          'UPDATE',
          'DELETE',
          'TRUNCATE',
          'REFERENCES',
          'TRIGGER'
      )

    union all

    select
        11,
        'DML_DIRECTO_ANON',
        0,
        count(*)
    from information_schema.role_table_grants g
    where g.table_schema = 'public'
      and g.table_name in (
          select table_name
          from expected_tables
      )
      and g.grantee = 'anon'
      and g.privilege_type in (
          'INSERT',
          'UPDATE',
          'DELETE',
          'TRUNCATE',
          'REFERENCES',
          'TRIGGER'
      )

    union all

    select
        12,
        'HELPERS_M13_EXPUESTOS',
        0,
        count(*)
    from information_schema.routine_privileges rp
    where rp.specific_schema = 'public'
      and left(rp.routine_name, 5) = '_m13_'
      and rp.grantee in ('PUBLIC', 'anon', 'authenticated')
      and rp.privilege_type = 'EXECUTE'

    union all

    select
        13,
        'PERMISOS_M13',
        7,
        count(distinct op.code)
    from public.organization_permissions op
    where op.code in (
        'lostfound.sighting.read',
        'lostfound.sighting.create',
        'lostfound.sighting.manage_own',
        'lostfound.sighting.moderate',
        'lostfound.match.read',
        'lostfound.match.review',
        'lostfound.match.confirm'
    )
)
select
    verificacion,
    esperado,
    obtenido,
    case
        when esperado = obtenido then 'PASS'
        else 'REVISAR'
    end as resultado
from checks
order by orden;

```

Resultado esperado:

```text
13 filas
13 PASS
```

## 3. Smoke remoto posterior

Después de los 13 PASS, validar al menos:

- creación de avistamiento asociado a un caso activo;
- rechazo de caso inexistente o inactivo;
- actualización y retiro solo por el reportante;
- listado público sin coordenadas exactas ni identidad del reportante;
- listado propio;
- listado gestionado por responsable o permiso;
- generación de candidatos;
- unicidad caso-avistamiento;
- recálculo idempotente;
- score entre 0 y 100;
- nivel coherente con score;
- razones explicables;
- especies distintas sin candidato;
- usuario no autorizado rechazado;
- media insegura rechazada;
- ausencia de confirmación/rechazo final en Bloque 2.

## Estado permitido

Con estructura aprobada pero smoke pendiente:

```text
M13 BLOQUE 2 VALIDACIÓN ESTRUCTURAL REMOTA PASS
M13 BLOQUE 2 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

Con estructura y smoke aprobados:

```text
M13 BLOQUE 2 REMOTO PASS
```
