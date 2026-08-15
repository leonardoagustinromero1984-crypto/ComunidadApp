-- LeoVer Canonical Baseline
-- Logical migration: 1006
-- M05 canonical media. Persist bucket + object_path. Never persist signed URL.

create table public.media_assets (
  id uuid primary key default gen_random_uuid(),
  bucket text not null check (bucket in (
    'public-media', 'private-media', 'documents', 'moderation-evidence'
  )),
  object_path text not null,
  mime_type text not null,
  byte_size bigint not null check (byte_size >= 0),
  owner_kind text not null check (owner_kind in ('PERSON', 'ORGANIZATION', 'PLATFORM')),
  owner_person_id uuid null references public.persons(user_id),
  owner_organization_id uuid null references public.organizations(id),
  visibility text not null default 'PRIVATE'
    check (visibility in ('PRIVATE', 'PUBLIC')),
  lifecycle_status text not null default 'DRAFT'
    check (lifecycle_status in (
      'DRAFT', 'UPLOADING', 'READY', 'ARCHIVED', 'REJECTED', 'QUARANTINED'
    )),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default timezone('utc', now()),
  archived_at timestamptz null,
  metadata jsonb not null default '{}'::jsonb,
  constraint media_assets_no_signed_url check (
    metadata ? 'signed_url' is not true
    and metadata ? 'signedUrl' is not true
  ),
  constraint media_assets_owner_xor check (
    owner_kind = 'PLATFORM'
    or public.holder_xor_ok(owner_kind, owner_person_id, owner_organization_id)
  ),
  unique (bucket, object_path)
);

create table public.media_asset_versions (
  id uuid primary key default gen_random_uuid(),
  asset_id uuid not null references public.media_assets(id) on delete cascade,
  object_path text not null,
  byte_size bigint not null,
  created_at timestamptz not null default timezone('utc', now())
);

create table public.media_asset_links (
  id uuid primary key default gen_random_uuid(),
  asset_id uuid not null references public.media_assets(id) on delete cascade,
  owner_table text not null,
  owner_id uuid not null,
  purpose text not null,
  created_at timestamptz not null default timezone('utc', now())
);

create index media_asset_links_asset_idx on public.media_asset_links (asset_id);
create index media_asset_links_owner_idx on public.media_asset_links (owner_table, owner_id);

alter table public.persons
  add constraint persons_avatar_asset_fk
  foreign key (avatar_asset_id) references public.media_assets(id);

alter table public.organizations
  add constraint organizations_logo_asset_fk
  foreign key (logo_asset_id) references public.media_assets(id);

alter table public.organizations
  add constraint organizations_cover_asset_fk
  foreign key (cover_asset_id) references public.media_assets(id);
