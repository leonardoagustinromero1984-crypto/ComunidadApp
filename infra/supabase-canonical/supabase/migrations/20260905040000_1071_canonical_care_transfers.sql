-- 1071: canonical care transfers ("Bajo el cuidado de").
-- Does not edit 1054/1007/1049. Current custodian actor controls.
-- Original creator remains historical. No hard delete. Same pet / VitaCora.

-- ---------------------------------------------------------------------------
-- Org permission compatible with permission_codes / organization_role_permissions
-- ---------------------------------------------------------------------------
insert into public.permission_codes (code, scope, description) values
  ('org.pets.transfer', 'ORG', 'Transfer pet care on behalf of the organization')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_code)
select rp.role_id, 'org.pets.transfer'
from public.organization_role_permissions rp
where rp.permission_code = 'org.pets.manage'
  and not exists (
    select 1
    from public.organization_role_permissions x
    where x.role_id = rp.role_id
      and x.permission_code = 'org.pets.transfer'
  );

-- ---------------------------------------------------------------------------
-- Current custodian on pets (PERSON | ORGANIZATION, XOR, no unsafe polymorphic FK)
-- ---------------------------------------------------------------------------
alter table public.pets
  add column if not exists current_custodian_kind text;

alter table public.pets
  add column if not exists current_custodian_person_id uuid references public.persons(user_id);

alter table public.pets
  add column if not exists current_custodian_organization_id uuid references public.organizations(id);

-- Legacy: preserve the real person. Do not invent historical organizations.
update public.pets p
set current_custodian_kind = 'PERSON',
    current_custodian_person_id = coalesce((
      select l.holder_person_id
      from public.pet_responsibility_links l
      where l.pet_id = p.id
        and l.status = 'ACTIVE'
        and l.holder_kind = 'PERSON'
        and l.role = 'OWNER'
        and l.holder_person_id = p.created_by_user_id
      limit 1
    ), (
      select l.holder_person_id
      from public.pet_responsibility_links l
      where l.pet_id = p.id
        and l.status = 'ACTIVE'
        and l.holder_kind = 'PERSON'
        and l.role = 'OWNER'
      order by l.valid_from
      limit 1
    ), p.created_by_user_id),
    current_custodian_organization_id = null
where p.current_custodian_kind is null
  and exists (
    select 1
    from public.pet_responsibility_links l
    where l.pet_id = p.id
      and l.status = 'ACTIVE'
      and l.holder_kind = 'PERSON'
      and l.role = 'OWNER'
  );

update public.pets p
set current_custodian_kind = 'ORGANIZATION',
    current_custodian_organization_id = (
      select l.holder_organization_id
      from public.pet_responsibility_links l
      where l.pet_id = p.id
        and l.status = 'ACTIVE'
        and l.holder_kind = 'ORGANIZATION'
        and l.role = 'RESPONSIBLE'
      limit 1
    ),
    current_custodian_person_id = null
where p.current_custodian_kind is null
  and exists (
    select 1
    from public.pet_responsibility_links l
    where l.pet_id = p.id
      and l.status = 'ACTIVE'
      and l.holder_kind = 'ORGANIZATION'
      and l.role = 'RESPONSIBLE'
  );

update public.pets
set current_custodian_kind = 'PERSON',
    current_custodian_person_id = created_by_user_id,
    current_custodian_organization_id = null
where current_custodian_kind is null;

alter table public.pets
  alter column current_custodian_kind set default 'PERSON';

alter table public.pets
  alter column current_custodian_kind set not null;

alter table public.pets
  drop constraint if exists pets_current_custodian_kind_check;

alter table public.pets
  add constraint pets_current_custodian_kind_check
  check (current_custodian_kind in ('PERSON', 'ORGANIZATION'));

alter table public.pets
  drop constraint if exists pets_current_custodian_xor;

alter table public.pets
  add constraint pets_current_custodian_xor check (
    public.holder_xor_ok(
      current_custodian_kind,
      current_custodian_person_id,
      current_custodian_organization_id
    )
  );

create index if not exists pets_current_custodian_person_idx
  on public.pets (current_custodian_person_id);

create index if not exists pets_current_custodian_org_idx
  on public.pets (current_custodian_organization_id);

-- ---------------------------------------------------------------------------
-- Care stages (media share applies to the closed stage, never deletes files)
-- ---------------------------------------------------------------------------
create table if not exists public.pet_care_stages (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  actor_kind text not null check (actor_kind in ('PERSON', 'ORGANIZATION')),
  actor_person_id uuid null references public.persons(user_id),
  actor_organization_id uuid null references public.organizations(id),
  opened_at timestamptz not null default timezone('utc', now()),
  closed_at timestamptz null,
  share_personal_media boolean null,
  opened_by_user_id uuid null references public.persons(user_id),
  closed_by_user_id uuid null references public.persons(user_id),
  transfer_id uuid null,
  constraint pet_care_stages_actor_xor check (
    public.holder_xor_ok(actor_kind, actor_person_id, actor_organization_id)
  )
);

