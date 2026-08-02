# M27 — Arquitectura (Bloque 1)

```mermaid
flowchart LR
  UI[M27 Compose screens] --> VM[ViewModels]
  VM --> Repo[M27IntegrationRepository]
  Repo --> Mock[MockM27IntegrationRepository]
  Mock --> Store[M27IntegrationMemoryStore]
  Repo --> Domain[M27ContractEligibility / RateLimitPolicy / PrivacySanitizer]
```

- Entrada desde Comunidad → hub M27 con subpantallas.
- `DataProvider.m27IntegrationRepository` usa mock determinista en Bloque 1.
- Bloque 2 agregará `SupabaseM27IntegrationRepository` y migración 075 (sin aplicar).
