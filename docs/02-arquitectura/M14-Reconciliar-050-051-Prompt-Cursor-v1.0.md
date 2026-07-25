# Cursor — M14 reconciliación de migraciones 050/051 ya aplicadas

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Situación real confirmada

- Rama remota previa: `main`.
- HEAD remoto mínimo: `6cf889679bd27fd142f39c104e8b7c92881b20a7`.
- La migración 050 del commit original tenía cuatro delimitadores PL/pgSQL incorrectos:
  - dos aperturas `as $` que debían ser `as $$`;
  - dos cierres `$;` que debían ser `$$;`.
- La primera ejecución de 050 falló dentro de `begin`, por lo que no llegó a `commit`.
- Se ejecutó después la 050 corregida completa y dio `Success`.
- Validación 050 posterior: 17/18 PASS.
- Único defecto: 15 privilegios directos residuales:
  - 5 tablas × `TRUNCATE`, `REFERENCES`, `TRIGGER`.
- La migración 051 correctiva ya fue ejecutada manualmente en Supabase y dio `Success`.
- Falta alinear el repositorio, los guards y la documentación con el SQL realmente aplicado.
- No aplicar SQL desde Cursor.

## Objetivo

Dejar el repositorio exactamente alineado con las migraciones ejecutadas:

1. corregir el archivo versionado de 050 para que coincida byte a byte en semántica con la versión aplicada;
2. agregar la migración 051 exacta ya aplicada;
3. actualizar el techo de migraciones y sus guardas a 051;
4. documentar el incidente y la validación;
5. hacer un único commit y push.

La corrección de 050 es únicamente una reconciliación del archivo con el SQL que efectivamente fue aplicado. No introducir cambios funcionales adicionales.

## Reglas

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 001–049.
- En 050 cambiar únicamente los cuatro delimitadores defectuosos.
- Crear únicamente la migración 051 ya aplicada.
- No crear 052.
- No aplicar SQL remotamente.
- No generar APK.
- No modificar código Android salvo que una guarda estrictamente necesaria lo requiera.
- No debilitar CI.
- No declarar M14 Bloque 2 remoto PASS sin la validación 18/18 proporcionada por el usuario.
- M12/M13 conservan sus pendientes externos.

## Paso 1 — Verificación inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
git rev-parse origin/main
```

No usar `reset`, `restore` ni `clean`.

Inspeccionar si el usuario ya reemplazó localmente el archivo 050 corregido. Preservar ese trabajo.

## Paso 2 — Reconciliar 050

Archivo:

```text
supabase/migrations/050_m14_pet_passports_and_credentials.sql
```

Cambiar únicamente:

### Función `m14_archive_my_pet_passport`

```sql
language plpgsql security definer set search_path = public as $$
...
$$;
```

### Función `m14_create_verification_request`

```sql
language plpgsql security definer set search_path = public as $$
...
$$;
```

No cambiar firmas, tablas, grants, policies, lógica ni comentarios salvo una nota mínima de corrección sintáctica si ya existe el patrón documental.

Confirmar que no queda ninguna línea:

```text
as $
$;
```

donde debía existir `$$`.

## Paso 3 — Agregar 051 exacta

Crear:

```text
supabase/migrations/051_m14_revoke_residual_table_privileges.sql
```

Contenido semántico obligatorio:

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

No agregar tablas, policies, funciones, datos ni permisos adicionales.

## Paso 4 — Guard CI

Actualizar deliberadamente:

```text
scripts/ci/m07_quality_checks.sh
```

de highest migration 050 a 051.

Actualizar únicamente las pruebas/guardas estáticas que verifican el techo de migraciones.

El control debe:

- exigir 051;
- fallar ante una futura 052 no incorporada;
- comprobar que 051 revoca todos los privilegios directos;
- comprobar que authenticated conserva únicamente SELECT;
- comprobar que anon no recibe privilegios directos;
- mantener intactos grants de RPC.

No hacer reemplazos globales.

## Paso 5 — Tests focalizados

Agregar o ajustar pruebas para cubrir:

1. 050 usa delimitadores PL/pgSQL válidos;
2. 001–049 intactas;
3. solo 051 nueva;
4. 051 contiene cinco `REVOKE ALL PRIVILEGES`;
5. 051 restaura cinco `GRANT SELECT` solo a authenticated;
6. anon sin privilegios de tabla;
7. sin INSERT/UPDATE/DELETE/TRUNCATE/REFERENCES/TRIGGER cliente;
8. RPC y grants de funciones intactos;
9. highest migration 051;
10. sin migración 052;
11. sin secretos.

Ejecutar:

```powershell
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Ejecutar pruebas focalizadas M14 y guards de migraciones.

No compilar Kotlin si no cambió código Android.

## Paso 6 — Documentación

Crear:

```text
docs/02-arquitectura/M14-Migracion-051-validacion.md
docs/05-operacion/M14-aplicacion-y-validacion-migracion-051.md
```

Actualizar:

```text
docs/05-operacion/M14-aplicacion-y-validacion-migracion-050.md
docs/02-arquitectura/M14-Bloque-2-validacion.md
docs/03-modulos/M14-persistencia-y-seguridad.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Registrar exactamente:

- primer intento 050 falló por delimitadores y transacción sin commit;
- 050 corregida se aplicó completa;
- validación inicial 17/18;
- 15 privilegios residuales detectados;
- 051 aplicada manualmente con Success;
- validación final 18/18 todavía pendiente de confirmación del usuario;
- 050 y 051 son las versiones canónicas aplicadas;
- no editar migraciones aplicadas en cambios futuros;
- toda corrección SQL posterior empieza en 052.

No inventar el resultado final.

## Paso 7 — Verificación final

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- 001–049 intactas;
- 050 solo cambia cuatro delimitadores;
- 051 exacta;
- sin 052;
- sin secretos;
- sin binarios;
- CI no debilitado.

## Paso 8 — Git

Commit único:

```text
fix(m14): reconcile applied passport migrations
```

Push:

```powershell
git push origin main
```

## Entrega final

Informar:

1. Estado inicial.
2. Estado local previo de 050.
3. Cuatro delimitadores corregidos.
4. Archivo 051.
5. Guard CI.
6. Pruebas.
7. Total PASS.
8. `bash -n`.
9. Quality script.
10. Documentación.
11. Migraciones intactas.
12. Confirmación de no SQL remoto.
13. SHA.
14. Push.
15. `git status -sb`.

## Estado final permitido

```text
M14 HOTFIX 051 VERSIONADO LOCALMENTE
MIGRACIÓN 050 APLICADA REMOTAMENTE
MIGRACIÓN 051 APLICADA REMOTAMENTE
VALIDACIÓN ESTRUCTURAL FINAL PENDIENTE DE CONFIRMACIÓN
```
