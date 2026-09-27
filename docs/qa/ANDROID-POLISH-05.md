# ANDROID POLISH 05

**Fecha:** 2026-09-13  
**Staging:** `tobqbddfcyitwgbkthhy`  
**Restore test (no usar):** `wystsapjfpdtoprlmizz`  
**Marca:** LeoVer · **Producto mascota:** VitaCora

Working tree dirty. **NO COMMIT. NO PUSH.**  
Web no empezó. READY FOR WEB: **NO**.

Migración máxima: **1083** (`20260913180000_1083_memories_transfer_gate_and_social_media_acl.sql`).  
Migration nueva: **NONE**. Backend: **NO**.

APK de referencia previa (CLOSURE-03):  
`FA570F2A91C0E573913A3AD27E53005EB18EE9B23D2B4C03B0124C63226D98AA`

---

## Contrato visual

| Kind | Dónde se ve | Qué es |
|---|---|---|
| **STORY** | Tray de historias arriba del Feed | Historia 24 h |
| **POST** | Card vertical de publicación | Publicación social (texto, foto, multiphoto o video) |
| **REEL / Clip** | Carrusel **Clips** + visor vertical | Video corto creado desde Publicar → Reel |

**POST VIDEO ≠ REEL.**  
Video grabado o elegido desde Publicación = POST.  
Video desde Publicar → Reel = REEL.  
Nunca se convierte solo. El MIME no cambia el kind.

POST / REEL / POST VIDEO + mascota seleccionada → VitaCora de esa mascota.  
Sin mascota → no se agrega a VitaCora.  
Seleccionar mascota no cambia creator / responsables / custodio / grants.  
Mis recuerdos permanece separado (gate 1083).

---

## SOCIAL FEED

**Stories placement:** tray arriba (`StoriesRow`).  
**Post presentation:** `LeoSocialPostCard` — avatar, nombre, contexto, timestamp, copy, media, reacciones.  
**Post video presentation:** misma card, ratio 4:5, `ReelFeedMedia` con frame real + play discreto. **No** entra al carrusel.  
**Clips carousel:** sección `Clips` horizontal, thumbs ~9:16.  
**Reel duplicate prevention:** `SocialFeedComposition.publications()` excluye `PostType.REEL`.  
**Clip preview:** `VideoPreviewFrame` (JPEG cacheado en IO + disco). Sin player en el carrusel.  
**Clip tap:** `clips/{postId}` → `ClipViewerScreen`.  
**Reel viewer:** `HomeReelsTab` vertical, overlay de autor/caption/acciones.  
**Performance strategy:** un frame cacheado por URL; ExoPlayer solo tras tap y solo en la página visible del visor.

---

## POST CAMERA

**Camera implementation:** `LeoVerCaptureCamera` (CameraX). No Chrome / browser / Google intent.  
**Photo:** captura JPEG en cache + FileProvider.  
**Video recording:** MP4 + indicador “Grabando…”.  
**Audio:** `RECORD_AUDIO` si está concedido; si no, video sin audio y mensaje humano.  
**Front/back:** `lensFacing` recrea use cases.  
**Flip:** “Girar cámara”; deshabilitado durante grabación; oculto si hay una sola lente.  
**Permissions:** CAMERA + RECORD_AUDIO.  
**Single-camera fallback:** sin botón Girar.  
**Output URI/file:** `FileProvider` `${package}.fileprovider`, cache-path.  
**Result:** compositor recibe URI; kind sigue POST.

---

## POST GALLERY

**Images:** Photo Picker, hasta el máximo existente.  
**Multi-image:** se conserva.  
**Video:** picker VideoOnly; reemplaza media (sin mixto foto+video).  
**Video remains POST:** `publishGeneral` → `PostType.GENERAL` → `kind = "POST"`.  
**Preview:** foto real / frame de video + copy “Video en publicación — no es un Clip.”  
**Result:** galería no abre cámara web.

---

## POST → VITACORA

**Photo + pet:** mismo `VitaCoraSocialSave` existente.  
**Video + pet:** igual; kind POST.  
**No pet:** no se llama save VitaCora.  
**Result:** sin cambio de linking.

---

## REEL

**Carousel representation:** solo Clips.  
**Preview:** frame cacheado, play 28 dp oscuro.  
**Viewer:** vertical, pager si hay más clips.  
**Publish transaction:** sin cambio (`ReelPublishController`, fases, “Tu Clip se está publicando.”).  
**Reel + pet:** `VitaCoraSocialSave.saveApprovedReel` intacto.  
**Result:** ya no es pantalla negra + Play verde.

---

## STORIES

**Camera:** misma `LeoVerCaptureCamera` (BOTH).  
**Front/back / Flip:** use cases nuevos por lente; bind fallback si HD falla.  
**Photo / Video / Gallery:** se conservan.  
**Result:** giro no cierra el compositor.

---

## COMPOSERS DS2

**Post / Reel / Story:** top bar Leo, `LeoTextField`, rows, CTA `LeoPrimaryButton`.  
**Media sheet:** `LeoVerMediaSourceSheet`.  
**Pet selector:** avatar + nombre + especie, sin check Material enorme.  
**Privacy:** PUBLIC / PRIVATE, solo chips Leo.  
**Location:** row icono/label/valor.  
**Progress:** fases compactas existentes.  
**Errors:** Reintentar / Cerrar sin cambiar la máquina de estados.

---

## PET PROFILE

**Hero:** foto 4:3.  
**Metadata:** especie · sexo · edad.  
**Actions:** VitaCora primaria, Editar outline, filas Compartir/QR / propuestas / acceso, Perdí distintiva.  
**Sections:** Información / Salud / Accesos — no card por campo.  
**Result:** perfil social, no ficha CRUD. Sin IDs internos.

---

## VITACORA

**Header:** foto + nombre + metadata.  
**Copy:** “VitaCora nace de vita (vida) y cora (corazón).”  
**Summary / Moments / Care / Access / Actions:** Identidad, Resumen, Momentos, Accesos, Acciones.  
**Density:** más espacio y hairlines. Sin tabs nuevas.  
**Result:** más aire, misma lógica.

---

## SUMATE

**Adoption / Lost / Found / Transit:** hub `V2NavRow` existente.  
**Campaigns / Events:** listas con hairline, `LeoFilterChip`, forms `LeoTextField`.  
**Result:** sin FilterChip Material crudo en estas rutas.

---

## PERFORMANCE

**Reel carousel players active simultaneously:** 0.  
**Thumbnail strategy:** un frame JPEG / URL, IO, Lru + disco.  
**Caching:** `VideoPreviewFrameCache`.  
**Camera file copies:** un archivo cache por captura.  
**New obvious N+1:** no.  
**Main-thread heavy work:** extracción de frame en `Dispatchers.IO`.  
**Result:** no se reintrodujo poll de perfil ni players por thumb.

---

## FUNCTIONAL REGRESSION

Backend changed: **NO**  
Migration: **NONE**  
Health / Mis recuerdos / Responsibility / Transfers / Privacy / Security / Session / Reel transaction: **UNCHANGED**

---

## TESTS

`:app:testLocalDebugUnitTest` dirigido:

- `SocialFeedCompositionTest`
- `AndroidPolish05ContractTest`
- `ReelFeedMediaContractTest`

---

## APK

path: `apk/LeoVer-M08-Staging-debug.apk`  
SHA-256: `AA6707DC9EB89DD05B4F218CB5C770C9E1676E908F991993C2840E8C46A1F583`

---

## READY FOR PHYSICAL QA

YES

## READY FOR WEB

NO

## GIT

NO COMMIT.  
NO PUSH.
