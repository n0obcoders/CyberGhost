package com.cyberghost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("CG2", "Boot completed — auto-start disabled by default")
            // auto-start only if user enabled it in prefs
            val prefs = context.getSharedPreferences("cg2_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_start", false)) {
                val i = Intent(context, CaptureService::class.java)
                context.startForegroundService(i)
            }
        }
    }
}
