# Operación — migraciones 058 y 059 (M18 eventos)

**LeoVer** · Supabase staging/pruebas · **no producción**.

## Archivos canónicos

```text
supabase/migrations/058_m18_community_events_and_registrations.sql
supabase/migrations/059_m18_event_operations_and_attendance.sql
```

## Estado (post cierre M18)

```text
058 — APLICADA EN STAGING
059 — APLICADA EN STAGING
Validación: scripts/ops/m18_remote_validation_058_059.sql — 110/110 PASS
```

## Orden obligatorio

```text
001–057 → 058 → 059
```

## Validación

```bash
supabase db query --linked -f scripts/ops/m18_remote_validation_058_059.sql
```

## Notas 059

- Estados `ATTENDED`, `REJECTED`
- RPC asistencia / no-show / reject / promote waitlist
- Revoke anon en tablas M18
- Fix idempotente `_m18_is_moderator`

## 058 pre-aplicación

Correcciones incluidas antes de primera aplicación staging:

- `_m18_is_moderator` → M02 `user_has_active_role`
- Orden parámetros RPC create/update event

## Referencias

- `docs/03-modulos/M18-cierre-oficial.md`
- `docs/03-modulos/M18-Bloque-5-validacion.md`
