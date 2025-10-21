# Redmi Note 8 Pro Setup Guide

## 🚨 CRITICAL: MIUI Background App Restrictions

Redmi Note 8 Pro runs MIUI which aggressively blocks background apps. Follow these steps **EXACTLY**:

## Step 1: Enable Autostart (MOST IMPORTANT!)

### Method 1: Security App
1. Open **Security** app (Безопасность)
2. Tap **Autostart** (Автозапуск)
3. Find **Fandomon** in the list
4. **Enable the toggle** ✅

### Method 2: Settings App
1. **Settings** → **Apps** → **Manage apps**
2. Find **Fandomon** → **Other permissions**
3. **Autostart** → **Allow**

## Step 2: Disable Battery Optimization

1. **Settings** → **Battery & Performance**
2. **Battery Saver** → **App battery saver**
3. Find **Fandomon** → Set to **No restrictions**

## Step 3: Grant Usage Stats Permission

1. **Settings** → **Apps** → **Special access** → **Usage access**
2. Find **Fandomon** → **Allow**

## Step 4: Allow Background Activity

1. **Settings** → **Apps** → **Manage apps** → **Fandomon**
2. **Battery usage** → **Background activity** → **Allow**

## Step 5: Enable Notifications

1. **Settings** → **Apps** → **Fandomon** → **Notifications**
2. **Allow notifications** ✅

## Step 6: Test the Setup

1. **Restart your device**
2. Open **Fandomon** app
3. Enable **Monitoring** in settings
4. Check if monitoring is active

## 🔧 Troubleshooting

### If app still doesn't run:

1. **Check MIUI version:**
   - Go to Settings → About phone → MIUI version
   - Note the version (e.g., MIUI 12.5.1)

2. **Try these additional steps:**
   - **Settings** → **Privacy** → **Special app access** → **Device admin apps**
   - **Settings** → **Apps** → **Default apps** → **Assist & voice input**

3. **Enable Developer Options:**
   - **Settings** → **About phone** → Tap **MIUI version** 7 times
   - **Settings** → **Additional settings** → **Developer options**
   - Enable **USB debugging**

## 📱 MIUI-Specific Settings

### For MIUI 12+:
- Security app → Autostart → Enable Fandomon
- Settings → Battery → App battery saver → No restrictions

### For MIUI 11:
- Security app → Permissions → Autostart → Enable Fandomon
- Settings → Battery → Battery optimization → Don't optimize

## ✅ Verification Steps

After setup, verify these work:

1. **Open Fandomon** → Should start normally
2. **Enable monitoring** → Should show "Monitoring Active"
3. **Restart device** → Fandomon should auto-start
4. **Check logs** → Should see monitoring activity

## 🆘 If Still Not Working

1. **Check device logs:**
   ```bash
   adb logcat | grep -i fandomon
   ```

2. **Try manual start:**
   - Open Fandomon manually after each restart
   - Enable monitoring each time

3. **Contact support** with:
   - MIUI version
   - Android version
   - Logcat output

## 📋 Quick Checklist

- [ ] Autostart enabled in Security app
- [ ] Battery optimization disabled
- [ ] Usage Stats permission granted
- [ ] Background activity allowed
- [ ] Notifications enabled
- [ ] Device restarted
- [ ] Monitoring enabled in app
- [ ] Test after restart

## 🎯 Expected Behavior

After proper setup:
1. **App starts automatically** after device restart
2. **Monitoring begins** without manual intervention
3. **Background monitoring** continues even when app is closed
4. **MQTT events** are sent when Fandomat stops/freezes
5. **Automatic restart** of Fandomat when needed

---

**Note:** MIUI is very aggressive with background restrictions. These settings are essential for Fandomon to work properly on Redmi Note 8 Pro.
