# LeoVer STAGING QA actors. Never targets production or legacy.
# Auth via Admin API. Domain rows via SQL. Password is local-only.
param(
    [switch]$SkipCommunityExtras,
    [switch]$SkipLoginCheck
)

$ErrorActionPreference = "Stop"
$ExpectedRef = "tobqbddfcyitwgbkthhy"
$LegacyRef = "wystsapjfpdtoprlmizz"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Workdir = Join-Path $RepoRoot "infra\supabase-canonical"
$QaDir = Join-Path $Workdir "qa"
$SqlActors = Join-Path $QaDir "seed_community_care_test_actors.sql"
$SqlValidate = Join-Path $QaDir "seed_community_care_test_actors_validate.sql"
$SqlCommunity = Join-Path $QaDir "seed_community_care_02.sql"
$LocalSecret = Join-Path $PSScriptRoot ".qa-community-care-actors.local.json"

$UsernameLoginSample = @(
    "QA01", "QA02", "QA03", "QA06", "QA07", "QA10", "QA11", "QA14", "QA16"
)

$Actors = @(
    @{ Id = "QA01"; Email = "qa01.owner@leoverapp.com"; Username = "qa01owner"; Name = "QA01 Owner"; Function = "PERSONAL"; Verification = "N/A"; Availability = "N/A"; Use = "LOST / VitaCora / QR / owner / adopter" }
    @{ Id = "QA02"; Email = "qa02.finder@leoverapp.com"; Username = "qa02finder"; Name = "QA02 Finder"; Function = "PERSONAL"; Verification = "N/A"; Availability = "N/A"; Use = "FOUND / custodio inicial" }
    @{ Id = "QA03"; Email = "qa03.rescuer@leoverapp.com"; Username = "qa03rescuer"; Name = "QA03 Rescuer Near"; Function = "RESCUER"; Verification = "VERIFIED"; Availability = "TRUE"; Use = "FOUND nearest responder" }
    @{ Id = "QA04"; Email = "qa04.rescuer2@leoverapp.com"; Username = "qa04rescuer2"; Name = "QA04 Rescuer Second"; Function = "RESCUER"; Verification = "VERIFIED"; Availability = "TRUE"; Use = "FOUND fanout segundo" }
    @{ Id = "QA05"; Email = "qa05.unavail@leoverapp.com"; Username = "qa05unavailable"; Name = "QA05 Rescuer Unavailable"; Function = "RESCUER"; Verification = "VERIFIED"; Availability = "FALSE"; Use = "NO debe recibir FOUND" }
    @{ Id = "QA06"; Email = "qa06.foster@leoverapp.com"; Username = "qa06foster"; Name = "QA06 Foster"; Function = "FOSTER"; Verification = "VERIFIED"; Availability = "TRUE"; Use = "Tránsito. Nunca FOUND" }
    @{ Id = "QA07"; Email = "qa07.shelter@leoverapp.com"; Username = "qa07shelter"; Name = "QA07 Shelter Verified"; Function = "SHELTER"; Verification = "VERIFIED"; Availability = "TRUE"; Use = "FOUND / adopción / tránsito" }
    @{ Id = "QA08"; Email = "qa08.pending@leoverapp.com"; Username = "qa08pending"; Name = "QA08 Shelter Pending"; Function = "SHELTER"; Verification = "PENDING"; Availability = "FALSE"; Use = "UI pending + exclusión fanout" }
    @{ Id = "QA09"; Email = "qa09.noreq@leoverapp.com"; Username = "qa09noreq"; Name = "QA09 Shelter Not Requested"; Function = "SHELTER"; Verification = "NOT_REQUESTED"; Availability = "FALSE"; Use = "Solicitar verificación (sin enviar)" }
    @{ Id = "QA10"; Email = "qa10.vet@leoverapp.com"; Username = "qa10vet"; Name = "QA10 Veterinary Admin"; Function = "VETERINARY"; Verification = "VERIFIED"; Availability = "N/A"; Use = "Community / consultorio / patients" }
    @{ Id = "QA11"; Email = "qa11.pro.a@leoverapp.com"; Username = "qa11proa"; Name = "QA11 Veterinary Professional A"; Function = "PROFESSIONAL"; Verification = "VERIFIED"; Availability = "N/A"; Use = "Historial privado QA10" }
    @{ Id = "QA12"; Email = "qa12.pro.ind@leoverapp.com"; Username = "qa12proind"; Name = "QA12 Professional Independent"; Function = "PROFESSIONAL"; Verification = "VERIFIED"; Availability = "N/A"; Use = "Community / acceso directo" }
    @{ Id = "QA13"; Email = "qa13.shop@leoverapp.com"; Username = "qa13shop"; Name = "QA13 Business Admin"; Function = "BUSINESS"; Verification = "UNVERIFIED"; Availability = "N/A"; Use = "Aún no verificado" }
    @{ Id = "QA14"; Email = "qa14.adopter.a@leoverapp.com"; Username = "qa14adopter"; Name = "QA14 Adopter A"; Function = "PERSONAL"; Verification = "N/A"; Availability = "N/A"; Use = "Postular adopción" }
    @{ Id = "QA15"; Email = "qa15.adopter.b@leoverapp.com"; Username = "qa15adopter2"; Name = "QA15 Adopter B"; Function = "PERSONAL"; Verification = "N/A"; Availability = "N/A"; Use = "Multi-aplicación / pause" }
    @{ Id = "QA16"; Email = "qa16.vet.norte@leoverapp.com"; Username = "qa16vetnorte"; Name = "QA16 Veterinary Professional B"; Function = "PROFESSIONAL+VET"; Verification = "VERIFIED"; Availability = "N/A"; Use = "Aislamiento historial Norte" }
)

