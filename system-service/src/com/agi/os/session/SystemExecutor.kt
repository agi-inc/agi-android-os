/*
 * SystemExecutor
 *
 * Executes system-level operations that require elevated privileges:
 * - App installation/uninstallation
 * - Permission management
 * - System settings modification
 * - Shell command execution
 * - Process management
 */
package com.agi.os.session

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Slog
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class SystemExecutor(private val context: Context) {

    companion object {
        private const val TAG = "SystemExecutor"
        private const val SHELL_TIMEOUT_SEC = 30L
    }

    private val packageManager: PackageManager = context.packageManager
    private val packageInstaller: PackageInstaller = packageManager.packageInstaller
    private val activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // ========== Package Management ==========

    /**
     * Get list of installed package names.
     */
    fun getInstalledPackages(): List<String> {
        return packageManager.getInstalledApplications(0).map { it.packageName }
    }

    /**
     * Install an APK file.
     * @return true if install was initiated successfully
     */
    fun installApk(apkPath: String): Boolean {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            throw IllegalArgumentException("APK file not found: $apkPath")
        }

        Slog.i(TAG, "Installing APK: $apkPath")

        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        params.setAppPackageName(null) // Let installer determine package name

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                session.fsync(out)
            }

            // Commit with a broadcast receiver intent
            // Note: In a real implementation, you'd want to wait for the result
            val intent = Intent("com.agi.os.INSTALL_RESULT")
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, sessionId, intent,
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            session.commit(pendingIntent.intentSender)
            Slog.i(TAG, "Install committed for session $sessionId")

            return true
        } catch (e: Exception) {
            Slog.e(TAG, "Install failed", e)
            session.abandon()
            throw e
        }
    }

    /**
     * Uninstall an app by package name.
     */
    fun uninstallApp(packageName: String): Boolean {
        Slog.i(TAG, "Uninstalling: $packageName")

        val intent = Intent("com.agi.os.UNINSTALL_RESULT")
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 0, intent,
            android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        packageInstaller.uninstall(packageName, pendingIntent.intentSender)
        return true
    }

    // ========== Permission Management ==========

    /**
     * Grant a runtime permission to an app.
     */
    fun grantPermission(packageName: String, permission: String) {
        Slog.i(TAG, "Granting permission: $packageName -> $permission")

        // Use hidden API - requires GRANT_RUNTIME_PERMISSIONS permission
        try {
            val method = PackageManager::class.java.getMethod(
                "grantRuntimePermission",
                String::class.java,
                String::class.java,
                UserHandle::class.java
            )
            method.invoke(packageManager, packageName, permission, Process.myUserHandle())
        } catch (e: Exception) {
            // Fallback to shell command
            executeShell("pm grant $packageName $permission")
        }
    }

    /**
     * Revoke a runtime permission from an app.
     */
    fun revokePermission(packageName: String, permission: String) {
        Slog.i(TAG, "Revoking permission: $packageName -> $permission")

        try {
            val method = PackageManager::class.java.getMethod(
                "revokeRuntimePermission",
                String::class.java,
                String::class.java,
                UserHandle::class.java
            )
            method.invoke(packageManager, packageName, permission, Process.myUserHandle())
        } catch (e: Exception) {
            executeShell("pm revoke $packageName $permission")
        }
    }

    // ========== System Settings ==========

    /**
     * Set a system setting.
     * @param namespace "system", "secure", or "global"
     */
    fun setSystemSetting(namespace: String, key: String, value: String) {
        Slog.d(TAG, "Setting $namespace/$key = $value")

        when (namespace.lowercase()) {
            "system" -> Settings.System.putString(context.contentResolver, key, value)
            "secure" -> Settings.Secure.putString(context.contentResolver, key, value)
            "global" -> Settings.Global.putString(context.contentResolver, key, value)
            else -> throw IllegalArgumentException("Unknown settings namespace: $namespace")
        }
    }

    /**
     * Get a system setting value.
     */
    fun getSystemSetting(namespace: String, key: String): String? {
        return when (namespace.lowercase()) {
            "system" -> Settings.System.getString(context.contentResolver, key)
            "secure" -> Settings.Secure.getString(context.contentResolver, key)
            "global" -> Settings.Global.getString(context.contentResolver, key)
            else -> throw IllegalArgumentException("Unknown settings namespace: $namespace")
        }
    }

    // ========== Shell Execution ==========

    /**
     * Execute a shell command with system privileges.
     * @return Combined stdout and stderr output
     */
    fun executeShell(command: String): String {
        Slog.d(TAG, "Executing shell: $command")

        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

        val stdout = BufferedReader(InputStreamReader(process.inputStream))
        val stderr = BufferedReader(InputStreamReader(process.errorStream))

        val completed = process.waitFor(SHELL_TIMEOUT_SEC, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw RuntimeException("Shell command timed out: $command")
        }

        val output = stdout.readText()
        val error = stderr.readText()

        return if (error.isNotEmpty() && process.exitValue() != 0) {
            "ERROR (exit ${process.exitValue()}): $error\n$output"
        } else {
            output + error
        }
    }

    // ========== Process Management ==========

    /**
     * Force stop an app.
     */
    fun forceStopApp(packageName: String) {
        Slog.i(TAG, "Force stopping: $packageName")

        // ActivityManager.forceStopPackage requires FORCE_STOP_PACKAGES permission
        try {
            val method = ActivityManager::class.java.getMethod(
                "forceStopPackage",
                String::class.java
            )
            method.invoke(activityManager, packageName)
        } catch (e: Exception) {
            // Fallback to shell
            executeShell("am force-stop $packageName")
        }
    }

    /**
     * Get the foreground app for a specific display.
     */
    fun getCurrentForegroundApp(displayId: Int): String {
        // Get running tasks and find the one on our display
        try {
            // Use hidden API to get tasks with display info
            val tasks = activityManager.getRunningTasks(10)

            // For now, return the top task (display filtering requires more work)
            return tasks.firstOrNull()?.topActivity?.packageName ?: ""
        } catch (e: Exception) {
            // Fallback to dumpsys parsing
            val output = executeShell("dumpsys activity activities | grep mResumedActivity")
            // Parse the output to extract package name
            val match = Regex("\\{[^}]*\\s([\\w.]+)/").find(output)
            return match?.groupValues?.getOrNull(1) ?: ""
        }
    }

    // ========== Notifications ==========

    /**
     * Read active notifications.
     * Requires NOTIFICATION_LISTENER permission or system access.
     */
    fun getActiveNotifications(): List<NotificationInfo> {
        // This would require a NotificationListenerService or system access
        // For now, use dumpsys as fallback
        val output = executeShell("dumpsys notification --noredact")
        // Parse notifications from output (implementation depends on format)
        return emptyList() // TODO: Implement parsing
    }

    data class NotificationInfo(
        val packageName: String,
        val title: String?,
        val text: String?,
        val postTime: Long
    )
}
