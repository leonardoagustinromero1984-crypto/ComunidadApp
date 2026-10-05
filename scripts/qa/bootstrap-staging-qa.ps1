# LeoVer STAGING QA bootstrap. Never targets production or legacy.
# Creates missing QA01-QA16 auth users. Reuses existing ones without changing passwords.
# Persons come from seed_community_care_test_actors.sql.
# Shelters A/B come from bootstrap_staging_qa_shelters.sql.
# Does not run the community-care 02 dataset and does not attach orgs to real persons.
param()

$ErrorActionPreference = "Stop"
$ExpectedRef = "tobqbddfcyitwgbkthhy"
$LegacyRef = "wystsapjfpdtoprlmizz"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Workdir = Join-Path $RepoRoot "infra\supabase-canonical"
$QaDir = Join-Path $Workdir "qa"
$SqlActors = Join-Path $QaDir "seed_community_care_test_actors.sql"
$SqlShelters = Join-Path $QaDir "bootstrap_staging_qa_shelters.sql"
$LocalSecret = Join-Path $PSScriptRoot ".qa-community-care-actors.local.json"

$Actors = @(
    @{ Id = "QA01"; Email = "qa01.owner@leoverapp.com"; Username = "qa01owner"; Name = "QA01 Owner" }
    @{ Id = "QA02"; Email = "qa02.finder@leoverapp.com"; Username = "qa02finder"; Name = "QA02 Finder" }
    @{ Id = "QA03"; Email = "qa03.rescuer@leoverapp.com"; Username = "qa03rescuer"; Name = "QA03 Rescuer Near" }
    @{ Id = "QA04"; Email = "qa04.rescuer2@leoverapp.com"; Username = "qa04rescuer2"; Name = "QA04 Rescuer Second" }
    @{ Id = "QA05"; Email = "qa05.unavail@leoverapp.com"; Username = "qa05unavailable"; Name = "QA05 Rescuer Unavailable" }
    @{ Id = "QA06"; Email = "qa06.foster@leoverapp.com"; Username = "qa06foster"; Name = "QA06 Foster" }
    @{ Id = "QA07"; Email = "qa07.shelter@leoverapp.com"; Username = "qa07shelter"; Name = "QA07 Shelter Verified" }
    @{ Id = "QA08"; Email = "qa08.pending@leoverapp.com"; Username = "qa08pending"; Name = "QA08 Shelter Pending" }
    @{ Id = "QA09"; Email = "qa09.noreq@leoverapp.com"; Username = "qa09noreq"; Name = "QA09 Shelter Not Requested" }
    @{ Id = "QA10"; Email = "qa10.vet@leoverapp.com"; Username = "qa10vet"; Name = "QA10 Veterinary Admin" }
    @{ Id = "QA11"; Email = "qa11.pro.a@leoverapp.com"; Username = "qa11proa"; Name = "QA11 Veterinary Professional A" }
    @{ Id = "QA12"; Email = "qa12.pro.ind@leoverapp.com"; Username = "qa12proind"; Name = "QA12 Professional Independent" }
    @{ Id = "QA13"; Email = "qa13.shop@leoverapp.com"; Username = "qa13shop"; Name = "QA13 Business Admin" }
    @{ Id = "QA14"; Email = "qa14.adopter.a@leoverapp.com"; Username = "qa14adopter"; Name = "QA14 Adopter A" }
    @{ Id = "QA15"; Email = "qa15.adopter.b@leoverapp.com"; Username = "qa15adopter2"; Name = "QA15 Adopter B" }
    @{ Id = "QA16"; Email = "qa16.vet.norte@leoverapp.com"; Username = "qa16vetnorte"; Name = "QA16 Veterinary Professional B" }
)

function Assert-StagingOnly {
    if (-not (Test-Path $Workdir)) {
        throw "QA_BOOTSTRAP_ABORT_WORKDIR_MISSING"
    }
    $refFile = Join-Path $Workdir "supabase\.temp\project-ref"
    $linkedJson = Join-Path $Workdir "supabase\.temp\linked-project.json"
    $poolerFile = Join-Path $Workdir "supabase\.temp\pooler-url"
    $linked = $null
    if (Test-Path $refFile) {
        $linked = (Get-Content $refFile -Raw).Trim()
    }
    if ([string]::IsNullOrWhiteSpace($linked) -and (Test-Path $linkedJson)) {
        $json = Get-Content $linkedJson -Raw | ConvertFrom-Json
        $linked = [string]$json.ref
    }
    if ([string]::IsNullOrWhiteSpace($linked)) {
        throw "QA_BOOTSTRAP_ABORT_UNLINKED"
    }
    if ($linked -eq $LegacyRef) {
        throw "QA_BOOTSTRAP_ABORT_LEGACY_PROJECT"
    }
    if ($linked -ne $ExpectedRef) {
        throw "QA_BOOTSTRAP_ABORT_UNKNOWN_PROJECT"
    }
    if (Test-Path $poolerFile) {
        $pooler = Get-Content $poolerFile -Raw
        if ($pooler -match $LegacyRef) { throw "QA_BOOTSTRAP_ABORT_LEGACY_POOLER" }
        if ($pooler -notmatch $ExpectedRef) { throw "QA_BOOTSTRAP_ABORT_POOLER_REF_MISMATCH" }
    }
    Write-Host "STAGING_ONLY_GUARD=PASS ref=$ExpectedRef"
}

