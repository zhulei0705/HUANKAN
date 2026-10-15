$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
$jar="$root/backend/target/huankan-s64-closure-final-0.1.0.jar"
if(-not(Test-Path $jar)){throw '缺少本轮最终JAR'}
$saved=Get-Content "$root/runtime/s64-closure-process.json" -Raw|ConvertFrom-Json
if($saved.port -ne 18085 -or $saved.schedulingEnabled -or $saved.collectorEnabled){throw '只允许本轮无调度研究进程'}
$p=Get-CimInstance Win32_Process -Filter "ProcessId=$($saved.processId)"
if($p){if(-not $p.CommandLine.Contains('--server.port=18085') -or -not $p.CommandLine.Contains('--huankan.scheduling-enabled=false') -or $p.CreationDate.ToUniversalTime().Ticks -ne ([datetime]$saved.createdAt).ToUniversalTime().Ticks){throw '进程身份不一致，拒绝停止'};Stop-Process -Id $saved.processId}
$oldEnabled=$env:HUANKAN_COLLECTOR_ENABLED
try{
 $env:HUANKAN_COLLECTOR_ENABLED='false'
 $next=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-jar',$jar,'--server.port=18085','--spring.flyway.enabled=false','--huankan.scheduling-enabled=false') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s64-closure-research-final.log" -RedirectStandardError "$root/runtime/s64-closure-research-final-error.log" -PassThru
 $info=@{processId=$next.Id;createdAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($next.Id)").CreationDate.ToUniversalTime().ToString('o');jarHash=(Get-FileHash $jar).Hash;port=18085;schedulingEnabled=$false;collectorEnabled=$false;flywayEnabled=$false}
 @{replaced=$saved;current=$info;at=[datetimeoffset]::Now.ToString('o');scope='仅本轮新增研究服务18085；原18080/18083与观察进程未操作'}|ConvertTo-Json -Depth 8|Set-Content "$root/docs/evidence/s64-closure-sidecar-update.json"
 $info|ConvertTo-Json|Set-Content "$root/runtime/s64-closure-process.json"
}finally{$env:HUANKAN_COLLECTOR_ENABLED=$oldEnabled}
$ready=$false
for($i=0;$i -lt 120;$i++){try{if((Invoke-RestMethod http://127.0.0.1:18085/actuator/health -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}
if(-not $ready){throw '新研究服务未就绪，保留进程与日志'}
Write-Output '本轮独立研究服务更新完成；正式采集与原观察未重启。'
