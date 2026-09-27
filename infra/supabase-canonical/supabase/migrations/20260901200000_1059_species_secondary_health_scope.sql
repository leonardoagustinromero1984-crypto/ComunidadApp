-- 1059: species lifecycle + secondary classification + health product species scope.
-- Additive. Does not edit 1057/1058. Does not migrate pets.breed_id.
-- Physical secondary storage remains public.breeds.

-- ---------------------------------------------------------------------------
-- Species: additive columns
-- ---------------------------------------------------------------------------
alter table public.species
  add column if not exists id uuid,
  add column if not exists status text,
  add column if not exists secondary_classification_enabled boolean not null default false,
  add column if not exists secondary_classification_kind text not null default 'NONE',
  add column if not exists secondary_label_singular text,
  add column if not exists secondary_label_plural text,
  add column if not exists catalog_visibility text not null default 'VISIBLE';

update public.species
  set id = gen_random_uuid()
  where id is null;

alter table public.species
  alter column id set default gen_random_uuid(),
  alter column id set not null;

create unique index if not exists species_id_uidx on public.species (id);

update public.species
  set status = case when active then 'ACTIVE' else 'INACTIVE' end
  where status is null or status not in ('PREPARATION', 'ACTIVE', 'INACTIVE');

alter table public.species
  alter column status set default 'PREPARATION';

alter table public.species
  alter column status set not null;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'species_status_allowed'
  ) then
    alter table public.species
      add constraint species_status_allowed
      check (status in ('PREPARATION', 'ACTIVE', 'INACTIVE'));
  end if;
  if not exists (
    select 1 from pg_constraint where conname = 'species_secondary_kind_allowed'
  ) then
    alter table public.species
      add constraint species_secondary_kind_allowed
      check (secondary_classification_kind in ('NONE', 'BREED', 'TYPE', 'VARIETY', 'CUSTOM'));
  end if;
  if not exists (
    select 1 from pg_constraint where conname = 'species_catalog_visibility_allowed'
  ) then
    alter table public.species
      add constraint species_catalog_visibility_allowed
      check (catalog_visibility in ('VISIBLE', 'LEGACY_HIDDEN'));
  end if;
end $$;

create or replace function public._species_sync_active()
returns trigger
language plpgsql
as $$
begin
  if tg_op = 'INSERT' or new.status is distinct from old.status then
    new.active := (new.status = 'ACTIVE');
  elsif new.active is distinct from old.active then
    if new.active then
      new.status := 'ACTIVE';
    elsif new.status = 'ACTIVE' then
      new.status := 'INACTIVE';
    end if;
  end if;
  if not new.secondary_classification_enabled then
    new.secondary_classification_kind := 'NONE';
  elsif new.secondary_classification_kind = 'NONE' then
    new.secondary_classification_enabled := false;
  end if;
  return new;
end;
$$;

drop trigger if exists species_sync_active on public.species;
create trigger species_sync_active
  before insert or update on public.species
  for each row execute function public._species_sync_active();

-- ---------------------------------------------------------------------------
-- Breeds: generic secondary item columns (same physical table)
-- ---------------------------------------------------------------------------
alter table public.breeds
  add column if not exists code text,
  add column if not exists normalized_name text;

create or replace function public._canon_normalize_catalog_name(p_name text)
returns text
language sql
immutable
as $$
  select lower(trim(regexp_replace(coalesce(p_name, ''), '\s+', ' ', 'g')));
$$;

create or replace function public._canon_secondary_code(p_name text)
returns text
language sql
immutable
as $$
  select nullif(
    upper(trim(regexp_replace(
      regexp_replace(coalesce(p_name, ''), '[^a-zA-Z0-9]+', '_', 'g'),
      '_+', '_', 'g'
    ), '_')),
    ''
  );
$$;

update public.breeds
  set normalized_name = public._canon_normalize_catalog_name(name)
  where normalized_name is null;

update public.breeds
  set code = public._canon_secondary_code(name)
  where code is null;

create unique index if not exists breeds_species_normalized_uidx
  on public.breeds (species_code, normalized_name)
  where normalized_name is not null;

-- ---------------------------------------------------------------------------
-- Canonical 15 species + hide leftovers (no DELETE)
-- ---------------------------------------------------------------------------
insert into public.species (code, name, sort_key, status, catalog_visibility)
values
  ('DOG', 'Perro', 1, 'ACTIVE', 'VISIBLE'),
  ('CAT', 'Gato', 2, 'ACTIVE', 'VISIBLE'),
  ('RABBIT', 'Conejo', 3, 'PREPARATION', 'VISIBLE'),
  ('FERRET', 'Hurón', 4, 'PREPARATION', 'VISIBLE'),
  ('GUINEA_PIG', 'Cobayo', 5, 'PREPARATION', 'VISIBLE'),
  ('HAMSTER', 'Hámster', 6, 'PREPARATION', 'VISIBLE'),
  ('HEDGEHOG', 'Erizo', 7, 'PREPARATION', 'VISIBLE'),
  ('HORSE', 'Caballo', 8, 'PREPARATION', 'VISIBLE'),
  ('DONKEY', 'Burro', 9, 'PREPARATION', 'VISIBLE'),
  ('PIG', 'Cerdo', 10, 'PREPARATION', 'VISIBLE'),
  ('BIRD', 'Ave', 11, 'PREPARATION', 'VISIBLE'),
  ('REPTILE', 'Reptil', 12, 'PREPARATION', 'VISIBLE'),
  ('FISH', 'Pez', 13, 'PREPARATION', 'VISIBLE'),
  ('AMPHIBIAN', 'Anfibio', 14, 'PREPARATION', 'VISIBLE'),
  ('INVERTEBRATE', 'Invertebrado', 15, 'PREPARATION', 'VISIBLE')
on conflict (code) do update
  set name = excluded.name,
      sort_key = excluded.sort_key,
      catalog_visibility = 'VISIBLE';

