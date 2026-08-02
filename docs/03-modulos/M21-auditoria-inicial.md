# M21 — Auditoría inicial

**Fecha:** 2026-08-02  
**Nombre oficial (D01):** M21 Reputación, verificaciones y reseñas

## Alcance D01

- Identidad verificada
- Matrículas profesionales
- Reseñas transaccionales
- Apelaciones

## Legacy reutilizado

| Activo | Ubicación | Uso M21 |
|--------|-----------|---------|
| `reputation_score` | `users` | Score agregado |
| `user_badges` | SQL 006 | Insignias perfil |
| `BadgeType` / `UserBadge` | `SocialModels.kt` | UI reputación |
| `ReputationSection` | UI perfil | Componente compartido |
| `add_reputation_points` RPC | 011 | Extensible vía submit review |

## Autoridades

- **M01** — actor autenticado, display name
- **M04** — apelaciones/moderación (adapter futuro; mock en B1)
- **M09/M12/M17** — targets de reseñas transaccionales (referencia por ID)

## Bloque 1

Fundación mock + UI + navegación `m21/*`. Sin SQL.

## Bloque 2

Migración **064** — tablas `m21_reviews`, `m21_verification_requests`, `m21_appeals`. **No aplicada** al cierre B2.