function Assert-StagingOnly {
    if (-not (Test-Path $Workdir)) {
        throw "QA_ACTORS_ABORT_WORKDIR_MISSING: $Workdir"
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
        throw "QA_ACTORS_ABORT_UNLINKED"
    }
    if ($linked -eq $LegacyRef) {
        throw "QA_ACTORS_ABORT_LEGACY_PROJECT"
    }
    if ($linked -ne $ExpectedRef) {
        throw "QA_ACTORS_ABORT_UNKNOWN_PROJECT:$linked"
    }
    if (Test-Path $poolerFile) {
        $pooler = Get-Content $poolerFile -Raw
        if ($pooler -match $LegacyRef) { throw "QA_ACTORS_ABORT_LEGACY_POOLER" }
        if ($pooler -notmatch $ExpectedRef) { throw "QA_ACTORS_ABORT_POOLER_REF_MISMATCH" }
    }
    Write-Host "STAGING_ONLY_GUARD=PASS ref=$ExpectedRef"
}

function Get-StagingApiKey([string]$Name) {
    $keysRaw = & supabase --workdir $Workdir projects api-keys --project-ref $ExpectedRef --reveal --output json
    if ($LASTEXITCODE -ne 0) {
        throw "QA_ACTORS_API_KEYS_FAILED"
    }
    $keys = $keysRaw | ConvertFrom-Json
    foreach ($k in @($keys)) {
        if ([string]$k.name -eq $Name) {
            $value = [string]$k.api_key
            if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
        }
    }
    throw "QA_ACTORS_API_KEY_MISSING:$Name"
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
    throw "QA_PASSWORD_MISSING set LEOVER_QA_PASSWORD or create gitignored $LocalSecret"
}

function Get-AdminHeaders([string]$Service) {
    return @{
        apikey        = $Service
        Authorization = "Bearer $Service"
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
    } while ($page -le 5)
    return $null
}

