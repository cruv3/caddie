[CmdletBinding()]
param(
    [string]$Serial,

    [int]$CalendarId = 1,

    [switch]$SkipCalendar,

    [switch]$VerifyOnly
)

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
        throw "ADB failed: $command`n$($output -join [Environment]::NewLine)"
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
    $arguments = @("devices")
    $command = Format-AdbCommand -Arguments $arguments
    Write-Host $command
    if ($VerifyOnly) {
        return
    }

    $output = & adb @script:AdbTarget devices 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB device check failed:`n$($output -join [Environment]::NewLine)"
    }
    $text = $output -join [Environment]::NewLine
    if ($text -notmatch "\bdevice\b") {
        throw "No active ADB device found:`n$text"
    }
}

function Invoke-StudyAppReset {
    param(
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$Action
    )

    Invoke-Adb -Arguments @("shell", "am", "broadcast", "-a", $Action, "-p", $Package) | Out-Null
}

function Stop-StudyApp {
    param([Parameter(Mandatory = $true)][string]$Package)

    Invoke-AdbBestEffort -Arguments @("shell", "am", "force-stop", $Package)
}

Assert-AdbDevice

Write-Host "adb reverse tcp:8787 tcp:8787"
Invoke-Adb -Arguments @("reverse", "tcp:8787", "tcp:8787") | Out-Null

$studyAppResets = @(
    @{
        Package = "com.caddie.studybank"
        Action = "com.caddie.studybank.ACTION_RESET"
        Command = "am broadcast -a com.caddie.studybank.ACTION_RESET -p com.caddie.studybank"
    },
    @{
        Package = "com.caddie.studymail"
        Action = "com.caddie.studymail.ACTION_RESET"
        Command = "am broadcast -a com.caddie.studymail.ACTION_RESET -p com.caddie.studymail"
    },
    @{
        Package = "com.caddie.studytelegram"
        Action = "com.caddie.studytelegram.ACTION_RESET"
        Command = "am broadcast -a com.caddie.studytelegram.ACTION_RESET -p com.caddie.studytelegram"
    },
    @{
        Package = "com.caddie.studygallery"
        Action = "com.caddie.studygallery.ACTION_RESET"
        Command = "am broadcast -a com.caddie.studygallery.ACTION_RESET -p com.caddie.studygallery"
    },
    @{
        Package = "com.caddie.studynotes"
        Action = "com.caddie.studynotes.ACTION_RESET"
        Command = "am broadcast -a com.caddie.studynotes.ACTION_RESET -p com.caddie.studynotes"
    }
)

foreach ($reset in $studyAppResets) {
    Write-Host $reset.Command
    Invoke-StudyAppReset -Package $reset.Package -Action $reset.Action
}

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
Stop-StudyApp -Package "com.caddie.studymail"
Stop-StudyApp -Package "com.caddie.studytelegram"
Stop-StudyApp -Package "com.caddie.studygallery"
Stop-StudyApp -Package "com.caddie.studynotes"

if (-not $SkipCalendar) {
    $calendarScript = Join-Path $PSScriptRoot "reset_study_calendar.ps1"
    $calendarArgs = @("-CalendarId", $CalendarId)
    if ($Serial) {
        $calendarArgs += @("-Serial", $Serial)
    }
    if ($VerifyOnly) {
        $calendarArgs += "-VerifyOnly"
    }
    & $calendarScript @calendarArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Calendar reset failed"
    }
}

Write-Host "Study device reset complete."
