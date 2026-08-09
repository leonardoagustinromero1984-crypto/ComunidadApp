# LeoVer Web

Fundación web oficial de LeoVer — una sola aplicación Next.js para web pública, organizaciones, portal profesional M28, Brand Studio M29 y administración.

## Stack

| Paquete | Versión (Foundation) |
|---------|----------------------|
| Node.js | 22+ LTS recomendado (dev local probado con 24.x) |
| pnpm | 10.12.4 (`packageManager`) |
| Next.js | 15.5.12 |
| React | 19.1.0 |
| TypeScript | ^5 |
| Tailwind CSS | ^4 |
| @supabase/supabase-js | ^2.49.4 |
| @supabase/ssr | ^0.6.1 |
| @opennextjs/cloudflare | ^1.20.2 |
| wrangler | ^4.86.0 |
| vitest | ^3.1.2 |

## Dominio canónico

| Dominio | Rol |
|---------|-----|
| **https://leover.com.ar** | URL canónica pública (metadata, OG, callbacks, links) |
| leoverapp.com.ar | Secundario — redirect permanente futuro a leover.com.ar |
| leoverapp.com | Reserva internacional — no canónico en V1 |
| www.leover.com.ar | Redirect futuro a leover.com.ar (Cloudflare — no configurado aún) |

**Routing:** un solo dominio (`leover.com.ar`) + paths — sin subdominios `pro.`, `brand.` ni `admin.` en V1:

```text
/  /acceso  /cuenta
/mascota/...  /perdidos/...  /encontrados/...  /adopciones/...
/organizaciones/...  /profesional/...  /marca/...  /admin/...
```

**Redirects futuros (Cloudflare — no configurados):** `leoverapp.com.ar`, `leoverapp.com` y `www.leover.com.ar` → `https://leover.com.ar`.

**App Links (futuro):** Android App Links e iOS Universal Links usarán URLs HTTPS de `leover.com.ar`. Sin `assetlinks.json` ni `apple-app-site-association` en Foundation.

## Requisitos

- Node.js 22+ (o 20 LTS mínimo)
- pnpm vía Corepack: `corepack pnpm install` (si `pnpm` no está en PATH)
- Proyecto Supabase existente (mismo que Android)

## Instalación

```bash
cd web
corepack pnpm install
cp .env.example .env.local
# Completar variables en .env.local (sin commitear)
```

## Variables de entorno

Ver `.env.example`. Variables públicas:

| Variable | Descripción |
|----------|-------------|
| `NEXT_PUBLIC_APP_URL` | URL canónica — `https://leover.com.ar` |
| `NEXT_PUBLIC_SUPABASE_URL` | URL del proyecto Supabase (Android: `SUPABASE_URL`) |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Clave anon/public (Android: `SUPABASE_ANON_KEY`) |

**No usar service role en el cliente web.**

## Scripts

```bash
corepack pnpm dev          # Next.js dev server (Node)
corepack pnpm typecheck    # tsc --noEmit
corepack pnpm test         # Vitest unit tests
corepack pnpm build        # next build
corepack pnpm preview      # OpenNext build + wrangler dev (Workers runtime)
```

## Estructura

```text
web/
├── app/
│   ├── (public)/          # Landing /
│   ├── (auth)/acceso/     # Login, callback
│   ├── (account)/cuenta/  # Ruta privada
│   ├── (org)/org/         # Placeholder M03
│   ├── (professional)/profesional/  # Placeholder M28
│   ├── (brand)/marca/     # Placeholder M29
│   └── (admin)/admin/     # Placeholder M04
├── components/ui/         # Button, Card, Input
├── components/layout/     # PageShell
├── features/auth/         # LoginForm, actions
├── lib/supabase/          # browser, server, middleware clients
├── lib/auth/              # session helpers
└── tests/
```

## Supabase

- Mismo backend que Android — sin DB ni Auth separados
- Clientes: `lib/supabase/client.ts` (browser), `server.ts` (RSC/actions), `middleware.ts` (refresh)
- Autorización real: RLS + RPC en backend

## Auth

- Supabase Auth con `@supabase/ssr`
- Rutas: `/acceso`, `/acceso/callback`, `/cuenta` (protegida)
- Middleware refresca sesión y redirige `/cuenta` → `/acceso` sin sesión
- Logout vía server action

## Cloudflare / OpenNext

- Adaptador: `@opennextjs/cloudflare`
- Config: `open-next.config.ts`, `wrangler.jsonc`
- `scripts/prepare-opennext.mjs` — prepara árbol standalone para OpenNext (dev Windows sin symlinks)
- Flags: `nodejs_compat`, `global_fetch_strictly_public`
- Preview local: `pnpm preview` (Workers runtime via wrangler)
- **No deploy productivo** en Foundation

## Imágenes (`next/image` vs `<img>`)

**Decisión Foundation:** usar `<img>` nativo y `images.unoptimized: true` en `next.config.ts`.

Motivo: evitar dependencia del optimizador de imágenes de Next.js en Workers/OpenNext; simplicidad y compatibilidad confirmada en build. Reevaluar si OpenNext habilita optimización estable en producción.

## Qué NO está implementado

- Web pública compartible (mascota, perdidos, adopciones)
- Portal organizaciones funcional
- M28 portal veterinario
- M29 Brand Studio
- Admin M04
- OpenAI / Mercado Pago
- Deploy Cloudflare production / DNS
- App Links assets (`assetlinks.json`, AASA)

## CI

Workflow: `.github/workflows/web-ci.yml` — path filter `web/**`, typecheck, tests, build, OpenNext build.
