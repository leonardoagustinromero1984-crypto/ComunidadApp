# M02 — Bootstrap del primer SUPERADMIN

El primer SUPERADMIN **no** se crea desde la app Android.

## Procedimiento (staging)

1. Aplicar migraciones `014`…`018` en staging (si aún no están).
2. Obtener el UUID de `auth.users` del operador (Dashboard / SQL), **fuera del repo**.
3. Ejecutar en SQL editor (reemplazar el placeholder):

```sql
-- NO pegar este UUID en el repositorio.
select public.ensure_default_user_role('<UUID_OPERADOR>'::uuid);

insert into public.user_role_assignments (user_id, role_id, assigned_by)
select
    '<UUID_OPERADOR>'::uuid,
    r.id,
    null
from public.platform_roles r
where r.code = 'SUPERADMIN'
  and not exists (
      select 1 from public.user_role_assignments a
      where a.user_id = '<UUID_OPERADOR>'::uuid
        and a.role_id = r.id
        and a.revoked_at is null
  );

insert into public.role_assignment_history (
    user_id, role_code, action, previous_state, new_state,
    reason_code, changed_by
) values (
    '<UUID_OPERADOR>'::uuid,
    'SUPERADMIN',
    'ASSIGN',
    null,
    'ACTIVE',
    'manual_admin',
    '<UUID_OPERADOR>'::uuid
);
```

4. Verificar: `select public.get_my_platform_roles();` con sesión del operador.
5. Auditar la operación (fecha, operador, ticket).

## Prohibiciones

- No versionar email, UUID ni secretos.
- No usar producción para el primer bootstrap de prueba.
- No crear SUPERADMIN desde un cliente Android.
