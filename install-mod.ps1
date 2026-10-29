# Kurumi Mod installer: build + verify GraalPy JarJar + install safely.
# Usage:
#   .\install-mod.ps1                 # Build locally and install
#   .\install-mod.ps1 -Pull           # Fast-forward GitHub main, then build/install
#   .\install-mod.ps1 -SkipBuild      # Install only an existing, locally verified JAR
param(
    [switch]$Pull,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$modsFolder = 'E:\sync\软件\.minecraft\versions\26.2-NeoForge_26.2.0.93\mods'
$jarFolder = Join-Path $PSScriptRoot 'build\libs'
$gradle = Join-Path $PSScriptRoot 'gradlew.bat'
$properties = Join-Path $PSScriptRoot 'gradle.properties'

if (-not (Test-Path -LiteralPath $modsFolder -PathType Container)) {
    throw "Minecraft mods directory not found: $modsFolder"
}
if (-not (Test-Path -LiteralPath $properties -PathType Leaf)) {
    throw "Missing gradle.properties: $properties"
}

if ($Pull) {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw "Git is not installed or not available on PATH."
    }
    $changes = @(git status --porcelain)
    if ($LASTEXITCODE -ne 0) { throw "Unable to inspect Git working tree." }
    if ($changes.Count -gt 0) {
        throw "Local Git changes found. Commit or stash them before using -Pull. No files were modified."
    }
    Write-Host "Updating repository from GitHub (fast-forward only)..."
    & git pull --ff-only origin main
    if ($LASTEXITCODE -ne 0) { throw "Git pull failed; installation cancelled." }
}

$versionLine = Get-Content -LiteralPath $properties |
    Where-Object { $_ -match '^mod_version\s*=' } | Select-Object -First 1
if (-not $versionLine) { throw "mod_version is missing from gradle.properties." }
$version = ($versionLine -replace '^mod_version\s*=\s*', '').Trim()
if ($version -notmatch '^[0-9A-Za-z._-]+$') { throw "Invalid mod_version: $version" }
$jarName = "mcaimod-$version.jar"
$source = Join-Path $jarFolder $jarName

if (-not $SkipBuild) {
    if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
        throw "Gradle wrapper not found: $gradle"
    }
    Write-Host "Building Mod and verifying embedded GraalPy dependencies..."
    & $gradle build verifyGraalPyJarJar --no-configuration-cache --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build / GraalPy packaging verification failed. Existing Mod was not changed."
    }
} else {
    Write-Warning "Skipping Gradle build: checking the existing JAR only."
}

if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
    throw "Build output not found: $source"
}
# Independent, lightweight check so -SkipBuild cannot accidentally install
# the old JAR that compiled but lacked the GraalPy language libraries.
Add-Type -AssemblyName System.IO.Compression
$zip = [System.IO.Compression.ZipFile]::OpenRead($source)
try {
    $entries = @($zip.Entries | ForEach-Object { $_.FullName })
    if ($entries -notcontains 'META-INF/jarjar/metadata.json') {
        throw "GraalPy JarJar metadata missing from $source"
    }
    foreach ($prefix in @('polyglot-', 'python-language-', 'python-resources-', 'truffle-api-')) {
        if (-not @($entries | Where-Object {
            $_.StartsWith('META-INF/jarjar/') -and $_.Contains($prefix) -and $_.EndsWith('.jar')
        }).Count) {
            throw "GraalPy dependency '$prefix' missing from $source"
        }
    }
} finally {
    $zip.Dispose()
}

$destination = Join-Path $modsFolder $jarName
$existingMods = @(Get-ChildItem -LiteralPath $modsFolder -Filter 'mcaimod-*.jar' -File)
$backupDir = Join-Path $modsFolder '.mcaimod-backup'
$backedUp = @()
if ($existingMods.Count -gt 0) {
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    foreach ($old in $existingMods) {
        $backupName = "$($old.BaseName)-$(Get-Date -Format 'yyyyMMdd-HHmmss-fff').jar"
        $backupPath = Join-Path $backupDir $backupName
        Copy-Item -LiteralPath $old.FullName -Destination $backupPath -ErrorAction Stop
        $backedUp += $backupPath
    }
}

try {
    Copy-Item -LiteralPath $source -Destination $destination -Force -ErrorAction Stop
    if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne
        (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash) {
        throw "Installed JAR checksum mismatch."
    }
    foreach ($old in $existingMods) {
        if ($old.FullName -ne $destination) {
            Remove-Item -LiteralPath $old.FullName -Force -ErrorAction Stop
        }
    }
} catch {
    Write-Warning "Installation failed; attempting to restore backed-up Mod versions."
    Remove-Item -LiteralPath $destination -Force -ErrorAction SilentlyContinue
    foreach ($backup in $backedUp) {
        $originalName = ([System.IO.Path]::GetFileName($backup) -replace '-\d{8}-\d{9}(?=\.jar$)', '')
        Copy-Item -LiteralPath $backup -Destination (Join-Path $modsFolder $originalName) -Force
    }
    throw
}
Write-Host "Installed $jarName to $modsFolder" -ForegroundColor Green
if ($backedUp.Count) { Write-Host "Previous JAR backup(s) in $backupDir" }
Write-Host "Note: JarJar checks passed; launch Minecraft to verify GraalPy runtime loads."
