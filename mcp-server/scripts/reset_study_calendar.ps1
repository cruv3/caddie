[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [int]$CalendarId,

    [string]$Serial,

    [switch]$VerifyOnly
)

$ErrorActionPreference = "Stop"
$mcpRoot = Split-Path -Parent $PSScriptRoot
$scratchRoot = Join-Path $mcpRoot ".tmp"
$calendarsFile = Join-Path $scratchRoot "calendar-reset-calendars.txt"
$eventsFile = Join-Path $scratchRoot "calendar-reset-events.txt"
$uiDumpFile = Join-Path $scratchRoot "calendar-reset-ui.xml"
$calendarProjection = "_id:account_type:visible:calendar_access_level"
$eventProjection = "_id:calendar_id:title:description:dtstart:dtend:eventTimezone:deleted:dirty:_sync_id"

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & adb @script:AdbTarget @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: adb $($script:AdbTarget -join ' ') $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    if (($output -join [Environment]::NewLine) -match "Error while accessing provider") {
        throw "Calendar Provider rejected the ADB request: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Save-ProviderState {
    $calendarOutput = Invoke-Adb -Arguments @(
        "shell", "content", "query",
        "--uri", "content://com.android.calendar/calendars",
        "--projection", $calendarProjection
    )
    $eventOutput = Invoke-Adb -Arguments @(
        "shell", "content", "query",
        "--uri", "content://com.android.calendar/events",
        "--projection", $eventProjection
    )
    [IO.File]::WriteAllLines($calendarsFile, [string[]]$calendarOutput)
    [IO.File]::WriteAllLines($eventsFile, [string[]]$eventOutput)
}

function Invoke-Planner {
    param([Parameter(Mandatory = $true)][ValidateSet("plan", "verify")][string]$Command)

    Push-Location $mcpRoot
    try {
        $json = & python -m caddie.study.calendar_reset $Command `
            --calendar-id $CalendarId `
            --calendars-file $calendarsFile `
            --events-file $eventsFile `
            --local-date $DeviceLocalDate `
            --timezone $DeviceTimezone 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Calendar reset validation failed:`n$($json -join [Environment]::NewLine)"
        }
        return ($json -join [Environment]::NewLine) | ConvertFrom-Json -DateKind String
    }
    finally {
        Pop-Location
    }
}

function Get-UiNode {
    param(
        [Parameter(Mandatory = $true)][string]$XPath,
        [Parameter(Mandatory = $true)][string]$Description
    )

    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        Invoke-Adb -Arguments @(
            "shell", "uiautomator", "dump", "/sdcard/caddie-calendar-reset.xml"
        ) | Out-Null
        Invoke-Adb -Arguments @(
            "pull", "/sdcard/caddie-calendar-reset.xml", $uiDumpFile
        ) | Out-Null
        [xml]$document = Get-Content -Raw $uiDumpFile
        $node = $document.SelectSingleNode($XPath)
        if ($node) {
            return $node
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Google Calendar UI element not found: $Description"
}

function Invoke-UiTap {
    param([Parameter(Mandatory = $true)]$Node)

    if ($Node.bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        throw "Google Calendar returned invalid accessibility bounds"
    }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Invoke-Adb -Arguments @("shell", "input", "tap", [string]$x, [string]$y) | Out-Null
    Start-Sleep -Milliseconds 700
}

function Invoke-CalendarUiReset {
    param(
        [Parameter(Mandatory = $true)][int]$EventId,
        [Parameter(Mandatory = $true)][long]$ExpectedStartMs,
        [Parameter(Mandatory = $true)][long]$ExpectedEndMs
    )

    $eventUri = "content://com.android.calendar/events/$EventId"
    $state = (Invoke-Adb -Arguments @(
        "shell", "content", "query", "--uri", $eventUri,
        "--projection", "_id:calendar_id:description:dtstart:dtend:dirty:_sync_id:deleted"
    )) -join [Environment]::NewLine
    if (
        $state -notmatch "calendar_id=$CalendarId" -or
        $state -notmatch "description=CADDIE_STUDY_T4_V1" -or
        $state -notmatch "deleted=0"
    ) {
        throw "Refusing UI reset because event $EventId is not the active marked study event"
    }

    $hasBaseline = (
        $state -match "dtstart=$ExpectedStartMs" -and
        $state -match "dtend=$ExpectedEndMs"
    )
    $isSynchronized = (
        $state -match "dirty=0" -and
        $state -match "_sync_id=(?!NULL)([^,\r\n]+)"
    )
    if ($hasBaseline -and $isSynchronized) {
        return
    }

    Invoke-Adb -Arguments @("shell", "input", "keyevent", "HOME") | Out-Null
    Start-Sleep -Milliseconds 500
    Invoke-Adb -Arguments @(
        "shell", "am", "start", "-a", "android.intent.action.VIEW",
        "-d", $eventUri, "-p", "com.google.android.calendar"
    ) | Out-Null
    $edit = Get-UiNode -XPath "//node[@content-desc='Bearbeiten']" -Description "Bearbeiten"
    Invoke-UiTap -Node $edit

    $startField = Get-UiNode `
        -XPath "//node[starts-with(@content-desc,'Beginnt um:')]" `
        -Description "Beginnt um"
    if (-not $hasBaseline) {
        Invoke-UiTap -Node $startField
        $hour = Get-UiNode `
            -XPath "//node[(starts-with(@content-desc,'14') or starts-with(@text,'14')) and (contains(@content-desc,'Stunden') or contains(@text,'Stunden'))]" `
            -Description "14 Stunden"
        Invoke-UiTap -Node $hour
        $ok = Get-UiNode `
            -XPath "//node[@text='OK' or @content-desc='OK']" `
            -Description "OK"
        Invoke-UiTap -Node $ok
        Get-UiNode `
            -XPath "//node[@content-desc='Beginnt um: 14:00']" `
            -Description "Beginnt um: 14:00" | Out-Null
        Get-UiNode `
            -XPath "//node[@content-desc='Endet um: 15:00']" `
            -Description "Endet um: 15:00" | Out-Null
    }

    $save = Get-UiNode `
        -XPath "//node[@text='Speichern' or @content-desc='Speichern']" `
        -Description "Speichern"
    Invoke-UiTap -Node $save

    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        $finalState = (Invoke-Adb -Arguments @(
            "shell", "content", "query", "--uri", $eventUri,
            "--projection", "_id:calendar_id:description:dtstart:dtend:dirty:_sync_id:deleted"
        )) -join [Environment]::NewLine
        if (
            $finalState -match "dtstart=$ExpectedStartMs" -and
            $finalState -match "dtend=$ExpectedEndMs" -and
            $finalState -match "dirty=0" -and
            $finalState -match "_sync_id=(?!NULL)([^,\r\n]+)" -and
            $finalState -match "deleted=0"
        ) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Google Calendar did not persist and synchronize the study baseline"
}

& adb start-server | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Unable to start the ADB server"
}

