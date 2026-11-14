#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "$JAVA_HOME" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD=java
fi
# The wrapper itself needs a writable Gradle home before Gradle starts, and this launcher is
# minimal: it does not read GRADLE_OPTS/JAVA_OPTS, so pass them explicitly. Set GRADLE_USER_HOME
# to keep the caches out of $HOME (personal fork: the build box may not expose ~/.gradle).
HOME_OPTS=""
if [ -n "$GRADLE_USER_HOME" ]; then
  mkdir -p "$GRADLE_USER_HOME" || true
  HOME_OPTS="-Duser.home=$GRADLE_USER_HOME -Dgradle.user.home=$GRADLE_USER_HOME"
fi
exec "$JAVA_CMD" $JAVA_OPTS $HOME_OPTS -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
