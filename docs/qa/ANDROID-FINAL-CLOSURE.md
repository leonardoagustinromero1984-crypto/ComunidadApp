# ANDROID FINAL CLOSURE

**Fecha:** 2026-09-13  
**Staging:** `tobqbddfcyitwgbkthhy`  
**Restore test (no usar):** `wystsapjfpdtoprlmizz`  
**Marca:** LeoVer · **Producto mascota:** VitaCora

Bloque único de estabilización Android antes de Web. Working tree dirty. Sin commit / push.

---

## MIS RECUERDOS

**Root cause:** query demasiado amplia. `_canon_media_is_vitacora_personal_memory` (1079) era verdadero si el asset estaba en un momento VitaCora o en un link MEMORY / PET_GALLERY / VITACORA_MEDIA. El contenido actual de mascotas bajo cuidado aparecía como recuerdo.

**Previous eligibility:** owner PERSON + origin readable + link VitaCora. Sin transferencia.

**New eligibility:** el link VitaCora **y** evidencia de transferencia de cuidado **completada** (`pet_care_transfers.status = 'ACCEPTED'`, `source_kind = 'PERSON'`, `source_person_id = viewer`). Las etapas cerradas con `transfer_id` hacia esa transferencia ACCEPTED corroboran el mismo evento.

**Transfer gate:** `_canon_viewer_completed_care_transfer(viewer, pet)`.

**Existing bad data/query handling:** no se borraron posts, Reels, assets ni momentos. Solo se estrecho la query.

**Result:** mascota actual + post/Reel VitaCora → VitaCora sí, Mis recuerdos no. Tras ACCEPTED, el vínculo anterior ve recuerdos históricos permitidos. El nuevo responsable no hereda recuerdos personales del anterior. Mis recuerdos no otorga `vitacora.view` actual.

Copy vacío actualizado: no confundir con Guardados.

---

## VITACORA CONTENT LINKING

**Post + pet:** `PublishViewModel` asocia con `VitaCoraSocialSave.saveApprovedPost` cuando hay `selectedPetIds` (ya no depende del toggle). Mismo `canon_save_social_vitacora_moment` (idempotente por pet + `source_social_post_id`).

**Reel + pet:** el job ya persistía `petIds`. El FAIL era no escribir el momento: `saveToVitaCora` quedaba en false y el banner pedía un diálogo. Ahora, tras create confirmado, se llama `saveApprovedReel` en el mismo job.

**No pet:** no se llama al save VitaCora.

**Duplicate prevention:** upsert canónico por pet + source post. Retry del mismo Reel reusa `backendPostId` / `findOwnReelIdByMediaAsset`.

**Result:** POST y REEL con mascota → un momento VitaCora. Sin mascota → ninguna.

---

## REEL PERFORMANCE

Instrumentación DEBUG: `ReelPublishTrace` / `ReelPublishStage`.

Tiempos físicos de un clip real: **pendientes de QA en dispositivo**. Esta máquina no corre emulator ni Logcat continuo.

**Previous bottleneck:** (1) `confirmCreatedReel` hacía hasta 3 polls con delay 400/800/1600 ms + `ensureVisiblePost` + resolución de URL; (2) `refreshPosts` / `refreshStories` bloqueaban el SUCCESS; (3) passthrough copiaba otra vez un archivo local ya copiado.

**Fix:** confirm = una sola asociación `asset → postId`. Refresh de feed en background. Passthrough reutiliza el file local.

**Result:** SUCCESS no espera player, signed URL ni feed. Medición física con la misma instrumentación en la APK final.

---

## REEL PUBLISH STATE

**Banner root cause:** SUCCESS con pets y `vitaCoraResolved = false` no era terminal. El banner/diálogo persistía (“Publicando clip” / prompt VitaCora) al navegar.

**Success terminal:** postId + `media_asset_id` + tipo REEL + asociación VitaCora ejecutada (o sin pets). Luego feedback breve y cleanup.

**DataStore cleanup:** `dismissTerminal` / `restore` limpian jobs no activos. SUCCESS se borra tras ~2.4 s.

**WorkManager cleanup:** el worker termina al devolver true. Cancel cancela el scheduler y limpia store.

**Restart behavior:** `restore()` descarta jobs no activos → no banner.

**Account switch:** `actorUserId` + `belongsTo` + `onSessionEnded` + keys por usuario.

**Result:** SUCCESS/FAILED/CANCELLED no dejan cartel persistente. FAILED: Reintentar + Cerrar.

---

## SOCIAL MEDIA VISIBILITY

**Root cause (doble):**

1. `_acl_media_readable` no tenía “post visible ⇒ su media”. Assets PRIVATE de un post/Reel autorizado fallaban.
2. Android `FileDisplayResolver` / `requestSignedUrl` exigían `media_assets` SELECT + `FileAuthorization.canRead` (solo owner). Los amigos nunca llegaban a `canon_authorize_media_signed_url`. Por eso 1082 no alcanzó.

