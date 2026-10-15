$ErrorActionPreference='Stop'
Import-Module "$PSScriptRoot/inc02-process-identity.psm1" -Force
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path;$dest="$root/docs/evidence/s69-inc02"
$saved=Get-Content "$dest/original-run-identity.json" -Raw|ConvertFrom-Json
$results=@();$actual=Get-Inc02ProcessIdentity -ProcessId $saved.pid -Port 18088
Assert-Inc02ProcessIdentity $saved $actual
$results+=@{name='实际Java完整8项身份匹配';passed=$true;identity=$actual}
foreach($key in @('createdAt','jar','jarSha256','java','account','purpose')){
 $bad=$saved|ConvertTo-Json|ConvertFrom-Json
 $bad.$key=switch($key){'createdAt'{([datetimeoffset]$saved.createdAt).AddSeconds(1).ToString('o')};'jar'{"$root/backend/target/huankan-s69-r3-candidate-0.1.0.jar"};'java'{'C:\wrong\java.exe'};'purpose'{'formal-original'};default{'错误值'}}
 $path="$dest/negative-identity-$key.json";$bad|ConvertTo-Json|Set-Content $path -Encoding utf8
 $rejected=$false;try{& "$PSScriptRoot/stop-s69-inc02-owned.ps1" -IdentityPath $path -Execute|Out-Null}catch{$rejected=$true;$message=$_.Exception.Message}
 if(-not $rejected){throw "实际停止负例未拒绝$key"}
 $results+=@{name="真实停止脚本拒绝$key篡改";passed=$true;reason=$message}
}
$launcher=Get-Content "$dest/launcher.json" -Raw|ConvertFrom-Json
$parent=Get-CimInstance Win32_Process -Filter "ProcessId=$($launcher.launcherPid)"
if($parent){throw '直接启动器仍在，不满足本测试'}
$supervisor=Get-CimInstance Win32_Process -Filter "ProcessId=$($launcher.supervisorPid)"
if(-not $supervisor){throw '监督器已退出'}
$results+=@{name='直接启动器退出后监督器与Java仍在';passed=$true;limitation='未验证Codex应用维护/job销毁及注销；没有注册任务计划程序'}
@{at=[datetimeoffset]::Now.ToString('o');count=$results.Count;tests=$results;passed=$true}|ConvertTo-Json -Depth 8|Set-Content "$dest/live-identity-tests.json" -Encoding utf8
Write-Output "$($results.Count)项实际进程保护验证通过。"
