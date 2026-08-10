# LeoVer — Arquitectura Web v1.0

**Producto:** LeoVer  
**Tipo:** Superficie / plataforma transversal (no es un Mxx)  
**Versión:** 1.0  
**Fecha:** 2026-08-09  
**Estado:**

```text
FOUNDATION IMPLEMENTADA — workspace web/ en repo
Dominio canónico: https://leover.com.ar
Prerrequisito desbloqueado para: web pública slice, M29 Brand Studio, M28 portal web
```

**Ruta de repositorio:** `/docs/04-arquitectura/LeoVer-Arquitectura-Web-v1.0.md`

---

## 1. Identificación

| Campo | Valor |
|-------|-------|
| Nombre | Arquitectura Web oficial LeoVer |
| Alcance | Una sola aplicación web en el monorepo `ComunidadApp` |
| Superficies alojadas | Pública, auth, org, profesional M28, Brand Studio M29, admin |
| ID modular | **Ninguno** — Web es transversal (D01 §9.3, §10) |
| Relación Android | Mismo repo, mismo Supabase, identidad compartida |

---

## 2. Estado

| Aspecto | Estado repo actual |
|---------|-------------------|
| Workspace Next.js | **Presente** — `web/` (pnpm, Next 15.5.12, OpenNext) |
| CI web | **Presente** — `.github/workflows/web-ci.yml` |
| Dominio canónico | **leover.com.ar** — adquirido; DNS/deploy pendiente |
| Supabase | **Presente** — `supabase/` (migraciones 001–080, config, Edge Functions parciales) |
| Android | **Presente** — Gradle `:app`, Kotlin/Compose (sin cambios funcionales Foundation) |
| Bloqueo M28 web | Portal web pendiente — fundación lista |
| Bloqueo M29 | Brand Studio pendiente — fundación lista |

---

## 3. Objetivo

Definir **una fundación web única** capaz de alojar progresivamente:

1. Web pública compartible (SEO, perdidos, adopciones, mascotas, orgs)  
2. Portal de organizaciones (M03)  
3. Portal profesional veterinario (M28)  
4. Brand Studio (M29)  
5. Superficies comerciales y administración (M04/M07)  

Sin crear un proyecto Next.js por módulo ni duplicar backend, auth o design system.

---

## 4. Alcance

- Ubicación oficial del workspace futuro  
- Stack, runtime, routing, layouts, auth, Supabase, Cloudflare/OpenNext  
- Patrones de componentes, data fetching, formularios, storage  
- Seguridad, privacidad, SEO, observabilidad  
- CI/CD GitHub + Cloudflare Workers Builds  
- Web Foundation Minimum (siguiente bloque técnico)  
- Orden post-foundation para M29/M28  

---

## 5. No objetivos

- Crear `web/` o ejecutar `create-next-app` en esta etapa  
- Instalar dependencias Node  
- Configurar Cloudflare, DNS o dominios productivos  
- Implementar M28, M29 ni páginas públicas completas  
- Introducir Prisma, NestJS u ORM paralelo  
- Asignar numeración Mxx a la web  
- Usar Vercel como hosting oficial inicial  
- Duplicar Supabase o Auth web separado  

---

## 6. Fuentes

| # | Documento |
|---|-----------|
| 1 | `docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md` |
| 2 | `docs/01-producto/D01-Modulos-y-Orden-v1.2.md` |
| 3 | `docs/03-modulos/M28-portal-veterinario-y-gestion-profesional-salud.md` |
| 4 | `docs/03-modulos/M28-cierre-implementacion.md` |
| 5 | `docs/03-modulos/M29-brand-studio-y-publicidad.md` |
| 6 | `docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md` |
| 7 | `docs/08-marca/D08-03-Sistema-de-Color-y-UI.md`, `D08-06-Sistema-UX-UI-LeoVer-v1.0.md` |
| 8 | Repo: `settings.gradle.kts`, `supabase/`, `.github/workflows/android-ci.yml` |

---

## 7. Decisiones aprobadas

| Decisión | Valor |
|----------|-------|
| Framework | Next.js + React + TypeScript |
| Router | App Router (preferido; sin evidencia en repo para Pages Router) |
| Backend | Mismo Supabase que Android |
| Auth | Supabase Auth — misma identidad |
| Hosting inicial | Cloudflare Workers |
| Adaptador Next | OpenNext para Cloudflare |
| Repo | Mismo `ComunidadApp` |
| CI/CD | GitHub + Cloudflare Workers Builds / previews |
| Vercel | Solo contingencia futura — **no** hosting oficial inicial |
| Web como Mxx | **No** — superficie transversal |

---

## 8. Ubicación workspace

### 8.1 Auditoría estructura actual

```text
ComunidadApp/
├── app/                 # Android (Gradle :app)
├── gradle/, gradlew*
├── settings.gradle.kts  # include(":app") únicamente
├── supabase/            # Backend compartido
├── docs/
├── scripts/             # CI shell, brand assets (no Node)
├── .github/workflows/   # android-ci.yml
└── firebase.json        # Android push — no web
```

