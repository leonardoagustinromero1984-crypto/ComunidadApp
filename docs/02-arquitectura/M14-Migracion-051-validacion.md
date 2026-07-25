# M14 Migración 051 — Validación (reconciliación repo)

```text
M14 HOTFIX 051 VERSIONADO LOCALMENTE
MIGRACIÓN 050 APLICADA REMOTAMENTE
MIGRACIÓN 051 APLICADA REMOTAMENTE
VALIDACIÓN ESTRUCTURAL FINAL PENDIENTE DE CONFIRMACIÓN
```

## Incidente

1. Primer intento de aplicar **050** falló por cuatro delimitadores PL/pgSQL (`as $` / `$;` en lugar de `$$`) → transacción sin `commit`.
2. Se aplicó la **050 corregida** completa → Success.
3. Validación inicial: **17/18 PASS**.
4. Único defecto: **15** privilegios residuales (`TRUNCATE`/`REFERENCES`/`TRIGGER` × 5 tablas) en `authenticated`.
5. **051** aplicada manualmente → Success.
6. Validación final **18/18**: pendiente de confirmación del usuario (no inventada aquí).

## Archivos canónicos (coinciden con lo aplicado)

```text
supabase/migrations/050_m14_pet_passports_and_credentials.sql
supabase/migrations/051_m14_revoke_residual_table_privileges.sql
```

- 050 en repo: solo reconciliación de los cuatro delimitadores.
- 051: `REVOKE ALL` + `GRANT SELECT` a `authenticated`; `anon` sin privilegios de tabla.
- No editar 050/051 en cambios futuros; correcciones posteriores → **052**.

## Checklist local

| # | Criterio | Resultado |
|---|----------|-----------|
| 1 | 001–049 intactas | PASS |
| 2 | 050 delimitadores `$$` válidos | PASS |
| 3 | 051 presente; sin 052 | PASS |
| 4 | 051: 5 revoke all + 5 grant select authenticated | PASS |
| 5 | Sin grant DML residual a clientes | PASS |
| 6 | Guard CI highest = 051 | PASS |
| 7 | Sin SQL remoto desde Cursor | PASS |
