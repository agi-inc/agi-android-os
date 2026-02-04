package com.agi.os.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import com.agi.os.IAgentService
import com.agi.os.IAgentSession
import com.agi.os.SessionConfig
import com.agi.os.service.SessionBinder
import com.agi.os.session.SessionManager

/**
 * Host service for the AGI Agent automation system.
 *
 * This foreground service:
 * 1. Starts on boot via BootReceiver
 * 2. Creates and manages the SessionManager instance
 * 3. Implements IAgentService and registers with ServiceManager for IPC access
 * 4. Runs persistently in the background for headless automation
 *
 * Client apps can connect via:
 * - ServiceManager.getService("agent") to get IAgentService binder
 * - Or use the agi-os-sdk library for a higher-level API
 */
class AgentHostService : Service() {

    companion object {
        private const val TAG = "AGI.HostService"
        private const val SERVICE_NAME = "agent"
        private const val NOTIFICATION_CHANNEL_ID = "agi_agent_service"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var sessionManager: SessionManager
    private val serviceBinder = AgentServiceImpl()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AgentHostService onCreate")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        initializeAgentService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "AgentHostService onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return serviceBinder
    }

    override fun onDestroy() {
        Log.i(TAG, "AgentHostService onDestroy")
        sessionManager.shutdown()
        super.onDestroy()
    }

    private fun initializeAgentService() {
        try {
            Log.i(TAG, "Initializing SessionManager...")

            // Create the session manager
            sessionManager = SessionManager(this)

            // Register with ServiceManager so other apps can find it
            try {
                ServiceManager.addService(SERVICE_NAME, serviceBinder)
                Log.i(TAG, "AgentService registered as '$SERVICE_NAME' in ServiceManager")
            } catch (e: SecurityException) {
                // May not have permission to add to ServiceManager - that's OK,
                // clients can still bind directly
                Log.w(TAG, "Could not register with ServiceManager (may need SELinux policy): ${e.message}")
            }

            // Signal boot completed to enable full functionality
            sessionManager.onBootCompleted()

            Log.i(TAG, "AgentHostService fully initialized")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AgentHostService", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "AGI Agent Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background automation service for headless sessions"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AGI Agent")
            .setContentText("Headless automation ready")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    /**
     * Implementation of IAgentService AIDL interface.
     */
    private inner class AgentServiceImpl : IAgentService.Stub() {

        override fun createSession(config: SessionConfig): IAgentSession {
            enforceCallingPermission()

            val callingUid = Binder.getCallingUid()
            Log.d(TAG, "createSession called by uid=$callingUid, config=$config")

            val session = sessionManager.createSession(config)
            return SessionBinder(session)
        }

        override fun destroySession(sessionId: String) {
            enforceCallingPermission()
            Log.d(TAG, "destroySession: $sessionId")
            sessionManager.destroySession(sessionId)
        }

        override fun listSessions(): List<String> {
            enforceCallingPermission()
            return sessionManager.listSessionIds()
        }

        override fun getSession(sessionId: String): IAgentSession? {
            enforceCallingPermission()
            val session = sessionManager.getSession(sessionId) ?: return null
            return SessionBinder(session)
        }

        override fun controlPrimaryDisplay(): IAgentSession {
            enforceCallingPermission()
            Log.d(TAG, "controlPrimaryDisplay")
            val session = sessionManager.controlPrimaryDisplay()
            return SessionBinder(session)
        }

        override fun releasePrimaryDisplay() {
            enforceCallingPermission()
            Log.d(TAG, "releasePrimaryDisplay")
            sessionManager.releasePrimaryDisplay()
        }

        override fun getInstalledPackages(): List<String> {
            enforceCallingPermission()
            return sessionManager.getInstalledPackages()
        }

        override fun executeShell(command: String): String {
            enforceCallingPermission()
            Log.d(TAG, "executeShell: $command")
            return sessionManager.executeShell(command)
        }

        private fun enforceCallingPermission() {
            val callingUid = Binder.getCallingUid()
            // Allow system, root, and our own UID
            if (callingUid == android.os.Process.SYSTEM_UID ||
                callingUid == android.os.Process.ROOT_UID ||
                callingUid == android.os.Process.myUid()) {
                return
            }
            // For other callers, check permission
            this@AgentHostService.enforceCallingPermission(
                "com.agi.os.permission.CONTROL_SESSIONS",
                "Caller does not have permission to control sessions"
            )
        }
    }
}
