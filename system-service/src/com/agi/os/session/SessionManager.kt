/*
 * SessionManager
 *
 * Manages all active sessions (both headless and physical display).
 * Thread-safe - can be called from multiple binder threads.
 */
package com.agi.os.session

import android.content.Context
import android.util.Slog
import com.agi.os.SessionConfig
import com.agi.os.display.VirtualDisplayManager
import com.agi.os.input.InputInjector
import com.agi.os.screen.ScreenCapturer
import java.util.concurrent.ConcurrentHashMap

class SessionManager(private val context: Context) {

    companion object {
        private const val TAG = "SessionManager"
        private const val MAX_SESSIONS = 10
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private var physicalDisplaySession: Session? = null
    private val physicalDisplayLock = Object()

    // Subsystems
    private val virtualDisplayManager = VirtualDisplayManager(context)
    private val inputInjector = InputInjector(context)
    private val screenCapturer = ScreenCapturer(context)
    private val systemExecutor = SystemExecutor(context)

    /**
     * Called when boot is completed. Initialize any deferred resources.
     */
    fun onBootCompleted() {
        Slog.i(TAG, "Boot completed, SessionManager ready")
    }

    /**
     * Create a new session.
     */
    fun createSession(config: SessionConfig): Session {
        if (sessions.size >= MAX_SESSIONS) {
            throw IllegalStateException("Maximum number of sessions ($MAX_SESSIONS) reached")
        }

        val session = if (config.headless) {
            Session.createHeadless(
                context = context,
                config = config,
                displayManager = virtualDisplayManager,
                inputInjector = inputInjector,
                screenCapturer = screenCapturer,
                systemExecutor = systemExecutor
            )
        } else {
            // Non-headless sessions use physical display
            synchronized(physicalDisplayLock) {
                if (physicalDisplaySession != null) {
                    throw IllegalStateException("Physical display is already controlled")
                }
                val session = Session.createPhysical(
                    context = context,
                    inputInjector = inputInjector,
                    screenCapturer = screenCapturer,
                    systemExecutor = systemExecutor
                )
                physicalDisplaySession = session
                session
            }
        }

        sessions[session.id] = session
        Slog.i(TAG, "Created session: ${session.id} (headless=${session.isHeadless}, displayId=${session.displayId})")

        return session
    }

    /**
     * Get an existing session by ID.
     */
    fun getSession(sessionId: String): Session? {
        return sessions[sessionId]
    }

    /**
     * List all session IDs.
     */
    fun listSessionIds(): List<String> {
        return sessions.keys().toList()
    }

    /**
     * Destroy a session.
     */
    fun destroySession(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return

        synchronized(physicalDisplayLock) {
            if (physicalDisplaySession?.id == sessionId) {
                physicalDisplaySession = null
            }
        }

        session.destroy()
        Slog.i(TAG, "Destroyed session: $sessionId")
    }

    /**
     * Take control of the physical display.
     */
    fun controlPrimaryDisplay(): Session {
        synchronized(physicalDisplayLock) {
            if (physicalDisplaySession != null) {
                return physicalDisplaySession!!
            }

            val session = Session.createPhysical(
                context = context,
                inputInjector = inputInjector,
                screenCapturer = screenCapturer,
                systemExecutor = systemExecutor
            )
            sessions[session.id] = session
            physicalDisplaySession = session

            Slog.i(TAG, "Controlling physical display")
            return session
        }
    }

    /**
     * Release control of the physical display.
     */
    fun releasePrimaryDisplay() {
        synchronized(physicalDisplayLock) {
            val session = physicalDisplaySession ?: return
            sessions.remove(session.id)
            session.destroy()
            physicalDisplaySession = null
            Slog.i(TAG, "Released physical display control")
        }
    }

    /**
     * Get list of installed packages.
     */
    fun getInstalledPackages(): List<String> {
        return systemExecutor.getInstalledPackages()
    }

    /**
     * Execute a shell command.
     */
    fun executeShell(command: String): String {
        return systemExecutor.executeShell(command)
    }

    /**
     * Clean up all sessions. Called during service shutdown.
     */
    fun shutdown() {
        Slog.i(TAG, "Shutting down SessionManager")
        for (session in sessions.values) {
            try {
                session.destroy()
            } catch (e: Exception) {
                Slog.e(TAG, "Error destroying session ${session.id}", e)
            }
        }
        sessions.clear()
        physicalDisplaySession = null
    }
}
