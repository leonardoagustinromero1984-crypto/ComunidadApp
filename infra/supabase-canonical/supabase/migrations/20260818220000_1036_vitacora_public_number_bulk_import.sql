-- LeoVer Canonical
-- Logical migration: 1036
-- VitaCora public numbering + bulk XLSX import engine (SELF_SERVICE / ASSISTED / ADMIN).
-- Forward-only. Do not edit 1000-1035. Staging only.

create sequence if not exists public.vitacora_public_number_seq as bigint start with 1;

alter table public.vitacora_profiles
  add column if not exists public_vitacora_number bigint;

create unique index if not exists vitacora_profiles_public_number_uidx
  on public.vitacora_profiles (public_vitacora_number)
  where public_vitacora_number is not null;

create or replace function public.vitacora_assign_public_number()
returns trigger
language plpgsql
as $$
begin
  if tg_op = 'INSERT' then
    new.public_vitacora_number := nextval('public.vitacora_public_number_seq');
    return new;
  end if;
  if tg_op = 'UPDATE' then
    new.public_vitacora_number := old.public_vitacora_number;
    return new;
  end if;
  return new;
end;
$$;

drop trigger if exists vitacora_profiles_assign_public_number on public.vitacora_profiles;
create trigger vitacora_profiles_assign_public_number
  before insert or update on public.vitacora_profiles
  for each row execute function public.vitacora_assign_public_number();

alter table public.organizations
  add column if not exists verification_status text not null default 'NOT_REQUESTED';

alter table public.organizations drop constraint if exists organizations_verification_status_check;
alter table public.organizations
  add constraint organizations_verification_status_check
  check (verification_status in ('NOT_REQUESTED', 'PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'));

insert into public.permission_codes (code, scope, description) values
  ('org.pets.import', 'ORG', 'Import org pets / VitaCora bulk')
on conflict (code) do nothing;

insert into public.organization_role_permissions (role_id, permission_code)
select rp.role_id, 'org.pets.import'
from public.organization_role_permissions rp
where rp.permission_code = 'org.pets.manage'
on conflict do nothing;

