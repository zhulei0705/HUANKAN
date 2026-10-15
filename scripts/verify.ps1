$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\load-local-env.ps1"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& mvn.cmd -s "$PSScriptRoot\maven-settings.xml" -f "$projectRoot\backend\pom.xml" test
if ($LASTEXITCODE -ne 0) { throw '后端测试失败' }
Push-Location "$projectRoot\frontend"
try {
 & npm.cmd ci --no-audit --no-fund
 if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
 & npm.cmd test
 if ($LASTEXITCODE -ne 0) { throw '前端测试失败' }
 & npm.cmd run build
 if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
 & npx.cmd playwright test
 if ($LASTEXITCODE -ne 0) { throw '浏览器测试失败；首次运行需 npx.cmd playwright install chromium' }
} finally { Pop-Location }