update public.species
set
  status = case when code in ('DOG', 'CAT') then 'ACTIVE' else 'PREPARATION' end,
  secondary_classification_enabled = true,
  secondary_classification_kind = case
    when code = 'FERRET' then 'VARIETY'
    when code in ('HAMSTER', 'HEDGEHOG', 'BIRD', 'REPTILE', 'FISH', 'AMPHIBIAN', 'INVERTEBRATE') then 'TYPE'
    else 'BREED'
  end,
  secondary_label_singular = case
    when code = 'FERRET' then 'Variedad'
    when code in ('HAMSTER', 'HEDGEHOG', 'BIRD', 'REPTILE', 'FISH', 'AMPHIBIAN', 'INVERTEBRATE') then 'Tipo'
    else 'Raza'
  end,
  secondary_label_plural = case
    when code = 'FERRET' then 'Variedades'
    when code in ('HAMSTER', 'HEDGEHOG', 'BIRD', 'REPTILE', 'FISH', 'AMPHIBIAN', 'INVERTEBRATE') then 'Tipos'
    else 'Razas'
  end,
  catalog_visibility = 'VISIBLE'
where code in (
  'DOG', 'CAT', 'RABBIT', 'FERRET', 'GUINEA_PIG', 'HAMSTER', 'HEDGEHOG',
  'HORSE', 'DONKEY', 'PIG', 'BIRD', 'REPTILE', 'FISH', 'AMPHIBIAN', 'INVERTEBRATE'
);

update public.species
set
  catalog_visibility = 'LEGACY_HIDDEN',
  status = 'INACTIVE',
  secondary_classification_enabled = false,
  secondary_classification_kind = 'NONE'
where code not in (
  'DOG', 'CAT', 'RABBIT', 'FERRET', 'GUINEA_PIG', 'HAMSTER', 'HEDGEHOG',
  'HORSE', 'DONKEY', 'PIG', 'BIRD', 'REPTILE', 'FISH', 'AMPHIBIAN', 'INVERTEBRATE'
);

-- ---------------------------------------------------------------------------
-- Idempotent secondary seeds (reuse breeds rows; never truncate)
-- ---------------------------------------------------------------------------
create or replace function public._canon_seed_secondary_item(
  p_species_code text,
  p_name text,
  p_sort integer,
  p_aliases text[] default '{}'
)
returns uuid
language plpgsql
as $$
declare
  v_id uuid;
  v_norm text;
  v_alias_norms text[];
begin
  if p_species_code is null or length(trim(p_name)) = 0 then
    return null;
  end if;
  v_norm := public._canon_normalize_catalog_name(p_name);
  select array_agg(public._canon_normalize_catalog_name(a))
    into v_alias_norms
  from unnest(coalesce(p_aliases, '{}')) a
  where length(trim(a)) > 0;

  select b.id into v_id
  from public.breeds b
  where b.species_code = p_species_code
    and (
      public._canon_normalize_catalog_name(b.name) = v_norm
      or (
        v_alias_norms is not null
        and public._canon_normalize_catalog_name(b.name) = any (v_alias_norms)
      )
    )
  order by case when public._canon_normalize_catalog_name(b.name) = v_norm then 0 else 1 end
  limit 1;

  if v_id is null then
    insert into public.breeds (species_code, name, sort_key, active, normalized_name, code)
    values (
      p_species_code,
      trim(p_name),
      coalesce(p_sort, 0),
      true,
      v_norm,
      public._canon_secondary_code(p_name)
    )
    on conflict (species_code, name) do update
      set normalized_name = excluded.normalized_name,
          code = coalesce(public.breeds.code, excluded.code)
    returning id into v_id;
  else
    update public.breeds
      set name = trim(p_name),
          normalized_name = v_norm,
          code = coalesce(code, public._canon_secondary_code(p_name)),
          sort_key = case when sort_key = 0 then coalesce(p_sort, 0) else sort_key end
    where id = v_id
      and public._canon_normalize_catalog_name(name) is distinct from v_norm
      and not exists (
        select 1 from public.breeds x
        where x.species_code = p_species_code
          and x.id <> v_id
          and x.name = trim(p_name)
      );
    update public.breeds
      set normalized_name = coalesce(normalized_name, v_norm),
          code = coalesce(code, public._canon_secondary_code(name))
    where id = v_id;
  end if;
  return v_id;
end;
$$;

revoke all on function public._canon_seed_secondary_item(text, text, integer, text[]) from public, anon, authenticated;

do $$
declare
  r record;
