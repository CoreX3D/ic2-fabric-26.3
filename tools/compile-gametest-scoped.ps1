$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJavaHome = $env:IC_JAVA_HOME
if ([string]::IsNullOrWhiteSpace($taskJavaHome)) {
    $taskJavaHome = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-epsilon\windows\java-runtime-epsilon'
}
$taskClasspathFile = Join-Path $taskRoot 'build\compile-classpath.txt'
if (-not (Test-Path -LiteralPath $taskClasspathFile)) {
    throw 'Run tools\gradle-local.ps1 writeCompileClasspath first.'
}
$taskMainClasses = Join-Path $taskRoot 'build\classes\java\main'
if (-not (Test-Path -LiteralPath $taskMainClasses)) {
    throw 'Compile production classes first with tools\gradle-local.ps1 compileJava.'
}
$taskOutput = Join-Path $taskRoot 'build\gametest-scoped'
New-Item -ItemType Directory -Path (Join-Path $taskOutput 'classes') -Force | Out-Null
$taskClasspath = $taskMainClasses + ';' + ([System.IO.File]::ReadAllText($taskClasspathFile).Trim())
$taskArguments = @(
    '--release', '25', '-encoding', 'UTF-8', '-proc:none', '-implicit:none', '-Xmaxerrs', '5000',
    '-classpath', ('"' + $taskClasspath.Replace('\', '/') + '"'),
    '-d', ('"' + (Join-Path $taskOutput 'classes').Replace('\', '/') + '"')
)
$taskArguments += Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\gametest\java') -Recurse -File -Filter '*.java' |
    ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
$taskArgumentFile = Join-Path $taskOutput 'javac.args'
[System.IO.File]::WriteAllLines($taskArgumentFile, $taskArguments, [System.Text.UTF8Encoding]::new($false))
& (Join-Path $taskJavaHome 'bin\javac.exe') ('@' + $taskArgumentFile) *> (Join-Path $taskOutput 'compile.log')
$taskCompileExitCode = $LASTEXITCODE
Get-Content -LiteralPath (Join-Path $taskOutput 'compile.log') -Tail 15
exit $taskCompileExitCode
