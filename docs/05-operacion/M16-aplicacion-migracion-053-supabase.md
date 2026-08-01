# Operación — migración 053 (M16 refugios públicos)

**LeoVer** · Supabase staging/pruebas.

## Archivo canónico

```text
supabase/migrations/053_m16_shelter_profiles_and_public_access.sql
```

## Prerrequisitos

- Migraciones 001–052 aplicadas en orden.
- Entorno **no productivo** confirmado por operador.

## Procedimiento

1. Confirmar proyecto Supabase de staging/pruebas (no producción).
2. Abrir SQL Editor en Supabase Dashboard.
3. Pegar **archivo completo** 053.
4. Ejecutar una sola vez.
5. Verificar:

```sql
select count(*) from information_schema.tables
where table_schema = 'public' and table_name like 'm16_shelter%';
-- esperado: 5 tablas

select proname from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and proname like 'm16_%'
order by 1;
```

## Estado

```text
PENDIENTE DE APPLY REMOTO AUTORIZADO
No aplicar desde Cursor ni CI automático.
```

## Límites

- No crear migración 054 desde esta tarea.
- No resetear base ni borrar datos.