function Ensure-AuthActors([string]$Password) {
    $service = Get-StagingApiKey "service_role"
    $url = "https://$ExpectedRef.supabase.co"
    $headers = Get-AdminHeaders $service
    foreach ($u in $Actors) {
        $id = Get-AuthUserIdByEmail $headers $url $u.Email
        $meta = @{
            username     = $u.Username
            display_name = $u.Name
            birth_date   = "1990-01-15"
        }
        if ([string]::IsNullOrWhiteSpace($id)) {
            $body = @{
                email         = $u.Email
                password      = $Password
                email_confirm = $true
                user_metadata = $meta
            } | ConvertTo-Json -Compress
            Invoke-RestMethod -Method Post -Uri "$Url/auth/v1/admin/users" -Headers $headers -Body $body | Out-Null
            Write-Host "AUTH_CREATED $($u.Id)"
        } else {
            $body = @{
                password      = $Password
                email_confirm = $true
                user_metadata = $meta
            } | ConvertTo-Json -Compress
            Invoke-RestMethod -Method Put -Uri "$Url/auth/v1/admin/users/$id" -Headers $headers -Body $body | Out-Null
            Write-Host "AUTH_UPDATED $($u.Id)"
        }
    }
}

function Invoke-StagingSqlFile([string]$File) {
    $raw = & supabase --output json --workdir $Workdir db query --linked --file $File
    $text = if ($raw -is [string]) { $raw } else { ($raw | Out-String) }
    if ($text -match "Failed to run sql query" -or $text -match "QA_ACTORS_ABORT_" -or $text -match "QA_COMMUNITY_PERSONS_MISSING") {
        Write-Host $text
        throw "QA_ACTORS_SQL_FAILED $File"
    }
    if ($LASTEXITCODE -ne 0 -and $text -notmatch '"boundary"') {
        Write-Host $text
        throw "QA_ACTORS_SQL_FAILED $File"
    }
    return $text
}

function Invoke-ActorsSql {
    $original = Get-Content $SqlActors -Raw -Encoding UTF8
    if ($original -notmatch "begin -- QA_ACTORS_INJECT_GUC") {
        throw "QA_ACTORS_SQL_MISSING_INJECT_MARKER"
    }
    $injection = @"
begin -- QA_ACTORS_INJECT_GUC
  perform set_config('leover.staging_reset.project_ref', '$ExpectedRef', true);
"@
    $injected = $original.Replace("begin -- QA_ACTORS_INJECT_GUC", $injection.TrimEnd())
    $tmp = Join-Path $env:TEMP "leover-qa-actors-seed.sql"
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($tmp, $injected, $utf8)
    try {
        Invoke-StagingSqlFile $tmp | Out-Null
        Write-Host "SQL_ACTORS=OK"
    } finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}

function Get-ValidateMap {
    $text = Invoke-StagingSqlFile $SqlValidate
    $map = @{}
    $parsed = $text | ConvertFrom-Json
    $rows = $parsed
    if ($parsed.PSObject.Properties.Name -contains "rows") {
        $rows = $parsed.rows
    }
    foreach ($row in @($rows)) {
        if ($row.k) { $map[[string]$row.k] = [int64]$row.n }
    }
    return $map
}

function Get-UsernameAuthMap {
    $usernames = ($Actors | ForEach-Object { "'" + $_.Username.Replace("'", "''") + "'" }) -join ","
    $tmp = Join-Path $env:TEMP "leover-qa-username-resolve.sql"
    $sql = @"
select p.username as username, u.email as email, p.user_id::text as user_id
  from public.persons p
  join auth.users u on u.id = p.user_id
 where p.username in ($usernames);
"@
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($tmp, $sql, $utf8)
    try {
        $text = Invoke-StagingSqlFile $tmp
        $parsed = $text | ConvertFrom-Json
        $rows = $parsed
        if ($parsed.PSObject.Properties.Name -contains "rows") {
            $rows = $parsed.rows
        }
        $map = @{}
        foreach ($row in @($rows)) {
            if ($row.username) {
                $map[[string]$row.username] = [string]$row.email
            }
        }
        return $map
    } finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}

