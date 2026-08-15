-- LeoVer Canonical Baseline
-- Logical migration: 1004
-- Legal documents, consent events, privacy requests, guardian, tutorials.
-- Documents are DRAFT PRE-LAUNCH. No marketing consent. Tutorial != consent.

create table public.legal_documents (
  id uuid primary key default gen_random_uuid(),
  type text not null check (type in (
    'TERMS', 'PRIVACY', 'COMMUNITY_RULES', 'CONTEXTUAL'
  )),
  version text not null,
  locale text not null default 'es-AR',
  effective_from date null,
  content_hash text not null,
  status text not null default 'DRAFT'
    check (status in ('DRAFT', 'EFFECTIVE', 'RETIRED')),
  published_at timestamptz null,
  consent_code text null,
  created_at timestamptz not null default timezone('utc', now())
);

create unique index legal_documents_type_version_locale_uidx
  on public.legal_documents (type, version, locale, coalesce(consent_code, ''));

create index legal_documents_current_idx
  on public.legal_documents (type, locale, status, effective_from);

create table public.legal_consent_events (
  id uuid primary key default gen_random_uuid(),
  subject_user_id uuid not null references public.persons(user_id),
  actor_user_id uuid not null references public.persons(user_id),
  document_id uuid not null references public.legal_documents(id),
  event_type text not null check (event_type in (
    'ACCEPT', 'WITHDRAW', 'MINOR_ASSENT', 'GUARDIAN_CONSENT',
    'CONTEXTUAL_GRANT', 'CONTEXTUAL_WITHDRAW'
  )),
  occurred_at timestamptz not null default timezone('utc', now()),
  source text not null default 'APP',
  evidence jsonb not null default '{}'::jsonb,
  metadata jsonb not null default '{}'::jsonb
);

create index legal_consent_events_subject_doc_idx
  on public.legal_consent_events (subject_user_id, document_id);

create table public.privacy_requests (
  id uuid primary key default gen_random_uuid(),
  subject_user_id uuid not null references public.persons(user_id),
  requester_user_id uuid not null references public.persons(user_id),
  request_type text not null check (request_type in (
    'ACCESS', 'RECTIFICATION', 'UPDATE', 'ERASURE'
  )),
  status text not null default 'OPEN'
    check (status in ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'CANCELLED')),
  resolution text null,
  processed_by uuid null references public.persons(user_id),
  created_at timestamptz not null default timezone('utc', now()),
  completed_at timestamptz null
);

create index privacy_requests_subject_status_idx
  on public.privacy_requests (subject_user_id, status);

create table public.privacy_request_events (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.privacy_requests(id) on delete cascade,
  actor_user_id uuid null references public.persons(user_id),
  from_status text null,
  to_status text not null,
  note text null,
  occurred_at timestamptz not null default timezone('utc', now())
);

create table public.guardian_relationships (
  id uuid primary key default gen_random_uuid(),
  minor_user_id uuid not null references public.persons(user_id),
  adult_user_id uuid not null references public.persons(user_id),
  requested_by uuid not null references public.persons(user_id),
  status text not null default 'PENDING'
    check (status in ('PENDING', 'ACTIVE', 'ENDED', 'DECLINED')),
  accepted_at timestamptz null,
  ended_at timestamptz null,
  verification_method text not null default 'ACCOUNT_CONFIRMED'
    check (verification_method in ('SELF_DECLARED', 'ACCOUNT_CONFIRMED', 'DOCUMENT_VERIFIED')),
  created_at timestamptz not null default timezone('utc', now()),
  constraint guardian_not_self check (minor_user_id <> adult_user_id)
);

create unique index guardian_relationships_active_pair_uidx
  on public.guardian_relationships (minor_user_id, adult_user_id)
  where status in ('PENDING', 'ACTIVE');

create index guardian_relationships_minor_status_idx
  on public.guardian_relationships (minor_user_id, status);

create table public.guardian_relationship_events (
  id uuid primary key default gen_random_uuid(),
  relationship_id uuid not null references public.guardian_relationships(id) on delete cascade,
  actor_user_id uuid not null references public.persons(user_id),
  event_type text not null,
  occurred_at timestamptz not null default timezone('utc', now()),
  metadata jsonb not null default '{}'::jsonb
);

create table public.tutorial_progress (
  user_id uuid not null references public.persons(user_id) on delete cascade,
  tutorial_key text not null,
  version text not null default '1',
  viewed_at timestamptz null,
  skipped_at timestamptz null,
  completed_at timestamptz null,
  reopened_at timestamptz null,
  updated_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, tutorial_key)
);
