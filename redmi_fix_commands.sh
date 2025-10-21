#!/bin/bash
# Redmi Note 8 Pro Fix Commands
# Run these commands to fix Fandomon not running on MIUI

echo "🔧 Redmi Note 8 Pro Fix Script"
echo "================================"

# Check device connection
echo "📱 Checking device connection..."
adb devices

# Check if app is installed
echo "📦 Checking app installation..."
adb shell pm list packages | grep fandomon

# Force stop and restart the app
echo "🔄 Restarting Fandomon..."
adb shell am force-stop com.tastamat.fandomon
sleep 2
adb shell am start -n com.tastamat.fandomon/.MainActivity

# Check if app is running
echo "🔍 Checking if app is running..."
adb shell ps | grep fandomon

# Check recent logs
echo "📋 Recent logs:"
adb logcat -d | grep -i fandomon | tail -5

echo ""
echo "📋 MANUAL STEPS REQUIRED ON YOUR REDMI NOTE 8 PRO:"
echo "=================================================="
echo "1. Open Security app"
echo "2. Go to Autostart (Автозапуск)"
echo "3. Find Fandomon and ENABLE it"
echo "4. Go to Settings → Battery & Performance"
echo "5. Battery Saver → App battery saver"
echo "6. Find Fandomon → Set to 'No restrictions'"
echo "7. Go to Settings → Apps → Special access → Usage access"
echo "8. Find Fandomon → Allow"
echo ""
echo "After these steps, restart your device and test!"
