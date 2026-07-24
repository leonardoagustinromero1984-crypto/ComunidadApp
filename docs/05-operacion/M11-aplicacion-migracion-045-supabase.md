# M11 — Aplicación manual de migración 045 en Supabase

LeoVer. **No aplicar desde Cursor ni desde la app Android.** Apply **manual** únicamente, en proyecto de **pruebas** primero. **LOCAL ONLY** hasta autorización explícita para remoto.

## Gate de inicio

No aplicar 045 hasta confirmar:

```text
M11 BLOQUE 2 REMOTO PASS
043 aplicada en Supabase de pruebas
044 hardening aplicada y validada (PASS)
Smoke funcional Bloque 2 aprobado
```

**No iniciar Bloque 3 antes de 044 PASS.**

## Orden exacto

```text
042_m11_shelter_operations_core.sql              (bloque 1 — prerequisito)
043_m11_shelter_campaigns_and_aid.sql            (bloque 2)
044_m11_harden_campaign_aid_permissions.sql      (hardening bloque 2 — PASS requerido)
045_m11_shelter_emergencies_events_reports.sql   (bloque 3)
```

Ejecutar cada archivo **completo**, en ese orden. No invertir. No aplicar 045 si 044 no está aplicada y validada.

## Antes de aplicar

* Confirmar proyecto Supabase de **pruebas** (no producción).
* Confirmar 042, 043 y **044 PASS** ya aplicadas.
* Baseline 001–041 consistente según entorno.
* Backup si hay datos relevantes.
* **No** ejecutar apply automático desde CI, Cursor o scripts no revisados.

## Ejecución

1. Abrir SQL Editor del proyecto de pruebas.
2. Verificar que 044 está aplicada (`docs/05-operacion/M11-aplicacion-migracion-044-seguridad.md`).
3. Pegar/ejecutar íntegro `045_m11_shelter_emergencies_events_reports.sql`.
4. Detener si cualquier paso falla.

## Verificación — tablas bloque 3

```sql
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in (
    'shelter_emergencies',
    'shelter_events',
    'shelter_event_registrations'
  )
order by table_name;
```

Esperado: **3 filas**.

## Verificación — RLS

```sql
select c.relname, c.relrowsecurity
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind = 'r'
  and c.relname in (
    'shelter_emergencies',
    'shelter_events',
    'shelter_event_registrations'
  )
order by c.relname;
```

Esperado: `relrowsecurity = true` en las tres tablas.

## Verificación — políticas

```sql
select tablename, policyname, cmd
from pg_policies
where schemaname = 'public'
  and tablename in (
    'shelter_emergencies',
    'shelter_events',
    'shelter_event_registrations'
  )
order by tablename, policyname;
```

Esperado: políticas `*_select_m11` y writes denegados (`ins/upd/del` con `false`).

## Verificación — constraints e índices

```sql
select conname, conrelid::regclass as table_name
from pg_constraint
where connamespace = 'public'::regnamespace
  and conrelid::regclass::text in (
    'shelter_emergencies',
    'shelter_events',
    'shelter_event_registrations'
  )
order by 2, 1;

select indexname, tablename
from pg_indexes
where schemaname = 'public'
  and tablename in (
    'shelter_emergencies',
    'shelter_events',
    'shelter_event_registrations'
  )
order by tablename, indexname;
```

Comprobar checks de categoría, severidad, visibilidad, status, fechas (`ends_at > starts_at`), refs `m05://|file_asset:` e índice único parcial de inscripciones abiertas.

## Verificación — funciones `m11_*` (bloque 3)

```sql
select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in (
    'm11_create_shelter_emergency',
    'm11_update_shelter_emergency',
    'm11_change_shelter_emergency_status',
    'm11_resolve_shelter_emergency',
    'm11_get_shelter_emergency',
    'm11_list_public_shelter_emergencies',
    'm11_list_shelter_emergencies',
    'm11_create_shelter_event',
    'm11_update_shelter_event',
    'm11_change_shelter_event_status',
    'm11_get_shelter_event',
    'm11_list_public_shelter_events',
    'm11_list_shelter_events',
    'm11_register_shelter_event',
    'm11_cancel_shelter_event_registration',
    'm11_mark_shelter_event_attendance',
    'm11_list_shelter_event_registrations',
    'm11_get_shelter_operational_summary',
    'm11_get_shelter_capacity_metrics',
    'm11_get_shelter_pet_metrics',
    'm11_get_shelter_volunteer_metrics',
    'm11_get_shelter_campaign_metrics',
    'm11_get_shelter_supply_metrics',
    'm11_get_shelter_emergency_metrics',
    'm11_get_shelter_event_metrics'
  )
order by p.proname;
```

Esperado: **25 filas**.

## Verificación — permisos M03 bloque 3

```sql
select code, description
from public.organization_permissions
where code in (
  'shelter.emergency.read',
  'shelter.emergency.manage',
  'shelter.event.read',
  'shelter.event.manage',
  'shelter.report.read',
  'shelter.report.export'
)
order by code;
```

Esperado: **6 filas**.

## Verificación — grants RPC (muestra)

```sql
select
  p.proname,
  has_function_privilege('authenticated', p.oid, 'EXECUTE') as auth_execute,
  has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
  has_function_privilege('public', p.oid, 'EXECUTE') as public_execute
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname = 'm11_create_shelter_emergency';
```

Esperado: `auth_execute = true`, `anon_execute = false`, `public_execute = false`.

## Smoke manual (post-apply, no automatizado)

Requiere org M03, refugio `ACTIVE` y usuario con permisos manage/read/export según paso.

1. **Urgencia draft** — `m11_create_shelter_emergency` con `p_activate = false`.
2. **Activar** — `m11_change_shelter_emergency_status` → `ACTIVE` (severidad `CRITICAL` opcional).
3. **Resolver** — `m11_resolve_shelter_emergency` con notas obligatorias.
4. **Evento** — `m11_create_shelter_event` draft → `m11_change_shelter_event_status` → `PUBLISHED`.
5. **Inscripción** — usuario autenticado: `m11_register_shelter_event`; repetir hasta waitlist si hay cupo.
6. **Asistencia** — manager: `m11_mark_shelter_event_attendance`.
7. **Métricas** — `m11_get_shelter_operational_summary` con rango válido.
8. **Export audit** — mismo RPC con `p_record_export_audit = true` y permiso `shelter.report.export`.

No ejecutar este smoke desde Cursor ni CI.

## Problemas por reejecución

Las políticas usan `drop policy if exists` antes de `create policy`. Reejecución parcial de políticas no debería fallar por duplicado. **No** garantiza idempotencia completa con datos reales.

## Estado remoto

* Migración **045** en repo; apply remoto **pendiente** hasta PASS de 044 confirmado.
* **No promover a producción** antes de smoke manual post-apply.
* **No apply desde Cursor.**

## Pendientes

* Apply remoto 044 si aún pendiente; validar hardening.
* Apply remoto 045 y smoke manual.
* Cierre final M11 **no iniciado** (push M06, reputación, chat, APK cuando se autorice).
