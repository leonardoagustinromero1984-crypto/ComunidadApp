# M13 — Smoke funcional pendiente de cierre

## Estado

```text
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

Decisión operativa: **no bloquear** el desarrollo local por estos smokes diferidos. **No** declarar ningún smoke como PASS.

## Riesgo funcional pendiente

Sin smoke remoto end-to-end, no hay evidencia operativa de:

1. Create/list/withdraw sighting vía RPC 048.
2. Generate/list candidates 048.
3. Open/confirm/reject/inconclusive/withdraw/expire vía 049.
4. Idempotencia y conflicto concurrente en remoto.
5. Que `lost_found_posts.status` no cambie al confirmar.
6. Redacción pública remota (coords/contacto/notas).
7. Expiración programada (cron) — además `REQUIERE_INFRA_EXTERNA`.

## Checklist sugerido (cuando ops lo ejecute)

- [ ] Auth real + actor `auth.uid()`.
- [ ] Sighting create → list público redactado.
- [ ] Generate candidates → open review → decide.
- [ ] Reintento decisión → `DECISION_ALREADY_EXISTS` / idempotente.
- [ ] Confirm no cierra caso Lost/Found.
- [ ] Reporter sin autoridad denegado.
- [ ] List decisions + status history.
- [ ] Documentar resultado sin inventar PASS parcial.

## Criterio de cierre oficial

Smoke M13 PASS documentado + decisión explícita. Hasta entonces: solo cierre técnico local.