**Post visibility:** sin cambio. PUBLIC / FOLLOWERS + ACCEPTED.

**Media ACL:** `_acl_social_post_media_readable(viewer, asset)` — asset es `social_posts.media_asset_id` o `composition.extra_media_asset_ids` **y** `_canon_social_post_visible`.

**Signed URL:** Edge + `canon_authorize_media_signed_url`. El cliente ya no niega si el SELECT local falla.

**Single / multiphoto / Reel:** mismo resolver + misma ACL.

**Unauthorized:** 403 del authorize. Sin SELECT general a private-media.

**Result:** si el feed muestra el post, la media de ESE post debe resolverse.

---

## CONNECTION PERSON AVATAR

**Root cause after 1082:** ACL SQL existía; el resolver Android fallaba antes (SELECT/client ACL). RPC `canon_get_public_person` devolvía `avatar_path` = `avatar_asset_id`; el DTO ahora también lee `avatar_asset_id`.

**RPC projection:** `avatar_asset_id` + `avatar_path`.

**Asset:** `persons.avatar_asset_id` exacto.

**ACL:** self / ACCEPTED / admin.

**Resolver:** signed URL vía authorize si el SELECT no está permitido.

**Result:** conexión ACCEPTED debe ver el avatar PERSON. Extraño no.

---

## CONNECTION PET AVATAR

**Root cause after 1082:** ACL pedía solo OWNER/PRINCIPAL. Perfil social puede mostrar la mascota por cualquier vínculo PERSON ACTIVE o custodio actual.

**RPC projection:** sin cambio de contrato de listado; el asset sigue siendo `pets.avatar_asset_id`.

**ACL:** asset == `pets.avatar_asset_id` + mascota ACTIVE asociada al PERSON del perfil + viewer self/ACCEPTED/admin. No convierte en responsable.

**Resolver:** mismo camino que PERSON.

**Result:** avatar PET de la conexión visible. Extraño no.

---

## MI MANADA

**Connection info:** persona (avatar, nombre, username) + acciones.

**Pets:** avatar, nombre, especie, sexo, edad (1081).

**Embedded post history removed:** navegación `from=manada` oculta el muro de publicaciones. El perfil público desde feed/autor sigue mostrando la pared social autorizada.

**Public profile unaffected:** `userProfile(id)` sin `from` no cambia.

**Result:** Mi manada no incrusta historial/feed.

---

## STORIES

**Ring:** `StoryRingPolicy` / `isActiveStory()`. Sin story activa → sin ring. Tap sin story no abre viewer vacío.

**Result:** revalidar en físico. Lógica no reescrita.

---

## GUARDADOS

Mismo resolver + ACL social. POST y REEL guardados deben abrir y reproducir. Un-save elimina. “Medio no disponible” solo si authorize falla de verdad.

---

## SECURITY

| Check | |
|---|---|
| General private media widened | NO |
| RLS broadly relaxed | NO |
| Connection becomes responsible | NO |
| Health exposed | NO |
| Service role Android | NO |
| New migration | `20260913180000_1083_memories_transfer_gate_and_social_media_acl.sql` |
| Applied STAGING | YES (`tobqbddfcyitwgbkthhy`, version `20260913180000`) |

---

## INTERNAL UX FOLLOW-UP

El pass inicial de internas dejó pantallas boxed. Follow-up visual (sin cambiar lógica):

- Comunidad detalle: sin `V2SurfaceCard`, inputs Leo, sin `BrandOrangeContainer`
- VitaCora hub: secciones y listados como rows/hairlines
- Guardados: caption 2.0
- Cuenta/seguridad y consentimientos: Leo buttons/fields

Aún pueden quedar colas admin densas y el composer de Publish con chips Material. No es un Design System distinto.

## APK

path: `apk/LeoVer-M08-Staging-debug.apk`  
SHA-256: `9379E63641CC25D41576083BFFBD0D62FD9A34592B4B2D4529D22B73166264DD`

Assemble que incluye el pass de internas + este follow-up. El SHA anterior (`B41A…`) quedó atrás.

## PENDIENTES FÍSICOS

1. Medir `ReelPublishStage` en un clip real (SOURCE_COPY → SUCCESS).
2. Confirmar avatars PERSON/PET de una amistad ACCEPTED concreta.
3. Confirmar Reel/post viejo visible y reproducible desde otra cuenta autorizada.
4. Transferencia ACCEPTED → recuerdos del vínculo anterior.
5. Cerrar/reabrir y A→logout→B sin leak de banner.

Health / login / Google / mensajes / responsables: smoke sin modificación de comportamiento.
