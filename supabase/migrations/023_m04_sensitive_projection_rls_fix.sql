-- M04 Etapa 5 — Corrección mínima de proyecciones sensibles / RLS (defectos reales de 022)
-- No edita 022. No crea Storage. No usa bucket leover.
-- Remoto / staging: PENDIENTE DE VALIDACIÓN REMOTA

-- =============================================================================
-- Defecto 1: content_reports SELECT directo exponía reporter_id / reason_detail
-- a staff con moderation.view|manage_reports, bypasseando proyección RPC.
-- Fix: SELECT directo solo reporter propio; staff usa RPC (list_moderation_queue /
-- get_content_report_for_staff) que redactan sin moderation.view_sensitive.
-- =============================================================================
drop policy if exists content_reports_select on public.content_reports;
create policy content_reports_select on public.content_reports
    for select to authenticated
    using (reporter_id = auth.uid());

comment on table public.content_reports is
    'M04: SELECT directo solo reporter propio. Staff lee vía RPC; reporter_id sensible sin moderation.view_sensitive.';

-- =============================================================================
-- Defecto 2: support_ticket_messages SELECT permitía support.view|manage sin
-- respetar categoría PRIVACY/SAFETY del ticket padre (bypass de support.view_sensitive).
-- =============================================================================
drop policy if exists support_ticket_messages_select on public.support_ticket_messages;
create policy support_ticket_messages_select on public.support_ticket_messages
    for select to authenticated
    using (
        (
            visibility = 'REQUESTER_VISIBLE'
            and exists (
                select 1 from public.support_tickets t
                where t.id = ticket_id and t.requester_user_id = auth.uid()
            )
        )
        or exists (
            select 1 from public.support_tickets t
            where t.id = ticket_id
              and (
                  public.has_permission('support.view')
                  or public.has_permission('support.manage')
              )
              and (
                  t.category not in ('PRIVACY', 'SAFETY')
                  or public.has_permission('support.view_sensitive')
              )
        )
    );

-- =============================================================================
-- Defecto 3a: organization_verification_reviews SELECT directo exponía review_note
-- a cualquier revisor. Lectura de nota vía RPC allowlist (3b).
-- =============================================================================
drop policy if exists organization_verification_reviews_select on public.organization_verification_reviews;
create policy organization_verification_reviews_select on public.organization_verification_reviews
    for select to authenticated
    using (false);

revoke select on public.organization_verification_reviews from authenticated;

-- =============================================================================
-- Defecto 3b: get_organization_verification_review devolvía row_to_json(r) con
-- review_note anidado aunque el campo top-level estuviera redactado.
-- =============================================================================
create or replace function public.get_organization_verification_review(p_review_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    actor uuid;
    r public.organization_verification_reviews;
    docs jsonb;
    can_sensitive boolean;
    review_json jsonb;
begin
    actor := public.m04_require_active_actor();
    perform public.m04_require_permission('organizations.review_verification');
    can_sensitive := public.has_permission('moderation.view_sensitive')
        or public.has_permission('support.view_sensitive');

    select * into r from public.organization_verification_reviews where id = p_review_id;
    if r.id is null then
        raise exception 'NOT_FOUND';
    end if;

    select coalesce(jsonb_agg(row_to_json(d)::jsonb), '[]'::jsonb)
    into docs
    from public.organization_verification_document_refs d
    where d.organization_id = r.organization_id
      and (d.review_id is null or d.review_id = r.id);

    review_json := jsonb_build_object(
        'id', r.id,
        'organization_id', r.organization_id,
        'requested_by_user_id', r.requested_by_user_id,
        'assigned_to_user_id', r.assigned_to_user_id,
        'status', r.status,
        'decision', r.decision,
        'created_at', r.created_at,
        'updated_at', r.updated_at,
        'decided_at', r.decided_at,
        'decided_by_user_id', r.decided_by_user_id
    );
    -- review_note solo en proyección top-level cuando hay permiso sensible
    if can_sensitive then
        review_json := review_json || jsonb_build_object('review_note', r.review_note);
    end if;

    return jsonb_build_object(
        'review', review_json,
        'documents', case when can_sensitive then docs else '[]'::jsonb end,
        'review_note', case when can_sensitive then to_jsonb(r.review_note) else 'null'::jsonb end
    );
end;
$$;

revoke all on function public.get_organization_verification_review(uuid) from public;
grant execute on function public.get_organization_verification_review(uuid) to authenticated;

comment on function public.get_organization_verification_review(uuid) is
    'M04 Etapa 5: proyección allowlist; review_note solo con permiso sensible.';
