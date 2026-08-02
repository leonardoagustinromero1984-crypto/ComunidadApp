# Operación — migraciones 068 y 069 (M23 agenda y reservas)

**LeoVer** · staging `wystsapjfpdtoprlmizz` · **no producción**.

## Estado (post cierre M23)

```text
068_m23_scheduling_availability_and_bookings.sql — APLICADA EN STAGING
069_m23_booking_operations_and_concurrency.sql — APLICADA EN STAGING
```

Registro `schema_migrations`: 068, 069 (post 067).

Validación: `scripts/ops/m23_remote_validation_068_069.sql` — **110/110 PASS**.

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
