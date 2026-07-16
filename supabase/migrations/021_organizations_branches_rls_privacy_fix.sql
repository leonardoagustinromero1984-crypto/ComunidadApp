-- M03 Etapa 5 — Corrective: branch SELECT must not expose private contact_phone.
-- Depends on: 020_organizations_teams_invitations_branches.sql
-- Do not edit prior migrations.
-- Issue: organization_branches_select allowed any authenticated user to SELECT
-- ACTIVE branches of ACTIVE/RESTRICTED orgs, returning full rows including
-- contact_phone even when contact_phone_public = false.
-- Public projection remains via list_organization_branches(..., include_private=false)
-- which uses org_branch_public_json (allowlist).

drop policy if exists organization_branches_select on public.organization_branches;

create policy organization_branches_select
    on public.organization_branches for select to authenticated
    using (
        public.has_org_permission(organization_id, 'organization.view_private')
    );

-- Keep SELECT grant for members with view_private; writes still via RPC only.
-- Anonymous/public consumers must use list_organization_branches RPC (already granted to anon).
