# M19 Bloque 2 — Auditoría

## Alcance auditado

Persistencia remota Supabase para publicaciones, comentarios y reacciones M19.

## Migración 060

| Aspecto | Estado |
|---------|--------|
| Archivo | `supabase/migrations/060_m19_social_posts_and_engagement.sql` |
| Aplicada | **No** |
| Permisos | `social.view`, `social.manage` insertados |
| Tablas | `m19_social_posts`, `m19_post_comments`, `m19_post_reactions` |
| RLS | Deny direct table mutation; RPC SECURITY DEFINER |
| RPC públicos | `m19_list_public_feed`, `m19_get_public_post`, etc. |
| RPC gestión | `m19_create_post`, `m19_transition_post`, etc. |

## Kotlin remoto

- `SupabaseM19RemoteDataSource` — mapeadores JSON seguros
- `SupabaseM19SocialRepository` — implementa contrato Bloque 1
- `DataProvider` selecciona mock vs Supabase

## Seguridad

- JSON público sin `user_id`, email ni teléfono
- `auth.uid()` en RPCs de interacción
- Permisos vía `has_org_permission` (M03), no roles paralelos

## Excluido

- Aplicación SQL en entorno
- M20 mensajería
- Cola moderación M04 duplicada

## Veredicto

```text
M19 BLOQUE 2 PERSISTENCIA DEFINIDA
MIGRACIÓN 060 CREADA NO APLICADA
MOCK CONSERVADO COMO FALLBACK
```
