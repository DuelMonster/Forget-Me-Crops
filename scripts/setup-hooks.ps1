$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

git config --local core.hooksPath .githooks

$configured = (git config --local --get core.hooksPath).Trim()
Write-Host "Configured repository hooks path: $configured" -ForegroundColor Green
Write-Host 'Done. pre-commit will now run docs validation from .githooks/.' -ForegroundColor Green
