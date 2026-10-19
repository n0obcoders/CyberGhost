package com.cyberghost

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class CaptureService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel("cg2_service", "CyberGhost Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, "cg2_service")
            .setContentTitle("CyberGhost 2")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true).build()
        startForeground(1338, notification)
        Log.i("CG2", "CaptureService started")
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { Log.i("CG2", "CaptureService destroyed"); super.onDestroy() }
}
