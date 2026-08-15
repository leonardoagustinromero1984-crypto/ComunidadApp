# LeoVer Canonical Supabase (REBASE-03C)

Isolated workdir. **Never** point this CLI at `wystsapjfpdtoprlmizz`.

```text
LEGACY_SUPABASE_WORKDIR     = supabase/          (repo root; 001–082 history)
CANONICAL_SUPABASE_WORKDIR  = infra/supabase-canonical/
OLD_REFERENCE_BACKEND       = wystsapjfpdtoprlmizz
STAGING_PROJECT_REF         = tobqbddfcyitwgbkthhy
CANONICAL_LOGICAL_RANGE     = 1000–1022
```

Run all CLI commands with `--workdir infra/supabase-canonical`.

Do not `supabase start` / Docker on this machine unless explicitly authorized.
Do not `db reset --linked` unless the printed project-ref is `tobqbddfcyitwgbkthhy`.
