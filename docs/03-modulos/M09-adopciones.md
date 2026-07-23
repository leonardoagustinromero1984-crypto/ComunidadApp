# M09 — Adopciones (integración completa)

Módulo técnico completo a nivel de código: publicaciones → postulaciones → proceso post-aceptación → finalización → seguimiento.

## Flujo

```text
Publicación
→ Postulación
→ Candidato aceptado (otras → REJECTED; publicación → PAUSED; sin transferir mascota)
→ Entrevista
→ Documentación (refs lógicas)
→ Acuerdo bilateral
→ Finalización (RPC atómica)
→ Transferencia PRINCIPAL (M08)
→ Seguimiento 7/30/90
```

## Persistencia

| Migración | Contenido |
|-----------|-----------|
| `037_m09_adoption_publications.sql` | Publicaciones |
| `038_m09_adoption_applications.sql` | Postulaciones |
| `039_m09_adoption_completion_followup.sql` | Entrevistas, docs, acuerdo, finalización, follow-up |

**037–039 pendientes de aplicación remota.** Guía: `docs/05-operacion/M09-aplicacion-migraciones-supabase.md`. Smoke manual pendiente tras el apply.

## Integración M08

Al finalizar: supersede PRINCIPAL, alta PRINCIPAL al adoptante, sync `owner_id`, historial de estado.

Decisión temporal de ciclo de mascota: `status = ARCHIVED` + `pet_status_history.reason = ADOPTED` (M08 no define ciclo `ADOPTED`). La UI de detalle/historial muestra **Adoptada** cuando coincide ese par; no se restaura una mascota adoptada desde archivo.

## Storage documental

Solo referencias lógicas seguras (`m05://`, `file_asset:`). Prohibido bucket público `leover`. Pipeline físico M05 pendiente.

## RPC / RLS

Mutaciones vía `m09_*` (SECURITY DEFINER + `search_path`). Writes directos denegados. Tras 039, `m09_mark_adoption_adopted` responde `ADOPTION_USE_FINALIZE` (el atajo del bloque 1 ya no finaliza).

## Pantallas

Proceso, entrevistas, documentos, acuerdo, finalización, seguimiento (+ detalle publicación / postulaciones).

## Tests

- `M09MigrationStaticGuardsTest`
- `M09AdoptionCompletionTest`
- `M09AdoptionApplicationTest`
- `M09AdoptionPublicationTest`

## Compilación

`compileLocalDebugKotlin` (sin APK salvo solicitud explícita).

## Pendientes

Apply remoto 037–039, smoke DB, pipeline M05 seguro, push/alertas `CRITICAL`, firma legal avanzada, APK cuando se solicite.