function Test-LoginAll([string]$Password) {
    $anon = Get-StagingApiKey "anon"
    $url = "https://$ExpectedRef.supabase.co"
    $headers = @{
        apikey        = $anon
        Authorization = "Bearer $anon"
        "Content-Type" = "application/json"
    }
    $resolved = Get-UsernameAuthMap
    $failed = @()
    foreach ($u in $Actors) {
        if (-not $resolved.ContainsKey($u.Username) -or [string]::IsNullOrWhiteSpace($resolved[$u.Username])) {
            $failed += $u.Id
            Write-Host "USERNAME_RESOLVE_FAIL $($u.Id) $($u.Username)"
            continue
        }
        $email = $resolved[$u.Username]
        $body = @{ email = $email; password = $Password } | ConvertTo-Json -Compress
        try {
            $tok = Invoke-RestMethod -Method Post -Uri "$url/auth/v1/token?grant_type=password" -Headers $headers -Body $body
            if ([string]::IsNullOrWhiteSpace([string]$tok.access_token)) {
                $failed += $u.Id
                Write-Host "USERNAME_LOGIN_FAIL $($u.Id) $($u.Username)"
            } else {
                Write-Host "USERNAME_LOGIN_OK $($u.Id) $($u.Username)"
            }
        } catch {
            $failed += $u.Id
            Write-Host "USERNAME_LOGIN_FAIL $($u.Id) $($u.Username)"
        }
    }
    if ($failed.Count -gt 0) {
        throw "QA_ACTORS_USERNAME_LOGIN_FAILED $($failed -join ',')"
    }
}

function Test-AndroidUsernameField([string]$Password) {
    $anon = Get-StagingApiKey "anon"
    $url = "https://$ExpectedRef.supabase.co"
    $headers = @{
        apikey        = $anon
        Authorization = "Bearer $anon"
        "Content-Type" = "application/json"
        Prefer        = "return=representation"
    }
    $sample = @($Actors | Where-Object { $UsernameLoginSample -contains $_.Id })
    $ok = 0
    $fail = 0
    foreach ($u in $sample) {
        $body = @{ p_username = $u.Username; p_password = $Password } | ConvertTo-Json -Compress
        try {
            $resp = Invoke-RestMethod -Method Post -Uri "$url/rest/v1/rpc/canon_begin_username_login" -Headers $headers -Body $body
            $email = $null
            if ($resp -is [string]) {
                $parsed = $resp | ConvertFrom-Json
                $email = [string]$parsed.email
            } else {
                $email = [string]$resp.email
            }
            if ([string]::IsNullOrWhiteSpace($email)) {
                $fail++
                Write-Host "ANDROID_FIELD_FAIL $($u.Id) $($u.Username) empty_email"
            } else {
                $tokBody = @{ email = $email; password = $Password } | ConvertTo-Json -Compress
                $tok = Invoke-RestMethod -Method Post -Uri "$url/auth/v1/token?grant_type=password" -Headers $headers -Body $tokBody
                if ([string]::IsNullOrWhiteSpace([string]$tok.access_token)) {
                    $fail++
                    Write-Host "ANDROID_FIELD_FAIL $($u.Id) $($u.Username) no_session"
                } else {
                    $ok++
                    Write-Host "ANDROID_FIELD_OK $($u.Id) $($u.Username)"
                }
            }
        } catch {
            $fail++
            Write-Host "ANDROID_FIELD_FAIL $($u.Id) $($u.Username)"
        }
    }
    if ($fail -eq 0 -and $ok -eq $sample.Count) {
        Write-Host "ANDROID_USERNAME_LOGIN_TEST=PASS"
        return $true
    }
    Write-Host "ANDROID_USERNAME_LOGIN_TEST=FAIL ok=$ok fail=$fail"
    throw "QA_ACTORS_ANDROID_USERNAME_LOGIN_FAILED"
}

