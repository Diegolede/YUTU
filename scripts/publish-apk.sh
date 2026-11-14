#!/usr/bin/env bash
# Builds the release APK and publishes it as the newest GitHub Release, so the link
#   https://github.com/Diegolede/YUTU/releases/latest/download/YUTU.apk
# always serves the latest build. The release is tagged v<versionName>; publishing the same version
# again replaces that release's APK and notes.
#
# Build it locally: the release is signed with the key in keystore.properties (see README). CI never
# builds release APKs, and without that key `assembleRelease` produces an unsigned APK.
#
# Release notes: the CHANGELOG.md section for this version becomes the release's notes, which the
# app's "What's new" sheet shows. Put changes under "## Unreleased"; publishing renames that section
# to the version (commit CHANGELOG.md afterwards).
#
#   scripts/publish-apk.sh            # build, then publish
#   scripts/publish-apk.sh --no-build # publish the APK already built
set -euo pipefail
cd "$(dirname "$0")/.."

version="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)"
tag="v$version"

section() { awk -v h="$1" '/^## /{ if (on) exit; on = (substr($0, 4) == h); next } on' CHANGELOG.md; }
from="$version"
notes="$(section "$version")"
if ! grep -q '^[-*] ' <<<"$notes"; then
  from="Unreleased"
  notes="$(section Unreleased)"
  grep -q '^[-*] ' <<<"$notes" || { echo "CHANGELOG.md has no notes for $version: add them under '## Unreleased'" >&2; exit 1; }
fi

[[ "${1:-}" == "--no-build" ]] || {
  export JAVA_HOME="${JAVA_HOME:-../toolchain/jdk21}"
  export ANDROID_HOME="${ANDROID_HOME:-../toolchain/sdk}"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-../gradle-home}"
  ./gradlew assembleRelease --console=plain
}

apk="$(mktemp -d)/YUTU.apk"
cp app/build/outputs/apk/release/app-release.apk "$apk"
body="$(dirname "$apk")/notes.md"
printf '%s\n\n---\n\nInstall YUTU.apk on Android (allow installs from your browser or file manager).\n' \
  "$(sed -e '/./,$!d' <<<"$notes")" >"$body"

if gh release view "$tag" >/dev/null 2>&1; then
  gh release upload "$tag" "$apk" --clobber
  gh release edit "$tag" --latest --notes-file "$body"
else
  gh release create "$tag" "$apk" --title "YUTU $version" --latest --notes-file "$body"
fi

if [[ "$from" == "Unreleased" ]]; then
  V="$version" perl -0pi -e 's/^## Unreleased\n/## Unreleased\n\n## $ENV{V}\n/m' CHANGELOG.md
  echo "CHANGELOG.md: Unreleased is now $version (commit it)"
fi

echo "Published $tag: https://github.com/Diegolede/YUTU/releases/latest/download/YUTU.apk"
