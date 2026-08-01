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
- `DataProvider.m17DonationRepository` → mock únicamente
- Sin Supabase, sin migración 054

## Dependencias

M03 (org), M02/M01 (auth), M04 (moderación futura), M05 (media ref), M06 (notif hooks), M08 (pet ref), M10 (ubicación), M16 (shelter ref), M24 (pagos futuro).

## Bloque 2 (pendiente)

Migración 054, RLS, repositorio remoto, validación remota, integración M03 permisos reales.
