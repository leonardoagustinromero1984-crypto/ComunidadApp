# M07 — Pruebas: persistencia, auditoría, seguridad y errores (Etapa 3)

**Producto:** LeoVer  
**Módulo:** M07  
**Etapa:** 3  
**Fecha:** 2026-07-17  
**Suite:** `M07Stage3PersistenceFoundationTest`, `M07DataProviderWiringTest` (ampliado), regresión M07 Etapa 2 + M00–M06.

---

## 1. Alcance de verificación

| Área | Evidencia |
|---|---|
| Migración 029 única nueva | test `migration029_isOnlyNewAnd001to028Intact` |
| 001–028 intactas | mismo test (prefijos 001–028 presentes; único `*m07*`) |
| Tablas M07 | asserts SQL `audit_events`, `security_events`, `application_errors`, `observability_export_requests` |
| Sin tablas postergadas | asserts ausencia `performance_metrics` / `analytics_events` |
| Catálogo 108 keys | SQL ⊇ Kotlin (`ObservabilityEventCatalog`) |
| Writers + search_path + DEFINER | asserts en texto 029 |
| Revoke PUBLIC/anon/authenticated writers internos | `revoke all … m07_write_audit_event` + `from public, anon, authenticated` |
| Grant service_role audit writer | assert `to service_role` |
| RLS deny-by-default | `enable row level security` + `using (false) with check (false)` |
| Correlation / sanitización | `sanitizer_andCorrelation_stillSafe` |
| Allowlists cliente | security/error restrictivas |
| Repos Supabase RPC contracts | wiring test |
| Auth/username intactos | `authUsernameIntact` + lectura `SupabaseAuthRepository` sin writers M07 |
| Anti-loop reporter | `reporter_disabledPreventsLoop` |

---

## 2. Casos cubiertos (matriz)

| Caso | Estado |
|---|---|
| Event key desconocido (SQL `OBS_EVENT_UNKNOWN`) | documentado + presente en 029 |
| Metadata deny-by-default | `m07_validate_metadata` en 029 |
| Correlation obligatorio / inválido | función + test parse |
| Append-only / sin DML cliente | RLS false |
| Fingerprint + dedup errores | índice + ventana 15m en 029 |
| Sin stack / token / signed URL en sanitizer | test + regex |
| Login failure sin email/password | instrumentación ViewModel + allowlist |
| Permission denied | AuthorizationService → reporter |
| Signed URL issued sin URL | FileDownload + `m07_client_note_data_access` |
| Deep-link deny | NotificationDeepLinkRouter |
| Dead-letter | trigger `trg_m07_dead_letter_observe` |
| Edge push invoked | `push/index.ts` best-effort |
| useSupabase sin service role Android | repos RPC only |
| DEBUG no remoto | AppLogger solo ERROR/WARNING allowlisted |
| Logout limpia correlation | SessionViewModel → `reportLogout` |

---

## 3. Resultado local

```text
assembleDebug          SUCCESS
testDebugUnitTest      515 tests / 0 failures / 0 errors / 0 skipped
lintDebug              SUCCESS
```

---

## 4. Staging

Migraciones **014–029** pendientes de validación remota. Esta batería es local (asserts de SQL + unit tests JVM). No sustituye apply/staging.

---

## 5. Fuera de alcance de estas pruebas

- Ejecución real contra staging/producción
- Dashboards / alertas operativas
- Proveedores externos de crash/analytics
- Corrección de username
