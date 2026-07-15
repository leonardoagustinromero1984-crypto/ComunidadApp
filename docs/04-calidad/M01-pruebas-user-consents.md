# M01 — Pruebas SQL/RLS para `user_consents`

**Migración:** `supabase/migrations/014_user_consents.sql`  
**Entorno:** solo proyecto de prueba / local. **No ejecutar destructivo en producción.**

## Precondiciones

1. Migraciones `001`–`013` aplicadas.
2. Aplicar `014_user_consents.sql`.
3. Rol de prueba con privilegios suficientes (SQL editor / service role en staging).

## Casos

### 1. Tabla y restricciones

```sql
select column_name, is_nullable, data_type
from information_schema.columns
where table_schema = 'public' and table_name = 'user_consents'
order by ordinal_position;

-- Debe fallar (versions blank):
-- insert into public.user_consents (user_id, terms_version, privacy_version, source)
-- values ('00000000-0000-0000-0000-000000000001', '', 'x', 'registration');
```

### 2. Trigger crea usuario + consentimiento

Crear un usuario Auth de prueba vía Dashboard o Admin API con metadata:

```json
{
  "name": "Test Consent",
  "terms_version": "draft-terms-2026-07",
  "privacy_version": "draft-privacy-2026-07",
  "consent_locale": "es-AR",
  "consent_source": "registration"
}
```

Verificar:

```sql
select id, email, account_type, profile_private from public.users where email = '<test>';
select user_id, terms_version, privacy_version, source, accepted_at
from public.user_consents where user_id = '<uuid>';
```

- `account_type` = `PERSON`
- `profile_private` = true
- `accepted_at` ≈ now() (hora de servidor)
- Sin `account_type` arbitrario del cliente

### 3. Metadata incompleta → rollback

Signup sin `terms_version`/`privacy_version` debe fallar (`CONSENT_REQUIRED`) y **no** dejar fila huérfana en `public.users` para ese `auth.users` (transacción del insert Auth).

### 4. Idempotencia

Re-ejecutar insert de consentimiento con mismo `(user_id, terms_version, privacy_version)` → `ON CONFLICT DO NOTHING` (sin error).

### 5. RLS

Como usuario A (JWT A):

```sql
select * from public.user_consents; -- solo filas de A
```

Como usuario B: no ve filas de A.

INSERT directo como `authenticated` debe fallar (sin policy insert).

### 6. Cascade

Eliminar `auth.users` de prueba → `user_consents` y `public.users` cascadean.

### 7. Perfil sigue funcionando

Confirmar que `handle_new_user` sigue creando `public.users` con columnas de `009` (`profile_private`).

## Resultado de esta etapa

| Caso | Ejecutado en cloud real |
|------|-------------------------|
| Aplicación migración 014 | **No verificado** en esta sesión (sin evidencia de proyecto) |
| RLS / trigger en vivo | **No verificado** |

Completar checklist en staging antes de release.