No existe monorepo JS (`package.json`, `pnpm-workspace`, `turbo.json`).

### 8.2 Decisión: ruta oficial futura

**`web/` en la raíz del repositorio**

| Criterio | `web/` | `apps/web/` |
|----------|--------|-------------|
| Coherencia con Gradle single-module | Sí — hermano de `app/` | Implica monorepo apps sin segundo app nativo |
| Claridad CI path filters | `web/**` | `apps/web/**` |
| Convención OpenNext docs | Común | Común en turborepos |
| Simplicidad máquina modesta | Menos anidación | Extra nivel |

**No crear la carpeta en esta tarea.** La spec fija la ruta para ADR/implementación futura.

### 8.3 Convención nombres

- Workspace npm/pnpm: nombre `"leover-web"` en `web/package.json`  
- Root repo **sin** `package.json` inicialmente — evita mezclar lifecycle Gradle/Node en raíz  
- Opcional futuro: `package.json` raíz solo si se adopta turborepo; **no recomendado en Foundation**  

---

## 9. Stack

| Capa | Elección | Notas |
|------|----------|-------|
| Runtime UI | React 19 compatible con Next estable | Verificar matriz OpenNext al implementar |
| Framework | Next.js (App Router) | SSR/SSG/RSC según ruta |
| Lenguaje | TypeScript strict | `strict: true` |
| Estilos | Ver §24 | Tailwind + CSS variables tokens marca |
| Supabase client | `@supabase/supabase-js` + `@supabase/ssr` | Patrón oficial SSR |
| Deploy | `@opennextjs/cloudflare` | Workers |
| Tests | Vitest + Testing Library | Liviano |
| Lint | ESLint flat + `eslint-config-next` | En Foundation |

**Versión Next.js:** usar la **última estable compatible** con OpenNext Cloudflare al momento de `create-next-app` — no fijar número obsoleto en esta spec; documentar versión elegida en `web/README.md` al crear workspace.

---

## 10. Runtime

| Entorno | Runtime |
|---------|---------|
| Cloudflare Workers (prod/preview) | V8 isolate — **sin Node.js completo** |
| `next dev` local | Node.js LTS (solo desarrollo) |
| Supabase Edge Functions | Deno — secretos OpenAI/MP |

**Regla:** toda dependencia npm debe evaluarse contra compatibilidad Workers (§11, §43).

---

## 11. Cloudflare / OpenNext

### 11.1 Pipeline conceptual

```text
GitHub push/PR
    → Cloudflare Workers Builds (conectado al repo)
        → install (pnpm) en web/
        → typecheck + tests focalizados
        → opennextjs-cloudflare build
        → preview (PR/branch) | production (main)
```

### 11.2 Entornos deploy

| Entorno | Trigger | Supabase target |
|---------|---------|-----------------|
| Preview | PR / branch | Staging project |
| Production | merge `main` | Production project (cuando exista) |

### 11.3 Variables Cloudflare (conceptual)

- `NEXT_PUBLIC_SUPABASE_URL`  
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`  
- `NEXT_PUBLIC_APP_URL` (URL Workers preview/prod)  
- Feature flags públicos (non-secret)  
- **Nunca** `SUPABASE_SERVICE_ROLE_KEY` en Workers env expuesto a build cliente  

Secretos server-only (si algún Route Handler los requiere): Cloudflare Secrets — preferir Supabase Edge Functions para operaciones privilegiadas.

### 11.4 Dominios LeoVer (adquiridos — 2026-08)

| Dominio | Rol | Estado deploy |
|---------|-----|---------------|
| **https://leover.com.ar** | **Canónico** — URL pública principal | Adquirido; DNS/deploy pendiente |
| leoverapp.com.ar | Defensivo — redirect permanente futuro → `leover.com.ar` | Adquirido; redirect pendiente |
| leoverapp.com | Reserva internacional — **no** canónico en V1 | Adquirido; sin uso productivo inicial |
| www.leover.com.ar | Alias — redirect futuro → `https://leover.com.ar` | Pendiente Cloudflare |

**Variable de configuración:** `NEXT_PUBLIC_APP_URL=https://leover.com.ar` (pública; ver `web/.env.example`).

**Usos del canónico:**

- `metadataBase`, canonical URLs, Open Graph  
- Callbacks web Supabase Auth cuando corresponda  
- Links públicos compartibles  
- Futuros Android App Links e iOS Universal Links sobre `https://leover.com.ar`  

**Sin implementar en Foundation:** `assetlinks.json`, `apple-app-site-association`, redirects Cloudflare, DNS productivo.

**Routing — una sola app, un solo dominio, paths:**

```text
/  /acceso  /cuenta
/mascota/...  /perdidos/...  /encontrados/...  /adopciones/...
/organizaciones/...  /profesional/...  /marca/...  /admin/...
```

**No crear** subdominios `pro.leover.com.ar`, `brand.leover.com.ar`, `admin.leover.com.ar` salvo decisión explícita futura.

