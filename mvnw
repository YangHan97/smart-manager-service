#!/bin/sh
# Maven Wrapper script for Unix-like systems
# Downloads and runs Maven if not already installed

set -e

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")" && pwd)}"
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ -f "$WRAPPER_PROPERTIES" ]; then
    DISTRIBUTION_URL=$(grep "distributionUrl" "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

DISTRIBUTION_URL="${DISTRIBUTION_URL:-https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip}"
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.6"

if [ ! -d "$MAVEN_HOME" ]; then
    echo "Downloading Maven from $DISTRIBUTION_URL"
    mkdir -p "$MAVEN_HOME"
    if command -v curl > /dev/null 2>&1; then
        curl -fsSL "$DISTRIBUTION_URL" -o "$MAVEN_HOME/maven.zip"
    elif command -v wget > /dev/null 2>&1; then
        wget -q "$DISTRIBUTION_URL" -O "$MAVEN_HOME/maven.zip"
    else
        echo "Error: curl or wget is required to download Maven"
        exit 1
    fi
    unzip -qo "$MAVEN_HOME/maven.zip" -d "$MAVEN_HOME"
    rm -f "$MAVEN_HOME/maven.zip"
fi

MAVEN_BIN=$(find "$MAVEN_HOME" -name "mvn" -path "*/bin/mvn" | head -1)
if [ -z "$MAVEN_BIN" ]; then
    echo "Error: Could not find Maven binary"
    exit 1
fi

exec "$MAVEN_BIN" "$@"
