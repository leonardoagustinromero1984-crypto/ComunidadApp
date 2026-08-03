# RC1 — Auditoría de privacidad y PII

**Alcance:** modelos públicos, DTOs, mapeadores, UI, logs, mocks, seeds, docs.

## Mecanismos de protección existentes

| Capa | Implementación |
|------|----------------|
| Modelos públicos | `*Public*` types, `toPublic*()` mappers |
| Sanitizers | `M21PrivacySanitizer`, `M27PrivacySanitizer`, `M26PrivacySanitizer`, `M23PrivacySanitizer` |
| Errores | `*ErrorMapper`, `*Resilience.safeUserMessage` |
| Logs | `AppLogger` centralizado (sin println en prod paths) |
| Archivos | `FileAuthorization`, visibilidad OWNER_ONLY/PUBLIC |
| M27 | scrub de secretos en textos públicos |

## Búsqueda realizada

- `println(` — no en código productivo relevante.
- `Log.d/e` — concentrado en `AppLogger` con sanitización de tag.
- IDs internos en UI — no detectados en strings user-facing de módulos M17–M27.
- Pagos — M25 sin estados PAID/REFUND; M17 intent stub sin datos financieros.

## Campos sensibles — estado

| Campo | Exposición pública | Notas |
|-------|-------------------|-------|
| userId / organizationId | No en modelos públicos M19/M25/M27 | OK |
| email / teléfono | Scrubbed en sanitizers | OK |
| ubicación precisa | Zona/texto en M16; coordenadas restringidas | OK |
| mensajes privados M20 | Solo participantes | OK |
| prompts M26 | Sessions no públicas | OK |
| API keys M27 | Hash + reveal-once; no en listados | OK |
| paths M05 privados | Signed URL temporal | OK |

## Hallazgos

| ID | Severidad | Descripción | Corregido RC1 |
|----|-----------|-------------|---------------|
| PII-001 | BAJO | Legacy `PaymentStatus` en mappers Supabase legacy (sin UI M24) | No — fuera alcance M24 |
| PII-002 | BAJO | Dual chat legacy podría mostrar nombres distintos | Backlog |

## Veredicto

Sin filtración **crítica** local demostrada. RC1 apto desde perspectiva PII en módulos cerrados M00–M27.
