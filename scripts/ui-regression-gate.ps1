# LeoVer UI-01 static regression gate (no emulator, no screenshots).
# Run from repo root on Leonardo's Windows machine:
#   powershell -File scripts/ui-regression-gate.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$gradlew = Join-Path $root "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat not found at $gradlew"
}

& $gradlew :app:testLocalDebugUnitTest `
    --tests com.comunidapp.app.ui.UiRegressionGateTest `
    --tests com.comunidapp.app.ui.LeoVerDesignSystemContractTest `
    --tests com.comunidapp.app.ui.LeoVerUx07ContractTest `
    --tests com.comunidapp.app.ui.LeoVerMapPolicyGateTest `
    --tests com.comunidapp.app.ui.PersonaBottomSurfacesTest `
    --tests com.comunidapp.app.ui.VisualDirectionV2PilotTest `
    --tests com.comunidapp.app.ui.Ux05SocialHomeCommunityProfileSettingsTest `
    --tests com.comunidapp.app.data.model.CommunityCanonicalUiTest `
    --tests com.comunidapp.app.domain.ux.CanonicalUiErrorMapperTest `
    --tests com.comunidapp.app.domain.auth.Auth05SignupOtpOnlyGuardsTest `
    --tests com.comunidapp.app.domain.onboarding.onb02.Onb02TutorialRoutingTest `
    --tests com.comunidapp.app.domain.foster.FosterDirectPlacementTest `
    --tests com.comunidapp.app.domain.auth.LeoVerAuth06ContractTest `
    --no-configuration-cache

if ($LASTEXITCODE -ne 0) {
    throw "UI-01 regression gate failed (exit $LASTEXITCODE)"
}

Write-Host "UI-01 static regression gate passed."
