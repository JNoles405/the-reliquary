#!/usr/bin/env bash
# Build the Windows installers for the current AppInfo.VERSION, tag the commit,
# and publish a GitHub release with the .msi/.exe attached.
#
# Usage:
#   scripts/release.sh [--notes <file.md>] [--prerelease] [--dry-run]
#
# Notes:
#   - Version is read from AppInfo.VERSION (the single source of truth). Bump it
#     before running. The tag is vAA.BB.CC.
#   - Auth reuses the credential `git` already has for github.com (never printed).
#   - Requires a JDK with jpackage: set JAVA_HOME to one, or keep a bundled JDK
#     under .jdk/ (e.g. .jdk/jdk-21.0.11+10).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

NOTES_FILE=""
PRERELEASE=false
DRY_RUN=false
while [ $# -gt 0 ]; do
  case "$1" in
    --notes) NOTES_FILE="$2"; shift 2 ;;
    --prerelease) PRERELEASE=true; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

# --- Version (single source of truth) ---------------------------------------
APPINFO="composeApp/src/commonMain/kotlin/com/reliquary/app/util/AppInfo.kt"
VERSION="$(sed -n 's/.*VERSION[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$APPINFO" | head -1)"
if ! printf '%s' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "AppInfo.VERSION is not AA.BB.CC (got '$VERSION')." >&2; exit 1
fi
TAG="v$VERSION"
echo "Releasing $TAG"

# --- Remote / repo slug ------------------------------------------------------
REMOTE_URL="$(git remote get-url origin)"
SLUG="$(printf '%s' "$REMOTE_URL" | sed -E 's#.*github.com[:/]([^/]+/[^/]+?)(\.git)?$#\1#')"
echo "Repo: $SLUG"

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "Tag $TAG already exists. Bump AppInfo.VERSION first." >&2; exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
  echo "Working tree not clean — commit or stash first." >&2; exit 1
fi

# --- Locate a jpackage-capable JDK ------------------------------------------
find_jdk() {
  if [ -n "${JAVA_HOME:-}" ] && { [ -x "$JAVA_HOME/bin/jpackage" ] || [ -x "$JAVA_HOME/bin/jpackage.exe" ]; }; then
    printf '%s' "$JAVA_HOME"; return 0
  fi
  for d in "$ROOT"/.jdk/*/; do
    if [ -x "${d}bin/jpackage" ] || [ -x "${d}bin/jpackage.exe" ]; then printf '%s' "${d%/}"; return 0; fi
  done
  return 1
}
PKG_JDK="$(find_jdk || true)"
if [ -z "$PKG_JDK" ]; then
  echo "No JDK with jpackage found. Set JAVA_HOME or add one under .jdk/." >&2; exit 1
fi
echo "Packaging JDK: $PKG_JDK"

# --- Release notes -----------------------------------------------------------
TMP_NOTES="$(mktemp)"
trap 'rm -f "$TMP_NOTES"' EXIT
if [ -n "$NOTES_FILE" ]; then
  cat "$NOTES_FILE" > "$TMP_NOTES"
else
  PREV_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
  echo "**The Reliquary $VERSION**" > "$TMP_NOTES"; echo >> "$TMP_NOTES"
  if [ -n "$PREV_TAG" ]; then
    echo "Changes since $PREV_TAG:" >> "$TMP_NOTES"; echo >> "$TMP_NOTES"
    git log --pretty='- %s' "$PREV_TAG"..HEAD >> "$TMP_NOTES"
  else
    git log --pretty='- %s' -n 20 >> "$TMP_NOTES"
  fi
  echo >> "$TMP_NOTES"
  echo "### Install (Windows)" >> "$TMP_NOTES"
  echo "Download the \`.msi\` (recommended) or \`.exe\` below. Updates in place; your library lives in \`%APPDATA%\\TheReliquary\`." >> "$TMP_NOTES"
fi

if $DRY_RUN; then
  echo "--- DRY RUN: would build installers, tag $TAG, and publish with notes: ---"
  cat "$TMP_NOTES"
  exit 0
fi

# --- Build installers --------------------------------------------------------
echo "Building installers…"
JAVA_HOME="$PKG_JDK" ./gradlew :composeApp:packageDistributionForCurrentOS --console=plain
MSI="$(ls composeApp/build/compose/binaries/main/msi/*.msi | head -1)"
EXE="$(ls composeApp/build/compose/binaries/main/exe/*.exe | head -1)"
echo "  msi: $MSI"
echo "  exe: $EXE"

# --- Tag ---------------------------------------------------------------------
git tag -a "$TAG" -m "The Reliquary $VERSION"
git push origin "$TAG"

# --- Auth (reuse git's stored github.com credential; never printed) ----------
TOKEN="$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null | sed -n 's/^password=//p')"
if [ -z "$TOKEN" ]; then echo "Could not obtain a github.com credential from git." >&2; exit 1; fi

# --- Create release ----------------------------------------------------------
PAYLOAD="$(mktemp)"; RESP="$(mktemp)"
trap 'rm -f "$TMP_NOTES" "$PAYLOAD" "$RESP"' EXIT
node -e 'const fs=require("fs");fs.writeFileSync(process.argv[3],JSON.stringify({tag_name:process.argv[1],name:"The Reliquary "+process.argv[1].slice(1),body:fs.readFileSync(process.argv[2],"utf8"),draft:false,prerelease:process.argv[4]==="true"}));' \
  "$TAG" "$TMP_NOTES" "$PAYLOAD" "$PRERELEASE"
CODE="$(curl -s -o "$RESP" -w '%{http_code}' -X POST \
  -H "Authorization: token $TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$SLUG/releases" --data @"$PAYLOAD")"
if [ "$CODE" != "201" ]; then echo "Create release failed (HTTP $CODE):"; cat "$RESP"; exit 1; fi
UPLOAD_BASE="$(node -e 'console.log(require(process.argv[1]).upload_url.split("{")[0])' "$RESP")"
HTML_URL="$(node -e 'console.log(require(process.argv[1]).html_url)' "$RESP")"

# --- Upload assets -----------------------------------------------------------
upload() {
  local file="$1" name="$2"
  echo "Uploading $name…"
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    -H "Authorization: token $TOKEN" -H "Content-Type: application/octet-stream" \
    "$UPLOAD_BASE?name=$name" --data-binary @"$file")"
  [ "$code" = "201" ] || { echo "  upload failed (HTTP $code)" >&2; return 1; }
}
upload "$MSI" "The.Reliquary-$VERSION.msi"
upload "$EXE" "The.Reliquary-$VERSION.exe"

echo "Published: $HTML_URL"
