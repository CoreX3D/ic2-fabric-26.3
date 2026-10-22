param()

$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskClasspathFile = Join-Path $taskProjectRoot 'build/compile-classpath.txt'
if (-not (Test-Path -LiteralPath $taskClasspathFile)) {
    throw 'Run tools/gradle-local.ps1 writeCompileClasspath first.'
}
$taskJavaHome = $env:IC_JAVA_HOME
if ([string]::IsNullOrWhiteSpace($taskJavaHome)) {
    $taskJavaHome = Join-Path $env:APPDATA '.minecraft/runtime/java-runtime-epsilon/windows/java-runtime-epsilon'
}
$taskClasspath = (Join-Path $taskProjectRoot 'build/classes/java/main') + ';' + (Get-Content -LiteralPath $taskClasspathFile -Raw).Trim()
$taskOutput = Join-Path $taskProjectRoot 'build/recipe-probe'
New-Item -ItemType Directory -Force $taskOutput | Out-Null
$taskSources = @(
    (Join-Path $PSScriptRoot 'RecipeCompatibilityProbe.java')
)
$taskSources += Get-ChildItem -LiteralPath (Join-Path $taskProjectRoot 'src/main/java/ic2/core/recipe') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& (Join-Path $taskJavaHome 'bin/javac.exe') -encoding UTF-8 -proc:none -classpath $taskClasspath -d $taskOutput @taskSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $taskJavaHome 'bin/java.exe') -classpath "$taskOutput;$taskClasspath" RecipeCompatibilityProbe 2>&1 |
    Tee-Object -FilePath (Join-Path $taskProjectRoot 'build/recipe-probe.log')
exit $LASTEXITCODE
