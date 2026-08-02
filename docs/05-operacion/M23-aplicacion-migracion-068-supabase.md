# Operación — migración 068 (M23 agenda y reservas)

**LeoVer** · aplicar solo en staging/pruebas; este documento no autoriza producción.

## Estado

```text
068_m23_scheduling_availability_and_bookings.sql — CREADA, NO APLICADA
```

## Prerrequisitos

- Migraciones 001–067 aplicadas en orden.
- M22 disponible con `m22_service_providers`, `m22_service_offerings` y `m22_provider_branches`.
- M03 disponible con `organizations`, roles, permisos y `has_org_permission`.
- Backup verificable del proyecto de staging.

## Procedimiento

1. Confirmar que el proyecto es staging/no productivo.
2. Ejecutar el contenido completo de `supabase/migrations/068_m23_scheduling_availability_and_bookings.sql` una única vez.
3. Verificar objetos:

```sql
select table_name
from information_schema.tables
where table_schema = 'public' and table_name like 'm23_%'
order by table_name;
-- esperado: 4
```

4. Verificar que las cuatro tablas tienen RLS activo:

```sql
select tablename, rowsecurity
from pg_tables
where schemaname = 'public' and tablename like 'm23_%'
order by tablename;
```

5. Con un JWT autenticado, ejecutar una reserva y repetirla con el mismo `client_request_id`; verificar idempotencia.
6. Intentar una reserva superpuesta para el mismo prestador; debe fallar con `M23_SLOT_UNAVAILABLE`.
7. Verificar los RPC públicos con `anon` y las mutaciones con `authenticated`; nunca usar `service_role` en Android.

## Límites y rollback

- No aplicar SQL desde la aplicación móvil.
- No hay rollback automático para una migración forward-only; ante error parcial, restaurar el backup de staging.
- No habilitar pagos, columnas de pago ni tablas de slots materializados: permanecen fuera del alcance de M23 Bloque 2.
