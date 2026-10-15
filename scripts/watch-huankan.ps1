param([int]$DurationSeconds=900,[int]$IntervalSeconds=15,[switch]$Recover)
$ErrorActionPreference='Stop'
if($DurationSeconds -lt 60 -or $DurationSeconds -gt 86400 -or $IntervalSeconds -lt 5){throw '运行时长或检查间隔无效'}
$root=Split-Path -Parent $PSScriptRoot
$started=[DateTime]::UtcNow;$failures=0;$recoveries=0;$samples=@()
do {
    $health=$null;$detail=$null
    try{$health=(Invoke-RestMethod 'http://127.0.0.1:18080/actuator/health' -TimeoutSec 5).status;if($health -ne 'UP'){$failures++}else{$failures=0}}
    catch{$failures++;$detail=$_.Exception.GetType().Name}
    $action='OBSERVE'
    if($Recover -and $failures -ge 3 -and $recoveries -lt 2){
        # start-services verifies the exact jar, PID creation time and owned port before stopping anything.
        & "$PSScriptRoot\start-services.ps1" -RestartBackend | Out-Null
        $action='RESTART_OWN_BACKEND';$recoveries++;$failures=0
    }
    $samples+=@{time=[DateTime]::UtcNow.ToString('o');health=$health;error=$detail;consecutive_failures=$failures;action=$action}
    @{started_at=$started.ToString('o');actual_duration_seconds=([DateTime]::UtcNow-$started).TotalSeconds;complete=$false;recoveries=$recoveries;scope='Owned HUANKAN process only; no Windows registration or PostgreSQL service operation';samples=$samples}|ConvertTo-Json -Depth 6|Set-Content -Encoding utf8 "$root\docs\evidence\s21-watch.json"
    if(([DateTime]::UtcNow-$started).TotalSeconds -ge $DurationSeconds){break}
    Start-Sleep -Seconds $IntervalSeconds
}while($true)
@{started_at=$started.ToString('o');finished_at=[DateTime]::UtcNow.ToString('o');actual_duration_seconds=([DateTime]::UtcNow-$started).TotalSeconds;complete=$true;recoveries=$recoveries;samples=$samples}|ConvertTo-Json -Depth 6|Set-Content -Encoding utf8 "$root\docs\evidence\s21-watch.json"
Write-Output "实际观察 $([math]::Round(([DateTime]::UtcNow-$started).TotalSeconds,1)) 秒，恢复操作 $recoveries 次。"
