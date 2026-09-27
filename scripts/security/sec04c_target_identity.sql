-- SEC-04C target identity check. Must run on wystsapjfpdtoprlmizz only.
select to_regclass('public.persons') is not null as has_persons,
       to_regclass('public.users') is not null as has_legacy_users,
       to_regclass('public.platform_admin_identities') is not null as has_canon_admin;
