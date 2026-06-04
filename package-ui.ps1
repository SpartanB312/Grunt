[CmdletBinding()]
param(
    [string]$OutputDir = "build/package/grunteon-ui",
    [switch]$NoClean,
    [switch]$NoZip,
    [switch]$WithTests
)

$ErrorActionPreference = "Stop"

function Get-FullPath([string]$Path) {
    return [System.IO.Path]::GetFullPath($Path)
}

function Invoke-Gradle([string[]]$Tasks) {
    $gradle = Join-Path $Root "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradle)) {
        $gradle = Join-Path $Root "gradlew"
    }
    if (-not (Test-Path -LiteralPath $gradle)) {
        throw "Gradle wrapper was not found in $Root"
    }

    & $gradle --console=plain @Tasks
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
}

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

$tasks = @()
if ($WithTests) {
    $tasks += ":grunt-ui:check"
}
$tasks += ":grunt-ui:packageUberJarForCurrentOS"

Write-Host "Building Grunteon UI package..."
Invoke-Gradle $tasks

$outputPath = if ([System.IO.Path]::IsPathRooted($OutputDir)) {
    Get-FullPath $OutputDir
} else {
    Get-FullPath (Join-Path $Root $OutputDir)
}

if (-not $NoClean -and (Test-Path -LiteralPath $outputPath)) {
    $allowedRoot = Get-FullPath (Join-Path $Root "build/package")
    if (-not $outputPath.StartsWith($allowedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean output outside $allowedRoot. Use -NoClean or choose an output under build/package."
    }
    Remove-Item -LiteralPath $outputPath -Recurse -Force
}

$appDir = Join-Path $outputPath "app"
$pluginsDir = Join-Path $outputPath "plugins"
$logsDir = Join-Path $outputPath "logs"
$workDir = Join-Path $outputPath "work"

New-Item -ItemType Directory -Path $appDir, $pluginsDir, $logsDir, $workDir -Force | Out-Null

$jarDir = Join-Path $Root "grunt-ui/build/compose/jars"
$uiJar = Get-ChildItem -LiteralPath $jarDir -Filter "*.jar" |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1

if ($null -eq $uiJar) {
    throw "UI uber jar was not found in $jarDir"
}

$packagedJar = Join-Path $appDir "grunteon-ui.jar"
Copy-Item -LiteralPath $uiJar.FullName -Destination $packagedJar -Force

$pluginsSrc = Join-Path $Root "plugins"
if (Test-Path -LiteralPath $pluginsSrc) {
    $pluginItems = Get-ChildItem -LiteralPath $pluginsSrc -Force
    if ($pluginItems.Count -gt 0) {
        Copy-Item -Path (Join-Path $pluginsSrc "*") -Destination $pluginsDir -Recurse -Force
    }
}

foreach ($doc in @("README.md", "LICENSE.md")) {
    $docPath = Join-Path $Root $doc
    if (Test-Path -LiteralPath $docPath) {
        Copy-Item -LiteralPath $docPath -Destination (Join-Path $outputPath $doc) -Force
    }
}

$runUiBat = @'
@echo off
setlocal
cd /d "%~dp0"
set "APP_JAR=%~dp0app\grunteon-ui.jar"
start "" javaw -jar "%APP_JAR%" %*
'@
Set-Content -LiteralPath (Join-Path $outputPath "run-ui.bat") -Value $runUiBat -Encoding ASCII

$runConsoleBat = @'
@echo off
setlocal
cd /d "%~dp0"
set "APP_JAR=%~dp0app\grunteon-ui.jar"
java -jar "%APP_JAR%" %*
'@
Set-Content -LiteralPath (Join-Path $outputPath "run-ui-console.bat") -Value $runConsoleBat -Encoding ASCII

$packageReadme = @"
Grunteon UI package
===================

Run:
  run-ui.bat

Console run:
  run-ui-console.bat

Contents:
  app/grunteon-ui.jar  - runnable Compose Desktop UI uber jar
  plugins/             - plugin jars loaded by Grunteon at startup
  logs/ and work/      - writable runtime directories

Build notes:
  package-ui.ps1 uses :grunt-ui:packageUberJarForCurrentOS so the UI,
  grunt-main, grunt-ir, ASM and Compose dependencies are included.
  No JDK/JRE is bundled. The launch scripts use java/javaw from PATH.
"@
Set-Content -LiteralPath (Join-Path $outputPath "PACKAGE_README.txt") -Value $packageReadme -Encoding ASCII

if (-not $NoZip) {
    $zipPath = "$outputPath.zip"
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $outputPath "*") -DestinationPath $zipPath -Force
    Write-Host "Zip written to $zipPath"
}

Write-Host "Package written to $outputPath"