**No** crear segundo workspace, segundo deployment ni aplicaciones separadas por dominio secundario.

---

## 12. Supabase

### 12.1 Principios

- **Un** proyecto Supabase por entorno (staging/prod) — mismo esquema que Android  
- **Sin** segunda base de datos  
- **Sin** Prisma/NestJS  
- Contratos: tablas + RLS + RPC existentes (`m08_*`, `m12_*`, `m28_*`, futuro `m29_*`)  
- Tipos TypeScript: generación `supabase gen types` → `web/lib/supabase/database.types.ts` (artefacto derivado, no fuente de verdad)  

### 12.2 Clientes

| Cliente | Uso | Dónde |
|---------|-----|-------|
| Browser | Lecturas permitidas por RLS, mutaciones vía RPC expuestas | Client Components |
| Server (RSC/Route Handler) | Sesión cookie, fetch inicial, SSR público sanitizado | Server Components |
| Edge Function | Secretos, webhooks, OpenAI, Mercado Pago | `supabase/functions/` |

### 12.3 Service role

**NUNCA** en navegador, bundle cliente ni `NEXT_PUBLIC_*`.  
Operaciones privilegiadas → Edge Functions o RPC `SECURITY DEFINER` con checks — patrón ya usado en Android (`SupabaseM28RemoteDataSource`, etc.).

---

## 13. Auth

### 13.1 Identidad única

Misma cuenta LeoVer que Android/iOS:

- Email/password (y métodos Supabase habilitados)  
- Username público (M02)  
- Sesión JWT Supabase  

### 13.2 Patrón Next.js + Supabase (App Router)

```text
Middleware (web/middleware.ts)
  → refresh session cookie (@supabase/ssr)
  → redirect rutas protegidas

Server Components
  → createServerClient(cookies) → getUser()

Client Components
  → createBrowserClient() → onAuthStateChange

Route /acceso/callback
  → exchange code PKCE / magic link
```

### 13.3 Flujos

| Flujo | Ruta conceptual |
|-------|-----------------|
| Login | `/acceso/iniciar-sesion` |
| Registro | `/acceso/registro` |
| Recuperar | `/acceso/recuperar` |
| Callback OAuth/email | `/acceso/callback` |
| Logout | Server action o Route Handler que limpia cookies |

### 13.4 Cloudflare compat

- Usar `@supabase/ssr` cookie handlers compatibles con Edge middleware  
- Evitar APIs Node-only en middleware (`fs`, `crypto` Node legacy) — Web Crypto API  
- Probar refresh token en preview Workers antes de production  

---

## 14. Autorización

**UI no es seguridad.** Ocultar nav ≠ autorizado.

| Capa | Autoridad |
|------|-----------|
| PostgreSQL RLS | Lecturas/mutaciones directas |
| RPC `SECURITY DEFINER` | Operaciones atómicas M28/M29/M04 |
| Permisos M02/M03 | `has_org_permission`, roles plataforma |
| Middleware web | Solo gating UX (sesión presente, rutas `/admin`) |

Portal org: verificar membresía M03 vía RPC/server antes de renderizar shell.  
Brand Studio: entitlement M29 + permisos `brand_studio.*`.  
Admin: permisos plataforma M04 — doble verificación server-side.

**No** replicar RBAC completo solo en frontend.

---

## 15. RLS / RPC

- Preferir **RPC** para: crear campaña, moderar, activar entitlement, export M28, mutaciones multi-tabla  
- Preferir **lectura directa** (server client) solo donde RLS sea suficiente y proyección esté acotada  
- Reutilizar error mappers conceptuales al estilo Android (`M28PetErrorMapper`, `M14ErrorMapper`) en `web/lib/errors/`  
- Idempotencia: `client_request_id` en formularios críticos (M28/M29)  

Android RemoteDataSources (`app/.../remote/supabase/mXX/`) son **referencia de contratos**, no código compartido.

---

## 16. Edge Functions

Usar Supabase Edge Functions cuando:

- OpenAI (M29 copy/imagen)  
- Mercado Pago webhooks/suscripciones  
- Webhooks externos M27  
- Cualquier `service_role` o secreto de terceros  

Next.js Route Handlers / Server Actions:

- Orquestación BFF **no privilegiada**  
- Validación formulario, redirecciones, agregación de respuestas RPC  
- **No** duplicar lógica de negocio que ya vive en RPC SQL  

---

## 17. Routing

Mapa conceptual **una app** — paths en español LeoVer, separación áreas:

### 17.1 Público (sin sesión o sesión opcional)

| Path | Propósito | Módulos |
|------|-----------|---------|
| `/` | Landing / explorar entrada | Transversal |
| `/explorar` | Descubrimiento público | M19 slice |
| `/mascota/[id]` | Ficha pública sanitizada | M08, M14 público |
| `/perdido/[id]` | Alerta perdido compartible | M13, legacy |
| `/encontrado/[id]` | Avistamiento/encontrado | M13 |
| `/adopcion/[id]` | Publicación adopción | M09 |
| `/organizacion/[slug]` | Perfil org público | M03, M16 |
| `/profesional/veterinaria/[slug]` | Directorio M12 público | M12 |
| `/evento/[id]` | Evento comunitario público | M18 |

