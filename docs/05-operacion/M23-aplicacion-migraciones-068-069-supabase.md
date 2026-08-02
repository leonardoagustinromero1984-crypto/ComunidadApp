# Operación — migraciones 068 y 069 (M23 agenda y reservas)

**LeoVer** · staging `wystsapjfpdtoprlmizz` · **no producción**.

## Estado (post cierre M23)

```text
068_m23_scheduling_availability_and_bookings.sql — APLICADA EN STAGING
069_m23_booking_operations_and_concurrency.sql — APLICADA EN STAGING
```

Registro `schema_migrations`: 068, 069 (post 067).

Validación: `scripts/ops/m23_remote_validation_068_069.sql` — **110/110 PASS** (2026-08-02).  
Smoke: `scripts/ops/m23_remote_smoke_25.sql` — **25/25 PASS** (2026-08-02).

## Verificación read-only (sin re-aplicar SQL)

Consultas de confirmación permitidas:

```sql
-- schema_migrations
select version from supabase_migrations.schema_migrations
where version in ('068','069') order by version;

-- tablas + RLS (esperado: 4 filas, rowsecurity = true)
select tablename, rowsecurity from pg_tables
where schemaname = 'public' and tablename like 'm23_%';

-- RPC M23 (esperado: 18 funciones públicas m23_*)
select count(*) from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname like 'm23_%';

-- columnas 069
select column_name from information_schema.columns
where table_name = 'm23_bookings'
  and column_name in ('pet_id','rescheduled_from_booking_id');
```

**No repetir** ejecución de archivos `068`/`069` ni `migration repair` si ya registradas.

## Incidencia transitoria CLI (tasks 19241, 19242)

Timeout puntual al registrar/verificar `schema_migrations` vía Supabase CLI (conexión, **no error SQL**). Task **19242** confirmado como timeout transitorio; migración **069 aplicada**; columnas `pet_id` y `rescheduled_from_booking_id` verificadas en staging. Reintento exitoso. **No re-ejecutar** 069 ni `migration repair`. Detalle: `M23-Bloque-4-validacion.md`.

## Prerrequisitos

- Migraciones 001–067 aplicadas.
- M22 activo (prestadores, ofertas, sedes).
- M03 permisos `booking.view`, `booking.manage`.

## Procedimiento (referencia)

1. Confirmar proyecto staging no productivo.
2. Ejecutar **068** completo una vez (fix `m23_list_provider_bookings` → `language sql` incluido en repo).
3. Verificar 4 tablas `m23_*` + RLS.
4. Ejecutar **069** completo una vez.
5. Verificar RPCs `m23_reschedule_booking`, `m23_expire_booking`, `m23_list_booking_history`.
6. Registrar versiones en `schema_migrations` si se aplicó vía SQL Editor.
7. Ejecutar script de validación 110 casos.

## Límites

- No `supabase db push` global.
- No aplicar 039–052.
- No producción.
- No service role en Android.

## Rollback

Forward-only; restaurar backup staging ante error parcial.