create unique index if not exists pet_care_stages_one_open_uidx
  on public.pet_care_stages (pet_id)
  where closed_at is null;

create index if not exists pet_care_stages_pet_idx
  on public.pet_care_stages (pet_id, opened_at);

-- ---------------------------------------------------------------------------
-- Transfer requests. Custody does not change until ACCEPTED.
-- ---------------------------------------------------------------------------
create table if not exists public.pet_care_transfers (
  id uuid primary key default gen_random_uuid(),
  pet_id uuid not null references public.pets(id) on delete cascade,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
  source_kind text not null check (source_kind in ('PERSON', 'ORGANIZATION')),
  source_person_id uuid null references public.persons(user_id),
  source_organization_id uuid null references public.organizations(id),
  target_kind text not null check (target_kind in ('PERSON', 'ORGANIZATION')),
  target_person_id uuid null references public.persons(user_id),
  target_organization_id uuid null references public.organizations(id),
  initiated_by_user_id uuid not null references public.persons(user_id),
  decided_by_user_id uuid null references public.persons(user_id),
  share_personal_media boolean not null default false,
  created_at timestamptz not null default timezone('utc', now()),
  decided_at timestamptz null,
  constraint pet_care_transfers_source_xor check (
    public.holder_xor_ok(source_kind, source_person_id, source_organization_id)
  ),
  constraint pet_care_transfers_target_xor check (
    public.holder_xor_ok(target_kind, target_person_id, target_organization_id)
  )
);

create unique index if not exists pet_care_transfers_one_pending_uidx
  on public.pet_care_transfers (pet_id)
  where status = 'PENDING';

create index if not exists pet_care_transfers_pet_idx
  on public.pet_care_transfers (pet_id, created_at desc);

create index if not exists pet_care_transfers_target_person_idx
  on public.pet_care_transfers (target_person_id, status);

create index if not exists pet_care_transfers_target_org_idx
  on public.pet_care_transfers (target_organization_id, status);

alter table public.pet_care_stages
  drop constraint if exists pet_care_stages_transfer_fk;

alter table public.pet_care_stages
  add constraint pet_care_stages_transfer_fk
  foreign key (transfer_id) references public.pet_care_transfers(id);

alter table public.pet_care_transfers enable row level security;
alter table public.pet_care_stages enable row level security;

revoke all on table public.pet_care_transfers from public, anon, authenticated;
revoke all on table public.pet_care_stages from public, anon, authenticated;

-- Backfill one open stage per pet from current custodian.
insert into public.pet_care_stages (
  pet_id, actor_kind, actor_person_id, actor_organization_id,
  opened_at, opened_by_user_id
)
select
  p.id,
  p.current_custodian_kind,
  p.current_custodian_person_id,
  p.current_custodian_organization_id,
  p.created_at,
  p.created_by_user_id
from public.pets p
where not exists (
  select 1 from public.pet_care_stages s
  where s.pet_id = p.id and s.closed_at is null
);

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
create or replace function public._canon_actor_display_name(
  p_kind text,
  p_person_id uuid,
  p_organization_id uuid
)
returns text
language sql
stable
security definer
set search_path = public
as $$
  select case
    when p_kind = 'ORGANIZATION' then (
      select nullif(btrim(o.name), '') from public.organizations o where o.id = p_organization_id
    )
    else (
      select nullif(btrim(pe.display_name), '') from public.persons pe where pe.user_id = p_person_id
    )
  end;
$$;

create or replace function public._acl_is_current_custodian_operator(
  p_user_id uuid,
  p_pet_id uuid,
  p_org_permission text
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.pets p
    where p.id = p_pet_id
      and (
        (
          p.current_custodian_kind = 'PERSON'
          and p.current_custodian_person_id = p_user_id
        )
        or (
          p.current_custodian_kind = 'ORGANIZATION'
          and p.current_custodian_organization_id is not null
          and public._acl_org_permission(
            p_user_id,
            p.current_custodian_organization_id,
            p_org_permission
          )
        )
      )
  );
$$;

