# ANDROID PERFORMANCE AUDIT — CLOSURE-03

**Fecha:** 2026-09-13  
**Marca:** LeoVer  
**Alcance:** cliente Android. Sin emulator en este bloque: **no hay timings de dispositivo medidos aquí**.  
Instrumentación DEBUG: `ScreenPerfProbe` + `ScreenPerfProbe.Ledger` (elapsed, RPC/request count conceptual, signed URL hit/miss). Sin PII.

Staging: `tobqbddfcyitwgbkthhy`.

---

## Cómo medir en QA físico

Logcat filtro `PERF02` y `ReelPublishStage` en debug. Cada pantalla lista `total_ms`, `first_content_ms`, `network_ms` y `requests=` / `signed_urls=` / `cache_hits=`.

No rellenar “before/after timing” con números inventados.

| Screen | Before requests | After requests | Before timing | After timing | Root cause | Fix |
|---|---|---|---|---|---|---|
| APP START → HOME READY | no medido | no medido | no medido | no medido | arranque + restore Reel | probe `home` en refresh |
| HOME feed first content | 1 feed + N signed URL serie + N `getUser` | feed page + signed URL cache + map paralelo (semáforo 6) | no medido | no medido | N+1 serial en `mapSocialPost` | `mapSocialPosts` acotado + extras en paralelo |
| PROFILE ready | combine + badges + authz | igual estructural | no medido | no medido | combine existente | probe pendiente de first-emit; no poll extra |
| MI MANADA connections | 1 lista + N `getPublicProfile` serial | misma N, concurrencia 6 | no medido | no medido | N+1 perfiles | `Semaphore(6)` |
| CONNECTION DETAIL | `getPublicProfile` cada 4 s | 1 fetch | no medido | no medido | poll 4000 ms | eliminado |
| PET profile | probe `pet_profile` ya existía | igual | no medido | no medido | — | sin cambio de contrato |
| VITACORA summary/moments | no medido | no medido | no medido | no medido | — | instrumentar en QA |
| MESSAGES list | no medido | no medido | no medido | no medido | — | paginación 1066 intacta |
| CHAT messages | no medido | no medido | no medido | no medido | — | paginación 1066 intacta |
| SAVED | loadSaved serial map | `mapSocialPosts` | no medido | no medido | map serial | paralelo acotado + probe `saved` |
| MEMORIES | probe existente | igual | no medido | no medido | gate 1083 | sin bypass |
| COMMUNITY | refresh directory | probe `community` | no medido | no medido | — | timings en QA |
| ADMIN HUB | no medido | no medido | no medido | no medido | — | PHYSICAL QA |

---

## Reel publish stages

Medir en el fixture físico con `ReelPublishStage` / `ReelPublishTrace`. Este entorno **no ejecutó** copy/probe/transcode/upload.

| Stage | ms (este entorno) |
|---|---|
| Source | not measured |
| Probe | not measured |
| Transcode | not measured |
| Upload | not measured |
| Register | not measured |
| Create | not measured |
| Attach pet | not measured (create RPC already attaches; client marks phase) |
| VitaCora | not measured |
| Total | not measured |
| Bottleneck | QA reportaba falsa-falla post-create (`confirmCreatedReel`), no el upload |
| Improvement | create id es terminal; retry no re-sube; confirm lookup ya no falla el job |

---

## Cambios de cliente (sin relajar seguridad)

- Signed URL: cache in-memory por `assetId` + `expiresAtEpochMs`. TTL servidor intacto. Ledger hit/miss en DEBUG.
- Avatares de lista: Coil `size` ~52 dp / 40 dp. Hero/media del post **sin** decode cap.
- Mi manada: perfiles en paralelo acotado.
- Detalle conexión: sin poll 4 s.
- Feed/saved: mapping concurrente acotado a 6.
- Paginación feed/mensajes 1066: no se revirtió.
- POLISH-06: crop full-width de POST video es viewport/Compose + ExoPlayer `ZOOM`, **sin** copiar el archivo.
- Cámara Post / Reel / Story: un solo stack `LeoVerCaptureCamera`.
- Mapa: un log de config + un timeout de tiles. Sin retry loop.

---

## Qué no se afirma

No se inventaron ms de startup/feed/profile. Completar la columna “after timing” solo con Logcat de la APK de este bloque.
