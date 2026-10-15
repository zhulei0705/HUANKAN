param([switch]$BackendOnly,[string]$JarName='huankan-s54-final-0.1.0.jar')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\load-local-env.ps1"
if($JarName -notmatch '^huankan-s54[-a-z0-9.]*[.]jar$'){throw '增量JAR文件名不在允许范围'}
foreach($port in $(if($BackendOnly){@(18083)}else{@(18083,5176)})){if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw "专用端口 $port 已占用，未操作其他进程"}}
$primary=Get-CimInstance Win32_Process -Filter 'ProcessId=16904'
if(-not $primary){throw '主服务不在原观察进程，先核查连续运行日志'}
$before=@{pid=$primary.ProcessId;created=$primary.CreationDate.ToUniversalTime().ToString('o');jarHash=(Get-FileHash "$root\backend\target\huankan-0.1.0.jar").Hash}
$saved=$env:HUANKAN_COLLECTOR_ENABLED
try{
 $env:HUANKAN_COLLECTOR_ENABLED='false'
 $p=Start-Process -FilePath 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe' -ArgumentList @('-Djavax.net.ssl.trustStoreType=Windows-ROOT','-jar',"$root\backend\target\$JarName",'--server.port=18083') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root\runtime\s54-service.log" -RedirectStandardError "$root\runtime\s54-service-error.log" -PassThru
 $meta=Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)"
 @{pid=$p.Id;created=$meta.CreationDate.ToUniversalTime().ToString('o');collectorEnabled=$false;port=18083;primaryBefore=$before}|ConvertTo-Json -Depth 5|Set-Content "$root\runtime\s54-service-process.json" -Encoding utf8
}finally{$env:HUANKAN_COLLECTOR_ENABLED=$saved}
$ready=$false
for($i=0;$i -lt 40;$i++){try{if((Invoke-RestMethod http://127.0.0.1:18083/actuator/health -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}
if(-not $ready){throw '增量服务健康检查失败，请查看 s54-service 日志，未重启主服务'}
if(-not $BackendOnly){
$node=(Get-Command node.exe).Source
$web=Start-Process -FilePath $node -ArgumentList @('node_modules/vite/bin/vite.js','--config','vite.s54.config.ts') -WorkingDirectory "$root\frontend" -WindowStyle Hidden -RedirectStandardOutput "$root\runtime\s54-web.log" -RedirectStandardError "$root\runtime\s54-web-error.log" -PassThru
@{pid=$web.Id;created=(Get-CimInstance Win32_Process -Filter "ProcessId=$($web.Id)").CreationDate.ToUniversalTime().ToString('o');port=5176}|ConvertTo-Json|Set-Content "$root\runtime\s54-web-process.json" -Encoding utf8
}
$after=Get-CimInstance Win32_Process -Filter 'ProcessId=16904'
@{before=$before;after=@{pid=$after.ProcessId;created=$after.CreationDate.ToUniversalTime().ToString('o');jarHash=(Get-FileHash "$root\backend\target\huankan-0.1.0.jar").Hash};checkedAt=(Get-Date).ToUniversalTime().ToString('o')}|ConvertTo-Json -Depth 5|Set-Content "$root\docs\evidence\s54-primary-preserved.json" -Encoding utf8
Write-Output '增量研究服务18083与中文入口5176就绪；原主服务18080、原Web5173未重启。'