begin
  for r in
    select * from (values
      ('DOG', 'Mestizo', 1, '{}'::text[]),
      ('DOG', 'Sin raza definida', 2, '{}'::text[]),
      ('DOG', 'Labrador Retriever', 3, array['Labrador']),
      ('DOG', 'Golden Retriever', 4, array['Golden']),
      ('DOG', 'Caniche', 5, array['Poodle']),
      ('DOG', 'Bulldog Francés', 6, array['French Bulldog']),
      ('DOG', 'Bulldog Inglés', 7, array['English Bulldog','Bulldog']),
      ('DOG', 'Pastor Alemán', 8, array['German Shepherd']),
      ('DOG', 'Border Collie', 9, '{}'::text[]),
      ('DOG', 'Beagle', 10, '{}'::text[]),
      ('DOG', 'Dachshund', 11, array['Salchicha']),
      ('DOG', 'Chihuahua', 12, '{}'::text[]),
      ('DOG', 'Yorkshire Terrier', 13, array['Yorkshire']),
      ('DOG', 'Shih Tzu', 14, '{}'::text[]),
      ('DOG', 'Maltés', 15, array['Maltese']),
      ('DOG', 'Schnauzer Miniatura', 16, '{}'::text[]),
      ('DOG', 'Schnauzer Estándar', 17, '{}'::text[]),
      ('DOG', 'Schnauzer Gigante', 18, '{}'::text[]),
      ('DOG', 'Rottweiler', 19, '{}'::text[]),
      ('DOG', 'Doberman', 20, array['Dobermann']),
      ('DOG', 'Boxer', 21, '{}'::text[]),
      ('DOG', 'Dogo Argentino', 22, '{}'::text[]),
      ('DOG', 'American Pit Bull Terrier', 23, array['Pit Bull']),
      ('DOG', 'American Staffordshire Terrier', 24, array['Amstaff']),
      ('DOG', 'Staffordshire Bull Terrier', 25, array['Staffy']),
      ('DOG', 'Bull Terrier', 26, '{}'::text[]),
      ('DOG', 'Cane Corso', 27, '{}'::text[]),
      ('DOG', 'Gran Danés', 28, array['Great Dane']),
      ('DOG', 'Akita Inu', 29, array['Akita']),
      ('DOG', 'Shiba Inu', 30, array['Shiba']),
      ('DOG', 'Husky Siberiano', 31, array['Siberian Husky','Husky']),
      ('DOG', 'Samoyedo', 32, array['Samoyed']),
      ('DOG', 'Chow Chow', 33, '{}'::text[]),
      ('DOG', 'Pug', 34, '{}'::text[]),
      ('DOG', 'Cocker Spaniel Inglés', 35, array['Cocker Spaniel','Cocker']),
      ('DOG', 'Cocker Spaniel Americano', 36, '{}'::text[]),
      ('DOG', 'Springer Spaniel Inglés', 37, array['English Springer Spaniel']),
      ('DOG', 'Basset Hound', 38, '{}'::text[]),
      ('DOG', 'Weimaraner', 39, '{}'::text[]),
      ('DOG', 'Pointer Inglés', 40, array['English Pointer','Pointer']),
      ('DOG', 'Setter Irlandés', 41, array['Irish Setter']),
      ('DOG', 'Jack Russell Terrier', 42, array['Jack Russell']),
      ('DOG', 'Fox Terrier', 43, '{}'::text[]),
      ('DOG', 'West Highland White Terrier', 44, array['Westie']),
      ('DOG', 'Scottish Terrier', 45, array['Scottie']),
      ('DOG', 'Boston Terrier', 46, '{}'::text[]),
      ('DOG', 'Bichón Frisé', 47, array['Bichon Frise']),
      ('DOG', 'Bichón Habanero', 48, array['Havanese']),
      ('DOG', 'Pomerania', 49, array['Pomeranian']),
      ('DOG', 'Collie de Pelo Largo', 50, array['Rough Collie','Collie']),
      ('DOG', 'Pastor Australiano', 51, array['Australian Shepherd']),
      ('DOG', 'Pastor Belga Malinois', 52, array['Malinois']),
      ('DOG', 'Pastor Belga Tervueren', 53, array['Tervueren']),
      ('DOG', 'Pastor de Shetland', 54, array['Shetland Sheepdog','Sheltie']),
      ('DOG', 'Australian Cattle Dog', 55, array['Blue Heeler']),
      ('DOG', 'Mastín Napolitano', 56, array['Neapolitan Mastiff']),
      ('DOG', 'San Bernardo', 57, array['Saint Bernard']),
      ('DOG', 'Terranova', 58, array['Newfoundland']),
      ('DOG', 'Boyero de Berna', 59, array['Bernese Mountain Dog']),
      ('DOG', 'Galgo', 60, array['Galgo Español']),
      ('DOG', 'Greyhound', 61, '{}'::text[]),
      ('DOG', 'Whippet', 62, '{}'::text[]),
      ('DOG', 'Borzoi', 63, '{}'::text[]),
      ('DOG', 'Shar Pei', 64, '{}'::text[]),
      ('DOG', 'Lhasa Apso', 65, '{}'::text[]),
      ('DOG', 'Pekinés', 66, array['Pekingese']),
      ('DOG', 'Basenji', 67, '{}'::text[]),
      ('DOG', 'Rhodesian Ridgeback', 68, '{}'::text[]),

      ('CAT', 'Mestizo', 1, '{}'::text[]),
      ('CAT', 'Sin raza definida', 2, '{}'::text[]),
      ('CAT', 'Siamés', 3, array['Siamese']),
      ('CAT', 'Persa', 4, array['Persian']),
      ('CAT', 'Maine Coon', 5, '{}'::text[]),
      ('CAT', 'Ragdoll', 6, '{}'::text[]),
      ('CAT', 'Bengalí', 7, array['Bengal']),
      ('CAT', 'British Shorthair', 8, '{}'::text[]),
      ('CAT', 'Scottish Fold', 9, '{}'::text[]),
      ('CAT', 'Sphynx', 10, '{}'::text[]),
      ('CAT', 'Azul Ruso', 11, array['Russian Blue']),
      ('CAT', 'Bosque de Noruega', 12, array['Norwegian Forest']),
      ('CAT', 'Siberiano', 13, array['Siberian']),
      ('CAT', 'Birmano', 14, array['Birman']),
      ('CAT', 'Abisinio', 15, array['Abyssinian']),
      ('CAT', 'Somalí', 16, array['Somali']),
      ('CAT', 'Devon Rex', 17, '{}'::text[]),
      ('CAT', 'Cornish Rex', 18, '{}'::text[]),
      ('CAT', 'Oriental de Pelo Corto', 19, array['Oriental Shorthair']),
      ('CAT', 'Balinés', 20, array['Balinese']),
      ('CAT', 'Bombay', 21, '{}'::text[]),
      ('CAT', 'Burmés', 22, array['Burmese']),
      ('CAT', 'Angora Turco', 23, array['Turkish Angora']),
      ('CAT', 'Van Turco', 24, array['Turkish Van']),
      ('CAT', 'Exótico de Pelo Corto', 25, array['Exotic Shorthair']),
      ('CAT', 'Himalayo', 26, array['Himalayan']),
      ('CAT', 'American Shorthair', 27, '{}'::text[]),
      ('CAT', 'Manx', 28, '{}'::text[]),
      ('CAT', 'Mau Egipcio', 29, array['Egyptian Mau']),
      ('CAT', 'Ocicat', 30, '{}'::text[]),
      ('CAT', 'Doméstico de Pelo Corto', 31, array['DSH']),
      ('CAT', 'Doméstico de Pelo Largo', 32, array['DLH']),

      ('RABBIT', 'Mestizo', 1, '{}'::text[]),
      ('RABBIT', 'Sin raza definida', 2, '{}'::text[]),
      ('RABBIT', 'Holland Lop', 3, '{}'::text[]),
      ('RABBIT', 'Mini Lop', 4, '{}'::text[]),
      ('RABBIT', 'French Lop', 5, '{}'::text[]),
      ('RABBIT', 'English Lop', 6, '{}'::text[]),
      ('RABBIT', 'Netherland Dwarf', 7, '{}'::text[]),
      ('RABBIT', 'Lionhead', 8, '{}'::text[]),
      ('RABBIT', 'Mini Rex', 9, '{}'::text[]),
      ('RABBIT', 'Rex', 10, '{}'::text[]),
      ('RABBIT', 'Angora Inglés', 11, array['English Angora']),
      ('RABBIT', 'Angora Francés', 12, array['French Angora']),
      ('RABBIT', 'Californiano', 13, array['Californian']),
      ('RABBIT', 'Nueva Zelanda', 14, array['New Zealand']),
      ('RABBIT', 'Gigante de Flandes', 15, array['Flemish Giant']),
      ('RABBIT', 'Dutch', 16, '{}'::text[]),
      ('RABBIT', 'Himalayo', 17, array['Himalayan']),
      ('RABBIT', 'Hotot', 18, '{}'::text[]),
      ('RABBIT', 'Harlequin', 19, '{}'::text[]),
      ('RABBIT', 'Satin', 20, '{}'::text[]),
      ('RABBIT', 'English Spot', 21, '{}'::text[]),
      ('RABBIT', 'Polish', 22, '{}'::text[]),
      ('RABBIT', 'Chinchilla Americano', 23, array['American Chinchilla']),

      ('FERRET', 'Sable', 1, '{}'::text[]),
      ('FERRET', 'Sable oscuro', 2, '{}'::text[]),
      ('FERRET', 'Albino', 3, '{}'::text[]),
      ('FERRET', 'Champagne', 4, '{}'::text[]),
      ('FERRET', 'Chocolate', 5, '{}'::text[]),
      ('FERRET', 'Cinnamon', 6, '{}'::text[]),
      ('FERRET', 'Black Sable', 7, '{}'::text[]),
      ('FERRET', 'Silver', 8, '{}'::text[]),
      ('FERRET', 'Blanco de ojos oscuros', 9, array['DEW']),
      ('FERRET', 'Panda', 10, '{}'::text[]),
      ('FERRET', 'Blaze', 11, '{}'::text[]),

      ('GUINEA_PIG', 'Mestizo', 1, '{}'::text[]),
      ('GUINEA_PIG', 'Sin raza definida', 2, '{}'::text[]),
      ('GUINEA_PIG', 'Americano', 3, array['American']),
      ('GUINEA_PIG', 'Abisinio', 4, array['Abyssinian']),
      ('GUINEA_PIG', 'Peruano', 5, array['Peruvian']),
      ('GUINEA_PIG', 'Sheltie', 6, '{}'::text[]),
      ('GUINEA_PIG', 'Texel', 7, '{}'::text[]),
      ('GUINEA_PIG', 'Coronet', 8, '{}'::text[]),
      ('GUINEA_PIG', 'Teddy', 9, '{}'::text[]),
      ('GUINEA_PIG', 'Rex', 10, '{}'::text[]),
      ('GUINEA_PIG', 'Skinny', 11, '{}'::text[]),
      ('GUINEA_PIG', 'Baldwin', 12, '{}'::text[]),
      ('GUINEA_PIG', 'Alpaca', 13, '{}'::text[]),
      ('GUINEA_PIG', 'Merino', 14, '{}'::text[]),
      ('GUINEA_PIG', 'Lunkarya', 15, '{}'::text[]),

      ('HAMSTER', 'Sirio', 1, array['Syrian']),
      ('HAMSTER', 'Enano ruso de Campbell', 2, array['Campbell']),
      ('HAMSTER', 'Enano ruso de invierno', 3, array['Winter White']),
      ('HAMSTER', 'Roborovski', 4, '{}'::text[]),
      ('HAMSTER', 'Chino', 5, array['Chinese']),
      ('HAMSTER', 'Otro', 6, '{}'::text[]),

      ('HEDGEHOG', 'Pigmeo africano', 1, array['African Pygmy']),
      ('HEDGEHOG', 'Orejudo', 2, '{}'::text[]),
      ('HEDGEHOG', 'Otro', 3, '{}'::text[]),

      ('HORSE', 'Mestizo', 1, '{}'::text[]),
      ('HORSE', 'Sin raza definida', 2, '{}'::text[]),
      ('HORSE', 'Criollo', 3, '{}'::text[]),
      ('HORSE', 'Pura Sangre de Carrera', 4, array['Thoroughbred']),
      ('HORSE', 'Árabe', 5, array['Arabian']),
      ('HORSE', 'Cuarto de Milla', 6, array['Quarter Horse']),
      ('HORSE', 'Appaloosa', 7, '{}'::text[]),
      ('HORSE', 'Paint Horse', 8, '{}'::text[]),
      ('HORSE', 'Percherón', 9, array['Percheron']),
      ('HORSE', 'Frisón', 10, array['Friesian']),
      ('HORSE', 'Andaluz', 11, array['Andalusian']),
      ('HORSE', 'Paso Peruano', 12, '{}'::text[]),
      ('HORSE', 'Mangalarga Marchador', 13, '{}'::text[]),
      ('HORSE', 'Falabella', 14, '{}'::text[]),
      ('HORSE', 'Shetland Pony', 15, '{}'::text[]),
      ('HORSE', 'Welsh Pony', 16, '{}'::text[]),
      ('HORSE', 'Haflinger', 17, '{}'::text[]),
      ('HORSE', 'Morgan', 18, '{}'::text[]),
      ('HORSE', 'Hanoveriano', 19, array['Hanoverian']),
      ('HORSE', 'Holsteiner', 20, '{}'::text[]),
      ('HORSE', 'Trakehner', 21, '{}'::text[]),
      ('HORSE', 'Silla Argentino', 22, '{}'::text[]),

      ('DONKEY', 'Mestizo', 1, '{}'::text[]),
      ('DONKEY', 'Sin raza definida', 2, '{}'::text[]),
      ('DONKEY', 'Criollo', 3, '{}'::text[]),
      ('DONKEY', 'Catalán', 4, '{}'::text[]),
      ('DONKEY', 'Andaluz', 5, '{}'::text[]),
      ('DONKEY', 'Zamorano-Leonés', 6, '{}'::text[]),
      ('DONKEY', 'Poitou', 7, '{}'::text[]),
      ('DONKEY', 'Mammoth Jackstock', 8, '{}'::text[]),
      ('DONKEY', 'Miniatura Mediterráneo', 9, '{}'::text[]),

      ('PIG', 'Mestizo', 1, '{}'::text[]),
      ('PIG', 'Sin raza definida', 2, '{}'::text[]),
      ('PIG', 'Vietnamita', 3, array['Potbelly']),
      ('PIG', 'KuneKune', 4, '{}'::text[]),
      ('PIG', 'Juliana', 5, '{}'::text[]),
      ('PIG', 'Göttingen Minipig', 6, array['Gottingen Minipig']),

      ('BIRD', 'Canario', 1, array['Canary']),
      ('BIRD', 'Periquito australiano', 2, array['Budgerigar','Periquito']),
      ('BIRD', 'Agapornis', 3, array['Lovebird']),
      ('BIRD', 'Ninfa / Carolina', 4, array['Cockatiel','Ninfa']),
      ('BIRD', 'Loro', 5, array['Parrot']),
      ('BIRD', 'Cacatúa', 6, array['Cockatoo']),
      ('BIRD', 'Guacamayo', 7, array['Macaw']),
      ('BIRD', 'Diamante mandarín', 8, '{}'::text[]),
      ('BIRD', 'Diamante de Gould', 9, '{}'::text[]),
      ('BIRD', 'Pinzón', 10, array['Finch']),
      ('BIRD', 'Paloma', 11, '{}'::text[]),
      ('BIRD', 'Tórtola', 12, '{}'::text[]),
      ('BIRD', 'Codorniz', 13, '{}'::text[]),
      ('BIRD', 'Gallina', 14, '{}'::text[]),
      ('BIRD', 'Pato', 15, '{}'::text[]),
      ('BIRD', 'Ganso', 16, '{}'::text[]),
      ('BIRD', 'Otro', 17, '{}'::text[]),

      ('REPTILE', 'Tortuga terrestre', 1, '{}'::text[]),
      ('REPTILE', 'Tortuga acuática', 2, '{}'::text[]),
      ('REPTILE', 'Iguana', 3, '{}'::text[]),
      ('REPTILE', 'Gecko', 4, '{}'::text[]),
      ('REPTILE', 'Camaleón', 5, '{}'::text[]),
      ('REPTILE', 'Dragón barbudo', 6, array['Bearded Dragon']),
      ('REPTILE', 'Serpiente', 7, '{}'::text[]),
      ('REPTILE', 'Lagarto / Tegú', 8, array['Tegu']),
      ('REPTILE', 'Escinco', 9, array['Skink']),
      ('REPTILE', 'Anolis', 10, '{}'::text[]),
      ('REPTILE', 'Otro', 11, '{}'::text[]),

      ('FISH', 'Betta', 1, '{}'::text[]),
      ('FISH', 'Goldfish', 2, '{}'::text[]),
      ('FISH', 'Guppy', 3, '{}'::text[]),
      ('FISH', 'Molly', 4, '{}'::text[]),
      ('FISH', 'Platy', 5, '{}'::text[]),
      ('FISH', 'Xipho / Cola de espada', 6, array['Swordtail','Xipho']),
      ('FISH', 'Tetra', 7, '{}'::text[]),
      ('FISH', 'Corydora', 8, array['Corydoras']),
      ('FISH', 'Pleco', 9, '{}'::text[]),
      ('FISH', 'Escalar', 10, array['Angelfish']),
      ('FISH', 'Disco', 11, array['Discus']),
      ('FISH', 'Cíclido', 12, array['Cichlid']),
      ('FISH', 'Koi', 13, '{}'::text[]),
      ('FISH', 'Gourami', 14, '{}'::text[]),
      ('FISH', 'Danio', 15, '{}'::text[]),
      ('FISH', 'Rasbora', 16, '{}'::text[]),
      ('FISH', 'Killifish', 17, '{}'::text[]),
      ('FISH', 'Botia', 18, '{}'::text[]),
      ('FISH', 'Oscar', 19, '{}'::text[]),
      ('FISH', 'Otro', 20, '{}'::text[]),

      ('AMPHIBIAN', 'Axolote', 1, array['Axolotl']),
      ('AMPHIBIAN', 'Rana arborícola', 2, '{}'::text[]),
      ('AMPHIBIAN', 'Rana enana africana', 3, '{}'::text[]),
      ('AMPHIBIAN', 'Rana cornuda / Pacman', 4, array['Pacman']),
      ('AMPHIBIAN', 'Dendrobátido', 5, '{}'::text[]),
      ('AMPHIBIAN', 'Salamandra', 6, '{}'::text[]),
      ('AMPHIBIAN', 'Tritón', 7, '{}'::text[]),
      ('AMPHIBIAN', 'Sapo', 8, '{}'::text[]),
      ('AMPHIBIAN', 'Otro', 9, '{}'::text[]),

      ('INVERTEBRATE', 'Araña', 1, '{}'::text[]),
      ('INVERTEBRATE', 'Tarántula', 2, '{}'::text[]),
      ('INVERTEBRATE', 'Escorpión', 3, '{}'::text[]),
      ('INVERTEBRATE', 'Mantis', 4, '{}'::text[]),
      ('INVERTEBRATE', 'Insecto palo', 5, '{}'::text[]),
      ('INVERTEBRATE', 'Caracol', 6, '{}'::text[]),
      ('INVERTEBRATE', 'Cangrejo ermitaño', 7, '{}'::text[]),
      ('INVERTEBRATE', 'Milpiés', 8, '{}'::text[]),
      ('INVERTEBRATE', 'Ciempiés', 9, '{}'::text[]),
      ('INVERTEBRATE', 'Escarabajo', 10, '{}'::text[]),
      ('INVERTEBRATE', 'Otro', 11, '{}'::text[])
    ) as t(species_code, name, sort_key, aliases)
  loop
    perform public._canon_seed_secondary_item(r.species_code, r.name, r.sort_key, r.aliases);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- Health products: many-to-many scope (do not invent medical mappings)
