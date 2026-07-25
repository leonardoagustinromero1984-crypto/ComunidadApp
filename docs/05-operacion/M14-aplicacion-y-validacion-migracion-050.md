# Operación — migración 050 (M14 pasaportes)

**LeoVer** · Supabase de pruebas.

## Estado remoto confirmado

```text
050 aplicada (Success, versión corregida)
Primer intento falló por delimitadores PL/pgSQL (sin commit)
Validación inicial 17/18 PASS
15 privilegios residuales → corregidos con 051
050 permanece canónica e intacta en semántica (solo $$ reconciliados en repo)
```

## Archivo canónico

```text
supabase/migrations/050_m14_pet_passports_and_credentials.sql
```

### Corrección sintáctica reconciliada en repo

En `m14_archive_my_pet_passport` y `m14_create_verification_request`:

- `as $` → `as $$`
- `$;` → `$$;`

Sin cambios funcionales adicionales. El archivo versionado debe coincidir con el SQL aplicado remotamente.

## Validación estructural

- Inicial post-050: **17/18** (fallo `DML_DIRECTO_CLIENTE` = 15).
- Tras 051: validación final **18/18 pendiente de confirmación** del operador.

## Smoke remoto

Pendiente de cierre de validación remota Bloque 2.

## Límites

- No reejecutar ni editar 050.
- Defectos posteriores → **052** (051 ya aplicada).
- M12/M13 smokes y cierres oficiales siguen pendientes externos.
