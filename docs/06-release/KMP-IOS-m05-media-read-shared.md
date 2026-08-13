# KMP-IOS — M05 media READ shared

## Circuito

```
photo_url / avatar_file_asset_id
→ MediaRef (Asset | RemoteUrl)
→ MediaResolver (CachingMediaResolver)
→ get_file_asset + resolve_public_file_asset | request_file_signed_url
→ createSignedUrl (TTL 300/600s fallback)
→ HTTP GET bytes (memoria)
→ SharedRemoteImage (decode → ImageBitmap)
```

## Contratos públicos SAFE

- `MediaRef`, `MediaResolver`, `MediaResolveResult`, `MediaResource`, `SharedRemoteImage`
- internal: `SupabaseM05MediaReadGateway`, cache, HttpClient, DTOs RPC

## Seguridad

- Signed URL nunca en DB / DataStore / Keychain / presentation permanente
- Logout → `clearCache()`
- Errores sanitizados (sin JWT / path / URL)
- Unconfigured → `Unavailable` (no fake demo)

## Write preservado

Upload M05 Lost/Found (KMP-9) intacto: `photo_url = assetId`.
