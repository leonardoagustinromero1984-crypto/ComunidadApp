# Operación — migración 058 (M18 eventos comunitarios)

**LeoVer** · Supabase staging/pruebas · **no producción**.

## Archivo canónico

```text
supabase/migrations/058_m18_community_events_and_registrations.sql
```

## Estado actual (post Bloques 1–4)

```text
058 — CREADA, NO APLICADA
059 — NO REQUERIDA
Validación remota M18 — PENDIENTE
```

## Script de preparación validación

```text
scripts/ops/m18_remote_validation_058_prep.sql
```

Ejecutar **después** de aplicar 058 para verificación estructural (tablas, RPC, RLS). No sustituye smoke funcional completo.

## Aplicación parcial

Si falla a mitad de 058:

1. No re-ejecutar ciego — revisar `schema_migrations` y objetos creados.
2. Corregir en migración incremental (059+) si hiciera falta; **no editar 058** retroactivamente.
3. Rollback no destructivo: preservar filas existentes para auditoría.

## Prerrequisitos

- Migraciones 001–057 aplicadas en orden en entorno **no productivo**.
- Permisos M03 `event.view` / `event.manage` disponibles (insertados en 058).
- Operador confirma proyecto Supabase de staging/pruebas.

## Procedimiento

1. Confirmar entorno **no productivo** (no producción).
2. Abrir SQL Editor en Supabase Dashboard.
3. Ejecutar **058 completo** una sola vez.
4. Verificar tablas M18:

```sql
select table_name from information_schema.tables
where table_schema = 'public' and table_name like 'm18_%'
order by 1;
-- esperado: m18_community_events, m18_event_registrations, m18_event_reminders
```

5. Verificar permisos:

```sql
select code from public.organization_permissions
where code in ('event.view', 'event.manage')
order by 1;
```

6. Verificar RPCs públicas:

```sql
select proname from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and proname like 'm18_%public%'
order by 1;
```

7. Smoke RLS (usuario autenticado sin permiso org → fallo esperado en mutación org).
8. Activar `useSupabase=true` en build de prueba y validar listados públicos desde app.

## Orden obligatorio

```text
001–057 → 058
```

No aplicar 058 antes de 057 (dependencias `organizations`, `has_org_permission`, `pets`, `m16_shelter_profiles`).

## Límites

- No resetear base ni borrar datos de producción.
- No usar service role desde Android.
- Pagos / venta de entradas permanecen fuera de alcance (M24).
- Recordatorios M06 no habilitados en Bloque 2.

## Validación remota pendiente

Tras aplicación en staging:

- Listar eventos publicados vía `m18_list_public_events`
- Crear evento borrador vía `m18_create_event` (org elegible + `event.manage`)
- Inscribirse autenticado vía `m18_register_for_event`
- Confirmar que DRAFT no aparece en superficies públicas
- Confirmar JSON público sin `organization_id` ni `user_id`

## Rollback

No hay rollback automático. Ante fallo parcial, restaurar desde backup de staging o re-ejecutar en entorno limpio.

## Referencias

- `docs/03-modulos/M18-Bloque-2-auditoria.md`
- `docs/03-modulos/M18-Bloque-4-validacion.md`
- `docs/03-modulos/M18-cierre-global-preparacion.md`
- `docs/02-arquitectura/M18-arquitectura-eventos.md`
