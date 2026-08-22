-- AUTH-04 read-only. No emails printed. No mutation.
select json_build_object(
  'auth_users_total', (select count(*)::int from auth.users),
  'persons_total', (select count(*)::int from public.persons),
  'unconfirmed_auth_users', (
    select count(*)::int from auth.users where email_confirmed_at is null
  ),
  'recent_24h', (
    select coalesce(json_agg(row_to_json(t) order by t.created_at desc), '[]'::json)
    from (
      select
        left(u.id::text, 8) || '…' as id_prefix,
        (u.email_confirmed_at is not null) as email_confirmed,
        (u.confirmed_at is not null) as confirmed_at_set,
        (p.user_id is not null) as person_exists,
        p.username is not null as person_has_username,
        (p.home_locality_id is not null) as person_has_locality,
        u.created_at
      from auth.users u
      left join public.persons p on p.user_id = u.id
      where u.created_at > timezone('utc', now()) - interval '48 hours'
      order by u.created_at desc
      limit 20
    ) t
  )
);