function Assert-Validate($map) {
    $required = @{
        AUTH_QA_USERS          = 16
        PERSONS_QA             = 16
        USERNAME_UNIQUE        = 16
        ORGS_QA_CORE           = 6
        PETS_QA_CORE           = 4
        VITACORA_QA_CORE       = 4
        ADOPTION_OPEN_LUNA     = 1
        FOSTER_PLACEMENTS      = 0
        FOSTER_REQUESTS        = 0
        LOST_FOUND_OPEN        = 0
        ADOPTION_APPLICATIONS  = 0
        QA03_ELIGIBLE          = 1
        QA04_ELIGIBLE          = 1
        QA05_EXCLUDED          = 1
        QA06_FOSTER_NOT_FOUND  = 1
        QA07_VERIFIED          = 1
        QA08_PENDING           = 1
        QA09_NOT_REQUESTED     = 1
        QA10_VERIFIED          = 1
        QA13_UNVERIFIED        = 1
        MORA_PRIVATE_CARE      = 1
        MORA_PENDING_PROPOSAL  = 1
        MORA_ACCEPTED_VISIBLE  = 1
        STAFF_PERSONS          = 0
        STAFF_AUTH             = 6
    }
    $bad = @()
    foreach ($k in $required.Keys) {
        $got = 0
        if ($map.ContainsKey($k)) { $got = [int64]$map[$k] }
        if ($got -lt [int64]$required[$k] -and $k -notin @("FOSTER_PLACEMENTS", "FOSTER_REQUESTS", "LOST_FOUND_OPEN", "ADOPTION_APPLICATIONS", "STAFF_PERSONS")) {
            $bad += "$k got=$got expected>=$($required[$k])"
        }
        if ($k -in @("FOSTER_PLACEMENTS", "FOSTER_REQUESTS", "LOST_FOUND_OPEN", "ADOPTION_APPLICATIONS", "STAFF_PERSONS") -and $got -ne $required[$k]) {
            $bad += "$k got=$got expected=$($required[$k])"
        }
        if ($k -in @("AUTH_QA_USERS", "PERSONS_QA", "USERNAME_UNIQUE", "ORGS_QA_CORE", "PETS_QA_CORE", "VITACORA_QA_CORE") -and $got -ne $required[$k]) {
            $bad += "$k got=$got expected=$($required[$k])"
        }
    }
    if ($bad.Count -gt 0) {
        throw "QA_ACTORS_VALIDATE_FAIL $($bad -join ' | ')"
    }
}

$seenUsernames = @{}
foreach ($u in $Actors) {
    $name = [string]$u.Username
    if ($name -match '@' -or $name -notmatch '^[a-z0-9._]{3,30}$') {
        throw "QA_ACTORS_USERNAME_INVALID $($u.Id)=$name"
    }
    if ($seenUsernames.ContainsKey($name)) {
        throw "QA_ACTORS_USERNAME_NOT_UNIQUE $name"
    }
    $seenUsernames[$name] = $true
}

Assert-StagingOnly
$password = Get-QaPassword
Ensure-AuthActors $password
Invoke-ActorsSql
if (-not $SkipCommunityExtras) {
    Invoke-StagingSqlFile $SqlCommunity | Out-Null
    Write-Host "SQL_COMMUNITY_EXTRAS=OK"
}
$map = Get-ValidateMap
Assert-Validate $map
if (-not $SkipLoginCheck) {
    Test-LoginAll $password
    $null = Test-AndroidUsernameField $password
}

Write-Host ""
Write-Host "QA_PASSWORD=from env or gitignored local file (not printed)"
Write-Host "PASSWORD FILE: $LocalSecret"
Write-Host ""
Write-Host "ACCOUNTS:"
foreach ($u in $Actors) {
    if ($u.Username -match '@') { throw "QA_ACTORS_USERNAME_HAS_AT $($u.Id)" }
    Write-Host ("{0}`t{1}`t{2}`t{3}`t{4}`t{5}" -f $u.Id, $u.Username, $u.Function, $u.Verification, $u.Availability, $u.Use)
}
Write-Host ""
Write-Host "VALIDATE_OK AUTH=$($map.AUTH_QA_USERS) PERSONS=$($map.PERSONS_QA) ORGS=$($map.ORGS_QA_CORE) PETS=$($map.PETS_QA_CORE)"
Write-Host "STAFF_PERSONS=$($map.STAFF_PERSONS) STAFF_AUTH=$($map.STAFF_AUTH)"
Write-Host "PROD_GUARD=PASS"
