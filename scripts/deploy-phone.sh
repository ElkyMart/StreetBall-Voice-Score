#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB_BIN:-$ROOT_DIR/.android-sdk/platform-tools/adb}"
APP_ID="com.streetball.voicescore"
MAIN_ACTIVITY="com.streetball.voicescore/.MainActivity"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if ! command -v java >/dev/null 2>&1; then
    echo "java not found. Install JDK 17 and set JAVA_HOME first." >&2
    exit 1
fi

if [[ ! -x "$ADB_BIN" ]]; then
    echo "adb not found at: $ADB_BIN" >&2
    exit 1
fi

if ! "$ADB_BIN" get-state >/dev/null 2>&1; then
    echo "No authorized Android device detected by adb." >&2
    echo "Run: $ADB_BIN devices -l" >&2
    exit 1
fi

cd "$ROOT_DIR"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle-home}" ./gradlew :app:assembleDebug --no-daemon

set +e
install_output="$("$ADB_BIN" install -r -t --fastdeploy "$APK_PATH" 2>&1)"
install_exit=$?
set -e

echo "$install_output"

if [[ $install_exit -ne 0 ]]; then
    if grep -q "INSTALL_FAILED_USER_RESTRICTED" <<<"$install_output"; then
        cat <<'EOF'
Install was blocked by phone policy.
On Xiaomi/HyperOS, enable:
- Developer options -> USB debugging (Security settings)
- Developer options -> Install via USB (or USB installation)
- Developer options -> Verify apps over USB = Off
EOF
    fi
    exit $install_exit
fi

"$ADB_BIN" shell am start -n "$MAIN_ACTIVITY"
echo "Deployed and launched: $APP_ID"
