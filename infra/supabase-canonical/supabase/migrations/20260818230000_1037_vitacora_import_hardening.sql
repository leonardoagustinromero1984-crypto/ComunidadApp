-- LeoVer Canonical
-- Logical migration: 1037
-- VitaCora import hardening: independent verified rescuer scope, age months,
-- unified WARNING/ERROR contract, admin verification UI RPCs.
-- Forward-only. Do not edit 1036. Staging only. No backfill of public numbers.

-- ---------------------------------------------------------------------------
-- Rescuer verification extends PERSON capability (not a parallel role system)
-- ---------------------------------------------------------------------------
alter table public.person_capabilities
  add column if not exists verification_status text not null default 'NOT_REQUESTED';
alter table public.person_capabilities
  drop constraint if exists person_capabilities_verification_status_check;
alter table public.person_capabilities
  add constraint person_capabilities_verification_status_check
  check (verification_status in ('NOT_REQUESTED', 'PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'));
alter table public.person_capabilities
  add column if not exists verified_at timestamptz null;
alter table public.person_capabilities
  add column if not exists verified_by uuid null references public.persons(user_id);

create table if not exists public.pet_rescuer_external_ids (
  pet_id uuid not null references public.pets(id) on delete cascade,
  person_id uuid not null references public.persons(user_id) on delete cascade,
  external_pet_id text not null,
  external_pet_id_normalized text not null,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (pet_id, person_id)
);
create unique index if not exists pet_rescuer_external_ids_norm_uidx
  on public.pet_rescuer_external_ids (person_id, external_pet_id_normalized);

alter table public.vitacora_import_jobs
  alter column organization_id drop not null;
alter table public.vitacora_import_jobs
  add column if not exists import_scope text not null default 'ORGANIZATION';
alter table public.vitacora_import_jobs
  add column if not exists rescuer_person_id uuid null references public.persons(user_id);
alter table public.vitacora_import_jobs
  drop constraint if exists vitacora_import_jobs_scope_xor;
alter table public.vitacora_import_jobs
  add constraint vitacora_import_jobs_scope_xor check (
    (import_scope = 'ORGANIZATION' and organization_id is not null)
    or (import_scope = 'INDEPENDENT_RESCUER' and rescuer_person_id is not null and organization_id is null)
  );

-- ---------------------------------------------------------------------------
-- Shared parsers (authoritative for ANALYZE + CONFIRM)
-- ---------------------------------------------------------------------------
create or replace function public._import_parse_age_months(p text)
returns integer
language plpgsql immutable as $$
declare
  v text := public._import_norm(p);
  v_years int;
  v_months int;
  v_bare int;
  v_out int;
begin
  if v is null or btrim(v) = '' then return null; end if;
  v_years := (regexp_match(v, '(\d+)\s*(anios?|anos?|years?|a)\y'))[1]::int;
  v_months := (regexp_match(v, '(\d+)\s*(meses?|months?|m)\y'))[1]::int;
  if v ~ '^\d+$' then v_bare := v::int; end if;
  v_out := case
    when v_years is not null and v_months is not null then v_years * 12 + v_months
    when v_years is not null then v_years * 12
    when v_months is not null then v_months
    when v_bare is not null and v_bare <= 30 then v_bare * 12
    when v_bare is not null and v_bare <= 480 then v_bare
    else null
  end;
  if v_out is null or v_out < 0 or v_out > 480 then return null; end if;
  return v_out;
end;
$$;

create or replace function public._import_parse_date(p text)
returns date
language plpgsql immutable as $$
declare v text := btrim(coalesce(p, '')); d date;
begin
  if v = '' then return null; end if;
  begin d := v::date; return d; exception when others then null; end;
  begin d := to_date(v, 'DD/MM/YYYY'); return d; exception when others then null; end;
  begin d := to_date(v, 'DD-MM-YYYY'); return d; exception when others then null; end;
  return null;
end;
$$;

