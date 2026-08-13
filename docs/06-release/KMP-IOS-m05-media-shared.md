# KMP-IOS — M05 media shared (Bloque 9)

## Capas

```text
FileRef (M08)
  → FileContentReader (androidMain / iosMain)
  → M05LostFoundMediaRules (mime/size/filename)
  → SupabaseM05MediaUploadGateway
       create_file_upload_session
       transition UPLOADING
       storage.upload(public-media, path, bytes)
       complete_file_upload
  → LostFoundWriteGateway.updatePhotoUrl(assetId)
```

Un solo `SupabaseClient` con Auth + Postgrest + Storage.

## Lost/Found publish

1. insert `lost_found_posts`
2. upload M05 → assetId
3. update `photo_url = assetId`
4. si media falla: alerta textual permanece (`mediaDeferred`)

## MEDIA READ

`hasPhoto` desde `photo_url` no vacío. Resolución signed/public URL para Coil/Compose: **PARTIAL** (deferred).
