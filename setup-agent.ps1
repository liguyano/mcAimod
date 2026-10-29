# Install CPython dependencies for the Kurumi 0.6.0 LangGraph agent.
# The Mod automatically extracts the Python sources to this directory at game startup.
param(
    [string]$Instance = '',
    [string]$Python = 'py'
)
$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 may decode UTF-8 scripts without BOM as ANSI.
# Construct the non-ASCII directory name from Unicode code points instead.
if ([string]::IsNullOrWhiteSpace($Instance)) {
    $software = [string][char]0x8F6F + [char]0x4EF6
    $Instance = Join-Path (Join-Path 'E:\sync' $software) '.minecraft\versions\26.2-NeoForge_26.2.0.93'
}
# Ask native Python processes to use UTF-8 for console output.
$env:PYTHONIOENCODING = 'utf-8'
try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch { }
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$target = Join-Path $Instance 'config\mcaimod-agent'
New-Item -Path $target -ItemType Directory -Force | Out-Null
$requirements = Join-Path $PSScriptRoot 'python-agent\requirements.txt'
if (-not (Test-Path -LiteralPath $requirements)) {
    $requirements = Join-Path $target 'requirements.txt'
}
if (-not (Test-Path -LiteralPath $requirements)) { throw "Missing requirements.txt; run git pull and retry." }
if ($Python -eq 'py') {
    & py -3 -m venv (Join-Path $target '.venv')
} else {
    & $Python -m venv (Join-Path $target '.venv')
}
if ($LASTEXITCODE -ne 0) { throw 'Python venv creation failed' }
$exe = Join-Path $target '.venv\Scripts\python.exe'
& $exe -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) { throw 'pip upgrade failed' }
& $exe -m pip install -r $requirements
if ($LASTEXITCODE -ne 0) { throw 'LangGraph dependency installation failed' }
Write-Host "Kurumi 0.6.0 Python environment installed: $exe" -ForegroundColor Green
