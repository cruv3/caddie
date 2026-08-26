[CmdletBinding()]
param(
    [switch]$IncludeStudyFixtures,
    [string]$Serial
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $IncludeStudyFixtures) {
    throw "Study fixture installation is opt-in. Re-run with -IncludeStudyFixtures."
}

$repository = Split-Path $PSScriptRoot -Parent
$adbTarget = if ($Serial) { @("-s", $Serial) } else { @() }
$fixtures = @(
    "study-bank", "study-calendar", "study-mail", "study-telegram",
    "study-gallery", "study-notes", "study-music", "study-training-sandbox"
)

Push-Location $repository
try {
    & .\gradlew.bat -PincludeStudyFixtures=true :app:assembleStudyDebug ($fixtures | ForEach-Object { ":research:fixtures:$($_):assembleDebug" })
    if ($LASTEXITCODE -ne 0) { throw "Study fixture build failed" }

    foreach ($fixture in $fixtures) {
        $apk = Join-Path $repository "research\fixtures\$fixture\build\outputs\apk\debug\app-debug.apk"
        if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found: $apk" }
        & adb @adbTarget install -r $apk
        if ($LASTEXITCODE -ne 0) { throw "Installation failed for $fixture" }
    }
    Write-Host "Study fixtures installed. Caddie itself is built separately by install-caddie-debug.ps1."
}
finally {
    Pop-Location
}
