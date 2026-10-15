$ErrorActionPreference='Stop'
. "$PSScriptRoot\load-local-env.ps1"
$root=Split-Path -Parent $PSScriptRoot
$pg='D:\Program Files\PostgreSQL\18\bin\psql.exe'
function Manifest {
 $env:PGPASSWORD=$env:HUANKAN_DB_PASSWORD
 try {
  $result=& $pg -h 127.0.0.1 -p 5432 -U huankan_app -d huankan -w -At -c "SELECT json_build_object('evidence_count',count(*),'manifest',encode(sha256(convert_to(string_agg(id::text||raw_content_hash,',' ORDER BY id),'UTF8')),'hex')) FROM raw_evidence;"
  if($LASTEXITCODE -ne 0){throw '业务库清单查询失败'}
  return $result|ConvertFrom-Json
 }finally{Remove-Item Env:PGPASSWORD}
}
$before=Manifest
& "$PSScriptRoot\start-services.ps1" -RestartBackend
$ready=$false
for($i=0;$i -lt 30;$i++){try{if((Invoke-RestMethod http://127.0.0.1:18080/actuator/health -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}
if(-not $ready){throw '重启后健康检查失败'}
$after=Manifest
$matches=$before.manifest -eq $after.manifest -and $before.evidence_count -eq $after.evidence_count
@{checked_at=[DateTimeOffset]::Now.ToString('o');before=$before;after=$after;unchanged=$matches;health='UP'}|ConvertTo-Json -Depth 4|Set-Content "$root\docs\evidence\persistence-restart.json"
if(-not $matches){throw '重启前后证据清单不一致'}
Write-Output '重启后全部原始证据ID与SHA-256清单保持一致。'
