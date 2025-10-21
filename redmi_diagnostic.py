#!/usr/bin/env python3
"""
Redmi Note 8 Pro Diagnostic Tool
Checks common issues that prevent Fandomon from running on MIUI devices
"""

import subprocess
import sys
import re

def run_adb_command(command):
    """Run ADB command and return output"""
    try:
        result = subprocess.run(command, shell=True, capture_output=True, text=True)
        return result.stdout.strip()
    except Exception as e:
        return f"Error: {e}"

def check_device_connection():
    """Check if device is connected via ADB"""
    print("🔍 Checking ADB connection...")
    devices = run_adb_command("adb devices")
    
    if "device" in devices:
        print("✅ Device connected via ADB")
        return True
    else:
        print("❌ No device connected via ADB")
        print("Please enable USB Debugging and connect your Redmi Note 8 Pro")
        return False

def check_app_installation():
    """Check if Fandomon is installed"""
    print("\n🔍 Checking Fandomon installation...")
    packages = run_adb_command("adb shell pm list packages | grep fandomon")
    
    if "com.tastamat.fandomon" in packages:
        print("✅ Fandomon is installed")
        return True
    else:
        print("❌ Fandomon is not installed")
        return False

def check_permissions():
    """Check critical permissions"""
    print("\n🔍 Checking permissions...")
    
    # Check Usage Stats permission
    usage_stats = run_adb_command("adb shell dumpsys package com.tastamat.fandomon | grep -i usage")
    if "granted=true" in usage_stats.lower():
        print("✅ Usage Stats permission granted")
    else:
        print("❌ Usage Stats permission NOT granted")
        print("   → Go to Settings → Apps → Special access → Usage access → Enable Fandomon")
    
    # Check notification permission
    notifications = run_adb_command("adb shell dumpsys notification | grep -i fandomon")
    if notifications:
        print("✅ Notification permission appears to be working")
    else:
        print("⚠️  Notification permission status unclear")

def check_battery_optimization():
    """Check battery optimization status"""
    print("\n🔍 Checking battery optimization...")
    
    battery_whitelist = run_adb_command("adb shell dumpsys deviceidle whitelist | grep fandomon")
    if "com.tastamat.fandomon" in battery_whitelist:
        print("✅ App is whitelisted from battery optimization")
    else:
        print("❌ App is NOT whitelisted from battery optimization")
        print("   → Go to Settings → Battery & Performance → Battery Saver → App battery saver")
        print("   → Find Fandomon → Set to 'No restrictions'")

def check_autostart():
    """Check autostart settings (MIUI specific)"""
    print("\n🔍 Checking MIUI autostart settings...")
    
    # This is harder to check via ADB, so we provide manual instructions
    print("⚠️  Manual check required for MIUI autostart:")
    print("   1. Open Security app")
    print("   2. Go to Autostart (Автозапуск)")
    print("   3. Find Fandomon and ENABLE it")
    print("   4. Alternative: Settings → Apps → Manage apps → Fandomon → Other permissions → Autostart")

def check_boot_receiver():
    """Check if BootReceiver is working"""
    print("\n🔍 Checking BootReceiver...")
    
    # Check if the app can receive boot events
    boot_events = run_adb_command("adb shell dumpsys package com.tastamat.fandomon | grep -i boot")
    if "BOOT_COMPLETED" in boot_events:
        print("✅ BootReceiver is registered")
    else:
        print("⚠️  BootReceiver status unclear")

def check_running_processes():
    """Check if Fandomon is currently running"""
    print("\n🔍 Checking running processes...")
    
    processes = run_adb_command("adb shell ps | grep fandomon")
    if processes:
        print("✅ Fandomon processes are running:")
        print(processes)
    else:
        print("❌ No Fandomon processes found")
        print("   → Try opening the app manually first")

def check_logs():
    """Check recent logs for errors"""
    print("\n🔍 Checking recent logs...")
    
    logs = run_adb_command("adb logcat -d | grep -i fandomon | tail -20")
    if logs:
        print("Recent Fandomon logs:")
        print(logs)
    else:
        print("No recent Fandomon logs found")

def main():
    print("🔧 Redmi Note 8 Pro Diagnostic Tool")
    print("=" * 50)
    
    if not check_device_connection():
        return
    
    check_app_installation()
    check_permissions()
    check_battery_optimization()
    check_autostart()
    check_boot_receiver()
    check_running_processes()
    check_logs()
    
    print("\n" + "=" * 50)
    print("📋 SUMMARY:")
    print("If Fandomon is not running, follow these steps:")
    print("1. Enable Autostart in Security app")
    print("2. Disable battery optimization")
    print("3. Grant Usage Stats permission")
    print("4. Allow background activity")
    print("5. Restart your device and test")

if __name__ == "__main__":
    main()
