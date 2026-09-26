-- READ ONLY. Keep these lists in sync with scripts/qa/reset-staging.sql.
-- Classifies live auth tables for dry-run. Never mutates.
with preserve(name) as (
  values
    ('custom_oauth_providers'),
    ('instances'),
    ('oauth_clients'),
    ('saml_providers'),
    ('schema_migrations'),
    ('scim_tokens'),
    ('scim_users'),
    ('sso_domains'),
    ('sso_providers')
),
wipe(name) as (
  values
    ('audit_log_entries'),
    ('flow_state'),
    ('identities'),
    ('mfa_amr_claims'),
    ('mfa_challenges'),
    ('mfa_factors'),
    ('mfa_recovery_code_sets'),
    ('mfa_recovery_codes'),
    ('oauth_authorizations'),
    ('oauth_client_states'),
    ('oauth_consents'),
    ('one_time_tokens'),
    ('refresh_tokens'),
    ('saml_relay_states'),
    ('sessions'),
    ('users'),
    ('webauthn_challenges'),
    ('webauthn_credentials')
)
select c.relname as table_name,
       case
         when p.name is not null then 'PRESERVE'
         when w.name is not null then 'CLEAN'
         else 'UNKNOWN'
       end as handling,
       (xpath('//row/c/text()', query_to_xml(format('select count(*)::text as c from auth.%I', c.relname), false, true, '')))[1]::text::bigint as row_count
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
left join preserve p on p.name = c.relname
left join wipe w on w.name = c.relname
where n.nspname = 'auth'
  and c.relkind = 'r'
order by 2, 1;
