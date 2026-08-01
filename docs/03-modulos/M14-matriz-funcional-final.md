# M14 — Matriz funcional final (local)

```text
M14 BLOQUE 4 CERRADO LOCALMENTE
MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
M14 SMOKE FUNCIONAL PENDIENTE EXTERNO
M14 CIERRE OFICIAL PENDIENTE
```

## Capacidades por bloque

| Capacidad | B1 | B2 | B3 | B4 | Remoto |
|-----------|----|----|----|----|--------|
| Dominio pasaporte/credencial | ✓ | ✓ | ✓ | ✓ | 050/051 PASS |
| RPC emisión base | — | ✓ | ✓ | ✓ | PASS |
| Revisión humana open/approve/reject | — | — | ✓ | ✓ | 052 pendiente |
| Rotación `public_code` | — | — | ✓ | ✓ | 052 pendiente |
| QR/deep link sin PII | ✓ | ✓ | ✓ | ✓ | local PASS |
| Expiración solicitudes/credenciales | — | — | — | ✓ | PENDIENTE_EXTERNO |
| Métricas agregadas sin PII | — | — | — | ✓ | PENDIENTE_EXTERNO |
| Hooks M06 preparados | — | — | — | ✓ | PENDIENTE_EXTERNO |
| UI gestor métricas/expiración | — | — | — | ✓ | local PASS |
| Anti-autoverificación | ✓ | ✓ | ✓ | ✓ | PASS |
| Proyección pública redactada | ✓ | ✓ | ✓ | ✓ | local PASS |

## Exclusiones permanentes

Historia clínica, pagos, GPS, chat, biometría, seguros, M15.

## Validación

Pruebas automáticas **no ejecutadas** en Bloque 4 por decisión del usuario. Validación manual diferida post-apply 052.
