-- M05 Etapa 3 — Persistencia, Storage, RLS y RPC de archivos para LeoVer.
-- El bucket "leover" queda como legado de solo lectura; no admite escrituras nuevas.
-- PENDIENTE DE VALIDACIÓN REMOTA contra el proyecto Supabase antes de producción.
-- Esta etapa no implementa antivirus ni extracción/eliminación de metadatos EXIF.

-- =============================================================================
-- 1) Storage: endurecimiento legacy y buckets físicos M05
-- =============================================================================

-- Legacy read-only: se preservan objetos existentes y su lectura pública.
drop policy if exists leover_public_read on storage.objects;
create policy leover_public_read
    on storage.objects for select
    using (bucket_id = 'leover');

drop policy if exists leover_authenticated_upload on storage.objects;
drop policy if exists leover_authenticated_update on storage.objects;
drop policy if exists leover_authenticated_delete on storage.objects;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    (
        'public-media', 'public-media', true, 8388608,
        array['image/jpeg', 'image/png', 'image/webp']
    ),
    (
        'organization-documents', 'organization-documents', false, 15728640,
        array['application/pdf', 'image/jpeg', 'image/png', 'image/webp']
    ),
    (
        'moderation-evidence', 'moderation-evidence', false, 15728640,
        array['application/pdf', 'image/jpeg', 'image/png', 'image/webp']
    ),
    (
        'support-attachments', 'support-attachments', false, 15728640,
        array['application/pdf', 'image/jpeg', 'image/png', 'image/webp']
    )
on conflict (id) do update set
    name = excluded.name,
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- profile-avatars y organization-media son buckets preexistentes y no se recrean aquí.

-- =============================================================================
-- 2) Modelo relacional
-- =============================================================================

create table if not exists public.file_assets (
    id uuid primary key default gen_random_uuid(),
    owner_kind text not null
        check (owner_kind in ('USER', 'ORGANIZATION', 'PLATFORM')),
    owner_user_id uuid null references auth.users(id),
    owner_organization_id uuid null references public.organizations(id),
    purpose text not null
        check (purpose in (
            'USER_AVATAR', 'USER_COVER', 'PET_AVATAR', 'PET_GALLERY',
            'ORGANIZATION_LOGO', 'ORGANIZATION_COVER', 'ORGANIZATION_DOCUMENT',
            'ORGANIZATION_VERIFICATION_DOCUMENT', 'POST_MEDIA', 'ADOPTION_MEDIA',
            'LOST_FOUND_MEDIA', 'SERVICE_PROFILE_MEDIA', 'SUPPORT_ATTACHMENT',
            'MODERATION_EVIDENCE', 'MESSAGE_ATTACHMENT', 'EVENT_MEDIA',
            'PRODUCT_MEDIA', 'OTHER'
        )),
    visibility text not null
        check (visibility in (
            'PUBLIC', 'OWNER_ONLY', 'ORGANIZATION_PRIVATE', 'AUTHORIZED_STAFF',
            'RESOURCE_PARTICIPANTS', 'SIGNED_LINK_ONLY'
        )),
    status text not null default 'DRAFT'
        check (status in (
            'DRAFT', 'UPLOADING', 'UPLOADED', 'PROCESSING', 'READY',
            'REJECTED', 'QUARANTINED', 'DELETED', 'FAILED'
        )),
    current_version_id uuid null,
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    deleted_at timestamptz null,
    retention_until timestamptz null,
    legal_hold boolean not null default false,
    processing_status text not null default 'NOT_REQUIRED'
        check (processing_status in (
            'NOT_REQUIRED', 'PENDING', 'PROCESSING', 'READY',
            'REJECTED', 'FAILED', 'QUARANTINED'
        )),
    metadata jsonb not null default '{}'::jsonb,
    constraint file_assets_owner_xor check (
        (owner_kind = 'USER' and owner_user_id is not null and owner_organization_id is null)
        or
        (owner_kind = 'ORGANIZATION' and owner_user_id is null and owner_organization_id is not null)
        or
        (owner_kind = 'PLATFORM' and owner_user_id is null and owner_organization_id is null)
    ),
    constraint file_assets_sensitive_not_public check (
        purpose not in (
            'MODERATION_EVIDENCE',
            'ORGANIZATION_VERIFICATION_DOCUMENT',
            'SUPPORT_ATTACHMENT'
        )
        or visibility <> 'PUBLIC'
    ),
    constraint file_assets_deleted_status_coherent check (
        (status = 'DELETED' and deleted_at is not null)
        or (status <> 'DELETED' and deleted_at is null)
    )
);

create table if not exists public.file_asset_versions (
    id uuid primary key default gen_random_uuid(),
    asset_id uuid not null references public.file_assets(id) on delete cascade,
    storage_bucket text not null
        check (storage_bucket in (
            'profile-avatars', 'organization-media', 'public-media',
            'organization-documents', 'moderation-evidence', 'support-attachments'
        )),
    storage_path text not null,
    original_filename text not null,
    safe_filename text not null,
    declared_mime_type text not null,
    detected_mime_type text null,
    size_bytes bigint not null check (size_bytes > 0),
    checksum text null,
    status text not null default 'PENDING'
        check (status in (
            'PENDING', 'UPLOADED', 'PROCESSING', 'READY',
            'REJECTED', 'FAILED', 'QUARANTINED'
        )),
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default timezone('utc', now()),
    constraint file_asset_versions_bucket_path_unique
        unique (storage_bucket, storage_path),
    constraint file_asset_versions_safe_path check (
        storage_path not like '%..%'
        and storage_path not like '%://%'
        and storage_path not like '/%'
        and storage_path not like '%/'
        and storage_path not like E'%\\%'
    )
);

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'file_assets_current_version_fk'
          and conrelid = 'public.file_assets'::regclass
    ) then
        alter table public.file_assets
            add constraint file_assets_current_version_fk
            foreign key (current_version_id)
            references public.file_asset_versions(id)
            on delete set null
            deferrable initially deferred;
    end if;
end;
$$;

create table if not exists public.file_asset_links (
    id uuid primary key default gen_random_uuid(),
    asset_id uuid not null references public.file_assets(id) on delete cascade,
    resource_type text not null
        check (resource_type in (
            'USER', 'PET', 'ORGANIZATION', 'POST', 'ADOPTION', 'LOST_FOUND_CASE',
            'SERVICE_PROFILE', 'MODERATION_CASE', 'ORGANIZATION_VERIFICATION',
            'SUPPORT_TICKET', 'MESSAGE', 'EVENT', 'PRODUCT', 'OTHER'
        )),
    resource_id text not null check (resource_id not ilike 'http%'),
    relation_type text not null
        check (relation_type in (
            'PRIMARY', 'GALLERY', 'ATTACHMENT', 'EVIDENCE',
            'DOCUMENT', 'COVER', 'OTHER'
        )),
    sort_order integer not null default 0,
    is_primary boolean not null default false,
    created_at timestamptz not null default timezone('utc', now()),
    created_by uuid not null references auth.users(id),
    deleted_at timestamptz null
);

create unique index if not exists file_asset_links_one_active_primary_idx
    on public.file_asset_links (resource_type, resource_id, relation_type)
    where deleted_at is null and is_primary;