create or replace function public._import_parse_yes_no(p text)
returns text
language sql immutable as $$
  select case
    when coalesce(btrim(p), '') = '' then null
    when public._import_norm(p) in ('si', 'yes', 'true', '1') then 'Y'
    when public._import_norm(p) in ('no', 'false', '0') then 'N'
    else 'INVALID'
  end;
$$;

create or replace function public._acl_verified_independent_rescuer(p_user_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.person_capabilities c
    where c.user_id = p_user_id
      and c.capability = 'RESCUER'
      and c.active
      and c.verification_status = 'VERIFIED'
  );
$$;

create or replace function public._acl_can_import_pets(p_user_id uuid, p_org_id uuid, p_mode text)
returns boolean language sql stable security definer set search_path = public as $$
  select case
    when public._acl_is_staff(p_user_id) then true
    when p_mode = 'ADMIN' then false
    when p_org_id is null then false
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

create or replace function public._acl_can_run_import(
  p_user_id uuid, p_scope text, p_org_id uuid, p_rescuer_id uuid, p_mode text
) returns boolean language sql stable security definer set search_path = public as $$
  select case
    when public._acl_is_staff(p_user_id) then true
    when p_mode = 'ADMIN' then false
    when p_scope = 'INDEPENDENT_RESCUER' then
      p_rescuer_id = p_user_id and public._acl_verified_independent_rescuer(p_user_id)
    else public._acl_can_import_pets(p_user_id, p_org_id, p_mode)
  end;
$$;

create or replace function public._import_storage_org_id(p_name text)
returns uuid language plpgsql immutable as $$
declare v text := split_part(p_name, '/', 1);
begin
  if v ~ '^[0-9a-fA-F-]{36}$' then return v::uuid; end if;
  return null;
end;
$$;

-- ---------------------------------------------------------------------------
-- Create job (scope ORGANIZATION | INDEPENDENT_RESCUER)
-- ---------------------------------------------------------------------------
drop function if exists public.canon_import_create_job(uuid, text, text, text, text);

