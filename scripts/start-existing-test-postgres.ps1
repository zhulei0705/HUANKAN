$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$data=(Resolve-Path (Join-Path $root 'runtime\postgres-data')).Path
if($data -ne (Join-Path $root 'runtime\postgres-data')){throw '测试数据路径不匹配'}
$pg='D:\Program Files\PostgreSQL\18\bin'
& "$pg\pg_ctl.exe" -D $data status
if($LASTEXITCODE -ne 0){
 Start-Process -FilePath "$pg\pg_ctl.exe" -ArgumentList @('-D',('"'+$data+'"'),'-l',('"'+(Join-Path $root 'runtime\postgres.log')+'"'),'start') -WindowStyle Hidden | Out-Null
}
$ready=$false
for($i=0;$i -lt 40;$i++){
 & "$pg\pg_isready.exe" -h 127.0.0.1 -p 55432 -d huankan_test | Out-Null
 if($LASTEXITCODE -eq 0){$ready=$true;break};Start-Sleep -Milliseconds 500
}
if(-not $ready){throw '现有项目测试实例未就绪；未改动5432或本地凭证'}
Write-Output '项目现有55432测试实例已就绪；未初始化数据、修改角色或数据源环境变量。'
