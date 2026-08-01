#!/bin/sh
# Gradle wrapper script — auto-downloads the correct Gradle version
# Generated for ScreenStream project

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
JAVA_OPTS="${JAVA_OPTS:-}"

exec java $JAVA_OPTS \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
