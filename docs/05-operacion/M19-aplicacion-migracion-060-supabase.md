# Operación — migración 060 (M19 red social y contenido)

**LeoVer** · Supabase staging/pruebas · **no producción**.

## Archivo canónico

```text
supabase/migrations/060_m19_social_posts_and_engagement.sql
```

## Estado actual (post Bloques 1–2)

```text
060 — CREADA, NO APLICADA
061 — NO EXISTE
Validación remota M19 — PENDIENTE
```

## Estado actual (post cierre M19)

```text
060 — APLICADA EN STAGING NO PRODUCTIVO
061 — APLICADA EN STAGING NO PRODUCTIVO
Validación remota M19 — 105/105 PASS
```

## Prerrequisitos

- Migraciones 001–059 aplicadas en orden en entorno **no productivo**.
- Permisos M03 `social.view` / `social.manage` disponibles (insertados en 060).
- Operador confirma proyecto Supabase de staging/pruebas.

## Procedimiento

1. Confirmar entorno **no productivo** (no producción).
2. Abrir SQL Editor en Supabase Dashboard.
3. Ejecutar **060 completo** una sola vez.
4. Verificar tablas M19:

```sql
select table_name from information_schema.tables
where table_schema = 'public' and table_name like 'm19_%'
order by table_name;
```

5. Verificar permisos:

```sql
select code from public.organization_permissions
where code like 'social.%';
```

6. Smoke RPC feed público:

```sql
select public.m19_list_public_feed(null, null, true);
```

## Rollback

- No editar 060 retroactivamente.
- Correcciones forward-only en migración 061+ si hiciera falta.
- Preservar filas existentes para auditoría.

## App

Con `useSupabase = true`, `DataProvider.m19SocialRepository` usa `SupabaseM19SocialRepository`.

Con mock local, el feed sigue operativo sin 060 aplicada.
