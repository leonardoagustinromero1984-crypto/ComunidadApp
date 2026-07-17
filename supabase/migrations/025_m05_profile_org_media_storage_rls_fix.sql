-- M05 Etapa 5 — Corrección bloqueante de Storage RLS para paths M05
-- en buckets preexistentes profile-avatars y organization-media.
--
-- Defecto (024): m05_build_storage_path genera
--   users/{uid}/avatars|{covers}/{assetId}/{file}
--   organizations/{orgId}/logo|cover/{assetId}/{file}
-- pero las policies legacy (017/019) solo permiten
--   users/{uid}/avatar/{file}
--   organizations/{orgId}/logo|cover/{file}
-- Resultado: create_file_upload_session OK + storage.upload DENEGADO.
--
-- Alcance mínimo: policies ADICIONALES session-gated / asset-gated.
-- No edita 024. No toca leover. No recrea buckets. No migra objetos legacy.
-- PENDIENTE DE VALIDACIÓN REMOTA.

-- =============================================================================
-- Helpers de path M05 (no reemplazan helpers legacy)
-- =============================================================================

create or replace function public.m05_profile_avatars_path_valid(object_name text)
returns boolean
language sql
immutable
security definer
set search_path = public
as $$
    select
        object_name is not null
        and object_name not like '%..%'
        and object_name not like '%://%'
        and object_name ~* (
            '^users/'
            || '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
            || '(avatars|covers)/'
            || '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
            || '[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
        );
$$;

create or replace function public.m05_organization_media_path_valid(object_name text)
returns boolean
language sql
immutable
security definer
set search_path = public
as $$
    select
        object_name is not null
        and object_name not like '%..%'
        and object_name not like '%://%'
        and object_name ~* (
            '^organizations/'
            || '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
            || '(logo|cover)/'
            || '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
            || '[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
        );
$$;

