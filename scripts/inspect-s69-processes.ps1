param([string]$Output)
$ErrorActionPreference='Stop'
# 从进程PEB读取实际工作目录，避免用端口或启动脚本猜测当前路径。
Add-Type -TypeDefinition @'
using System; using System.Runtime.InteropServices;
public static class S69ProcessPath {
 [DllImport("kernel32.dll")] static extern IntPtr OpenProcess(uint access,bool inherit,int pid);
 [DllImport("kernel32.dll")] static extern bool ReadProcessMemory(IntPtr h,IntPtr p,byte[] b,int n,out IntPtr read);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);
 [DllImport("ntdll.dll")] static extern int NtQueryInformationProcess(IntPtr h,int kind,byte[] b,int n,out int returned);
 static byte[] Read(IntPtr h,long p,int n){byte[] b=new byte[n];IntPtr read;if(!ReadProcessMemory(h,new IntPtr(p),b,n,out read)||read.ToInt64()!=n)throw new Exception("进程内存读取失败");return b;}
 public static string Cwd(int pid){var h=OpenProcess(0x410,false,pid);if(h==IntPtr.Zero)throw new Exception("进程只读访问失败");try{byte[] info=new byte[48];int n;if(NtQueryInformationProcess(h,0,info,48,out n)!=0)throw new Exception("进程信息读取失败");long peb=BitConverter.ToInt64(info,8);long parameters=BitConverter.ToInt64(Read(h,peb+0x20,8),0);byte[] directory=Read(h,parameters+0x38,16);int size=BitConverter.ToUInt16(directory,0);long pointer=BitConverter.ToInt64(directory,8);return System.Text.Encoding.Unicode.GetString(Read(h,pointer,size));}finally{CloseHandle(h);}}
}
'@
$ports=Get-NetTCPConnection -State Listen | Where-Object {$_.LocalPort -in @(5173,5176,5180,5181,5182,5184,18080,18087,18088)} | Select-Object LocalAddress,LocalPort,OwningProcess
$ids=@($ports.OwningProcess)+@(43988,36400)
$processes=Get-CimInstance Win32_Process | Where-Object {$_.ProcessId -in $ids} | ForEach-Object {
 $directory=$null;$pathError=$null
 try{$directory=[S69ProcessPath]::Cwd($_.ProcessId)}catch{$pathError=$_.Exception.Message}
 [pscustomobject]@{processId=$_.ProcessId;parentProcessId=$_.ParentProcessId;createdAt=$_.CreationDate.ToUniversalTime().ToString('o');executable=$_.ExecutablePath;command=$_.CommandLine;workingDirectory=$directory;pathError=$pathError}
}
$record=@{auditedAt=(Get-Date).ToUniversalTime().ToString('o');ports=@($ports);processes=@($processes)}|ConvertTo-Json -Depth 5
if($Output){if(Test-Path -LiteralPath $Output){throw '拒绝覆盖已有进程审计'};$record|Set-Content -LiteralPath $Output -Encoding utf8}
$record