create table if not exists public.file_upload_sessions (
    id uuid primary key default gen_random_uuid(),
    asset_id uuid not null references public.file_assets(id) on delete cascade,
    version_id uuid not null references public.file_asset_versions(id) on delete cascade,
    state text not null default 'CREATED'
        check (state in (
            'CREATED', 'VALIDATING', 'READY_TO_UPLOAD', 'UPLOADING', 'UPLOADED',
            'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED'
        )),
    progress_percent integer not null default 0
        check (progress_percent between 0 and 100),
    failure_code text null,
    expires_at timestamptz null,
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.file_access_audit (
    id uuid primary key default gen_random_uuid(),
    asset_id uuid not null references public.file_assets(id) on delete cascade,
    actor_user_id uuid null references auth.users(id),
    action text not null,
    result text not null,
    context_type text null,
    context_id text null,
    created_at timestamptz not null default timezone('utc', now()),
    metadata jsonb not null default '{}'::jsonb
);

create table if not exists public.file_retention_policies (
    purpose text primary key
        check (purpose in (
            'USER_AVATAR', 'USER_COVER', 'PET_AVATAR', 'PET_GALLERY',
            'ORGANIZATION_LOGO', 'ORGANIZATION_COVER', 'ORGANIZATION_DOCUMENT',
            'ORGANIZATION_VERIFICATION_DOCUMENT', 'POST_MEDIA', 'ADOPTION_MEDIA',
            'LOST_FOUND_MEDIA', 'SERVICE_PROFILE_MEDIA', 'SUPPORT_ATTACHMENT',
            'MODERATION_EVIDENCE', 'MESSAGE_ATTACHMENT', 'EVENT_MEDIA',
            'PRODUCT_MEDIA', 'OTHER'
        )),
    retain_days integer null check (retain_days is null or retain_days >= 0),
    legal_hold_allowed boolean not null default false,
    requires_audit_on_delete boolean not null default false
);

insert into public.file_retention_policies (
    purpose, retain_days, legal_hold_allowed, requires_audit_on_delete
)
values
    ('MODERATION_EVIDENCE', 365, true, true),
    ('ORGANIZATION_VERIFICATION_DOCUMENT', 365, true, true),
    ('SUPPORT_ATTACHMENT', 365, true, true),
    ('USER_AVATAR', null, false, false),
    ('USER_COVER', null, false, false),
    ('PET_AVATAR', null, false, false),
    ('PET_GALLERY', null, false, false),
    ('ORGANIZATION_LOGO', null, false, false),
    ('ORGANIZATION_COVER', null, false, false),
    ('ORGANIZATION_DOCUMENT', null, false, false),
    ('POST_MEDIA', null, false, false),
    ('ADOPTION_MEDIA', null, false, false),
    ('LOST_FOUND_MEDIA', null, false, false),
    ('SERVICE_PROFILE_MEDIA', null, false, false),
    ('MESSAGE_ATTACHMENT', null, false, false),
    ('EVENT_MEDIA', null, false, false),
    ('PRODUCT_MEDIA', null, false, false),
    ('OTHER', null, false, false)
on conflict (purpose) do update set
    retain_days = excluded.retain_days,
    legal_hold_allowed = excluded.legal_hold_allowed,
    requires_audit_on_delete = excluded.requires_audit_on_delete;

create index if not exists file_assets_owner_user_idx
    on public.file_assets (owner_user_id) where owner_user_id is not null;
create index if not exists file_assets_owner_org_idx
    on public.file_assets (owner_organization_id) where owner_organization_id is not null;
create index if not exists file_assets_purpose_idx on public.file_assets (purpose);
create index if not exists file_assets_status_idx on public.file_assets (status);
create index if not exists file_asset_versions_asset_idx on public.file_asset_versions (asset_id);
create index if not exists file_asset_links_resource_idx
    on public.file_asset_links (resource_type, resource_id) where deleted_at is null;
create index if not exists file_asset_links_asset_idx
    on public.file_asset_links (asset_id) where deleted_at is null;
create index if not exists file_upload_sessions_asset_idx on public.file_upload_sessions (asset_id);
create index if not exists file_upload_sessions_version_idx on public.file_upload_sessions (version_id);
create index if not exists file_upload_sessions_actor_state_idx
    on public.file_upload_sessions (created_by, state);
create index if not exists file_access_audit_asset_created_idx
    on public.file_access_audit (asset_id, created_at desc);

comment on table public.file_assets is
    'M05 aggregate root for logical file assets; storage URLs and tokens are never persisted.';
comment on table public.file_asset_versions is
    'M05 immutable-ish physical object versions; the legacy leover bucket is intentionally excluded.';
comment on table public.file_asset_links is
    'M05 soft-deletable links between file assets and domain resources.';
comment on table public.file_upload_sessions is
    'M05 short-lived upload authorization sessions.';
comment on table public.file_access_audit is
    'M05 append-only access and sensitive-operation audit trail.';
comment on table public.file_retention_policies is
    'M05 purpose-based retention and legal-hold policy.';

-- =============================================================================
-- 3) Helpers
-- =============================================================================

create or replace function public.m05_require_active_actor()
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    return public.m04_require_active_actor();
end;
$$;

create or replace function public.m05_is_sensitive_purpose(p_purpose text)
returns boolean
language sql
immutable
security definer
set search_path = public
as $$
    select upper(trim(coalesce(p_purpose, ''))) in (
        'MODERATION_EVIDENCE',
        'ORGANIZATION_VERIFICATION_DOCUMENT',
        'SUPPORT_ATTACHMENT'
    );
$$;

create or replace function public.m05_logical_bucket(p_purpose text)
returns text
language sql
immutable
security definer
set search_path = public
as $$
    select case upper(trim(coalesce(p_purpose, '')))
        when 'USER_AVATAR' then 'profile-avatars'
        when 'USER_COVER' then 'profile-avatars'
        when 'ORGANIZATION_LOGO' then 'organization-media'
        when 'ORGANIZATION_COVER' then 'organization-media'
        when 'ORGANIZATION_DOCUMENT' then 'organization-documents'
        when 'ORGANIZATION_VERIFICATION_DOCUMENT' then 'organization-documents'
        when 'MODERATION_EVIDENCE' then 'moderation-evidence'
        when 'SUPPORT_ATTACHMENT' then 'support-attachments'
        when 'MESSAGE_ATTACHMENT' then 'support-attachments'
        when 'PET_AVATAR' then 'public-media'
        when 'PET_GALLERY' then 'public-media'
        when 'POST_MEDIA' then 'public-media'
        when 'ADOPTION_MEDIA' then 'public-media'
        when 'LOST_FOUND_MEDIA' then 'public-media'
        when 'SERVICE_PROFILE_MEDIA' then 'public-media'
        when 'EVENT_MEDIA' then 'public-media'
        when 'PRODUCT_MEDIA' then 'public-media'
        else null
    end;
$$;

create or replace function public.m05_purpose_allows_visibility(
    p_purpose text,
    p_visibility text
)
returns boolean
language sql
immutable
security definer
set search_path = public
as $$
    with v as (
        select
            upper(trim(coalesce(p_purpose, ''))) as purpose,
            upper(trim(coalesce(p_visibility, ''))) as visibility
    )
    select case
        when purpose in (
            'USER_AVATAR', 'USER_COVER', 'PET_AVATAR', 'PET_GALLERY',
            'POST_MEDIA', 'ADOPTION_MEDIA', 'LOST_FOUND_MEDIA',
            'SERVICE_PROFILE_MEDIA', 'EVENT_MEDIA', 'PRODUCT_MEDIA'
        ) then visibility in ('PUBLIC', 'OWNER_ONLY', 'SIGNED_LINK_ONLY')
        when purpose in ('ORGANIZATION_LOGO', 'ORGANIZATION_COVER')
            then visibility in (
                'PUBLIC', 'OWNER_ONLY', 'SIGNED_LINK_ONLY', 'ORGANIZATION_PRIVATE'
            )
        when purpose = 'ORGANIZATION_DOCUMENT'
            then visibility in ('OWNER_ONLY', 'ORGANIZATION_PRIVATE', 'SIGNED_LINK_ONLY')
        when purpose = 'MESSAGE_ATTACHMENT'
            then visibility in ('RESOURCE_PARTICIPANTS', 'OWNER_ONLY', 'SIGNED_LINK_ONLY')
        when purpose in (
            'MODERATION_EVIDENCE',
            'ORGANIZATION_VERIFICATION_DOCUMENT',
            'SUPPORT_ATTACHMENT'
        ) then visibility in (
            'AUTHORIZED_STAFF', 'ORGANIZATION_PRIVATE', 'SIGNED_LINK_ONLY',
            'OWNER_ONLY', 'RESOURCE_PARTICIPANTS'
        )
        when purpose = 'OTHER' then visibility = 'OWNER_ONLY'
        else false
    end
    from v;
$$;

