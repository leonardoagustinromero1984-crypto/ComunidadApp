# M21 Bloque 3 — Auditoría matriz funcional

**Fecha:** 2026-08-02  
**Módulo:** M21 Reputación, verificaciones y reseñas  
**Migración base:** `064_m21_reputation_reviews_and_verifications.sql` (creada, **no aplicada**)  
**Estado al cierre de auditoría:** Bloque 3 **no iniciado** — matriz de brechas y decisiones previas a implementación Kotlin.

## Alcance Bloque 3 (previsto)

- Dominio ampliado: sujeto transaccional, elegibilidad, respuesta del evaluado, disputa, edición/archivo, moderación M04, agregados y distribución pública.
- Mock completo + adaptadores locales antes de SQL adicional.
- Extensión remota diferida a migración **065** (Bloque 4); **064 permanece intacta**.

## Autoridades confirmadas

| Dominio | Autoridad | Uso M21 B3 |
|---------|-----------|------------|
| Actor / display name | M01 | `reviewerUserId`, `reviewerDisplayName` |
| Moderación / reportes | M04 | `M21ReputationModerationAdapter` (nuevo) |
| Adopciones | M09 | target `ADOPTION` |
| Turnos / servicios | M12 | target `SERVICE` |
| Donaciones | M17 | target `DONATION` |
| Organizaciones | M03 | target `ORGANIZATION` |
| Insignias perfil | 006 / `UserBadge` | agregados UI (mock B3; remoto B4) |
| Archivos evidencia | M05 | refs en verificación (mock B3) |

## Matriz de auditoría (20 filas)

