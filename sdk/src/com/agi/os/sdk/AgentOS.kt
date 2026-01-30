/*
 * AgentOS SDK
 *
 * Client library for interacting with the AgentSystemService.
 * Provides a clean Kotlin API for session management and automation.
 *
 * Usage:
 *   val agentOS = AgentOS.getInstance(context)
 *   val session = agentOS.createSession(SessionConfig(headless = true))
 *   session.launchApp("com.example.app")
 *   val screenshot = session.captureScreen()
 *   session.destroy()
 */
package com.agi.os.sdk

import android.content.Context
import android.os.IBinder
import android.os.RemoteException
import com.agi.os.IAgentService
import com.agi.os.SessionConfig
import com.agi.os.sdk.internal.SessionImpl

/**
 * Main entry point for the AGI-OS SDK.
 *
 * Provides access to session creation, listing, and system operations.
 * Thread-safe - can be used from any thread.
 */
class AgentOS private constructor(private val context: Context) {

    companion object {
        private const val SERVICE_NAME = "agent"

        @Volatile
        private var instance: AgentOS? = null

        /**
         * Get the AgentOS singleton instance.
         *
         * @param context Application or activity context
         * @return AgentOS instance
         * @throws ServiceNotFoundException if AgentSystemService is not available
         *         (device is not running AGI-Android OS)
         */
        @JvmStatic
        fun getInstance(context: Context): AgentOS {
            return instance ?: synchronized(this) {
                instance ?: AgentOS(context.applicationContext).also { instance = it }
            }
        }
    }

    private val service: IAgentService by lazy {
        val binder = getServiceBinder()
            ?: throw ServiceNotFoundException(
                "AgentSystemService not found. Is this device running AGI-Android OS?"
            )
        IAgentService.Stub.asInterface(binder)
    }

    // ========== Session Management ==========

    /**
     * Create a new session with the given configuration.
     *
     * @param config Session configuration (dimensions, headless flag)
     * @return Session instance for interaction
     * @throws RemoteException if IPC fails
     * @throws SessionException if session creation fails
     */
    fun createSession(config: SessionConfig = SessionConfig()): Session {
        try {
            val sessionBinder = service.createSession(config)
            return SessionImpl(sessionBinder)
        } catch (e: RemoteException) {
            throw SessionException("Failed to create session", e)
        }
    }

    /**
     * Get an existing session by ID.
     *
     * @param sessionId The session ID
     * @return Session instance, or null if not found
     */
    fun getSession(sessionId: String): Session? {
        return try {
            service.getSession(sessionId)?.let { SessionImpl(it) }
        } catch (e: RemoteException) {
            null
        }
    }

    /**
     * List all active sessions.
     *
     * @return List of session info objects
     */
    fun listSessions(): List<SessionInfo> {
        return try {
            service.listSessions().mapNotNull { id ->
                service.getSession(id)?.let { binder ->
                    SessionInfo(
                        id = binder.id,
                        displayId = binder.displayId,
                        isHeadless = true, // Can't easily determine from binder
                        width = binder.screenSize[0],
                        height = binder.screenSize[1]
                    )
                }
            }
        } catch (e: RemoteException) {
            emptyList()
        }
    }

    /**
     * Destroy a session by ID.
     *
     * @param sessionId The session ID to destroy
     */
    fun destroySession(sessionId: String) {
        try {
            service.destroySession(sessionId)
        } catch (e: RemoteException) {
            // Ignore - session may already be destroyed
        }
    }

    // ========== Physical Display Control ==========

    /**
     * Take control of the physical display (display 0).
     *
     * Only one controller at a time. Call [releasePrimaryDisplay] when done.
     *
     * @return Session for the physical display
     * @throws SessionException if display is already controlled
     */
    fun controlPrimaryDisplay(): Session {
        try {
            val sessionBinder = service.controlPrimaryDisplay()
            return SessionImpl(sessionBinder)
        } catch (e: RemoteException) {
            throw SessionException("Failed to control primary display", e)
        }
    }

    /**
     * Release control of the physical display.
     */
    fun releasePrimaryDisplay() {
        try {
            service.releasePrimaryDisplay()
        } catch (e: RemoteException) {
            // Ignore
        }
    }

    // ========== System Operations ==========

    /**
     * Get list of installed package names on the device.
     *
     * @return List of package name strings
     */
    fun getInstalledPackages(): List<String> {
        return try {
            service.installedPackages
        } catch (e: RemoteException) {
            emptyList()
        }
    }

    /**
     * Execute a shell command with system privileges.
     *
     * @param command Shell command to execute
     * @return ShellResult with exit code and output
     */
    fun executeShell(command: String): ShellResult {
        return try {
            val output = service.executeShell(command)
            // Parse exit code from output if present
            if (output.startsWith("ERROR (exit ")) {
                val exitCode = output.substringAfter("exit ").substringBefore(")").toIntOrNull() ?: 1
                ShellResult(exitCode, "", output.substringAfter("): "))
            } else {
                ShellResult(0, output, "")
            }
        } catch (e: RemoteException) {
            ShellResult(-1, "", "RemoteException: ${e.message}")
        }
    }

    // ========== Private Helpers ==========

    private fun getServiceBinder(): IBinder? {
        // Use ServiceManager to get the system service
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            getServiceMethod.invoke(null, SERVICE_NAME) as? IBinder
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Information about an active session.
 */
data class SessionInfo(
    val id: String,
    val displayId: Int,
    val isHeadless: Boolean,
    val width: Int,
    val height: Int
)

/**
 * Result of shell command execution.
 */
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
    val output: String get() = stdout.ifEmpty { stderr }
}

/**
 * Thrown when the AgentSystemService is not available.
 */
class ServiceNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when a session operation fails.
 */
class SessionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