create table if not exists public.pet_org_external_ids (
  pet_id uuid not null references public.pets(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  external_pet_id text not null,
  external_pet_id_normalized text not null,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (pet_id, organization_id)
);

create unique index if not exists pet_org_external_ids_norm_uidx
  on public.pet_org_external_ids (organization_id, external_pet_id_normalized);

create table if not exists public.vitacora_import_jobs (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  mode text not null check (mode in ('SELF_SERVICE', 'ASSISTED', 'ADMIN')),
  status text not null check (status in (
    'UPLOADED', 'ANALYZING', 'PENDING', 'EN_REVISION', 'READY', 'IMPORTING',
    'COMPLETED', 'COMPLETED_WITH_ERRORS', 'REQUIRES_CORRECTION', 'CANCELLED', 'FAILED'
  )),
  template_type text not null default 'LEOVER_VITACORA_IMPORT',
  template_version integer not null default 1,
  initiated_by uuid not null references public.persons(user_id),
  executed_by uuid null references public.persons(user_id),
  comment text null,
  storage_bucket text not null default 'vitacora-import',
  storage_path text not null,
  file_sha256 text null,
  total_rows integer not null default 0,
  valid_rows integer not null default 0,
  warning_rows integer not null default 0,
  failed_rows integer not null default 0,
  duplicate_rows integer not null default 0,
  existing_rows integer not null default 0,
  created_count integer not null default 0,
  created_at timestamptz not null default timezone('utc', now()),
  analyzed_at timestamptz null,
  confirmed_at timestamptz null,
  completed_at timestamptz null
);

create table if not exists public.vitacora_import_rows (
  id uuid primary key default gen_random_uuid(),
  import_id uuid not null references public.vitacora_import_jobs(id) on delete cascade,
  row_number integer not null,
  external_pet_id text null,
  external_pet_id_normalized text null,
  pet_name text null,
  status text not null,
  payload jsonb not null default '{}'::jsonb,
  issues jsonb not null default '[]'::jsonb,
  created_pet_id uuid null references public.pets(id),
  created_vitacora_number bigint null,
  error_code text null,
  error_message text null,
  unique (import_id, row_number)
);

alter table public.pets
  add column if not exists created_from_import_id uuid null references public.vitacora_import_jobs(id);

create index if not exists pets_created_from_import_idx
  on public.pets (created_from_import_id) where created_from_import_id is not null;
create index if not exists vitacora_import_jobs_org_idx
  on public.vitacora_import_jobs (organization_id, created_at desc);

insert into storage.buckets (id, name, public, file_size_limit)
values ('vitacora-import', 'vitacora-import', false, 5242880)
on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit;

create or replace function public._import_norm(p text)
returns text language sql immutable as $$
  select lower(btrim(translate(coalesce(p, ''),
    'ÁÀÄÂáàäâÉÈËÊéèëêÍÌÏÎíìïîÓÒÖÔóòöôÚÙÜÛúùüûÑñÇç',
    'AAAAaaaaEEEEeeeeIIIIiiiiOOOOooooUUUUuuuuNnCc')));
$$;

create or replace function public._acl_is_staff(p_user_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public._acl_is_admin(p_user_id) or public._acl_platform_role(p_user_id, 'MODERATOR');
$$;

create or replace function public._acl_can_import_pets(p_user_id uuid, p_org_id uuid, p_mode text)
returns boolean language sql stable security definer set search_path = public as $$
  select case
    when public._acl_is_staff(p_user_id) then true
    when p_mode = 'ADMIN' then false
    else exists (
      select 1
      from public.organization_memberships m
      join public.organizations o on o.id = m.organization_id
      where m.person_id = p_user_id
        and m.organization_id = p_org_id
        and m.status = 'ACTIVE'
        and o.lifecycle_status = 'ACTIVE'
        and o.verification_status = 'VERIFIED'
        and (
          public._acl_org_permission(p_user_id, p_org_id, 'org.pets.import')
          or public._acl_org_permission(p_user_id, p_org_id, 'org.pets.manage')
        )
    )
  end;
$$;

create or replace function public._import_match_location(p_kind text, p_name text, p_parent text)
returns text language sql stable as $$
  select n.id from public.location_nodes n
  where n.kind = p_kind and n.active
    and (p_parent is null or n.parent_id = p_parent)
    and (public._import_norm(n.name) = public._import_norm(p_name)
         or lower(coalesce(n.iso_code, '')) = lower(btrim(p_name)))
  order by n.sort_key limit 1;
$$;

create or replace function public.canon_import_create_job(
  p_organization_id uuid, p_mode text, p_storage_path text,
  p_file_sha256 text default null, p_comment text default null
) returns uuid language plpgsql security definer set search_path = public as $$
declare v_mode text := upper(btrim(p_mode)); v_id uuid; v_status text;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_mode not in ('SELF_SERVICE', 'ASSISTED', 'ADMIN') then raise exception 'INVALID_MODE'; end if;
  if p_storage_path is null or btrim(p_storage_path) = '' or p_storage_path like '%..%' then
    raise exception 'STORAGE_PATH_INVALID';
  end if;
  if not public._acl_can_import_pets(auth.uid(), p_organization_id, v_mode) then raise exception 'FORBIDDEN'; end if;
  v_status := case when v_mode = 'ASSISTED' then 'PENDING' else 'UPLOADED' end;
  insert into public.vitacora_import_jobs (
    organization_id, mode, status, initiated_by, storage_path, file_sha256, comment
  ) values (
    p_organization_id, v_mode, v_status, auth.uid(), btrim(p_storage_path), p_file_sha256, p_comment
  ) returning id into v_id;
  perform public.canon_audit('vitacora.import.create', 'vitacora_import_jobs', v_id,
    jsonb_build_object('mode', v_mode, 'organization_id', p_organization_id));
  return v_id;
end;
$$;

create or replace function public.canon_import_get(p_import_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v_job public.vitacora_import_jobs;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_job from public.vitacora_import_jobs where id = p_import_id;
  if v_job.id is null then raise exception 'NOT_FOUND'; end if;
  if not public._acl_can_import_pets(auth.uid(), v_job.organization_id, v_job.mode)
     and not public._acl_is_staff(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  return jsonb_build_object(
    'job', to_jsonb(v_job),
    'organization_name', (select name from public.organizations where id = v_job.organization_id),
    'rows', coalesce((
      select jsonb_agg(to_jsonb(r) order by r.row_number)
      from public.vitacora_import_rows r where r.import_id = p_import_id
    ), '[]'::jsonb)
  );
end;
$$;

create or replace function public.canon_import_analyze(p_import_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_job public.vitacora_import_jobs; v_row jsonb; v_i int := 0;
  v_ext text; v_norm text; v_name text; v_status text; v_issues jsonb;
  v_ready int := 0; v_err int := 0; v_dup int := 0; v_exist int := 0;
  v_seen text[] := '{}'; v_country text; v_admin text; v_loc text;
  v_job_status text; v_meta_type text; v_meta_ver int;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_job from public.vitacora_import_jobs where id = p_import_id;
  if v_job.id is null then raise exception 'NOT_FOUND'; end if;
  if not public._acl_can_import_pets(auth.uid(), v_job.organization_id, v_job.mode) then
    raise exception 'FORBIDDEN';
  end if;
  if v_job.status in ('COMPLETED', 'COMPLETED_WITH_ERRORS', 'IMPORTING') then
    return public.canon_import_get(p_import_id);
  end if;
  update public.vitacora_import_jobs set status = 'ANALYZING', executed_by = auth.uid() where id = p_import_id;
  v_meta_type := p_payload->>'template_type';
  v_meta_ver := coalesce((p_payload->>'template_version')::int, 0);
  if coalesce(p_payload->>'rejected','') <> '' or v_meta_type is null or v_meta_type <> 'LEOVER_VITACORA_IMPORT' or v_meta_ver <> 1 then
    delete from public.vitacora_import_rows where import_id = p_import_id;
    insert into public.vitacora_import_rows (import_id, row_number, status, error_code, error_message)
    values (p_import_id, 0, 'ERROR', coalesce(nullif(p_payload->>'rejected',''),
      case when v_meta_type is null then 'TEMPLATE_META_MISSING'
           when v_meta_type <> 'LEOVER_VITACORA_IMPORT' then 'TEMPLATE_TYPE_INVALID'
           else 'TEMPLATE_VERSION_INVALID' end), 'Archivo o plantilla inválida');
    update public.vitacora_import_jobs set status = 'FAILED', analyzed_at = timezone('utc', now()), failed_rows = 1 where id = p_import_id;
    return public.canon_import_get(p_import_id);
  end if;
  if jsonb_typeof(p_payload->'rows') <> 'array' or jsonb_array_length(p_payload->'rows') > 500 then
    update public.vitacora_import_jobs set status = 'FAILED', analyzed_at = timezone('utc', now()) where id = p_import_id;
    insert into public.vitacora_import_rows (import_id, row_number, status, error_code, error_message)
    values (p_import_id, 0, 'ERROR', 'MAX_ROWS_EXCEEDED', 'Máximo 500 filas') on conflict do nothing;
    return public.canon_import_get(p_import_id);
  end if;
  delete from public.vitacora_import_rows where import_id = p_import_id;
  for v_row in select value from jsonb_array_elements(p_payload->'rows') loop
    v_i := v_i + 1; v_issues := '[]'::jsonb;
    v_ext := btrim(coalesce(v_row->'values'->>'external_pet_id',''));
    v_name := btrim(coalesce(v_row->'values'->>'name',''));
    v_norm := public._import_norm(v_ext);
    if jsonb_array_length(coalesce(v_row->'formula_fields','[]'::jsonb)) > 0 then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','FORMULA_FORBIDDEN','field','archivo'));
    end if;
    if v_ext = '' then v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','REQUIRED','field','external_pet_id')); end if;
    if v_name = '' then v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','REQUIRED','field','name')); end if;
    if public._import_norm(coalesce(v_row->'values'->>'species','')) not in ('perro','dog','gato','cat','otro','otra','other') then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ENUM_INVALID','field','species'));
    end if;
    if public._import_norm(coalesce(v_row->'values'->>'sex','')) not in ('macho','male','m','hembra','female','h','f','desconocido','unknown') then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ENUM_INVALID','field','sex'));
    end if;
    if public._import_norm(coalesce(v_row->'values'->>'status','')) not in ('activo','active','archivado','archived','en adopcion','adopcion') then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ENUM_INVALID','field','status'));
    end if;
    v_country := public._import_match_location('COUNTRY', coalesce(nullif(btrim(v_row->'values'->>'country'),''),'Argentina'), null);
    if v_country is null then v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','COUNTRY_INVALID','field','country')); end if;
    v_admin := public._import_match_location('PROVINCE', v_row->'values'->>'administrative_area', v_country);
    if coalesce(btrim(v_row->'values'->>'administrative_area'),'') = '' or v_admin is null then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ADMINISTRATIVE_AREA_INVALID','field','administrative_area'));
    end if;
    v_loc := public._import_match_location('LOCALITY', v_row->'values'->>'locality', v_admin);
    if coalesce(btrim(v_row->'values'->>'locality'),'') = '' or v_loc is null then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','LOCALITY_INVALID','field','locality'));
    elsif v_admin is not null and not exists (select 1 from public.location_nodes l where l.id = v_loc and l.parent_id = v_admin) then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','LOCALITY_NOT_IN_AREA','field','locality'));
    end if;
    if v_norm <> '' and v_norm = any (v_seen) then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','DUPLICATE_IN_FILE','field','external_pet_id'));
    elsif v_norm <> '' then
      v_seen := array_append(v_seen, v_norm);
      if exists (select 1 from public.pet_org_external_ids e where e.organization_id = v_job.organization_id and e.external_pet_id_normalized = v_norm) then
        v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ALREADY_EXISTS','field','external_pet_id'));
      end if;
    end if;
    if exists (select 1 from jsonb_array_elements(v_issues) j where j->>'code' = 'ALREADY_EXISTS') then
      v_status := 'YA_EXISTE'; v_exist := v_exist + 1;
    elsif exists (select 1 from jsonb_array_elements(v_issues) j where j->>'code' = 'DUPLICATE_IN_FILE') then
      v_status := 'POSIBLE_DUPLICADO'; v_dup := v_dup + 1;
    elsif jsonb_array_length(v_issues) > 0 then
      v_status := 'ERROR'; v_err := v_err + 1;
    else
      v_status := 'LISTA'; v_ready := v_ready + 1;
    end if;
    insert into public.vitacora_import_rows (
      import_id, row_number, external_pet_id, external_pet_id_normalized, pet_name, status, payload, issues, error_code
    ) values (
      p_import_id, coalesce((v_row->>'row_number')::int, v_i), nullif(v_ext,''), nullif(v_norm,''), nullif(v_name,''),
      v_status, coalesce(v_row->'values','{}'::jsonb) || jsonb_build_object('country_id', v_country, 'administrative_area_id', v_admin, 'locality_id', v_loc),
      v_issues, case when v_status <> 'LISTA' then v_issues->0->>'code' else null end
    );
  end loop;
  v_job_status := case when v_ready = 0 then 'REQUIRES_CORRECTION' else 'READY' end;
  update public.vitacora_import_jobs set
    status = v_job_status, analyzed_at = timezone('utc', now()), executed_by = auth.uid(),
    total_rows = v_i, valid_rows = v_ready, failed_rows = v_err, duplicate_rows = v_dup, existing_rows = v_exist
  where id = p_import_id;
  perform public.canon_audit('vitacora.import.analyze', 'vitacora_import_jobs', p_import_id, jsonb_build_object('rows', v_i));
  return public.canon_import_get(p_import_id);
end;
$$;

create or replace function public._import_insert_pet(p_job public.vitacora_import_jobs, p_row public.vitacora_import_rows)
returns void language plpgsql security definer set search_path = public as $$
declare v_pet uuid; v_link uuid; v_species text; v_sex text; v_life text; v_size text; v_number bigint; v_precision text := 'UNKNOWN'; v_birth date;
begin
  v_species := case public._import_norm(p_row.payload->>'species') when 'gato' then 'CAT' when 'cat' then 'CAT' when 'otro' then 'OTHER' when 'otra' then 'OTHER' when 'other' then 'OTHER' else 'DOG' end;
  v_sex := case public._import_norm(p_row.payload->>'sex') when 'hembra' then 'FEMALE' when 'female' then 'FEMALE' when 'h' then 'FEMALE' when 'f' then 'FEMALE' when 'desconocido' then 'UNKNOWN' when 'unknown' then 'UNKNOWN' else 'MALE' end;
  v_life := case public._import_norm(p_row.payload->>'status') when 'archivado' then 'ARCHIVED' when 'archived' then 'ARCHIVED' else 'ACTIVE' end;
  v_size := case public._import_norm(coalesce(p_row.payload->>'size','')) when 'pequeno' then 'SMALL' when 'chico' then 'SMALL' when 'small' then 'SMALL' when 'grande' then 'LARGE' when 'large' then 'LARGE' when 'mediano' then 'MEDIUM' when 'medium' then 'MEDIUM' else null end;
  begin v_birth := (p_row.payload->>'birth_date')::date; exception when others then v_birth := null; end;
  if v_birth is not null then v_precision := 'EXACT_DATE'; end if;
  insert into public.pets (created_by_user_id, name, species_code, sex, size, home_locality_id, birth_precision, birth_date, lifecycle_status, created_from_import_id)
  values (p_job.executed_by, p_row.pet_name, v_species, v_sex, v_size, p_row.payload->>'locality_id', v_precision, v_birth, v_life, p_job.id)
  returning id into v_pet;
  insert into public.vitacora_profiles (pet_id) values (v_pet);
  select public_vitacora_number into v_number from public.vitacora_profiles where pet_id = v_pet;
  insert into public.pet_responsibility_links (pet_id, holder_kind, holder_organization_id, role, granted_by_actor_user_id)
  values (v_pet, 'ORGANIZATION', p_job.organization_id, 'RESPONSIBLE', p_job.executed_by) returning id into v_link;
  insert into public.pet_permission_grants (pet_id, link_id, subject_organization_id, permission_code, granted_by)
  select v_pet, v_link, p_job.organization_id, code, p_job.executed_by from public.permission_codes where scope in ('PET','VITACORA');
  insert into public.pet_org_external_ids (pet_id, organization_id, external_pet_id, external_pet_id_normalized)
  values (v_pet, p_job.organization_id, p_row.external_pet_id, p_row.external_pet_id_normalized);
  if coalesce(p_row.payload->>'notes', p_row.payload->>'description', p_row.payload->>'color', p_row.payload->>'special_needs','') <> '' then
    insert into public.pet_declared_health (pet_id, notes, updated_by)
    values (
      v_pet,
      nullif(btrim(concat_ws(E'\n',
        p_row.payload->>'notes',
        case when coalesce(p_row.payload->>'description','') = '' then null else 'Descripción: ' || (p_row.payload->>'description') end,
        case when coalesce(p_row.payload->>'color','') = '' then null else 'Color: ' || (p_row.payload->>'color') end,
        case when coalesce(p_row.payload->>'special_needs','') = '' then null else 'Necesidades especiales: ' || (p_row.payload->>'special_needs') end
      )), ''),
      p_job.executed_by
    )
    on conflict (pet_id) do update set notes = excluded.notes, updated_by = excluded.updated_by;
  end if;
  insert into public.pet_lifecycle_events (pet_id, to_status, actor_user_id, note) values (v_pet, v_life, p_job.executed_by, 'IMPORT');
  update public.vitacora_import_rows set created_pet_id = v_pet, created_vitacora_number = v_number, status = 'LISTA', error_code = null where id = p_row.id;
end;
$$;

create or replace function public.canon_import_confirm(p_import_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_job public.vitacora_import_jobs; v_row public.vitacora_import_rows; v_created int := 0; v_failed int := 0;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_job from public.vitacora_import_jobs where id = p_import_id for update;
  if v_job.id is null then raise exception 'NOT_FOUND'; end if;
  if not public._acl_can_import_pets(auth.uid(), v_job.organization_id, v_job.mode) then raise exception 'FORBIDDEN'; end if;
  if v_job.mode in ('ASSISTED','ADMIN') and not public._acl_is_staff(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  if v_job.status in ('COMPLETED','COMPLETED_WITH_ERRORS','IMPORTING') then return public.canon_import_get(p_import_id); end if;
  if v_job.status <> 'READY' then raise exception 'NOT_READY'; end if;
  update public.vitacora_import_jobs set status = 'IMPORTING', executed_by = auth.uid(), confirmed_at = timezone('utc', now())
    where id = p_import_id and status = 'READY';
  if not found then return public.canon_import_get(p_import_id); end if;
  select * into v_job from public.vitacora_import_jobs where id = p_import_id;
  for v_row in select * from public.vitacora_import_rows where import_id = p_import_id and status in ('LISTA','ADVERTENCIA') order by row_number loop
    begin
      if exists (select 1 from public.pet_org_external_ids e where e.organization_id = v_job.organization_id and e.external_pet_id_normalized = v_row.external_pet_id_normalized) then
        update public.vitacora_import_rows set status = 'YA_EXISTE', error_code = 'ALREADY_EXISTS' where id = v_row.id;
        v_failed := v_failed + 1;
      else
        perform public._import_insert_pet(v_job, v_row);
        v_created := v_created + 1;
      end if;
    exception when others then
      update public.vitacora_import_rows set status = 'ERROR', error_code = 'ROW_FAILED', error_message = sqlerrm where id = v_row.id;
      v_failed := v_failed + 1;
    end;
  end loop;
  update public.vitacora_import_jobs set
    status = case when v_created > 0 and v_failed > 0 then 'COMPLETED_WITH_ERRORS' when v_created > 0 then 'COMPLETED' else 'REQUIRES_CORRECTION' end,
    created_count = v_created, completed_at = timezone('utc', now())
  where id = p_import_id;
  perform public.canon_audit('vitacora.import.confirm', 'vitacora_import_jobs', p_import_id, jsonb_build_object('created', v_created, 'executed_by', auth.uid()));
  return public.canon_import_get(p_import_id);
end;
$$;

create or replace function public.canon_import_list(p_organization_id uuid default null, p_status text default null)
returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if public._acl_is_staff(auth.uid()) then
    return coalesce((select jsonb_agg(jsonb_build_object(
      'id', j.id, 'organization_id', j.organization_id, 'organization_name', o.name, 'mode', j.mode, 'status', j.status,
      'template_version', j.template_version, 'initiated_by', j.initiated_by, 'executed_by', j.executed_by,
      'created_at', j.created_at, 'comment', j.comment, 'total_rows', j.total_rows, 'valid_rows', j.valid_rows,
      'failed_rows', j.failed_rows, 'created_count', j.created_count, 'file_sha256', j.file_sha256
    ) order by j.created_at desc)
    from public.vitacora_import_jobs j join public.organizations o on o.id = j.organization_id
    where (p_organization_id is null or j.organization_id = p_organization_id) and (p_status is null or j.status = p_status)
    ), '[]'::jsonb);
  end if;
  if p_organization_id is null then raise exception 'ORG_REQUIRED'; end if;
  if not public._acl_can_import_pets(auth.uid(), p_organization_id, 'SELF_SERVICE') then raise exception 'FORBIDDEN'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object(
    'id', j.id, 'organization_id', j.organization_id, 'mode', j.mode, 'status', j.status,
    'created_at', j.created_at, 'total_rows', j.total_rows, 'valid_rows', j.valid_rows, 'created_count', j.created_count
  ) order by j.created_at desc)
  from public.vitacora_import_jobs j where j.organization_id = p_organization_id and (p_status is null or j.status = p_status)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_search_organizations(p_query text)
returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_is_staff(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('id', o.id, 'name', o.name, 'slug', o.slug, 'verification_status', o.verification_status) order by o.name)
    from public.organizations o
    where o.lifecycle_status = 'ACTIVE'
      and (p_query is null or btrim(p_query) = '' or o.name ilike '%'||btrim(p_query)||'%' or o.slug ilike '%'||btrim(p_query)||'%')
    limit 40), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_set_org_verification(p_org_id uuid, p_status text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_is_staff(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  if p_status not in ('NOT_REQUESTED','PENDING','VERIFIED','REJECTED','EXPIRED') then raise exception 'INVALID_STATUS'; end if;
  update public.organizations set verification_status = p_status where id = p_org_id;
  perform public.canon_audit('org.verification.set', 'organizations', p_org_id, jsonb_build_object('status', p_status));
end;
$$;

create or replace function public.canon_search_vitacora_number(p_query text)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v_num bigint; v_q text := btrim(p_query);
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  v_q := regexp_replace(regexp_replace(v_q, '^#', ''), '^[Vv]ita[Cc]ora\s*#?\s*', '');
  begin v_num := v_q::bigint; exception when others then return '[]'::jsonb; end;
  return coalesce((select jsonb_agg(jsonb_build_object('pet_id', p.id, 'name', p.name, 'public_vitacora_number', v.public_vitacora_number, 'needs_photo', p.avatar_asset_id is null))
    from public.vitacora_profiles v join public.pets p on p.id = v.pet_id
    where v.public_vitacora_number = v_num and (public._acl_pet_holder(auth.uid(), p.id) or public._acl_is_staff(auth.uid()))
  ), '[]'::jsonb);
end;
$$;

drop policy if exists vitacora_profiles_holder_select on public.vitacora_profiles;
create policy vitacora_profiles_holder_select on public.vitacora_profiles for select using (
  public._acl_pet_holder(auth.uid(), pet_id)
  or public._acl_is_admin(auth.uid())
  or public._acl_is_staff(auth.uid())
);

alter table public.vitacora_import_jobs enable row level security;
alter table public.vitacora_import_rows enable row level security;
alter table public.pet_org_external_ids enable row level security;

drop policy if exists vitacora_import_jobs_select on public.vitacora_import_jobs;
create policy vitacora_import_jobs_select on public.vitacora_import_jobs for select using (
  public._acl_is_staff(auth.uid()) or public._acl_can_import_pets(auth.uid(), organization_id, mode)
);
drop policy if exists vitacora_import_rows_select on public.vitacora_import_rows;
create policy vitacora_import_rows_select on public.vitacora_import_rows for select using (
  exists (select 1 from public.vitacora_import_jobs j where j.id = import_id and (public._acl_is_staff(auth.uid()) or public._acl_can_import_pets(auth.uid(), j.organization_id, j.mode)))
);
drop policy if exists pet_org_external_ids_select on public.pet_org_external_ids;
create policy pet_org_external_ids_select on public.pet_org_external_ids for select using (
  public._acl_is_staff(auth.uid()) or public._acl_org_member(auth.uid(), organization_id) or public._acl_pet_holder(auth.uid(), pet_id)
);

drop policy if exists vitacora_import_storage_insert on storage.objects;
create policy vitacora_import_storage_insert on storage.objects for insert to authenticated with check (
  bucket_id = 'vitacora-import' and (
    public._acl_is_staff(auth.uid())
    or public._acl_can_import_pets(auth.uid(), nullif(split_part(name, '/', 1), '')::uuid, 'SELF_SERVICE')
    or public._acl_can_import_pets(auth.uid(), nullif(split_part(name, '/', 1), '')::uuid, 'ADMIN')
  )
);
drop policy if exists vitacora_import_storage_select on storage.objects;
create policy vitacora_import_storage_select on storage.objects for select to authenticated using (
  bucket_id = 'vitacora-import' and (
    public._acl_is_staff(auth.uid())
    or public._acl_can_import_pets(auth.uid(), nullif(split_part(name, '/', 1), '')::uuid, 'SELF_SERVICE')
  )
);

grant execute on function public.canon_import_create_job(uuid, text, text, text, text) to authenticated;
grant execute on function public.canon_import_analyze(uuid, jsonb) to authenticated;
grant execute on function public.canon_import_confirm(uuid) to authenticated;
grant execute on function public.canon_import_get(uuid) to authenticated;
grant execute on function public.canon_import_list(uuid, text) to authenticated;
grant execute on function public.canon_admin_search_organizations(text) to authenticated;
grant execute on function public.canon_admin_set_org_verification(uuid, text) to authenticated;
grant execute on function public.canon_search_vitacora_number(text) to authenticated;
revoke execute on function public._import_insert_pet(public.vitacora_import_jobs, public.vitacora_import_rows) from public, anon, authenticated;

