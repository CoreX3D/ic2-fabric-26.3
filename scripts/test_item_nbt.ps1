param(
    [string]$JdkHome
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($JdkHome)) { $JdkHome = $env:IC_JAVA_HOME }
if ([string]::IsNullOrWhiteSpace($JdkHome)) { $JdkHome = $env:JAVA_HOME }
if ([string]::IsNullOrWhiteSpace($JdkHome)) {
    $taskJavacCommand = Get-Command javac -CommandType Application -ErrorAction SilentlyContinue
    if ($null -eq $taskJavacCommand) {
        throw 'JDK 25 was not found. Set IC_JAVA_HOME, JAVA_HOME, or add the JDK bin directory to PATH.'
    }
    $JdkHome = Split-Path -Parent (Split-Path -Parent $taskJavacCommand.Source)
}
$taskExecutableSuffix = if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) { '.exe' } else { '' }
$taskJavacExecutable = Join-Path $JdkHome "bin/javac$taskExecutableSuffix"
$taskJavaExecutable = Join-Path $JdkHome "bin/java$taskExecutableSuffix"
if (-not (Test-Path -LiteralPath $taskJavacExecutable) -or -not (Test-Path -LiteralPath $taskJavaExecutable)) {
    throw 'The selected JDK must contain bin/java and bin/javac. Use a JDK 25 installation.'
}
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskClasspath = (Get-Content -LiteralPath (Join-Path $taskRoot 'build\compile-classpath.txt') -Raw).Trim()
$taskOutput = Join-Path $taskRoot 'build\item-nbt-check'
$taskCompileArgs = Join-Path $taskRoot 'build\item-nbt-javac.args'
$taskRunArgs = Join-Path $taskRoot 'build\item-nbt-java.args'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
function Quote-JavaArgument([string]$Value) {
    return '"' + $Value.Replace('\', '/').Replace('"', '\"') + '"'
}
$taskArguments = @(
    '-proc:none', '--release', '25', '-encoding', 'UTF-8', '-d', $taskOutput,
    '-classpath', $taskClasspath,
    (Join-Path $taskRoot 'src\main\java\ic2\core\util\ItemNbt.java'),
    (Join-Path $taskRoot 'tools\validation\ItemNbtRoundTrip.java')
)
$taskArguments | ForEach-Object { Quote-JavaArgument $_ } | Set-Content -LiteralPath $taskCompileArgs -Encoding utf8NoBOM
& $taskJavacExecutable ('@' + $taskCompileArgs)
if ($LASTEXITCODE -ne 0) { throw "ItemNbt harness compilation failed ($LASTEXITCODE)" }
@('-classpath', ($taskOutput + [System.IO.Path]::PathSeparator + $taskClasspath), 'ItemNbtRoundTrip') |
    ForEach-Object { Quote-JavaArgument $_ } | Set-Content -LiteralPath $taskRunArgs -Encoding utf8NoBOM
Push-Location $taskRoot
try {
    & $taskJavaExecutable ('@' + $taskRunArgs)
    if ($LASTEXITCODE -ne 0) { throw "ItemNbt harness failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}