create or replace function public.m05_staff_can_access_purpose(p_purpose text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select case upper(trim(coalesce(p_purpose, '')))
        when 'MODERATION_EVIDENCE'
            then public.has_permission('moderation.view_sensitive')
        when 'ORGANIZATION_VERIFICATION_DOCUMENT'
            then public.has_permission('organizations.review_verification')
        when 'SUPPORT_ATTACHMENT'
            then public.has_permission('support.view_sensitive')
        else false
    end;
$$;

create or replace function public.m05_owner_kind_allows_purpose(
    p_purpose text,
    p_owner_kind text
)
returns boolean
language sql
immutable
security definer
set search_path = public
as $$
    with x as (
        select upper(trim(coalesce(p_purpose, ''))) p,
               upper(trim(coalesce(p_owner_kind, ''))) o
    )
    select case
        when p in ('USER_AVATAR', 'USER_COVER', 'PET_AVATAR', 'PET_GALLERY',
                   'MESSAGE_ATTACHMENT')
            then o = 'USER'
        when p in ('ORGANIZATION_LOGO', 'ORGANIZATION_COVER', 'ORGANIZATION_DOCUMENT')
            then o = 'ORGANIZATION'
        when p = 'ORGANIZATION_VERIFICATION_DOCUMENT'
            then o in ('ORGANIZATION', 'PLATFORM')
        when p = 'SUPPORT_ATTACHMENT'
            then o in ('USER', 'PLATFORM')
        when p = 'MODERATION_EVIDENCE'
            then o = 'PLATFORM'
        when p in ('POST_MEDIA', 'ADOPTION_MEDIA', 'LOST_FOUND_MEDIA',
                   'SERVICE_PROFILE_MEDIA', 'EVENT_MEDIA', 'PRODUCT_MEDIA')
            then o in ('USER', 'ORGANIZATION')
        when p = 'OTHER'
            then o in ('USER', 'ORGANIZATION', 'PLATFORM')
        else false
    end
    from x;
$$;

create or replace function public.m05_audit(
    p_asset_id uuid,
    p_action text,
    p_result text,
    p_context_type text default null,
    p_context_id text default null,
    p_metadata jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id uuid;
begin
    insert into public.file_access_audit (
        asset_id, actor_user_id, action, result, context_type, context_id, metadata
    )
    values (
        p_asset_id,
        auth.uid(),
        upper(trim(p_action)),
        upper(trim(p_result)),
        nullif(upper(trim(coalesce(p_context_type, ''))), ''),
        nullif(trim(coalesce(p_context_id, '')), ''),
        coalesce(p_metadata, '{}'::jsonb)
    )
    returning id into v_id;
    return v_id;
end;
$$;

create or replace function public.m05_can_read_asset(p_asset public.file_assets)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select
        p_asset.deleted_at is null
        and (
            (p_asset.visibility = 'PUBLIC' and p_asset.status = 'READY')
            or (auth.uid() is not null and p_asset.owner_user_id = auth.uid())
            or (
                auth.uid() is not null
                and p_asset.owner_organization_id is not null
                and (
                    public.has_org_permission(
                        p_asset.owner_organization_id,
                        'organization.view_private'
                    )
                    or public.has_org_permission(
                        p_asset.owner_organization_id,
                        'organization.update'
                    )
                )
            )
            or (auth.uid() is not null and public.m05_staff_can_access_purpose(p_asset.purpose))
        );
$$;

create or replace function public.m05_can_read_asset(p_asset_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(
        (select public.m05_can_read_asset(a) from public.file_assets a where a.id = p_asset_id),
        false
    );
$$;

create or replace function public.m05_can_write_asset(
    p_purpose text,
    p_owner_kind text,
    p_owner_user_id uuid,
    p_owner_organization_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select
        auth.uid() is not null
        and public.m05_owner_kind_allows_purpose(p_purpose, p_owner_kind)
        and (
            (upper(p_owner_kind) = 'USER' and p_owner_user_id = auth.uid())
            or (
                upper(p_owner_kind) = 'ORGANIZATION'
                and p_owner_organization_id is not null
                and public.has_org_permission(
                    p_owner_organization_id,
                    'organization.update'
                )
            )
            or (
                upper(p_owner_kind) = 'PLATFORM'
                and public.m05_staff_can_access_purpose(p_purpose)
            )
            or public.m05_staff_can_access_purpose(p_purpose)
        );
$$;

create or replace function public.m05_can_write_asset(p_asset public.file_assets)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.m05_can_write_asset(
        p_asset.purpose,
        p_asset.owner_kind,
        p_asset.owner_user_id,
        p_asset.owner_organization_id
    );
$$;

create or replace function public.m05_can_write_asset(p_asset_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(
        (select public.m05_can_write_asset(a) from public.file_assets a where a.id = p_asset_id),
        false
    );
$$;

create or replace function public.m05_build_storage_path(
    p_purpose text,
    p_owner_user_id uuid,
    p_owner_organization_id uuid,
    p_resource_type text,
    p_resource_id text,
    p_asset_id uuid,
    p_safe_filename text
)
returns text
language plpgsql
immutable
security definer
set search_path = public
as $$
declare
    p text := upper(trim(coalesce(p_purpose, '')));
    rt text := upper(trim(coalesce(p_resource_type, '')));
    rid text := trim(coalesce(p_resource_id, ''));
    safe text := trim(coalesce(p_safe_filename, ''));
    result text;
begin
    if p_asset_id is null
       or safe = ''
       or safe !~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
       or safe like '%..%'
       or safe like '%.%'
          and lower(split_part(safe, '.', array_length(string_to_array(safe, '.'), 1)))
              not in ('jpg', 'jpeg', 'png', 'webp', 'pdf') then
        raise exception 'PATH_SEGMENT_INVALID';
    end if;
    if rid <> '' and (
        rid !~ '^[A-Za-z0-9._-]{1,128}$'
        or rid like '%..%'
        or rid ilike 'http%'
    ) then
        raise exception 'RESOURCE_ID_INVALID';
    end if;

    result := case p
        when 'USER_AVATAR' then
            'users/' || p_owner_user_id || '/avatars/' || p_asset_id || '/' || safe
        when 'USER_COVER' then
            'users/' || p_owner_user_id || '/covers/' || p_asset_id || '/' || safe
        when 'PET_AVATAR' then
            case when rt = 'PET' then
                'users/' || p_owner_user_id || '/pets/' || rid || '/' || p_asset_id || '/' || safe
            end
        when 'PET_GALLERY' then
            case when rt = 'PET' then
                'users/' || p_owner_user_id || '/pets/' || rid || '/gallery/' || p_asset_id || '/' || safe
            end
        when 'ORGANIZATION_LOGO' then
            'organizations/' || p_owner_organization_id || '/logo/' || p_asset_id || '/' || safe
        when 'ORGANIZATION_COVER' then
            'organizations/' || p_owner_organization_id || '/cover/' || p_asset_id || '/' || safe
        when 'ORGANIZATION_DOCUMENT' then
            'organizations/' || p_owner_organization_id || '/documents/' || p_asset_id || '/' || safe
        when 'ORGANIZATION_VERIFICATION_DOCUMENT' then
            case
                when p_owner_organization_id is not null then
                    'organizations/' || p_owner_organization_id || '/verification/' || p_asset_id || '/' || safe
                when rt in ('ORGANIZATION', 'ORGANIZATION_VERIFICATION') then
                    'organizations/' || rid || '/verification/' || p_asset_id || '/' || safe
            end
        when 'POST_MEDIA' then
            case when rt = 'POST' then 'posts/' || rid || '/' || p_asset_id || '/' || safe end
        when 'ADOPTION_MEDIA' then
            case when rt = 'ADOPTION' then 'adoptions/' || rid || '/' || p_asset_id || '/' || safe end
        when 'LOST_FOUND_MEDIA' then
            case when rt = 'LOST_FOUND_CASE' then 'lost_found/' || rid || '/' || p_asset_id || '/' || safe end
        when 'SERVICE_PROFILE_MEDIA' then
            case when rt = 'SERVICE_PROFILE' then 'services/' || rid || '/' || p_asset_id || '/' || safe end
        when 'SUPPORT_ATTACHMENT' then
            case when rt = 'SUPPORT_TICKET' then 'support/tickets/' || rid || '/' || p_asset_id || '/' || safe end
        when 'MODERATION_EVIDENCE' then
            case when rt = 'MODERATION_CASE' then 'moderation/cases/' || rid || '/evidence/' || p_asset_id || '/' || safe end
        when 'MESSAGE_ATTACHMENT' then
            case when rt = 'MESSAGE' then 'messages/' || rid || '/' || p_asset_id || '/' || safe end
        when 'EVENT_MEDIA' then
            case when rt = 'EVENT' then 'events/' || rid || '/' || p_asset_id || '/' || safe end
        when 'PRODUCT_MEDIA' then
            case when rt = 'PRODUCT' then 'products/' || rid || '/' || p_asset_id || '/' || safe end
        else null
    end;

    if result is null
       or result like '%..%'
       or result like '%://%'
       or result ilike '%leover%' then
        raise exception 'PATH_BUILD_DENIED';
    end if;
    return result;
end;
$$;

-- Helpers are internal implementation details.
revoke all on function public.m05_require_active_actor() from public;
revoke all on function public.m05_is_sensitive_purpose(text) from public;
revoke all on function public.m05_logical_bucket(text) from public;
revoke all on function public.m05_purpose_allows_visibility(text, text) from public;
revoke all on function public.m05_staff_can_access_purpose(text) from public;
revoke all on function public.m05_owner_kind_allows_purpose(text, text) from public;
revoke all on function public.m05_audit(uuid, text, text, text, text, jsonb) from public;
revoke all on function public.m05_can_read_asset(public.file_assets) from public;
revoke all on function public.m05_can_read_asset(uuid) from public;
revoke all on function public.m05_can_write_asset(text, text, uuid, uuid) from public;
revoke all on function public.m05_can_write_asset(public.file_assets) from public;
revoke all on function public.m05_can_write_asset(uuid) from public;
revoke all on function public.m05_build_storage_path(text, uuid, uuid, text, text, uuid, text) from public;

-- RLS ejecuta estos helpers bajo el rol del solicitante; sus cuerpos SECURITY
-- DEFINER mantienen encapsuladas las tablas y los permisos subyacentes.
grant execute on function public.m05_can_read_asset(public.file_assets) to authenticated;
grant execute on function public.m05_can_read_asset(uuid) to authenticated;
grant execute on function public.m05_can_write_asset(public.file_assets) to authenticated;
grant execute on function public.m05_can_write_asset(uuid) to authenticated;

-- =============================================================================
-- 4) RLS de tablas
-- =============================================================================

alter table public.file_assets enable row level security;
alter table public.file_asset_versions enable row level security;
alter table public.file_asset_links enable row level security;
alter table public.file_upload_sessions enable row level security;
alter table public.file_access_audit enable row level security;
alter table public.file_retention_policies enable row level security;

drop policy if exists file_assets_select on public.file_assets;
create policy file_assets_select on public.file_assets
    for select to authenticated
    using (public.m05_can_read_asset(file_assets));
drop policy if exists file_assets_insert_deny on public.file_assets;
create policy file_assets_insert_deny on public.file_assets
    for insert to authenticated with check (false);
drop policy if exists file_assets_update_deny on public.file_assets;
create policy file_assets_update_deny on public.file_assets
    for update to authenticated using (false) with check (false);
drop policy if exists file_assets_delete_deny on public.file_assets;
create policy file_assets_delete_deny on public.file_assets
    for delete to authenticated using (false);

drop policy if exists file_asset_versions_select on public.file_asset_versions;
create policy file_asset_versions_select on public.file_asset_versions
    for select to authenticated
    using (public.m05_can_read_asset(asset_id));
drop policy if exists file_asset_versions_insert_deny on public.file_asset_versions;
create policy file_asset_versions_insert_deny on public.file_asset_versions
    for insert to authenticated with check (false);
drop policy if exists file_asset_versions_update_deny on public.file_asset_versions;
create policy file_asset_versions_update_deny on public.file_asset_versions
    for update to authenticated using (false) with check (false);
drop policy if exists file_asset_versions_delete_deny on public.file_asset_versions;
create policy file_asset_versions_delete_deny on public.file_asset_versions
    for delete to authenticated using (false);

drop policy if exists file_asset_links_select on public.file_asset_links;
create policy file_asset_links_select on public.file_asset_links
    for select to authenticated
    using (public.m05_can_read_asset(asset_id));
drop policy if exists file_asset_links_insert_deny on public.file_asset_links;
create policy file_asset_links_insert_deny on public.file_asset_links
    for insert to authenticated with check (false);
drop policy if exists file_asset_links_update_deny on public.file_asset_links;
create policy file_asset_links_update_deny on public.file_asset_links
    for update to authenticated using (false) with check (false);
drop policy if exists file_asset_links_delete_deny on public.file_asset_links;
create policy file_asset_links_delete_deny on public.file_asset_links
    for delete to authenticated using (false);

drop policy if exists file_upload_sessions_select on public.file_upload_sessions;
create policy file_upload_sessions_select on public.file_upload_sessions
    for select to authenticated using (created_by = auth.uid());
drop policy if exists file_upload_sessions_insert_deny on public.file_upload_sessions;
create policy file_upload_sessions_insert_deny on public.file_upload_sessions
    for insert to authenticated with check (false);
drop policy if exists file_upload_sessions_update_deny on public.file_upload_sessions;
create policy file_upload_sessions_update_deny on public.file_upload_sessions
    for update to authenticated using (false) with check (false);
drop policy if exists file_upload_sessions_delete_deny on public.file_upload_sessions;
create policy file_upload_sessions_delete_deny on public.file_upload_sessions
    for delete to authenticated using (false);

drop policy if exists file_access_audit_select on public.file_access_audit;
create policy file_access_audit_select on public.file_access_audit
    for select to authenticated
    using (
        public.has_permission('audit.view')
        or public.has_permission('moderation.view_sensitive')
    );
drop policy if exists file_access_audit_insert_deny on public.file_access_audit;
create policy file_access_audit_insert_deny on public.file_access_audit
    for insert to authenticated with check (false);
drop policy if exists file_access_audit_update_deny on public.file_access_audit;
create policy file_access_audit_update_deny on public.file_access_audit
    for update to authenticated using (false) with check (false);
drop policy if exists file_access_audit_delete_deny on public.file_access_audit;
create policy file_access_audit_delete_deny on public.file_access_audit
    for delete to authenticated using (false);

drop policy if exists file_retention_policies_select on public.file_retention_policies;
create policy file_retention_policies_select on public.file_retention_policies
    for select to authenticated using (true);
drop policy if exists file_retention_policies_insert_deny on public.file_retention_policies;
create policy file_retention_policies_insert_deny on public.file_retention_policies
    for insert to authenticated with check (false);
drop policy if exists file_retention_policies_update_deny on public.file_retention_policies;
create policy file_retention_policies_update_deny on public.file_retention_policies
    for update to authenticated using (false) with check (false);
drop policy if exists file_retention_policies_delete_deny on public.file_retention_policies;
create policy file_retention_policies_delete_deny on public.file_retention_policies
    for delete to authenticated using (false);

grant select on public.file_assets to authenticated;
grant select on public.file_asset_versions to authenticated;
grant select on public.file_asset_links to authenticated;
grant select on public.file_upload_sessions to authenticated;
grant select on public.file_access_audit to authenticated;
grant select on public.file_retention_policies to authenticated;

revoke insert, update, delete on public.file_assets from authenticated;
revoke insert, update, delete on public.file_asset_versions from authenticated;
revoke insert, update, delete on public.file_asset_links from authenticated;
revoke insert, update, delete on public.file_upload_sessions from authenticated;
revoke insert, update, delete on public.file_access_audit from authenticated;
revoke insert, update, delete on public.file_retention_policies from authenticated;

-- =============================================================================
-- 5) Storage object policies M05
-- =============================================================================

drop policy if exists m05_public_media_select on storage.objects;
create policy m05_public_media_select on storage.objects
    for select
    using (bucket_id = 'public-media');

drop policy if exists m05_public_media_insert on storage.objects;
create policy m05_public_media_insert on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'public-media'
        and name not like '%..%'
        and exists (
            select 1
            from public.file_asset_versions v
            join public.file_upload_sessions s on s.version_id = v.id
            where v.storage_path = name
              and v.storage_bucket = 'public-media'
              and s.created_by = auth.uid()
              and s.state in ('READY_TO_UPLOAD', 'UPLOADING', 'CREATED', 'VALIDATING')
              and (s.expires_at is null or s.expires_at > now())
        )
    );

drop policy if exists m05_public_media_update on storage.objects;
create policy m05_public_media_update on storage.objects
    for update to authenticated
    using (
        bucket_id = 'public-media'
        and exists (
            select 1
            from public.file_asset_versions v
            left join public.file_upload_sessions s on s.version_id = v.id
            where v.storage_bucket = bucket_id
              and v.storage_path = name
              and (
                  s.created_by = auth.uid()
                  or public.m05_can_write_asset(v.asset_id)
              )
        )
    )
    with check (
        bucket_id = 'public-media'
        and name not like '%..%'
        and exists (
            select 1
            from public.file_asset_versions v
            left join public.file_upload_sessions s on s.version_id = v.id
            where v.storage_bucket = bucket_id
              and v.storage_path = name
              and (
                  s.created_by = auth.uid()
                  or public.m05_can_write_asset(v.asset_id)
              )
        )
    );

drop policy if exists m05_public_media_delete on storage.objects;
create policy m05_public_media_delete on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'public-media'
        and exists (
            select 1
            from public.file_asset_versions v
            left join public.file_upload_sessions s on s.version_id = v.id
            where v.storage_bucket = bucket_id
              and v.storage_path = name
              and (
                  s.created_by = auth.uid()
                  or public.m05_can_write_asset(v.asset_id)
              )
        )
    );

drop policy if exists m05_private_files_select on storage.objects;
create policy m05_private_files_select on storage.objects
    for select to authenticated
    using (
        bucket_id in (
            'organization-documents', 'moderation-evidence', 'support-attachments'
        )
        and exists (
            select 1
            from public.file_asset_versions v
            where v.storage_bucket = bucket_id
              and v.storage_path = name
              and public.m05_can_read_asset(v.asset_id)
        )
    );

drop policy if exists m05_private_files_insert on storage.objects;
create policy m05_private_files_insert on storage.objects
    for insert to authenticated
    with check (
        bucket_id in (
            'organization-documents', 'moderation-evidence', 'support-attachments'
        )
        and name not like '%..%'
        and exists (
            select 1
            from public.file_asset_versions v
            join public.file_upload_sessions s on s.version_id = v.id
            where v.storage_bucket = bucket_id
              and v.storage_path = name
              and s.created_by = auth.uid()
              and s.state in ('READY_TO_UPLOAD', 'UPLOADING', 'CREATED', 'VALIDATING')
              and (s.expires_at is null or s.expires_at > now())
        )
    );

drop policy if exists m05_private_files_update on storage.objects;
create policy m05_private_files_update on storage.objects
    for update to authenticated
    using (
        bucket_id in (
            'organization-documents', 'moderation-evidence', 'support-attachments'
        )
        and exists (
            select 1
            from public.file_asset_versions v
            where v.storage_bucket = bucket_id
              and v.storage_path = name
              and public.m05_can_write_asset(v.asset_id)
        )
    )
    with check (
        bucket_id in (
            'organization-documents', 'moderation-evidence', 'support-attachments'
        )
        and name not like '%..%'
        and exists (
            select 1
            from public.file_asset_versions v
            where v.storage_bucket = bucket_id
              and v.storage_path = name
              and public.m05_can_write_asset(v.asset_id)
        )
    );

drop policy if exists m05_private_files_delete on storage.objects;
create policy m05_private_files_delete on storage.objects
    for delete to authenticated
    using (
        bucket_id in (
            'organization-documents', 'moderation-evidence', 'support-attachments'
        )
        and exists (
            select 1
            from public.file_asset_versions v
            where v.storage_bucket = bucket_id
              and v.storage_path = name
              and public.m05_can_write_asset(v.asset_id)
        )
    );

-- No se alteran las policies de profile-avatars ni organization-media.

-- =============================================================================
-- 6) RPCs
-- =============================================================================

