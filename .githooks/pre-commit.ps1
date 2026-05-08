$ErrorActionPreference = 'Stop'

$repoRoot = (git rev-parse --show-toplevel).Trim()
$script = Join-Path $repoRoot 'scripts/validate-docs.ps1'

if (-not (Test-Path $script)) {
    Write-Host 'Missing docs validator: scripts/validate-docs.ps1' -ForegroundColor Red
    exit 1
}

& $script
exit $LASTEXITCODE
