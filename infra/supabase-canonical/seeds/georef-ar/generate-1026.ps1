# Regenerates the 1026 Argentina catalog SQL from official Georef JSON.
# Runtime LeoVer never calls Georef; this is a build-time import only.
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$provPath = Join-Path $here "provincias.json"
$locPath = Join-Path $here "localidades.json"
$outSql = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) "supabase\migrations\20260816200000_1026_argentina_complete_geography_catalog.sql"
$outCsv = Join-Path $here "localidades-canonical.csv"
$outManifest = Join-Path $here "SOURCE.json"

$provinceByGeoref = @{
  "02" = "loc-ar-prov-caba"
  "06" = "loc-ar-prov-buenos-aires"
  "10" = "loc-ar-prov-catamarca"
  "14" = "loc-ar-prov-cordoba"
  "18" = "loc-ar-prov-corrientes"
  "22" = "loc-ar-prov-chaco"
  "26" = "loc-ar-prov-chubut"
  "30" = "loc-ar-prov-entre-rios"
  "34" = "loc-ar-prov-formosa"
  "38" = "loc-ar-prov-jujuy"
  "42" = "loc-ar-prov-la-pampa"
  "46" = "loc-ar-prov-la-rioja"
  "50" = "loc-ar-prov-mendoza"
  "54" = "loc-ar-prov-misiones"
  "58" = "loc-ar-prov-neuquen"
  "62" = "loc-ar-prov-rio-negro"
  "66" = "loc-ar-prov-salta"
  "70" = "loc-ar-prov-san-juan"
  "74" = "loc-ar-prov-san-luis"
  "78" = "loc-ar-prov-santa-cruz"
  "82" = "loc-ar-prov-santa-fe"
  "86" = "loc-ar-prov-santiago"
  "90" = "loc-ar-prov-tucuman"
  "94" = "loc-ar-prov-tierra-del-fuego"
}

function CategoryRank([string]$cat) {
  switch ($cat) {
    "Localidad simple" { 0 }
    "Componente de localidad compuesta" { 1 }
    "Entidad" { 2 }
    default { 9 }
  }
}

function SqlLit([string]$value) {
  return "'" + ($value -replace "'", "''") + "'"
}

$provincias = (Get-Content -Raw -Encoding UTF8 $provPath | ConvertFrom-Json).provincias
$localidades = (Get-Content -Raw -Encoding UTF8 $locPath | ConvertFrom-Json).localidades
if ($provincias.Count -ne 24) { throw "Expected 24 Georef provinces, got $($provincias.Count)" }

$best = @{}
foreach ($row in $localidades) {
  $parent = $provinceByGeoref[$row.provincia.id]
  if (-not $parent) { throw "Unmapped Georef province $($row.provincia.id)" }
  $key = $parent + "|" + $row.nombre.Trim().ToLowerInvariant()
  $candidate = [pscustomobject]@{
    GeorefId = [string]$row.id
    ParentId = $parent
    Name     = [string]$row.nombre
    Category = [string]$row.categoria
    Rank     = CategoryRank $row.categoria
  }
  if (-not $best.ContainsKey($key) -or $candidate.Rank -lt $best[$key].Rank -or (
      $candidate.Rank -eq $best[$key].Rank -and $candidate.GeorefId -lt $best[$key].GeorefId
    )) {
    $best[$key] = $candidate
  }
}

$chosen = $best.Values | Sort-Object ParentId, Name, GeorefId
$csv = New-Object System.Text.StringBuilder
[void]$csv.AppendLine("leover_id,parent_id,name,georef_id,categoria")
$values = New-Object System.Text.StringBuilder
$i = 0
foreach ($row in $chosen) {
  $leoverId = "loc-ar-loc-georef-" + $row.GeorefId
  [void]$csv.AppendLine(($leoverId + "," + $row.ParentId + "," + ('"' + ($row.Name -replace '"', '""') + '"') + "," + $row.GeorefId + "," + $row.Category))
  if ($i -gt 0) { [void]$values.AppendLine(",") }
  [void]$values.Append("  (" + (SqlLit $leoverId) + ", " + (SqlLit $row.ParentId) + ", " + (SqlLit $row.Name) + ", " + (SqlLit $row.GeorefId) + ")")
  $i++
}

$manifest = [ordered]@{
  source = "GEOREF_AR"
  source_api = "https://apis.datos.gob.ar/georef/api/v2.1"
  source_resources = @(
    "https://apis.datos.gob.ar/georef/api/v2.1/provincias.json?campos=id,nombre,iso_id,nombre_completo&max=100"
    "https://apis.datos.gob.ar/georef/api/v2.1/localidades.json?campos=id,nombre,provincia,categoria&max=5000"
  )
  source_date = "2026-08-16"
  source_version = "georef-api-v2.1"
  georef_province_count = $provincias.Count
  georef_locality_raw_count = $localidades.Count
  imported_locality_count = $chosen.Count
  dedup_rule = "unique (province, official name); prefer Localidad simple, then componente, then entidad; keep existing LeoVer IDs"
}

$sql = @"
-- LeoVer Canonical Forward
-- Logical migration: 1026
-- 1026_argentina_complete_geography_catalog
--
-- Official source: GEOREF_AR (Georef Argentina v2.1 complete download, 2026-08-16).
-- Runtime does not call Georef. This file is generated from
-- infra/supabase-canonical/seeds/georef-ar/ by generate-1026.ps1.
--
-- Preserves existing country/province IDs and the 14 baseline locality IDs.
-- New localities use deterministic IDs loc-ar-loc-georef-{georef_id}.
-- iso_code on new/matched LOCALITY rows stores the Georef source_external_id.
-- Idempotent: ON CONFLICT DO NOTHING + skip when (parent, lower(name)) exists.

create or replace function public.canon_list_location_catalog()
returns jsonb
language sql
stable
security definer
set search_path = public
as `$`$
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', n.id,
        'kind', n.kind,
        'parent_id', n.parent_id,
        'name', n.name,
        'iso_code', n.iso_code,
        'sort_key', n.sort_key,
        'active', n.active
      )
      order by n.kind, n.sort_key, n.name
    ),
    '[]'::jsonb
  )
  from public.location_nodes n
  where n.active
    and n.kind in ('PROVINCE', 'LOCALITY');
`$`$;

revoke all on function public.canon_list_location_catalog() from public;
grant execute on function public.canon_list_location_catalog() to anon, authenticated;

with src(id, parent_id, name, georef_id) as (
values
$($values.ToString())
)
update public.location_nodes n
set iso_code = src.georef_id
from src
where n.kind = 'LOCALITY'
  and n.parent_id = src.parent_id
  and lower(n.name) = lower(src.name)
  and (n.iso_code is null or n.iso_code = src.georef_id);

with src(id, parent_id, name, georef_id) as (
values
$($values.ToString())
)
insert into public.location_nodes (id, kind, parent_id, name, iso_code, sort_key)
select src.id, 'LOCALITY', src.parent_id, src.name, src.georef_id, 100
from src
where not exists (
  select 1
  from public.location_nodes n
  where n.kind = 'LOCALITY'
    and n.parent_id = src.parent_id
    and lower(n.name) = lower(src.name)
)
on conflict (id) do nothing;
"@

$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($outCsv, $csv.ToString(), $utf8)
[System.IO.File]::WriteAllText($outManifest, ($manifest | ConvertTo-Json -Depth 6), $utf8)
[System.IO.File]::WriteAllText($outSql, $sql, $utf8)
Write-Host "IMPORTED_LOCALITIES=$($chosen.Count)"
Write-Host "SQL=$outSql"
Write-Host "CSV=$outCsv"
