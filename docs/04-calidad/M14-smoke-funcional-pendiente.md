# M14 — Smoke funcional pendiente

## Estado

```text
M14 SMOKE FUNCIONAL PENDIENTE EXTERNO
M14 CIERRE OFICIAL PENDIENTE
MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
VALIDACIÓN ESTRUCTURAL 052 PENDIENTE
GITHUB ANDROID CI PENDIENTE
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

Decisión operativa: **no bloquear** el cierre técnico local. **No** declarar ningún smoke como PASS.

Pruebas automáticas del Bloque 4: **no ejecutadas** por decisión del usuario. Validación = **manual**.

## Riesgo funcional pendiente

Sin apply remoto de 052 ni smoke end-to-end:

1. RPC de revisión humana / emisión / revocación / rotación en Supabase.
2. Expiraciones programadas (cron) — además `REQUIERE_INFRA_EXTERNA`.
3. Métricas operativas remotas sin PII.
4. Idempotencia y conflicto concurrente en remoto.
5. Redacción pública remota (QR, microchip, documentos privados).

## Checklist sugerido (cuando ops lo ejecute)

- [ ] Apply migración 052 + validación estructural PASS.
- [ ] Auth real + actor `auth.uid()`.
- [ ] Flujo open → approve/reject → emisión credencial.
- [ ] Rotación `public_code` + QR público sin PII.
- [ ] Expiración solicitud PENDING / UNDER_REVIEW.
- [ ] Expiración credencial por `expiresAt`.
- [ ] Reintento idempotente → sin duplicar historial.
- [ ] Métricas gestor sin PII en rango válido.
- [ ] Documentar resultado sin inventar PASS parcial.

## Criterio de cierre oficial

Smoke M14 PASS documentado + 052 aplicada + decisión explícita. Hasta entonces: solo cierre técnico local.