### 17.2 Auth

| Path | Propósito |
|------|-----------|
| `/acceso/iniciar-sesion` | Login |
| `/acceso/registro` | Alta |
| `/acceso/recuperar` | Reset password |
| `/acceso/callback` | Supabase auth callback |

### 17.3 Cuenta usuario (sesión)

| Path | Propósito |
|------|-----------|
| `/cuenta` | Perfil / preferencias web |
| `/cuenta/notificaciones` | Preferencias M06 slice |

### 17.4 Portal organización

| Path | Propósito |
|------|-----------|
| `/org/[orgId]` | Dashboard org |
| `/org/[orgId]/equipo` | M03 equipos |
| `/org/[orgId]/...` | Módulos según permisos (M09, M16, M17…) |

### 17.5 Portal profesional M28

| Path | Propósito |
|------|-----------|
| `/profesional` | Selector clínica/contexto |
| `/profesional/veterinaria/[clinicId]` | Dashboard M28 |
| `/profesional/veterinaria/[clinicId]/agenda` | M12 appointments |
| `/profesional/veterinaria/[clinicId]/pacientes` | M28 pacientes autorizados |
| `/profesional/veterinaria/[clinicId]/atenciones` | M28 care |
| `/profesional/veterinaria/[clinicId]/propuestas` | Propuestas M14 |

### 17.6 Brand Studio M29

| Path | Propósito |
|------|-----------|
| `/marca` | Dashboard anunciante |
| `/marca/campanas` | Listado |
| `/marca/campanas/nueva` | Wizard 3 pasos |
| `/marca/campanas/[id]` | Detalle / analytics |
| `/marca/creativos` | Biblioteca |
| `/marca/suscripcion` | Entitlement |

### 17.7 Admin

| Path | Propósito |
|------|-----------|
| `/admin` | Dashboard M04 |
| `/admin/moderacion` | Colas |
| `/admin/campanas` | Moderación M29 |
| `/admin/organizaciones` | Verificación org |

**Regla:** no mezclar layouts públicos con shells autenticados en la misma ruta padre sin route groups.

---

## 18. Layouts (Route Groups)

Estructura App Router recomendada:

```text
web/app/
├── (public)/           # marketing + páginas compartibles
│   layout.tsx          # header/footer público, mobile-first
├── (auth)/acceso/      # layout minimal, sin nav app
├── (account)/cuenta/   # shell usuario logueado liviano
├── (org)/org/[orgId]/ # sidebar org, permisos M03
├── (professional)/profesional/...
├── (brand)/marca/      # shell Brand Studio desktop-first
├── (admin)/admin/      # shell admin reforzado
├── layout.tsx          # root: providers, fonts, metadata base
└── not-found.tsx, error.tsx, global-error.tsx
```

Evitar un único `layout.tsx` con `if (isAdmin) ... else if (isBrand) ...`.

---

## 19. Web pública

Fundamental para compartir sin instalar app (Maestro §6.1).

### 19.1 Proyección sanitizada

Exponer solo campos públicos vía RPC/vistas dedicadas (patrón M14 public projection, M13 sanitización).

**No exponer:** teléfono, dirección exacta, coordenadas precisas, documentos clínicos M28, chats, datos Pasaporte privados.

### 19.2 Urgencias / perdidos

- Zona aproximada en mapa — no pin exacto de domicilio  
- `robots`/meta evaluados por tipo página  
- Contenido URGENT priorizado en apps nativas; web pública informativa sin filtrar coordenadas  

---

## 20. Portal organización

- Reutiliza M03 membresías, invitaciones, sucursales  
- Web complementa Android M11/M16 donde operadores prefieren desktop  
- Misma org puede activar Brand Studio (M29) y portal vet (M28) sin duplicar org  

---

## 21. Portal profesional M28

- Área `(professional)` — consume M12 + M28 + M14 RPC existentes  
- No duplicar backend (spec M28 + cierre 080)  
- PDF export: generación vía RPC snapshot + render en **Edge Function** o descarga cliente según evaluación runtime Workers (§31)  
- Android responsable sigue resolviendo propuestas; web profesional es superficie principal post-Foundation  

---

## 22. Brand Studio M29

- Área `(brand)` — wizard, creativos, IA vía Edge Functions, moderación  
- Feature flag `brand_studio_enabled` antes de rutas productivas  
- No implementar en Foundation — solo reservar estructura `features/brand/`  

---

## 23. Admin

- Área `(admin)` — M04 moderación, M07 auditoría  
- Mismo design system — no SPA admin separada  
- Controles adicionales: IP allowlist futuro (decisión abierta), sesión corta, MFA futuro  

---

## 24. Design system

### 24.1 Estrategia recomendada: **Tailwind CSS + CSS variables**

