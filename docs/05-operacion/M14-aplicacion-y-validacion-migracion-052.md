# Operación — aplicación y validación de migración 052 (M14 verificación)

**LeoVer** · aplicar **solo** con autorización explícita. No aplicar desde Cursor.

## Archivo

```text
supabase/migrations/052_m14_credential_verification_and_public_access.sql
```

Prerrequisito: **050** y **051** aplicadas (18/18 PASS).

## Orden

1. Snapshot si el proceso lo exige.
2. Ejecutar 052 una sola vez (`begin`…`commit`).
3. Success → no reejecutar.
4. Validación estructural abajo.
5. Smoke remoto (pendiente).
6. No editar 052 tras apply; defectos → **053**.

## Validación estructural (orientativa)

```sql
select conname, pg_get_constraintdef(oid)
from pg_constraint
where conrelid = 'public.pet_passport_verification_requests'::regclass
  and conname like '%status%';

select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in (
    'm14_open_verification_review',
    'm14_approve_verification_request',
    'm14_reject_verification_request',
    'm14_expire_verification_request',
    'm14_get_verification_decision',
    'm14_list_verification_decisions',
    'm14_issue_verified_credential',
    'm14_revoke_verified_credential',
    'm14_rotate_public_code',
    'm14_list_passport_status_history'
  )
order by 1;
```

## Smoke remoto (pendiente)

1. Abrir revisión → UNDER_REVIEW.
2. Aprobar / rechazar solo desde UNDER_REVIEW.
3. Expirar PENDING/UNDER_REVIEW.
4. Segunda decisión → denegada / idempotente.
5. Emisión directa sin ser responsable M08.
6. Revocar VERIFIED como emisor.
7. Rotar public_code; passport_number intacto.
8. Deep link solo con public_code.

## Límites

- No modificar 001–051.
- No crear 053 en este bloque.
- M12/M13 smokes oficiales pendientes.