| # | Área | Modelo Kotlin | Mock | 064 | Repo Supabase | Brecha | Decisión |
|---|------|---------------|------|-----|---------------|--------|----------|
| 1 | **Sujeto** (`subject`) | `M21ReviewTargetType`, `targetId`, `targetDisplayLabel` en `M21Review` / `SubmitM21ReviewInput` | Sí — seeds ADOPTION/SERVICE/ORGANIZATION | Sí — `target_type`, `target_id`, `target_display_label` + CHECK tipos | Sí — `submitReview`, `listReviewsForTarget` | Sin FK ni validación de existencia del sujeto en módulo origen | **B3 Kotlin:** resolver label vía caller; validar tipo permitido. **065:** lookup opcional por módulo |
| 2 | **Autor** (`author`) | `reviewerUserId`, `reviewerDisplayName` en `M21Review`; actor vía `actorUserId()` | Sí — `M21MockUsers`, display name fijo | Sí — FK `reviewer_user_id`, nombre desde `users` en RPC | Sí — `is_own_review` en JSON público | Sin perfil público del autor más allá del alias | **B3:** mantener alias sanitizado; no exponer UUID en superficies públicas |
| 3 | **Contexto** (`context`) | Solo `targetType` + `targetId` (referencia opaca) | Parcial — IDs mock (`M21MockTargetIds`) | Parcial — mismos campos, sin JSON de contexto | Parcial — sin snapshot contextual | Sin `context_snapshot` ni enlace a transacción concreta (M09/M12/M17) | **B3 Kotlin:** `M21ReviewContextRef` opcional en dominio/mock. **065:** columna JSON sanitizada |
| 4 | **Elegibilidad** (`eligibility`) | No modelado — submit sin gate transaccional | No — cualquier actor autenticado puede reseñar | No — solo duplicate guard reviewer+target | No — delega a RPC sin elegibilidad | Sin regla “solo tras transacción completada” ni ventana temporal | **B3 Kotlin:** `M21ReviewEligibilityService` mock (completed adoption, attended appointment, confirmed donation). **065:** RPC `_m21_assert_review_eligible` |
| 5 | **Calificación** (`rating`) | `rating: Int` en `M21Review`; validación 1–5 | Sí | Sí — CHECK + `M21_INVALID_RATING` | Sí | — | **B3:** conservar validación compartida `M21ReputationValidators` |
| 6 | **Comentario** (`comment`) | `content: String` (1–2000) | Sí | Sí — `content` + CHECK longitud | Sí | Sin historial de ediciones del texto | **B3:** editar comentario propio en mock. **065:** `updated_at` + RPC `m21_edit_review` |
| 7 | **Publicación** (`publication`) | `M21ReviewStatus.PUBLISHED`; submit → publicado directo | Sí — auto PUBLISHED | Sí — default `PUBLISHED`; enum incluye `PENDING` sin uso | Sí — `m21_submit_review` | `PENDING` definido pero no usado en flujo | **B3:** flujo mock con `PENDING` opcional pre-moderación. **065:** transición explícita publish/hide |
| 8 | **Edición** (`edit`) | No — modelo inmutable post-submit | No | No — sin RPC edit | No | Sin ventana de edición ni auditoría | **B3 Kotlin:** `editOwnReview` en mock + validadores. **065:** RPC + policy autor solo |
| 9 | **Archivo** (`archive`) | `HIDDEN` / `REMOVED` en enum; mock seed `HIDDEN` | Parcial — seed oculta, sin acción usuario | Parcial — estados en CHECK, sin RPC archive | No — listados filtran `PUBLISHED` only | Sin acción “archivar mi reseña” ni archivo por sujeto evaluado | **B3 Kotlin:** `archiveReview` mock (autor → HIDDEN). **065:** `m21_archive_review` |
| 10 | **Moderación** (`moderation`) | Estados `HIDDEN`, `REMOVED`, `APPEALED`; `M21Appeal` | Parcial — apelación autor; sin adapter M04 | Parcial — apelaciones + cambio status APPEALED | Parcial — `submitAppeal` only | Sin integración `ModerationRepository` ni resolución staff | **B3 Kotlin:** `M21ReputationModerationAdapter` + ocultar/remover mock. **065:** hooks staff vía service_role |
| 11 | **Respuesta** (`response`) | No modelado | No | No — sin tabla `m21_review_responses` | No | Sujeto evaluado no puede responder reseña | **B3 Kotlin:** `M21ReviewResponse` + submit mock one-to-one. **065:** tabla + RPC `m21_submit_review_response` |
| 12 | **Reporte** (`report`) | No — sin wrapper M04 | No | No | No | Usuario no puede reportar reseña ajena | **B3 Kotlin:** report vía M04 adapter (`M21_REVIEW` target). **065:** sin tabla paralela |
| 13 | **Disputa** (`dispute`) | `M21Appeal` (autor vs moderación) | Sí — apelación reseña propia | Parcial — `m21_appeals` ligada a `review_id` | Sí — `submitAppeal` | Sin disputa del **sujeto evaluado** (target owner); confusión appeal/dispute | **B3 Kotlin:** `M21SubjectDispute` separado de appeal. **065:** `m21_subject_disputes` |
| 14 | **Agregados** (`aggregates`) | `M21ReputationSummary` / `M21PublicReputationSummary`; score en mock map | Sí — score, count, avg, badges mock | Parcial — summary RPC; score +5 en submit; sin badges | Parcial — `badges = emptyList()` remoto | Sin RPC agregados por target; badges remotos vacíos | **B3 Kotlin:** agregados por target en mock. **065:** `m21_get_target_reputation_summary` + badges |
| 15 | **Distribución** (`distribution`) | `observeReviewsForTarget`, `observeMyReviews`, hub resumen | Sí — Flow reactivo | Parcial — list RPC sin paginación ni orden configurable | Sí — mapeo JSON | Sin cursor, filtros por rating, ni feed cross-target | **B3 Kotlin:** paginación mock + orden reciente. **065:** cursor RPC |
| 16 | **Verificación** (`verification`) | `M21VerificationRequest`, tipos IDENTITY / PROFESSIONAL_LICENSE | Sí | Sí — tabla + submit/list RPC | Sí | Solo submit; sin panel resolución | **B3:** listado ampliado mock; resolución staff diferida B4 |
| 17 | **Evidencia** (`evidence`) | `M21LicenseCredential`; campos matrícula | Parcial — texto matrícula | Parcial — `license_number`, authority, jurisdiction | Parcial — sin file ref | Sin adjunto M05 para identidad/matrícula | **B3 Kotlin:** `evidenceFileRef` opcional mock. **065:** columna ref M05 |
| 18 | **Revocación** (`revocation`) | No — solo `REJECTED` en submit | No | No — sin RPC revoke | No | Staff no puede revocar verificación aprobada | **B3 Kotlin:** transición mock APPROVED→REVOKED (dominio). **065:** status `REVOKED` + RPC staff |
| 19 | **Expiración** (`expiration`) | `M21VerificationStatus.EXPIRED`; `expiresAt` en credential (no persistido) | Parcial — enum only | Parcial — EXPIRED en CHECK; sin job ni `expires_at` columna | No | Licencias no expiran automáticamente | **B3 Kotlin:** regla mock `expiresAt` vencido → EXPIRED. **065:** columna + sweep |
| 20 | **Privacidad** (`privacy`) | `M21PublicReview`, `M21PublicVerification`, `M21PrivacySanitizer` | Sí — scrub email/teléfono | Parcial — `_m21_public_review_json` omite `target_id`, UUID reviewer | Parcial — mapper alinea JSON | Remoto aún expone `status` APPEALED/HIDDEN en listados propios | **B3:** ampliar sanitizer (estados internos, PII context). **065:** JSON público por rol |

