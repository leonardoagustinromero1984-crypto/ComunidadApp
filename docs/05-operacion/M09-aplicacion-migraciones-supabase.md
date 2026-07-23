# M09 — Aplicación manual de migraciones en Supabase

Guía operativa para aplicar las migraciones del módulo de adopciones (**LeoVer** M09) en un proyecto Supabase de pruebas. **No incluye secretos ni URLs privadas.**

Estado actual del repositorio: las migraciones **037**, **038** y **039** existen en el código y están **pendientes de aplicación remota** hasta que se ejecuten manualmente.

---

## Antes de aplicar

1. Confirmar que el SQL Editor apunta al **proyecto Supabase de pruebas** correcto (no producción).
2. Verificar el estado de migraciones anteriores (hasta 036) ya aplicadas en ese entorno.
3. Si el entorno ya tiene datos relevantes, realizar un **backup lógico** (export / dump) antes de continuar.
4. **No aplicar en producción** sin validar antes en staging / pruebas.

---

## Orden exacto

Ejecutar **en este orden**, una archivo completo por vez:

1. `supabase/migrations/037_m09_adoption_publications.sql`
2. `supabase/migrations/038_m09_adoption_applications.sql`
3. `supabase/migrations/039_m09_adoption_completion_followup.sql`

No alterar el orden. No saltar archivos. No mezclar fragmentos.

---

## Aplicación (SQL Editor)

1. Abrir el **SQL Editor** del proyecto Supabase de pruebas.
2. Abrir el contenido completo de `037_m09_adoption_publications.sql` desde el repositorio.
3. Pegar y ejecutar el script **entero**. Esperar éxito.
4. Repetir con `038_m09_adoption_applications.sql`.
5. Repetir con `039_m09_adoption_completion_followup.sql`.

Cada migración incluye su propia transacción (`begin` / `commit`) donde corresponde. Si una falla, **detenerse** (ver sección Fallos).

---

## Verificación posterior

Consultas seguras de comprobación (solo lectura):

```sql
-- Tablas M09
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in (
    'adoptions',
    'adoption_applications',
    'adoption_interviews',
    'adoption_document_requirements',
    'adoption_agreements',
    'adoption_finalizations',
    'adoption_followup_plans',
    'adoption_followup_checks'
  )
order by table_name;
```

```sql
-- RLS habilitado
select c.relname as table_name, c.relrowsecurity as rls_enabled
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in (
    'adoptions',
    'adoption_applications',
    'adoption_interviews',
    'adoption_document_requirements',
    'adoption_agreements',
    'adoption_finalizations',
    'adoption_followup_plans',
    'adoption_followup_checks'
  )
order by c.relname;
```

```sql
-- Políticas
select schemaname, tablename, policyname, cmd
from pg_policies
where schemaname = 'public'
  and tablename like 'adoption%'
order by tablename, policyname;
```

```sql
-- Funciones m09_*
select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname like 'm09_%'
order by p.proname;
```

```sql
-- Índices relevantes
select indexname, tablename
from pg_indexes
where schemaname = 'public'
  and tablename like 'adoption%'
order by tablename, indexname;
```

```sql
-- Constraints de unicidad / checks (muestra)
select conname, conrelid::regclass as table_name
from pg_constraint
where conrelid::regclass::text like 'public.adoption%'
order by table_name, conname;
```

Opcional — registro de migraciones del CLI (si el proyecto lo usa):

```sql
select * from supabase_migrations.schema_migrations
order by version desc
limit 20;
```

(Si esa tabla no existe en el proyecto, omitir esta consulta.)

---

## Smoke de base de datos (manual, no ejecutar ahora)

Después del apply, en un entorno de pruebas con usuarios de prueba:

1. Crear publicación de adopción.
2. Postularse con otro usuario.
3. Aceptar candidato (publicación → `PAUSED`; **sin** transferir mascota).
4. Agendar y completar entrevista.
5. Solicitar / presentar / aprobar documentación (solo refs lógicas seguras; **nunca** bucket público `leover`).
6. Crear y aceptar acuerdo por ambas partes.
7. Ejecutar `m09_finalize_adoption`.
8. Verificar transferencia `PRINCIPAL` al adoptante.
9. Verificar historial de mascota (`ARCHIVED` + reason `ADOPTED`).
10. Verificar controles de seguimiento a 7 / 30 / 90 días.

---

## Fallos

Si una migración falla:

* **No** continuar con la siguiente.
* Copiar el **error completo**.
* **No** editar tablas a mano para “hacerla pasar”.
* Corregir el archivo SQL en el repositorio.
* Crear un **nuevo commit**.
* Volver a probar en un entorno limpio cuando sea posible.

---

## Notas de producto

* Documentos sensibles: solo referencias lógicas compatibles con M05; upload físico seguro pendiente.
* Finalización: mascota `ARCHIVED` + historial `ADOPTED` (M08 no tiene ciclo `ADOPTED`); publicación `ADOPTED`.
* Atajo legacy `m09_mark_adoption_adopted` queda bloqueado tras 039 (`ADOPTION_USE_FINALIZE`); el camino correcto es el proceso de finalización.
