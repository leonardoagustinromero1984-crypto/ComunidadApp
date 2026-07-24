# Operación — aplicación y validación de migración 048 (M13 Bloque 2)

**LeoVer** · Supabase de pruebas · aplicar **solo** cuando se autorice explícitamente.

Esta guía **no** aplica la migración desde Cursor. El Bloque 2 cierra con 048 **creada y no aplicada**.

## Archivo

```text
supabase/migrations/048_m13_sightings_and_match_candidates.sql
```

Prerrequisito: migraciones **001–047** ya aplicadas en el entorno destino (incluida **047** M12).

## Orden manual sugerido

1. Backup / snapshot del proyecto de pruebas si el proceso lo exige.
2. Abrir SQL Editor (o CLI) en el proyecto de pruebas.
3. Ejecutar el **contenido completo** del archivo `048_…sql` (transacción `begin`…`commit`).
4. Si el editor responde **Success**, **no reejecutar** el mismo archivo.
5. Correr las consultas de validación estructural abajo.
6. Smoke remoto: **pendiente** (no simular resultados aquí).
7. No aplicar en producción sin checklist de release.

## Reglas post-aplicación

- **No editar** `048_…sql` después de aplicada remotamente.
- Si aparece un defecto SQL posterior, crear **049** (bloque separado); no parchear 048 in-place.

## Validación estructural (orientativa)

```sql
-- Tablas M13
select tablename, rowsecurity
from pg_tables
where schemaname = 'public'
  and tablename in (
    'lost_found_sighting_details',
    'lost_found_match_candidates',
    'lost_found_match_decisions',
    'lost_found_match_status_history'
  )
order by 1;

-- Legacy intacto
select to_regclass('public.lost_found_sightings') is not null as legacy_ok;

-- 13 RPC cliente m13_*
select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname like 'm13_%'
  and p.proname not like '\_%' escape '\'
order by 1;

-- Helpers sin EXECUTE a anon/authenticated/public
select p.proname, r.rolname, has_function_privilege(r.oid, p.oid, 'EXECUTE') as can_exec
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
cross join pg_roles r
where n.nspname = 'public'
  and p.proname like '\_m13\_%' escape '\'
  and r.rolname in ('anon', 'authenticated', 'public')
order by 1, 2;

-- Permisos
select code from public.organization_permissions
where code like 'lostfound.%'
order by 1;
```

## Smoke remoto (pendiente)

1. Crear avistamiento con caso ACTIVE + media `m05://…`.
2. Listar público (sin coords exactas / sin identidad reportante).
3. Listar mismíos / managed según autoridad.
4. Generar candidatos por sighting y por caso; verificar score/nivel/razones.
5. Recalcular candidato no terminal.
6. Confirmar que **no** existen RPC de confirm/reject todavía.
7. Confirmar que DML directo a tablas M13 falla para `authenticated`.

## Límites

- No modificar 001–047.
- No crear 049 en este bloque.
- No declarar M12 cerrado.
- No implementar Bloque 3 desde este runbook.
