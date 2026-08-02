-- LeoVer M22 — migración 067: corrección CHECK cobertura RADIUS (forward-only sobre 066).
-- Defecto: coverage_radius_km NULL satisfacía el CHECK por evaluación NULL en BETWEEN.
-- LOCAL ONLY hasta apply remoto autorizado; no modifica 066 ya aplicada.

begin;

alter table public.m22_provider_branches
  drop constraint if exists m22_branch_coverage_chk;

alter table public.m22_provider_branches
  add constraint m22_branch_coverage_chk check (
    (coverage_type = 'CITY' and coverage_neighborhood is null and coverage_radius_km is null)
    or (
      coverage_type = 'NEIGHBORHOOD'
      and char_length(trim(coalesce(coverage_neighborhood, ''))) between 2 and 120
      and coverage_radius_km is null
    )
    or (
      coverage_type = 'RADIUS'
      and coverage_neighborhood is null
      and coverage_radius_km is not null
      and coverage_radius_km between 1 and 200
    )
  );

commit;
