# M21 — Arquitectura reputación y reseñas

## Capas

```text
UI (M21ReputationScreens)
  → ViewModels (M21ReputationViewModels)
  → M21ReputationRepository
  → MockM21ReputationRepository | SupabaseM21ReputationRepository
  → M21ReputationValidators / M21PrivacySanitizer
```

## Rutas

| Ruta | Pantalla |
|------|----------|
| `m21/hub` | Resumen reputación |
| `m21/reviews` | Mis reseñas |
| `m21/verifications` | Verificaciones |

Entrada: **Comunidad → Reputación (M21)**.

## Bloque 2

Migración `064_m21_reputation_reviews_and_verifications.sql` — **creada, no aplicada**.
