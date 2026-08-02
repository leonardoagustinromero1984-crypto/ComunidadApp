# M26 Bloque 3 — Auditoría de operaciones e revisión humana

## Matriz de cobertura (20 puntos)

| # | Capacidad | Kotlin | Mock | 072 | Remoto | UI | Brecha |
|---|-----------|--------|------|-----|--------|----|--------|
| 1 | Solicitud de análisis | `M26AiJob`, `requestAnalysis` | ✅ | ❌ jobs | stub | Hub/Historial | 072 sin jobs → 073 Bloque 4 |
| 2 | Ejecución | `M26JobLifecycle`, estados job | ✅ stub sync | ❌ | stub | Historial | Motor externo no integrado |
| 3 | Resultado | `M26AiResult`, artifacts | ✅ | parcial recs | stub | Historial | Tabla resultados en 073 |
| 4 | Error | `M26AiErrors`, validadores | ✅ | ❌ RPC fail | stub | ErrorState | RPC remoto Bloque 4 |
| 5 | Cancelación | `cancelJob` idempotente | ✅ | ❌ | stub | — | 073 |
| 6 | Matching visual | artifacts + público sanitizado | ✅ | ✅ 072 | ✅ RPC 072 | Matching | Sin visión real |
| 7 | Duplicados | par canónico | ✅ | parcial 072 | ✅ | Duplicados | constraint canónico 073 |
| 8 | Asistencia | sesión privada stub | ✅ | ✅ 072 | ✅ | Asistencia | Sin LLM externo |
| 9 | Recomendaciones | elegibilidad APPROVED | ✅ | ✅ 072 | ✅ | Recomendaciones | — |
| 10 | Revisión humana | cola M26 propia | ✅ | ❌ | stub | Cola revisión | 073 |
| 11 | Aprobación | `reviewResult` | ✅ | parcial | stub | Cola | — |
| 12 | Rechazo | idempotente | ✅ | parcial | stub | Cola | — |
| 13 | Expiración | lifecycle EXPIRED | ✅ modelo | ❌ job expire | — | — | Scheduler futuro |
| 14 | Modelo y versión | `M26ModelDescriptor` | ✅ stub | parcial | — | Historial | Columnas 073 |
| 15 | Explicabilidad | `M26ReasonCode` | ✅ | ❌ | — | Cards | 073 |
| 16 | Privacidad | `M26PrivacySanitizer` | ✅ | RLS 072 | parcial | UI | Endurecer 073 |
| 17 | Retención | `M26RetentionPolicy` | ✅ doc | ❌ | — | — | Admin scheduler |
| 18 | Idempotencia | `clientRequestId` | ✅ | ❌ | — | — | unique 073 |
| 19 | Moderación | separado M04 | ✅ | deny M04 dup | — | copy UI | M04 autoridad sanciones |
| 20 | Auditoría | provenance append | ✅ | ❌ reviews | — | — | 073 |

## Capacidades reales vs stub

- **Real (dominio/mock):** jobs, resultados, lifecycle, revisión humana M26, idempotencia local, par canónico duplicados, sanitización, retención documentada.
- **Stub:** motor IA `leover-stub/1.0.0`, ejecución síncrona mock, asistencia texto fijo, sin proveedor externo.
- **072:** matching, duplicados, asistencia, recomendaciones — **no aplicada** en staging.
- **073:** pendiente Bloque 4 (jobs, results, reviews, idempotencia remota).

## Limitaciones

- COMPLETED ≠ APPROVED; solo APPROVED es público.
- Sin diagnósticos veterinarios ni decisiones M04.
- Supabase operaciones nuevas retornan `M26_REMOTE_NOT_READY` hasta Bloque 4.
