# M14 Bloque 2 — Validación local

```text
M14 BLOQUE 2 CERRADO LOCALMENTE
MIGRACIÓN 050 PENDIENTE DE APLICACIÓN REMOTA
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```

## Checklist

| # | Criterio | Resultado |
|---|----------|-----------|
| 1 | Autoridad M08 vía `m08_actor_has_active_responsibility` | PASS |
| 2 | `public_code` con `extensions.gen_random_bytes` | PASS |
| 3 | Migración 050 única nueva; 001–049 intactas; sin 051 | PASS |
| 4 | 5 tablas + unicidad no final por mascota | PASS |
| 5 | 18 RPC cliente | PASS |
| 6 | anon solo en RPC pública | PASS |
| 7 | Helpers protegidos; sin DML directo | PASS |
| 8 | 9 permisos passport.* | PASS |
| 9 | Sin resolución remota / sin historia clínica | PASS |
| 10 | Repos Supabase + DataProvider | PASS |
| 11 | Guard CI → 050 | PASS |
| 12 | Tests focalizados | PASS — 143/143 (M14 + regresiones M07/M08/M12/M13) |
| 13 | `compileLocalDebugKotlin` | PASS |
