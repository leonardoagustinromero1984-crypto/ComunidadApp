# KMP-IOS — Lost/Found publish REAL_REMOTE (Bloque 8)

## Write

| Pieza | Valor |
| ----- | ----- |
| Fuente | PostgREST insert `lost_found_posts` |
| Status inicial | `ACTIVE` |
| Author | `author_id` / `author_name` desde sesión autenticada únicamente |
| Location | `ApproximateLocation` → texto `location` (sin coords) |
| Contact | `contact_info` derivado (nota opcional o “Contactar por LeoVer (…)”) — no en UI SAFE read |
| public_code | trigger DB; se re-lee post-insert |
| Media | **PARTIAL** — FileRef opcional; M05 upload no portado a `:shared`; no se finge éxito |

## API

`LostFoundRepository.publish(LostFoundPublishRequest)` → `LostFoundPublishResult`

Gateways internal: `LostFoundWriteGateway`, `PartialLostFoundMediaUploadGateway`.

## Host

`PocIosViewController` → runtime + `IosImagePicker` (PHPicker) para foto opcional.

## Fuera de alcance

Adoption write, APNs, GPS obligatorio, cámara avanzada, M05 session RPCs en KMP.
