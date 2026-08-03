# RC1 — Auditoría DataProvider

**Factory principal:** `app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt`  
**Auth (M01):** `AuthProvider.kt`  
**Flag global:** `AppConfigProvider.featureFlags().useSupabase`

## Mecanismo de selección

```
useSupabase = override ?? (SUPABASE_ENABLED && URL HTTPS válida && anon key presente)
```

- Un solo flag para todos los repos cableados.
- Credenciales inválidas → modo mock seguro + `missingConfigMessage`.
- Repos `by lazy`; sin refresh en caliente (tests usan `AppConfigProvider.resetForTests()`).

## Matriz por módulo

| Módulo | Mock | Supabase | Fallback | Notas |
|--------|------|----------|----------|-------|
| M01 | `MockAuthRepository` | `SupabaseAuthRepository` | Mock | vía `AuthProvider` |
| M02 | Mock×5 | Supabase×5 | Mock | users, permissions, platform, admin, friends |
| M03 | Mock×4 | Supabase×4 | Mock | orgs, membership, invitation, permission |
| M04 | Mock×4 | Supabase×4 | Mock | moderation, verification, support, audit |
| M05 | Mock×6 | Supabase×6 | Mock | storage helpers **null** en mock |
| M06 | mock bundle | Supabase inbox/prefs/install | Mock | delivery/outbox **ClientDenied** en Supabase |
| M07 | mock parcial | Supabase parcial | Mock | metrics/health/analytics **siempre mock** |
| M08 | `MockPetRepository` | `LegacyPetRepositoryAdapter` | Mock | domain repos **null** en mock |
| M09 | Mock×8 | Supabase×8 | Mock | adopciones completas |
| M10 | `M10FosterMemoryStore` | Supabase×6 | Mock | |
| M11 | `M11ShelterMemoryStore` | Supabase×9 | Mock | legacy `shelterRepository` activo |
| M12 | `M12VeterinaryMemoryStore` | Supabase×8 | Mock | |
| M13 | `M13MemoryStore` | Supabase×3 | Mock | |
| M14 | `M14MemoryStore` | Supabase×4 | Mock | authority policy mock-only |
| M15 | `M15MemoryStore` | delega M10 | Mock separado | **split-brain mock** vs M10 |
| M16 | Mock + seeds | Supabase | Mock | ops seeds M11/M15 en mock |
| M17 | `M17MemoryStore` | Supabase×4 | Mock | `m17ContributionIntentService` **siempre mock** |
| M18 | Mock | Supabase | Mock | |
| M19 | Mock | Supabase | Mock | paralelo: `feedRepository` legacy |
| M20 | Mock | Supabase | Mock | paralelo: `chatRepository` legacy |
| M21 | Mock | Supabase | Mock | |
| M22 | Mock | Supabase | Mock | paralelo: `serviceRepository` legacy |
| M23 | Mock | Supabase | Mock | M20 adapter en mock |
| M24 | — | — | — | **No cableado** |
| M25 | Mock×3 | Supabase×3 | Mock | sin pagos |
| M26 | Mock | Supabase | Mock | |
| M27 | Mock | Supabase | Mock | |

## Intencionales (no defectos)

- M06 ClientDenied en delivery/outbox remoto.
- M07 métricas/analytics mock incluso con Supabase.
- M08 domain repos solo Supabase.
- M17 payment intent stub hasta M24.
- M24 ausente; `enablePaymentsStub` no consumido en `DataProvider`.

## Riesgos documentados

| ID | Severidad | Descripción |
|----|-----------|-------------|
| DP-001 | MEDIO | M10 vs M15 stores mock separados (split-brain en mock mode) |
| DP-002 | MEDIO | Dual stacks: feed/M19, chat/M20, service/M22, shelter/M11 vs M16 |
| DP-003 | BAJO | Lazy singletons no re-cablean tras cambio de flag |

## Service role y secretos

- No se detectó service role en `DataProvider` ni ViewModels.
- URLs/credenciales vía `AppConfigProvider` / BuildConfig.

## Veredicto

Cableado **consistente** con arquitectura actual. Sin defecto claro que requiera corrección en RC1; deudas DP-001/002 en backlog.
