-- 1065: SEC-FINAL least privilege. Does not edit 1057–1064.
-- Residual table DML grants were unused by Android (RPC-only writes).
-- RLS already denied INSERT (0 insert policies). Revoke the extra grants.

revoke insert, update, delete on table
  public.commercial_offer_snapshots,
  public.country_markets,
  public.pet_friendly_venue_subtypes,
  public.pet_org_external_ids,
  public.pet_rescuer_external_ids,
  public.provider_weekly_hours,
  public.vitacora_import_jobs,
  public.vitacora_import_rows
from anon, authenticated;

notify pgrst, 'reload schema';
