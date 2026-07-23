# M10 — Aplicación manual de migraciones en Supabase

LeoVer. Aplicar **solo** en un proyecto de pruebas primero. **No** aplicar automáticamente desde la app Android.

## Orden exacto

```text
040_m10_foster_homes_core.sql
041_m10_foster_care_management.sql
```

Ejecutar cada archivo **completo**, en ese orden. No invertir.

## Antes de aplicar

* Confirmar el proyecto Supabase de **pruebas** (no producción).
* Confirmar que las migraciones **037–039** (M09) ya están aplicadas si se necesita integración con mascotas/responsabilidades M08–M09.
* Revisar que 001–039 (o el baseline del entorno) esté consistente.
* No aplicar primero en producción.
* Si hay datos importantes, realizar backup del proyecto.

## Ejecución

1. Abrir el SQL Editor (o pipeline controlado) del proyecto de pruebas.
2. Pegar/ejecutar el contenido íntegro de `040_m10_foster_homes_core.sql`.
3. Esperar resultado correcto (sin error).
4. Ejecutar íntegro `041_m10_foster_care_management.sql`.
5. No continuar si uno falla.
6. No ejecutar fragmentos aislados salvo una corrección controlada y documentada.

## Verificación

### Tablas `foster_%` (M10)

```sql
select table_schema, table_name
from information_schema.tables
where table_schema = 'public'
  and table_name like 'foster_%'
order by table_name;
```

Esperado (entre otras posibles legacy):  
`foster_care_requests`, `foster_home_profiles`, `foster_placements`,  
`foster_expenses`, `foster_evolution_entries`, `foster_help_requests`, `foster_help_contributions`.

### Funciones `m10_%`

```sql
select n.nspname, p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname like 'm10_%'
order by p.proname;
```

### RLS solo sobre tablas (no índices)

```sql
select c.relname, c.relrowsecurity
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind in ('r', 'p')
  and c.relname like 'foster_%'
order by c.relname;
```

### Políticas

```sql
select schemaname, tablename, policyname, cmd
from pg_policies
where schemaname = 'public'
  and tablename like 'foster_%'
order by tablename, policyname;
```

### Índices y constraints

```sql
select indexname, tablename
from pg_indexes
where schemaname = 'public'
  and tablename like 'foster_%'
order by tablename, indexname;

select conname, conrelid::regclass as table_name
from pg_constraint
where connamespace = 'public'::regnamespace
  and conrelid::regclass::text like 'foster_%'
order by 2, 1;
```

### Columnas de finalización

```sql
select column_name, data_type
from information_schema.columns
where table_schema = 'public'
  and table_name = 'foster_placements'
  and column_name in ('end_reason', 'end_notes', 'ended_by', 'ended_at')
order by column_name;
```

### `TEMPORARY_CUSTODIAN` (M08)

```sql
select id, pet_id, person_id, role_code, status
from public.pet_responsibilities
where role_code = 'TEMPORARY_CUSTODIAN'
order by created_at desc
limit 20;
```

## Problemas por reejecución

Las políticas M10 usan `drop policy if exists` antes de `create policy`, por lo que una **reejecución parcial** no debería fallar por “policy already exists”.

Esto **no** garantiza que toda la migración sea idempotente con datos reales (inserts, secuencias de estado, etc.). Preferir apply una vez por entorno limpio o con control explícito.

## Smoke manual de base (no automatizado aquí)

1. Crear hogar  
2. Activar  
3. Enviar solicitud  
4. Aceptar  
5. Confirmar reserva (capacidad reservada, ocupación sin subir)  
6. Iniciar alojamiento  
7. Verificar `TEMPORARY_CUSTODIAN`  
8. Registrar gasto (ref `m05://` o `file_asset:`)  
9. Registrar evolución  
10. Crear pedido de ayuda  
11. Registrar aporte  
12. Completar alojamiento  
13. Verificar liberación de capacidad / disponibilidad  
14. Verificar revocación temporal  
15. Verificar que `PRINCIPAL` permanezca  

No ejecutar este smoke desde CI ni desde esta guía de forma automática.

## Pendientes

* Apply remoto 040 → 041 en pruebas  
* Smoke manual  
* Apply producción solo tras validación  
* APK solo cuando se solicite  
* Pagos / reputación / push fuera de alcance M10