create or replace function public.create_file_asset_draft(
    p_purpose text,
    p_owner_kind text,
    p_owner_user_id uuid,
    p_owner_organization_id uuid,
    p_visibility text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := public.m05_require_active_actor();
    purpose_value text := upper(trim(coalesce(p_purpose, '')));
    owner_value text := upper(trim(coalesce(p_owner_kind, '')));
    visibility_value text := upper(trim(coalesce(p_visibility, '')));
    user_value uuid := p_owner_user_id;
    new_asset public.file_assets;
begin
    if purpose_value = 'OTHER'
       or public.m05_logical_bucket(purpose_value) is null
       or not public.m05_owner_kind_allows_purpose(purpose_value, owner_value)
       or not public.m05_purpose_allows_visibility(purpose_value, visibility_value) then
        raise exception 'VALIDATION';
    end if;

    if owner_value = 'USER' then
        user_value := actor;
        p_owner_organization_id := null;
    elsif owner_value = 'ORGANIZATION' then
        user_value := null;
        if p_owner_organization_id is null
           or not public.has_org_permission(p_owner_organization_id, 'organization.update') then
            raise exception 'FORBIDDEN';
        end if;
    elsif owner_value = 'PLATFORM' then
        user_value := null;
        p_owner_organization_id := null;
        if not public.m05_staff_can_access_purpose(purpose_value) then
            raise exception 'FORBIDDEN';
        end if;
    else
        raise exception 'VALIDATION';
    end if;

    insert into public.file_assets (
        owner_kind, owner_user_id, owner_organization_id,
        purpose, visibility, status, created_by
    )
    values (
        owner_value, user_value, p_owner_organization_id,
        purpose_value, visibility_value, 'DRAFT', actor
    )
    returning * into new_asset;

    return to_jsonb(new_asset);
end;
$$;

create or replace function public.get_file_asset(p_asset_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    result public.file_assets;
begin
    perform public.m05_require_active_actor();
    select * into result from public.file_assets where id = p_asset_id;
    if result.id is null or not public.m05_can_read_asset(result) then
        raise exception 'FORBIDDEN';
    end if;
    return to_jsonb(result);
end;
$$;

create or replace function public.list_file_assets_for_resource(
    p_resource_type text,
    p_resource_id text
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    rt text := upper(trim(coalesce(p_resource_type, '')));
    rid text := trim(coalesce(p_resource_id, ''));
begin
    perform public.m05_require_active_actor();
    if rid = '' or rid ilike 'http%' then
        raise exception 'VALIDATION';
    end if;
    return coalesce((
        select jsonb_agg(
            jsonb_build_object('asset', to_jsonb(a), 'link', to_jsonb(l))
            order by l.sort_order, l.created_at
        )
        from public.file_asset_links l
        join public.file_assets a on a.id = l.asset_id
        where l.resource_type = rt
          and l.resource_id = rid
          and l.deleted_at is null
          and public.m05_can_read_asset(a)
    ), '[]'::jsonb);
end;
$$;

create or replace function public.link_file_asset(
    p_asset_id uuid,
    p_resource_type text,
    p_resource_id text,
    p_relation_type text,
    p_is_primary boolean,
    p_sort_order integer
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := public.m05_require_active_actor();
    a public.file_assets;
    rt text := upper(trim(coalesce(p_resource_type, '')));
    rid text := trim(coalesce(p_resource_id, ''));
    relation_value text := upper(trim(coalesce(p_relation_type, '')));
    result public.file_asset_links;
begin
    select * into a from public.file_assets where id = p_asset_id for update;
    if a.id is null or not public.m05_can_write_asset(a) then
        raise exception 'FORBIDDEN';
    end if;
    if rid = '' or rid ilike 'http%' or rid like '%..%' then
        raise exception 'VALIDATION';
    end if;

    if coalesce(p_is_primary, false) then
        update public.file_asset_links
        set is_primary = false
        where resource_type = rt
          and resource_id = rid
          and relation_type = relation_value
          and deleted_at is null
          and is_primary;
    end if;

    insert into public.file_asset_links (
        asset_id, resource_type, resource_id, relation_type,
        sort_order, is_primary, created_by
    )
    values (
        p_asset_id, rt, rid, relation_value,
        greatest(coalesce(p_sort_order, 0), 0), coalesce(p_is_primary, false), actor
    )
    returning * into result;
    return to_jsonb(result);
end;
$$;

create or replace function public.unlink_file_asset(
    p_asset_id uuid,
    p_resource_type text,
    p_resource_id text,
    p_relation_type text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    result public.file_asset_links;
begin
    perform public.m05_require_active_actor();
    if not public.m05_can_write_asset(p_asset_id) then
        raise exception 'FORBIDDEN';
    end if;
    update public.file_asset_links
    set deleted_at = timezone('utc', now()), is_primary = false
    where asset_id = p_asset_id
      and resource_type = upper(trim(p_resource_type))
      and resource_id = trim(p_resource_id)
      and relation_type = upper(trim(p_relation_type))
      and deleted_at is null
    returning * into result;
    return coalesce(to_jsonb(result), jsonb_build_object('unlinked', false));
end;
$$;

create or replace function public.request_file_asset_delete(p_asset_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    a public.file_assets;
begin
    perform public.m05_require_active_actor();
    select * into a from public.file_assets where id = p_asset_id for update;
    if a.id is null or not public.m05_can_write_asset(a) then
        raise exception 'FORBIDDEN';
    end if;
    if a.legal_hold then
        raise exception 'LEGAL_HOLD';
    end if;
    if a.status <> 'DELETED' then
        update public.file_assets
        set status = 'DELETED',
            deleted_at = timezone('utc', now()),
            retention_until = coalesce(
                retention_until,
                timezone('utc', now()) + (
                    coalesce((
                        select retain_days
                        from public.file_retention_policies
                        where purpose = a.purpose
                    ), 0) || ' days'
                )::interval
            ),
            updated_at = timezone('utc', now())
        where id = p_asset_id
        returning * into a;
        perform public.m05_audit(p_asset_id, 'SOFT_DELETE', 'SUCCESS');
    end if;
    return to_jsonb(a);
end;
$$;

create or replace function public.restore_file_asset(p_asset_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    a public.file_assets;
begin
    perform public.m05_require_active_actor();
    select * into a from public.file_assets where id = p_asset_id for update;
    if a.id is null or not public.m05_can_write_asset(a) then
        raise exception 'FORBIDDEN';
    end if;
    if a.status = 'DELETED' then
        update public.file_assets
        set status = case when current_version_id is null then 'DRAFT' else 'READY' end,
            deleted_at = null,
            retention_until = null,
            updated_at = timezone('utc', now())
        where id = p_asset_id
        returning * into a;
        perform public.m05_audit(p_asset_id, 'RESTORE', 'SUCCESS');
    end if;
    return to_jsonb(a);
end;
$$;

create or replace function public.create_file_upload_session(
    p_purpose text,
    p_owner_kind text,
    p_owner_user_id uuid,
    p_owner_organization_id uuid,
    p_visibility text,
    p_resource_type text,
    p_resource_id text,
    p_original_filename text,
    p_declared_mime_type text,
    p_size_bytes bigint,
    p_safe_filename text,
    p_storage_path text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := public.m05_require_active_actor();
    purpose_value text := upper(trim(coalesce(p_purpose, '')));
    owner_value text := upper(trim(coalesce(p_owner_kind, '')));
    visibility_value text := upper(trim(coalesce(p_visibility, '')));
    mime_value text := lower(trim(coalesce(p_declared_mime_type, '')));
    bucket_value text;
    path_value text;
    user_value uuid := p_owner_user_id;
    max_bytes bigint;
    a public.file_assets;
    v public.file_asset_versions;
    s public.file_upload_sessions;
begin
    bucket_value := public.m05_logical_bucket(purpose_value);
    if purpose_value = 'OTHER'
       or bucket_value is null
       or bucket_value = 'leover'
       or not public.m05_owner_kind_allows_purpose(purpose_value, owner_value)
       or not public.m05_purpose_allows_visibility(purpose_value, visibility_value) then
        raise exception 'VALIDATION';
    end if;

    if owner_value = 'USER' then
        user_value := actor;
        p_owner_organization_id := null;
    elsif owner_value = 'ORGANIZATION' then
        user_value := null;
        if p_owner_organization_id is null
           or not public.has_org_permission(p_owner_organization_id, 'organization.update') then
            raise exception 'FORBIDDEN';
        end if;
    elsif owner_value = 'PLATFORM' then
        user_value := null;
        p_owner_organization_id := null;
        if not public.m05_staff_can_access_purpose(purpose_value) then
            raise exception 'FORBIDDEN';
        end if;
    else
        raise exception 'VALIDATION';
    end if;

    max_bytes := case purpose_value
        when 'USER_AVATAR' then 5242880
        when 'USER_COVER' then 5242880
        when 'ORGANIZATION_LOGO' then 5242880
        when 'ORGANIZATION_COVER' then 5242880
        when 'MODERATION_EVIDENCE' then 10485760
        when 'ORGANIZATION_DOCUMENT' then 15728640
        when 'ORGANIZATION_VERIFICATION_DOCUMENT' then 15728640
        when 'SUPPORT_ATTACHMENT' then 15728640
        else 8388608
    end;
    if p_size_bytes is null or p_size_bytes <= 0 or p_size_bytes > max_bytes then
        raise exception 'FILE_SIZE_INVALID';
    end if;
    if bucket_value in ('organization-documents', 'moderation-evidence', 'support-attachments') then
        if mime_value not in ('application/pdf', 'image/jpeg', 'image/png', 'image/webp') then
            raise exception 'MIME_TYPE_INVALID';
        end if;
    elsif mime_value not in ('image/jpeg', 'image/png', 'image/webp') then
        raise exception 'MIME_TYPE_INVALID';
    end if;

    insert into public.file_assets (
        owner_kind, owner_user_id, owner_organization_id, purpose,
        visibility, status, processing_status, created_by
    )
    values (
        owner_value, user_value, p_owner_organization_id, purpose_value,
        visibility_value, 'UPLOADING',
        case when purpose_value in (
            'USER_AVATAR', 'USER_COVER', 'PET_AVATAR', 'PET_GALLERY',
            'ORGANIZATION_LOGO', 'ORGANIZATION_COVER', 'POST_MEDIA',
            'ADOPTION_MEDIA', 'LOST_FOUND_MEDIA', 'SERVICE_PROFILE_MEDIA',
            'EVENT_MEDIA', 'PRODUCT_MEDIA'
        ) then 'PENDING' else 'NOT_REQUIRED' end,
        actor
    )
    returning * into a;

    path_value := public.m05_build_storage_path(
        purpose_value, user_value, p_owner_organization_id,
        p_resource_type, p_resource_id, a.id, p_safe_filename
    );
    if nullif(trim(coalesce(p_storage_path, '')), '') is not null
       and trim(p_storage_path) <> path_value then
        raise exception 'CLIENT_STORAGE_PATH_DENIED';
    end if;

    insert into public.file_asset_versions (
        asset_id, storage_bucket, storage_path, original_filename, safe_filename,
        declared_mime_type, size_bytes, status, created_by
    )
    values (
        a.id, bucket_value, path_value, left(trim(p_original_filename), 255),
        trim(p_safe_filename), mime_value, p_size_bytes, 'PENDING', actor
    )
    returning * into v;

    insert into public.file_upload_sessions (
        asset_id, version_id, state, expires_at, created_by
    )
    values (
        a.id, v.id, 'CREATED', timezone('utc', now()) + interval '1 hour', actor
    )
    returning * into s;

    if nullif(trim(coalesce(p_resource_id, '')), '') is not null then
        insert into public.file_asset_links (
            asset_id, resource_type, resource_id, relation_type,
            is_primary, sort_order, created_by
        )
        values (
            a.id, upper(trim(p_resource_type)), trim(p_resource_id),
            case purpose_value
                when 'MODERATION_EVIDENCE' then 'EVIDENCE'
                when 'ORGANIZATION_DOCUMENT' then 'DOCUMENT'
                when 'ORGANIZATION_VERIFICATION_DOCUMENT' then 'DOCUMENT'
                when 'SUPPORT_ATTACHMENT' then 'ATTACHMENT'
                when 'MESSAGE_ATTACHMENT' then 'ATTACHMENT'
                when 'USER_COVER' then 'COVER'
                when 'ORGANIZATION_COVER' then 'COVER'
                when 'PET_GALLERY' then 'GALLERY'
                else 'PRIMARY'
            end,
            purpose_value not in (
                'PET_GALLERY', 'POST_MEDIA', 'ADOPTION_MEDIA', 'LOST_FOUND_MEDIA',
                'SERVICE_PROFILE_MEDIA', 'SUPPORT_ATTACHMENT', 'MODERATION_EVIDENCE',
                'MESSAGE_ATTACHMENT', 'EVENT_MEDIA', 'PRODUCT_MEDIA',
                'ORGANIZATION_DOCUMENT', 'ORGANIZATION_VERIFICATION_DOCUMENT'
            ),
            0,
            actor
        );
    end if;

    return jsonb_build_object(
        'session', to_jsonb(s),
        'asset', to_jsonb(a),
        'version', to_jsonb(v)
    );
end;
$$;

create or replace function public.complete_file_upload(p_session_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := public.m05_require_active_actor();
    s public.file_upload_sessions;
    a public.file_assets;
    v public.file_asset_versions;
begin
    select * into s from public.file_upload_sessions where id = p_session_id for update;
    if s.id is null or (s.created_by <> actor and not public.m05_can_write_asset(s.asset_id)) then
        raise exception 'FORBIDDEN';
    end if;
    if s.state = 'COMPLETED' then
        select * into a from public.file_assets where id = s.asset_id;
        select * into v from public.file_asset_versions where id = s.version_id;
        return jsonb_build_object('session', to_jsonb(s), 'asset', to_jsonb(a), 'version', to_jsonb(v));
    end if;
    if s.state in ('FAILED', 'CANCELLED', 'EXPIRED')
       or (s.expires_at is not null and s.expires_at <= now()) then
        raise exception 'UPLOAD_SESSION_INVALID';
    end if;

    update public.file_asset_versions
    set status = 'READY'
    where id = s.version_id
    returning * into v;
    update public.file_assets
    set status = 'READY',
        processing_status = case
            when processing_status = 'PENDING' then 'READY'
            else processing_status
        end,
        current_version_id = s.version_id,
        updated_at = timezone('utc', now())
    where id = s.asset_id
    returning * into a;
    update public.file_upload_sessions
    set state = 'COMPLETED', progress_percent = 100,
        updated_at = timezone('utc', now())
    where id = s.id
    returning * into s;
    perform public.m05_audit(a.id, 'UPLOAD_COMPLETE', 'SUCCESS');
    return jsonb_build_object('session', to_jsonb(s), 'asset', to_jsonb(a), 'version', to_jsonb(v));
end;
$$;

create or replace function public.fail_file_upload(
    p_session_id uuid,
    p_failure_code text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := public.m05_require_active_actor();
    s public.file_upload_sessions;
begin
    select * into s from public.file_upload_sessions where id = p_session_id for update;
    if s.id is null or (s.created_by <> actor and not public.m05_can_write_asset(s.asset_id)) then
        raise exception 'FORBIDDEN';
    end if;
    update public.file_upload_sessions
    set state = 'FAILED',
        failure_code = left(coalesce(nullif(trim(p_failure_code), ''), 'UNKNOWN'), 100),
        updated_at = timezone('utc', now())
    where id = s.id
    returning * into s;
    update public.file_asset_versions set status = 'FAILED' where id = s.version_id;
    update public.file_assets
    set status = 'FAILED', processing_status = 'FAILED',
        updated_at = timezone('utc', now())
    where id = s.asset_id and status <> 'DELETED';
    perform public.m05_audit(s.asset_id, 'UPLOAD_FAIL', 'FAILED', null, null,
        jsonb_build_object('failure_code', s.failure_code));
    return to_jsonb(s);
end;
$$;

create or replace function public.cancel_file_upload(p_session_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := public.m05_require_active_actor();
    s public.file_upload_sessions;
begin
    select * into s from public.file_upload_sessions where id = p_session_id for update;
    if s.id is null or (s.created_by <> actor and not public.m05_can_write_asset(s.asset_id)) then
        raise exception 'FORBIDDEN';
    end if;
    if s.state not in ('COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED') then
        update public.file_upload_sessions
        set state = 'CANCELLED', updated_at = timezone('utc', now())
        where id = s.id returning * into s;
        update public.file_asset_versions set status = 'FAILED' where id = s.version_id;
        update public.file_assets
        set status = 'FAILED', processing_status = 'FAILED',
            updated_at = timezone('utc', now())
        where id = s.asset_id and status <> 'DELETED';
    end if;
    return to_jsonb(s);
end;
$$;

create or replace function public.update_file_upload_progress(
    p_session_id uuid,
    p_progress integer
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    actor uuid := public.m05_require_active_actor();
    s public.file_upload_sessions;
begin
    if p_progress not between 0 and 100 then
        raise exception 'VALIDATION';
    end if;
    select * into s from public.file_upload_sessions where id = p_session_id for update;
    if s.id is null or s.created_by <> actor then
        raise exception 'FORBIDDEN';
    end if;
    if s.state in ('COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED') then
        return to_jsonb(s);
    end if;
    update public.file_upload_sessions
    set progress_percent = greatest(progress_percent, p_progress),
        state = case when p_progress > 0 then 'UPLOADING' else state end,
        updated_at = timezone('utc', now())
    where id = s.id
    returning * into s;
    return to_jsonb(s);
end;
$$;

create or replace function public.resolve_public_file_asset(p_asset_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    a public.file_assets;
    v public.file_asset_versions;
begin
    select * into a
    from public.file_assets
    where id = p_asset_id
      and visibility = 'PUBLIC'
      and status = 'READY'
      and deleted_at is null;
    if a.id is null then
        raise exception 'NOT_FOUND';
    end if;
    select * into v
    from public.file_asset_versions
    where id = a.current_version_id
      and status = 'READY'
      and storage_bucket in ('public-media', 'profile-avatars', 'organization-media');
    if v.id is null then
        raise exception 'NOT_PUBLICLY_RESOLVABLE';
    end if;
    return jsonb_build_object(
        'asset', to_jsonb(a),
        'bucket', v.storage_bucket,
        'path', v.storage_path,
        'version_id', v.id
    );
end;
$$;

create or replace function public.request_file_signed_url(
    p_asset_id uuid,
    p_ttl_class text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    ttl_class text := upper(trim(coalesce(p_ttl_class, '')));
    expires_seconds integer;
    a public.file_assets;
    v public.file_asset_versions;
begin
    perform public.m05_require_active_actor();
    select * into a from public.file_assets where id = p_asset_id;
    if a.id is null or not public.m05_can_read_asset(a) then
        if a.id is not null then
            perform public.m05_audit(p_asset_id, 'SIGNED_URL', 'FORBIDDEN');
        end if;
        raise exception 'FORBIDDEN';
    end if;
    if ttl_class = 'SENSITIVE_SHORT' then
        expires_seconds := 600;
    elsif ttl_class = 'STANDARD_PRIVATE' then
        expires_seconds := 3600;
    else
        raise exception 'TTL_CLASS_INVALID';
    end if;
    if public.m05_is_sensitive_purpose(a.purpose) then
        expires_seconds := least(expires_seconds, 600);
    end if;
    select * into v from public.file_asset_versions
    where id = a.current_version_id and status = 'READY';
    if v.id is null then
        raise exception 'VERSION_NOT_READY';
    end if;
    perform public.m05_audit(
        a.id, 'SIGNED_URL', 'SUCCESS', 'TTL_CLASS', ttl_class,
        jsonb_build_object('expires_in_seconds', expires_seconds)
    );
    -- La firma real la crea Storage; este RPC nunca persiste ni devuelve tokens.
    return jsonb_build_object(
        'bucket', v.storage_bucket,
        'path', v.storage_path,
        'expires_in_seconds', expires_seconds,
        'asset_id', a.id
    );
end;
$$;

create or replace function public.place_file_legal_hold(p_asset_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    a public.file_assets;
    allowed boolean;
begin
    perform public.m05_require_active_actor();
    select * into a from public.file_assets where id = p_asset_id for update;
    select legal_hold_allowed into allowed
    from public.file_retention_policies where purpose = a.purpose;
    if a.id is null
       or not coalesce(allowed, false)
       or not (
           public.has_permission('audit.view')
           or public.m05_staff_can_access_purpose(a.purpose)
       ) then
        raise exception 'FORBIDDEN';
    end if;
    update public.file_assets
    set legal_hold = true, updated_at = timezone('utc', now())
    where id = p_asset_id returning * into a;
    perform public.m05_audit(p_asset_id, 'LEGAL_HOLD_PLACE', 'SUCCESS');
    return to_jsonb(a);
end;
$$;

create or replace function public.release_file_legal_hold(p_asset_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    a public.file_assets;
begin
    perform public.m05_require_active_actor();
    select * into a from public.file_assets where id = p_asset_id for update;
    if a.id is null
       or not (
           public.has_permission('audit.view')
           or public.m05_staff_can_access_purpose(a.purpose)
       ) then
        raise exception 'FORBIDDEN';
    end if;
    update public.file_assets
    set legal_hold = false, updated_at = timezone('utc', now())
    where id = p_asset_id returning * into a;
    perform public.m05_audit(p_asset_id, 'LEGAL_HOLD_RELEASE', 'SUCCESS');
    return to_jsonb(a);
end;
$$;

create or replace function public.can_physically_delete_file_asset(p_asset_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    a public.file_assets;
    active_links integer;
    retain_days_value integer;
    allowed boolean;
    reason text;
begin
    perform public.m05_require_active_actor();
    select * into a from public.file_assets where id = p_asset_id;
    if a.id is null or not public.m05_can_write_asset(a) then
        raise exception 'FORBIDDEN';
    end if;
    select count(*) into active_links
    from public.file_asset_links
    where asset_id = p_asset_id and deleted_at is null;
    select retain_days into retain_days_value
    from public.file_retention_policies where purpose = a.purpose;

    allowed :=
        a.status = 'DELETED'
        and not a.legal_hold
        and active_links = 0
        and (
            coalesce(a.retention_until, a.deleted_at) <= timezone('utc', now())
            or (
                retain_days_value is null
                and a.deleted_at <= timezone('utc', now())
            )
        );
    reason := case
        when a.status <> 'DELETED' then 'NOT_SOFT_DELETED'
        when a.legal_hold then 'LEGAL_HOLD'
        when active_links > 0 then 'ACTIVE_LINKS'
        when coalesce(a.retention_until, a.deleted_at) > timezone('utc', now())
            then 'RETENTION_ACTIVE'
        else 'ALLOWED'
    end;
    return jsonb_build_object(
        'asset_id', a.id,
        'can_physically_delete', allowed,
        'reason', reason,
        'active_links', active_links,
        'retention_until', a.retention_until
    );
end;
$$;

create or replace function public.list_file_access_audit(
    p_asset_id uuid,
    p_limit integer default 100
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    perform public.m05_require_active_actor();
    if not (
        public.has_permission('audit.view')
        or public.has_permission('moderation.view_sensitive')
    ) then
        raise exception 'FORBIDDEN';
    end if;
    return coalesce((
        select jsonb_agg(to_jsonb(x) order by x.created_at desc)
        from (
            select *
            from public.file_access_audit
            where asset_id = p_asset_id
            order by created_at desc
            limit least(greatest(coalesce(p_limit, 100), 1), 500)
        ) x
    ), '[]'::jsonb);
end;
$$;

-- RPC surface: nunca ejecutable por anon/public.
revoke all on function public.create_file_asset_draft(text, text, uuid, uuid, text) from public;
revoke all on function public.get_file_asset(uuid) from public;
revoke all on function public.list_file_assets_for_resource(text, text) from public;
revoke all on function public.link_file_asset(uuid, text, text, text, boolean, integer) from public;
revoke all on function public.unlink_file_asset(uuid, text, text, text) from public;
revoke all on function public.request_file_asset_delete(uuid) from public;
revoke all on function public.restore_file_asset(uuid) from public;
revoke all on function public.create_file_upload_session(
    text, text, uuid, uuid, text, text, text, text, text, bigint, text, text
) from public;
revoke all on function public.complete_file_upload(uuid) from public;
revoke all on function public.fail_file_upload(uuid, text) from public;
revoke all on function public.cancel_file_upload(uuid) from public;
revoke all on function public.update_file_upload_progress(uuid, integer) from public;
revoke all on function public.resolve_public_file_asset(uuid) from public;
revoke all on function public.request_file_signed_url(uuid, text) from public;
revoke all on function public.place_file_legal_hold(uuid) from public;
revoke all on function public.release_file_legal_hold(uuid) from public;
revoke all on function public.can_physically_delete_file_asset(uuid) from public;
revoke all on function public.list_file_access_audit(uuid, integer) from public;

grant execute on function public.create_file_asset_draft(text, text, uuid, uuid, text) to authenticated;
grant execute on function public.get_file_asset(uuid) to authenticated;
grant execute on function public.list_file_assets_for_resource(text, text) to authenticated;
grant execute on function public.link_file_asset(uuid, text, text, text, boolean, integer) to authenticated;
grant execute on function public.unlink_file_asset(uuid, text, text, text) to authenticated;
grant execute on function public.request_file_asset_delete(uuid) to authenticated;
grant execute on function public.restore_file_asset(uuid) to authenticated;
grant execute on function public.create_file_upload_session(
    text, text, uuid, uuid, text, text, text, text, text, bigint, text, text
) to authenticated;
grant execute on function public.complete_file_upload(uuid) to authenticated;
grant execute on function public.fail_file_upload(uuid, text) to authenticated;
grant execute on function public.cancel_file_upload(uuid) to authenticated;
grant execute on function public.update_file_upload_progress(uuid, integer) to authenticated;
grant execute on function public.resolve_public_file_asset(uuid) to authenticated;
grant execute on function public.request_file_signed_url(uuid, text) to authenticated;
grant execute on function public.place_file_legal_hold(uuid) to authenticated;
grant execute on function public.release_file_legal_hold(uuid) to authenticated;
grant execute on function public.can_physically_delete_file_asset(uuid) to authenticated;
grant execute on function public.list_file_access_audit(uuid, integer) to authenticated;

create or replace function public.transition_file_upload_session(p_session_id uuid, p_state text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  actor uuid := public.m05_require_active_actor();
  s public.file_upload_sessions;
  st text := upper(trim(p_state));
begin
  if st not in ('READY_TO_UPLOAD','UPLOADING','VALIDATING') then
    raise exception 'VALIDATION';
  end if;
  select * into s from public.file_upload_sessions where id = p_session_id for update;
  if s.id is null or s.created_by <> actor then raise exception 'FORBIDDEN'; end if;
  if s.state in ('COMPLETED','FAILED','CANCELLED','EXPIRED') then return to_jsonb(s); end if;
  if s.expires_at is not null and s.expires_at <= now() then
    update public.file_upload_sessions set state='EXPIRED', updated_at=timezone('utc',now()) where id=s.id returning * into s;
    raise exception 'UPLOAD_SESSION_EXPIRED';
  end if;
  update public.file_upload_sessions set state=st, updated_at=timezone('utc',now()) where id=s.id returning * into s;
  return to_jsonb(s);
end;
$$;
revoke all on function public.transition_file_upload_session(uuid, text) from public;
grant execute on function public.transition_file_upload_session(uuid, text) to authenticated;

-- Fin M05 Etapa 3. No incluye Edge Functions ni afirma validación en producción.
