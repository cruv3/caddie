# Minimaler Modell-Verifier: lms load + simple chat-probe pro Modell.
# Output: pro Modell Load-Zeit, Status, kurze Antwort.

$models = @(
    "qwen/qwen3.6-35b-a3b",
    "qwen/qwen3-vl-8b",
    "qwen/qwen3-8b",
    "google/gemma-4-e4b",
    "gemma-4-e2b-it",
    "pixtral-12b"
)

$h = @{
    "Authorization" = "Bearer sk-lm-iG96vKaS:xMhkAb4XIHQupKFkZJx7"
    "Content-Type"  = "application/json"
}

Write-Host ("{0,-40} {1,8} {2,8} {3,4} Antwort" -f "Modell","Load","Chat","OK")
Write-Host ("-" * 100)

foreach ($m in $models) {
    & lms unload --all *> $null
    Start-Sleep -Seconds 1
    $t0 = Get-Date
    & lms load $m --yes --parallel 1 --context-length 32768 --ttl 28800 --gpu max *> $null
    $loadOk = $LASTEXITCODE -eq 0
    $loadT = ((Get-Date) - $t0).TotalSeconds
    if (-not $loadOk) {
        Write-Host ("{0,-40} {1,7:F1}s LOAD-FAIL  X" -f $m, $loadT)
        continue
    }

    $body = @{
        model = $m
        input = "Antworte mit nur einem Wort: Hallo."
        stream = $false
    } | ConvertTo-Json -Compress

    $t1 = Get-Date
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:1234/api/v1/chat" -Headers $h -Method Post -Body $body -TimeoutSec 60 -SkipHttpErrorCheck
        $chatT = ((Get-Date) - $t1).TotalSeconds
        if ($r.StatusCode -eq 200) {
            $j = $r.Content | ConvertFrom-Json
            $msg = ""
            foreach ($item in $j.output) {
                if ($item.type -eq "message") { $msg = $item.content.Trim(); break }
            }
            if ([string]::IsNullOrEmpty($msg)) { $msg = "<empty>" }
            $short = $msg.Substring(0, [Math]::Min(60, $msg.Length))
            Write-Host ("{0,-40} {1,7:F1}s {2,7:F1}s  OK  {3}" -f $m, $loadT, $chatT, $short)
        } else {
            $err = ($r.Content | ConvertFrom-Json).error.message
            Write-Host ("{0,-40} {1,7:F1}s {2,7:F1}s  X   HTTP {3}: {4}" -f $m, $loadT, $chatT, $r.StatusCode, $err.Substring(0, [Math]::Min(80, $err.Length)))
        }
    } catch {
        $chatT = ((Get-Date) - $t1).TotalSeconds
        Write-Host ("{0,-40} {1,7:F1}s {2,7:F1}s  X   {3}" -f $m, $loadT, $chatT, $_.Exception.Message)
    }
}