-- ---------------------------------------------------------------------------
create table if not exists public.pet_health_product_species (
  product_id uuid not null references public.pet_health_products(id) on delete restrict,
  species_id uuid not null references public.species(id) on delete restrict,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (product_id, species_id)
);

alter table public.pet_health_product_species enable row level security;
revoke all on table public.pet_health_product_species from anon, authenticated;

insert into public.pet_health_product_species (product_id, species_id)
select p.id, s.id
from public.pet_health_products p
join public.species s on s.code = p.species_code
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- JSON helpers
-- ---------------------------------------------------------------------------
create or replace function public._canon_species_status_label(p_status text)
returns text
language sql
immutable
as $$
  select case p_status
    when 'PREPARATION' then 'En preparación'
    when 'ACTIVE' then 'Activa'
    when 'INACTIVE' then 'Inactiva'
    else coalesce(p_status, '')
  end;
$$;

create or replace function public._canon_species_row_json(s public.species)
returns jsonb
language plpgsql
stable
as $$
begin
  return jsonb_build_object(
    'id', s.id,
    'code', s.code,
    'name', s.name,
    'sort_key', s.sort_key,
    'active', s.active,
    'status', s.status,
    'status_label', public._canon_species_status_label(s.status),
    'secondary_classification_enabled', s.secondary_classification_enabled,
    'secondary_classification_kind', s.secondary_classification_kind,
    'secondary_label_singular', s.secondary_label_singular,
    'secondary_label_plural', s.secondary_label_plural,
    'catalog_visibility', s.catalog_visibility,
    'code_in_use', exists (select 1 from public.pets p where p.species_code = s.code)
  );
