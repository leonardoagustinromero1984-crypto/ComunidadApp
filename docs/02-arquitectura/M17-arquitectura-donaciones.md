# M17 — Arquitectura donaciones y campañas solidarias

## Límites

M17 modela **campañas solidarias** de organizaciones autorizadas M03 para necesidades de bienestar animal. No reemplaza M03 (organizaciones), M08 (mascotas), M16 (refugios) ni M24 (pagos).

## Relación con organizaciones

```text
Organization (M03) 1 — * M17DonationCampaign
Tipos elegibles: SHELTER, RESCUE_GROUP, NGO
```

Una campaña **nunca** crea organización, usuario, mascota ni refugio.

## Separación campaña / pago

```text
M17DonationCampaign          M17Contribution (contrato)
        │                              │
        │                              ├── amountMinor (Long)
        │                              ├── status (mock B1)
        │                              └── providerReference (interno)
        │
        └── M24 (futuro): checkout, tokenización, conciliación
```

Bloque 1: `registerMockContribution` exclusivamente local; UI avisa que pagos reales no están habilitados.

## Capas

```text
UI (M17DonationScreens)
  → ViewModels (M17DonationViewModels)
  → M17DonationRepository (interface)
  → MockM17DonationRepository
  → M17MemoryStore (seeds deterministas)
  → M17DonationValidators / M17FinancialCalculator
  → M17PrivacySanitizer → M17PublicCampaign
```

## Modelo interno vs público

| Campo | Interno (`M17DonationCampaign`) | Público (`M17PublicCampaign`) |
|-------|--------------------------------|--------------------------------|
| organizationId | Sí | No |
| createdBy | Sí | No |
| internalNotes | Sí | No |
| moderationStatus | Sí | No |
| providerReference | En contribución interna | No |
| título, descripción, objetivo | Sí | Sí (sanitizado) |
| resumen financiero | Calculado | Sí (solo CONFIRMED) |

Toda lectura pública pasa por `M17PrivacySanitizer.toPublicCampaign()`.

## Dinero

- Representación: `amountMinor: Long` + `currency: String` (centavos/unidades mínimas)
- Cálculo: `M17FinancialCalculator.summarize()` — suma solo `CONFIRMED`
- Sin `Double` para montos
- Moneda inmutable tras contribuciones confirmadas

## Transiciones de estado

```text
DRAFT → PUBLISHED → PAUSED ↔ PUBLISHED
PUBLISHED|PAUSED → COMPLETED | CANCELLED (terminales)
COMPLETED|CANCELLED → (sin reapertura)
```

Validación: `M17DonationValidators.validateStateTransition()`.

## Permisos

Mock: `MockM17DonationAuthorityPolicy` + `organizationManagers`.

Producción (B2): `donation.view` / `donation.manage` vía M03 membership; verificación en repositorio.

## Errores

`M17Exception` + `M17DonationErrorMapper.userMessage()` — sin filtrar rutas, tokens ni refs privadas.

## Notificaciones

`M17M06Hooks` preparados; allowlist M06 **no ampliada** en Bloque 1.

## Estrategia local Bloque 1

- `M17MemoryStore` con 9 campañas seed + contribuciones variadas
- Mock cuando `useSupabase=false`

## Bloque 2 — persistencia remota

- Migración **054** creada, **no aplicada**
- `SupabaseM17DonationRepository` + RPCs sanitizados
- Permisos `donation.view`, `donation.manage`
- Trigger anti-CONFIRMED cliente; pagos reales M24

## Dependencias

M03 (org), M02/M01 (auth), M04 (moderación), M05 (media ref), M06 (notif hooks), M08 (pet ref), M10 (ubicación), M16 (shelter ref), M24 (pagos futuro).

## Bloque 3 — bienes, voluntariado, transparencia

- Modelos `M17ExtendedModels` — in-kind, volunteer, transparency
- Repos mock: `MockM17InKindRepository`, `MockM17VolunteerRepository`, `MockM17TransparencyRepository`
- `M17ContributionIntentService` — mock / unavailable (sin pasarela)
- Hub UI `m17/hub` con pestañas
- Enlace contextual M16 → M17

## Bloque 4 — persistencia remota extendida

- Migración **055** creada, **no aplicada** (depende de 054)
- Tablas: in-kind needs/pledges, volunteer opportunities/applications, transparency reports/items/milestones
- `SupabaseM17ExtendedRemoteDataSource` + mappers JSON
- `SupabaseM17InKindRepository`, `SupabaseM17VolunteerRepository`, `SupabaseM17TransparencyRepository`
- `DataProvider`: mock cuando `useSupabase=false`; Supabase cuando activo
- RPCs públicas sanitizadas; mutaciones SECURITY DEFINER con M03
- `M17CampaignModerationAdapter` extendido (M04)
- M06 allowlist sin ampliar; notificaciones diferidas
- Validación remota pendiente; cierre operativo global M17 pendiente (Bloque 5 no iniciado)
