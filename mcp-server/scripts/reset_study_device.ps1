[CmdletBinding()]
param(
    [string]$Serial,
    [switch]$VerifyOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$script:AdbTarget = @()
if ($Serial) {
    $script:AdbTarget = @("-s", $Serial)
}

function Format-AdbCommand {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $target = ""
    if ($script:AdbTarget.Count -gt 0) {
        $target = " $($script:AdbTarget -join ' ')"
    }
    return "adb$target $($Arguments -join ' ')"
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $command = Format-AdbCommand -Arguments $Arguments
    Write-Host $command
    if ($VerifyOnly) {
        return @()
    }

    $output = & adb @script:AdbTarget @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed with exit code ${LASTEXITCODE}: adb $($Arguments -join ' ')`nOutput: $($output -join "`n")"
    }
    return $output
}

function Invoke-AdbBestEffort {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    try {
        Invoke-Adb -Arguments $Arguments | Out-Null
    }
    catch {
        Write-Warning $_
    }
}

function Assert-AdbDevice {
    if ($VerifyOnly) {
        Write-Host (Format-AdbCommand -Arguments @("devices"))
        return
    }

    $output = & adb @script:AdbTarget devices 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB device check failed:`n$($output -join [Environment]::NewLine)"
    }
    if (($output -join [Environment]::NewLine) -notmatch "\bdevice\b") {
        throw "No active ADB device found:`n$($output -join [Environment]::NewLine)"
    }
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

function Stop-StudyApp {
    param([Parameter(Mandatory = $true)][string]$Package)

    Invoke-AdbBestEffort -Arguments @("shell", "am", "force-stop", $Package)
}

function Invoke-StudyAppReset {
    param(
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$Action,
        [Parameter(Mandatory = $true)][int]$ResultCode,
        [Parameter(Mandatory = $true)][string]$ResultData
    )

    if ($VerifyOnly) {
        Write-Host "am broadcast -a $Action -p $Package"
        return
    }

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

function Invoke-BestEffortStudyAppReset {
    param(
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$Action
    )

    Write-Host "am broadcast -a $Action -p $Package"
    Invoke-AdbBestEffort -Arguments @("shell", "am", "broadcast", "--include-stopped-packages", "-p", $Package, "-a", $Action)
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

Assert-AdbDevice

Write-Host "adb reverse tcp:8787 tcp:8787"
Invoke-Adb -Arguments @("reverse", "tcp:8787", "tcp:8787") | Out-Null
Write-Host "am broadcast -a com.caddie.studygallery.ACTION_RESET -p com.caddie.studygallery"
Write-Host "am broadcast -a com.caddie.studynotes.ACTION_RESET -p com.caddie.studynotes"

foreach ($reset in $resets) {
    Invoke-StudyAppReset -Package $reset.Package -Action $reset.Action -ResultCode $reset.ResultCode -ResultData $reset.ResultData
}

$bestEffortResets = @(
    @{
        Package = "com.caddie.studybank"
        Action = "com.caddie.studybank.ACTION_RESET"
        Command = "am broadcast -a com.caddie.studybank.ACTION_RESET -p com.caddie.studybank"
    }
    @{
        Package = "com.caddie.studymail"
        Action = "com.caddie.studymail.ACTION_RESET"
        Command = "am broadcast -a com.caddie.studymail.ACTION_RESET -p com.caddie.studymail"
    }
    @{
        Package = "com.caddie.studytelegram"
        Action = "com.caddie.studytelegram.ACTION_RESET"
        Command = "am broadcast -a com.caddie.studytelegram.ACTION_RESET -p com.caddie.studytelegram"
    }
)

foreach ($reset in $bestEffortResets) {
    Write-Host $reset.Command
    Invoke-BestEffortStudyAppReset -Package $reset.Package -Action $reset.Action
}

Invoke-Adb -Arguments @("shell", "cmd", "notification", "set_dnd", "off") | Out-Null
Write-Host "settings put global zen_mode 0"
Invoke-AdbBestEffort -Arguments @("shell", "settings", "put", "global", "zen_mode", "0")
Write-Host "settings put system screen_brightness 180"
Invoke-AdbBestEffort -Arguments @("shell", "settings", "put", "system", "screen_brightness", "180")
Write-Host "settings put system screen_off_timeout 600000"
Invoke-AdbBestEffort -Arguments @("shell", "settings", "put", "system", "screen_off_timeout", "600000")
Write-Host "cmd audio set-volume 3 7"
Invoke-AdbBestEffort -Arguments @("shell", "cmd", "audio", "set-volume", "3", "7")

Stop-StudyApp -Package "com.caddie"
Stop-StudyApp -Package "com.caddie.studybank"
Stop-StudyApp -Package "com.caddie.studycalendar"
Stop-StudyApp -Package "com.caddie.studymail"
Stop-StudyApp -Package "com.caddie.studytelegram"
Stop-StudyApp -Package "com.caddie.studygallery"
Stop-StudyApp -Package "com.caddie.studynotes"
Stop-StudyApp -Package "com.android.settings"

Write-Host "Study device reset complete."
