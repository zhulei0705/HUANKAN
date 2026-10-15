$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
foreach($port in @(18085,5178)){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "端口$port已占用，未操作已有进程"}}
$jar="$root/backend/target/huankan-s64-closure-final-0.1.0.jar"
if(-not(Test-Path $jar)){throw '请先完成本轮后端打包'}
if($env:HUANKAN_DB_URL -ne 'jdbc:postgresql://127.0.0.1:5432/huankan'){throw '仅允许本项目数据库'}
$saved=$env:HUANKAN_COLLECTOR_ENABLED
try{
 $env:HUANKAN_COLLECTOR_ENABLED='false'
 $p=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-jar',$jar,'--server.port=18085','--spring.flyway.enabled=false','--huankan.scheduling-enabled=false') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s64-closure-research.log" -RedirectStandardError "$root/runtime/s64-closure-research-error.log" -PassThru
 @{processId=$p.Id;createdAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)").CreationDate.ToUniversalTime().ToString('o');jarHash=(Get-FileHash $jar).Hash;port=18085;schedulingEnabled=$false;collectorEnabled=$false;flywayEnabled=$false}|ConvertTo-Json|Set-Content "$root/runtime/s64-closure-process.json"
 $web=Start-Process (Get-Command node.exe).Source -ArgumentList @('node_modules/vite/bin/vite.js','--config','vite.s64.config.ts','--strictPort') -WorkingDirectory "$root/frontend" -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s64-closure-web.log" -RedirectStandardError "$root/runtime/s64-closure-web-error.log" -PassThru
 @{processId=$web.Id;createdAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($web.Id)").CreationDate.ToUniversalTime().ToString('o');port=5178}|ConvertTo-Json|Set-Content "$root/runtime/s64-closure-web-process.json"
}finally{$env:HUANKAN_COLLECTOR_ENABLED=$saved}
$ready=$false
for($i=0;$i -lt 120;$i++){try{if((Invoke-RestMethod http://127.0.0.1:18085/actuator/health -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}
if(-not $ready){throw '新研究服务未就绪，保留日志供排查；没有操作原服务'}
Write-Output '新研究入口5178，API18085；采集、调度、迁移均关闭，已有服务未重启。'

