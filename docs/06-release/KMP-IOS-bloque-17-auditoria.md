# KMP-IOS — Bloque 17 auditoría (Deep links)

Fuente: `shared/.../deeplink/` + `iosApp` URL scheme.

## CONTRATO

| Entrada | Destino |
| ------- | ------- |
| `https://leover.com.ar/mascota/{code}` | `PetPublic` |
| `/adopciones/{code}` | `AdoptionPublic` |
| `/perdidos/{code}` | `LostCase` |
| `/encontrados/{code}` | `FoundCase` |
| `leover://passport/{code}` | `Passport` |
| push `deep_link_type` + `resource_id` | `NotificationIntentParser` |

## REGLAS

- Hosts HTTPS allowlist: `leover.com.ar`, `www.leover.com.ar`
- Scheme custom: `leover`
- Reject: blank, wrong host, `javascript:` / `file:` / `data:` / `content:`, empty/invalid code
- `publicCode`: trim + URL-decode + `[A-Za-z0-9_\-.:]{1,128}`
- Push: solo `PUB-…` → destino tipado; UUID → `SafeHome`
- UI: `SharedDeepLinkLandingScreen` (código + hubs) — **sin RPC pública inventada**
- Associated Domains / AASA: **NO** (web blocked)

## API iOS

- `offerDeepLinkUrl(url)` / `IosDeepLinkBridge`
- `Info.plist` `CFBundleURLTypes` scheme `leover`
- `onOpenURL` en `iOSApp`
