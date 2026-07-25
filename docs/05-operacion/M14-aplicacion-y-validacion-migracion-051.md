# Operación — migración 051 (privilegios residuales M14)

**LeoVer** · Supabase de pruebas.

## Estado remoto confirmado

```text
051 aplicada (Success)
050 aplicada (Success, versión corregida)
Validación estructural final 18/18: pendiente de confirmación
```

## Archivo canónico

```text
supabase/migrations/051_m14_revoke_residual_table_privileges.sql
```

Semántica aplicada:

1. `REVOKE ALL PRIVILEGES` en las 5 tablas M14 desde `authenticated` y `anon`.
2. `GRANT SELECT` solo a `authenticated` (RLS intacto).
3. Sin cambios de schema, policies, RPC ni datos.

## Post-apply (ya ejecutado remotamente)

No reejecutar 051. No editar 051. Defectos posteriores → **052**.

## Validación de privilegios (orientativa)

Esperado tras 051:

- `authenticated`: únicamente `SELECT` en las 5 tablas.
- `anon`: sin privilegios directos de tabla.
- Sin `INSERT`/`UPDATE`/`DELETE`/`TRUNCATE`/`REFERENCES`/`TRIGGER` para clientes.
- Grants EXECUTE de RPC 050 intactos.

## Límites

- No modificar 001–050 en cambios futuros (050 ya reconciliada en repo).
- No crear 052 en esta reconciliación.
- M12/M13 pendientes externos preservados.
