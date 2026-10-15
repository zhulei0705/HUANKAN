# 本轮只交付：默认只读预检；Execute同时要求后续技术Go与真实用户批准。
param([switch]$Execute,[switch]$UserApproved)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
if(-not $Execute){& "$PSScriptRoot/prepare-s69-inc01-original.ps1";return}
$gate=Get-Content "$root/docs/evidence/s69-inc01/20261014T063813Z/recovery-gate.json" -Raw|ConvertFrom-Json
if($gate.originalRecovery -ne 'Go' -or -not $UserApproved -or -not $gate.independentHostVerified -or -not $gate.shutdownAuthVerified -or -not $gate.businessRecoveryVerified){throw '正式恢复门禁未满足；本轮No-Go，未启动任何进程'}
& "$PSScriptRoot/prepare-s69-inc01-original.ps1" | Out-Null
. "$PSScriptRoot/load-local-env.ps1"
$jar="$root/docs/evidence/s69-inc01/20261014T063813Z/frozen/backend/target/huankan-s70-reviewed-0.1.0.jar"
if((Get-FileHash $jar).Hash.ToLowerInvariant() -ne '197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77'){throw '恢复冻结JAR不匹配'}
# 此分支仅可在后续重新评审且用户批准后，由已验证的独立宿主执行。
$env:HUANKAN_COLLECTOR_ENABLED='true'
$stamp=(Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ')
$out="$root/runtime/s69-inc01-original-$stamp.log";$err="$root/runtime/s69-inc01-original-$stamp-error.log"
$p=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-Djavax.net.ssl.trustStoreType=Windows-ROOT','-jar',$jar,'--server.address=127.0.0.1','--server.port=18080','--spring.flyway.enabled=false','--management.endpoint.shutdown.enabled=true','--management.endpoints.web.exposure.include=health,shutdown','--server.shutdown=graceful') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
$info=Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)"
@{started=[datetimeoffset]::Now.ToString('o');pid=$p.Id;createdAt=$info.CreationDate.ToUniversalTime().ToString('o');parentPid=$info.ParentProcessId;jar=$jar;sha256=(Get-FileHash $jar).Hash;port=18080;account=[Security.Principal.WindowsIdentity]::GetCurrent().Name;stdout=$out;stderr=$err;health='待实测';collection='待实测';rollback='待实测'}|ConvertTo-Json|Set-Content "$root/runtime/s69-inc01-original-$stamp-process.json" -Encoding utf8
Write-Output '已提交启动；所有验收状态待实际检查。此脚本不启动前端、研究服务或24小时观察。'
