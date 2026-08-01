# M15 — Smoke funcional pendiente

## Estado

```text
M15 SMOKE FUNCIONAL REMOTO PENDIENTE EXTERNO
M15 CIERRE OFICIAL PENDIENTE
M15 VALIDACIÓN FUNCIONAL PENDIENTE
```

Decisión operativa: **no ejecutar** operaciones remotas desde Cursor. **No** declarar smoke PASS.

## Registro sesión 2026-08-01

| Campo | Valor |
|-------|-------|
| Entorno utilizado | No informado |
| Fecha y hora | No informado |
| Resultado general | **NO DISPONIBLE** |
| Operaciones ejecutadas | No informadas |
| Errores observados | No informados |
| Evidencia / logs sanitizados | No entregados (plantilla con placeholders) |

**Motivo del bloqueo:** sin evidencia remota real no procede cierre oficial M15.

## Checklist integrado M15/M10 (manual externo)

- [ ] 1. Abrir hub M15 (`m15/hub`).
- [ ] 2. Consultar hogares desde M10 (listado público sin dirección privada).
- [ ] 3. Crear solicitud de tránsito.
- [ ] 4. Aceptar o rechazar solicitud.
- [ ] 5. Reservar placement (capacidad decrementada).
- [ ] 6. Iniciar placement (custodia temporal M08).
- [ ] 7. Agregar evolución append-only.
- [ ] 8. Registrar gasto (sin pago).
- [ ] 9. Abrir y resolver pedido de ayuda (sin chat).
- [ ] 10. Egresar placement (motivo + outcome).
- [ ] 11. Verificar capacidad liberada en hogar.
- [ ] 12. Verificar custodia temporal revocada.
- [ ] 13. Verificar privacidad pública (sin PII).
- [ ] 14. Verificar eventos M06 o fallback documentado.
- [ ] 15. Verificar métricas agregadas en `m15/operations`.
- [ ] 16. Confirmar que no existe duplicación M10/M15.

## Criterio de cierre oficial M15

Smoke M15 PASS documentado + validación funcional manual + decisión explícita de producto/ops.

Hasta entonces: solo **cierre técnico local**.

## Riesgo funcional pendiente

Sin smoke remoto end-to-end no hay evidencia operativa de métricas remotas, hooks M06 en Supabase ni idempotencia concurrente en RPC M10.