| Opción | Veredicto |
|--------|-----------|
| Tailwind + tokens CSS | **Recomendado** — rápido, responsive, compatible Workers, mapeo directo D08-03 |
| CSS Modules solo | Viable pero más lento para portales densos |
| MUI/Chakra | Descartado — bundle pesado, estilo difícil alinear con LeoVer |

### 24.2 Tokens marca (fuente D08-03)

Implementar en `web/app/globals.css`:

```css
:root {
  --brand-orange: #FF7A00;
  --brand-orange-soft: #FFA64D;
  --brand-green: #49B749;
  --brand-green-dark: #247A3D;
  --brand-cream: #FFF6EA;
  --brand-text: #2F3A37;
  --brand-white: #FFFFFF;
}
```

Tipografía: alinear con `D08-06` — webfont única (sin duplicar arbitrary fonts Android).

Componentes base en `components/ui/` — botones, inputs, cards, badges (`Patrocinado`, `Verificado` ≠ pago).

---

## 25. Componentes

```text
web/
├── components/
│   ├── ui/              # Button, Input, Card, Dialog — sin lógica dominio
│   └── layout/          # Header, Sidebar, Footer
├── features/
│   ├── public/          # páginas compartibles
│   ├── auth/
│   ├── org/
│   ├── professional/    # M28
│   ├── brand/           # M29
│   └── admin/
├── lib/
│   ├── supabase/        # clients, types
│   ├── rpc/             # wrappers tipados
│   ├── auth/
│   └── flags/
├── types/
└── public/              # estáticos, favicon, og-default.png
```

**Regla:** dominio vive en `features/*`, no en `components/ui`.

---

## 26. Estado (state management)

**No Redux global.**

| Tipo | Herramienta |
|------|-------------|
| Server state | RSC + fetch / React `cache()` |
| URL state | `searchParams`, pathname |
| Formularios | React `useActionState` / controlled + server actions |
| Cliente puntual | `useState`/`useReducer` local |
| Auth session | Supabase client + context mínimo si necesario |

Introducir TanStack Query solo si un portal muestra polling intensivo — evaluar en M28 agenda, no en Foundation.

---

## 27. Data fetching

| Caso | Patrón |
|------|--------|
| Página pública SEO | Server Component + RPC/read RLS + `generateMetadata` |
| Portal autenticado | Server Component inicial + Client hydration selectiva |
| Mutaciones críticas | RPC vía Server Action o `supabase.rpc()` client-side solo si RLS lo permite |
| Realtime | Supabase realtime solo donde ya exista patrón (M20 futuro web) — no en Foundation |
| Analytics events | Fire-and-forget batch — no bloquear UI |

**No** hacer todo client-side. **No** SSR innecesario en dashboards altamente interactivos si no aporta SEO.

---

## 28. Formularios

Patrón común `features/*/components/forms/`:

- Validación schema (Zod) compartida client/server  
- Estados: idle, loading, success, error  
- Idempotency key en submit (M28 care, M29 campaña)  
- Disable double-submit  
- Errores mapeados a códigos dominio (`M29_AI_QUOTA_EXCEEDED`, etc.)  
- Mensajes usuario en español — sin stack traces  

---

## 29. Storage / uploads

Reutilizar **Supabase Storage M05**:

- Upload browser → signed URL o policy RLS bucket existente  
- Tipos MIME allowlist por feature  
- Límites tamaño alineados Android  
- Progreso UI — Client Component  
- Documentos sensibles M28: buckets privados, URL firmada corta, no public CDN  

No S3 paralelo ni uploads a Cloudflare R2 salvo ADR futuro.

---

## 30. Imágenes

| Tipo | Estrategia |
|------|------------|
| Públicas marketing | `public/` o Supabase public bucket + `<img>` / loader custom |
| Privadas | Signed URL Supabase |
| Creativos M29 | Storage M05 + metadata campaña |

**Next/Image:** verificar soporte OpenNext Cloudflare — si `next/image` optimization no está disponible en Workers, usar:

- Loader custom hacia Supabase transform (si habilitado) o  
- `<img>` con dimensiones + lazy loading  

Documentar decisión en Foundation README tras smoke build.

---

## 31. PDF / export

M28 requiere export PDF (spec + Android `M28ExportPdfGenerator`).

Opciones compatibles Cloudflare:

1. **Preferida:** RPC `m28_get_export_snapshot` → Edge Function genera PDF (lib compatible Deno/Workers) → download  
2. **Alternativa:** snapshot JSON → generación PDF en cliente (pdf-lib) solo para export no clínico masivo  
3. **Evitar:** puppeteer/playwright headless en Workers  

Decisión final en implementación M28 web — Foundation solo deja hook `features/professional/export/`.

---

## 32. SEO / social sharing

Páginas `(public)` implementan:

- `generateMetadata` — title, description, canonical  
- Open Graph + Twitter cards  
- Imagen OG por tipo (mascota, perdido, adopción) — default `public/og-default.png`  
- `sitemap.xml` route handler — solo rutas públicas indexables  
- `robots.txt` — disallow `/admin`, `/marca`, `/profesional`, `/org` privados  
- Datos sensibles: `noindex` o no publicar ruta  

