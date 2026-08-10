# LeoVer — Web Pública Compartible — Cierre v1.0

**Fecha:** 2026-08-10  
**Rama:** `main`  
**Slice:** primer bloque de páginas públicas sanitizadas en `web/`

---

## 1. Objetivo cumplido

Páginas HTTPS canónicas en `https://leover.com.ar` para compartir:

- Mascota (Pasaporte M14 público)
- Mascota perdida
- Mascota / animal encontrado
- Adopción publicada

Sin login. Sin PII por defecto. Contratos backend explícitos.

---

## 2. Rutas web

| Ruta | Archivo |
|------|---------|
| `/mascota/[publicCode]` | `web/app/(public)/mascota/[publicCode]/page.tsx` |
| `/perdidos/[publicCode]` | `web/app/(public)/perdidos/[publicCode]/page.tsx` |
| `/encontrados/[publicCode]` | `web/app/(public)/encontrados/[publicCode]/page.tsx` |
| `/adopciones/[publicCode]` | `web/app/(public)/adopciones/[publicCode]/page.tsx` |

`revalidate = 60` en cada página (estados cambian; no cache indefinido).

---

## 3. Dominios reutilizados

| Módulo | Uso |
|--------|-----|
| **M08** | Foto/edad de mascota vinculada a adopción vía join interno en RPC (sin exponer `pet_id`) |
| **M09** | Publicaciones `adoptions` — estados `PUBLISHED` / `ADOPTED` / `CLOSED` |
| **M13** | Patrón de zona aproximada (`zone_text`), sin coords en JSON público |
| **M14** | Pasaporte `PUBLIC_REDACTED` — autoridad de identidad pública de mascota |
| **lost_found_*** | Tabla `lost_found_posts` — tipos `LOST` / `FOUND`, estados `ACTIVE` / `RESOLVED` |
| **M04** | `_web_is_content_blocked` consulta `moderation_actions` (`CONTENT_HIDDEN`, `CONTENT_REMOVED`) |
| **M05** | `_web_sanitize_public_image` — solo URLs públicas o `storage:bucket/path` resoluble |

---

## 4. Migración SQL

**Archivo:** `supabase/migrations/081_web_public_shareable_pages.sql`

- Columnas `public_code` en `adoptions` y `lost_found_posts` (backfill + trigger insert)
- Helpers: `_web_generate_public_code`, `_web_is_content_blocked`, `_web_sanitize_public_image`, `_web_public_zone_text`
- RPC anon:
  - `get_public_pet(text)`
  - `get_public_adoption(text)`
  - `get_public_lost_case(text)`
  - `get_public_found_case(text)`

**Apply:** manual según flujo del proyecto (no auto-aplicado a producción en este bloque).

---

## 5. Sanitización / privacidad

Excluido de todas las RPC públicas:

- Teléfono, email, `contact_info`
- `author_id`, `author_name`, `publisher_id`, `pet_id`
- Coordenadas exactas (`latitude`, `longitude`)
- Postulaciones M09, avistamientos gestionados M13, datos clínicos M28
- Motivos de moderación internos

404 uniforme para inexistente / no público / moderado (sin filtrar “existe pero privado”).

---

## 6. SEO / Open Graph

- `generateMetadata` server-side por página
- `title`, `description`, `canonical`, Open Graph, Twitter
- Imagen OG: foto pública segura si existe; si no, favicon LeoVer
- `robots`: indexable solo en estados activos (`is_active` / pasaporte público)

---

## 7. Compartir

- Componente `ShareButton` — Web Share API + fallback copiar enlace
- URL siempre canónica `https://leover.com.ar/...`

---

## 8. UI

- Foundation existente: `PageShell`, `Button`, `Card`, tokens LeoVer
- Header público: LeoVer · Inicio · Ingresar
- CTAs: Ingresar / Quiero adoptar / Reportar avistamiento (sin App Links aún)

---

## 9. Pruebas

- `web/tests/public-shareable.test.ts` — helpers, metadata, guards estáticos migración 081
- Suite existente de foundation sin regresión

---

## 10. Pendientes Post-Pilot

- Sitemap dinámico de páginas elegibles
- `www.leover.com.ar` → apex
- Supabase Redirect URLs productivas (callback web)
- Apply remoto migración 081 + Web Deploy manual
- App Links / Universal Links sobre mismas rutas HTTPS
- Pasaporte: foto pública cuando M14 exponga media sanitizado

---

## 11. Fuera de alcance (confirmado)

- M28 portal veterinario
- M29 publicidad
- Modificaciones Android
- App Links / Universal Links
