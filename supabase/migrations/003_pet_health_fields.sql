-- Add extended pet health fields (run if you already applied 001_initial_schema.sql)

alter table public.pets add column if not exists deworming_product text;
alter table public.pets add column if not exists flea_treatment_product text;
alter table public.pets add column if not exists sterilized text;
alter table public.pets add column if not exists microchip_id text;
alter table public.pets add column if not exists last_vet_visit text;
alter table public.pets add column if not exists health_notes text;