if ($Serial) {
    $script:AdbTarget = @("-s", $Serial)
    $state = (& adb @script:AdbTarget get-state 2>&1) -join ""
    if ($LASTEXITCODE -ne 0 -or $state.Trim() -ne "device") {
        throw "ADB device '$Serial' is not authorized and online"
    }
}
else {
    $devices = @(
        & adb devices |
            Select-Object -Skip 1 |
            Where-Object { $_ -match "^([^\s]+)\s+device$" } |
            ForEach-Object { $Matches[1] }
    )
    if ($devices.Count -ne 1) {
        throw "Expected exactly one authorized ADB device, found $($devices.Count)"
    }
    $Serial = $devices[0]
    $script:AdbTarget = @("-s", $Serial)
    $state = (& adb @script:AdbTarget get-state 2>&1) -join ""
    if ($LASTEXITCODE -ne 0 -or $state.Trim() -ne "device") {
        throw "ADB device '$Serial' is not online"
    }
}

$DeviceLocalDate = ((Invoke-Adb -Arguments @("shell", "date", "+%F")) -join "").Trim()
if ($DeviceLocalDate -notmatch "^\d{4}-\d{2}-\d{2}$") {
    throw "Android device returned an invalid local date: '$DeviceLocalDate'"
}
$DeviceTimezone = ((Invoke-Adb -Arguments @(
    "shell", "getprop", "persist.sys.timezone"
)) -join "").Trim()
if ([string]::IsNullOrWhiteSpace($DeviceTimezone)) {
    throw "Android device returned no IANA timezone"
}

New-Item -ItemType Directory -Force -Path $scratchRoot | Out-Null

try {
    Save-ProviderState
    if ($VerifyOnly) {
        $result = Invoke-Planner -Command "verify"
        Write-Output (@{
            ok = [bool]$result.ok
            serial = $Serial
            calendar_id = $CalendarId
            mode = "verify"
        } | ConvertTo-Json -Compress)
        return
    }

    $plan = Invoke-Planner -Command "plan"
    foreach ($operation in $plan.operations) {
        $arguments = @(
            "shell", "content", [string]$operation.verb,
            "--uri", [string]$operation.uri
        )
        foreach ($bind in $operation.binds) {
            $arguments += @("--bind", [string]$bind)
        }
        Invoke-Adb -Arguments $arguments | Out-Null
    }

    Save-ProviderState
    $uiPlan = Invoke-Planner -Command "plan"
    if (-not $uiPlan.requires_ui_reset -or -not $uiPlan.ui_event_id) {
        throw "Calendar planner did not return one exact study event for UI reset"
    }
    Invoke-CalendarUiReset `
        -EventId ([int]$uiPlan.ui_event_id) `
        -ExpectedStartMs ([long]$uiPlan.start_ms) `
        -ExpectedEndMs ([long]$uiPlan.end_ms)
    Save-ProviderState
    $verification = Invoke-Planner -Command "verify"
    Invoke-Adb -Arguments @(
        "shell", "am", "start", "-a", "android.intent.action.VIEW",
        "-d", "content://com.android.calendar/time/$($verification.start_ms)",
        "-p", "com.google.android.calendar"
    ) | Out-Null
    Start-Sleep -Milliseconds 700
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "HOME") | Out-Null
    Write-Output (@{
        ok = [bool]$verification.ok
        serial = $Serial
        calendar_id = $CalendarId
        operations = @($plan.operations).Count
        start = [string]$verification.start_iso
        end = [string]$verification.end_iso
    } | ConvertTo-Json -Compress)
}
finally {
    Remove-Item -LiteralPath $calendarsFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $eventsFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $uiDumpFile -Force -ErrorAction SilentlyContinue
}
