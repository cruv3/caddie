Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param([string[]]$Arguments)

    $output = & adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed with exit code $LASTEXITCODE: adb $($Arguments -join ' ')"
    }

    return $output
}

function Stop-StudyApp {
    param([string]$Package)

    Invoke-Adb -Arguments @("shell", "am", "force-stop", $Package) | Out-Null
}

function Invoke-StudyAppReset {
    param([string]$Package, [string]$Action)

    Stop-StudyApp -Package $Package
    $broadcastOutput = Invoke-Adb -Arguments @("shell", "am", "broadcast", "-p", $Package, "-a", $Action)
    if (($broadcastOutput -join "`n") -notmatch "Broadcast completed") {
        throw "ADB broadcast did not confirm completion for $Package"
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
