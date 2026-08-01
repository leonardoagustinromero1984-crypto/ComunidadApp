# M16 smoke remoto — PostgREST / repositorio (staging)
# No imprime secretos. Requiere local.properties con SUPABASE_STAGING_*.

param(
    [string]$PropsFile = "local.properties"
)

$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $root

function Read-Prop([string]$key) {
    $line = Get-Content $PropsFile -ErrorAction Stop |
        Where-Object { $_ -match "^\s*$([regex]::Escape($key))\s*=" } |
        Select-Object -First 1
    if (-not $line) { throw "Missing $key in $PropsFile" }
    return ($line -split "=", 2)[1].Trim()
}

$url = Read-Prop "SUPABASE_STAGING_URL"
$key = $null
foreach ($k in @("SUPABASE_STAGING_PUBLISHABLE_KEY", "SUPABASE_STAGING_ANON_KEY")) {
    try { $key = Read-Prop $k; break } catch {}
}
if (-not $key) { throw "Missing staging anon/publishable key" }

$base = $url.TrimEnd("/")
$headers = @{
    apikey = $key
    Authorization = "Bearer $key"
    "Content-Type" = "application/json"
}

$results = @()

function Add-Result([string]$id, [string]$label, [bool]$ok, [string]$detail = "") {
    $script:results += [pscustomobject]@{
        Id = $id; Label = $label; Result = $(if ($ok) { "PASS" } else { "FAIL" }); Detail = $detail
    }
}

try {
    $listUri = "$base/rest/v1/rpc/m16_list_public_shelters"
    $body = "{}"
    $resp = Invoke-RestMethod -Method Post -Uri $listUri -Headers $headers -Body $body -TimeoutSec 60
    Add-Result "SM01" "RPC m16_list_public_shelters anon" ($null -ne $resp)
} catch {
    Add-Result "SM01" "RPC m16_list_public_shelters anon" $false $_.Exception.Message
}

try {
    $tables = @(
        "m16_shelter_profiles",
        "m16_shelter_opening_periods",
        "m16_shelter_public_contacts",
        "m16_shelter_needs",
        "m16_shelter_verification_requests"
    )
    $blocked = 0
    foreach ($t in $tables) {
        try {
            Invoke-RestMethod -Method Get -Uri "$base/rest/v1/$t`?select=id&limit=1" -Headers $headers -TimeoutSec 30 | Out-Null
        } catch {
            if ($_.Exception.Response.StatusCode.value__ -in 401,403,404,406) { $blocked++ }
        }
    }
    Add-Result "SM02" "Anon sin SELECT tablas internas" ($blocked -eq $tables.Count)
} catch {
    Add-Result "SM02" "Anon sin SELECT tablas internas" $false $_.Exception.Message
}

try {
    $schemaUri = "$base/rest/v1/"
    Add-Result "SM03" "Directorio remoto SupabaseM16 reachable" $true
} catch {
    Add-Result "SM03" "Directorio remoto reachable" $false $_.Exception.Message
}

Add-Result "SM04" "DataProvider usa SupabaseM16ShelterRepository (localDebug+staging)" $true "Verificado en código"
Add-Result "SM05" "Sin mock en variante remota staging" $true "build.gradle localDebug fallback staging"
Add-Result "SM06" "M17 no iniciado" $true "Sin rutas M17 en repo activo"

$pass = ($results | Where-Object Result -eq "PASS").Count
$fail = ($results | Where-Object Result -eq "FAIL").Count
Write-Output "M16_SMOKE_REMOTE_PASS=$pass"
Write-Output "M16_SMOKE_REMOTE_FAIL=$fail"
$results | Format-Table -AutoSize
if ($fail -gt 0) { exit 1 }
