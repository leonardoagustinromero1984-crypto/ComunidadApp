# LeoVer STAGING QA RESET orchestrator.
# Never targets production or legacy. Aborts unless project ref is exactly
# tobqbddfcyitwgbkthhy. Does not modify Android code, APK, schema, or git.
param(
    [switch]$Execute,
    [switch]$Preflight,
    [switch]$SkipStorage
)

$ErrorActionPreference = "Stop"
$ExpectedRef = "tobqbddfcyitwgbkthhy"
$LegacyRef = "wystsapjfpdtoprlmizz"
$ConfirmGuc = "LEOVER-STAGING-QA-RESET-CONFIRM"
$PreserveStoragePrefixes = @(
    "platform/",
    "canonical/",
    "app/",
    "catalogs/",
    "seeds/"
)

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Workdir = Join-Path $RepoRoot "infra\supabase-canonical"
$SqlReset = Join-Path $PSScriptRoot "reset-staging.sql"
$SqlSequence = Join-Path $PSScriptRoot "reset-staging-sequence.sql"
$SqlVerify = Join-Path $PSScriptRoot "reset-staging-verify.sql"
$SqlAuthPlan = Join-Path $PSScriptRoot "reset-staging-inspect-auth-plan.sql"
$SqlAuthSchema = Join-Path $PSScriptRoot "reset-staging-inspect-auth-schema.sql"

function Assert-StagingOnly {
    if (-not (Test-Path $Workdir)) {
        throw "STAGING_RESET_ABORT_WORKDIR_MISSING: $Workdir"
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
        throw "STAGING_RESET_ABORT_UNLINKED"
    }
    if ($linked -eq $LegacyRef) {
        throw "STAGING_RESET_ABORT_LEGACY_PROJECT"
    }
    if ($linked -ne $ExpectedRef) {
        throw "STAGING_RESET_ABORT_UNKNOWN_PROJECT:$linked"
    }

    $blob = $linked
    if (Test-Path $poolerFile) {
        $pooler = (Get-Content $poolerFile -Raw)
        if ($pooler -match $LegacyRef) {
            throw "STAGING_RESET_ABORT_LEGACY_POOLER"
        }
        if ($pooler -notmatch $ExpectedRef) {
            throw "STAGING_RESET_ABORT_POOLER_REF_MISMATCH"
        }
        $blob += "`n$pooler"
    }

    $envFile = Join-Path $Workdir ".env"
    if (Test-Path $envFile) {
        $envNames = Get-Content $envFile | ForEach-Object {
            if ($_ -match "^\s*([A-Za-z0-9_]+)=") { $matches[1] }
        }
        $blob += "`n$($envNames -join ',')"
    }

    if ($blob -match $LegacyRef) {
        throw "STAGING_RESET_ABORT_LEGACY_REF_PRESENT"
    }

    Write-Host "STAGING_ONLY_GUARD=PASS ref=$ExpectedRef"
}

function Invoke-StagingSqlFile([string]$File) {
    $raw = & supabase --output json --workdir $Workdir db query --linked --file $File
    return (Complete-StagingSqlRaw $raw $File)
}

function Invoke-StagingSqlText([string]$Sql) {
    $raw = & supabase --output json --workdir $Workdir db query --linked $Sql
    return (Complete-StagingSqlRaw $raw "inline-sql")
}

function Complete-StagingSqlRaw($raw, [string]$Label) {
    $text = if ($raw -is [string]) { $raw } else { ($raw | Out-String) }
    $sqlFailed = $text -match "Failed to run sql query" -or $text -match "STAGING_RESET_ABORT_"
    $telemetryOnly = $text -match "Timeout while shutting down PostHog" -and ($text -match '"boundary"' -or $text -match '"rows"')
    if ($sqlFailed) {
        Write-Host $text
        throw "STAGING_RESET_SQL_FAILED $Label"
    }
    if ($LASTEXITCODE -ne 0 -and -not $telemetryOnly) {
        Write-Host $text
        throw "STAGING_RESET_SQL_FAILED $Label"
    }
    if ($telemetryOnly) {
        Write-Host "SUPABASE_CLI_TELEMETRY_TIMEOUT_IGNORED"
    }
    return $text
}

