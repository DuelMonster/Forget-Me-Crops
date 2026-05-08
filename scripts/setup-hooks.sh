#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

git config --local core.hooksPath .githooks
chmod +x .githooks/pre-commit scripts/validate-docs.sh 2>/dev/null || true

echo "Configured repository hooks path: $(git config --local --get core.hooksPath)"
echo "Done. pre-commit will now run docs validation from .githooks/."
