# Operación — aplicación y validación de migración 049 (M13 revisión humana)

**LeoVer** · Supabase de pruebas.

Estado operativo confirmado: **049 aplicada**; validación estructural **14/14 PASS**. Smoke funcional remoto: **PENDIENTE EXTERNO** (no PASS). Esta guía no reaplica SQL desde Cursor.

## Archivo

```text
supabase/migrations/049_m13_match_review_workflow.sql
```

Prerrequisito: **048** aplicada y validación estructural 13/13 PASS.

## Orden manual

1. Snapshot/backup si el proceso lo exige.
2. Ejecutar el contenido completo del archivo (begin…commit).
3. Si Success → **no reejecutar**.
4. Validación estructural abajo.
5. Smoke remoto pendiente (no simular aquí).
6. **No editar 049** después de aplicada; defectos posteriores → **050**.

## Validación estructural (orientativa)

```sql
select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in (
    'm13_open_match_review',
    'm13_confirm_match_candidate',
    'm13_reject_match_candidate',
    'm13_mark_match_inconclusive',
    'm13_withdraw_match_candidate',
    'm13_expire_match_candidate',
    'm13_list_match_decisions',
    'm13_list_match_status_history'
  )
order by 1;

select indexname from pg_indexes
where tablename = 'lost_found_match_decisions'
  and indexname = 'lost_found_match_decisions_candidate_uniq';
```

## Smoke remoto (pendiente)

1. Abrir revisión desde PROPOSED.
2. Confirmar / rechazar / inconclusa desde UNDER_REVIEW.
3. Reintento idempotente.
4. Conflicto entre confirm y reject concurrentes.
5. Withdraw/expire.
6. Listar decisiones e historial.
7. Verificar que `lost_found_posts.status` no cambia al confirmar.
8. Reportante sin autoridad → denegado.

## Límites

- No modificar 001–048.
- No crear 050 en este bloque.
- No declarar M12/M13 cerrados.
