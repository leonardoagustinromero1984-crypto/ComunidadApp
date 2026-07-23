# M09 — Finalización y seguimiento postadopción

## Migración

`supabase/migrations/039_m09_adoption_completion_followup.sql`

Forward-only sobre 037–038. **No modifica** 037/038. **No aplicada** remotamente.

## Tablas

- `adoption_interviews`
- `adoption_document_requirements` (referencia lógica; rechaza paths públicos `leover`)
- `adoption_agreements` (unique parcial un acuerdo no cancelado por adopción)
- `adoption_finalizations` (unique por `adoption_id`)
- `adoption_followup_plans`
- `adoption_followup_checks` (controles 7/30/90 generados en `m09_finalize_adoption`)

## Finalización (`m09_finalize_adoption`)

Transacción única:

1. Publicación `PAUSED`
2. Exactamente una postulación `ACCEPTED`
3. Entrevista `COMPLETED`
4. Docs obligatorias `APPROVED`/`NOT_REQUIRED`
5. Acuerdo `ACCEPTED` por ambas partes
6. Publicación → `ADOPTED`
7. Mascota → `ARCHIVED` + historial `reason=ADOPTED` (M08 no tiene ciclo `ADOPTED`)
8. Transferencia PRINCIPAL al adoptante (supersede + insert, sync owner)
9. Plan + checks 7/30/90

Idempotente si ya existe finalización.

## Storage documental

No se usa el bucket público `leover`. Referencias aceptadas: `m05://…`, `file_asset:…`. Upload físico real queda pendiente de pipeline M05 seguro.

## RLS

Lectura solo proceso aceptado / gestor. Writes directos denegados; mutaciones vía RPC `m09_*`.
