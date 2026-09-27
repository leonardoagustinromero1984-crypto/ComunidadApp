# ANDROID POLISH 06

**Fecha:** 2026-09-13  
**Staging:** `tobqbddfcyitwgbkthhy`  
**Restore test (no usar):** `wystsapjfpdtoprlmizz`  
**Marca:** LeoVer · **Producto mascota:** VitaCora

Working tree dirty. **NO COMMIT. NO PUSH.**  
Web no empezó. READY FOR WEB: **NO**.

Migración máxima previa: **1083**  
Migraciones nuevas: **1084**, **1085** — aplicadas en STAGING.

APK de referencia previa (POLISH-05):  
`AA6707DC9EB89DD05B4F218CB5C770C9E1676E908F991993C2840E8C46A1F583`

==================================================
ANDROID POLISH 06
==================================================

==================================================
POST VIDEO
==================================================

Previous black sidebars:  
Feed usaba viewport 4:5 + player `FIT` → letterbox negro a los lados.

Root cause:  
`StoryVideoPlayer` default `RESIZE_MODE_FIT` dentro de un frame 4:5.

Feed viewport:  
4:5 full-width de la zona media del post (`LeoSocialPostCard`).

Crop:  
Preview idle = `VideoPreviewFrame` `ContentScale.Crop`. Tap en feed abre el detalle (viewer). No stretch.

Original ratio viewer:  
Detalle de publicación (sin `onPostClick`) usa caja `heightIn(220–520.dp)` + `FIT` para ver el video completo.

Performance:  
Crop es viewport/Compose + ExoPlayer `ZOOM`. No se copia el archivo.

Result:  
Preview llena el ancho del post. Viewer conserva el ratio original.

==================================================
VITACORA
==================================================

Etimology copy removed from operational view:  
**YES**

El copy “VitaCora nace de vita (vida) y cora (corazón).” permanece en onboarding/tutorial (`TutorialCatalog`) y en documentación DS2. No en `M14PassportScreens`.

Divider before:  
Hairline global `#E5EAE4` / 0.5 dp — demasiado claro.

Divider after:  
`#B4BAB2` / 1 dp entre secciones operativas.

Sections:  
Título de sección + caption secundaria + padding + hairline. Sin card dentro de card.

Density:  
Títulos `LeoCardTitle`. Metadata en `LeoCaption` / `MutedText`.

Result:  
Jerarquía más clara en la vista cotidiana. Copy etimológico fuera de la operativa.

==================================================
PET PROFILE
==================================================

Edit + VitaCora:  
`[ Editar ] [ VitaCora ]` en el mismo `Row`.

Side by side:  
**YES** — `weight(1f)` iguales.

Small-screen:  
Weights iguales; no se apilan.

Secondary actions:  
Compartir / QR / Responsables / Perdí mi mascota siguen como rows.

Result:  
VitaCora primary, Editar outline, mismo peso visual.

==================================================
REEL CAMERA
==================================================

Record live:  
**YES** — Publicar → Reel → Grabar Clip → `LeoVerCaptureCamera(VIDEO)`.

Gallery:  
**YES** — elegir video de galería sigue disponible.

Front/back:  
**YES** — “Girar cámara” antes de grabar.

Flip:  
Deshabilitado durante grabación (`enabled = !recording`).

Audio:  
`RECORD_AUDIO` si está concedido; si no, video sin audio (comportamiento compartido).

Single-camera fallback:  
Un solo stack `LeoVerCaptureCamera` para Post / Reel / Story.

Post/Reel separation:  
Video grabado desde Reel = `kind = "REEL"`. Video desde Publicación = POST. No se mezclan.

Result:  
Clip se puede grabar en vivo. Galería intacta. Reel + mascota → VitaCora sin tocar la transaction resumable.

==================================================
STORY QUOTA
==================================================

Root cause:  
1. El copy exacto de QA (“Alcanzaste el límite diario de **subidas**”) sale de `FileUiErrorMapper` ante `QUOTA_EXCEEDED` genérico (media register / bytes / video), no de un mensaje de Historia.
2. `social.story.create` en 1063 era **12 / 3600 s**, `RATE_LIMITED`, `fail_closed=false`. El wrapper 1063 **consumía la cuota antes** de insertar. Un create fallido o un retry de create fallido quemaba el contador de Historia aunque no hubiera Historia publicada.
3. POST / REEL / avatar **no** usan `social.story.create`. Un usuario con 0 Historias + 1 POST no debería chocar esa key salvo creates fallidos previos en la misma persona, o un cupo de media compartido mal etiquetado como “subidas”.

