# M14 Bloque 2 — Validación

```text
M14 BLOQUE 2 CERRADO LOCALMENTE
MIGRACIÓN 050 APLICADA REMOTAMENTE
MIGRACIÓN 051 APLICADA REMOTAMENTE
VALIDACIÓN ESTRUCTURAL FINAL PENDIENTE DE CONFIRMACIÓN
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```

## Incidente post-apply (resumen)

1. Primer apply 050 falló por delimitadores `$`/`$;` (sin commit).
2. 050 corregida aplicada → Success; validación **17/18**.
3. 15 privilegios residuales; **051** aplicada → Success.
4. Validación final **18/18** aún pendiente de confirmación externa.

## Checklist local (Bloque 2 + reconciliación)

| # | Criterio | Resultado |
|---|----------|-----------|
| 1 | Autoridad M08 vía `m08_actor_has_active_responsibility` | PASS |
| 2 | `public_code` con `extensions.gen_random_bytes` | PASS |
| 3 | 001–049 intactas; 050+051 canónicas; sin 052 | PASS |
| 4 | 5 tablas + unicidad no final por mascota | PASS |
| 5 | 18 RPC cliente | PASS |
| 6 | anon solo en RPC pública | PASS |
| 7 | Helpers protegidos; privilegios tabla solo SELECT authenticated (051) | PASS |
| 8 | 9 permisos passport.* | PASS |
| 9 | Sin resolución remota / sin historia clínica | PASS |
| 10 | Repos Supabase + DataProvider | PASS |
| 11 | Guard CI → 051 | PASS |
| 12 | Delimitadores `$$` reconciliados en 050 | PASS |
| 13 | Tests focalizados | PASS (ver corrida reconciliación) |
