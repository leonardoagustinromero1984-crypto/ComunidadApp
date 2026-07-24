# M13 — Plan funcional y técnico

## Objetivo

Registrar avistamientos seguros, proponer coincidencias explicables con casos Lost/Found activos y permitir decisión humana, sin exponer ubicación exacta ni datos privados.

## Alcance Bloque 1

- Dominio M13 (estados, entidades, permisos constantes).
- Validadores y errores tipificados.
- Fake store + repositorios mock.
- Adaptador `LostFoundSighting` ↔ `M13Sighting`.
- Scoring local (reglas 1–14 de la spec).
- UI: lista, alta, detalle, candidatos, revisión local.
- Navegación canónica `m13/...` integrada desde `LostFoundScreen`.
- Pruebas focalizadas y documentación.

## Exclusiones

Pagos, historia clínica, chat, IA, biometría, GPS background, push real, confirm/reject remoto (Bloque 3), cierre automático de caso, reemplazo destructivo del legacy, apply remoto de 048 en este bloque.

## Dependencias

M01/M02 auth, M03/M04 autoridad, M05 media refs, M07 eventos (nombres), M08 mascota (futuro), Lost/Found legacy, M12 técnico independiente (Veterinarias) — smoke M12 sigue pendiente externo.

## Arquitectura Bloque 1

```text
UI (m13/*) → ViewModels → MockM13*Repository → M13MemoryStore
                              ↘ M13MatchingEngine
                              ↘ M13LegacySightingAdapter → InMemoryDataStore.addSighting (opcional)
```

## Bloque 2 (implementado localmente)

- Migración `048_m13_sightings_and_match_candidates.sql` (no aplicada remotamente).
- Tabla lateral + candidatos + decisiones + historial.
- 13 RPC; RLS; grants authenticated; helpers `_m13_*` revocados.
- `SupabaseM13*` + switching en `DataProvider`.
- Docs: `M13-persistencia-y-seguridad.md`, `M13-Bloque-2-validacion.md`, runbook 048.
- Guard CI highest = **048**.

## Bloque 3 (propuesta exacta)

RPC `m13_open_match_review`, `m13_confirm_match_candidate`, `m13_reject_match_candidate` (o equivalentes), escritura en `lost_found_match_decisions` + historial, autoridad dueño/org, sin autoconfirmación, UI remota de decisión, smoke remoto post-048.
