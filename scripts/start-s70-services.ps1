param([string]$JarName='huankan-s70-reviewed-0.1.0.jar')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot\load-local-env.ps1"
if($JarName -notmatch '^huankan-s70[-a-z0-9.]*[.]jar$'){throw 'JAR名称无效'}
if(-not (Test-Path "$root\backend\target\$JarName")){throw '请先完成后端打包'}
foreach($port in @(18080,18083,5173,5176)){if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw "端口$port正在使用，未重启任何已有服务"}}
if(-not (Test-Path "$root\docs\evidence\s63-backup-restore.json")){throw '缺少本轮正式库备份恢复证据'}
$backup=Get-Content "$root\docs\evidence\s63-backup-restore.json" -Raw|ConvertFrom-Json
if(-not $backup.restore_passed){throw '正式库备份恢复尚未通过'}
$saved=$env:HUANKAN_COLLECTOR_ENABLED
try{
 foreach($port in @(18080,18083)){
  $env:HUANKAN_COLLECTOR_ENABLED=if($port -eq 18080){'true'}else{'false'}
  $role=if($port -eq 18080){'main'}else{'research'}
  $p=Start-Process -FilePath 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe' -ArgumentList @('-Djavax.net.ssl.trustStoreType=Windows-ROOT','-jar',"$root\backend\target\$JarName","--server.port=$port") -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root\runtime\s70-$role.log" -RedirectStandardError "$root\runtime\s70-$role-error.log" -PassThru
  @{processId=$p.Id;createdAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)").CreationDate.ToUniversalTime().ToString('o');port=$port;jar=$JarName;collectorEnabled=($port -eq 18080);jarHash=(Get-FileHash "$root\backend\target\$JarName").Hash}|ConvertTo-Json|Set-Content "$root\runtime\s70-$role-process.json" -Encoding utf8
  $ready=$false
  for($i=0;$i -lt 90;$i++){try{if((Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}
  if(-not $ready){throw "$role 服务未就绪，保留进程与日志供诊断"}
 }
 foreach($port in @(5173,5176)){
  $config=if($port -eq 5173){'vite.config.ts'}else{'vite.s54.config.ts'}
  $p=Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @('node_modules/vite/bin/vite.js','--config',$config,'--strictPort') -WorkingDirectory "$root\frontend" -WindowStyle Hidden -RedirectStandardOutput "$root\runtime\s70-web-$port.log" -RedirectStandardError "$root\runtime\s70-web-$port-error.log" -PassThru
  @{processId=$p.Id;createdAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)").CreationDate.ToUniversalTime().ToString('o');port=$port}|ConvertTo-Json|Set-Content "$root\runtime\s70-web-$port-process.json" -Encoding utf8
 }
}finally{$env:HUANKAN_COLLECTOR_ENABLED=$saved}
Write-Output '主采集18080、研究18083与中文页面5173/5176已启动；未操作PostgreSQL服务，旧观察窗口不拼接。'
