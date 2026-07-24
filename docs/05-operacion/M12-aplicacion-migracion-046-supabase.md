# M12 — Aplicación migración 046 (Supabase)

**LeoVer.** Guía operativa. **No afirma que 046 esté aplicada.**

## Precondiciones

1. Migraciones **040–045** ya aplicadas en el ambiente objetivo.
2. Working tree con archivo `supabase/migrations/046_m12_veterinary_profiles_and_services.sql`.
3. No modificar 040–045.

## Aplicación íntegra

Ejecutar **todo** el archivo 046 en una sola transacción (`begin`…`commit`) vía el canal autorizado del proyecto (SQL editor / CLI).  
**Cursor no aplica SQL remoto en este bloque.**

## Verificación estructural

```sql
-- Tablas
select tablename from pg_tables
where schemaname = 'public' and tablename like 'veterinary_%'
order by 1;

-- RLS
select relname, relrowsecurity
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and relname like 'veterinary_%';

-- Policies
select tablename, policyname from pg_policies
where schemaname = 'public' and tablename like 'veterinary_%';

-- Permisos
select code from public.organization_permissions where code like 'veterinary.%' order by 1;

-- RPC m12_*
select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname like 'm12_%'
order by 1;
```

## Seguridad esperada

- RLS habilitado en las 6 tablas.
- Sin GRANT INSERT/UPDATE/DELETE a `anon`/`authenticated` sobre tablas.
- RPC cliente: EXECUTE solo `authenticated`.
- Helpers `_m12_*`: sin EXECUTE a `anon`/`authenticated`.
- `SECURITY DEFINER` + `search_path = public`.
- Actor desde `auth.uid()`.
- Sin `service_role` desde Android.

## Smoke funcional (post-apply, manual)

1. Crear borrador de clínica (org ACTIVE + permiso `veterinary.profile.manage`).
2. Crear servicio + reemplazar horarios.
3. Activar clínica.
4. Crear/vincular profesional; reemplazar especialidades.
5. Solicitar verificación; revisar con rol que tenga `organizations.review_verification` (M04).
6. Listar directorio público y detalle redactado.

**Fuera de alcance 046:** agenda, turnos, historia clínica, pagos.
