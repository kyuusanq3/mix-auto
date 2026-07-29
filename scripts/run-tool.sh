#!/usr/bin/env bash
# MixAuto tool runner — invoke tools/*.py without python -c quoting issues.
# Usage (repo root or any cwd):
#   ./scripts/run-tool.sh                    # list recipes
#   ./scripts/run-tool.sh check_driving_text_pitch
set -euo pipefail

repoRoot="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repoRoot"

host="$(uname -s | tr '[:upper:]' '[:lower:]')"
echo "host=${host}"

resolve_python() {
  for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1; then
      echo "$candidate"
      return 0
    fi
  done
  echo "No Python found (tried python3, python)" >&2
  return 1
}

list_recipes() {
  find "$repoRoot/tools" -maxdepth 1 -name '*.py' -type f \
    | sed 's|.*/||; s/\.py$//' \
    | grep -E '^(check_|fix_)' \
    | sort -u
}

pythonExe="$(resolve_python)"
echo "python=${pythonExe}"

if [[ $# -eq 0 ]]; then
  echo "recipes:"
  list_recipes | sed 's/^/  /'
  exit 0
fi

toolName="$1"
if [[ "$toolName" == *"/"* || "$toolName" == *"\\"* || "$toolName" == *".."* ]]; then
  echo "Invalid tool name (no path separators): $toolName" >&2
  exit 1
fi

scriptPath="$repoRoot/tools/${toolName}.py"
if [[ ! -f "$scriptPath" ]]; then
  echo "Unknown tool: $toolName (expected $scriptPath)" >&2
  exit 1
fi

echo "tool=${toolName}"
shift
exec "$pythonExe" "$scriptPath" "$@"
