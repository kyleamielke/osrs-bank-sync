#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: scripts/release.sh <new-version>" >&2
  exit 1
fi

new_version="$1"
if [[ ! -f "build.gradle" ]]; then
  echo "Error: run from repository root (build.gradle not found)." >&2
  exit 1
fi

build_file="build.gradle"
capture_file="src/main/java/io/kyil/osrsbanksync/BankCaptureService.java"
submitter_file="src/main/java/io/kyil/osrsbanksync/BankSubmitter.java"
changelog_file="CHANGELOG.md"

current_version="$(sed -n "s/^version = '\(.*\)'$/\1/p" "$build_file")"
if [[ -z "$current_version" ]]; then
  echo "Error: could not detect current version from $build_file" >&2
  exit 1
fi

echo "Current version: $current_version"
echo "New version: $new_version"

sed -i "s/^version = '$current_version'$/version = '$new_version'/" "$build_file"
sed -i "s/static final String PLUGIN_VERSION = \"$current_version\";/static final String PLUGIN_VERSION = \"$new_version\";/" "$capture_file"
sed -i "s/osrs-bank-sync\/$current_version /osrs-bank-sync\/$new_version /" "$submitter_file"

release_date="$(date +%Y-%m-%d)"
release_heading="## [$new_version] - $release_date"
if grep -Fq "$release_heading" "$changelog_file"; then
  echo "Changelog already contains release heading: $release_heading"
else
  awk -v heading="$release_heading" '
    !inserted && $0 == "## [Unreleased]" {
      print
      print ""
      print heading
      print ""
      inserted=1
      next
    }
    { print }
  ' "$changelog_file" > "$changelog_file.tmp"
  mv "$changelog_file.tmp" "$changelog_file"
fi

echo
echo "Verification:"
grep -n "^version = " "$build_file"
grep -n "PLUGIN_VERSION" "$capture_file"
grep -n "USER_AGENT" "$submitter_file"
grep -n "^## \\[Unreleased\\]$" "$changelog_file"
grep -n "^## \\[$new_version\\] - $release_date$" "$changelog_file"

echo
echo "Manual post-merge release steps:"
echo "  git checkout osrs-bank-sync"
echo "  git pull --ff-only origin osrs-bank-sync"
echo "  git tag v$new_version"
echo "  git push origin v$new_version"
echo "  git remote get-url mirror >/dev/null 2>&1 || git remote add mirror git@github.com:kyleamielke/osrs-bank-sync.git"
echo "  git push --force mirror HEAD:main"
echo "  git push mirror v$new_version"
echo "  RELEASE_SHA=\$(git rev-parse HEAD)"
