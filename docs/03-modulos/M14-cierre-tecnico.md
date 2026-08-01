# M14 — Cierre técnico local

```text
M14 BLOQUE 4 CERRADO LOCALMENTE
M14 CIERRE TÉCNICO LOCAL COMPLETADO
MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
VALIDACIÓN ESTRUCTURAL 052 PENDIENTE
M14 SMOKE FUNCIONAL PENDIENTE EXTERNO
M14 CIERRE OFICIAL PENDIENTE
GITHUB ANDROID CI PENDIENTE
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

## Alcance del cierre técnico

Cierre **local** de M14 tras Bloques 1–4:

| Bloque | Resultado local |
|--------|-----------------|
| 1 Fundación + fakes + UI | Cerrado |
| 2 Persistencia 050/051 | Cerrado; estructural remoto 18/18 PASS |
| 3 Revisión humana + 052 (SQL creado) | Cerrado localmente; 052 no aplicada |
| 4 Endurecimiento | Cerrado localmente (este documento) |

## Qué incluye Bloque 4

- Política local de expiración (`America/Argentina/Buenos_Aires`); cron = `REQUIERE_INFRA_EXTERNA`.
- Expiración de solicitudes `PENDING` / `UNDER_REVIEW` y credenciales por `expiresAt`.
- Preservación de estados terminales; reintentos idempotentes.
- Privacidad reforzada: proyección pública, QR/deep link sin PII, microchip enmascarado.
- Métricas operativas agregadas sin PII + UI gestores.
- Hooks M06 preparados (sin push real).
- Eventos M07 best-effort (sin ampliar catálogo canónico).
- `SupabaseM14OperationsRepository` devuelve `REMOTE_VALIDATION_PENDING` hasta apply 052.
- Sin migración 053; 001–052 intactas.
- Sin APK; M15 no iniciado.

## Pruebas automáticas

**No ejecutadas** en este cierre por decisión del usuario. Validación funcional = **manual** y diferida.

## Riesgo funcional pendiente

Sin apply remoto de 052 ni smoke end-to-end, no hay evidencia operativa de expiraciones RPC, métricas remotas ni hooks en Supabase.

## Criterio de cierre oficial M14

Solo cuando:

1. Migración 052 aplicada y validación estructural PASS.
2. Smoke funcional M14 remoto PASS documentado.
3. GitHub Android CI resuelto o aceptado explícitamente.
4. Decisión explícita de producto/ops.

Hasta entonces: **M14 CIERRE OFICIAL PENDIENTE**.

## Limitaciones M06 / M07

- M06: hooks preparados; push/delivery real = infraestructura externa.
- M07: auditoría local + nombres de evento; ampliación de catálogo = decisión separada.
