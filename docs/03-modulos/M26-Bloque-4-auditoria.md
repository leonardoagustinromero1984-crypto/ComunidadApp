# M26 Bloque 4 — Paridad, seguridad y cierre

## Migraciones staging (`wystsapjfpdtoprlmizz`)

| Versión | Archivo | Estado |
|---------|---------|--------|
| 072 | `072_m26_ai_matching_duplicates_assistance_recommendations.sql` | APLICADA |
| 073 | `073_m26_ai_operations_review_and_safety.sql` | APLICADA |
| 074 | `074_m26_review_queue_permission_fix.sql` | APLICADA (correctiva crítica cola revisión) |

## Paridad Kotlin / mock / remoto

| Área | Kotlin/mock | 072/073 | Supabase RPC | Brecha |
|------|-------------|---------|--------------|--------|
| Jobs | ✅ | 073 | ✅ | — |
| Resultados | ✅ | 073 | ✅ | — |
| Idempotencia | ✅ | unique client_request | ✅ | — |
| Revisión humana | ✅ | 073 + reviews | ✅ | — |
| Matching | ✅ | 072 | ✅ | stub motor |
| Duplicados canónicos | ✅ | 073 trigger | ✅ | — |
| Asistencia | ✅ | 072 | ✅ | stub |
| Recomendaciones | ✅ | 072 | ✅ | solo APPROVED público |
| Privacidad | sanitizer | scrub SQL | proyecciones | — |
| RLS | n/a Android | deny-all + RPC | ✅ | — |

## Validación remota

- `scripts/ops/m26_remote_validation_072_073.sql` — **125/125 PASS**
- `scripts/ops/m26_remote_smoke_25.sql` — **25/25 PASS**

## Limitaciones cerradas

- Sin proveedor IA externo; motor `leover-stub/1.0.0`.
- M04 conserva moderación/sanciones; M26 solo calidad IA.
- M24 pagos pospuesto.
