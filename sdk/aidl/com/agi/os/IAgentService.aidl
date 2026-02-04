/*
 * IAgentService.aidl
 *
 * Main service interface for session management and system operations.
 */
package com.agi.os;

import com.agi.os.IAgentSession;
import com.agi.os.SessionConfig;

interface IAgentService {

    /**
     * Create a new session with the given configuration.
     * @param config Session configuration (size, dpi, headless flag)
     * @return Session interface for interacting with the session
     */
    IAgentSession createSession(in SessionConfig config);

    /**
     * Destroy an existing session by ID.
     * @param sessionId The session ID to destroy
     */
    void destroySession(String sessionId);

    /**
     * List all active session IDs.
     * @return List of session ID strings
     */
    List<String> listSessions();

    /**
     * Get a session by its ID.
     * @param sessionId The session ID
     * @return Session interface, or null if not found
     */
    IAgentSession getSession(String sessionId);

    /**
     * Take control of the physical display (display 0).
     * Only one controller at a time - throws if already controlled.
     * @return Session interface for the physical display
     */
    IAgentSession controlPrimaryDisplay();

    /**
     * Release control of the physical display.
     */
    void releasePrimaryDisplay();

    /**
     * Get list of all installed package names on the device.
     * @return List of package name strings
     */
    List<String> getInstalledPackages();

    /**
     * Execute a shell command with system privileges.
     * @param command The shell command to execute
     * @return Command output (stdout + stderr)
     */
    String executeShell(String command);
}
