$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\load-local-env.ps1"
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10' }
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw '请设置指向 Java 21 的 JAVA_HOME' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
if (-not $env:HUANKAN_DB_PASSWORD -or -not $env:HUANKAN_ADMIN_TOKEN) { throw '请设置 HUANKAN_DB_PASSWORD 和至少32字符的 HUANKAN_ADMIN_TOKEN' }
& mvn.cmd -s "$PSScriptRoot\maven-settings.xml" -f "$projectRoot\backend\pom.xml" spring-boot:run
if ($LASTEXITCODE -ne 0) { throw '后端启动失败，请查看上方错误' }
