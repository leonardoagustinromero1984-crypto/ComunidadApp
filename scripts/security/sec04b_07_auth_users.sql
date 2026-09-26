select count(*)::int as auth_users,
       count(*) filter (where email_confirmed_at is not null)::int as email_confirmed,
       count(*) filter (where last_sign_in_at is not null)::int as ever_signed_in,
       count(*) filter (where last_sign_in_at is null)::int as never_signed_in
  from auth.users;