end;
$$;

create or replace function public._canon_resolve_species_code(p_species_id text)
returns text
language plpgsql
stable
as $$
declare
  v_code text;
begin
  if p_species_id is null or length(trim(p_species_id)) = 0 then
    return null;
  end if;
  if p_species_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    select code into v_code from public.species where id = p_species_id::uuid;
    if v_code is not null then
      return v_code;
    end if;
  end if;
  return upper(trim(p_species_id));
end;
$$;

create or replace function public._canon_secondary_item_json(b public.breeds)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'id', b.id,
    'species_id', (select s.id from public.species s where s.code = b.species_code),
    'species_code', b.species_code,
    'name', b.name,
    'code', b.code,
    'active', b.active,
    'sort_order', b.sort_key,
    'sort_key', b.sort_key
  );
$$;

-- ---------------------------------------------------------------------------
-- Read RPCs (additive fields; existing consumers keep working)
-- ---------------------------------------------------------------------------
create or replace function public.canon_list_species()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return coalesce((
    select jsonb_agg(public._canon_species_row_json(s) order by s.sort_key, s.name)
    from public.species s
    where s.status = 'ACTIVE'
      and s.catalog_visibility = 'VISIBLE'
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_species(p_species_id text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_code text;
  v_row public.species;
begin
  v_code := public._canon_resolve_species_code(p_species_id);
  if v_code is null then
    return null;
  end if;
  select * into v_row from public.species where code = v_code;
  if not found then
    return null;
  end if;
  return public._canon_species_row_json(v_row);
end;
$$;

create or replace function public.canon_list_breeds(p_species_code text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return coalesce((
    select jsonb_agg(public._canon_secondary_item_json(b) order by b.sort_key, b.name)
    from public.breeds b
    where b.active and b.species_code = p_species_code
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_get_breed(p_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_row public.breeds;
begin
  if p_id is null then return null; end if;
  select * into v_row from public.breeds where id = p_id;
  if not found then return null; end if;
  return public._canon_secondary_item_json(v_row);
end;
$$;

create or replace function public.canon_list_species_secondary_items(p_species_id text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_code text;
begin
  v_code := public._canon_resolve_species_code(p_species_id);
  if v_code is null then
    return '[]'::jsonb;
  end if;
  return public.canon_list_breeds(v_code);
end;
$$;

create or replace function public.canon_get_species_secondary_item(p_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return public.canon_get_breed(p_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- Admin species
-- ---------------------------------------------------------------------------
create or replace function public.canon_admin_list_species()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.has_permission('catalogs.view') then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(public._canon_species_row_json(s) order by s.sort_key, s.name)
    from public.species s
    where s.catalog_visibility = 'VISIBLE'
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_upsert_species(
  p_code text,
  p_name text,
  p_sort_key integer,
  p_active boolean,
  p_status text default null,
  p_secondary_enabled boolean default null,
  p_secondary_kind text default null,
  p_secondary_label_singular text default null,
  p_secondary_label_plural text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text;
  v_prev public.species;
  v_row public.species;
  v_status text;
  v_kind text;
  v_enabled boolean;
  v_created boolean := false;
  v_pets_with_secondary boolean;
begin
  if not public.has_permission('catalogs.manage') then raise exception 'FORBIDDEN'; end if;
  if p_code is null or length(trim(p_code)) = 0 then raise exception 'VALIDATION'; end if;
  if p_name is null or length(trim(p_name)) < 2 then raise exception 'VALIDATION'; end if;
  v_code := upper(trim(regexp_replace(regexp_replace(p_code, '[^a-zA-Z0-9]+', '_', 'g'), '_+', '_', 'g')));
  v_code := trim(both '_' from v_code);
  if v_code is null or length(v_code) = 0 then raise exception 'VALIDATION'; end if;

  select * into v_prev from public.species where code = v_code;
  v_created := v_prev.code is null;

  v_status := coalesce(
    nullif(upper(trim(p_status)), ''),
    v_prev.status,
    case when coalesce(p_active, true) then 'ACTIVE' else 'INACTIVE' end
  );
  if v_status not in ('PREPARATION', 'ACTIVE', 'INACTIVE') then
    raise exception 'VALIDATION';
  end if;

  v_enabled := coalesce(p_secondary_enabled, v_prev.secondary_classification_enabled, false);
  v_kind := coalesce(nullif(upper(trim(p_secondary_kind)), ''), v_prev.secondary_classification_kind, 'NONE');
  if v_kind not in ('NONE', 'BREED', 'TYPE', 'VARIETY', 'CUSTOM') then
    raise exception 'VALIDATION';
  end if;
  if not v_enabled then
    v_kind := 'NONE';
  elsif v_kind = 'NONE' then
    v_enabled := false;
  end if;

  if not v_created and v_kind = 'NONE' and v_prev.secondary_classification_enabled then
    select exists (
      select 1 from public.pets p
      where p.species_code = v_code and p.breed_id is not null
    ) into v_pets_with_secondary;
    if v_pets_with_secondary then
      raise exception 'SPECIES_SECONDARY_IN_USE';
    end if;
  end if;

  if v_created then
    insert into public.species (
      code, name, sort_key, status, catalog_visibility,
      secondary_classification_enabled, secondary_classification_kind,
      secondary_label_singular, secondary_label_plural
    ) values (
      v_code, trim(p_name), coalesce(p_sort_key, 100), v_status, 'VISIBLE',
      v_enabled, v_kind,
      nullif(trim(p_secondary_label_singular), ''),
      nullif(trim(p_secondary_label_plural), '')
    )
    returning * into v_row;
  else
    update public.species
      set name = trim(p_name),
          sort_key = coalesce(p_sort_key, sort_key),
          status = v_status,
          secondary_classification_enabled = v_enabled,
          secondary_classification_kind = v_kind,
          secondary_label_singular = coalesce(nullif(trim(p_secondary_label_singular), ''), secondary_label_singular),
          secondary_label_plural = coalesce(nullif(trim(p_secondary_label_plural), ''), secondary_label_plural)
    where code = v_code
    returning * into v_row;
  end if;

  perform public._canon_admin_audit(
    case
      when v_created then 'SPECIES_CREATED'
      when v_prev.status is distinct from v_row.status then 'SPECIES_STATUS_CHANGED'
      else 'SPECIES_UPDATED'
    end,
    'species', v_row.id,
    jsonb_build_object(
      'code', v_row.code,
      'status', v_row.status,
      'secondary_kind', v_row.secondary_classification_kind
    )
  );
  return public._canon_species_row_json(v_row);
end;
$$;

create or replace function public.canon_admin_set_species_status(p_species_id text, p_status text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text;
  v_row public.species;
  v_prev text;
begin
  if not public.has_permission('catalogs.manage') then raise exception 'FORBIDDEN'; end if;
  v_code := public._canon_resolve_species_code(p_species_id);
  if v_code is null or upper(trim(p_status)) not in ('PREPARATION', 'ACTIVE', 'INACTIVE') then
    raise exception 'VALIDATION';
  end if;
  select status into v_prev from public.species where code = v_code;
  if v_prev is null then raise exception 'NOT_FOUND'; end if;
  update public.species
    set status = upper(trim(p_status))
  where code = v_code
  returning * into v_row;
  perform public._canon_admin_audit(
    'SPECIES_STATUS_CHANGED',
    'species', v_row.id,
    jsonb_build_object('code', v_row.code, 'from', v_prev, 'to', v_row.status)
  );
  return public._canon_species_row_json(v_row);
end;
$$;

-- Drop 4-arg overload so PostgREST does not fail on ambiguous canon_admin_upsert_species.
drop function if exists public.canon_admin_upsert_species(text, text, integer, boolean);

-- ---------------------------------------------------------------------------
-- Admin secondary items
-- ---------------------------------------------------------------------------
create or replace function public.canon_admin_upsert_species_secondary_item(
  p_id uuid,
  p_species_id text,
  p_name text,
  p_sort_order integer,
  p_active boolean,
  p_code text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text;
  v_id uuid;
  v_prev boolean;
  v_row public.breeds;
  v_norm text;
  v_item_code text;
  v_created boolean := false;
begin
  if not public.has_permission('catalogs.manage') then raise exception 'FORBIDDEN'; end if;
  v_code := public._canon_resolve_species_code(p_species_id);
  if v_code is null or length(trim(p_name)) = 0 then raise exception 'VALIDATION'; end if;
  v_norm := public._canon_normalize_catalog_name(p_name);
  v_item_code := coalesce(nullif(public._canon_secondary_code(p_code), ''), public._canon_secondary_code(p_name));

  if exists (
    select 1 from public.breeds b
    where b.species_code = v_code
      and public._canon_normalize_catalog_name(b.name) = v_norm
      and (p_id is null or b.id <> p_id)
  ) then
    raise exception 'DUPLICATE';
  end if;

  if p_id is not null then
    select active into v_prev from public.breeds where id = p_id;
    if v_prev is null then raise exception 'NOT_FOUND'; end if;
  else
    v_created := true;
  end if;

  if p_id is null then
    insert into public.breeds (species_code, name, sort_key, active, normalized_name, code)
    values (v_code, trim(p_name), coalesce(p_sort_order, 0), coalesce(p_active, true), v_norm, v_item_code)
    on conflict (species_code, name) do update
      set sort_key = excluded.sort_key,
          active = excluded.active,
          normalized_name = excluded.normalized_name,
          code = coalesce(public.breeds.code, excluded.code)
    returning * into v_row;
  else
    update public.breeds
      set name = trim(p_name),
          sort_key = coalesce(p_sort_order, sort_key),
          active = coalesce(p_active, active),
          normalized_name = v_norm,
          code = coalesce(v_item_code, code)
    where id = p_id
    returning * into v_row;
  end if;

  perform public._canon_admin_audit(
    case
      when v_created then 'SPECIES_SECONDARY_CREATED'
      when v_prev is distinct from v_row.active then 'SPECIES_SECONDARY_STATUS_CHANGED'
      else 'SPECIES_SECONDARY_UPDATED'
    end,
    'breeds', v_row.id,
    jsonb_build_object('species', v_code, 'name', v_row.name, 'active', v_row.active)
  );
  return public._canon_secondary_item_json(v_row);
end;
$$;

create or replace function public.canon_admin_set_species_secondary_item_status(p_id uuid, p_active boolean)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.breeds;
  v_prev boolean;
begin
  if not public.has_permission('catalogs.manage') then raise exception 'FORBIDDEN'; end if;
  if p_id is null then raise exception 'VALIDATION'; end if;
  select active into v_prev from public.breeds where id = p_id;
  if v_prev is null then raise exception 'NOT_FOUND'; end if;
  update public.breeds
    set active = coalesce(p_active, active)
  where id = p_id
  returning * into v_row;
  perform public._canon_admin_audit(
    'SPECIES_SECONDARY_STATUS_CHANGED',
    'breeds', v_row.id,
    jsonb_build_object('from', v_prev, 'to', v_row.active, 'name', v_row.name)
  );
  return public._canon_secondary_item_json(v_row);
end;
$$;

-- Existing breed admin writes keep working and now fill normalized_name.
create or replace function public.canon_admin_upsert_breed(
  p_id uuid, p_species_code text, p_name text, p_sort_key integer, p_active boolean
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_json jsonb;
begin
  v_json := public.canon_admin_upsert_species_secondary_item(
    p_id, p_species_code, p_name, p_sort_key, p_active, null
  );
  return (v_json->>'id')::uuid;
end;
$$;

create or replace function public.canon_admin_list_breeds(p_species_code text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.has_permission('catalogs.view') then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(public._canon_secondary_item_json(b) order by b.species_code, b.sort_key, b.name)
    from public.breeds b
    where p_species_code is null or b.species_code = p_species_code
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Admin health product species
-- ---------------------------------------------------------------------------
create or replace function public.canon_admin_list_pet_health_products(p_kind text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.has_permission('catalogs.view') then raise exception 'FORBIDDEN'; end if;
  return coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'id', p.id,
        'kind', p.kind,
        'code', p.code,
        'display_name', p.display_name,
        'species_code', p.species_code,
        'active', p.active,
        'sort_order', p.sort_order,
        'species_ids', coalesce((
          select jsonb_agg(s.id order by s.sort_key, s.name)
          from public.pet_health_product_species j
          join public.species s on s.id = j.species_id
          where j.product_id = p.id
        ), '[]'::jsonb),
        'species_codes', coalesce((
          select jsonb_agg(s.code order by s.sort_key, s.name)
          from public.pet_health_product_species j
          join public.species s on s.id = j.species_id
          where j.product_id = p.id
        ), '[]'::jsonb),
        'unscoped', not exists (
          select 1 from public.pet_health_product_species j where j.product_id = p.id
        )
      )
      order by p.kind, p.sort_order, p.display_name
    )
    from public.pet_health_products p
    where p_kind is null or p.kind = p_kind
  ), '[]'::jsonb);
end;
$$;

create or replace function public.canon_admin_set_health_product_species(
  p_product_id uuid,
  p_species_ids text[]
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_codes text[] := '{}';
  v_id text;
  v_code text;
  v_species_uuid uuid;
begin
  if not public.has_permission('catalogs.manage') then raise exception 'FORBIDDEN'; end if;
  if p_product_id is null then raise exception 'VALIDATION'; end if;
  if not exists (select 1 from public.pet_health_products where id = p_product_id) then
    raise exception 'NOT_FOUND';
  end if;

  delete from public.pet_health_product_species where product_id = p_product_id;

  if p_species_ids is not null then
    foreach v_id in array p_species_ids loop
      v_code := public._canon_resolve_species_code(v_id);
      if v_code is null then
        continue;
      end if;
      select id into v_species_uuid from public.species where code = v_code;
      if v_species_uuid is null then
        continue;
      end if;
      insert into public.pet_health_product_species (product_id, species_id)
      values (p_product_id, v_species_uuid)
      on conflict do nothing;
      v_codes := array_append(v_codes, v_code);
    end loop;
  end if;

  perform public._canon_admin_audit(
    'HEALTH_PRODUCT_SPECIES_CHANGED',
    'pet_health_products', p_product_id,
    jsonb_build_object('species_codes', to_jsonb(v_codes))
  );
  return public.canon_admin_list_pet_health_products(null);
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
revoke all on function public.canon_get_species(text) from public;
revoke all on function public.canon_list_species_secondary_items(text) from public;
revoke all on function public.canon_get_species_secondary_item(uuid) from public;
revoke all on function public.canon_admin_set_species_status(text, text) from public, anon;
revoke all on function public.canon_admin_upsert_species_secondary_item(uuid, text, text, integer, boolean, text) from public, anon;
revoke all on function public.canon_admin_set_species_secondary_item_status(uuid, boolean) from public, anon;
revoke all on function public.canon_admin_set_health_product_species(uuid, text[]) from public, anon;

grant execute on function public.canon_list_species() to authenticated, anon;
grant execute on function public.canon_get_species(text) to authenticated, anon;
grant execute on function public.canon_list_breeds(text) to authenticated, anon;
grant execute on function public.canon_get_breed(uuid) to authenticated, anon;
grant execute on function public.canon_list_species_secondary_items(text) to authenticated, anon;
grant execute on function public.canon_get_species_secondary_item(uuid) to authenticated, anon;
grant execute on function public.canon_admin_list_species() to authenticated;
grant execute on function public.canon_admin_upsert_species(text, text, integer, boolean, text, boolean, text, text, text) to authenticated;
grant execute on function public.canon_admin_set_species_status(text, text) to authenticated;
grant execute on function public.canon_admin_list_breeds(text) to authenticated;
grant execute on function public.canon_admin_upsert_breed(uuid, text, text, integer, boolean) to authenticated;
grant execute on function public.canon_admin_upsert_species_secondary_item(uuid, text, text, integer, boolean, text) to authenticated;
grant execute on function public.canon_admin_set_species_secondary_item_status(uuid, boolean) to authenticated;
grant execute on function public.canon_admin_list_pet_health_products(text) to authenticated;
grant execute on function public.canon_admin_set_health_product_species(uuid, text[]) to authenticated;

notify pgrst, 'reload schema';
