# Operación — aplicación y validación de migración 050 (M14 pasaportes)

**LeoVer** · Supabase de pruebas · aplicar **solo** con autorización explícita.

Esta guía **no** aplica la migración desde Cursor. 050 queda **creada y no aplicada**.

## Archivo

```text
supabase/migrations/050_m14_pet_passports_and_credentials.sql
```

Prerrequisito: migraciones **001–049** aplicadas (incluidas 048/049 M13).

## Orden manual

1. Snapshot/backup si el proceso lo exige.
2. Ejecutar el contenido completo del archivo (`begin`…`commit`).
3. Si Success → **no reejecutar**.
4. Validación estructural abajo.
5. Smoke remoto pendiente (no simular aquí).
6. **No editar 050** después de aplicada; defectos posteriores → **051**.

## Validación estructural (orientativa)

```sql
select to_regclass('public.pet_passports') is not null as passports;
select to_regclass('public.pet_passport_credentials') is not null as credentials;
select to_regclass('public.pet_passport_verification_requests') is not null as requests;
select to_regclass('public.pet_passport_verification_decisions') is not null as decisions;
select to_regclass('public.pet_passport_status_history') is not null as history;

select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname like 'm14_%'
order by 1;
```

Esperado: 18 funciones `m14_*` cliente.

## Smoke remoto (pendiente)

1. Crear pasaporte sobre mascota ACTIVE con responsabilidad M08.
2. Duplicado no final → denegado.
3. Activar / actualizar / archivar.
4. Crear credencial con media `m05://`; rechazar URL http.
5. Solicitar verificación; cancelar; sin resolución final.
6. Proyección pública por `public_code` sin pet_id/user_id/microchip completo.
7. anon solo en RPC pública.

## Límites

- No modificar 001–049.
- No crear 051 en este bloque.
- No declarar M12/M13/M14 cerrados oficialmente.
- No iniciar M15.