function Get-StagingApiKey([string]$Name) {
    $keysRaw = & supabase --workdir $Workdir projects api-keys --project-ref $ExpectedRef --reveal --output json
    if ($LASTEXITCODE -ne 0) {
        throw "QA_BOOTSTRAP_API_KEYS_FAILED"
    }
    $keys = $keysRaw | ConvertFrom-Json
    foreach ($k in @($keys)) {
        if ([string]$k.name -eq $Name) {
            $value = [string]$k.api_key
            if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
        }
    }
    throw "QA_BOOTSTRAP_API_KEY_MISSING"
}

function Get-QaPassword {
    if (-not [string]::IsNullOrWhiteSpace($env:LEOVER_QA_PASSWORD)) {
        return [string]$env:LEOVER_QA_PASSWORD
    }
    if (Test-Path $LocalSecret) {
        $doc = Get-Content $LocalSecret -Raw | ConvertFrom-Json
        $pwd = [string]$doc.password
        if (-not [string]::IsNullOrWhiteSpace($pwd)) { return $pwd }
    }
    throw "QA_PASSWORD_MISSING"
}

function Get-AdminHeaders([string]$Service) {
    return @{
        apikey         = $Service
        Authorization  = "Bearer $Service"
        "Content-Type" = "application/json"
    }
}

function Get-AuthUserIdByEmail([hashtable]$Headers, [string]$Url, [string]$Email) {
    $page = 1
    do {
        $resp = Invoke-RestMethod -Method Get -Uri "$Url/auth/v1/admin/users?page=$page&per_page=200" -Headers $Headers
        $users = @($resp.users)
        foreach ($u in $users) {
            if ([string]$u.email -and ([string]$u.email).ToLowerInvariant() -eq $Email.ToLowerInvariant()) {
                return [string]$u.id
            }
        }
        if ($users.Count -lt 200) { break }
        $page++
    } while ($page -le 20)
    return $null
}

function Ensure-AuthActors {
    $allowed = @{}
    foreach ($actor in $Actors) { $allowed[$actor.Email.ToLowerInvariant()] = $true }
    $service = Get-StagingApiKey "service_role"
    $url = "https://$ExpectedRef.supabase.co"
    $headers = Get-AdminHeaders $service
    $password = $null
    foreach ($actor in $Actors) {
        if (-not $allowed.ContainsKey($actor.Email.ToLowerInvariant())) {
            throw "QA_BOOTSTRAP_ABORT_REAL_USER"
        }
        if ($actor.Email -notmatch '^qa\d{2}\.[a-z0-9.]+@leoverapp\.com$') {
            throw "QA_BOOTSTRAP_ABORT_REAL_USER"
        }
        $id = Get-AuthUserIdByEmail $headers $url $actor.Email
        if ([string]::IsNullOrWhiteSpace($id)) {
            if ($null -eq $password) { $password = Get-QaPassword }
            $body = @{
                email         = $actor.Email
                password      = $password
                email_confirm = $true
                user_metadata = @{
                    username     = $actor.Username
                    display_name = $actor.Name
                    birth_date   = "1990-01-15"
                }
            } | ConvertTo-Json -Compress
            try {
                Invoke-RestMethod -Method Post -Uri "$url/auth/v1/admin/users" -Headers $headers -Body $body | Out-Null
            } catch {
                throw "QA_BOOTSTRAP_AUTH_CREATE_FAILED $($actor.Id)"
            }
            Write-Host "AUTH_CREATED $($actor.Id)"
        } else {
            Write-Host "AUTH_REUSED $($actor.Id)"
        }
    }
}

function Invoke-StagingSqlFile([string]$File) {
    $raw = & supabase --output json --workdir $Workdir db query --linked --file $File
    $text = if ($raw -is [string]) { $raw } else { ($raw | Out-String) }
    if ($text -match "Failed to run sql query" -or $text -match "QA_BOOTSTRAP_ABORT_" -or $text -match "QA_ACTORS_ABORT_") {
        throw "QA_BOOTSTRAP_SQL_FAILED"
    }
    if ($LASTEXITCODE -ne 0 -and $text -notmatch '"boundary"') {
        throw "QA_BOOTSTRAP_SQL_FAILED"
    }
    return $text
}

function Invoke-GuardedSql([string]$SourceFile, [string]$Marker) {
    $original = Get-Content $SourceFile -Raw -Encoding UTF8
    if ($original -notmatch [regex]::Escape($Marker)) {
        throw "QA_BOOTSTRAP_SQL_MISSING_INJECT_MARKER"
    }
    $injection = @"
$Marker
  perform set_config('leover.staging_reset.project_ref', '$ExpectedRef', true);
"@
    $injected = $original.Replace($Marker, $injection.TrimEnd())
    $tmp = Join-Path $env:TEMP ("leover-qa-bootstrap-" + [guid]::NewGuid().ToString("N") + ".sql")
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($tmp, $injected, $utf8)
    try {
        Invoke-StagingSqlFile $tmp | Out-Null
    } finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}

Assert-StagingOnly
Ensure-AuthActors
Invoke-GuardedSql $SqlActors "begin -- QA_ACTORS_INJECT_GUC"
Write-Host "SQL_ACTORS=OK"
Invoke-GuardedSql $SqlShelters "begin -- QA_BOOTSTRAP_INJECT_GUC"
Write-Host "SQL_SHELTERS=OK ref=$ExpectedRef orgs=qa-cc02-shelter-n,qa-cc02-shelter-u"
