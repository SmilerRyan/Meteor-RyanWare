#!/usr/bin/env sh
set -e

APP_NAME=$(basename "$0")
DIRNAME=$(dirname "$0")
SCRIPT="${DIRNAME}/gradle/wrapper/gradle-wrapper.jar"
CLASSPATH="${SCRIPT}"

defaultJvmOpts=""

# support --no-daemon by forwarding as an extra argument if needed
get_java_major() {
  version="$1"
  if echo "$version" | grep -q '^1\.'; then
    echo "$version" | awk -F. '{print $2}'
  else
    echo "$version" | awk -F. '{print $1}'
  fi
}

java_ok() {
  version="$1"
  major=$(get_java_major "$version")
  if [ "${major:-0}" -ge 17 ] 2>/dev/null; then
    return 0
  fi
  return 1
}

find_java() {
  candidates=""

  if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    candidates="$JAVA_HOME/bin/java"
  fi

  sdkman_root="/usr/local/sdkman/candidates/java"
  if [ -d "$sdkman_root" ]; then
    candidates="$candidates $sdkman_root/current/bin/java"
    for candidate in $(ls -1 "$sdkman_root" 2>/dev/null | sort); do
      candidates="$candidates $sdkman_root/$candidate/bin/java"
    done
  fi

  if [ -x "/usr/lib/jvm/java-21-openjdk/bin/java" ]; then
    candidates="$candidates /usr/lib/jvm/java-21-openjdk/bin/java"
  fi

  for candidate in $candidates; do
    if [ -x "$candidate" ]; then
      version=$("$candidate" -version 2>&1 | awk -F '"' '/version/ {print $2}')
      if java_ok "$version"; then
        echo "$candidate"
        return 0
      fi
    fi
  done

  if command -v java >/dev/null 2>&1; then
    echo "$(command -v java)"
    return 0
  fi

  return 1
}

JAVA_CMD=$(find_java)
if [ -z "$JAVA_CMD" ]; then
  echo "Java executable not found" 1>&2
  exit 1
fi

if [ ! -f "$SCRIPT" ]; then
  echo "Gradle wrapper JAR not found: $SCRIPT" 1>&2
  exit 1
fi

exec "$JAVA_CMD" $defaultJvmOpts -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
