# Build first: .\gradlew.bat build
# Then run:    .\install-mod.ps1
$ErrorActionPreference = 'Stop'

$jarFolder = Join-Path $PSScriptRoot 'build\libs'
$modsFolder = 'E:\sync\软件\.minecraft\versions\26.2-NeoForge_26.2.0.93\mods'

if (-not (Test-Path -LiteralPath $jarFolder -PathType Container)) {
    throw "找不到编译输出目录：$jarFolder。请先执行 .\gradlew.bat build"
}
if (-not (Test-Path -LiteralPath $modsFolder -PathType Container)) {
    throw "找不到 Minecraft mods 目录：$modsFolder"
}

$properties = Join-Path $PSScriptRoot 'gradle.properties'
$versionLine = Get-Content -LiteralPath $properties |
    Where-Object { $_ -match '^mod_version\s*=' } | Select-Object -First 1
if (-not $versionLine) { throw "gradle.properties 里没有 mod_version" }
$version = ($versionLine -replace '^mod_version\s*=\s*', '').Trim()
if ($version -notmatch '^[0-9A-Za-z._-]+$') { throw "无效的 mod_version：$version" }
$jarName = "mcaimod-$version.jar"
$source = Join-Path $jarFolder $jarName
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
    throw "找不到编译后的 $jarName，请先执行 .\gradlew.bat build"
}
$destination = Join-Path $modsFolder $jarName
Move-Item -LiteralPath $source -Destination $destination -Force -ErrorAction Stop
if (-not (Test-Path -LiteralPath $destination -PathType Leaf)) {
    throw "移动后没有找到目标 JAR：$destination"
}
Get-ChildItem -LiteralPath $modsFolder -Filter 'mcaimod-*.jar' -File |
    Where-Object { $_.Name -ne $jarName } |
    ForEach-Object {
        Write-Host "移除同一 Mod 的旧版本：$($_.FullName)"
        Remove-Item -LiteralPath $_.FullName -Force -ErrorAction Stop
    }
Write-Host "Mod 已移动并覆盖同名文件：$destination" -ForegroundColor Green
