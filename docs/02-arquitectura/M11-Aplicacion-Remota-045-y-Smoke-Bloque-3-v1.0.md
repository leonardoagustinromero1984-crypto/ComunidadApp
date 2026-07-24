# M11 — Aplicación remota de 045 y smoke del Bloque 3

## Ruta sugerida

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp\docs\02-arquitectura\M11-Aplicacion-Remota-045-y-Smoke-Bloque-3-v1.0.md
```

## Precondiciones

- Proyecto Supabase de **pruebas**, no producción.
- Migraciones 040–044 aplicadas.
- Hardening 044 con todos los controles en PASS.
- Rama `main` alineada con `origin/main`.
- Commit del Bloque 3:

```text
353627fb713a5cf7f9b1ca534319b246053b0b62
```

## Paso 1 — Aplicación

Ejecutar íntegramente:

```text
supabase/migrations/045_m11_shelter_emergencies_events_reports.sql
```

Reglas:

- Ejecutar el archivo completo.
- No ejecutar fragmentos.
- Detenerse ante cualquier error.
- No crear objetos manualmente para saltear fallos.
- Una vez aplicada correctamente, 045 queda inmutable.
- Toda corrección SQL posterior debe comenzar en 046.

## Paso 2 — Verificación consolidada

Ejecutar:

```sql
with expected_tables(table_name) as (
    values
        ('shelter_emergencies'),
        ('shelter_events'),
        ('shelter_event_registrations')
),
client_rpcs as (
    select
        p.oid,
        p.proname,
        p.prosecdef,
        p.proconfig
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname like 'm11_%'
      and (
          p.proname like '%emergency%'
          or p.proname like '%event%'
          or p.proname like '%shelter_operational_summary%'
          or p.proname like '%capacity_metrics%'
          or p.proname like '%pet_metrics%'
          or p.proname like '%volunteer_metrics%'
          or p.proname like '%campaign_metrics%'
          or p.proname like '%supply_metrics%'
      )
),
checks as (
    select
        1 as orden,
        'TABLAS_PRESENTES' as verificacion,
        3::bigint as esperado,
        count(*)::bigint as obtenido
    from expected_tables t
    where to_regclass('public.' || t.table_name) is not null

    union all

    select
        2,
        'RLS_ACTIVO',
        3,
        count(*)
    from expected_tables t
    join pg_class c on c.relname = t.table_name
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relkind in ('r', 'p')
      and c.relrowsecurity = true

    union all

    select
        3,
        'TABLAS_CON_POLICIES',
        3,
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
        'RPC_BLOQUE_3',
        25,
        count(distinct proname)
    from client_rpcs

    union all

    select
        5,
        'RPC_SECURITY_DEFINER',
        25,
        count(distinct proname)
    from client_rpcs
    where prosecdef = true

    union all

    select
        6,
        'RPC_SEARCH_PATH_SEGURO',
        25,
        count(distinct r.proname)
    from client_rpcs r
    where exists (
        select 1
        from unnest(coalesce(r.proconfig, array[]::text[])) cfg
        where replace(cfg, ' ', '') = 'search_path=public'
    )

    union all

    select
        7,
        'PRIMARY_KEYS',
        3,
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
        8,
        'FOREIGN_KEYS',
        3,
        count(*)
    from expected_tables t
    where exists (
        select 1
        from pg_constraint c
        where c.conrelid = to_regclass('public.' || t.table_name)
          and c.contype = 'f'
    )

    union all

    select
        9,
        'ESCRITURA_DIRECTA_AUTHENTICATED',
        0,
        count(*)
    from information_schema.role_table_grants g
    where g.table_schema = 'public'
      and g.table_name in (select table_name from expected_tables)
      and g.grantee = 'authenticated'
      and g.privilege_type in (
          'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'
      )

    union all

    select
        10,
        'ESCRITURA_DIRECTA_ANON',
        0,
        count(*)
    from information_schema.role_table_grants g
    where g.table_schema = 'public'
      and g.table_name in (select table_name from expected_tables)
      and g.grantee = 'anon'
      and g.privilege_type in (
          'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'
      )

    union all

    select
        11,
        'EXECUTE_RPC_PUBLICO_O_ANON',
        0,
        count(*)
    from information_schema.routine_privileges rp
    where rp.specific_schema = 'public'
      and rp.routine_name in (select proname from client_rpcs)
      and rp.grantee in ('PUBLIC', 'anon')
      and rp.privilege_type = 'EXECUTE'

    union all

    select
        12,
        'RPC_AUTHENTICATED',
        25,
        count(distinct rp.routine_name)
    from information_schema.routine_privileges rp
    where rp.specific_schema = 'public'
      and rp.routine_name in (select proname from client_rpcs)
      and rp.grantee = 'authenticated'
      and rp.privilege_type = 'EXECUTE'
)
select
    verificacion,
    esperado,
    obtenido,
    case when esperado = obtenido then 'PASS' else 'REVISAR' end as resultado
