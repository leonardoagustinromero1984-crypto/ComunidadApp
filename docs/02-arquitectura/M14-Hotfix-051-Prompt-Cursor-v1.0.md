# Cursor — Hotfix M14 migración 051: privilegios residuales de tablas

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `6cf889679bd27fd142f39c104e8b7c92881b20a7`.
- Migración 050 aplicada remotamente.
- Validación estructural 050: 17/18 PASS.
- Único fallo:
  - `DML_DIRECTO_CLIENTE`
  - esperado `0`
  - obtenido `15`
- Causa confirmada: quedaron `TRUNCATE`, `REFERENCES` y `TRIGGER` para `authenticated` en 5 tablas.
- No modificar 050 porque ya fue aplicada.
- Próxima migración: 051.

## Objetivo

Crear una migración forward-only que elimine todos los privilegios directos residuales de cliente sobre las cinco tablas M14 y conserve únicamente `SELECT` para `authenticated`, protegido por RLS.

## Reglas

- Trabajar directamente sobre `main`.
- Sin ramas, backups ni commits intermedios.
- Un único commit y push.
- No modificar migraciones 001–050.
- Crear únicamente:
  `supabase/migrations/051_m14_revoke_residual_table_privileges.sql`
- No aplicar SQL remotamente desde Cursor.
- No crear 052.
- No modificar tablas, policies, RPC ni datos.
- No generar APK.
- Actualizar deliberadamente el guard CI de highest migration 050 a 051.
- Actualizar las guardas estáticas relacionadas.
- Agregar una prueba que compruebe ausencia de:
  `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, `REFERENCES`, `TRIGGER`
  para `authenticated` y `anon`.
- Conservar `SELECT` para `authenticated`.
- `anon` debe quedar sin privilegios directos de tabla.
- RPC y grants de funciones deben permanecer intactos.
- M12/M13 siguen pendientes externos.

## SQL obligatorio

Usar exactamente esta semántica:

```sql
begin;

revoke all privileges on table public.pet_passports
  from authenticated, anon;
revoke all privileges on table public.pet_passport_credentials
  from authenticated, anon;
revoke all privileges on table public.pet_passport_verification_requests
  from authenticated, anon;
revoke all privileges on table public.pet_passport_verification_decisions
  from authenticated, anon;
revoke all privileges on table public.pet_passport_status_history
  from authenticated, anon;

grant select on table public.pet_passports to authenticated;
grant select on table public.pet_passport_credentials to authenticated;
grant select on table public.pet_passport_verification_requests to authenticated;
grant select on table public.pet_passport_verification_decisions to authenticated;
grant select on table public.pet_passport_status_history to authenticated;

commit;
```

## Validaciones locales

Ejecutar:

```powershell
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Ejecutar pruebas focalizadas M14 y guards de migración/privilegios.

No hace falta compilar Kotlin si no cambia código Android. Si cambia Kotlin accidentalmente, detenerse y revisar.

## Documentación

Crear:

```text
docs/02-arquitectura/M14-Migracion-051-validacion.md
docs/05-operacion/M14-aplicacion-y-validacion-migracion-051.md
```

Actualizar la guía de 050 para registrar:

```text
050 aplicada
17/18 controles PASS
Defecto de grants corregido mediante 051
050 permanece intacta
```

## Git

Commit único:

```text
fix(m14): revoke residual passport table privileges
```

Push a `origin/main`.

## Estado final permitido

```text
M14 HOTFIX 051 CERRADO LOCALMENTE
MIGRACIÓN 051 PENDIENTE DE APLICACIÓN REMOTA
M14 BLOQUE 2 VALIDACIÓN REMOTA PENDIENTE
```