---

## 33. Privacidad

- Minimización datos en Server Components públicos  
- No incluir PII en HTML inicial perdidos/adopciones  
- Analytics sin trackers third-party publicitarios  
- Separar product analytics vs advertising analytics (M29)  
- Consentimiento cookies si aplica jurisdicción — banner futuro (decisión legal abierta)  
- Territorio piloto San Vicente + Almirante Brown vía **config backend** — no hardcode permanente en web  

---

## 34. Seguridad

- CSP headers en `next.config` / Cloudflare  
- HTTPS only  
- Cookies Supabase: `Secure`, `HttpOnly`, `SameSite=Lax`  
- CSRF: Server Actions Next + SameSite  
- Rate limit: Cloudflare rules + RPC throttling existente  
- Dependabot/Renovate recomendado  
- Guard CI: grep `service_role` en `web/` → falla build  

---

## 35. Logging

**No loguear:**

- Tokens JWT completos  
- Passwords  
- Cookies sesión  
- Documentos clínicos íntegros  
- Direcciones exactas  
- Prompts IA completos (M29) — preferir hash + metadata  

Sanitización en Route Handlers antes de `console.error` — producción enviar a observabilidad M07 slice futuro.

---

## 36. Observabilidad

Mínimo Foundation + evolución:

| Señal | Mecanismo |
|-------|-----------|
| Errores client | boundary + optional Sentry/Cloudflare Logpush (decisión abierta) |
| Errores server | structured log + `request_id` |
| Correlación | header `x-request-id` propagado a RPC metadata cuando exista |
| M07 eventos | Reutilizar claves catalogo — `platform: web` |

Sin registrar PII en eventos.

---

## 37. Analytics

- **Product analytics:** eventos first-party → M07 pipeline (no Google Analytics por defecto)  
- **Advertising analytics M29:** agregados campaña — ver spec M29  
- **Audit M04/M07:** acciones admin — backend authoritative  

---

## 38. Performance

Objetivos cualitativos (sin baseline numérico inventado):

- Mobile-first en público — JS inicial reducido  
- Code splitting por route group  
- Lazy load features `(brand)`, `(professional)`  
- No bloquear feed nativo Android con publicidad web  
- Evitar waterfalls: parallel fetch en RSC donde sea seguro  
- Prefetch Next `Link` en nav portales  

---

## 39. Accesibilidad

- WCAG 2.2 buenas prácticas (no certificación en esta spec)  
- Focus visible, labels, roles ARIA en componentes `ui/`  
- Contraste D08-03 (naranja + texto `#2F3A37`)  
- Alt text obligatorio en creativos  
- Subtítulos en reels públicos cuando haya video  
- Label **Patrocinado** accesible — no solo color  

---

## 40. Responsive

| Área | Prioridad |
|------|-----------|
| `(public)` | Mobile-first |
| `(auth)` | Mobile-first |
| `(brand)`, `(professional)` | Desktop-first operacional, tablet usable |
| `(admin)` | Desktop-first |

Breakpoints Tailwind estándar — sin duplicar sistema Android dp.

---

## 41. Feature flags

Integrar con patrón existente (`AppConfig` / M07 `feature_flag` en eventos).

Flags web (env + backend config):

| Flag | Efecto |
|------|--------|
| `web_public_enabled` | Rutas `(public)` |
| `web_org_portal_enabled` | `/org/*` |
| `web_m28_portal_enabled` | `/profesional/veterinaria/*` |
| `web_m29_brand_studio_enabled` | `/marca/*` |
| `web_admin_enabled` | `/admin/*` |

**No desactivables por flag:** RLS, sanitización pública, deny-by-default, prohibición service_role cliente.

---

## 42. Testing

Estrategia liviana (máquina modesta):

| Nivel | Cuándo |
|-------|--------|
| `tsc --noEmit` | Cada PR |
| Vitest unit (`lib/`, validators) | PR |
| Component tests críticos (`ui/`, auth) | PR selectivo |
| Playwright E2E | Solo smoke: login + ruta pública + ruta protegida — manual nightly o pre-release |
| OpenNext build | `main` + pre-merge |

**No** duplicar `testDebugUnitTest` Android en cada commit web.

---

## 43. CI/CD

### 43.1 Workflow GitHub (futuro)

Archivo propuesto: `.github/workflows/web-ci.yml`

```yaml
on:
  pull_request:
    paths: ['web/**', 'supabase/**']
  push:
    branches: [main]
    paths: ['web/**']
```

Jobs:

1. `pnpm install --frozen-lockfile`  
2. `pnpm typecheck`  
3. `pnpm test` (unit focalizado)  
4. `pnpm build` (opennext cloudflare build)  

Android CI **no se ejecuta** en cambios solo-`web/` (path filters).

### 43.2 Cloudflare Workers Builds

- Conectar repo → root directory `web`  
- Preview en PR  
- Production en `main`  

---

## 44. Dependencias

