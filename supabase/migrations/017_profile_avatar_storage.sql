-- M02 Etapa 3 — Storage de avatares con ownership por path.
-- Path oficial: users/{auth.uid()}/avatar/{filename}
-- Bucket dedicado profile-avatars (privado).

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'profile-avatars',
    'profile-avatars',
    false,
    5242880,
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create or replace function public.storage_avatar_is_owner(object_name text)
returns boolean
language sql
stable
as $$
    select
        auth.uid() is not null
        and object_name like ('users/' || auth.uid()::text || '/avatar/%')
        and object_name not like '%..%';
$$;

drop policy if exists profile_avatars_select on storage.objects;
create policy profile_avatars_select
    on storage.objects for select to authenticated
    using (
        bucket_id = 'profile-avatars'
        and (
            public.storage_avatar_is_owner(name)
            or exists (
                select 1
                from public.users u
                left join public.user_privacy_settings p on p.user_id = u.id
                where u.avatar_path = name
                  and u.onboarding_status = 'COMPLETED'
                  and u.account_status = 'ACTIVE'
                  and (
                      auth.uid() = u.id
                      or coalesce(p.profile_visibility, 'PRIVATE') = 'PUBLIC'
                      or (
                          coalesce(p.profile_visibility, 'PRIVATE') = 'FRIENDS'
                          and public.are_accepted_friends(auth.uid(), u.id)
                      )
                  )
            )
        )
    );

drop policy if exists profile_avatars_insert on storage.objects;
create policy profile_avatars_insert
    on storage.objects for insert to authenticated
    with check (
        bucket_id = 'profile-avatars'
        and public.storage_avatar_is_owner(name)
    );

drop policy if exists profile_avatars_update on storage.objects;
create policy profile_avatars_update
    on storage.objects for update to authenticated
    using (
        bucket_id = 'profile-avatars'
        and public.storage_avatar_is_owner(name)
    )
    with check (
        bucket_id = 'profile-avatars'
        and public.storage_avatar_is_owner(name)
    );

drop policy if exists profile_avatars_delete on storage.objects;
create policy profile_avatars_delete
    on storage.objects for delete to authenticated
    using (
        bucket_id = 'profile-avatars'
        and public.storage_avatar_is_owner(name)
    );
