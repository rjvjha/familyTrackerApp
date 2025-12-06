package com.example.familytracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.familytracker.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Broadcast receiver that listens for device boot completion and posts a notification
 * to allow the user to restart location tracking.
 */
class BootRestartReceiver : BroadcastReceiver() {

    companion object {
        const val BOOT_NOTIFICATION_CHANNEL_ID = "boot_restart_channel"
        const val BOOT_NOTIFICATION_ID = 54321
        const val EXTRA_DEVICE_RESTARTED = "EXTRA_DEVICE_RESTARTED"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Log.d("BootRestartReceiver", "Device boot completed")
            
            // Check if tracking was enabled before reboot
            val sharedPref = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
            val wasTracking = sharedPref.getBoolean("is_tracking", false)
            val familyPersonName = sharedPref.getString("family_person_name", "") ?: ""
            
            if (wasTracking && familyPersonName.isNotEmpty()) {
                // Add 10 second delay before posting notification
                CoroutineScope(Dispatchers.Default).launch {
                    delay(10000) // 10 seconds
                    Log.d("BootRestartReceiver", "Posting boot restart notification")
                    postBootRestartNotification(context, familyPersonName)
                }
            }
        }
    }

    private fun postBootRestartNotification(context: Context, familyPersonName: String) {
        // Create notification channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BOOT_NOTIFICATION_CHANNEL_ID,
                "Device Restart Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifications for restarting location tracking after device restart"
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to launch MainActivity with restart flag
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEVICE_RESTARTED, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build and post notification
        val notification = NotificationCompat.Builder(context, BOOT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Resume Location Sharing?")
            .setContentText("Tap to resume sharing your location as $familyPersonName")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(BOOT_NOTIFICATION_ID, notification)
    }
}
