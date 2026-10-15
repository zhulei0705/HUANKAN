param([Parameter(Mandatory)][string]$IdentityPath,[switch]$Execute)
$ErrorActionPreference='Stop'
Import-Module "$PSScriptRoot/inc02-process-identity.psm1" -Force
$saved=Get-Content -LiteralPath $IdentityPath -Raw|ConvertFrom-Json
if($saved.purpose -ne 'inc02-isolated-original' -or $saved.port -ne 18088){throw '仅允许INC02隔离原版；正式进程及他项服务拒绝操作'}
if($saved.jarSha256 -ne '197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77'){throw '隔离原版固定SHA不匹配'}
$actual=Get-Inc02ProcessIdentity -ProcessId $saved.pid -Port $saved.port
Assert-Inc02ProcessIdentity -Expected $saved -Actual $actual
if(-not $Execute){@{at=[datetimeoffset]::Now.ToString('o');identity=$actual;action='只读停止预检';executed=$false}|ConvertTo-Json -Depth 6;return}
if(-not $env:HUANKAN_INC02_TEST_TOKEN){throw '隔离停止令牌缺失'}
# POST前再次核验全部身份；不使用Stop-Process/taskkill，也不关闭整棵进程树。
$again=Get-Inc02ProcessIdentity -ProcessId $saved.pid -Port $saved.port
Assert-Inc02ProcessIdentity -Expected $saved -Actual $again
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$($saved.port)/actuator/shutdown" -Headers @{Authorization="Bearer $env:HUANKAN_INC02_TEST_TOKEN"} -ContentType 'application/json' -Body '{}' -TimeoutSec 5|Out-Null
$p=Get-Process -Id $saved.pid -ErrorAction SilentlyContinue
if($p -and -not $p.WaitForExit(15000)){throw '正常停止超时，保留现场，不强杀'}
@{at=[datetimeoffset]::Now.ToString('o');pid=$saved.pid;stopped=$true;forced=$false}|ConvertTo-Json
