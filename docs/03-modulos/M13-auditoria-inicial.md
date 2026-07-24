# M13 — Auditoría inicial

## Fuente canónica

- `docs/03-modulos/M13-avistamientos-y-coincidencias.md`
- `docs/02-arquitectura/ADR-013-M13-track-tecnico-avistamientos-coincidencias.md`
- `docs/01-producto/D01-Modulos-y-Orden.md` (R3 — nombre de producto)

## Nombre exacto

**M13 — Avistamientos y coincidencias**

## Estado anterior

`NO INICIADO` (track técnico post M12 Veterinarias; smoke M12 externo pendiente).

## Hallazgos legacy (clasificación)

| Hallazgo | Clasificación |
|---|---|
| `LostFoundSighting` / `LostFoundScreen` / `PlatformRepository.addSighting` | REUTILIZABLE |
| `lost_found_sightings` + migración `012` | COMPATIBLE (no tocar en Bloque 1) |
| Matching / confirmación humana | REQUIERE_ADAPTACIÓN → dominio M13 nuevo |
| IA / biometría / push real | FUERA_DE_ALCANCE |
| Migración `048` | IMPLEMENTADA LOCALMENTE (Bloque 2; no aplicada remotamente) |

## Decisiones

1. M13 enriquece Lost/Found; no módulo paralelo.
2. Matching local determinista y explicable.
3. Sin autoconfirmación.
4. Sin SQL en Bloque 1.
5. Coordenadas exactas y contacto privado no salen en vistas públicas.
