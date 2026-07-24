# Operación — aplicar migración 047 (M12 Bloque 3)

**LeoVer** · Supabase de pruebas · aplicar **solo** cuando se autorice explícitamente.

La migración **no** se aplica desde Cursor en el Bloque 3.

## Archivo

```text
supabase/migrations/047_m12_veterinary_appointments_and_availability.sql
```

Prerrequisito: **046** ya aplicada y validada (Bloque 2 remoto PASS).

## Orden manual sugerido

1. Backup / snapshot del proyecto de pruebas si el proceso lo exige.
2. Abrir SQL Editor (o CLI) en el proyecto de pruebas.
3. Ejecutar el contenido completo de `047_…sql` (transacción `begin`…`commit`).
4. Verificar que el commit remoto de schema coincida con el archivo del repo.
5. Correr las consultas de validación y el smoke abajo.
6. No aplicar en producción sin checklist de release.

## Consultas de validación (orientativas)

```sql
-- Tablas
select tablename, rowsecurity
from pg_tables
where schemaname = 'public'
  and tablename like 'veterinary_%'
order by 1;

-- RPC m12 cliente (047)
select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and (
    p.proname like 'm12_%veterinary%appointment%'
    or p.proname like 'm12_%veterinary%schedule%'
    or p.proname like 'm12_%veterinary%availability%'
  )
order by 1;

-- Sin EXECUTE a PUBLIC/anon en helpers
select p.proname, r.rolname, has_function_privilege(r.oid, p.oid, 'EXECUTE') as can_exec
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
cross join pg_roles r
where n.nspname = 'public'
  and p.proname like '_m12_%'
  and r.rolname in ('anon', 'authenticated', 'public')
order by 1, 2;

-- Permisos sembrados
select code from public.organization_permissions
where code like 'veterinary.schedule.%'
   or code like 'veterinary.appointment.%'
order by 1;
```

## Smoke posterior (manual)

1. Upsert settings de clínica + timezone IANA.
2. Crear regla recurrente y listar slots para una fecha.
3. Excepción `CLOSED` → sin slots ese día.
4. Solicitar turno con mascota M08 autorizada → `REQUESTED`.
5. Segundo request mismo cupo 1 → capacidad agotada.
6. Confirmar / rechazar / cancelar; verificar historial.
7. Completar / no-show solo en ventana temporal válida.
8. Requester no ve turnos ajenos; gestor sí ve agenda de su clínica.
9. Confirmar que no hay DML directo ni tabla de slots masivos.

## Límites

- No modificar 040–046.
- No pagos ni historia clínica.
- No comenzar Bloque 4 desde este runbook.
