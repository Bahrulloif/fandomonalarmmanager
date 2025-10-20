# Android 10+ Background Activity Launch Restrictions

## Проблема

Starting with **Android 10 (API 29)**, Google introduced strict restrictions on launching activities from background for security and user experience reasons.

### What Happens

When Fandomon (running in background via AlarmManager/BroadcastReceiver) tries to restart Fandomat using `startActivity()`, the system **blocks** the launch:

```
W ActivityTaskManager: Background activity start [
  callingPackage: com.tastamat.fandomon;
  isCallingUidForeground: false;
  callingUidHasAnyVisibleWindow: false;
  callingUidProcState: RECEIVER;
  isBgStartWhitelisted: false
] ❌ BLOCKED
```

### Why This Happens

- `isCallingUidForeground: false` - Fandomon is not in foreground
- `callingUidProcState: RECEIVER` - Call originates from BroadcastReceiver
- `isBgStartWhitelisted: false` - Not whitelisted for background starts

## Solutions

### ✅ Solution 1: Remote MQTT Commands (RECOMMENDED)

This is the **primary and recommended** approach for production use:

1. Fandomon detects Fandomat is stopped
2. Fandomon sends event `FANDOMAT_STOPPED` to MQTT broker
3. Server/Administrator receives notification
4. Server/Administrator sends MQTT command `RESTART_FANDOMAT`
5. Fandomon receives command and attempts restart

**Advantages:**
- ✅ Fully compliant with Android restrictions
- ✅ Centralized control and monitoring
- ✅ Works reliably on all Android versions
- ✅ Allows manual intervention when needed
- ✅ Audit trail of all restart commands

**Configuration:**
Set `Auto-Restart = OFF` in Fandomon settings to disable local restart attempts.

### ⚠️ Solution 2: Shell Commands (LIMITED)

Attempting to use `Runtime.getRuntime().exec("am start ...")` from within the app:

```kotlin
val command = "am start -n com.tastamat.fandomat/.MainActivity"
Runtime.getRuntime().exec(command)
```

**Limitations:**
- ⚠️ May not work on all devices (depends on SELinux policies)
- ⚠️ Requires specific permissions on some ROMs
- ⚠️ Not guaranteed to work on Android 10+

### ❌ Solution 3: Direct startActivity() (NOT RECOMMENDED)

Using `context.startActivity(intent)` from background:

**Status:** ❌ **BLOCKED** by Android 10+ system restrictions

This method will **NOT** work when:
- App is in background
- Call originates from BroadcastReceiver
- No user interaction recently occurred

## Recommended Configuration

### For Production Devices

```
Auto-Restart Fandomat: OFF ⏸️
```

**Workflow:**
1. Fandomon detects issue → Sends MQTT event
2. Server receives alert → Analyzes issue
3. Server sends MQTT command → Fandomon restarts Fandomat
4. All actions logged for audit

### For Development/Testing Only

```
Auto-Restart Fandomat: ON 🔄
```

**Note:** May attempt shell command restart, but success not guaranteed on all devices.

## Exception Cases Where startActivity() IS Allowed

Android allows background activity launches in these cases:

1. **Recent user interaction** - Within last few seconds
2. **Pending Intents** - From notifications
3. **Whitelisted apps** - System apps, device owner apps
4. **Foreground service** - App has active foreground service

Fandomon uses **AlarmManager** (not ForegroundService) to minimize battery usage, so these exceptions don't apply.

## Implementation Status

Current Fandomon implementation:

1. ✅ Detects when Fandomat is stopped
2. ✅ Logs `FANDOMAT_STOPPED` event
3. ✅ Sends event to MQTT broker
4. ⚠️ Attempts shell command restart (may fail on Android 10+)
5. ✅ Supports remote MQTT `RESTART_FANDOMAT` command

## Testing Results

| Method | Android 9- | Android 10+ | Reliability |
|--------|-----------|-------------|-------------|
| startActivity() | ✅ Works | ❌ Blocked | Low |
| Shell command | ✅ Works | ⚠️ Maybe | Medium |
| MQTT command | ✅ Works | ✅ Works | **High** |

## References

- [Android Background Activity Launch Restrictions](https://developer.android.com/guide/components/activities/background-starts)
- [Restrictions on starting activities from the background](https://developer.android.com/about/versions/10/behavior-changes-10#background-activity-starts)

## Conclusion

**For reliable operation on Android 10+ devices in production:**

1. Set `Auto-Restart = OFF`
2. Use MQTT commands for all restart operations
3. Implement server-side monitoring and automated response
4. Keep audit trail of all restart commands

This provides the most reliable, secure, and manageable solution for monitoring and restarting Fandomat in production environments.