create or replace function public.canon_import_create_job(
  p_organization_id uuid,
  p_mode text,
  p_storage_path text,
  p_file_sha256 text default null,
  p_comment text default null,
  p_scope text default 'ORGANIZATION',
  p_rescuer_person_id uuid default null
) returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_mode text := upper(btrim(p_mode));
  v_scope text := upper(btrim(coalesce(p_scope, 'ORGANIZATION')));
  v_id uuid;
  v_status text;
  v_org uuid;
  v_rescuer uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if v_mode not in ('SELF_SERVICE', 'ASSISTED', 'ADMIN') then raise exception 'INVALID_MODE'; end if;
  if v_scope not in ('ORGANIZATION', 'INDEPENDENT_RESCUER') then raise exception 'INVALID_SCOPE'; end if;
  if p_storage_path is null or btrim(p_storage_path) = '' or p_storage_path like '%..%' then
    raise exception 'STORAGE_PATH_INVALID';
  end if;
  if v_scope = 'ORGANIZATION' then
    v_org := p_organization_id;
    v_rescuer := null;
    if v_org is null then raise exception 'ORG_REQUIRED'; end if;
  else
    v_org := null;
    if public._acl_is_staff(auth.uid()) then
      v_rescuer := coalesce(p_rescuer_person_id, auth.uid());
    else
      if p_rescuer_person_id is not null and p_rescuer_person_id is distinct from auth.uid() then
        raise exception 'PERSON_ESCALATION_DENIED';
      end if;
      v_rescuer := auth.uid();
    end if;
  end if;
  if not public._acl_can_run_import(auth.uid(), v_scope, v_org, v_rescuer, v_mode) then
    raise exception 'FORBIDDEN';
  end if;
  v_status := case when v_mode = 'ASSISTED' then 'PENDING' else 'UPLOADED' end;
  insert into public.vitacora_import_jobs (
    organization_id, rescuer_person_id, import_scope, mode, status, initiated_by,
    storage_path, file_sha256, comment
  ) values (
    v_org, v_rescuer, v_scope, v_mode, v_status, auth.uid(),
    btrim(p_storage_path), p_file_sha256, p_comment
  ) returning id into v_id;
  perform public.canon_audit('vitacora.import.create', 'vitacora_import_jobs', v_id,
    jsonb_build_object('mode', v_mode, 'scope', v_scope, 'organization_id', v_org, 'rescuer_person_id', v_rescuer));
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
  if not public._acl_can_run_import(auth.uid(), v_job.import_scope, v_job.organization_id, v_job.rescuer_person_id, v_job.mode)
     and not public._acl_is_staff(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  return jsonb_build_object(
    'job', to_jsonb(v_job),
    'organization_name', (select name from public.organizations where id = v_job.organization_id),
    'rescuer_name', (select coalesce(display_name, username) from public.persons where user_id = v_job.rescuer_person_id),
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
  v_ready int := 0; v_warn int := 0; v_err int := 0; v_dup int := 0; v_exist int := 0;
  v_seen text[] := '{}'; v_country text; v_admin text; v_loc text;
  v_job_status text; v_meta_type text; v_meta_ver int;
  v_age int; v_birth date; v_yn text; v_life text; v_precision text;
  v_vals jsonb;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_job from public.vitacora_import_jobs where id = p_import_id;
  if v_job.id is null then raise exception 'NOT_FOUND'; end if;
  if not public._acl_can_run_import(auth.uid(), v_job.import_scope, v_job.organization_id, v_job.rescuer_person_id, v_job.mode) then
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
    v_vals := coalesce(v_row->'values','{}'::jsonb);
    v_ext := btrim(coalesce(v_vals->>'external_pet_id',''));
    v_name := btrim(coalesce(v_vals->>'name',''));
    v_norm := public._import_norm(v_ext);
    if jsonb_array_length(coalesce(v_row->'formula_fields','[]'::jsonb)) > 0 then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','FORMULA_FORBIDDEN','field','archivo','message','Hay una fórmula. No se ejecuta.','suggestion','Escribí el valor, no una fórmula.'));
    end if;
    if v_ext = '' then v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','REQUIRED','field','external_pet_id','message','La referencia interna es obligatoria.','suggestion','Usá una referencia única.')); end if;
    if v_name = '' then v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','REQUIRED','field','name','message','El nombre es obligatorio.','suggestion','Completá el nombre.')); end if;
    if public._import_norm(coalesce(v_vals->>'species','')) not in ('perro','dog','gato','cat','otro','otra','other') then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ENUM_INVALID','field','species','message','Especie no reconocida.','suggestion','Usá Perro o Gato.'));
    end if;
    if public._import_norm(coalesce(v_vals->>'sex','')) not in ('macho','male','m','hembra','female','h','f','desconocido','unknown') then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ENUM_INVALID','field','sex','message','Sexo no reconocido.','suggestion','Usá Macho o Hembra.'));
    end if;
    v_life := 'ACTIVE';
    if public._import_norm(coalesce(v_vals->>'status','')) in ('archivado','archived') then v_life := 'ARCHIVED';
    elsif public._import_norm(coalesce(v_vals->>'status','')) in ('en adopcion','adopcion') then
      v_life := 'ACTIVE';
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','STATUS_ADOPTION_NOT_PUBLISHED','field','status','message','En adopción no publica un aviso. La VitaCora se crea activa.','suggestion','Publicá en adopción después, cuando esté lista.'));
    elsif public._import_norm(coalesce(v_vals->>'status','')) not in ('activo','active') then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ENUM_INVALID','field','status','message','Estado no reconocido.','suggestion','Usá Activo o Archivado.'));
    end if;
    v_country := public._import_match_location('COUNTRY', coalesce(nullif(btrim(v_vals->>'country'),''),'Argentina'), null);
    if v_country is null then v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','COUNTRY_INVALID','field','country','message','País no encontrado.','suggestion','En v1 usá Argentina.')); end if;
    v_admin := public._import_match_location('PROVINCE', v_vals->>'administrative_area', v_country);
    if coalesce(btrim(v_vals->>'administrative_area'),'') = '' or v_admin is null then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ADMINISTRATIVE_AREA_INVALID','field','administrative_area','message','Provincia no encontrada.','suggestion','Revisá la provincia.'));
    end if;
    v_loc := public._import_match_location('LOCALITY', v_vals->>'locality', v_admin);
    if coalesce(btrim(v_vals->>'locality'),'') = '' or v_loc is null then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','LOCALITY_INVALID','field','locality','message','Localidad no encontrada.','suggestion','Revisá la localidad.'));
    elsif v_admin is not null and not exists (select 1 from public.location_nodes l where l.id = v_loc and l.parent_id = v_admin) then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','LOCALITY_NOT_IN_AREA','field','locality','message','La localidad no pertenece a esa provincia.','suggestion','Elegí una localidad de la provincia indicada.'));
    end if;
    foreach v_yn in array array['neutered','vaccinated','lives_with_dogs','lives_with_cats','lives_with_kids'] loop
      if public._import_parse_yes_no(v_vals->>v_yn) = 'INVALID' then
        v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','YES_NO_INVALID','field',v_yn,'message','Usá Sí o No.','suggestion','Completá con Sí o No.'));
      end if;
    end loop;
    v_age := public._import_parse_age_months(v_vals->>'estimated_age');
    if coalesce(btrim(v_vals->>'estimated_age'),'') <> '' and v_age is null then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','AGE_INVALID','field','estimated_age','message','Edad aproximada inválida.','suggestion','Usá años o meses (ej. 2 años).'));
    end if;
    if coalesce(btrim(v_vals->>'birth_date'),'') <> '' then
      v_birth := public._import_parse_date(v_vals->>'birth_date');
      if v_birth is null then
        v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','DATE_INVALID','field','birth_date','message','Fecha inválida.','suggestion','Usá AAAA-MM-DD o DD/MM/AAAA.'));
      end if;
    else
      v_birth := null;
    end if;
    if coalesce(btrim(v_vals->>'intake_date'),'') <> '' and public._import_parse_date(v_vals->>'intake_date') is null then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','DATE_INVALID','field','intake_date','message','Fecha inválida.','suggestion','Usá AAAA-MM-DD o DD/MM/AAAA.'));
    end if;
    if v_age is not null and v_birth is not null and abs((extract(year from current_date)::int - extract(year from v_birth)::int) - (v_age / 12)) > 1 then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','AGE_BIRTH_MISMATCH','field','estimated_age','message','La edad aproximada no coincide con la fecha de nacimiento.','suggestion','Dejá uno de los dos campos o corregí el valor.'));
    end if;
    v_precision := case when v_birth is not null then 'EXACT_DATE' when v_age is not null then 'ESTIMATED' else 'UNKNOWN' end;
    if v_norm <> '' and v_norm = any (v_seen) then
      v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','DUPLICATE_IN_FILE','field','external_pet_id','message','La referencia se repite en el archivo.','suggestion','Usá una referencia única.'));
    elsif v_norm <> '' then
      v_seen := array_append(v_seen, v_norm);
      if v_job.import_scope = 'INDEPENDENT_RESCUER' then
        if exists (select 1 from public.pet_rescuer_external_ids e where e.person_id = v_job.rescuer_person_id and e.external_pet_id_normalized = v_norm) then
          v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ALREADY_EXISTS','field','external_pet_id','message','Ya existe una mascota con esa referencia.','suggestion','No se actualiza automáticamente.'));
        end if;
      else
        if exists (select 1 from public.pet_org_external_ids e where e.organization_id = v_job.organization_id and e.external_pet_id_normalized = v_norm) then
          v_issues := v_issues || jsonb_build_array(jsonb_build_object('code','ALREADY_EXISTS','field','external_pet_id','message','Ya existe una mascota con esa referencia en la organización.','suggestion','No se actualiza automáticamente.'));
        end if;
      end if;
    end if;
    if exists (select 1 from jsonb_array_elements(v_issues) j where j->>'code' = 'ALREADY_EXISTS') then
      v_status := 'YA_EXISTE'; v_exist := v_exist + 1;
    elsif exists (select 1 from jsonb_array_elements(v_issues) j where j->>'code' = 'DUPLICATE_IN_FILE') then
      v_status := 'POSIBLE_DUPLICADO'; v_dup := v_dup + 1;
    elsif exists (select 1 from jsonb_array_elements(v_issues) j where j->>'code' in (
      'REQUIRED','ENUM_INVALID','DATE_INVALID','YES_NO_INVALID','COUNTRY_INVALID','ADMINISTRATIVE_AREA_INVALID',
      'LOCALITY_INVALID','LOCALITY_NOT_IN_AREA','FORMULA_FORBIDDEN','AGE_INVALID'
    )) then
      v_status := 'ERROR'; v_err := v_err + 1;
    elsif jsonb_array_length(v_issues) > 0 then
      v_status := 'ADVERTENCIA'; v_warn := v_warn + 1; v_ready := v_ready + 1;
    else
      v_status := 'LISTA'; v_ready := v_ready + 1;
    end if;
    insert into public.vitacora_import_rows (
      import_id, row_number, external_pet_id, external_pet_id_normalized, pet_name, status, payload, issues, error_code
    ) values (
      p_import_id, coalesce((v_row->>'row_number')::int, v_i), nullif(v_ext,''), nullif(v_norm,''), nullif(v_name,''),
      v_status,
      v_vals || jsonb_build_object(
        'country_id', v_country,
        'administrative_area_id', v_admin,
        'locality_id', v_loc,
        'estimated_age_months', v_age,
        'birth_date_normalized', v_birth,
        'birth_precision', v_precision,
        'lifecycle_status', v_life
      ),
      v_issues, case when v_status not in ('LISTA','ADVERTENCIA') then v_issues->0->>'code' else null end
    );
  end loop;
  v_job_status := case when v_ready = 0 then 'REQUIRES_CORRECTION' else 'READY' end;
  update public.vitacora_import_jobs set
    status = v_job_status, analyzed_at = timezone('utc', now()), executed_by = auth.uid(),
    total_rows = v_i, valid_rows = v_ready, warning_rows = v_warn, failed_rows = v_err,
    duplicate_rows = v_dup, existing_rows = v_exist
  where id = p_import_id;
  perform public.canon_audit('vitacora.import.analyze', 'vitacora_import_jobs', p_import_id, jsonb_build_object('rows', v_i));
  return public.canon_import_get(p_import_id);
end;
$$;

create or replace function public._import_insert_pet(p_job public.vitacora_import_jobs, p_row public.vitacora_import_rows)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_pet uuid; v_link uuid; v_species text; v_sex text; v_life text; v_size text; v_number bigint;
  v_precision text; v_birth date; v_age int;
begin
  v_species := case public._import_norm(p_row.payload->>'species') when 'gato' then 'CAT' when 'cat' then 'CAT' when 'otro' then 'OTHER' when 'otra' then 'OTHER' when 'other' then 'OTHER' else 'DOG' end;
  v_sex := case public._import_norm(p_row.payload->>'sex') when 'hembra' then 'FEMALE' when 'female' then 'FEMALE' when 'h' then 'FEMALE' when 'f' then 'FEMALE' when 'desconocido' then 'UNKNOWN' when 'unknown' then 'UNKNOWN' else 'MALE' end;
  v_life := coalesce(nullif(p_row.payload->>'lifecycle_status',''), case public._import_norm(p_row.payload->>'status') when 'archivado' then 'ARCHIVED' when 'archived' then 'ARCHIVED' else 'ACTIVE' end);
  v_size := case public._import_norm(coalesce(p_row.payload->>'size','')) when 'pequeno' then 'SMALL' when 'chico' then 'SMALL' when 'small' then 'SMALL' when 'grande' then 'LARGE' when 'large' then 'LARGE' when 'mediano' then 'MEDIUM' when 'medium' then 'MEDIUM' else null end;
  v_birth := coalesce(public._import_parse_date(p_row.payload->>'birth_date_normalized'), public._import_parse_date(p_row.payload->>'birth_date'));
  begin v_age := (p_row.payload->>'estimated_age_months')::int; exception when others then v_age := public._import_parse_age_months(p_row.payload->>'estimated_age'); end;
  v_precision := coalesce(nullif(p_row.payload->>'birth_precision',''), case when v_birth is not null then 'EXACT_DATE' when v_age is not null then 'ESTIMATED' else 'UNKNOWN' end);
  insert into public.pets (
    created_by_user_id, name, species_code, sex, size, home_locality_id,
    birth_precision, birth_date, estimated_age_months, estimated_as_of, lifecycle_status, created_from_import_id
  ) values (
    coalesce(p_job.executed_by, p_job.initiated_by), p_row.pet_name, v_species, v_sex, v_size, p_row.payload->>'locality_id',
    v_precision, v_birth,
    case when v_precision = 'ESTIMATED' then v_age else null end,
    case when v_precision = 'ESTIMATED' then current_date else null end,
    v_life, p_job.id
  ) returning id into v_pet;
  insert into public.vitacora_profiles (pet_id) values (v_pet);
  select public_vitacora_number into v_number from public.vitacora_profiles where pet_id = v_pet;
  if p_job.import_scope = 'INDEPENDENT_RESCUER' then
    insert into public.pet_responsibility_links (pet_id, holder_kind, holder_person_id, role, granted_by_actor_user_id)
    values (v_pet, 'PERSON', p_job.rescuer_person_id, 'OWNER', p_job.executed_by) returning id into v_link;
    insert into public.pet_permission_grants (pet_id, link_id, subject_person_id, permission_code, granted_by)
    select v_pet, v_link, p_job.rescuer_person_id, code, p_job.executed_by from public.permission_codes where scope in ('PET','VITACORA');
    insert into public.pet_rescuer_external_ids (pet_id, person_id, external_pet_id, external_pet_id_normalized)
    values (v_pet, p_job.rescuer_person_id, p_row.external_pet_id, p_row.external_pet_id_normalized);
  else
    insert into public.pet_responsibility_links (pet_id, holder_kind, holder_organization_id, role, granted_by_actor_user_id)
    values (v_pet, 'ORGANIZATION', p_job.organization_id, 'RESPONSIBLE', p_job.executed_by) returning id into v_link;
    insert into public.pet_permission_grants (pet_id, link_id, subject_organization_id, permission_code, granted_by)
    select v_pet, v_link, p_job.organization_id, code, p_job.executed_by from public.permission_codes where scope in ('PET','VITACORA');
    insert into public.pet_org_external_ids (pet_id, organization_id, external_pet_id, external_pet_id_normalized)
    values (v_pet, p_job.organization_id, p_row.external_pet_id, p_row.external_pet_id_normalized);
  end if;
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
declare v_job public.vitacora_import_jobs; v_row public.vitacora_import_rows; v_created int := 0; v_failed int := 0; v_exists boolean;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into v_job from public.vitacora_import_jobs where id = p_import_id for update;
  if v_job.id is null then raise exception 'NOT_FOUND'; end if;
  if not public._acl_can_run_import(auth.uid(), v_job.import_scope, v_job.organization_id, v_job.rescuer_person_id, v_job.mode) then
    raise exception 'FORBIDDEN';
  end if;
  if v_job.mode in ('ASSISTED','ADMIN') and not public._acl_is_staff(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  if v_job.status in ('COMPLETED','COMPLETED_WITH_ERRORS','IMPORTING') then return public.canon_import_get(p_import_id); end if;
  if v_job.status <> 'READY' then raise exception 'NOT_READY'; end if;
  update public.vitacora_import_jobs set status = 'IMPORTING', executed_by = auth.uid(), confirmed_at = timezone('utc', now())
    where id = p_import_id and status = 'READY';
  if not found then return public.canon_import_get(p_import_id); end if;
  select * into v_job from public.vitacora_import_jobs where id = p_import_id;
  for v_row in select * from public.vitacora_import_rows where import_id = p_import_id and status in ('LISTA','ADVERTENCIA') order by row_number loop
    begin
      if v_job.import_scope = 'INDEPENDENT_RESCUER' then
        v_exists := exists (select 1 from public.pet_rescuer_external_ids e where e.person_id = v_job.rescuer_person_id and e.external_pet_id_normalized = v_row.external_pet_id_normalized);
      else
        v_exists := exists (select 1 from public.pet_org_external_ids e where e.organization_id = v_job.organization_id and e.external_pet_id_normalized = v_row.external_pet_id_normalized);
      end if;
      if v_exists then
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
    return coalesce((select jsonb_agg(to_jsonb(j) || jsonb_build_object(
        'organization_name', o.name,
        'rescuer_name', coalesce(p.display_name, p.username)
      ) order by j.created_at desc)
      from public.vitacora_import_jobs j
      left join public.organizations o on o.id = j.organization_id
      left join public.persons p on p.user_id = j.rescuer_person_id
      where (p_organization_id is null or j.organization_id = p_organization_id)
        and (p_status is null or j.status = p_status)
    ), '[]'::jsonb);
  end if;
  return coalesce((select jsonb_agg(to_jsonb(j) || jsonb_build_object('organization_name', o.name) order by j.created_at desc)
    from public.vitacora_import_jobs j
    left join public.organizations o on o.id = j.organization_id
    where public._acl_can_run_import(auth.uid(), j.import_scope, j.organization_id, j.rescuer_person_id, j.mode)
      and (p_organization_id is null or j.organization_id = p_organization_id)
      and (p_status is null or j.status = p_status)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_set_org_verification(p_org_id uuid, p_status text)
returns void language plpgsql security definer set search_path = public as $$
declare v_old text; v_new text := upper(btrim(p_status));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_is_staff(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  if v_new not in ('NOT_REQUESTED','PENDING','VERIFIED','REJECTED','EXPIRED') then raise exception 'INVALID_STATUS'; end if;
  select verification_status into v_old from public.organizations where id = p_org_id;
  if not found then raise exception 'NOT_FOUND'; end if;
  update public.organizations set verification_status = v_new where id = p_org_id;
  perform public.canon_audit('org.verification.set', 'organizations', p_org_id,
    jsonb_build_object('from', v_old, 'to', v_new, 'actor', auth.uid()));
end;
$$;

create or replace function public.canon_admin_set_rescuer_verification(p_person_id uuid, p_status text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_old text; v_new text := upper(btrim(p_status));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_is_staff(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  if v_new not in ('NOT_REQUESTED','PENDING','VERIFIED','REJECTED','EXPIRED') then raise exception 'INVALID_STATUS'; end if;
  if not exists (select 1 from public.persons where user_id = p_person_id) then raise exception 'NOT_FOUND'; end if;
  select verification_status into v_old from public.person_capabilities
    where user_id = p_person_id and capability = 'RESCUER';
  insert into public.person_capabilities (user_id, capability, active, verification_status, verified_by, verified_at, updated_at)
  values (p_person_id, 'RESCUER', true, v_new, auth.uid(), case when v_new = 'VERIFIED' then timezone('utc', now()) else null end, timezone('utc', now()))
  on conflict (user_id, capability) do update
    set verification_status = excluded.verification_status,
        verified_by = excluded.verified_by,
        verified_at = excluded.verified_at,
        active = true,
        updated_at = timezone('utc', now());
  perform public.canon_audit('rescuer.verification.set', 'person_capabilities', p_person_id,
    jsonb_build_object('from', v_old, 'to', v_new, 'actor', auth.uid()));
  return jsonb_build_object('person_id', p_person_id, 'from', v_old, 'to', v_new, 'actor', auth.uid(), 'at', timezone('utc', now()));
end;
$$;

create or replace function public.canon_admin_search_rescuers(p_query text)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare v_q text := public._import_norm(coalesce(p_query, ''));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if not public._acl_is_staff(auth.uid()) then raise exception 'FORBIDDEN'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object(
      'id', p.user_id,
      'name', coalesce(p.display_name, p.username, p.user_id::text),
      'username', p.username,
      'verification_status', coalesce(c.verification_status, 'NOT_REQUESTED'),
      'active', coalesce(c.active, false)
    ) order by coalesce(p.display_name, p.username))
    from public.persons p
    left join public.person_capabilities c on c.user_id = p.user_id and c.capability = 'RESCUER'
    where (v_q = '' or public._import_norm(coalesce(p.display_name,'')) like '%' || v_q || '%'
           or public._import_norm(coalesce(p.username,'')) like '%' || v_q || '%')
      and (c.capability is not null or v_q <> '')
    limit 40
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_list_my_person_capabilities()
returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'capability', c.capability,
      'active', c.active,
      'verification_status', c.verification_status,
      'updated_at', c.updated_at
    ) order by c.capability)
    from public.person_capabilities c
    where c.user_id = auth.uid()
  ), '[]'::jsonb);
end;
$$;

alter table public.pet_rescuer_external_ids enable row level security;
drop policy if exists pet_rescuer_external_ids_select on public.pet_rescuer_external_ids;
create policy pet_rescuer_external_ids_select on public.pet_rescuer_external_ids for select using (
  public._acl_is_staff(auth.uid()) or person_id = auth.uid() or public._acl_pet_holder(auth.uid(), pet_id)
);

drop policy if exists vitacora_import_jobs_select on public.vitacora_import_jobs;
create policy vitacora_import_jobs_select on public.vitacora_import_jobs for select using (
  public._acl_is_staff(auth.uid())
  or public._acl_can_run_import(auth.uid(), import_scope, organization_id, rescuer_person_id, mode)
);
drop policy if exists vitacora_import_rows_select on public.vitacora_import_rows;
create policy vitacora_import_rows_select on public.vitacora_import_rows for select using (
  exists (
    select 1 from public.vitacora_import_jobs j
    where j.id = import_id and (
      public._acl_is_staff(auth.uid())
      or public._acl_can_run_import(auth.uid(), j.import_scope, j.organization_id, j.rescuer_person_id, j.mode)
    )
  )
);

drop policy if exists vitacora_import_storage_insert on storage.objects;
create policy vitacora_import_storage_insert on storage.objects for insert to authenticated with check (
  bucket_id = 'vitacora-import' and (
    public._acl_is_staff(auth.uid())
    or (
      split_part(name, '/', 1) = 'rescuer'
      and split_part(name, '/', 2)::uuid = auth.uid()
      and public._acl_verified_independent_rescuer(auth.uid())
    )
    or public._acl_can_import_pets(auth.uid(), public._import_storage_org_id(name), 'SELF_SERVICE')
    or public._acl_can_import_pets(auth.uid(), public._import_storage_org_id(name), 'ADMIN')
  )
);
drop policy if exists vitacora_import_storage_select on storage.objects;
create policy vitacora_import_storage_select on storage.objects for select to authenticated using (
  bucket_id = 'vitacora-import' and (
    public._acl_is_staff(auth.uid())
    or (
      split_part(name, '/', 1) = 'rescuer'
      and split_part(name, '/', 2)::uuid = auth.uid()
    )
    or public._acl_can_import_pets(auth.uid(), public._import_storage_org_id(name), 'SELF_SERVICE')
  )
);

grant execute on function public.canon_import_create_job(uuid, text, text, text, text, text, uuid) to authenticated;
grant execute on function public.canon_import_analyze(uuid, jsonb) to authenticated;
grant execute on function public.canon_import_confirm(uuid) to authenticated;
grant execute on function public.canon_import_get(uuid) to authenticated;
grant execute on function public.canon_import_list(uuid, text) to authenticated;
grant execute on function public.canon_admin_set_org_verification(uuid, text) to authenticated;
grant execute on function public.canon_admin_set_rescuer_verification(uuid, text) to authenticated;
grant execute on function public.canon_admin_search_rescuers(text) to authenticated;
revoke execute on function public._import_insert_pet(public.vitacora_import_jobs, public.vitacora_import_rows) from public, anon, authenticated;
