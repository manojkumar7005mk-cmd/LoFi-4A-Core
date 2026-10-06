#!/bin/sh
# Gradle wrapper start-up script for POSIX systems
APP_HOME=$(cd "$(dirname "$0")" && pwd -P) || exit
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ ! -f "$JAR" ]; then
    echo "gradle-wrapper.jar not found; bootstrapping with system gradle..."
    if command -v gradle >/dev/null 2>&1; then
        gradle wrapper --gradle-version 8.9 --distribution-type bin
    else
        echo "ERROR: gradle-wrapper.jar is missing and no system 'gradle' is available." >&2
        echo "Install Gradle or run: gradle wrapper --gradle-version 8.9" >&2
        exit 1
    fi
fi

exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS         -classpath "$JAR"         org.gradle.wrapper.GradleWrapperMain "$@"
