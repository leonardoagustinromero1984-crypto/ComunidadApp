# M12 — Aplicación remota de 046 y smoke del Bloque 2

## Ruta sugerida

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp\docs\02-arquitectura\M12-Aplicacion-Remota-046-y-Smoke-Bloque-2-v1.0.md
```

## Estado confirmado

- Commit local: `ee48ea9d6c69485960d3ff517bed544a003d0014`.
- Rama `main` alineada con `origin/main`.
- Migraciones 040–045 aplicadas en Supabase de pruebas.
- Migración 046 creada pero todavía no aplicada.

## Paso 1 — Aplicar 046

En el SQL Editor del proyecto Supabase de **pruebas**, ejecutar íntegramente:

```text
supabase/migrations/046_m12_veterinary_profiles_and_services.sql
```

Reglas:

- Ejecutar el archivo completo.
- No ejecutar fragmentos.
- Detenerse ante el primer error.
- No crear objetos manualmente para saltear errores.
- Una vez aplicada correctamente, no modificar 046.
- Toda corrección SQL posterior debe empezar en 047 y realizarse como bloque separado.

## Paso 2 — Verificación consolidada

Ejecutar:

```sql
with expected_tables(table_name) as (
    values
        ('veterinary_clinic_profiles'),
        ('veterinary_professionals'),
        ('veterinary_clinic_professionals'),
        ('veterinary_professional_specialties'),
        ('veterinary_services'),
        ('veterinary_opening_hours')
),
expected_rpcs(function_name) as (
    values
        ('m12_create_veterinary_clinic_draft'),
        ('m12_update_veterinary_clinic_profile'),
        ('m12_change_veterinary_clinic_status'),
        ('m12_request_veterinary_clinic_verification'),
        ('m12_review_veterinary_clinic_verification'),
        ('m12_get_public_veterinary_clinic'),
        ('m12_get_managed_veterinary_clinic'),
        ('m12_list_public_veterinary_clinics'),
        ('m12_list_managed_veterinary_clinics'),
        ('m12_create_veterinary_professional'),
        ('m12_update_veterinary_professional'),
        ('m12_link_veterinary_professional'),
        ('m12_unlink_veterinary_professional'),
        ('m12_replace_veterinary_professional_specialties'),
        ('m12_request_veterinary_professional_verification'),
        ('m12_review_veterinary_professional_verification'),
        ('m12_list_public_veterinary_professionals'),
        ('m12_list_managed_veterinary_professionals'),
        ('m12_create_veterinary_service'),
        ('m12_update_veterinary_service'),
        ('m12_change_veterinary_service_status'),
        ('m12_list_public_veterinary_services'),
        ('m12_list_managed_veterinary_services'),
        ('m12_replace_veterinary_opening_hours'),
        ('m12_list_public_veterinary_opening_hours'),
        ('m12_list_managed_veterinary_opening_hours')
),
rpc_catalog as (
    select
        p.oid,
        p.proname,
        p.prosecdef,
        p.proconfig
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (select function_name from expected_rpcs)
),
checks as (
    select
        1 as orden,
        'TABLAS_PRESENTES' as verificacion,
        6::bigint as esperado,
        count(*)::bigint as obtenido
    from expected_tables t
    where to_regclass('public.' || t.table_name) is not null

    union all

    select
        2,
        'RLS_ACTIVO',
        6,
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
        6,
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
        6,
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
        26,
        count(distinct proname)
    from rpc_catalog

    union all

    select
        6,
        'RPC_SECURITY_DEFINER',
        26,
        count(distinct proname)
    from rpc_catalog
    where prosecdef = true

    union all

    select
        7,
        'RPC_SEARCH_PATH_SEGURO',
        26,
        count(distinct r.proname)
    from rpc_catalog r
    where exists (
        select 1
        from unnest(coalesce(r.proconfig, array[]::text[])) cfg
        where replace(cfg, ' ', '') = 'search_path=public'
    )

    union all

    select
        8,
        'RPC_AUTHENTICATED',
        26,
        count(distinct rp.routine_name)
    from information_schema.routine_privileges rp
    where rp.specific_schema = 'public'
      and rp.routine_name in (select function_name from expected_rpcs)
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
      and rp.routine_name in (select function_name from expected_rpcs)
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
      and g.table_name in (select table_name from expected_tables)
      and g.grantee = 'authenticated'
      and g.privilege_type in (
          'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'
      )

    union all

    select
        11,
        'DML_DIRECTO_ANON',
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
        12,
        'HELPERS_M12_EXPUESTOS',
        0,
        count(*)
    from information_schema.routine_privileges rp
    where rp.specific_schema = 'public'
      and rp.routine_name like '\_m12\_%' escape '\'
      and rp.grantee in ('PUBLIC', 'anon', 'authenticated')
      and rp.privilege_type = 'EXECUTE'

    union all

    select
        13,
        'PERMISOS_VETERINARY',
        6,
        count(distinct permission_name)
    from permissions
    where permission_name in (
        'veterinary.profile.read',
        'veterinary.profile.manage',
        'veterinary.professional.read',
        'veterinary.professional.manage',
        'veterinary.service.read',
        'veterinary.service.manage'
    )
)
select
    verificacion,
    esperado,
    obtenido,
    case when esperado = obtenido then 'PASS' else 'REVISAR' end as resultado