## Resumen 064 vs dominio Bloque 3

| Función | 064 | Brecha B3 | Resolución |
|---------|-----|-----------|------------|
| Reseñas CRUD básico | Sí | Edición / archivo | Mock B3 → **065** |
| Verificaciones submit/list | Sí | Evidencia archivo, revocación, expiración | Mock B3 → **065** |
| Apelaciones autor | Sí | Disputa sujeto evaluado | Mock B3 → **065** |
| Respuestas del evaluado | No | Sí | Mock B3 → **065** |
| Elegibilidad transaccional | No | Sí | Mock B3 → **065** |
| Agregados por target / badges | No | Sí | Mock B3 → **065** |
| Moderación M04 | No | Sí | Adapter B3; staff **065** |
| Estados extendidos en uso | Parcial | PENDING, flujos hide/archive | Mock B3 → **065** |

## Decisión: no modificar 064

**064 permanece intacta.** Toda extensión SQL del dominio Bloque 3 se concentra en migración **065** (Bloque 4), siguiendo el patrón M19 (060→061) y M20 (062→063).

## Implementación Bloque 3 (Kotlin)

Prioridad de entrega local:

1. Modelos ampliados (`M21ReviewResponse`, `M21SubjectDispute`, `M21ReviewContextRef`, elegibilidad).
2. `MockM21ReputationRepository` — respuestas, disputas, edit/archive, agregados target, elegibilidad.
3. `M21ReputationModerationAdapter` — reportes M04 sin cola paralela.
4. Validadores y `M21PrivacySanitizer` ampliados.
5. UI: detalle reseña con respuesta, disputa sujeto, estados moderación.
6. Tests dominio mock (`M21ReputationBlock3Test` o ampliación suite existente).

Repositorio Supabase **sin cambios funcionales** hasta 065; métodos nuevos devolverán `M21_*_REMOTE_PENDING` o quedarán sin override.

## Bloque 4 (065)

- Tablas: `m21_review_responses`, `m21_subject_disputes`.
- RPC: edit, archive, response, dispute, agregados target, elegibilidad, revocación/expiración verificaciones.
- Aplicación operativa 064 + 065 en staging (documento operativo pendiente).
- Badges remotos alineados con `user_badges` / score.

## Estado terminal

| Item | Valor |
|------|-------|
| 064 aplicada | No |
| 065 creada | No |
| Bloque 3 Kotlin | No iniciado |
| Bloque 4 | No iniciado |
