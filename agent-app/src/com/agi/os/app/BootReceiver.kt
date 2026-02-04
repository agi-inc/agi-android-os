package com.agi.os.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives boot completed broadcasts and starts the AgentHostService.
 *
 * This receiver is registered for both BOOT_COMPLETED and LOCKED_BOOT_COMPLETED
 * to ensure the agent service starts as early as possible after device boot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AGI.BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Received broadcast: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(TAG, "Starting AgentHostService...")
                startAgentService(context)
            }
        }
    }

    private fun startAgentService(context: Context) {
        try {
            val serviceIntent = Intent(context, AgentHostService::class.java)
            context.startForegroundService(serviceIntent)
            Log.i(TAG, "AgentHostService start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AgentHostService", e)
        }
    }
}
