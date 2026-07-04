-- Auto-create public.users row when a new auth user signs up.
-- Required when "Confirm email" is ON (no session → RLS blocks client insert).

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.users (id, email, name, account_type, email_verified)
    values (
        new.id,
        coalesce(new.email, ''),
        coalesce(new.raw_user_meta_data ->> 'name', split_part(coalesce(new.email, ''), '@', 1)),
        coalesce(new.raw_user_meta_data ->> 'account_type', 'PERSON'),
        new.email_confirmed_at is not null
    )
    on conflict (id) do update set
        email = excluded.email,
        name = excluded.name,
        account_type = excluded.account_type,
        email_verified = excluded.email_verified,
        updated_at = timezone('utc', now());
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();