- Lockfile **`pnpm-lock.yaml` obligatorio**  
- Revisar licencias (MIT/Apache preferidos)  
- Evitar paquetes abandonados  
- Política compatibilidad Workers antes de agregar dependencia:

```text
Checklist nueva dependencia:
[ ] ¿Usa fs/net/dns/node:crypto legacy?
[ ] ¿Binarios nativos?
[ ] ¿Funciona en edge runtime o solo en Node dev?
[ ] ¿Tamaño bundle > 50kb gzip justificado?
```

Script futuro opcional: `scripts/ci/web_edge_compat_check.sh`.

---

## 45. Contratos compartidos

Android, iOS y Web **comparten backend**, no UI.

| Mecanismo | Rol |
|-----------|-----|
| PostgreSQL schema + migrations | Fuente de verdad |
| RPC names + params | Contrato API |
| Docs Mxx + cierre | Semántica |
| `supabase gen types typescript` | Tipos derivados web |
| Tests SQL staging scripts | Validación contrato |

**No** introducir KMP ni codegen complejo cross-platform en Foundation.

Opcional: job CI regenera tipos si migración cambia — diff review.

---

## 46. Árbol futuro (workspace `web/`)

```text
web/
├── README.md
├── package.json
├── pnpm-lock.yaml
├── tsconfig.json
├── next.config.ts
├── open-next.config.ts
├── wrangler.toml                 # Cloudflare
├── middleware.ts
├── app/
│   ├── layout.tsx
│   ├── globals.css
│   ├── (public)/
│   ├── (auth)/acceso/
│   ├── (account)/cuenta/
│   ├── (org)/org/[orgId]/
│   ├── (professional)/profesional/
│   ├── (brand)/marca/
│   └── (admin)/admin/
├── components/
│   ├── ui/
│   └── layout/
├── features/
│   ├── public/
│   ├── auth/
│   ├── org/
│   ├── professional/
│   ├── brand/
│   └── admin/
├── lib/
│   ├── supabase/
│   │   ├── client.ts
│   │   ├── server.ts
│   │   ├── middleware.ts
│   │   └── database.types.ts     # generated
│   ├── rpc/
│   ├── auth/
│   ├── flags/
│   └── errors/
├── types/
├── public/
└── tests/
    ├── unit/
    └── e2e/                      # opcional, pocos tests
```

Repo root sin cambios Gradle. `supabase/` permanece en raíz compartida.

---

## 47. Web Foundation Minimum

Bloque **pequeño** siguiente — criterio “fundación creada”:

| # | Entregable |
|---|------------|
| 1 | Carpeta `web/` con Next.js App Router + TypeScript |
| 2 | pnpm + lockfile |
| 3 | Tailwind + tokens D08-03 en `globals.css` |
| 4 | Route groups: `(public)`, `(auth)`, `(account)` shells vacíos |
| 5 | Supabase browser + server + middleware session refresh |
| 6 | Páginas: `/` pública placeholder, `/acceso/iniciar-sesion`, `/cuenta` protegida |
| 7 | Auth completo: login, logout, callback, sesión persistente |
| 8 | Autorización UX: redirect si no autenticado en `/cuenta` |
| 9 | RPC smoke: una llamada read-only demostrando RLS (ej. perfil propio) |
| 10 | `error.tsx`, `not-found.tsx`, loading states |
| 11 | Responsive shell header/footer público |
| 12 | OpenNext + wrangler config + build local exitoso |
| 13 | `.github/workflows/web-ci.yml` typecheck + test + build |
| 14 | Cloudflare Workers Builds preview conectado |
| 15 | `web/README.md`: env vars, dev, deploy, compat Workers |
| 16 | CI guard: no `service_role` en fuentes web |
| 17 | Placeholder routes reservadas `(brand)`, `(professional)`, `(admin)` — 404 controlado “próximamente” |

**Explícitamente fuera Foundation:** M28 funcional, M29 wizard, páginas públicas completas, admin, OpenAI, Mercado Pago.

---

## 48. Orden de implementación post-Foundation

```text
1. Web Foundation Minimum          ← desbloquea todo
2. Web pública slice               ← /mascota, /perdido, /adopcion (R2 Maestro)
3. M29 Brand Studio Pilot-Minimum  ← producto 100% web; desbloqueado por M28-cierre
4. M28 Portal Profesional Web      ← agenda, pacientes, atenciones (RPC 080 existente)
5. Portal org ampliado + Admin web ← progresivo según M04/M03 prioridad
```

### Justificación secuencia M29 antes M28 web

| Factor | M29 primero | M28 web primero |
|--------|-------------|-----------------|
| Dependencia web | Total — no hay Android Brand Studio | Android responsable + care mínimo ya existe |
| Alcance piloto | Wizard + moderación acotada | Portal clínico multi-módulo |
| Desbloqueo comercial | R5B Brand Studio | R5 portal vet |
| Riesgo clínico | Bajo (publicidad) | Mayor (datos salud UI) |

Si producto prioriza operación clínica sobre ingresos publicitarios, puede invertirse 3↔4 mediante ADR — Foundation es prerrequisito común.

