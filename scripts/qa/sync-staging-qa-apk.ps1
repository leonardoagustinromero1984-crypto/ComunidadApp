# LeoVer: the signed staging QA APK is built remotely by GitHub Actions
# (.github/workflows/staging-physical-qa-apk.yml).
# This script only downloads the latest successful artifact for the current
# branch and copies it to apk\LeoVer-M08-Staging-debug.apk.
# It does not compile, sign, read signing secrets, or git add.

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$WorkflowFile = "staging-physical-qa-apk.yml"
$ArtifactName = "LeoVer-M08-Staging-physical-qa-final"
$DestinationName = "LeoVer-M08-Staging-debug.apk"

function Fail([string]$Message) {
    Write-Host ""
    Write-Host $Message
    exit 1
}

Set-Location $RepoRoot

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    Fail @"
GitHub CLI (gh) no esta instalado.
Instalalo desde https://cli.github.com/ y despues ejecuta:
  gh auth login
No se modifico ningun archivo.
"@
}

& gh auth status
if ($LASTEXITCODE -ne 0) {
    Fail @"
GitHub CLI no esta autenticado.
Ejecuta:
  gh auth login
y volve a lanzar este script.
No se modifico ningun archivo.
"@
}

$branch = (& git -C $RepoRoot rev-parse --abbrev-ref HEAD).Trim()
if ([string]::IsNullOrWhiteSpace($branch) -or $branch -eq "HEAD") {
    Fail "No se pudo leer la rama actual. No se descargo ningun APK."
}

Write-Host "RAMA=$branch"
Write-Host "WORKFLOW=$WorkflowFile"

$json = & gh run list --workflow $WorkflowFile --branch $branch --status success --limit 1 --json databaseId,headSha,url,createdAt,displayTitle,conclusion
if ($LASTEXITCODE -ne 0) {
    Fail "gh run list fallo para $WorkflowFile en $branch. No se descargo ningun APK."
}
if ([string]::IsNullOrWhiteSpace($json) -or $json.Trim() -eq "[]") {
    Fail "No hay una ejecucion exitosa de $WorkflowFile para la rama $branch."
}

$runs = @($json | ConvertFrom-Json)
$run = $runs[0]
if (-not $run -or -not $run.databaseId) {
    Fail "No hay una ejecucion exitosa de $WorkflowFile para la rama $branch."
}

Write-Host "RUN=$($run.databaseId)"
Write-Host "SHA=$($run.headSha)"
Write-Host "URL=$($run.url)"

$temp = Join-Path ([System.IO.Path]::GetTempPath()) ("leover-staging-qa-apk-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
    & gh run download $run.databaseId --name $ArtifactName --dir $temp
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo descargar el artifact $ArtifactName del run $($run.databaseId)."
    }

    $apk = Get-ChildItem -Path $temp -Recurse -File -Filter "*.apk" |
        Sort-Object Length -Descending |
        Select-Object -First 1
    if (-not $apk) {
        throw "El artifact $ArtifactName no contiene un APK."
    }

    $destDir = Join-Path $RepoRoot "apk"
    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    $dest = Join-Path $destDir $DestinationName
    Copy-Item -LiteralPath $apk.FullName -Destination $dest -Force

    if (-not (Test-Path -LiteralPath $dest)) {
        throw "No se escribio $dest."
    }
    $item = Get-Item -LiteralPath $dest
    if ($item.Length -le 0) {
        throw "El APK de destino esta vacio: $dest"
    }

    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $dest
    Write-Host "DESTINO=$($item.FullName)"
    Write-Host "TAMANO=$($item.Length)"
    Write-Host "SHA256=$($hash.Hash)"

    & git -C $RepoRoot check-ignore -q -- $dest
    if ($LASTEXITCODE -ne 0) {
        throw "El APK no esta ignorado por Git ($dest). No se hizo git add."
    }
    Write-Host "GIT_IGNORE=YES"
    Write-Host "GIT_ADD=NO"
}
catch {
    Write-Host ""
    Write-Host $_.Exception.Message
    exit 1
}
finally {
    if (Test-Path -LiteralPath $temp) {
        Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
    }
}
