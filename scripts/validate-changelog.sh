#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
changelog_path="$repo_root/CHANGELOG.md"

if [[ ! -f "$changelog_path" ]]; then
  echo "CHANGELOG.md is missing." >&2
  exit 1
fi

staged="$(git -C "$repo_root" diff --cached --name-only --diff-filter=ACMR)"

staged_code_changes="$(printf '%s\n' "$staged" | grep -E '^(src/main/.+\.(java|kt|kts|groovy|scala|json|toml|yml|yaml|properties|mcmeta|mixins\.json)|src/test/.+\.(java|kt|kts|groovy|scala|json|toml|yml|yaml|properties))$' || true)"

if [[ -n "$staged_code_changes" ]] && ! printf '%s\n' "$staged" | grep -Fxq 'CHANGELOG.md'; then
  echo "CHANGELOG enforcement failed: staged code changes detected, but CHANGELOG.md is not staged." >&2
  echo "When code changes are committed, include an appropriate CHANGELOG.md update in the same commit." >&2
  exit 1
fi

# Check that CHANGELOG has at least one version header (e.g., ## 0.18.0)
if ! grep -E "^## [0-9]+\.[0-9]+\.[0-9]+" "$changelog_path" > /dev/null 2>&1; then
  echo "CHANGELOG.md missing version headers (format: ## X.Y.Z)" >&2
  exit 1
fi

# Check that there is at least one bullet entry anywhere in the file
if ! grep -E "^-" "$changelog_path" > /dev/null 2>&1; then
  echo "CHANGELOG.md has version headers but no entries (format: - description)" >&2
  exit 1
fi

echo "CHANGELOG validation passed."
exit 0
