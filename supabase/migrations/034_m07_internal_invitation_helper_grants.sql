-- LeoVer M07 — migración 034: grants del helper interno de invitaciones
-- Defecto staging: EXECUTE directo residual a anon (y authenticated) sobre
-- public._resolve_invitation_by_token(text), SECURITY DEFINER.
-- No edita 001–033. No cambia firma, cuerpo, search_path ni security mode.

begin;

-- Helper interno: no RPC pública. Solo service_role (y owner implícito).
-- Callers SECURITY DEFINER del mismo owner siguen pudiendo invocarlo.
revoke all on function public._resolve_invitation_by_token(text) from public;
revoke all on function public._resolve_invitation_by_token(text) from anon;
revoke all on function public._resolve_invitation_by_token(text) from authenticated;

grant execute on function public._resolve_invitation_by_token(text) to service_role;

comment on function public._resolve_invitation_by_token(text) is
  'M07-034: internal invitation resolver; EXECUTE revoked from anon/authenticated; service_role retained. Body/security/search_path unchanged.';

commit;
