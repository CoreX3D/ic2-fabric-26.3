param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments
)

$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskJavaHome = $env:IC_JAVA_HOME
if ([string]::IsNullOrWhiteSpace($taskJavaHome)) {
    $taskJavaHome = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-epsilon\windows\java-runtime-epsilon'
}
if (-not (Test-Path -LiteralPath (Join-Path $taskJavaHome 'bin\javac.exe'))) {
    throw 'JDK 25 with javac was not found. Set IC_JAVA_HOME to a JDK 25 directory.'
}

$previousTaskJavaHome = $env:JAVA_HOME
$previousTaskGradleHome = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $taskJavaHome
    $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot 'gradle-user-home'
    $taskGradleExecutable = Join-Path $PSScriptRoot 'gradle-9.6.0\bin\gradle.bat'
    if (-not (Test-Path -LiteralPath $taskGradleExecutable)) {
        $taskGradleExecutable = Join-Path $taskProjectRoot 'gradlew.bat'
    }
    & $taskGradleExecutable --no-daemon --project-dir $taskProjectRoot @GradleArguments
    $taskGradleExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousTaskJavaHome
    $env:GRADLE_USER_HOME = $previousTaskGradleHome
}
exit $taskGradleExitCode