create or replace function public.m05_storage_session_allows_write(
    p_bucket text,
    p_path text
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if auth.uid() is null
       or p_bucket is null
       or p_path is null
       or p_path like '%..%'
       or p_bucket = 'leover' then
        return false;
    end if;
    return exists (
        select 1
        from public.file_asset_versions v
        join public.file_upload_sessions s on s.version_id = v.id
        where v.storage_bucket = p_bucket
          and v.storage_path = p_path
          and s.created_by = auth.uid()
          and s.state in ('READY_TO_UPLOAD', 'UPLOADING', 'CREATED', 'VALIDATING')
          and (s.expires_at is null or s.expires_at > now())
    );
exception
    when others then
        return false;
end;
$$;

create or replace function public.m05_storage_can_read_object(
    p_bucket text,
    p_path text
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if auth.uid() is null or p_bucket is null or p_path is null then
        return false;
    end if;
    return exists (
        select 1
        from public.file_asset_versions v
        where v.storage_bucket = p_bucket
          and v.storage_path = p_path
          and public.m05_can_read_asset(v.asset_id)
    );
exception
    when others then
        return false;
end;
$$;

create or replace function public.m05_storage_can_write_object(
    p_bucket text,
    p_path text
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if auth.uid() is null or p_bucket is null or p_path is null then
        return false;
    end if;
    return exists (
        select 1
        from public.file_asset_versions v
        where v.storage_bucket = p_bucket
          and v.storage_path = p_path
          and public.m05_can_write_asset(v.asset_id)
    );
exception
    when others then
        return false;
end;
$$;

revoke all on function public.m05_profile_avatars_path_valid(text) from public;
revoke all on function public.m05_organization_media_path_valid(text) from public;
revoke all on function public.m05_storage_session_allows_write(text, text) from public;
revoke all on function public.m05_storage_can_read_object(text, text) from public;
revoke all on function public.m05_storage_can_write_object(text, text) from public;

grant execute on function public.m05_profile_avatars_path_valid(text) to authenticated;
grant execute on function public.m05_organization_media_path_valid(text) to authenticated;
grant execute on function public.m05_storage_session_allows_write(text, text) to authenticated;
grant execute on function public.m05_storage_can_read_object(text, text) to authenticated;
grant execute on function public.m05_storage_can_write_object(text, text) to authenticated;

-- =============================================================================
-- profile-avatars: policies M05 adicionales (legacy 017 intactas)
-- =============================================================================

drop policy if exists m05_profile_avatars_select on storage.objects;
create policy m05_profile_avatars_select
    on storage.objects for select to authenticated
    using (
        bucket_id = 'profile-avatars'
        and public.m05_profile_avatars_path_valid(name)
        and public.m05_storage_can_read_object(bucket_id, name)
    );

drop policy if exists m05_profile_avatars_insert on storage.objects;
create policy m05_profile_avatars_insert
    on storage.objects for insert to authenticated
    with check (
        bucket_id = 'profile-avatars'
        and public.m05_profile_avatars_path_valid(name)
        and public.m05_storage_session_allows_write(bucket_id, name)
    );

drop policy if exists m05_profile_avatars_update on storage.objects;
create policy m05_profile_avatars_update
    on storage.objects for update to authenticated
    using (
        bucket_id = 'profile-avatars'
        and public.m05_profile_avatars_path_valid(name)
        and (
            public.m05_storage_session_allows_write(bucket_id, name)
            or public.m05_storage_can_write_object(bucket_id, name)
        )
    )
    with check (
        bucket_id = 'profile-avatars'
        and public.m05_profile_avatars_path_valid(name)
        and (
            public.m05_storage_session_allows_write(bucket_id, name)
            or public.m05_storage_can_write_object(bucket_id, name)
        )
    );

drop policy if exists m05_profile_avatars_delete on storage.objects;
create policy m05_profile_avatars_delete
    on storage.objects for delete to authenticated
    using (
        bucket_id = 'profile-avatars'
        and public.m05_profile_avatars_path_valid(name)
        and public.m05_storage_can_write_object(bucket_id, name)
    );

-- =============================================================================
-- organization-media: policies M05 adicionales (legacy 019 intactas)
-- =============================================================================

drop policy if exists m05_organization_media_select on storage.objects;
create policy m05_organization_media_select
    on storage.objects for select to authenticated
    using (
        bucket_id = 'organization-media'
        and public.m05_organization_media_path_valid(name)
        and public.m05_storage_can_read_object(bucket_id, name)
    );

drop policy if exists m05_organization_media_insert on storage.objects;
create policy m05_organization_media_insert
    on storage.objects for insert to authenticated
    with check (
        bucket_id = 'organization-media'
        and public.m05_organization_media_path_valid(name)
        and public.m05_storage_session_allows_write(bucket_id, name)
    );

drop policy if exists m05_organization_media_update on storage.objects;
create policy m05_organization_media_update
    on storage.objects for update to authenticated
    using (
        bucket_id = 'organization-media'
        and public.m05_organization_media_path_valid(name)
        and (
            public.m05_storage_session_allows_write(bucket_id, name)
            or public.m05_storage_can_write_object(bucket_id, name)
        )
    )
    with check (
        bucket_id = 'organization-media'
        and public.m05_organization_media_path_valid(name)
        and (
            public.m05_storage_session_allows_write(bucket_id, name)
            or public.m05_storage_can_write_object(bucket_id, name)
        )
    );

drop policy if exists m05_organization_media_delete on storage.objects;
create policy m05_organization_media_delete
    on storage.objects for delete to authenticated
    using (
        bucket_id = 'organization-media'
        and public.m05_organization_media_path_valid(name)
        and public.m05_storage_can_write_object(bucket_id, name)
    );

comment on function public.m05_profile_avatars_path_valid(text) is
    'M05 Etapa 5: valida paths tipados users/{uid}/avatars|covers/{assetId}/{file} para profile-avatars.';
comment on function public.m05_organization_media_path_valid(text) is
    'M05 Etapa 5: valida paths tipados organizations/{orgId}/logo|cover/{assetId}/{file} para organization-media.';

-- Fin 025. No afirma validación remota. No toca leover ni 001–024.
