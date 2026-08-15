-- LeoVer Canonical Baseline
-- Logical migration: 1012
-- M20 person↔person and person↔organization. No guardian auto-read.

create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  subject_kind text not null check (subject_kind in (
    'PERSON', 'ORGANIZATION', 'PLATFORM_SUPPORT'
  )),
  created_by uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now()),
  archived_at timestamptz null
);

create table public.conversation_participants (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  participant_kind text not null check (participant_kind in ('PERSON', 'ORGANIZATION')),
  person_id uuid null references public.persons(user_id),
  organization_id uuid null references public.organizations(id),
  joined_at timestamptz not null default timezone('utc', now()),
  left_at timestamptz null,
  constraint conversation_participant_xor check (
    public.holder_xor_ok(participant_kind, person_id, organization_id)
  )
);

create unique index conversation_participants_key_uidx
  on public.conversation_participants (
    conversation_id,
    coalesce(person_id, '00000000-0000-0000-0000-000000000000'::uuid),
    coalesce(organization_id, '00000000-0000-0000-0000-000000000000'::uuid)
  );

create index conversation_participants_person_idx
  on public.conversation_participants (person_id);
create index conversation_participants_org_idx
  on public.conversation_participants (organization_id);

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  actor_user_id uuid not null references public.persons(user_id),
  body text not null,
  created_at timestamptz not null default timezone('utc', now()),
  hidden_at timestamptz null
);

create index messages_conversation_created_idx
  on public.messages (conversation_id, created_at);

create table public.message_blocks (
  blocker_user_id uuid not null references public.persons(user_id),
  blocked_user_id uuid not null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now()),
  primary key (blocker_user_id, blocked_user_id),
  constraint message_blocks_not_self check (blocker_user_id <> blocked_user_id)
);
