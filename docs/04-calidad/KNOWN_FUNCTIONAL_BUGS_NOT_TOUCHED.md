# KNOWN_FUNCTIONAL_BUGS_NOT_TOUCHED

Bugs funcionales detectados en prueba real. **No resolver dentro de UI-V2-03.**

No modificar SQL/backend, no generar IDs desde Android, no cambiar contratos para ocultar el fallo.

---

## LF-DB-001 — INSERT `lost_found_posts` / `gen_random_bytes`

| Campo | Valor |
|---|---|
| ID | LF-DB-001 |
| Estado | **NO TOCADO** (requiere fix SQL/backend aparte) |
| Severidad | Alta (no se puede publicar alerta perdida **ni** encontrada) |
| Detección | Prueba real Android — publicar animal perdido y encontrado |
| Entorno | Supabase / PostgreSQL |

```text
LOST_POST_CREATION = HOTFIX_082
FOUND_POST_CREATION = HOTFIX_082
LOST_FOUND_DB_ERROR = function gen_random_bytes(integer) does not exist
LOST_FOUND_DB_FIX_REQUIRED = NO (migración 082)
GEN_RANDOM_BYTES_CALLER = public._web_generate_public_code (trigger lost_found_posts_ensure_public_code)
```

Causa exacta: migración `081_web_public_shareable_pages.sql` define `public._web_generate_public_code()` con `search_path = public` y llama `gen_random_bytes(16)` **sin schema**. En Supabase, `pgcrypto` está en `extensions`. El trigger `lost_found_posts_ensure_public_code` (INSERT Lost y Found) falla.

**Hotfix:** `supabase/migrations/082_lost_found_public_code_pgcrypto_schema.sql` reemplaza la función con `extensions.gen_random_bytes`. No se edita 081. No se generan IDs en Android.

**UI-V2-03 (solo presentación):** el usuario ya no ve URL, Authorization, Bearer, headers ni el error PostgREST crudo.

```text
RAW_SUPABASE_ERROR_EXPOSED_TO_USER = FIXED
RAW_BACKEND_ERROR_HIDDEN = YES
```

---

## UI-V2-03 — Form UX (entrega)

Auditoría estática. Validación en teléfono físico a cargo de Leonardo. Sin emulador ni ADB.

```text
LOST_POST_DB_BLOCKED = YES
FOUND_POST_DB_BLOCKED = YES
RAW_BACKEND_ERROR_HIDDEN = YES

PREVIEW_ONLY_CROPPED = NO
UPLOADED_IMAGE_ACTUALLY_CROPPED = NO
IMAGE_FORM_PREVIEW_FIXED = YES
IMAGE_FORM_PREVIEW_MODE = Fit
UPLOAD_PIPELINE_CHANGED = NO

GLOBAL_IME_FORM_FIX = YES
FOCUSED_FIELD_VISIBLE_WITH_IME = YES
FORM_SCROLL_WITH_KEYBOARD = YES
IME_NEXT_DONE_REVIEW = YES

LOST_FOUND_FORM_V2 = YES
```

Notas:

- IME global: `Modifier.imePadding()` en el NavHost raíz. `windowSoftInputMode=adjustResize` se mantiene (edge-to-edge).
- Campo enfocado: `v2KeepVisibleOnFocus()` (BringIntoView) en `V2FormTextField`, ubicación, password y formularios principales.
- Next/Done: `V2FormTextField` (Next en intermedios, Done en el último; multilínea sin Done).
- Preview de formulario: `V2FormImagePreview` con `ContentScale.Fit`. Thumbnails de feed/card siguen en Crop.
- Upload: se envían los bytes originales; no hay recorte destructivo.
