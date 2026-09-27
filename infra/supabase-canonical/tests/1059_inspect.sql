select
  to_regprocedure('public.canon_get_species(text)') is not null as get_species,
  to_regprocedure('public.canon_list_species_secondary_items(text)') is not null as list_secondary,
  to_regprocedure('public.canon_get_species_secondary_item(uuid)') is not null as get_secondary,
  to_regprocedure('public.canon_admin_set_species_status(text, text)') is not null as set_status,
  to_regprocedure('public.canon_admin_upsert_species_secondary_item(uuid, text, text, integer, boolean, text)') is not null as upsert_secondary,
  to_regprocedure('public.canon_admin_set_health_product_species(uuid, text[])') is not null as set_product_species,
  exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'species' and column_name = 'status'
  ) as species_status,
  exists (
    select 1 from information_schema.tables
    where table_schema = 'public' and table_name = 'pet_health_product_species'
  ) as health_junction,
  (select count(*) from public.species where catalog_visibility = 'VISIBLE') as visible_species,
  (select count(*) from public.species where code in ('DOG','CAT') and status = 'ACTIVE') as dog_cat_active,
  (select count(*) from public.species
    where code in ('RABBIT','FERRET','BIRD') and status = 'PREPARATION') as sample_prep,
  (select count(*) from public.breeds b
    where b.species_code = 'DOG' and b.name = 'Labrador Retriever') as dog_labrador,
  (select count(*) from public.breeds b
    where b.species_code = 'BIRD' and b.name = 'Canario') as bird_canario,
  (select count(*) from public.breeds b
    where b.species_code = 'FERRET' and b.name = 'Sable') as ferret_sable,
  (select count(*) from public.pet_health_products p
    where p.species_code is null
      and not exists (
        select 1 from public.pet_health_product_species j where j.product_id = p.id
      )) as unscoped_products;