from checks
order by orden;
```

### Resultado esperado

Las 12 filas deben quedar en `PASS`.

Si `RPC_BLOQUE_3` no devuelve exactamente 25, no corregir SQL todavía. Exportar el inventario con:

```sql
select
    p.proname,
    pg_get_function_identity_arguments(p.oid) as argumentos,
    p.prosecdef,
    p.proconfig
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname like 'm11_%'
order by p.proname;
```

y revisar el filtro contra las 25 RPC reales de 045.

## Paso 3 — Smoke funcional

Usar un usuario autenticado con permisos sobre un refugio M11 ACTIVE.

### Urgencias

1. Crear urgencia DRAFT.
2. Activarla.
3. Crear una urgencia CRITICAL y confirmar que prepara el hook M06 sin enviar push.
4. Verificar que una vista pública no exponga datos privados.
5. Resolver una urgencia con notas obligatorias.
6. Confirmar historial y auditoría M07.
7. Verificar rechazo de evidencia `http://` o `https://`.

### Eventos e inscripciones

1. Crear evento DRAFT.
2. Publicarlo.
3. Registrar usuarios hasta completar el cupo.
4. Confirmar que el siguiente usuario pase a WAITLISTED.
5. Cancelar una inscripción activa.
6. Confirmar promoción coherente desde waitlist si la implementación lo contempla.
7. Marcar asistencia con un gestor autorizado.
8. Cancelar un evento y comprobar que el historial se conserva.
9. Confirmar ausencia de pagos, entradas pagas o checkout.

### Reportes y exportación

1. Abrir resumen operativo.
2. Verificar métricas de capacidad, mascotas, voluntarios, campañas, insumos, urgencias y eventos.
3. Probar rango de fechas válido.
4. Confirmar rechazo de un rango inválido.
5. Exportar CSV.
6. Revisar que el CSV no contenga:
   - dirección privada;
   - teléfonos;
   - notas internas;
   - datos de aportantes;
   - datos bancarios.

## Estado de cierre remoto

**Validación estructural/seguridad 045:** PASS (25 RPC, RLS, sin escritura directa, sin EXECUTE PUBLIC/anon).

El conteo inicial 23/25 fue error de filtro (`%emergency%` vs `%emergencies%`). Las RPC
`m11_list_public_shelter_emergencies` y `m11_list_shelter_emergencies` fueron verificadas
individualmente. **No falta RPC. No se necesita 046** por ese hallazgo.

**Smoke UI completo:** `PENDIENTE_EXTERNO` — no inventar como aprobado.

Registro estructural:

```text
M11 BLOQUE 3 REMOTO PASS (estructural/seguridad)
045 aplicada en Supabase de pruebas
3 tablas con RLS activo
25 RPC validadas
Sin escritura directa del cliente
```

Smoke UI (cuando se ejecute, registrar aparte):

```text
PENDIENTE_EXTERNO — smoke UI urgencias / eventos / métricas-CSV
```

Cierre técnico del módulo: prompt `M11-Cierre-Final-Prompt-Cursor-v1.0.md` / `docs/03-modulos/M11-cierre-final.md`.
