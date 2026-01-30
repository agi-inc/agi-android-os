/*
 * AgentSystemService
 *
 * A system service providing programmatic control over virtual displays,
 * input injection, and system operations. Runs in system_server with
 * full system privileges.
 *
 * This is pure infrastructure - no agent logic. Consumers (agents, scripts,
 * test frameworks) use this via the SDK.
 */
package com.agi.os.service

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.util.Slog
import com.android.server.SystemService
import com.agi.os.IAgentService
import com.agi.os.IAgentSession
import com.agi.os.SessionConfig
import com.agi.os.session.SessionManager

class AgentSystemService(context: Context) : SystemService(context) {

    companion object {
        private const val TAG = "AgentSystemService"
        const val SERVICE_NAME = "agent"
    }

    private lateinit var sessionManager: SessionManager
    private val serviceBinder = AgentServiceImpl()

    override fun onStart() {
        Slog.i(TAG, "Starting AgentSystemService")

        // Initialize session manager
        sessionManager = SessionManager(context)

        // Publish the binder service
        publishBinderService(SERVICE_NAME, serviceBinder)

        Slog.i(TAG, "AgentSystemService started successfully")
    }

    override fun onBootPhase(phase: Int) {
        when (phase) {
            PHASE_SYSTEM_SERVICES_READY -> {
                Slog.i(TAG, "System services ready")
            }
            PHASE_BOOT_COMPLETED -> {
                Slog.i(TAG, "Boot completed, AgentSystemService fully operational")
                sessionManager.onBootCompleted()
            }
        }
    }

    /**
     * Implementation of IAgentService AIDL interface.
     */
    private inner class AgentServiceImpl : IAgentService.Stub() {

        override fun createSession(config: SessionConfig): IAgentSession {
            enforceCallingPermission()

            val callingUid = Binder.getCallingUid()
            Slog.d(TAG, "createSession called by uid=$callingUid, config=$config")

            val session = sessionManager.createSession(config)
            return SessionBinder(session)
        }

        override fun destroySession(sessionId: String) {
            enforceCallingPermission()
            Slog.d(TAG, "destroySession: $sessionId")
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
            Slog.d(TAG, "controlPrimaryDisplay")
            val session = sessionManager.controlPrimaryDisplay()
            return SessionBinder(session)
        }

        override fun releasePrimaryDisplay() {
            enforceCallingPermission()
            Slog.d(TAG, "releasePrimaryDisplay")
            sessionManager.releasePrimaryDisplay()
        }

        override fun getInstalledPackages(): List<String> {
            enforceCallingPermission()
            return sessionManager.getInstalledPackages()
        }

        override fun executeShell(command: String): String {
            enforceCallingPermission()
            Slog.d(TAG, "executeShell: $command")
            return sessionManager.executeShell(command)
        }

        private fun enforceCallingPermission() {
            // For now, allow system apps and apps with our signature permission
            // TODO: Implement proper permission checking
            val callingUid = Binder.getCallingUid()
            if (callingUid != android.os.Process.SYSTEM_UID &&
                callingUid != android.os.Process.ROOT_UID) {
                // Check if caller has our permission
                context.enforceCallingPermission(
                    "com.agi.os.permission.CONTROL_SESSIONS",
                    "Caller does not have permission to control sessions"
                )
            }
        }
    }
}
