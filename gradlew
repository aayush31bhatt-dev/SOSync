#!/bin/sh

##############################################################################
##
##  Gradle start up script for POSIX generated manually for this project.
##
##############################################################################

APP_BASE_NAME=${0##*/}
APP_HOME=$(cd "${0%/*}" && pwd -P)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

