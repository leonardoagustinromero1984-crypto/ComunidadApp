# M09 — Adopciones (bloque 3: finalización y seguimiento)

## Auditoría inicial

| Área | Clasificación |
|------|----------------|
| Publicaciones 037 / postulaciones 038 | **Reutilizable** |
| `InterviewStatus` legacy en `AdoptionRequest` | **Legacy / parcial** |
| Entrevistas / docs / acuerdo / follow-up M09 | **Ausente** → implementado |
| Transferencias M08 PRINCIPAL | **Reutilizable** dentro de `m09_finalize_adoption` |
| Storage M05 seguro para docs personales | **Parcial** — contrato + referencia; upload real pendiente |
| Bucket público `leover` | **Incompatible** para docs sensibles |

## Modelos

Entrevistas, requisitos documentales, acuerdos, finalización, plan y controles de seguimiento (`AdoptionCompletionModels`).

## Persistencia

Migración `039_m09_adoption_completion_followup.sql`.

Detalle: `docs/02-arquitectura/M09-finalizacion-y-seguimiento.md`

**037, 038 y 039 pendientes de apply remoto.**

## RPC / RLS

Entrevistas, docs, acuerdo, `m09_finalize_adoption`, follow-up list/get/complete. Tablas no públicas; writes RPC-only.

## Integración M08

Al finalizar: supersede PRINCIPAL anterior, alta PRINCIPAL adoptante, `owner_id` sync, mascota `ARCHIVED` + historial `ADOPTED` (sin estado de ciclo `ADOPTED` en M08).

## Pantallas / rutas

`adoption_process/{id}`, `adoption_interviews/{id}`, `adoption_interview_detail/{id}`, `adoption_documents/{id}`, `adoption_agreement/{id}`, `adoption_finalize/{id}`, `adoption_followup/{id}`, `adoption_followup_check/{id}`

## Tests

Clases ejecutadas:

- `M09AdoptionCompletionTest`
- `M09AdoptionApplicationTest`
- `M09AdoptionPublicationTest`
- `M08IntegrationRegressionTest`

Resultado: **PASS**

## Compilación

`.\gradlew.bat compileLocalDebugKotlin` → **PASS**

## Limitación storage

Documentación: solo referencia lógica segura; sin upload a bucket público; pipeline M05 físico pendiente.

## Pendientes posteriores

Firma digital certificada, validación gubernamental de identidad, videollamada propia, chat/push, reputación, estadísticas, pagos, apply remoto 037–039, staging/APK.
