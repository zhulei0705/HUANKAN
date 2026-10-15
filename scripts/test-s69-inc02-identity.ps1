param([string]$OutputFile="$PSScriptRoot/../docs/evidence/s69-inc02/identity-tests.json")
$ErrorActionPreference='Stop'
Import-Module "$PSScriptRoot/inc02-process-identity.psm1" -Force
$base=[pscustomobject]@{pid=12345;createdAt='2026-10-15T02:00:00Z';jar='D:\myAI\HUANKAN\backend\target\huankan-s70-reviewed-0.1.0.jar';jarSha256='197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77';java='C:\Program Files\Java\jdk-21.0.10\bin\java.exe';account='DESKTOP-JMS8B0H\sthl';port=18088;purpose='inc02-isolated-original'}
$results=@()
Assert-Inc02ProcessIdentity $base $base
$results+=@{name='完全匹配允许';passed=$true}
foreach($key in @('pid','createdAt','jar','jarSha256','java','account','port','purpose')){
 $copy=$base|ConvertTo-Json|ConvertFrom-Json
 $copy.$key=switch($key){'pid'{12346};'port'{18080};'createdAt'{'2026-10-15T02:00:01Z'};'jar'{'D:\myAI\HUANKAN\backend\target\huankan-s69-r3-candidate-0.1.0.jar'};'java'{'C:\Program Files\Java\jdk1.8.0_201\bin\java.exe'};'purpose'{'formal-original'};default{'错误值'}}
 $rejected=$false;try{Assert-Inc02ProcessIdentity $base $copy}catch{$rejected=$true}
 if(-not $rejected){throw "负例未拒绝：$key"};$results+=@{name="身份$key变化拒绝";passed=$true}
}
$same=$base|ConvertTo-Json|ConvertFrom-Json;$same.createdAt='2026-10-15T10:00:00+08:00';Assert-Inc02ProcessIdentity $base $same
$results+=@{name='时区归一化匹配';passed=$true}
$args=[Inc02CommandLine]::Parse('"C:\Program Files\Java\jdk-21.0.10\bin\java.exe" -Dhuankan.process.purpose=inc02-isolated-original -jar "D:\路径 含空格\original.jar" --server.port=18088')
if($args[3] -ne 'D:\路径 含空格\original.jar'){throw '空格路径解析失败'}
$results+=@{name='Windows命令行空格路径解析';passed=$true}
@{at=[datetimeoffset]::Now.ToString('o');tests=$results;count=$results.Count;passed=$true;actualProcessStopped=$false}|ConvertTo-Json -Depth 5|Set-Content $OutputFile -Encoding utf8
Write-Output "$($results.Count)项身份保护测试通过；未停止实际进程。"
