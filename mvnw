#!/bin/sh
# Maven Wrapper script - downloads Maven if not present
# See https://maven.apache.org/wrapper/

if [ -z "$MAVEN_HOME" ]; then
  MAVEN_CMD="mvn"
else
  MAVEN_CMD="$MAVEN_HOME/bin/mvn"
fi

exec $MAVEN_CMD "$@"
