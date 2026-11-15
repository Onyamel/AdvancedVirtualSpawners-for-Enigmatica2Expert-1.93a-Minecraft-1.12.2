param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleHome = $env:GRADLE_HOME,
    [string]$E2E,
    [switch]$Offline,
    [switch]$RuntimeChecks
)
$ErrorActionPreference = 'Stop'
$tools = Join-Path (Split-Path -Parent $PSScriptRoot) '_tools'
if (!$JavaHome) { $JavaHome = Join-Path $tools 'jdk8/jdk8u512-b09' }
if (!$GradleHome) { $GradleHome = Join-Path $tools 'gradle/gradle-4.10.3' }
$java = Join-Path $JavaHome 'bin/java.exe'
$gradle = Join-Path $GradleHome 'bin/gradle.bat'
if (!(Test-Path -LiteralPath $java) -or !(Test-Path -LiteralPath $gradle)) {
    throw 'Укажите пути к JDK 8 и Gradle 4.10.3 через -JavaHome и -GradleHome.'
}
$arguments = @('build', '--console', 'plain')
if ($Offline) { $arguments += '--offline' }
if ($E2E) { $arguments += "-Pe2eDir=$E2E" }
if ($RuntimeChecks) { $arguments += 'reobfRuntimeJar' }
$originalJavaHome = $env:JAVA_HOME
Push-Location -LiteralPath $PSScriptRoot
try {
    $env:JAVA_HOME = $JavaHome
    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle завершился с кодом $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $originalJavaHome
    Pop-Location
}
