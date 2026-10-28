#!/usr/bin/env bash
# Refuse credential-shaped strings and sensitive local files.
#
# Usage:
#   scripts/check_secrets.sh --staged   # files in the Git index (pre-commit)
#   scripts/check_secrets.sh --all      # files in HEAD (repo audit)
#
# The scan prints only file:line and a generic label, never the matched value.

set -u

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "check_secrets: not inside a Git repository" >&2
  exit 2
}
cd "$REPO_ROOT" || exit 2

mode="${1:---staged}"
case "$mode" in
  --staged)
    list_cmd=(git diff --cached --name-only -z --diff-filter=ACMR)
    show_prefix=":"
    ;;
  --all)
    list_cmd=(git ls-tree -r --name-only -z HEAD)
    show_prefix="HEAD:"
    ;;
  *)
    echo "Usage: $0 [--staged|--all]" >&2
    exit 2
    ;;
esac

# Deliberately specific formats. A generic "password=" rule produces too much noise.
patterns=(
  'sk_(live|test)_[A-Za-z0-9]{10,}'
  'whsec_[A-Za-z0-9]{10,}'
  'sk-ant-[A-Za-z0-9_-]{20,}'
  'sk-proj-[A-Za-z0-9_-]{20,}'
  'sk-[A-Za-z0-9]{20,}'
  'AKIA[0-9A-Z]{16}'
  'ASIA[0-9A-Z]{16}'
  'gh[pousr]_[A-Za-z0-9]{20,}'
  'github_pat_[A-Za-z0-9_]{20,}'
  'glpat-[A-Za-z0-9_-]{20,}'
  'xox[baprs]-[A-Za-z0-9-]{10,}'
  'AIza[0-9A-Za-z_-]{35}'
  'hf_[A-Za-z0-9]{20,}'
  '-----BEGIN [A-Z ]{0,20}PRIVATE KEY-----'
  '_authToken[=]'
)
combined="$(IFS='|'; printf '%s' "${patterns[*]}")"

shopt -s nocasematch

is_sensitive_path() {
  local base="${1##*/}"
  case "$base" in
    .env.example) return 1 ;;
    .env|.env.*|.envrc|.netrc|id_rsa|id_ed25519|local.properties)
      return 0 ;;
    google-services.json|provider-keys.json)
      return 0 ;;
    *.pem|*.key|*.p8|*.p12|*.pfx|*.jks|*.keystore|*.mobileprovision)
      return 0 ;;
  esac
  return 1
}

found=0
checked=0

while IFS= read -r -d '' file; do
  [ -n "$file" ] || continue

  # The checker contains its own patterns; scanning it would self-match.
  [ "$file" = "scripts/check_secrets.sh" ] && continue

  if is_sensitive_path "$file"; then
    echo "Refusing sensitive file: $file" >&2
    found=1
    continue
  fi

  checked=$((checked + 1))
  matches="$(
    git show "${show_prefix}${file}" 2>/dev/null |
      grep -n -I -E -e "$combined" 2>/dev/null |
      cut -d: -f1 |
      head -5
  )" || true

  if [ -n "$matches" ]; then
    while IFS= read -r line; do
      [ -n "$line" ] || continue
      echo "Potential credential-shaped string: $file:$line" >&2
    done <<< "$matches"
    found=1
  fi
done < <("${list_cmd[@]}")

if [ "$found" -ne 0 ]; then
  echo "" >&2
  echo "Secret scan failed. Remove the credential before committing." >&2
  echo "If it was already pushed, rotate it: a Git history rewrite does not make it private." >&2
  exit 1
fi

echo "Secret scan passed ($checked file(s) checked, mode $mode)."
