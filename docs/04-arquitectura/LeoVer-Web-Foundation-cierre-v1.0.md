# LeoVer Web Foundation — Cierre v1.0

**Fecha:** 2026-08-09  
**Rama:** `main`  
**Commit:** (pendiente al publicar)  
**Estado:** FOUNDATION IMPLEMENTADA — validación local completada

---

## 1. Resumen

Se creó el workspace web oficial único en `web/` — superficie transversal compartida por web pública futura, organizaciones, M28, M29 y admin. Sin funcionalidad profunda de esos dominios en este bloque.

---

## 2. Workspace y stack

| Componente | Versión / valor |
|------------|-----------------|
| Ubicación | `web/` (raíz repo) |
| Package manager | pnpm 10.12.4 |
| Node.js (dev local) | v24.13.0 |
| Next.js | 15.5.12 |
| React | 19.1.0 |
| TypeScript | ^5 |
| Tailwind CSS | ^4 |
| @supabase/supabase-js | ^2.49.4 |
| @supabase/ssr | ^0.6.1 |
| @opennextjs/cloudflare | ^1.20.2 |
| wrangler | ^4.86.0 |
| vitest | ^3.2.7 |

---

## 3. Dominios

| Dominio | Rol |
|---------|-----|
| **https://leover.com.ar** | Canónico — `NEXT_PUBLIC_APP_URL` |
| leoverapp.com.ar | Defensivo — redirect futuro → canónico |
| leoverapp.com | Reserva internacional — no canónico V1 |
| www.leover.com.ar | Redirect futuro → canónico |

DNS, redirects Cloudflare y deploy productivo: **no configurados** en Foundation.

---

## 4. Archivos principales

```text
web/
├── app/                    # Route groups (public, auth, account, org, professional, brand, admin)
├── components/ui/          # Button, Card, Input
├── components/layout/      # PageShell
├── features/auth/          # LoginForm, actions
├── lib/supabase/           # client, server, middleware
├── lib/auth/               # session helpers
├── lib/env.ts              # getAppUrl(), Supabase env
├── middleware.ts           # session refresh + /cuenta guard
├── scripts/prepare-opennext.mjs
├── wrangler.jsonc
├── open-next.config.ts
└── tests/

.github/workflows/web-ci.yml
docs/04-arquitectura/LeoVer-Arquitectura-Web-v1.0.md
```

---

## 5. Auth

- Patrón `@supabase/ssr` con cookies
- Middleware: refresh + redirect `/cuenta` → `/acceso` sin sesión
- Rutas: `/acceso`, `/acceso/callback`, `/cuenta` (privada), logout server action
- **Sin** service role en cliente
- Misma identidad Supabase que Android

---

## 6. Supabase

- Mismo backend — sin DB nueva, sin SQL, sin migraciones
- Variables: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- RLS/RPC siguen siendo autoridad

---

## 7. Rutas implementadas

| Ruta | Tipo |
|------|------|
| `/` | Pública — landing Foundation |
| `/acceso` | Login |
| `/acceso/callback` | Auth callback |
| `/cuenta` | Privada — sesión mínima + logout |
| `/org`, `/profesional`, `/marca`, `/admin` | Placeholders “próximamente” |

---

## 8. Cloudflare / OpenNext

- `output: "standalone"` en `next.config.ts`
- `scripts/prepare-opennext.mjs` — workaround App Router `pages-manifest.json`
- Wrangler: `nodejs_compat`, `global_fetch_strictly_public`
- Preview local vía `pnpm preview` (Workers runtime)
- **Sin** deploy productivo

---

## 9. Imágenes

**Decisión:** `<img>` nativo + `images.unoptimized: true` — compatible con Workers/OpenNext.

---

## 10. Testing

| Suite | Resultado |
|-------|-----------|
| `pnpm typecheck` | PASS |
| `pnpm test` (5 tests) | PASS |
| `pnpm build` | PASS |
| `pnpm cf:build` (OpenNext) | PASS (local Windows; CI Linux) |
| `pnpm preview` (Workers) | Startup OK; runtime completo validar en Linux/WSL |

Tests: landing render, auth redirect helper, UI components.

---

## 11. CI

`.github/workflows/web-ci.yml` — path filter `web/**`:

- install frozen lockfile
- guard service_role
- typecheck, test, build, OpenNext build

---

## 12. Decisiones cerradas

- Workspace `web/` + pnpm
- App Router + route groups
- Dominio canónico `leover.com.ar`
- Routing path-based (sin subdominios pro/brand/admin)
- `<img>` sobre `next/image`
- OpenNext + wrangler configurados

---

## 13. Pendientes (post-Foundation)

1. Web pública slice (`/mascota`, `/perdidos`, `/adopciones`)
2. M29 Brand Studio Pilot-Minimum
3. M28 Portal Profesional Web
4. DNS + redirects Cloudflare (leoverapp.com.ar, www → canónico)
5. Deploy Cloudflare Workers Builds
6. `assetlinks.json` / AASA para App Links

---

## 14. Validaciones

| Check | Resultado |
|-------|-----------|
| SQL modificado | NO |
| Android funcional modificado | NO |
| M28 implementado en web | NO |
| M29 implementado | NO |
| Secretos commiteados | NO |
| Service role en web sources | NO |

---

## 15. Referencias

- `docs/04-arquitectura/LeoVer-Arquitectura-Web-v1.0.md`
- `web/README.md`
