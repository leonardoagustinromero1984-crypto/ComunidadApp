# M14 — Persistencia y seguridad

## Migraciones

```text
supabase/migrations/050_m14_pet_passports_and_credentials.sql
supabase/migrations/051_m14_revoke_residual_table_privileges.sql
supabase/migrations/052_m14_credential_verification_and_public_access.sql
```

- **050/051** aplicadas remotamente; validación estructural **18/18 PASS**.
- **052** creada (Bloque 3); **no aplicada** remotamente.
- No editar 050–052 tras apply; siguientes correcciones → **053**.

## Tablas

- `pet_passports`
- `pet_passport_credentials`
- `pet_passport_verification_requests` (+ `UNDER_REVIEW` en 052)
- `pet_passport_verification_decisions` (escritura en 052)
- `pet_passport_status_history` (append-only; eventos de credencial vía metadata)

## Generación server-side

- `_m14_generate_passport_number` → `LV-AR-YYYY-XXXXXXXX`
- `_m14_generate_public_code` → `PUB-` + `extensions.gen_random_bytes(16)`

## Autoridad

Actor = `auth.uid()`. M08 responsabilidad + permisos `passport.*` + emisor org/M12. Sin confiar en IDs de actor/rol de UI.

## RPC cliente

- 050: 18 RPC base (anon solo en proyección pública).
- 052: +10 RPC verificación/emisión/rotación/historial (solo authenticated).

## Seguridad

- SECURITY DEFINER + `search_path = public`
- Tabla cliente: solo `SELECT` authenticated (RLS); anon sin grants de tabla
- Helpers `_m14_*` revocados
- Sin DML directo; sin historia clínica; sin `service_role` en Android

## CI

Guard highest migration: **052**.
