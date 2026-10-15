Set-StrictMode -Version Latest
if(-not ('Inc02CommandLine' -as [type])){
 Add-Type -TypeDefinition @'
using System;using System.Runtime.InteropServices;
public static class Inc02CommandLine {
 [DllImport("shell32.dll",SetLastError=true)]static extern IntPtr CommandLineToArgvW([MarshalAs(UnmanagedType.LPWStr)]string cmd,out int count);
 [DllImport("kernel32.dll")]static extern IntPtr LocalFree(IntPtr p);
 public static string[] Parse(string cmd){int n;IntPtr p=CommandLineToArgvW(cmd,out n);if(p==IntPtr.Zero)throw new Exception("命令行解析失败");try{var a=new string[n];for(int i=0;i<n;i++)a[i]=Marshal.PtrToStringUni(Marshal.ReadIntPtr(p,i*IntPtr.Size));return a;}finally{LocalFree(p);}}
}
'@
}
function Get-Inc02ProcessIdentity {
 param([int]$ProcessId,[int]$Port)
 $p=Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
 if(-not $p){throw '目标进程不存在'}
 $owner=Invoke-CimMethod -InputObject $p -MethodName GetOwner
 if($owner.ReturnValue -ne 0){throw '无法核实进程运行账户'}
 $args=[Inc02CommandLine]::Parse($p.CommandLine)
 $jarPositions=@(for($i=0;$i -lt $args.Length;$i++){if($args[$i] -eq '-jar'){$i}})
 if($jarPositions.Count -ne 1 -or $jarPositions[0]+1 -ge $args.Length){throw '必须具有唯一-jar参数'}
 $jar=$args[$jarPositions[0]+1]
 if($jar -notmatch '^(?:[A-Za-z]:[\\/]|\\\\)'){throw 'JAR必须使用绝对路径'}
 $jar=(Resolve-Path -LiteralPath $jar).Path
 $purpose=@($args|Where-Object {$_ -like '-Dhuankan.process.purpose=*'})
 if($purpose.Count -ne 1){throw '用途标识缺失或重复'}
 $expectedPort="--server.port=$Port"
 if(@($args|Where-Object {$_ -like '--server.port=*'}).Count -ne 1 -or $expectedPort -notin $args){throw '端口参数不匹配'}
 $listeners=@(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
 if($listeners.Count -eq 0 -or @($listeners|Where-Object { $_.OwningProcess -ne $ProcessId -or $_.LocalAddress -ne '127.0.0.1' }).Count -ne 0){throw '监听端口/地址/归属不匹配'}
 return [pscustomobject]@{pid=$ProcessId;createdAt=$p.CreationDate.ToUniversalTime().ToString('o');jar=$jar;jarSha256=(Get-FileHash -LiteralPath $jar).Hash.ToLowerInvariant();java=(Resolve-Path -LiteralPath $p.ExecutablePath).Path;account="$($owner.Domain)\$($owner.User)";port=$Port;purpose=$purpose[0].Substring('-Dhuankan.process.purpose='.Length);parentPid=$p.ParentProcessId}
}
function Assert-Inc02ProcessIdentity {
 param($Expected,$Actual)
 foreach($key in @('pid','createdAt','jar','jarSha256','java','account','port','purpose')){
  if($null -eq $Expected.$key -or $null -eq $Actual.$key){throw "完整身份字段缺失：$key"}
  if($key -eq 'createdAt'){$equal=([datetimeoffset]$Expected.$key).UtcDateTime.Ticks -eq ([datetimeoffset]$Actual.$key).UtcDateTime.Ticks}
  elseif($key -in @('jar','java')){$equal=[IO.Path]::GetFullPath($Expected.$key).Equals([IO.Path]::GetFullPath($Actual.$key),[StringComparison]::OrdinalIgnoreCase)}
  else{$equal=[string]$Expected.$key -ceq [string]$Actual.$key}
  if(-not $equal){throw "进程完整身份不匹配：$key"}
 }
}
Export-ModuleMember -Function Get-Inc02ProcessIdentity,Assert-Inc02ProcessIdentity
