#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Optional ENV vars
#   JAVA_HOME - location of a JDK home dir
#   MAVEN_OPTS - parameters passed to the Java VM when running Maven
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != true ] || set -x

# OS specific support.
native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

# set JAVA_CMD
if [ -n "${JAVA_HOME-}" ]; then
  if [ -x "$JAVA_HOME/jre/sh/java" ]; then
    JAVA_CMD="$JAVA_HOME/jre/sh/java"
  else
    JAVA_CMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVA_CMD" ]; then
      die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME
Please set the JAVA_HOME variable in your environment to match the location of your Java installation."
    fi
  fi
else
  JAVA_CMD="$( command -v java )" || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
Please set the JAVA_HOME variable in your environment to match the location of your Java installation."
fi

# hash://
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd -- "$(dirname -- "$0")" && pwd -P)}"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

# Download maven-wrapper.jar if needed
if [ ! -e "$MAVEN_WRAPPER_JAR" ]; then
  MAVEN_WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
  if command -v wget > /dev/null; then
    [ "${MVNW_VERBOSE-}" != true ] || echo "Found wget ... using wget"
    wget -nv -O "$MAVEN_WRAPPER_JAR" "$MAVEN_WRAPPER_URL" || rm -f "$MAVEN_WRAPPER_JAR"
  elif command -v curl > /dev/null; then
    [ "${MVNW_VERBOSE-}" != true ] || echo "Found curl ... using curl"
    curl -sfLo "$MAVEN_WRAPPER_JAR" "$MAVEN_WRAPPER_URL" || rm -f "$MAVEN_WRAPPER_JAR"
  else
    echo "Falling back to using Java to download"
    javaSource="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/MavenWrapperDownloader.java"
    javaClass="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/MavenWrapperDownloader.class"
    if [ -e "$javaSource" ]; then
      if [ ! -e "$javaClass" ]; then
        echo " - Compiling MavenWrapperDownloader.java ..."
        ("$JAVA_CMD" -cp . -d "$MAVEN_PROJECTBASEDIR/.mvn/wrapper" "$javaSource")
      fi
      echo " - Running MavenWrapperDownloader.java ..."
      ("$JAVA_CMD" -cp "$MAVEN_PROJECTBASEDIR/.mvn/wrapper" MavenWrapperDownloader "$MAVEN_WRAPPER_URL" "$MAVEN_WRAPPER_JAR") || rm -f "$MAVEN_WRAPPER_JAR"
    fi
  fi
  [ -e "$MAVEN_WRAPPER_JAR" ] || {
    echo "ERROR: could not download maven-wrapper.jar" >&2
    exit 1
  }
fi

# read MAVEN_CONFIG if present
if [ -f "$MAVEN_PROJECTBASEDIR/.mvn/maven.config" ]; then
  MAVEN_CONFIG="$(tr -s '\r\n' ' ' < "$MAVEN_PROJECTBASEDIR/.mvn/maven.config")"
else
  MAVEN_CONFIG=""
fi

exec "$JAVA_CMD" \
  ${MAVEN_OPTS-} \
  ${MAVEN_DEBUG_OPTS-} \
  -classpath "$(native_path "$MAVEN_WRAPPER_JAR")" \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  org.apache.maven.wrapper.MavenWrapperMain \
  ${MAVEN_CONFIG} \
  "$@"
