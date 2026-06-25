#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.
# Maven Wrapper - downloads Apache Maven if not already cached.
set -e

SCRIPT="$0"
# Follow symlinks
while [ -h "$SCRIPT" ]; do
    ls=$(ls -ld "$SCRIPT")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        SCRIPT="$link"
    else
        SCRIPT="$(dirname "$SCRIPT")/$link"
    fi
done
PROJECT_BASEDIR=$(cd "$(dirname "$SCRIPT")" && pwd)

PROPERTIES_FILE="$PROJECT_BASEDIR/.mvn/wrapper/maven-wrapper.properties"
if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "ERROR: $PROPERTIES_FILE not found" >&2
    exit 1
fi

# Read distribution URL
DIST_URL=$(grep "^distributionUrl=" "$PROPERTIES_FILE" | cut -d'=' -f2- | tr -d '\r\n')
if [ -z "$DIST_URL" ]; then
    echo "ERROR: distributionUrl not found in $PROPERTIES_FILE" >&2
    exit 1
fi

# Determine Maven home path from URL
DIST_NAME=$(basename "$DIST_URL")
DIST_BASE="${DIST_NAME%.zip}"

MVN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"
WRAPPER_DISTS="$MVN_USER_HOME/wrapper/dists"
MVN_HOME="$WRAPPER_DISTS/$DIST_BASE"

if [ ! -f "$MVN_HOME/bin/mvn" ]; then
    echo "Downloading $DIST_NAME to $MVN_HOME ..."
    mkdir -p "$WRAPPER_DISTS"
    TMP_ZIP="$WRAPPER_DISTS/$DIST_NAME"
    TMP_EXTRACT="$WRAPPER_DISTS/.extract_$$"

    # Download
    if command -v curl > /dev/null 2>&1; then
        curl --fail --silent --show-error --location --output "$TMP_ZIP" "$DIST_URL"
    elif command -v wget > /dev/null 2>&1; then
        wget -q -O "$TMP_ZIP" "$DIST_URL"
    else
        echo "ERROR: Neither curl nor wget is available" >&2
        exit 1
    fi

    # Extract — prefer unzip, fall back to jar (always available with JDK)
    mkdir -p "$TMP_EXTRACT"
    if command -v unzip > /dev/null 2>&1; then
        unzip -q "$TMP_ZIP" -d "$TMP_EXTRACT"
    else
        (cd "$TMP_EXTRACT" && jar xf "$TMP_ZIP")
    fi

    # The zip contains a top-level directory like apache-maven-3.9.10
    EXTRACTED_DIR=$(ls "$TMP_EXTRACT" | head -1)
    mv "$TMP_EXTRACT/$EXTRACTED_DIR" "$MVN_HOME"

    # Cleanup
    rm -rf "$TMP_ZIP" "$TMP_EXTRACT"
    echo "Maven installed at $MVN_HOME"
fi

exec "$MVN_HOME/bin/mvn" "$@"
