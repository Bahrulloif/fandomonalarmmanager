package com.tastamat.fandomon.service

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.tastamat.fandomon.BuildConfig
import com.tastamat.fandomon.data.local.FandomonDatabase
import com.tastamat.fandomon.data.model.EventType
import com.tastamat.fandomon.data.model.MonitorEvent
import com.tastamat.fandomon.data.repository.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

/**
 * UpdateManager - handles OTA (Over-The-Air) updates for Fandomon app
 *
 * Features:
 * - Downloads APK from URL (supports GitHub Releases)
 * - Validates version before installation
 * - Triggers installation automatically
 * - Logs update events to database
 *
 * Usage via MQTT command:
 * {
 *   "command": "update_app",
 *   "parameters": {
 *     "apk_url": "https://github.com/user/repo/releases/download/v2.5.0/app-release.apk",
 *     "version": "2.5.0"
 *   }
 * }
 */
class UpdateManager(private val context: Context) {

    private val TAG = "UpdateManager"
    private val eventRepository = EventRepository(FandomonDatabase.getDatabase(context).eventDao())
    private var downloadId: Long = -1

    /**
     * Download and install APK update
     *
     * @param apkUrl Direct download URL for APK file (e.g., GitHub Releases)
     * @param version Target version (e.g., "2.5.0") - optional, for validation
     */
    fun downloadAndInstallUpdate(apkUrl: String, version: String? = null) {
        Log.d(TAG, "📥 Starting OTA update download...")
        Log.d(TAG, "📦 APK URL: $apkUrl")
        version?.let { Log.d(TAG, "🔢 Target version: $it") }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Log update start event
                logUpdateEvent(
                    "UPDATE_STARTED",
                    "OTA update started - downloading from: $apkUrl" + (version?.let { ", target version: $it" } ?: "")
                )

                // Validate current version if target version provided
                if (version != null && !shouldUpdate(version)) {
                    Log.w(TAG, "⚠️ Update skipped - current version ${BuildConfig.VERSION_NAME} is same or newer than $version")
                    logUpdateEvent(
                        "UPDATE_SKIPPED",
                        "Update skipped - already at version ${BuildConfig.VERSION_NAME}"
                    )
                    return@launch
                }

                // Download APK
                startDownload(apkUrl, version)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting update: ${e.message}", e)
                logUpdateEvent(
                    "UPDATE_FAILED",
                    "Update failed to start: ${e.message}"
                )
            }
        }
    }

    /**
     * Check if update should be applied based on version comparison
     */
    private fun shouldUpdate(targetVersion: String): Boolean {
        val current = BuildConfig.VERSION_NAME
        Log.d(TAG, "🔍 Comparing versions: current=$current, target=$targetVersion")

        // Simple version comparison (supports semantic versioning like 2.4.8 vs 2.5.0)
        return try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val targetParts = targetVersion.split(".").map { it.toIntOrNull() ?: 0 }

            // Pad with zeros if needed
            val maxLength = maxOf(currentParts.size, targetParts.size)
            val currentPadded = currentParts + List(maxLength - currentParts.size) { 0 }
            val targetPadded = targetParts + List(maxLength - targetParts.size) { 0 }

            // Compare each part
            for (i in 0 until maxLength) {
                when {
                    targetPadded[i] > currentPadded[i] -> {
                        Log.d(TAG, "✅ Target version is newer - update should proceed")
                        return true
                    }
                    targetPadded[i] < currentPadded[i] -> {
                        Log.d(TAG, "⚠️ Target version is older - update should be skipped")
                        return false
                    }
                }
            }

            Log.d(TAG, "⚠️ Versions are identical - update should be skipped")
            false
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Version comparison failed, allowing update: ${e.message}")
            true // If comparison fails, allow update
        }
    }

    /**
     * Start APK download using Android DownloadManager
     */
    private fun startDownload(apkUrl: String, version: String?) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Create download request
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("Fandomon Update")
                setDescription("Downloading Fandomon ${version ?: "latest"}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "fandomon-update.apk")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            // Enqueue download
            downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "📥 Download started - ID: $downloadId")

            // Register receiver for download completion
            registerDownloadReceiver()

            CoroutineScope(Dispatchers.IO).launch {
                logUpdateEvent(
                    "UPDATE_DOWNLOADING",
                    "APK download in progress - download ID: $downloadId"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Download failed: ${e.message}", e)
            CoroutineScope(Dispatchers.IO).launch {
                logUpdateEvent(
                    "UPDATE_DOWNLOAD_FAILED",
                    "Download failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Register BroadcastReceiver to listen for download completion
     */
    private fun registerDownloadReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Log.d(TAG, "✅ Download completed - ID: $id")

                    CoroutineScope(Dispatchers.IO).launch {
                        logUpdateEvent(
                            "UPDATE_DOWNLOADED",
                            "APK downloaded successfully - starting installation"
                        )
                    }

                    // Trigger installation
                    installUpdate()

                    // Unregister receiver
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        Log.w(TAG, "Receiver already unregistered: ${e.message}")
                    }
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        Log.d(TAG, "📡 Download completion receiver registered")
    }

    /**
     * Install downloaded APK using PackageInstaller for automatic installation
     */
    private fun installUpdate() {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Get file path from download manager
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            if (cursor == null || !cursor.moveToFirst()) {
                Log.e(TAG, "❌ Downloaded file not found in DownloadManager")
                CoroutineScope(Dispatchers.IO).launch {
                    logUpdateEvent(
                        "UPDATE_INSTALL_FAILED",
                        "Downloaded APK file not found"
                    )
                }
                return
            }

            val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val localUri = cursor.getString(columnIndex)
            cursor.close()

            val apkFile = File(Uri.parse(localUri).path ?: "")
            if (!apkFile.exists()) {
                Log.e(TAG, "❌ APK file does not exist: ${apkFile.absolutePath}")
                CoroutineScope(Dispatchers.IO).launch {
                    logUpdateEvent(
                        "UPDATE_INSTALL_FAILED",
                        "APK file not found at path: ${apkFile.absolutePath}"
                    )
                }
                return
            }

            Log.d(TAG, "📦 Installing APK from: ${apkFile.absolutePath}")
            Log.d(TAG, "📦 File size: ${apkFile.length()} bytes")

            // Use PackageInstaller for automatic installation (Android 5.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                installUsingPackageInstaller(apkFile)
            } else {
                // Fallback to standard install for older Android
                installUsingIntent(apkFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Installation failed: ${e.message}", e)
            CoroutineScope(Dispatchers.IO).launch {
                logUpdateEvent(
                    "UPDATE_INSTALL_FAILED",
                    "Installation failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Install APK using PackageInstaller API (automatic, no user prompt)
     * Requires REQUEST_INSTALL_PACKAGES permission
     */
    private fun installUsingPackageInstaller(apkFile: File) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            // Write APK to session
            FileInputStream(apkFile).use { input ->
                session.openWrite("fandomon", 0, -1).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            Log.d(TAG, "✅ APK written to PackageInstaller session")

            // Create pending intent for installation result
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // Commit the session (triggers installation)
            session.commit(pendingIntent.intentSender)
            session.close()

            Log.d(TAG, "✅ PackageInstaller session committed - installation will proceed automatically")

            CoroutineScope(Dispatchers.IO).launch {
                logUpdateEvent(
                    "UPDATE_INSTALL_STARTED",
                    "Automatic APK installation started via PackageInstaller"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ PackageInstaller installation failed: ${e.message}", e)
            Log.d(TAG, "⚠️ Falling back to standard install intent")

            // Fallback to standard install if PackageInstaller fails
            installUsingIntent(apkFile)
        }
    }

    /**
     * Install APK using standard Intent (shows install prompt to user)
     * Used as fallback if PackageInstaller fails
     */
    private fun installUsingIntent(apkFile: File) {
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Log.d(TAG, "✅ Installation intent launched - user will see install prompt")

            CoroutineScope(Dispatchers.IO).launch {
                logUpdateEvent(
                    "UPDATE_INSTALL_STARTED",
                    "APK installation prompt shown to user"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Standard installation also failed: ${e.message}", e)
            CoroutineScope(Dispatchers.IO).launch {
                logUpdateEvent(
                    "UPDATE_INSTALL_FAILED",
                    "All installation methods failed: ${e.message}"
                )
            }
        }
    }

    /**
     * BroadcastReceiver to handle PackageInstaller result
     */
    class InstallResultReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

            val TAG = "UpdateManager"
            Log.d(TAG, "📦 Installation result received - status: $status, message: $message")

            when (status) {
                PackageInstaller.STATUS_SUCCESS -> {
                    Log.d(TAG, "✅ Installation completed successfully!")
                    // App will restart automatically after successful installation
                }
                PackageInstaller.STATUS_FAILURE -> {
                    Log.e(TAG, "❌ Installation failed: $message")
                }
                PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                    Log.e(TAG, "❌ Installation blocked: $message")
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    Log.e(TAG, "❌ Installation aborted: $message")
                }
                PackageInstaller.STATUS_FAILURE_INVALID -> {
                    Log.e(TAG, "❌ Installation invalid: $message")
                }
                PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                    Log.e(TAG, "❌ Installation conflict: $message")
                }
                else -> {
                    Log.e(TAG, "❌ Unknown installation status: $status - $message")
                }
            }
        }
    }

    /**
     * Log update event to database (will be sent via MQTT)
     */
    private suspend fun logUpdateEvent(eventType: String, message: String) {
        try {
            val event = MonitorEvent(
                eventType = EventType.valueOf("COMMAND_$eventType"),
                message = message
            )
            eventRepository.insertEvent(event)
            Log.d(TAG, "📝 Update event logged: $eventType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log update event: ${e.message}", e)
        }
    }
}
