#!/bin/sh
DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
if [ -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Install Android Studio or run: gradle wrapper"
echo "Then: ./gradlew :app:assembleDebug"
exit 1
