# M14 — Persistencia y seguridad (Bloque 2)

## Migración

```text
supabase/migrations/050_m14_pet_passports_and_credentials.sql
```

Forward-only sobre 001–049. **No aplicada** remotamente en este bloque.

## Tablas

- `pet_passports`
- `pet_passport_credentials`
- `pet_passport_verification_requests`
- `pet_passport_verification_decisions` (preparada Bloque 3; sin RPC de resolución)
- `pet_passport_status_history` (append-only)

## Generación server-side

- `_m14_generate_passport_number` → `LV-AR-YYYY-XXXXXXXX`
- `_m14_generate_public_code` → `PUB-` + `extensions.gen_random_bytes(16)` (no predecible)

## Autoridad

Actor = `auth.uid()`. Gestión vía `m08_actor_has_active_responsibility` (+ permisos `passport.*`). Sin confiar en `ownerId` de UI.

## 18 RPC cliente

Pasaporte (8), credenciales (5), solicitudes (5).  
`m14_get_public_pet_passport`: única con EXECUTE para `anon` + `authenticated`.

## Seguridad

- SECURITY DEFINER + `search_path = public`
- Sin DML directo cliente
- Helpers `_m14_*` revocados a clientes
- RLS en las 5 tablas
- Sin `service_role` en el archivo 050
- Sin resolución remota de verificaciones
- Sin historia clínica

## Android

`SupabaseM14RemoteDataSource` + `SupabaseM14*Repository` + DataProvider switch. Mocks conservados.

## CI

Guard highest migration: **050** (`scripts/ci/m07_quality_checks.sh`).
