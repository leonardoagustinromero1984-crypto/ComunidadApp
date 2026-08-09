-- =============================================================================
-- LeoVer RC1.2 — migración 078: Reels / Historias sobre public.posts
-- Forward-only, no destructiva. LOCAL / staging — no aplicar en producción
-- sin autorización explícita.
-- =============================================================================

begin;

alter table public.posts
  add column if not exists pet_id uuid null references public.pets (id) on delete set null;

alter table public.posts
  add column if not exists expires_at timestamptz null;

comment on column public.posts.expires_at is
  'Vencimiento de contenido efímero (historias). Null = sin vencimiento.';

comment on column public.posts.pet_id is
  'Mascota asociada opcional a la publicación social.';

-- Tipos sociales adicionales viven en la columna text "type"
-- (GENERAL, QUESTION, PROMO, ADOPTION, LOST_FOUND, URGENT, REEL, STORY).
-- Sin CHECK restrictivo previo: no se altera el constraint.

create index if not exists posts_type_created_at_idx
  on public.posts (type, created_at desc);

create index if not exists posts_active_stories_idx
  on public.posts (type, expires_at)
  where type = 'STORY';

create index if not exists posts_reels_idx
  on public.posts (created_at desc)
  where type = 'REEL';

commit;