from checks
order by orden;
```

> Si la tabla real de permisos no se llama `permissions`, reemplazar únicamente ese nombre por el encontrado en la migración 046 o en la guía operativa. No modificar el resto de la consulta.

## Paso 3 — Inventarios

### Constraints e índices

```sql
select
    c.conrelid::regclass::text as tabla,
    c.conname,
    case c.contype
        when 'p' then 'PRIMARY KEY'
        when 'f' then 'FOREIGN KEY'
        when 'u' then 'UNIQUE'
        when 'c' then 'CHECK'
        when 'x' then 'EXCLUSION'
        else c.contype::text
    end as tipo,
    pg_get_constraintdef(c.oid) as definicion
from pg_constraint c
where c.connamespace = 'public'::regnamespace
  and c.conrelid in (
      'public.veterinary_clinic_profiles'::regclass,
      'public.veterinary_professionals'::regclass,
      'public.veterinary_clinic_professionals'::regclass,
      'public.veterinary_professional_specialties'::regclass,
      'public.veterinary_services'::regclass,
      'public.veterinary_opening_hours'::regclass
  )
order by tabla, c.conname;

select
    tablename,
    indexname,
    indexdef
from pg_indexes
where schemaname = 'public'
  and tablename in (
      'veterinary_clinic_profiles',
      'veterinary_professionals',
      'veterinary_clinic_professionals',
      'veterinary_professional_specialties',
      'veterinary_services',
      'veterinary_opening_hours'
  )
order by tablename, indexname;
```

## Paso 4 — Smoke funcional

Usar datos ficticios en Supabase de pruebas y un usuario con organización M03 ACTIVE.

Orden:

1. Crear clínica DRAFT mediante RPC.
2. Confirmar que organización inactiva o sede ajena sean rechazadas.
3. Crear un servicio activo.
4. Cargar horarios semanales válidos.
5. Activar la clínica.
6. Crear un profesional sin verificación automática.
7. Vincularlo a la clínica.
8. Reemplazar especialidades.
9. Solicitar verificación de clínica y profesional.
10. Confirmar que un gestor común no pueda marcar VERIFIED.
11. Verificar la revisión con autoridad M04 real.
12. Consultar directorio público.
13. Confirmar redacción de contacto cuando opt-in está deshabilitado.
14. Confirmar que matrícula completa y datos internos no aparezcan públicamente.
15. Verificar rechazo de asset `http://` o `https://`.
16. Confirmar que website HTTPS válido sí sea aceptado como enlace.
17. Archivar o pausar conservando historial.
18. Confirmar que no existen turnos, pagos ni historia clínica en 046.

## Resultado de cierre remoto

Registrar únicamente después del PASS:

```text
M12 BLOQUE 2 REMOTO PASS
046 aplicada en Supabase de pruebas
6 tablas con RLS activo
26 RPC validadas
Sin DML directo del cliente
Helpers internos protegidos
Permisos veterinary.* presentes
Smoke de clínica/profesional/servicios/horarios aprobado
Directorio público seguro aprobado
```

Recién entonces ejecutar el prompt de M12 Bloque 3.
