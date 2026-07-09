-- Leover — Privacidad de perfil configurable por usuario

alter table public.users
    add column if not exists profile_private boolean not null default true;

-- Personas privadas por defecto; cuentas de refugio/negocio públicas por defecto
update public.users
set profile_private = (account_type = 'PERSON')
where profile_private is distinct from (account_type = 'PERSON');

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    resolved_account_type text := coalesce(new.raw_user_meta_data ->> 'account_type', 'PERSON');
begin
    insert into public.users (id, email, name, account_type, email_verified, profile_private)
    values (
        new.id,
        coalesce(new.email, ''),
        coalesce(new.raw_user_meta_data ->> 'name', split_part(coalesce(new.email, ''), '@', 1)),
        resolved_account_type,
        new.email_confirmed_at is not null,
        resolved_account_type = 'PERSON'
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
