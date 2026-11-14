#!/usr/bin/env bash
# Rebuilds this personal fork of Yuno (notify-only updater, signed with its own key).
#
#   ./rebuild.sh
#
# Toolchain expected next to the project (../toolchain): JDK 21, Android SDK, and a writable
# Gradle home. Override any of them with environment variables.
set -euo pipefail
cd "$(dirname "$0")"

TOOLCHAIN="${YUNO_TOOLCHAIN:-../toolchain}"
export JAVA_HOME="${JAVA_HOME:-$TOOLCHAIN/jdk21}"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLCHAIN/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-../gradle-home}"
export ANDROID_PREFS_ROOT="${ANDROID_PREFS_ROOT:-$TOOLCHAIN/home}"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "No encuentro el JDK en $JAVA_HOME (ajusta YUNO_TOOLCHAIN o JAVA_HOME)." >&2
  exit 1
fi
if [ ! -f keystore.properties ]; then
  echo "Falta keystore.properties (y el .jks): sin la clave no se puede firmar." >&2
  exit 1
fi

./gradlew assembleRelease --console=plain "$@"

APK=app/build/outputs/apk/release/app-release.apk
echo
echo "APK: $APK"
ls -lh "$APK"
sha256sum "$APK"
