$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
$candidatePath="$root/runtime/s69-isolation-candidate.json"
if(-not(Test-Path -LiteralPath $candidatePath)){throw '请先运行prepare-s69-isolation.mjs并完成隔离验证，禁止使用共享frontend启动S66'}
$candidate=Get-Content -Raw -LiteralPath $candidatePath|ConvertFrom-Json
$frontendRoot=(Resolve-Path -LiteralPath $candidate.work).Path
if(-not $frontendRoot.StartsWith("$root\runtime\s69-isolation\",[StringComparison]::OrdinalIgnoreCase) -or (Get-Item -LiteralPath $frontendRoot).Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)){throw 'S66开发目录必须为独立源码副本'}
if(-not(Test-Path -LiteralPath "$frontendRoot/vite.s69-isolated.config.ts") -or -not(Test-Path -LiteralPath "$frontendRoot/node_modules")){throw '独立配置或依赖缺失'}
foreach($port in @(18087,5181)){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "端口$port已占用，未操作已有进程"}}
$jar="$root/backend/target/huankan-s66-accepted-0.1.0.jar"
if(-not(Test-Path $jar)){throw '请先完成本轮后端打包'}
if($env:HUANKAN_DB_URL -ne 'jdbc:postgresql://127.0.0.1:5432/huankan'){throw '仅允许本项目数据库'}
$saved=$env:HUANKAN_COLLECTOR_ENABLED
try{
 $env:HUANKAN_COLLECTOR_ENABLED='false'
 $p=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-jar',$jar,'--server.port=18087','--spring.flyway.enabled=false','--huankan.scheduling-enabled=false') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s66-research.log" -RedirectStandardError "$root/runtime/s66-research-error.log" -PassThru
 @{processId=$p.Id;createdAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)").CreationDate.ToUniversalTime().ToString('o');jarHash=(Get-FileHash $jar).Hash;port=18087;schedulingEnabled=$false;collectorEnabled=$false;flywayEnabled=$false}|ConvertTo-Json|Set-Content "$root/runtime/s66-process.json"
 $web=Start-Process (Get-Command node.exe).Source -ArgumentList @('node_modules/vite/bin/vite.js','--config','vite.s69-isolated.config.ts','--strictPort') -WorkingDirectory $frontendRoot -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s66-isolated-dev-$($candidate.stamp).log" -RedirectStandardError "$root/runtime/s66-isolated-dev-$($candidate.stamp)-error.log" -PassThru
 @{processId=$web.Id;createdAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($web.Id)").CreationDate.ToUniversalTime().ToString('o');port=5181;workingDirectory=$frontendRoot}|ConvertTo-Json|Set-Content "$root/runtime/s66-isolated-dev-process.json"
}finally{$env:HUANKAN_COLLECTOR_ENABLED=$saved}
$ready=$false
for($i=0;$i -lt 120;$i++){try{if((Invoke-RestMethod http://127.0.0.1:18087/actuator/health -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}
if(-not $ready){throw '新研究服务未就绪，保留日志供排查；没有操作原服务'}
Write-Output 'S66独立开发入口5181，API18087；采集、调度、迁移均关闭，已有服务未重启。'




