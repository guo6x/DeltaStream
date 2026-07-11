[CmdletBinding()]
param(
    [switch]$Online,
    [switch]$RunLintVital
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\nonRoot\performance\app-nonRoot-performance.apk'
$androidStudioJbr = 'C:\Program Files\Android\Android Studio\jbr'

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    if (-not (Test-Path (Join-Path $androidStudioJbr 'bin\java.exe'))) {
        throw 'JAVA_HOME is invalid and Android Studio JBR was not found.'
    }

    $env:JAVA_HOME = $androidStudioJbr
}

$drive = $null
foreach ($letter in @('M', 'N', 'P', 'Q', 'R')) {
    $candidate = "${letter}:"
    if (-not (Test-Path "$candidate\")) {
        $drive = $candidate
        break
    }
}

if (-not $drive) {
    throw 'No free temporary drive letter was found for the ASCII build path.'
}

$gradleArgs = @(
    ':app:assembleNonRootPerformance',
    '--no-daemon',
    '--console=plain'
)

if (-not $Online) {
    $gradleArgs += '--offline'
}

if (-not $RunLintVital) {
    # The current Gradle cache does not contain AGP lint-vital artifacts.
    # Compile, native release optimization, R8, packaging, and signing still run.
    $gradleArgs += @(
        '-x', 'lintVitalAnalyzeNonRootPerformance',
        '-x', 'lintVitalReportNonRootPerformance',
        '-x', 'lintVitalNonRootPerformance'
    )
}

$mapped = $false
$buildExitCode = 1

try {
    & subst.exe $drive $projectRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to map $projectRoot to $drive."
    }

    $mapped = $true
    Push-Location "$drive\"
    try {
        & .\gradlew.bat @gradleArgs
        $buildExitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}
finally {
    if ($mapped) {
        & subst.exe $drive /d | Out-Null
    }
}

if ($buildExitCode -ne 0) {
    throw "Performance build failed with exit code $buildExitCode."
}

if (-not (Test-Path $apkPath)) {
    throw "Gradle completed but the APK was not found at $apkPath."
}

$apk = Get-Item $apkPath
Write-Host "Performance APK: $($apk.FullName)"
Write-Host "Size: $($apk.Length) bytes"