Quota key:  
`social.story.create` (tabla `security_rate_limit_policies`).

Previous limit:  
12

Previous window:  
3600 s (1 hora)

Previous counter:  
No se inspeccionó el contador live de la cuenta de QA (sin PII). El síntoma es compatible con consume-antes-de-insert y/o con cuota de media genérica.

What incorrectly consumed quota:  
`canon_create_story` (1063) llamaba `_canon_consume_rate_limit` **antes** del INSERT. Fallos no publicaban Historia pero sí incrementaban el contador.

New counting rule:  
INSERT exitoso → después `_canon_consume_rate_limit('social.story.create', 1)`. Si el consume falla, el INSERT se revierte. Cancel / fail / upload start no llaman `canon_create_story`.

New limit:  
20 historias publicadas / person / día

Window:  
86400 s

POST consumes Story quota:  
**NO**

REEL consumes Story quota:  
**NO**

Cancelled Story consumes:  
**NO**

Failed Story consumes:  
**NO**

Successful Story consumes:  
**YES**

Retry double counts:  
**NO** — retry de un create fallido no encontró fila previa; consume ocurre solo tras INSERT. Retry de una Historia ya publicada no vuelve a crear.

Migration:  
`20260913220000_1084_story_quota_after_success.sql`

Mensaje real de tope:  
“Alcanzaste el límite diario de historias. Podés volver a publicar mañana.”

No se tocaron: Reel, Post, signed URL, messages, imports, `media.video.count.daily`, `media.register`.

Result:  
Cuota de Historia aislada, contada al publicar, 20/día en config canónica. Android no hardcodea el número.

==================================================
CAMPAIGNS
==================================================

Alias:  
Se muestra `alias_cbu` / `payment_alias`. Botón “Copiar alias”.

External transfer copy:  
“Las transferencias se realizan fuera de LeoVer.”

Colaboré:  
Sheet DS2 — monto + nota opcional.

Declared amount:  
Se declara en menor (centavos). Queda **PENDING**.

Pending:  
No entra al total recaudado.

Creator confirm:  
Creador (`created_by`) confirma → **CONFIRMED**. Idempotente: confirmar dos veces no suma dos veces.

Creator reject:  
**REJECTED**. No entra al total.

Confirmed total:  
Solo `status = CONFIRMED`. Se muestran objetivo, total confirmado, progreso y cantidad de colaboraciones confirmadas.

Duplicate prevention:  
Update `WHERE status = 'PENDING'`. Re-confirm de CONFIRMED devuelve la misma fila.

Privacy:  
Sin leaderboard público de donantes. El detalle individual queda en la lista del creador / propia contribución.

Backend model:  
`donation_campaigns` + `donation_contributions` (canónico 1017) + columnas `status`, `note`, `confirmed_at`, `confirmed_by`, `currency`, `amount_minor`.

RPCs:  
`canon_declare_campaign_contribution`  
`canon_confirm_campaign_contribution`  
`canon_reject_campaign_contribution`  
`canon_list_campaign_contributions`  
`canon_get_donation_campaign`

No checkout. No Mercado Pago. No wallet.

Migration:  
`20260913223000_1085_campaign_declared_contributions.sql`

Result:  
Flujo declarado → pendiente → confirmado/rechazado persistido. LeoVer no mueve dinero.

==================================================
MAP
==================================================

SDK:  
Google Maps SDK for Android + Maps Compose. Solo render. Sin Places / Geocoding / Routes.

Key injected:  
**YES** — `MAPS_API_KEY` en `local.properties` llega a `BuildConfig` + manifest. Length 39, prefijo Maps. No se loguea ni commitea la key.

Package:  
STAGING APK = `com.comunidapp.app.staging`  
También existen `com.comunidapp.app` y `com.comunidapp.app.local`.

