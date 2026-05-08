$ErrorActionPreference = 'Stop'

$repoRoot = (git rev-parse --show-toplevel).Trim()
$docsScript = Join-Path $repoRoot 'scripts/validate-docs.ps1'
$optScript = Join-Path $repoRoot 'scripts/validate-optimization-pass.ps1'

if (-not (Test-Path $docsScript)) {
    Write-Host 'Missing docs validator: scripts/validate-docs.ps1' -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $optScript)) {
    Write-Host 'Missing optimization validator: scripts/validate-optimization-pass.ps1' -ForegroundColor Red
    exit 1
}

& $docsScript
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $optScript
exit $LASTEXITCODE
