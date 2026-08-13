# KMP-IOS — Backlog de plataforma (post KMP-3)

## Compartido terminado

- Dominio pets M08 + OrganizationId + onboarding models
- M23 booking resilience + lost/found/adoption status rules
- PlatformClock
- POC M08/M22 Compose + Navigation MP + FileRef/ImagePicker
- iosApp SwiftUI shell + CI workflow (arm64 simulator)
- **KMP-3:** Session / Profile / Pets vertical (contratos + fakes + CMP UI)
- PlatformPreferences + OnboardingIntentStore
- AndroidSessionMapper (bridge sin tokens)

## Android específico (sigue)

- DataProvider / Supabase productivo
- Auth GoTrue completo + deep links
- FCM / notification channels
- DataStore / Room
- Cámara / intents / Manifest
- PetAuthorizationBridge (M02)
- UI Compose productiva (~279 destinos)
- WIP M09 decoding (fuera de KMP)

## iOS — funciona hoy

- Home branding + estado sesión (stub) + badge SHARED_FAKE/SESSION_STUB
- Perfil / lista mascotas / detalle (fake shared)
- Host SwiftUI → LeoVerShared
- Preferencias no sensibles (NSUserDefaults)

## iOS pendiente

- Auth productivo Supabase / GoTrue
- Keychain / secure storage
- APNs / Photos-cámara productivos / ubicación
- Deep links / Apple Sign In / background tasks
- TestFlight / certificados / App Store

## Siguiente bloque propuesto (no implementar aún)

**KMP-4:** Perdidos / Encontrados + Adopciones (vertical UI + contratos shared; sin SQL; respetar WIP M09).
