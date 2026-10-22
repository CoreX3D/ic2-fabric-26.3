param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments
)

$ErrorActionPreference = 'Stop'
# Opens the real Minecraft client in build/client-render-smoke. The test rejects any other game directory.
# Evidence: build/client-render-smoke/render-validation.txt and screenshots/ic2-*-render.png.
& (Join-Path $PSScriptRoot 'gradle-local.ps1') '-PenableGameTests=true' '-PenableClientGameTests=true' 'runClientGameTest' @GradleArguments
exit $LASTEXITCODE
