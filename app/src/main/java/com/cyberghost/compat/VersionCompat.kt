package com.cyberghost.compat

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object VersionCompat {
    const val CHANNEL_ID = "cyberghost_capture"
    fun requiredPermissions(): Array<String> {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.INTERNET)
        if (Build.VERSION.SDK_INT >= 33) { p.add(Manifest.permission.POST_NOTIFICATIONS); p.add("android.permission.NEARBY_WIFI_DEVICES") }
        if (Build.VERSION.SDK_INT >= 37) { try { p.add(Manifest.permission::class.java.getField("LOCAL_NETWORK_ACCESS").get(null) as String) } catch (_: Exception) {} }
        return p.toTypedArray()
    }
    fun hasAllPermissions(ctx: Context) = requiredPermissions().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    fun startForegroundCompat(svc: Service, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) svc.startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else svc.startForeground(id, n)
    }
    fun createNotificationChannel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "CyberGhost 2", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
    }
    fun buildNotification(ctx: Context, text: String): Notification = NotificationCompat.Builder(ctx, CHANNEL_ID).setContentTitle("CyberGhost 2").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_manage).setOngoing(true).build()
}
