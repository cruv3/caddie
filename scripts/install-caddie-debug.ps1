[CmdletBinding()]
param(
    [string]$Serial,
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repository = Split-Path $PSScriptRoot -Parent
$package = "com.caddie.debug"
$activity = "$package/com.caddie.app.MainActivity"
$accessibilityService = "$package/com.caddie.app.CompanionAccessibilityService"
$apk = Join-Path $repository "app\build\outputs\apk\normal\debug\app-normal-debug.apk"
$adbTarget = if ($Serial) { @("-s", $Serial) } else { @() }

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & adb @adbTarget @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: adb $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return $output
}

function Wait-AccessibilityBinding {
    param([Parameter(Mandatory = $true)][string]$Component)

    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        # Secure settings can settle asynchronously after installation. Re-assert the
        # already-computed service list so a transient empty value cannot lose Caddie.
        $current = ((Invoke-Adb -Arguments @("shell", "settings", "get", "secure", "enabled_accessibility_services")) -join "").Trim()
        if (($current -split ":") -notcontains $Component) {
            Invoke-Adb -Arguments @("shell", "settings", "put", "secure", "enabled_accessibility_services", $script:enabledAccessibilityServices) | Out-Null
            Invoke-Adb -Arguments @("shell", "settings", "put", "secure", "accessibility_enabled", "1") | Out-Null
        }
        $state = (Invoke-Adb -Arguments @("shell", "dumpsys", "activity", "services", $Component)) -join "`n"
        if ($state -match "requested=true received=true hasBound=true") { return $true }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    return $false
}

Push-Location $repository
try {
    Invoke-Adb -Arguments @("get-state") | Out-Null

    if (-not $SkipBuild) {
        # This installer intentionally builds only Caddie's app module. Study
        # fixtures require the separate opt-in install-study-fixtures.ps1 helper.
        & .\gradlew.bat :app:assembleNormalDebug
        if ($LASTEXITCODE -ne 0) { throw "Caddie debug build failed" }
    }
    if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found: $apk" }

    # -r preserves app data. Voice input remains an explicit, optional choice.
    Invoke-Adb -Arguments @("install", "-r", $apk) | Out-Host
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $package) | Out-Null
    Invoke-Adb -Arguments @("shell", "pm", "grant", $package, "android.permission.POST_NOTIFICATIONS") | Out-Null
    Invoke-Adb -Arguments @("shell", "appops", "set", $package, "SYSTEM_ALERT_WINDOW", "allow") | Out-Null

    # Append Caddie without disabling accessibility services already enabled by the user.
    $enabled = ((Invoke-Adb -Arguments @("shell", "settings", "get", "secure", "enabled_accessibility_services")) -join "").Trim()
    if ($enabled -eq "null" -or [string]::IsNullOrWhiteSpace($enabled)) {
        $enabled = $accessibilityService
    }
    elseif (($enabled -split ":") -notcontains $accessibilityService) {
        $enabled = "$enabled`:$accessibilityService"
    }
    $script:enabledAccessibilityServices = $enabled
    Invoke-Adb -Arguments @("shell", "settings", "put", "secure", "enabled_accessibility_services", $enabled) | Out-Null
    Start-Sleep -Milliseconds 500
    Invoke-Adb -Arguments @("shell", "settings", "put", "secure", "accessibility_enabled", "1") | Out-Null

    Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-n", $activity) | Out-Host

    $overlay = (Invoke-Adb -Arguments @("shell", "appops", "get", $package, "SYSTEM_ALERT_WINDOW")) -join " "
    $accessibilityBound = Wait-AccessibilityBinding -Component $accessibilityService
    $services = (Invoke-Adb -Arguments @("shell", "settings", "get", "secure", "enabled_accessibility_services")) -join ""
    $packageState = (Invoke-Adb -Arguments @("shell", "dumpsys", "package", $package)) -join "`n"
    $notificationsGranted = $packageState -match "android.permission.POST_NOTIFICATIONS: granted=true"
    $failedChecks = @()
    if (-not $notificationsGranted) { $failedChecks += "notifications" }
    if ($overlay -notmatch "allow") { $failedChecks += "overlay" }
    if ($services -notmatch [regex]::Escape($accessibilityService)) { $failedChecks += "accessibility setting" }
    if (-not $accessibilityBound) { $failedChecks += "accessibility binding" }
    if ($failedChecks.Count -gt 0) {
        throw "Permission verification failed: $($failedChecks -join ', ')"
    }

    Write-Host "Caddie is installed and ready for text tasks. Voice input remains optional."
}
finally {
    Pop-Location
}
