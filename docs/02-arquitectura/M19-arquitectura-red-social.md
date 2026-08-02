# M19 — Arquitectura red social y contenido

## Límites

M19 modela **publicaciones sociales de organizaciones** autorizadas M03. No reemplaza M03 (organizaciones), M04 (moderación) ni M20 (mensajería).

## Relación con organizaciones

```text
Organization (M03) 1 — * M19Post
Tipos elegibles: SHELTER, RESCUE_GROUP, NGO, TRAINING_CENTER, VETERINARY_CLINIC
```

Una publicación **nunca** crea organización ni usuario.

## Separación post / comentario / reacción

```text
M19Post
  ├── M19Comment (interno: userId, authorDisplayName)
  └── M19Reaction (interno: userId, reactionType)
```

Bloque 1: mock local en `M19SocialMemoryStore`. **Bloque 2:** persistencia Supabase vía RPC.

## Capas

```text
UI (M19SocialScreens)
  → ViewModels (M19SocialViewModels)
  → M19SocialRepository (interface)
  → MockM19SocialRepository | SupabaseM19SocialRepository
  → M19SocialMemoryStore (mock) | SupabaseM19RemoteDataSource (RPC)
  → M19SocialValidators / M19EngagementCalculator
  → M19PrivacySanitizer → M19PublicPost
```

## Modelo interno vs público

| Campo | Interno (`M19Post`) | Público (`M19PublicPost`) |
|-------|---------------------|---------------------------|
| organizationId | Sí | No |
| authorUserId | Sí | No |
| createdBy | Sí | No |
| moderationStatus | Sí | No |
| título, contenido | Sí | Sí (sanitizado) |
| contadores agregados | Calculado | Sí |

Toda lectura pública pasa por `M19PrivacySanitizer.toPublicPost()`.

## Transiciones de estado

```text
DRAFT → PUBLISHED ↔ HIDDEN
DRAFT|PUBLISHED|HIDDEN → REMOVED (terminal)
REMOVED → (sin reapertura)
```

## Reportes M04

`M19SocialModerationAdapter` envía a cola M04 con `otherDescription`:
- `M19_SOCIAL_POST`
- `M19_SOCIAL_COMMENT`
- `M19_SOCIAL_POST_IMAGE`

## Permisos

Mock: `MockM19SocialAuthorityPolicy` + `organizationManagers`.

Producción (B2): `social.view` / `social.manage` vía M03 membership + RPC `has_org_permission`.

## Rutas

| Ruta | Pantalla |
|------|----------|
| `m19/feed` | Feed público |
| `m19/posts/{postId}` | Detalle + comentarios + reacciones |
| `m19/posts/manage` | Panel organizador |
| `m19/posts/create` | Nueva publicación |
| `m19/posts/{postId}/edit` | Edición |

Entrada principal: **Comunidad → Red social (M19)**. M18 eventos permanece en Sumate → Eventos.

## Bloque 3

- `M19FeedService` — paginación cursor `publishedAt|postId`
- `M19ContentReferenceResolver` — snapshots M08/M16/M17/M18
- `M19SocialResilience` — PartialData ante fallos parciales

## Bloque 4 (cierre)

- Migración **061** — feed paginado RPC, ARCHIVED, LOVE, visibility, JSON references/media, comment edit/archive
- `SupabaseM19SocialRepository.searchFeedPage` → `m19_list_public_feed_page`
- Validación staging: 105/105 PASS
- **M19 cerrado oficialmente** 2026-08-02
