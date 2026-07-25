# M13 — Aplicación y validación de la migración 049

## Estado previo

```text
M13 REVISIÓN REMOTA CERRADA LOCALMENTE
MIGRACIÓN 049 PENDIENTE DE APLICACIÓN REMOTA
SHA: 14cec199cb7cab9faf2848c6ec6899662128e3ec
```

## Archivo a ejecutar

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp\supabase\migrations\049_m13_match_review_workflow.sql
```

Copiar el archivo completo y ejecutarlo **una sola vez** en el SQL Editor de Supabase de pruebas.

- Si devuelve `Success`, no volver a ejecutarlo.
- No editar la 049 después de aplicada.
- Ante un error, guardar el mensaje exacto y detenerse.
- Toda corrección SQL posterior comienza en la migración 050.

## Validación estructural

Ejecutar después del `Success`:

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
        ('m13_open_match_review'),
        ('m13_confirm_match_candidate'),
        ('m13_reject_match_candidate'),
        ('m13_mark_match_inconclusive'),
        ('m13_withdraw_match_candidate'),
        ('m13_expire_match_candidate'),
        ('m13_list_match_decisions'),
        ('m13_list_match_status_history')
),
mutating_rpcs(function_name) as (
    values
        ('m13_open_match_review'),
        ('m13_confirm_match_candidate'),
        ('m13_reject_match_candidate'),
        ('m13_mark_match_inconclusive'),
        ('m13_withdraw_match_candidate'),
        ('m13_expire_match_candidate')
),
rpc_catalog as (
    select
        p.oid,
        p.proname,
        p.prosecdef,
        p.proconfig,
        lower(pg_get_functiondef(p.oid)) as definition
    from pg_proc p
    join pg_namespace n
      on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
          select function_name
          from expected_rpcs
      )
),
mutating_catalog as (
    select *
    from rpc_catalog
    where proname in (
        select function_name
        from mutating_rpcs
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
        'RPC_PRESENTES',
        8,
        count(distinct proname)
    from rpc_catalog

    union all

    select
        4,
        'RPC_SECURITY_DEFINER',
        8,
        count(distinct proname)
    from rpc_catalog
    where prosecdef = true

    union all

    select
        5,
        'RPC_SEARCH_PATH_SEGURO',
        8,
        count(distinct r.proname)
    from rpc_catalog r
    where exists (
        select 1
        from unnest(coalesce(r.proconfig, array[]::text[])) as cfg
        where replace(cfg, ' ', '') = 'search_path=public'
    )

    union all

    select
        6,
        'RPC_AUTHENTICATED',
        8,
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
        7,
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
        8,
        'DML_DIRECTO_CLIENTE',
        0,
        count(*)
    from information_schema.role_table_grants g
    where g.table_schema = 'public'
      and g.table_name in (
          select table_name
          from expected_tables
      )
      and g.grantee in ('authenticated', 'anon')
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
        9,
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
        10,
        'RPC_MUTACION_FOR_UPDATE',
        6,
        count(distinct proname)
    from mutating_catalog
    where definition ~ 'for[[:space:]]+update'

    union all

    select
        11,
        'RPC_CON_GUARDA_AUTORIDAD',
        8,
        count(distinct proname)
    from rpc_catalog
    where definition ~ '(auth[.]uid|_m13_can_)'

    union all

    select
        12,
        'DECISION_FINAL_UNICA',
        1,
        case
            when exists (
                select 1
                from pg_indexes i
                where i.schemaname = 'public'
                  and i.tablename = 'lost_found_match_decisions'
                  and lower(i.indexdef) like '%unique%'
                  and lower(i.indexdef) like '%candidate_id%'
            )
            then 1::bigint
            else 0::bigint
        end

    union all

    select
        13,
        'CIERRE_AUTOMATICO_LOST_FOUND_POSTS',
        0,
        count(distinct proname)
    from mutating_catalog
    where definition ~ 'update[[:space:]]+(public[.])?lost_found_posts'

    union all

    select
        14,
        'PERMISOS_REVISION',
        2,
        count(distinct op.code)
    from public.organization_permissions op
    where op.code in (
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
14 filas
14 PASS
```

## Smoke remoto pendiente

Después de la validación estructural, probar:

- abrir revisión desde `PROPOSED`;
- confirmar desde `UNDER_REVIEW`;
- rechazar desde `UNDER_REVIEW`;
- marcar inconclusa;
- retirar desde `PROPOSED` y `UNDER_REVIEW`;
- expirar;
- impedir confirmación directa desde `PROPOSED`;
- impedir reapertura de estados finales;
- impedir segunda decisión final;
- validar reintento idempotente;
- validar conflicto incompatible;
- validar reportante sin autoridad;
- validar responsable/gestor/moderador autorizado;
- listar decisiones;
- listar historial append-only;
- confirmar que el avistamiento pasa a `CONFIRMED`;
- confirmar que `lost_found_posts` no se cierra automáticamente;
- confirmar privacidad de notas, contacto y ubicación.

## Estado permitido

Con estructura aprobada:

```text
M13 MIGRACIÓN 049 VALIDACIÓN ESTRUCTURAL REMOTA PASS
SMOKE FUNCIONAL M13 PENDIENTE EXTERNO
```

Con estructura y smoke aprobados:

```text
M13 BLOQUE 3 REMOTO PASS
```
