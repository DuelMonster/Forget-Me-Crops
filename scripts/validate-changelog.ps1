$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$changelogPath = Join-Path $repoRoot 'CHANGELOG.md'

if (-not (Test-Path $changelogPath)) {
    Write-Host 'CHANGELOG.md is missing.' -ForegroundColor Red
    exit 1
}

Set-Location $repoRoot
$staged = @(git diff --cached --name-only --diff-filter=ACMR)

$stagedCodeChanges = @(
    $staged | Where-Object {
        $_ -match '^src/main/.+\.(java|kt|kts|groovy|scala|json|toml|yml|yaml|properties|mcmeta|mixins\.json)$' -or
        $_ -match '^src/test/.+\.(java|kt|kts|groovy|scala|json|toml|yml|yaml|properties)$'
    }
)

if ($stagedCodeChanges.Count -gt 0 -and -not ($staged -contains 'CHANGELOG.md')) {
    Write-Host 'CHANGELOG enforcement failed: staged code changes detected, but CHANGELOG.md is not staged.' -ForegroundColor Red
    Write-Host 'When code changes are committed, include an appropriate CHANGELOG.md update in the same commit.' -ForegroundColor Red
    exit 1
}

$changelog = Get-Content -Raw -Path $changelogPath

# Check that CHANGELOG has at least one version header (e.g., ## 0.18.0)
if ($changelog -notmatch '## \d+\.\d+\.\d+') {
    Write-Host 'CHANGELOG.md missing version headers (format: ## X.Y.Z)' -ForegroundColor Yellow
    exit 1
}

# Check that there is at least one bullet entry anywhere in the file
if ($changelog -notmatch '(?m)^-\s+') {
    Write-Host 'CHANGELOG.md has version headers but no entries (format: - description)' -ForegroundColor Yellow
    exit 1
}

Write-Host 'CHANGELOG validation passed.' -ForegroundColor Green
exit 0
