Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$StudyCalendarResetResultCode = 1204
$StudyCalendarResetResultData = "calendar_reset_ok"

function Invoke-Adb {
    param([string[]]$Arguments)

    $output = & adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed with exit code ${LASTEXITCODE}: adb $($Arguments -join ' ')`nOutput: $($output -join "`n")"
    }

    return $output
}

function Stop-StudyApp {
    param([string]$Package)

    Invoke-Adb -Arguments @("shell", "am", "force-stop", $Package) | Out-Null
}

function Test-StudyAppInstalled {
    param([string[]]$Output)

    return [bool]($Output | Where-Object { $_ -match "^package:" })
}

function Test-StudyResetAcknowledgement {
    param([string[]]$Output)

    $expectedAcknowledgement = 'Broadcast completed: result={0}, data="{1}"' -f $StudyCalendarResetResultCode, $StudyCalendarResetResultData
    return [bool]($Output | Where-Object {
        $_ -cmatch "^\s*$([regex]::Escape($expectedAcknowledgement))\s*$"
    })
}

function Invoke-StudyAppReset {
    param([string]$Package, [string]$Action)

    $installedOutput = Invoke-Adb -Arguments @("shell", "pm", "path", $Package)
    if (-not (Test-StudyAppInstalled -Output $installedOutput)) {
        throw "ADB package validation failed for $Package.`nOutput: $($installedOutput -join "`n")"
    }

    Stop-StudyApp -Package $Package
    $broadcastOutput = Invoke-Adb -Arguments @("shell", "am", "broadcast", "-p", $Package, "-a", $Action)
    if (-not (Test-StudyResetAcknowledgement -Output $broadcastOutput)) {
        throw "ADB broadcast did not return the reset acknowledgement for $Package.`nOutput: $($broadcastOutput -join "`n")"
    }
}

$resets = @(
    @{
        Package = "com.caddie.studycalendar"
        Action = "com.caddie.studycalendar.ACTION_RESET"
    }
)

foreach ($reset in $resets) {
    Invoke-StudyAppReset -Package $reset.Package -Action $reset.Action
}

Invoke-Adb -Arguments @("shell", "settings", "put", "global", "zen_mode", "0") | Out-Null
Stop-StudyApp -Package "com.caddie.studycalendar"
Write-Host "Study device reset complete."
