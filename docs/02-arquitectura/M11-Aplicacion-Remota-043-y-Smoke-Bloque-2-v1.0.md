# M11 — Aplicación remota de 043 y smoke del Bloque 2

## Ruta sugerida

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp\docs\02-arquitectura\M11-Aplicacion-Remota-043-y-Smoke-Bloque-2-v1.0.md
```

## Precondiciones

- Proyecto Supabase de **pruebas**, no producción.
- Migraciones 040, 041 y 042 aplicadas.
- Rama `main` alineada con `origin/main`.
- Commit M11 Bloque 2:

```text
8ba4326f79e3d74e2f7fdf5ccbb9bd6a18c01bcc
```

## Paso 1 — Aplicación

Abrir y ejecutar completa:

```text
supabase/migrations/043_m11_shelter_campaigns_and_aid.sql
```

Reglas:

- Ejecutar el archivo completo.
- No ejecutar fragmentos.
- Detenerse ante cualquier error.
- No crear objetos manualmente para saltear errores.
- Una vez aplicada con éxito, no modificar 043; cualquier corrección posterior debe ser 044.

## Paso 2 — Verificación estructural

Ejecutar:

```sql
-- Tablas y RLS
select
    c.relname as tabla,
    c.relrowsecurity as rls_activo,
    c.relforcerowsecurity as rls_forzado
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind in ('r', 'p')
  and c.relname in (
      'shelter_campaigns',
      'shelter_campaign_updates',
      'shelter_supply_requests',
      'shelter_supply_contributions'
  )
order by c.relname;

-- Policies
select
    tablename,
    policyname,
    cmd,
    roles,
    qual,
    with_check
from pg_policies
where schemaname = 'public'
  and tablename in (
      'shelter_campaigns',
      'shelter_campaign_updates',
      'shelter_supply_requests',
      'shelter_supply_contributions'
  )
order by tablename, policyname;

-- RPC M11 relacionadas con campañas, pedidos y aportes
select
    p.proname as funcion,
    pg_get_function_identity_arguments(p.oid) as argumentos,
    p.prosecdef as security_definer,
    coalesce(array_to_string(p.proconfig, ', '), '') as configuracion
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname like 'm11_%'
  and (
      p.proname like '%campaign%'
      or p.proname like '%supply%'
      or p.proname like '%contribution%'
  )
order by p.proname;

-- Constraints
select
    conrelid::regclass::text as tabla,
    conname,
    case contype
        when 'p' then 'PRIMARY KEY'
        when 'f' then 'FOREIGN KEY'
        when 'u' then 'UNIQUE'
        when 'c' then 'CHECK'
        when 'x' then 'EXCLUSION'
        else contype::text
    end as tipo,
    pg_get_constraintdef(oid) as definicion
from pg_constraint
where connamespace = 'public'::regnamespace
  and conrelid in (
      'public.shelter_campaigns'::regclass,
      'public.shelter_campaign_updates'::regclass,
      'public.shelter_supply_requests'::regclass,
      'public.shelter_supply_contributions'::regclass
  )
order by tabla, conname;

-- Índices
select
    tablename,
    indexname,
    indexdef
from pg_indexes
where schemaname = 'public'
  and tablename in (
      'shelter_campaigns',
      'shelter_campaign_updates',
      'shelter_supply_requests',
      'shelter_supply_contributions'
  )
order by tablename, indexname;

-- Grants de ejecución sobre RPC M11
select
    routine_name,
    grantee,
    privilege_type
from information_schema.routine_privileges
where specific_schema = 'public'
  and routine_name like 'm11_%'
  and (
      routine_name like '%campaign%'
      or routine_name like '%supply%'
      or routine_name like '%contribution%'
  )
order by routine_name, grantee;
```

## Resultado esperado

- Cuatro tablas encontradas.
- `rls_activo = true` en las cuatro.
- Policies presentes para cada tabla.
- RPC esperadas con `security_definer = true`.
- Configuración de función con `search_path=public`.
- Checks de estados y cantidades.
- Foreign keys e índices por refugio, campaña, pedido, aportante, estado y vencimiento.
- `authenticated` con EXECUTE solo sobre RPC cliente.
- Sin grants de escritura directa para `anon` o `authenticated`.

## Paso 3 — Smoke funcional

El smoke debe ejecutarse con un usuario autenticado que tenga permiso sobre un refugio M11 ACTIVE.

Orden:

1. Crear campaña DRAFT.
2. Activarla.
3. Crear pedido OPEN vinculado a la campaña.
4. Verificar listado público sin notas internas.
5. Registrar aporte parcial.
6. Confirmarlo.
7. Registrar recepción parcial.
8. Registrar recepción restante.
9. Confirmar que el pedido quedó FULFILLED.
10. Confirmar actualización de cantidades comprometidas y recibidas.
11. Agregar una novedad pública con referencia segura `m05://` o `file_asset:`.
12. Rechazar una evidencia `http://` o `https://`.
13. Verificar que no se almacenaron datos monetarios.
14. Confirmar historial y auditoría M07.

No usar datos productivos.

## Hallazgo post-aplicación (seguridad)

043 se aplicó **correctamente** en Supabase de pruebas (tablas, RLS, RPC, smoke estructural).

La verificación posterior de privilegios detectó:

* Las **20 RPC** del Bloque 2 conservaban `EXECUTE` para `PUBLIC` y/o `anon`.
* Las **cuatro tablas** concedían privilegios directos amplios a `authenticated`
  (`INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, `REFERENCES`, `TRIGGER`, además de `SELECT`).

**No modificar 043** (ya aplicada). La correctiva es la migración:

```text
supabase/migrations/044_m11_harden_campaign_aid_permissions.sql
```

Guía operativa: `docs/05-operacion/M11-aplicacion-migracion-044-seguridad.md`.

## Cierre

Después del smoke funcional de 043 registrar:

```text
M11 BLOQUE 2 REMOTO PASS (funcional)
043 aplicada en Supabase de pruebas
4 tablas con RLS activo
RPC verificadas
Smoke campaña → pedido → aporte → recepción → FULFILLED aprobado
```

**Obligatorio antes del Bloque 3:** aplicar y validar **044** hasta obtener:

```text
M11 044 HARDENING PASS
EXECUTE_PUBLICO_O_ANON = 0
ESCRITURA_DIRECTA_CLIENTE = 0
```

**Prohibido** avanzar al prompt del Bloque 3 (migración **045**) sin el PASS de 044.
No promover Bloque 2 a producción sin 044.
