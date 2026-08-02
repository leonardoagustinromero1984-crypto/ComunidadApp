# M21 — Cierre oficial

**Módulo:** M21 Reputación, reseñas y verificaciones  
**Fecha cierre:** 2026-08-02  
**Entorno remoto:** Supabase staging `wystsapjfpdtoprlmizz` (no producción)

## Commits de cierre

| Bloque | SHA | Mensaje |
|--------|-----|---------|
| 1 | `f9b3620` | `feat(m21): establish reputation and reviews foundation` |
| 2 | `3ebc030` | `feat(m21): add reputation and reviews persistence` |
| 3 | `9764e17` | `feat(m21): add review operations and verification workflows` |
| 4 | `599363e` | `fix(m21): complete remote validation and module closure` |

**Reconciliación historial (2026-08-02):** migración 065 aplicada vía `db query`; objetos verificados; `supabase migration repair 065 --status applied --linked` completado tras disponibilidad de credenciales. `schema_migrations`: 064 y 065 registradas.

## Tests Kotlin — historial

Cuatro tests fallaron en ejecución intermedia (`errorDoesNotExposePayload`, `disputeCreatesM04Case`, `duplicateReviewRejected`, `submitReviewWorks`) sobre código previo a correcciones de Bloques 3–4. **Ejecución final:** 42/42 PASS — no hay assertions fallidas vigentes. No re-ejecutar suite M21 salvo cambios Kotlin M21.

## Migraciones

| Versión | Archivo | Staging |
|---------|---------|---------|
| 064 | `064_m21_reputation_reviews_and_verifications.sql` | Aplicada |
| 065 | `065_m21_review_operations_and_verification_workflows.sql` | Aplicada (imprescindible para Bloques 3–4) |

Hotfix operativo: `scripts/ops/m21_hotfix_post_065.sql` (paridad agregados y resumen propio).

`schema_migrations`: 064, 065 registradas.

## Validación remota

| Script | Resultado |
|--------|-----------|
| `scripts/ops/m21_remote_validation_064_065.sql` | **130/130 PASS** |
| `scripts/ops/m21_smoke_remote_01_25.sql` | **25/25 PASS** |

## Tests Kotlin (local)

`com.comunidapp.app.domain.m21.*` — **42/42 PASS**.

## Alcance cerrado

- Elegibilidad por contexto real
- Ciclo de vida de reseñas (DRAFT → PUBLISHED/EDITED/ARCHIVED/DISPUTED)
- Respuestas del sujeto
- Agregados y distribución transparentes
- Antiabuso (señales internas)
- Disputas y reportes M04
- Verificaciones con evidencia M05 privada
- Privacidad en modelos, RPC y UI

## Producción

No afectada.

## Siguiente módulo

**M22 Prestadores y catálogo de servicios** — Bloque 1 autorizado; Bloque 3 no iniciado.

## Veredicto

```text
M21 CERRADO OFICIALMENTE
```
