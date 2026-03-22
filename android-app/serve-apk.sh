#!/bin/bash
# Serves the debug APK over HTTP so you can download it on your Android device.
# Usage: ./serve-apk.sh [port]
# Then open the URL on your Android device's browser to download the APK.

PORT=${1:-8080}
APK_DIR="app/build/outputs/apk/debug"
APK_FILE="app-debug.apk"

if [ ! -f "$APK_DIR/$APK_FILE" ]; then
  echo "APK not found. Run './gradlew assembleDebug' first."
  exit 1
fi

# Get local IP address
LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "localhost")

echo ""
echo "=== APK Download Server ==="
echo ""
echo "Open this URL on your Android device:"
echo ""
echo "  http://${LOCAL_IP}:${PORT}/${APK_FILE}"
echo ""
echo "Make sure your phone is on the same Wi-Fi network."
echo "Press Ctrl+C to stop the server."
echo ""

cd "$APK_DIR" && python3 -m http.server "$PORT"
