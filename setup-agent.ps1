# Install CPython dependencies for the Kurumi 0.6.0 LangGraph agent.
# The Mod automatically extracts the Python sources to this directory at game startup.
param(
    [string]$Instance = 'E:\sync\软件\.minecraft\versions\26.2-NeoForge_26.2.0.93',
    [string]$Python = 'py'
)
$ErrorActionPreference = 'Stop'
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