create or replace function public._acl_can_operate_care_actor(
  p_user_id uuid,
  p_kind text,
  p_person_id uuid,
  p_organization_id uuid,
  p_org_permission text
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    p_user_id is not null
    and public.holder_xor_ok(p_kind, p_person_id, p_organization_id)
    and (
      (p_kind = 'PERSON' and p_person_id = p_user_id)
      or (
        p_kind = 'ORGANIZATION'
        and p_organization_id is not null
        and public._acl_org_permission(p_user_id, p_organization_id, p_org_permission)
      )
    );
$$;

create or replace function public._acl_pet_permission(p_user_id uuid, p_pet_id uuid, p_code text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public._acl_pet_holder(p_user_id, p_pet_id)
     and (
       (
         p_code in (
           'responsibility.manage',
           'privacy.manage',
           'services.authorize',
           'vitacora.share'
         )
         and public._acl_is_current_custodian_operator(p_user_id, p_pet_id, 'org.pets.manage')
       )
       or (
         p_code not in (
           'responsibility.manage',
           'privacy.manage',
           'services.authorize',
           'vitacora.share'
         )
         and (
           exists (
             select 1 from public.pet_permission_grants g
             where g.pet_id = p_pet_id
               and g.revoked_at is null
               and g.permission_code = p_code
               and (g.subject_person_id = p_user_id or (
                 g.subject_organization_id is not null
                 and public._acl_org_member(p_user_id, g.subject_organization_id)
               ))
           )
           or exists (
             select 1 from public.pet_responsibility_links l
             where l.pet_id = p_pet_id
               and l.status = 'ACTIVE'
               and l.holder_person_id = p_user_id
               and l.role = 'OWNER'
               and p_code in (
                 'pet.view',
                 'pet.edit',
                 'vitacora.view',
                 'vitacora.manage',
                 'health.manage_declared'
               )
           )
         )
       )
     );
$$;

create or replace function public._canon_media_linked_pet_id(p_asset public.media_assets)
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (
      select p.id
      from public.pets p
      where p.avatar_asset_id = p_asset.id
      limit 1
    ),
    (
      select m.pet_id
      from public.vitacora_moments m
      where m.asset_id = p_asset.id
      limit 1
    ),
    (
      select case
        when l.owner_table = 'pets' then l.owner_id
        when l.owner_table = 'vitacora_moments' then (
          select vm.pet_id from public.vitacora_moments vm where vm.id = l.owner_id
        )
        else null
      end
      from public.media_asset_links l
      where l.asset_id = p_asset.id
      order by l.created_at
      limit 1
    )
  );
$$;

create or replace function public._canon_media_is_health_or_professional(p_asset public.media_assets)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    p_asset.bucket = 'documents'
    or coalesce(p_asset.metadata->>'origin', '') in (
      'HEALTH', 'PROFESSIONAL', 'CLINICAL', 'VETERINARY', 'SANITARY'
    )
    or coalesce(p_asset.metadata->>'domain', '') in (
      'HEALTH', 'PROFESSIONAL', 'CLINICAL', 'VETERINARY', 'SANITARY'
    )
    or exists (
      select 1
      from public.media_asset_links l
      where l.asset_id = p_asset.id
        and (
          l.purpose in (
            'HEALTH', 'CLINICAL', 'PROFESSIONAL', 'MEDICAL', 'VET',
            'DOCUMENT', 'ORGANIZATION_DOCUMENT', 'SANITARY'
          )
          or l.owner_table in (
            'pet_declared_health_profiles',
            'pet_vaccinations',
            'pet_conditions',
            'vitacora_update_proposals',
            'vitacora_access_grants'
          )
        )
    );
$$;

create or replace function public._canon_media_is_personal_social(p_asset public.media_assets)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    not public._canon_media_is_health_or_professional(p_asset)
    and (
      p_asset.object_path ~* '/(gallery|posts|stories|reels)/'
      or exists (
        select 1
        from public.media_asset_links l
        where l.asset_id = p_asset.id
          and l.purpose in (
            'PET_GALLERY', 'POST_MEDIA', 'STORY_MEDIA', 'REEL_MEDIA',
            'SOCIAL', 'GALLERY', 'MEMORY'
          )
      )
      or exists (
        select 1
        from public.vitacora_moments m
        where m.asset_id = p_asset.id
          and m.kind in ('PHOTO', 'MEMORY', 'TRIP', 'MILESTONE')
      )
    );
$$;

create or replace function public._canon_personal_media_withheld_from(
  p_user_id uuid,
  p_asset public.media_assets
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_pet uuid;
  v_stage public.pet_care_stages%rowtype;
begin
  if p_user_id is null then
    return false;
  end if;
  if not public._canon_media_is_personal_social(p_asset) then
    return false;
  end if;
  v_pet := public._canon_media_linked_pet_id(p_asset);
  if v_pet is null then
    return false;
  end if;

  select * into v_stage
  from public.pet_care_stages s
  where s.pet_id = v_pet
    and s.closed_at is not null
    and s.share_personal_media is false
    and p_asset.created_at >= s.opened_at
    and p_asset.created_at <= s.closed_at
  order by s.closed_at desc
  limit 1;

  if not found then
    return false;
  end if;

  if v_stage.actor_kind = 'PERSON' and v_stage.actor_person_id = p_user_id then
    return false;
  end if;
  if v_stage.actor_kind = 'ORGANIZATION'
     and v_stage.actor_organization_id is not null
     and public._acl_org_member(p_user_id, v_stage.actor_organization_id) then
    return false;
  end if;
  return true;
end;
$$;

create or replace function public._acl_media_readable(p_user_id uuid, p_asset public.media_assets)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_pet uuid;
begin
  if p_asset.visibility = 'PUBLIC' then
    return true;
  end if;
  if p_user_id is null then
    return false;
  end if;
  if p_asset.owner_person_id = p_user_id then
    return true;
  end if;
  if public._acl_is_admin(p_user_id) then
    return true;
  end if;
  if p_asset.owner_kind = 'ORGANIZATION'
     and p_asset.owner_organization_id is not null
     and public._acl_org_member(p_user_id, p_asset.owner_organization_id) then
    return true;
  end if;
  if exists (
    select 1 from public.pets p
    where p.avatar_asset_id = p_asset.id
      and public._acl_pet_holder(p_user_id, p.id)
  ) then
    return true;
  end if;

  v_pet := public._canon_media_linked_pet_id(p_asset);
  if v_pet is null then
    return false;
  end if;
  if public._canon_personal_media_withheld_from(p_user_id, p_asset) then
    return false;
  end if;
  if public._acl_pet_holder(p_user_id, v_pet)
     or public._acl_pet_permission(p_user_id, v_pet, 'vitacora.view')
     or public._acl_pet_permission(p_user_id, v_pet, 'health.manage_declared') then
    return true;
  end if;
  return false;
end;
$$;

create or replace function public._canon_open_care_stage(
  p_pet_id uuid,
  p_kind text,
  p_person uuid,
  p_org uuid,
  p_actor uuid,
  p_transfer_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  if exists (
    select 1 from public.pet_care_stages
    where pet_id = p_pet_id and closed_at is null
  ) then
    select id into v_id
    from public.pet_care_stages
    where pet_id = p_pet_id and closed_at is null
    limit 1;
    return v_id;
  end if;
  insert into public.pet_care_stages (
    pet_id, actor_kind, actor_person_id, actor_organization_id,
    opened_by_user_id, transfer_id
  ) values (
    p_pet_id, p_kind, p_person, p_org, p_actor, p_transfer_id
  ) returning id into v_id;
  return v_id;
end;
$$;

create or replace function public._canon_apply_current_custodian(
  p_pet_id uuid,
  p_kind text,
  p_person uuid,
  p_org uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.pets
  set current_custodian_kind = p_kind,
      current_custodian_person_id = p_person,
      current_custodian_organization_id = p_org,
      management_context_kind = case
        when p_kind in ('PERSON', 'ORGANIZATION') then p_kind
        else management_context_kind
      end,
      management_context_id = case
        when p_kind = 'PERSON' then p_person::text
        when p_kind = 'ORGANIZATION' then p_org::text
        else management_context_id
      end
  where id = p_pet_id;
end;
$$;

create or replace function public._canon_grant_custodian_control(
  p_pet_id uuid,
  p_kind text,
  p_person uuid,
  p_org uuid,
  p_granted_by uuid
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link uuid;
begin
  if p_kind = 'PERSON' then
    insert into public.pet_responsibility_links (
      pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id
    ) values (p_pet_id, 'PERSON', p_person, 'OWNER', p_granted_by)
    returning id into v_link;
    insert into public.pet_permission_grants (
      pet_id, link_id, subject_person_id, permission_code, granted_by
    )
    select p_pet_id, v_link, p_person, code, p_granted_by
    from public.permission_codes
    where scope in ('PET', 'VITACORA');
  else
    insert into public.pet_responsibility_links (
      pet_id, holder_kind, holder_organization_id, role, granted_by_actor_user_id
    ) values (p_pet_id, 'ORGANIZATION', p_org, 'RESPONSIBLE', p_granted_by)
    returning id into v_link;
    insert into public.pet_permission_grants (
      pet_id, link_id, subject_organization_id, permission_code, granted_by
    )
    select p_pet_id, v_link, p_org, code, p_granted_by
    from public.permission_codes
    where scope in ('PET', 'VITACORA');
  end if;
  return v_link;
end;
$$;

create or replace function public._canon_revoke_current_care_holders(p_pet_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.pet_permission_grants g
  set revoked_at = timezone('utc', now())
  where g.pet_id = p_pet_id
    and g.revoked_at is null
    and exists (
      select 1
      from public.pet_responsibility_links l
      where l.id = g.link_id
        and l.pet_id = p_pet_id
        and l.status in ('ACTIVE', 'PENDING')
    );

  update public.pet_responsibility_links
  set status = 'ENDED',
      valid_until = timezone('utc', now())
  where pet_id = p_pet_id
    and status in ('ACTIVE', 'PENDING');
end;
$$;

create or replace function public._canon_pets_after_insert_care()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.current_custodian_kind is null
     or (new.current_custodian_person_id is null and new.current_custodian_organization_id is null) then
    new.current_custodian_kind := 'PERSON';
    new.current_custodian_person_id := new.created_by_user_id;
    new.current_custodian_organization_id := null;
  end if;
  return new;
end;
$$;

drop trigger if exists pets_before_insert_care on public.pets;
create trigger pets_before_insert_care
  before insert on public.pets
  for each row execute function public._canon_pets_after_insert_care();

create or replace function public._canon_pets_after_insert_open_stage()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public._canon_open_care_stage(
    new.id,
    new.current_custodian_kind,
    new.current_custodian_person_id,
    new.current_custodian_organization_id,
    new.created_by_user_id,
    null
  );
  return new;
end;
$$;

drop trigger if exists pets_after_insert_open_stage on public.pets;
create trigger pets_after_insert_open_stage
  after insert on public.pets
  for each row execute function public._canon_pets_after_insert_open_stage();

-- ---------------------------------------------------------------------------
-- Archive: current custodian (including transfer recipient). Soft archive only.
-- ---------------------------------------------------------------------------
create or replace function public.canon_archive_pet(p_pet_id uuid, p_reason text default null)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not exists (select 1 from public.pets where id = p_pet_id) then
    raise exception 'NOT_FOUND';
  end if;
  if not public._acl_is_current_custodian_operator(auth.uid(), p_pet_id, 'org.pets.manage') then
    raise exception 'FORBIDDEN';
  end if;

  update public.pets
    set lifecycle_status = 'ARCHIVED',
        archived_at = timezone('utc', now())
    where id = p_pet_id
      and lifecycle_status = 'ACTIVE';
  if not found then raise exception 'PET_NOT_ACTIVE'; end if;

  insert into public.pet_lifecycle_events (pet_id, from_status, to_status, actor_user_id, note)
  values (
    p_pet_id,
    'ACTIVE',
    'ARCHIVED',
    auth.uid(),
    nullif(btrim(coalesce(p_reason, '')), '')
  );
  return p_pet_id;
end;
$$;

create or replace function public.canon_end_pet_responsibility(p_link_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link public.pet_responsibility_links%rowtype;
  v_pet public.pets%rowtype;
  v_owners integer;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_link from public.pet_responsibility_links where id = p_link_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_link.status not in ('ACTIVE', 'PENDING') then return p_link_id; end if;
  if not public._acl_pet_permission(auth.uid(), v_link.pet_id, 'responsibility.manage') then
    raise exception 'FORBIDDEN';
  end if;

  select * into v_pet from public.pets where id = v_link.pet_id;
  if v_link.holder_kind = 'PERSON'
     and v_pet.current_custodian_kind = 'PERSON'
     and v_link.holder_person_id is not distinct from v_pet.current_custodian_person_id then
    raise exception 'CURRENT_CUSTODIAN_REQUIRED';
  end if;
  if v_link.holder_kind = 'ORGANIZATION'
     and v_pet.current_custodian_kind = 'ORGANIZATION'
     and v_link.holder_organization_id is not distinct from v_pet.current_custodian_organization_id then
    raise exception 'CURRENT_CUSTODIAN_REQUIRED';
  end if;

  if v_link.role = 'OWNER' and v_link.status = 'ACTIVE' then
    select count(*) into v_owners
    from public.pet_responsibility_links
    where pet_id = v_link.pet_id and status = 'ACTIVE' and role = 'OWNER';
    if v_owners <= 1 then
      raise exception 'LAST_OWNER_REQUIRED';
    end if;
  end if;

  update public.pet_responsibility_links
    set status = 'ENDED', valid_until = timezone('utc', now())
    where id = p_link_id;
  update public.pet_permission_grants
    set revoked_at = timezone('utc', now())
    where link_id = p_link_id and revoked_at is null;
  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type)
  values (v_link.pet_id, p_link_id, auth.uid(), 'ENDED');
  return p_link_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Transfer RPCs
-- ---------------------------------------------------------------------------
create or replace function public.canon_initiate_care_transfer(
  p_pet_id uuid,
  p_target_kind text,
  p_target_person_id uuid default null,
  p_target_organization_id uuid default null,
  p_share_personal_media boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pet public.pets%rowtype;
  v_kind text := upper(btrim(coalesce(p_target_kind, '')));
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_age_allows(auth.uid(), 'pet.transfer_responsibility') then
    raise exception 'AGE_CAPABILITY_DENIED';
  end if;
  select * into v_pet from public.pets where id = p_pet_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_pet.lifecycle_status <> 'ACTIVE' then raise exception 'PET_NOT_ACTIVE'; end if;
  if not public._acl_is_current_custodian_operator(auth.uid(), p_pet_id, 'org.pets.transfer') then
    raise exception 'FORBIDDEN';
  end if;
  if v_kind not in ('PERSON', 'ORGANIZATION') then
    raise exception 'VALIDATION';
  end if;
  if not public.holder_xor_ok(v_kind, p_target_person_id, p_target_organization_id) then
    raise exception 'PET_TRANSFER_DEST_XOR_REQUIRED';
  end if;
  if v_kind = 'PERSON' and not exists (select 1 from public.persons where user_id = p_target_person_id) then
    raise exception 'PET_TRANSFER_TARGET_INVALID';
  end if;
  if v_kind = 'ORGANIZATION' and not exists (
    select 1 from public.organizations
    where id = p_target_organization_id and lifecycle_status = 'ACTIVE'
  ) then
    raise exception 'PET_TRANSFER_TARGET_INVALID';
  end if;
  if (
    v_pet.current_custodian_kind = v_kind
    and v_pet.current_custodian_person_id is not distinct from p_target_person_id
    and v_pet.current_custodian_organization_id is not distinct from p_target_organization_id
  ) then
    raise exception 'PET_TRANSFER_SAME_ACTOR';
  end if;
  if exists (
    select 1 from public.pet_care_transfers
    where pet_id = p_pet_id and status = 'PENDING'
  ) then
    raise exception 'PET_TRANSFER_PENDING_EXISTS';
  end if;

  insert into public.pet_care_transfers (
    pet_id, status,
    source_kind, source_person_id, source_organization_id,
    target_kind, target_person_id, target_organization_id,
    initiated_by_user_id, share_personal_media
  ) values (
    p_pet_id, 'PENDING',
    v_pet.current_custodian_kind, v_pet.current_custodian_person_id, v_pet.current_custodian_organization_id,
    v_kind, p_target_person_id, p_target_organization_id,
    auth.uid(), coalesce(p_share_personal_media, false)
  ) returning id into v_id;

  insert into public.pet_responsibility_events (pet_id, actor_user_id, event_type, metadata)
  values (
    p_pet_id,
    auth.uid(),
    'CARE_TRANSFER_INITIATED',
    jsonb_build_object('transfer_id', v_id)
  );

  return public._canon_care_transfer_json(v_id);
end;
$$;

create or replace function public._canon_care_transfer_json(p_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', t.id,
    'pet_id', t.pet_id,
    'pet_name', p.name,
    'status', t.status,
    'source_kind', t.source_kind,
    'source_person_id', t.source_person_id,
    'source_organization_id', t.source_organization_id,
    'source_display_name', public._canon_actor_display_name(
      t.source_kind, t.source_person_id, t.source_organization_id
    ),
    'target_kind', t.target_kind,
    'target_person_id', t.target_person_id,
    'target_organization_id', t.target_organization_id,
    'target_display_name', public._canon_actor_display_name(
      t.target_kind, t.target_person_id, t.target_organization_id
    ),
    'initiated_by_user_id', t.initiated_by_user_id,
    'decided_by_user_id', t.decided_by_user_id,
    'share_personal_media', t.share_personal_media,
    'created_at', t.created_at,
    'decided_at', t.decided_at
  )
  from public.pet_care_transfers t
  join public.pets p on p.id = t.pet_id
  where t.id = p_id;
$$;

create or replace function public.canon_accept_care_transfer(p_transfer_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_tr public.pet_care_transfers%rowtype;
  v_pet public.pets%rowtype;
  v_link uuid;
  v_open public.pet_care_stages%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_transfer_id is null then raise exception 'VALIDATION'; end if;

  select * into v_tr
  from public.pet_care_transfers
  where id = p_transfer_id
  for update;
  if not found then raise exception 'NOT_FOUND'; end if;

  select * into v_pet
  from public.pets
  where id = v_tr.pet_id
  for update;
  if not found then raise exception 'NOT_FOUND'; end if;

  if v_tr.status = 'ACCEPTED' then
    return public._canon_care_transfer_json(v_tr.id);
  end if;
  if v_tr.status <> 'PENDING' then
    raise exception 'PET_TRANSFER_NOT_PENDING';
  end if;
  if not public._acl_can_operate_care_actor(
    auth.uid(), v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id, 'org.pets.transfer'
  ) then
    raise exception 'FORBIDDEN';
  end if;
  if v_pet.lifecycle_status <> 'ACTIVE' then
    raise exception 'PET_NOT_ACTIVE';
  end if;
  if v_pet.current_custodian_kind is distinct from v_tr.source_kind
     or v_pet.current_custodian_person_id is distinct from v_tr.source_person_id
     or v_pet.current_custodian_organization_id is distinct from v_tr.source_organization_id then
    raise exception 'PET_TRANSFER_SOURCE_STALE';
  end if;
  if (
    v_tr.target_kind = v_pet.current_custodian_kind
    and v_tr.target_person_id is not distinct from v_pet.current_custodian_person_id
    and v_tr.target_organization_id is not distinct from v_pet.current_custodian_organization_id
  ) then
    raise exception 'PET_TRANSFER_SAME_ACTOR';
  end if;
  if v_tr.target_kind = 'PERSON'
     and not exists (select 1 from public.persons where user_id = v_tr.target_person_id) then
    raise exception 'PET_TRANSFER_TARGET_INVALID';
  end if;
  if v_tr.target_kind = 'ORGANIZATION'
     and not exists (
       select 1 from public.organizations
       where id = v_tr.target_organization_id and lifecycle_status = 'ACTIVE'
     ) then
    raise exception 'PET_TRANSFER_TARGET_INVALID';
  end if;

  select * into v_open
  from public.pet_care_stages
  where pet_id = v_pet.id and closed_at is null
  for update;

  if found then
    update public.pet_care_stages
    set closed_at = timezone('utc', now()),
        share_personal_media = v_tr.share_personal_media,
        closed_by_user_id = auth.uid(),
        transfer_id = v_tr.id
    where id = v_open.id;
  else
    insert into public.pet_care_stages (
      pet_id, actor_kind, actor_person_id, actor_organization_id,
      opened_at, closed_at, share_personal_media,
      opened_by_user_id, closed_by_user_id, transfer_id
    ) values (
      v_pet.id, v_tr.source_kind, v_tr.source_person_id, v_tr.source_organization_id,
      v_pet.created_at, timezone('utc', now()), v_tr.share_personal_media,
      v_tr.initiated_by_user_id, auth.uid(), v_tr.id
    );
  end if;

  perform public._canon_revoke_current_care_holders(v_pet.id);
  perform public._canon_apply_current_custodian(
    v_pet.id, v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id
  );
  v_link := public._canon_grant_custodian_control(
    v_pet.id, v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id, auth.uid()
  );
  perform public._canon_open_care_stage(
    v_pet.id, v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id, auth.uid(), v_tr.id
  );

  update public.pet_care_transfers
  set status = 'ACCEPTED',
      decided_by_user_id = auth.uid(),
      decided_at = timezone('utc', now())
  where id = v_tr.id
    and status = 'PENDING';

  insert into public.pet_responsibility_events (pet_id, link_id, actor_user_id, event_type, metadata)
  values (
    v_pet.id,
    v_link,
    auth.uid(),
    'CARE_TRANSFERRED',
    jsonb_build_object(
      'transfer_id', v_tr.id,
      'source_kind', v_tr.source_kind,
      'target_kind', v_tr.target_kind,
      'share_personal_media', v_tr.share_personal_media,
      'performed_by', auth.uid()
    )
  );

  return public._canon_care_transfer_json(v_tr.id);
end;
$$;

create or replace function public.canon_reject_care_transfer(p_transfer_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_tr public.pet_care_transfers%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_tr from public.pet_care_transfers where id = p_transfer_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_tr.status <> 'PENDING' then raise exception 'PET_TRANSFER_NOT_PENDING'; end if;
  if not public._acl_can_operate_care_actor(
    auth.uid(), v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id, 'org.pets.transfer'
  ) then
    raise exception 'FORBIDDEN';
  end if;
  update public.pet_care_transfers
  set status = 'REJECTED',
      decided_by_user_id = auth.uid(),
      decided_at = timezone('utc', now())
  where id = v_tr.id and status = 'PENDING';
  return public._canon_care_transfer_json(v_tr.id);
end;
$$;

create or replace function public.canon_cancel_care_transfer(p_transfer_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_tr public.pet_care_transfers%rowtype;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_tr from public.pet_care_transfers where id = p_transfer_id for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  if v_tr.status <> 'PENDING' then raise exception 'PET_TRANSFER_NOT_PENDING'; end if;
  if not public._acl_is_current_custodian_operator(auth.uid(), v_tr.pet_id, 'org.pets.transfer') then
    raise exception 'FORBIDDEN';
  end if;
  update public.pet_care_transfers
  set status = 'CANCELLED',
      decided_by_user_id = auth.uid(),
      decided_at = timezone('utc', now())
  where id = v_tr.id and status = 'PENDING';
  return public._canon_care_transfer_json(v_tr.id);
end;
$$;

create or replace function public.canon_list_care_transfers(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_pet_holder(auth.uid(), p_pet_id)
     and not exists (
       select 1 from public.pet_care_transfers t
       where t.pet_id = p_pet_id
         and public._acl_can_operate_care_actor(
           auth.uid(), t.target_kind, t.target_person_id, t.target_organization_id, 'org.pets.transfer'
         )
     ) then
    raise exception 'FORBIDDEN';
  end if;
  return coalesce((
    select jsonb_agg(public._canon_care_transfer_json(t.id) order by t.created_at desc)
    from public.pet_care_transfers t
    where t.pet_id = p_pet_id
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_incoming_care_transfers()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(public._canon_care_transfer_json(t.id) order by t.created_at desc)
    from public.pet_care_transfers t
    where t.status = 'PENDING'
      and public._acl_can_operate_care_actor(
        auth.uid(), t.target_kind, t.target_person_id, t.target_organization_id, 'org.pets.transfer'
      )
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_pet_care_context(p_pet_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_pet public.pets%rowtype;
  v_incoming boolean := false;
  v_operator boolean := false;
  v_holder boolean := false;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_pet from public.pets where id = p_pet_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  v_holder := public._acl_pet_holder(auth.uid(), p_pet_id);
  v_operator := public._acl_is_current_custodian_operator(auth.uid(), p_pet_id, 'org.pets.manage');
  v_incoming := exists (
    select 1 from public.pet_care_transfers t
    where t.pet_id = p_pet_id
      and t.status = 'PENDING'
      and public._acl_can_operate_care_actor(
        auth.uid(), t.target_kind, t.target_person_id, t.target_organization_id, 'org.pets.transfer'
      )
  );
  if not v_holder and not v_incoming then
    raise exception 'FORBIDDEN';
  end if;
  return jsonb_build_object(
    'pet_id', v_pet.id,
    'pet_name', v_pet.name,
    'relation_code', case
      when public._acl_is_current_custodian_operator(auth.uid(), p_pet_id, 'org.pets.transfer') then 'CUSTODIAN'
      when v_incoming then 'TRANSFER_TARGET'
      when v_holder then 'RESPONSIBLE'
      else 'HOLDER'
    end,
    'original_creator_person_id', v_pet.created_by_user_id,
    'original_creator_display_name', public._canon_actor_display_name(
      'PERSON', v_pet.created_by_user_id, null
    ),
    'principal_person_id', v_pet.current_custodian_person_id,
    'principal_organization_id', v_pet.current_custodian_organization_id,
    'principal_display_name', public._canon_actor_display_name(
      v_pet.current_custodian_kind,
      v_pet.current_custodian_person_id,
      v_pet.current_custodian_organization_id
    ),
    'current_custodian_kind', v_pet.current_custodian_kind,
    'can_read', v_holder or v_incoming,
    'can_update', public._acl_can_edit_pet(auth.uid(), p_pet_id),
    'can_manage_health', public._acl_can_manage_declared_health(auth.uid(), p_pet_id),
    'can_manage_media', v_operator,
    'can_manage_responsibilities', public._acl_pet_permission(auth.uid(), p_pet_id, 'responsibility.manage'),
    'can_manage_authorizations', v_operator,
    'can_initiate_transfer', public._acl_is_current_custodian_operator(auth.uid(), p_pet_id, 'org.pets.transfer')
      and v_pet.lifecycle_status = 'ACTIVE',
    'can_accept_transfer', v_incoming,
    'can_cancel_transfer', public._acl_is_current_custodian_operator(auth.uid(), p_pet_id, 'org.pets.transfer'),
    'can_archive', public._acl_is_current_custodian_operator(auth.uid(), p_pet_id, 'org.pets.manage')
      and v_pet.lifecycle_status = 'ACTIVE',
    'can_restore', false,
    'can_mark_deceased', v_operator and v_pet.lifecycle_status = 'ACTIVE',
    'can_view_history', v_holder or v_incoming
  );
end;
$$;

create or replace function public.canon_search_care_transfer_targets(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_q text := btrim(coalesce(p_query, ''));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if char_length(v_q) < 2 then return '[]'::jsonb; end if;
  return coalesce((
    select jsonb_agg(row_data)
    from (
      select jsonb_build_object(
        'kind', 'PERSON',
        'id', pe.user_id,
        'display_name', pe.display_name,
        'subtitle', coalesce('@' || pe.username, '')
      ) as row_data
      from public.persons pe
      where pe.user_id <> auth.uid()
        and (
          pe.display_name ilike ('%' || v_q || '%')
          or coalesce(pe.username, '') ilike ('%' || v_q || '%')
        )
      order by pe.display_name
      limit 10
    ) people
  ), '[]'::jsonb) || coalesce((
    select jsonb_agg(row_data)
    from (
      select jsonb_build_object(
        'kind', 'ORGANIZATION',
        'id', o.id,
        'display_name', o.name,
        'subtitle', coalesce(o.primary_label, '')
      ) as row_data
      from public.organizations o
      where o.lifecycle_status = 'ACTIVE'
        and (
          o.name ilike ('%' || v_q || '%')
          or coalesce(o.slug, '') ilike ('%' || v_q || '%')
        )
      order by o.name
      limit 10
    ) orgs
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.canon_initiate_care_transfer(uuid, text, uuid, uuid, boolean) from public, anon;
revoke all on function public.canon_accept_care_transfer(uuid) from public, anon;
revoke all on function public.canon_reject_care_transfer(uuid) from public, anon;
revoke all on function public.canon_cancel_care_transfer(uuid) from public, anon;
revoke all on function public.canon_list_care_transfers(uuid) from public, anon;
revoke all on function public.canon_list_incoming_care_transfers() from public, anon;
revoke all on function public.canon_get_pet_care_context(uuid) from public, anon;
revoke all on function public.canon_search_care_transfer_targets(text) from public, anon;
revoke all on function public.canon_archive_pet(uuid, text) from public, anon;

grant execute on function public.canon_initiate_care_transfer(uuid, text, uuid, uuid, boolean) to authenticated;
grant execute on function public.canon_accept_care_transfer(uuid) to authenticated;
grant execute on function public.canon_reject_care_transfer(uuid) to authenticated;
grant execute on function public.canon_cancel_care_transfer(uuid) to authenticated;
grant execute on function public.canon_list_care_transfers(uuid) to authenticated;
grant execute on function public.canon_list_incoming_care_transfers() to authenticated;
grant execute on function public.canon_get_pet_care_context(uuid) to authenticated;
grant execute on function public.canon_search_care_transfer_targets(text) to authenticated;
grant execute on function public.canon_archive_pet(uuid, text) to authenticated;
grant execute on function public.canon_end_pet_responsibility(uuid) to authenticated;

notify pgrst, 'reload schema';