---

## 49. Riesgos

| Riesgo | Mitigación |
|--------|------------|
| OpenNext incompatibilidad dependencia | Checklist §44; smoke build temprano |
| Auth cookies en Workers | Probar middleware en preview CF |
| Duplicar lógica RPC en Server Actions | Code review — autoridad SQL |
| Bundle size portales | Route groups + lazy features |
| SEO filtra datos sensibles | RPC públicos dedicados + noindex |
| CI lento en máquina modesta | Path filters; sin E2E en cada commit |
| Confusión `app/` Android vs `web/app` | Documentación + nombres claros en README |
| Dos feeds sociales (`posts` vs M19) | ADR placement M29 — Inicio Android vs web público |

---

## 50. Decisiones abiertas

| ID | Tema | Estado |
|----|------|--------|
| DEC-WEB-02 | Proveedor observabilidad frontend (Sentry vs CF Logpush) | ABIERTO |
| DEC-WEB-03 | Librería charts portal M29/M28 | ABIERTO post-pilot |
| DEC-WEB-05 | PWA instalable | **NO** obligatoria V1 — evaluar post-pilot |
| DEC-WEB-06 | i18n más allá de español | Arquitectura preparada, no implementar |

### Cerradas (no reabrir)

Next.js, React, TS, Supabase, Cloudflare Workers, OpenNext, GitHub CI, identidad auth única, RLS autoridad, no service_role cliente.

| ID | Decisión cerrada | Valor |
|----|------------------|-------|
| DEC-WEB-01 | Dominio canónico | **https://leover.com.ar** |
| DEC-WEB-01b | Dominios defensivos | leoverapp.com.ar, leoverapp.com (redirect/reserva; no segundo deploy) |
| DEC-WEB-04 | Imágenes en Workers | `<img>` + `images.unoptimized: true` (Foundation) |
| DEC-WEB-07 | Routing | Un dominio + paths; sin subdominios pro/brand/admin en V1 |

---

## 51. Definition of Done — Web Foundation

Foundation se considerará completada cuando:

- [x] Existe `web/` en ruta oficial con build OpenNext exitoso  
- [x] Login/logout Supabase integrado (preview Cloudflare validable localmente)  
- [x] Ruta pública + ruta protegida demostrables  
- [x] Middleware refresh sesión operativo  
- [x] No secretos service role en bundle (CI guard)  
- [x] Typecheck + tests unit mínimos + web CI definido  
- [x] README técnico completo  
- [x] Route groups reservan M28/M29/admin sin implementarlos  
- [x] Tokens marca LeoVer aplicados en shell  
- [x] Android CI no regresiona (path filters)  
- [x] Dominio canónico `leover.com.ar` documentado (`NEXT_PUBLIC_APP_URL`)  

---

## 51.1 Web pública compartible (slice 1 — 2026-08-10)

Estado: **IMPLEMENTADA** en `web/` + migración `081_web_public_shareable_pages.sql`.

| Ruta | Dominio | Contrato backend |
|------|---------|------------------|
| `/mascota/[publicCode]` | M14 Pasaporte | `get_public_pet(text)` → `m14_get_public_pet_passport` |
| `/perdidos/[publicCode]` | lost_found + M13 zona | `get_public_lost_case(text)` |
| `/encontrados/[publicCode]` | lost_found + M13 zona | `get_public_found_case(text)` |
| `/adopciones/[publicCode]` | M09 adopciones | `get_public_adoption(text)` |

Identificador público: `public_code` (`PUB-*`) en adopciones y casos perdidos/encontrados; mascota reutiliza `pet_passports.public_code` (M14).

Sanitización en RPC SECURITY DEFINER (sin service role, sin SELECT * en React). Cierre: `LeoVer-Web-Publica-Compartible-cierre-v1.0.md`.

---

## 52. Historial

| Versión | Fecha | Cambios |
|---------|-------|---------|
| 1.0 | 2026-08-09 | Especificación inicial arquitectura web única LeoVer |
| 1.0.1 | 2026-08-09 | Foundation implementada en `web/`; dominios adquiridos (canónico leover.com.ar); decisiones DEC-WEB-01/04/07 cerradas |

### Decisiones técnicas Foundation (implementación real)

| Tema | Valor |
|------|-------|
| Workspace | `web/` |
| Package manager | pnpm 10.12.4 |
| Next.js | 15.5.12 |
| React | 19.1.0 |
| @opennextjs/cloudflare | ^1.20.2 |
| wrangler | ^4.86.0 |
| @supabase/ssr | ^0.6.1 |
| Auth | `@supabase/ssr` + middleware refresh |
| Imágenes | `<img>` nativo; `images.unoptimized: true` |
| Dominio canónico | `NEXT_PUBLIC_APP_URL` → https://leover.com.ar |
| CI | `.github/workflows/web-ci.yml` |

---

**Próximo paso recomendado:** Web pública slice (`/mascota`, `/perdido`, `/adopcion`) y luego M29 Pilot-Minimum (§48).
