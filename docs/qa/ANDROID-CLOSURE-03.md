# ANDROID CLOSURE 03

**Fecha:** 2026-09-13  
**Staging:** `tobqbddfcyitwgbkthhy`  
**Restore test (no usar):** `wystsapjfpdtoprlmizz`  
**Marca:** LeoVer · **Producto mascota:** VitaCora

Working tree dirty. Sin commit / push. Sin Web.

Migración máxima inspeccionada: **1083** (`20260913180000`). No se creó 1084: PK `(post_id, pet_id)` + `ON CONFLICT DO NOTHING` y upsert VitaCora por `source_social_post_id` ya existen.

---

## 1083 RECONCILIATION

CLOSURE-03 **no creó** 1083 ni 1084. El informe funcional que dijo “migración nueva: ninguna” era correcto para *este* bloque, pero omitió que 1083 ya existía (ANDROID-FINAL-CLOSURE). Eso se leía como contradicción si se entraba a CLOSURE-03 creyendo que el máximo era 1082.

| Campo | Valor |
|---|---|
| Filename | `infra/supabase-canonical/supabase/migrations/20260913180000_1083_memories_transfer_gate_and_social_media_acl.sql` |
| Timestamp | `20260913180000` |
| Created during CLOSURE-03 | **NO** — disco 13/09/2026 ~12:58 (FINAL-CLOSURE). CLOSURE-03 empezó después. |
| Git | untracked (`??`). No se edita. |
| Purpose | Mis recuerdos solo si hay transferencia PERSON ACCEPTED; ACL estrecho de media de post social y avatares; `canon_get_public_person` expone `avatar_asset_id`. |
| Objetos | `_canon_viewer_completed_care_transfer`, `_canon_media_is_vitacora_personal_memory`, `_acl_social_post_media_readable`, `_acl_social_avatar_readable`, `_acl_media_readable`, `canon_get_public_person` |
| Grants | `revoke all` de esas funciones a `public, anon`. `notify pgrst, 'reload schema'`. |
| Applied STAGING | **YES** — documentado en `docs/qa/ANDROID-FINAL-CLOSURE.md` (`tobqbddfcyitwgbkthhy`, version `20260913180000`). Esta continuación **no** reaplicó ni reparó 1083. |
| 1084 | **NO** existe. No se crea: no hay cambio backend nuevo. |

---

## REEL FALSE FAILURE

**Exact root cause:** `canon_create_social_post` devolvía `postId` válido. `confirmCreatedReel` hacía `social_posts` SELECT por `media_asset_id` y, si el lookup no coincidía (RLS / timing / vacío), lanzaba `REEL_MEDIA_ASSOCIATION_NOT_VISIBLE`. El catch marcaba FAILED. `VitaCoraSocialSave` nunca corría. `fromBlob` clasificaba eso como UPLOAD → “No pudimos publicar el Clip”.

**Post created at phase:** CREATE (`addReel` / `backendPostId`).

**Failure occurred at phase:** post-CREATE confirm lookup (antes de ATTACH_PETS / VITACORA).

**Old behavior:** job FAILED aunque el Reel existiera; banner genérico; retry podía reconfirmar y volver a fallar; VitaCora ausente.

**New resumable behavior:** fases PREPARE → UPLOAD → REGISTER → CREATE → ATTACH_PETS → VITACORA → DONE. Con `postId` + `assetId` se persiste `createCompleted` de inmediato. Retry no re-sube, no re-registra, no re-crea. Confirm es nota/debug, no gate.

**Duplicate prevention:** mismo `jobId` / `assetId` / `postId`. Pets: PK + `ON CONFLICT DO NOTHING`. VitaCora: upsert canónico. Fallback `createMoment` solo si el RPC no existe.

**Result:** Reel creado = no es “clip no publicado”. Si falta el vínculo de mascota: copy LINK_FAILED + Reintentar / Cerrar.

---

## REEL → VITACORA

**selectedPetIds preserved:** `accept()` persiste `petIds` + `SocialPostMedia.withPetIds` en composition.

**social_post_pets:** `canon_create_social_post` → `_canon_attach_social_post_pets` (p_pet_id + composition `pet_ids`).

**VitaCora link:** mismo `VitaCoraSocialSave` / `canon_save_social_vitacora_moment` que el POST. Sin pet → no se llama.

**Retry:** si CREATE ok y VitaCora falla → fase VITACORA solamente.

**Result:** el falso FAILED ya no impide el momento. Sin mascota → sin VitaCora.

---

## Banner / restart

SUCCESS: no espera feed refresh / signed URL / player. Refresh es async. Banner SUCCESS se limpia (~2.4 s) → DataStore clear, controller idle.

FAILED retryable se conserva en restore. DONE no reconstruye banner. Otro actor: `belongsTo` + store por usuario.

---

## MIS RECUERDOS

Gate 1083 sin cambios: solo transferencia PERSON ACCEPTED. Pendiente / cancelada / rechazada → no. Mascota actual en VitaCora ≠ Mis recuerdos.

---

## MEDIA / AVATARS / MI MANADA

ACL 1083 + signed URL vía Edge (sin exigir SELECT cliente). Mi manada sin muro (`from=manada`). Avatares persona/pet siguen el ACL avatar-only de conexión ACCEPTED.

---

## PERFORMANCE / DS2

Ver `docs/performance/ANDROID-PERFORMANCE-AUDIT.md` y `docs/design/LEOVER-UI-AUDIT.md` §8.

Pass visual post-reinicio: Lost/Found, colas admin, Search, M15–M22/M26/M28, Profile invites, FeedPostCard, PetV2Card, My organizations, Mi negocio. Sin cambio funcional ni backend.

READY FOR WEB: **NO**.

## APK

path: `apk/LeoVer-M08-Staging-debug.apk`  
SHA-256: `FA570F2A91C0E573913A3AD27E53005EB18EE9B23D2B4C03B0124C63226D98AA`

---

## Continuación

ANDROID-POLISH-05 continúa este cierre: feed Stories/POST/Clips, cámara de publicación, flip de historias, composers/pet/VitaCora/Sumate.  
Ver `docs/qa/ANDROID-POLISH-05.md`. CLOSURE-03 no se reabre.
