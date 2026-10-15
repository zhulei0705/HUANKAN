param([string]$Candidate='runtime/s69-isolation-candidate.json')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
$entry=Get-Content -Raw -LiteralPath (Join-Path $root $Candidate)|ConvertFrom-Json
foreach($port in @(5181,5182)){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "端口$port已占用，不操作已有进程"}}
$node=(Get-Command node.exe).Source
$started=@()
foreach($kind in @('static','development')){
 $args=if($kind -eq 'static'){@("$root/scripts/serve-s69-static.mjs",$entry.release,'5182',$entry.manifest_sha256)}else{@('node_modules/vite/bin/vite.js','--config','vite.s69-isolated.config.ts','--strictPort')}
 $cwd=if($kind -eq 'static'){$root}else{$entry.work}
 $log="$root/runtime/s69-isolation-$($entry.stamp)-$kind.log";$errorLog="$root/runtime/s69-isolation-$($entry.stamp)-$kind-error.log"
 if((Test-Path -LiteralPath $log) -or (Test-Path -LiteralPath $errorLog)){throw '该版本启动日志已存在，拒绝覆盖'}
 $p=Start-Process $node -ArgumentList $args -WorkingDirectory $cwd -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $errorLog -PassThru
 $info=Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)"
 $started+=@{kind=$kind;processId=$p.Id;createdAt=$info.CreationDate.ToUniversalTime().ToString('o');workingDirectory=$cwd;command=$info.CommandLine;log=$log;errorLog=$errorLog}
}
@{candidate=$entry;processes=$started}|ConvertTo-Json -Depth 6|Set-Content "$root/runtime/s69-isolation-$($entry.stamp)-processes.json" -Encoding utf8
Write-Output '独立开发5181与候选静态5182已启动；原5173/5180、Java与PostgreSQL未操作。'
