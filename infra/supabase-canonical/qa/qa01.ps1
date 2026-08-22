# QA-01 Staging-only orchestrator. Never writes to legacy/production.
param(
    [ValidateSet("seed", "reset", "reseed")]
    [string]$Action = "seed"
)

$ErrorActionPreference = "Stop"
$ExpectedRef = "tobqbddfcyitwgbkthhy"
$LegacyRef = "wystsapjfpdtoprlmizz"
$Workdir = Join-Path $PSScriptRoot ".."
$QaDir = $PSScriptRoot

function Assert-StagingOnly {
    $refFile = Join-Path $Workdir ".temp\project-ref"
    $linked = $null
    if (Test-Path $refFile) {
        $linked = (Get-Content $refFile -Raw).Trim()
    }
    if (-not $linked) {
        $status = & supabase --workdir $Workdir projects list 2>$null
        if ("$status" -match $LegacyRef) {
            throw "QA_01_ABORT_LEGACY_PROJECT"
        }
    }
    if ($linked -and $linked -ne $ExpectedRef) {
        if ($linked -eq $LegacyRef) { throw "QA_01_ABORT_LEGACY_PROJECT" }
        throw "QA_01_ABORT_UNKNOWN_PROJECT:$linked"
    }
    Write-Host "STAGING_ONLY_GUARD=PASS ref=$ExpectedRef"
}

function Assert-QaPassword {
    if ([string]::IsNullOrWhiteSpace($env:LEOVER_QA_PASSWORD)) {
        throw "QA_PASSWORD_REQUIRED"
    }
}

function Ensure-AuthUsers {
    $service = $env:SUPABASE_SERVICE_ROLE_KEY
    if ([string]::IsNullOrWhiteSpace($service)) {
        throw "QA_SERVICE_ROLE_REQUIRED"
    }
    $url = "https://$ExpectedRef.supabase.co"
    $users = @(
        @{ email = "qa.public@leoverapp.com"; username = "qa_public"; name = "QA • Persona Pública" },
        @{ email = "qa.private@leoverapp.com"; username = "qa_private"; name = "QA • Persona Privada" },
        @{ email = "qa.rescuer@leoverapp.com"; username = "qa_rescuer"; name = "QA • Rescatista" },
        @{ email = "qa.orgadmin@leoverapp.com"; username = "qa_org_admin"; name = "QA • Admin Refugio" },
        @{ email = "qa.vet@leoverapp.com"; username = "qa_vet"; name = "QA • Veterinaria Persona" },
        @{ email = "qa.provider@leoverapp.com"; username = "qa_provider"; name = "QA • Paseos" },
        @{ email = "qa.daycare@leoverapp.com"; username = "qa_daycare"; name = "QA • Guardería Persona" },
        @{ email = "qa.second@leoverapp.com"; username = "qa_second"; name = "QA • Segundo Consumidor" }
    )
    $headers = @{
        apikey = $service
        Authorization = "Bearer $service"
        "Content-Type" = "application/json"
    }
    foreach ($u in $users) {
        $body = @{
            email = $u.email
            password = $env:LEOVER_QA_PASSWORD
            email_confirm = $true
            user_metadata = @{
                username = $u.username
                display_name = $u.name
                birth_date = "1990-01-15"
            }
        } | ConvertTo-Json -Compress
        try {
            Invoke-RestMethod -Method Post -Uri "$url/auth/v1/admin/users" -Headers $headers -Body $body | Out-Null
            Write-Host "AUTH_CREATED $($u.username)"
        } catch {
            Write-Host "AUTH_EXISTS_OR_SKIP $($u.username)"
        }
    }
}

function Invoke-QaSql([string]$file) {
    & supabase --workdir $Workdir db query --linked (Get-Content $file -Raw)
}

Assert-StagingOnly

switch ($Action) {
    "reset" {
        Invoke-QaSql (Join-Path $QaDir "qa01_reset.sql")
    }
    "seed" {
        Assert-QaPassword
        Ensure-AuthUsers
        Invoke-QaSql (Join-Path $QaDir "qa01_seed.sql")
    }
    "reseed" {
        Assert-QaPassword
        Invoke-QaSql (Join-Path $QaDir "qa01_reset.sql")
        Ensure-AuthUsers
        Invoke-QaSql (Join-Path $QaDir "qa01_seed.sql")
    }
}
