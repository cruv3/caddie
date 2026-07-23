Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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
    param(
        [string[]]$Output,
        [int]$ResultCode,
        [string]$ResultData
    )

    $expectedAcknowledgement = 'Broadcast completed: result={0}, data="{1}"' -f $ResultCode, $ResultData
    return [bool]($Output | Where-Object {
        $_ -cmatch "^\s*$([regex]::Escape($expectedAcknowledgement))\s*$"
    })
}

function Invoke-StudyAppReset {
    param(
        [string]$Package,
        [string]$Action,
        [int]$ResultCode,
        [string]$ResultData
    )

    $installedOutput = Invoke-Adb -Arguments @("shell", "pm", "path", $Package)
    if (-not (Test-StudyAppInstalled -Output $installedOutput)) {
        throw "ADB package validation failed for $Package.`nOutput: $($installedOutput -join "`n")"
    }

    Stop-StudyApp -Package $Package
    $broadcastOutput = Invoke-Adb -Arguments @("shell", "am", "broadcast", "--include-stopped-packages", "-p", $Package, "-a", $Action)
    if (-not (Test-StudyResetAcknowledgement -Output $broadcastOutput -ResultCode $ResultCode -ResultData $ResultData)) {
        throw "ADB broadcast did not return the reset acknowledgement for $Package.`nOutput: $($broadcastOutput -join "`n")"
    }
}

$resets = @(
    @{
        Package = "com.caddie.studycalendar"
        Action = "com.caddie.studycalendar.ACTION_RESET"
        ResultCode = 1204
        ResultData = "calendar_reset_ok"
    }
    @{
        Package = "com.caddie.studygallery"
        Action = "com.caddie.studygallery.ACTION_RESET"
        ResultCode = 1205
        ResultData = "gallery_reset_ok"
    }
    @{
        Package = "com.caddie.studynotes"
        Action = "com.caddie.studynotes.ACTION_RESET"
        ResultCode = 1206
        ResultData = "notes_reset_ok"
    }
)

foreach ($reset in $resets) {
    Invoke-StudyAppReset -Package $reset.Package -Action $reset.Action -ResultCode $reset.ResultCode -ResultData $reset.ResultData
}

Invoke-Adb -Arguments @("shell", "cmd", "notification", "set_dnd", "off") | Out-Null
Stop-StudyApp -Package "com.android.settings"
Stop-StudyApp -Package "com.caddie.studycalendar"
Stop-StudyApp -Package "com.caddie.studygallery"
Stop-StudyApp -Package "com.caddie.studynotes"
Write-Host "Study device reset complete."