Certificate restriction (debug de esta máquina):  
SHA-1 `5C:C4:73:97:49:01:75:60:6D:FD:F9:EF:25:7B:EA:DC:E1:D5:67:8D`  
SHA-256 `4C:AA:28:B0:32:C0:15:3C:40:20:FD:EB:39:D6:C7:E8:73:18:E2:68:28:44:F1:2E:54:04:AF:1F:71:68:59:EF`

Maps API enabled:  
No verificable desde el repo. Debe estar **Maps SDK for Android** en el proyecto Google Cloud dueño de la key.

Billing/config:  
No verificable desde el repo. Si el proyecto no tiene billing activo, los tiles quedan en blanco.

Root cause:  
El viewport existe y la key **sí** se inyecta. Un mapa blanco con key presente es fallo de autorización Google (paquete/SHA, API no habilitada, o billing), no un bug de styling Compose.

Code change:  
Diagnóstico one-shot `LeoVerMaps` (configured + key_len + package + loaded/timeout). Overlay DS2 si los tiles no cargan en 12 s. Sin retry loop. Sin simular mapa.

Manual external config required:  
**YES** — STOP solo de tiles hasta configurar Cloud:

| Item | Valor |
|---|---|
| Google Cloud Project | El proyecto **dueño de la API key** (candidato Firebase `comunidadapp-31a74` / número `882711094221` — confirmar en Cloud Console, no asumir) |
| API required | **Maps SDK for Android** only |
| Package | `com.comunidapp.app.staging` (y `.local` / `com.comunidapp.app` si se usan esas flavors) |
| SHA | SHA-1 y SHA-256 de debug arriba |
| Restriction | Android apps + Maps SDK for Android. Nunca Places/Geocoding/Routes |
| Secret/config name | `MAPS_API_KEY` en `local.properties` (gitignored) |

Result:  
Diagnóstico real. Tiles visibles **después** de alinear paquete + SHA + API + billing. No se simuló el mapa.

==================================================
SOCIAL FEED REGRESSION
==================================================

Stories:  
Tray arriba. Intacta.

Posts:  
Verticales. Intactos.

Post video:  
Sigue siendo POST. Preview 4:5 crop.

Clips carousel:  
Horizontal. Preview real. Sin ExoPlayer extra en el carrusel.

Reel viewer:  
Tap → `ClipViewerScreen` vertical.

Duplicate prevention:  
Reel no se duplica como post.

==================================================
SECURITY / FUNCTIONAL
==================================================

Health:  
UNCHANGED

Mis recuerdos:  
UNCHANGED

Responsibility:  
UNCHANGED

Transfers:  
UNCHANGED

Privacy:  
UNCHANGED

Signed media:  
UNCHANGED

Session:  
UNCHANGED

==================================================
MIGRATIONS
==================================================

Previous max:  
1083

New migration(s):  
- `20260913220000_1084_story_quota_after_success.sql`  
- `20260913223000_1085_campaign_declared_contributions.sql`

Applied STAGING:  
**YES** (`tobqbddfcyitwgbkthhy`, workdir `infra/supabase-canonical`, sin reset)

==================================================
TESTS
==================================================

Dirigidos `testLocalDebugUnitTest` PASS:

- `AndroidPolish06ContractTest` — video crop, VitaCora, pet row, reel camera, story quota SQL, campañas, mapa, feed POLISH-05
- `AndroidPolish05ContractTest` — composición social + DS2 (etimología operativa ahora ausente)
- `FileUiErrorMapperTest` — cuota genérica intacta; Historia usa copy de historias
- `M17DonationFoundationTest` — PENDING/REJECTED no suman
- `M17DonationRemoteMapperTest` — declare PENDING, confirm una vez, reject excluido
- `ReelFeedMediaContractTest` — preview/player contract

==================================================
APK
==================================================

path:  
`apk/LeoVer-M08-Staging-debug.apk`

SHA-256:  
`78070379ACA6B23372CD243CD8033777EFAE026351E66835A22F4978C312837C`

==================================================
READY FOR PHYSICAL QA
==================================================

**YES** — excepto tiles de mapa, que requieren el ajuste manual de Google Cloud de la tabla de arriba.

==================================================
READY FOR WEB
==================================================

**NO**

==================================================
GIT
==================================================

NO COMMIT.  
NO PUSH.