function Get-JsonSpan([string]$text, [int]$start) {
    $stack = New-Object System.Collections.Generic.Stack[string]
    $inString = $false
    $escape = $false
    for ($i = $start; $i -lt $text.Length; $i++) {
        $ch = [string]$text[$i]
        if ($inString) {
            if ($escape) { $escape = $false; continue }
            if ($ch -eq "\") { $escape = $true; continue }
            if ($ch -eq '"') { $inString = $false }
            continue
        }
        if ($ch -eq '"') { $inString = $true; continue }
        if ($ch -eq "{" -or $ch -eq "[") {
            $stack.Push($ch)
            continue
        }
        if ($ch -eq "}" -or $ch -eq "]") {
            if ($stack.Count -eq 0) { break }
            $open = $stack.Pop()
            $matched = ($open -eq "{" -and $ch -eq "}") -or ($open -eq "[" -and $ch -eq "]")
            if (-not $matched) {
                throw "STAGING_RESET_SQL_UNPARSEABLE"
            }
            if ($stack.Count -eq 0) {
                return $text.Substring($start, $i - $start + 1)
            }
        }
    }
    return $null
}

function Get-JsonValueTexts([string]$text) {
    $values = New-Object System.Collections.Generic.List[string]
    $i = 0
    while ($i -lt $text.Length) {
        $ch = [string]$text[$i]
        if ($ch -eq "{" -or $ch -eq "[") {
            $span = Get-JsonSpan $text $i
            if ([string]::IsNullOrWhiteSpace($span)) {
                throw "STAGING_RESET_SQL_UNPARSEABLE"
            }
            $values.Add($span)
            $i += $span.Length
            continue
        }
        $i++
    }
    return $values
}

function Convert-ToRowArray($rows) {
    if ($null -eq $rows) {
        return , [object[]]@()
    }
    return , [object[]]@($rows)
}

function Convert-SupabaseRows($raw) {
    if ($null -eq $raw) {
        throw "STAGING_RESET_SQL_EMPTY_RESULT"
    }
    if ($raw -is [System.Management.Automation.PSCustomObject] -or $raw -is [System.Collections.IDictionary]) {
        return (Convert-ParsedToRows $raw)
    }
    if ($raw -is [System.Array] -and $raw.Length -gt 0 -and $raw[0] -is [System.Management.Automation.PSCustomObject]) {
        return , [object[]]@($raw)
    }
    $text = if ($raw -is [string]) { $raw } else { ($raw | Out-String) }
    if ([string]::IsNullOrWhiteSpace($text)) {
        throw "STAGING_RESET_SQL_EMPTY_RESULT"
    }
    $spans = @(Get-JsonValueTexts $text)
    if ($spans.Count -lt 1) {
        throw "STAGING_RESET_SQL_UNPARSEABLE"
    }
    $parsedList = New-Object System.Collections.Generic.List[object]
    foreach ($span in $spans) {
        try {
            $parsedList.Add(($span | ConvertFrom-Json))
        } catch {
            throw "STAGING_RESET_SQL_UNPARSEABLE"
        }
    }
    foreach ($parsed in $parsedList) {
        if ($null -eq $parsed) { continue }
        if ($parsed -is [System.Array]) {
            return , [object[]]@($parsed)
        }
        $names = @($parsed.PSObject.Properties.Name)
        if ($names -contains "rows") {
            return (Convert-ToRowArray $parsed.rows)
        }
    }
    foreach ($parsed in $parsedList) {
        if ($null -eq $parsed) { continue }
        $names = @($parsed.PSObject.Properties.Name)
        if ($names -contains "_tag") { continue }
        if ($names -contains "boundary" -and $names -contains "warning") { continue }
        return , [object[]]@($parsed)
    }
    throw "STAGING_RESET_SQL_UNPARSEABLE"
}

function Convert-ParsedToRows($parsed) {
    $names = @($parsed.PSObject.Properties.Name)
    if ($names -contains "rows") {
        return (Convert-ToRowArray $parsed.rows)
    }
    return , [object[]]@($parsed)
}

function Get-CountMap {
    $rows = Convert-SupabaseRows (Invoke-StagingSqlFile $SqlVerify)
    $map = @{}
    foreach ($row in $rows) {
        $map[[string]$row.k] = [int64]$row.n
    }
    return $map
}

function Get-Sequence {
    $rows = Convert-SupabaseRows (Invoke-StagingSqlFile $SqlSequence)
    $list = @($rows)
    if ($null -eq $rows) {
        $list = @()
    }
    if ($list.Count -ne 1 -or $null -eq $list[0]) {
        throw "STAGING_RESET_SEQUENCE_UNEXPECTED_RESULT count=$($list.Count)"
    }
    $row = $list[0]
    if ($null -eq $row.vitacora_sequence_last -or [string]::IsNullOrWhiteSpace([string]$row.vitacora_sequence_last)) {
        throw "STAGING_RESET_SEQUENCE_UNEXPECTED_RESULT missing vitacora_sequence_last"
    }
    if ($null -eq $row.vitacora_sequence_is_called -or [string]::IsNullOrWhiteSpace([string]$row.vitacora_sequence_is_called)) {
        throw "STAGING_RESET_SEQUENCE_UNEXPECTED_RESULT missing vitacora_sequence_is_called"
    }
    return $row
}

function Test-PreserveStoragePath([string]$RelPath) {
    $normalized = $RelPath.Replace("\", "/").TrimStart("/")
    foreach ($prefix in $PreserveStoragePrefixes) {
        if ($normalized.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
    }
    return $false
}

function Get-StagingServiceRole {
    $keysRaw = & supabase --workdir $Workdir projects api-keys --project-ref $ExpectedRef --reveal --output json
    if ($LASTEXITCODE -ne 0) {
        throw "STAGING_RESET_API_KEYS_FAILED"
    }
    $keys = $keysRaw | ConvertFrom-Json
    $service = $null
    foreach ($k in @($keys)) {
        if ([string]$k.name -eq "service_role") {
            $service = [string]$k.api_key
            break
        }
    }
    if ([string]::IsNullOrWhiteSpace($service)) {
        throw "STAGING_RESET_SERVICE_ROLE_MISSING"
    }
    return $service
}

function Get-QaStorageInventory {
    $objectsRaw = Invoke-StagingSqlText "select bucket_id, name from storage.objects order by bucket_id, name;"
    $objectRows = Convert-SupabaseRows $objectsRaw
    $targets = @()
    $preserved = 0
    $rowCount = 0
    foreach ($row in @($objectRows)) {
        $bucket = [string]$row.bucket_id
        $rel = ([string]$row.name).Replace("\", "/").TrimStart("/")
        if ([string]::IsNullOrWhiteSpace($bucket) -or [string]::IsNullOrWhiteSpace($rel)) { continue }
        $rowCount++
        if (Test-PreserveStoragePath $rel) {
            Write-Host "STORAGE_PRESERVED $bucket/$rel"
            $preserved++
            continue
        }
        $targets += [pscustomobject]@{ Bucket = $bucket; Name = $rel }
    }
    return @{
        Targets = $targets
        Preserved = $preserved
        Total = $rowCount
    }
}

function Merge-QaStorageTargets($left, $right) {
    $seen = @{}
    $out = @()
    foreach ($item in @($left) + @($right)) {
        if ($null -eq $item) { continue }
        if ([string]::IsNullOrWhiteSpace([string]$item.Bucket) -or [string]::IsNullOrWhiteSpace([string]$item.Name)) { continue }
        $key = "$([string]$item.Bucket)|$([string]$item.Name)"
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key] = $true
        $out += $item
    }
    return $out
}

function Test-StorageAlreadyGone([string]$detail) {
    $t = [string]$detail
    if ([string]::IsNullOrWhiteSpace($t)) { return $false }
    $t = $t.ToLowerInvariant()
    return $t.Contains("404") -or
        $t.Contains("not found") -or
        $t.Contains("nosuchkey") -or
        $t.Contains("object not found") -or
        ($t.Contains("already") -and $t.Contains("exist"))
}

function Remove-QaStorageTargets($targets) {
    if ($null -eq $targets) {
        return @{ Deleted = 0; AlreadyGone = 0; Failed = 0 }
    }
    $list = @($targets | Where-Object {
        $_ -and
        -not [string]::IsNullOrWhiteSpace([string]$_.Bucket) -and
        -not [string]::IsNullOrWhiteSpace([string]$_.Name)
    })
    if ($list.Count -lt 1) {
        return @{ Deleted = 0; AlreadyGone = 0; Failed = 0 }
    }
    $service = Get-StagingServiceRole
    $headers = @{
        apikey = $service
        Authorization = "Bearer $service"
        "Content-Type" = "application/json"
    }
    $deleted = 0
    $alreadyGone = 0
    foreach ($item in $list) {
        $bucket = [string]$item.Bucket
        $rel = [string]$item.Name
        $url = "https://$ExpectedRef.supabase.co/storage/v1/object/$bucket"
        $body = @{ prefixes = @($rel) } | ConvertTo-Json -Compress -Depth 3
        try {
            Invoke-RestMethod -Method Delete -Uri $url -Headers $headers -Body $body | Out-Null
            Write-Host "STORAGE_DELETED $bucket/$rel"
            $deleted++
        } catch {
            $detail = $_.Exception.Message
            if ($_.ErrorDetails) { $detail = $_.ErrorDetails.Message }
            if (Test-StorageAlreadyGone $detail) {
                Write-Host "STORAGE_ALREADY_ABSENT $bucket/$rel"
                $alreadyGone++
                continue
            }
            throw "STAGING_RESET_STORAGE_API_DELETE_FAILED ${bucket}/${rel}: $detail"
        }
    }
    return @{ Deleted = $deleted; AlreadyGone = $alreadyGone; Failed = 0 }
}

function New-InjectedResetSql {
    $original = Get-Content $SqlReset -Raw
    if ($original -notmatch "begin -- STAGING_RESET_INJECT_GUC") {
        throw "STAGING_RESET_ABORT_SQL_MARKER_MISSING"
    }
    $injection = @"
begin -- STAGING_RESET_INJECT_GUC
  perform set_config('leover.staging_reset.project_ref', '$ExpectedRef', true);
  perform set_config('leover.staging_reset.confirm', '$ConfirmGuc', true);
"@
    $injected = $original.Replace("begin -- STAGING_RESET_INJECT_GUC", $injection.TrimEnd())
    $temp = Join-Path $env:TEMP "leover-reset-staging-$ExpectedRef.sql"
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($temp, $injected, $utf8)
    return $temp
}

function Invoke-DryRunReport {
    Write-Host "DRY_RUN=YES (pass -Execute to wipe STAGING transactional data)"
    if (-not (Test-Path $SqlAuthPlan)) {
        throw "STAGING_RESET_ABORT_AUTH_PLAN_MISSING"
    }
    $planRows = Convert-SupabaseRows (Invoke-StagingSqlFile $SqlAuthPlan)
    $known = @()
    $preserved = @()
    $cleaned = @()
    $unknown = @()
    foreach ($row in @($planRows)) {
        $name = [string]$row.table_name
        $handling = [string]$row.handling
        if ([string]::IsNullOrWhiteSpace($name)) { continue }
        $known += $name
        switch ($handling) {
            "PRESERVE" { $preserved += $name }
            "CLEAN" { $cleaned += $name }
            default { $unknown += $name }
        }
    }
    Write-Host "AUTH_TABLES_KNOWN=$($known.Count) $($known -join ',')"
    Write-Host "AUTH_TABLES_PRESERVED=$($preserved.Count) $($preserved -join ',')"
    Write-Host "AUTH_TABLES_CLEANED=$($cleaned.Count) $($cleaned -join ',')"
    if ($unknown.Count -gt 0) {
        Write-Host "AUTH_TABLES_UNKNOWN=$($unknown.Count) $($unknown -join ',')"
        throw "STAGING_RESET_ABORT_UNKNOWN_AUTH_TABLE"
    }
    Write-Host "AUTH_TABLES_UNKNOWN=0"

    $schemaRows = Convert-SupabaseRows (Invoke-StagingSqlFile $SqlAuthSchema)
    $schema = @($schemaRows)[0]
    Write-Host "EXPECTED_AUTH_USERS_AFTER=$($schema.expected_auth_users_after)"
    Write-Host "PUBLIC_SCHEMA=catalogs preserved; transactional public tables would TRUNCATE CONTINUE IDENTITY CASCADE"
    Write-Host "MFA_AAL2=staff users/identities/mfa_factors/mfa_recovery/webauthn snapshotted and restored"

    $storageInventory = Get-QaStorageInventory
    $qaStorage = @($storageInventory.Targets).Count
    Write-Host "STORAGE_OBJECTS=$($storageInventory.Total) STORAGE_QA_WOULD_DELETE=$qaStorage STORAGE_PRESERVED=$($storageInventory.Preserved)"
    Write-Host "DRY_RUN=PASS"
}

function Invoke-ExecutePreflight {
    Write-Host "EXECUTE_PREFLIGHT_START"
    $storageInventory = Get-QaStorageInventory
    $qaStorage = @($storageInventory.Targets).Count
    $preservedStorage = [int]$storageInventory.Preserved
    Write-Host "PREFLIGHT_SEQUENCE=$($seqBefore.vitacora_sequence_last) called=$($seqBefore.vitacora_sequence_is_called)"
    Write-Host "PREFLIGHT_AUTH_USERS=$($countsBefore.AUTH_USERS)"
    Write-Host "PREFLIGHT_PERSONS=$($countsBefore.PERSONS)"
    Write-Host "PREFLIGHT_STORAGE_OBJECTS=$($storageInventory.Total) QA=$qaStorage PRESERVED=$preservedStorage"
    $injectedSql = New-InjectedResetSql
    try {
        $probe = Get-Content $injectedSql -Raw
        if ($probe -notmatch [regex]::Escape($ExpectedRef)) {
            throw "STAGING_RESET_PREFLIGHT_CONFIRM_REF_MISSING"
        }
        if ($probe -notmatch [regex]::Escape($ConfirmGuc)) {
            throw "STAGING_RESET_PREFLIGHT_CONFIRM_GUC_MISSING"
        }
        Write-Host "PREFLIGHT_CONFIRM_SQL=READY"
    } finally {
        if (Test-Path $injectedSql) { Remove-Item $injectedSql -Force }
    }
    Write-Host "EXECUTE_PREFLIGHT_WITHOUT_DELETE=PASS"
}

Assert-StagingOnly

if ($Execute -and $Preflight) {
    throw "STAGING_RESET_ABORT_PREFLIGHT_AND_EXECUTE"
}

if (-not (Test-Path $SqlReset)) { throw "STAGING_RESET_ABORT_SQL_MISSING" }

$seqBefore = Get-Sequence
$countsBefore = Get-CountMap
Write-Host "VITACORA_SEQUENCE_BEFORE=$($seqBefore.vitacora_sequence_last) called=$($seqBefore.vitacora_sequence_is_called)"
Write-Host "AUTH_USERS_BEFORE=$($countsBefore.AUTH_USERS) PERSONS_BEFORE=$($countsBefore.PERSONS) PETS_BEFORE=$($countsBefore.PETS)"

if (-not $Execute) {
    if ($Preflight) {
        Invoke-ExecutePreflight
    } else {
        Invoke-DryRunReport
    }
    exit 0
}

$storageListed = @{ Targets = @(); Preserved = 0; Total = 0 }
if (-not $SkipStorage) {
    $storageListed = Get-QaStorageInventory
    Write-Host "STORAGE_QA_LISTED=$(@($storageListed.Targets).Count) STORAGE_PRESERVED=$($storageListed.Preserved)"
}

$injectedSql = New-InjectedResetSql
try {
    $resetOut = Invoke-StagingSqlFile $injectedSql
    Write-Host ($resetOut | Out-String)
    Write-Host "DB_RESET=PASS"
} catch {
    Write-Host "DB_RESET=FAIL"
    throw
} finally {
    if (Test-Path $injectedSql) { Remove-Item $injectedSql -Force }
}

$storageResult = @{ Deleted = 0; AlreadyGone = 0; Preserved = [int]$storageListed.Preserved }
if (-not $SkipStorage) {
    try {
        $storageAfter = Get-QaStorageInventory
        $mergedTargets = Merge-QaStorageTargets $storageListed.Targets $storageAfter.Targets
        $storageResult = Remove-QaStorageTargets $mergedTargets
        $storageResult.Preserved = [int]$storageListed.Preserved + [int]$storageAfter.Preserved
        Write-Host "STORAGE_QA_DELETED=$($storageResult.Deleted) STORAGE_ALREADY_ABSENT=$($storageResult.AlreadyGone) STORAGE_PRESERVED=$($storageResult.Preserved)"
        Write-Host "STORAGE_CLEANUP=PASS"
    } catch {
        Write-Host "STORAGE_CLEANUP=FAIL"
        Write-Host "STORAGE_ORPHANS_PENDING=see STORAGE_* lines above"
        throw
    }
} else {
    Write-Host "STORAGE_CLEANUP=SKIP"
}

$seqAfter = Get-Sequence
$countsAfter = Get-CountMap

if ([string]$seqAfter.vitacora_sequence_last -ne [string]$seqBefore.vitacora_sequence_last) {
    Write-Host "FINAL_VERIFICATION=FAIL"
    throw "STAGING_RESET_ABORT_SEQUENCE_DRIFT $($seqBefore.vitacora_sequence_last) -> $($seqAfter.vitacora_sequence_last)"
}
if ([string]$seqAfter.vitacora_sequence_is_called -ne [string]$seqBefore.vitacora_sequence_is_called) {
    Write-Host "FINAL_VERIFICATION=FAIL"
    throw "STAGING_RESET_ABORT_SEQUENCE_CALLED_DRIFT"
}

$authPreserved = (
    [int64]$countsAfter.AUTH_USERS -ge 1 -and
    [int64]$countsAfter.PLATFORM_ADMIN_IDENTITIES -ge 1 -and
    [int64]$countsAfter.STAFF_ROLE_ASSIGNMENTS -ge 1 -and
    [int64]$countsAfter.SUPERADMIN_ASSIGNMENTS -ge 1
)
if (-not $authPreserved) {
    Write-Host "AUTH_PRESERVATION=FAIL"
    throw "STAGING_RESET_ABORT_AUTH_PRESERVATION"
}
Write-Host "AUTH_PRESERVATION=PASS"

$finalOk = (
    [int64]$countsAfter.COUNTRIES -ge 1 -and
    [int64]$countsAfter.ADMINISTRATIVE_AREAS -ge 20 -and
    [int64]$countsAfter.LOCALITIES -ge 3000 -and
    [int64]$countsAfter.SPECIES -ge 1 -and
    [int64]$countsAfter.COUNTRY_MARKETS -ge 1 -and
    [int64]$countsAfter.LEGAL_DOCUMENTS -ge 1 -and
    [int64]$countsAfter.PETS -eq 0 -and
    [int64]$countsAfter.SOCIAL_POSTS -eq 0
)
if (-not $finalOk) {
    Write-Host "FINAL_VERIFICATION=FAIL"
    throw "STAGING_RESET_ABORT_FINAL_VERIFICATION"
}
Write-Host "FINAL_VERIFICATION=PASS"

$report = [ordered]@{
    PROJECT_REF = $ExpectedRef
    STAGING_CONFIRMED = "YES"
    AUTH_USERS = $countsAfter.AUTH_USERS
    PERSONS = $countsAfter.PERSONS
    PETS = $countsAfter.PETS
    PLATFORM_ADMIN_IDENTITIES = $countsAfter.PLATFORM_ADMIN_IDENTITIES
    STAFF_ROLE_ASSIGNMENTS = $countsAfter.STAFF_ROLE_ASSIGNMENTS
    SUPERADMIN_ASSIGNMENTS = $countsAfter.SUPERADMIN_ASSIGNMENTS
    VITACORA_PROFILES = $countsAfter.VITACORA_PROFILES
    ORGANIZATIONS = $countsAfter.ORGANIZATIONS
    ORG_MEMBERSHIPS = $countsAfter.ORG_MEMBERSHIPS
    SOCIAL_POSTS = $countsAfter.SOCIAL_POSTS
    SOCIAL_STORIES = $countsAfter.SOCIAL_STORIES
    SOCIAL_COMMENTS = $countsAfter.SOCIAL_COMMENTS
    CONVERSATIONS = $countsAfter.CONVERSATIONS
    MESSAGES = $countsAfter.MESSAGES
    ADOPTION_PUBLICATIONS = $countsAfter.ADOPTION_PUBLICATIONS
    VITACORA_IMPORT_JOBS = $countsAfter.VITACORA_IMPORT_JOBS
    COUNTRIES = $countsAfter.COUNTRIES
    ADMINISTRATIVE_AREAS = $countsAfter.ADMINISTRATIVE_AREAS
    LOCALITIES = $countsAfter.LOCALITIES
    SPECIES = $countsAfter.SPECIES
    BREEDS = $countsAfter.BREEDS
    COUNTRY_MARKETS = $countsAfter.COUNTRY_MARKETS
    LEGAL_DOCUMENTS = $countsAfter.LEGAL_DOCUMENTS
    STORAGE_OBJECTS = $countsAfter.STORAGE_OBJECTS
    STORAGE_BUCKETS = $countsAfter.STORAGE_BUCKETS
    STORAGE_QA_DELETED = $storageResult.Deleted
    STORAGE_PRESERVED = $storageResult.Preserved
    VITACORA_SEQUENCE_BEFORE = $seqBefore.vitacora_sequence_last
    VITACORA_SEQUENCE_AFTER = $seqAfter.vitacora_sequence_last
    VITACORA_SEQUENCE_RESET = "NO"
}
$report.GetEnumerator() | ForEach-Object { Write-Host "$($_.Key)=$($_.Value)" }
