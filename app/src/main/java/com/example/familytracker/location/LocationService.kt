package com.example.familytracker.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.*

/**
 * A robust Foreground Service for periodic location tracking.
 * Works on Android 8.0 through Android 14+.
 */
class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var familyPersonName: String = "Unknown"

    // Configuration
    private val UPDATE_INTERVAL_MS = 60000L // 60 Seconds
    private val MIN_UPDATE_DISTANCE_METERS = 42f // Only update if moved 42m

    companion object {
        const val CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 12345
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Define the callback for location updates
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    emitLocation(location)
                }
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                familyPersonName = intent.getStringExtra("EXTRA_FAMILY_PERSON_NAME") ?: "Unknown"
                startTracking()
            }
            ACTION_STOP -> stopTracking()
        }

        // START_STICKY ensures the service restarts if the system kills it for memory
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        // 1. Create the persistent notification required for Foreground Service
        val notification = buildNotification()

        // 2. Start the service in the foreground
        // Android 14 requires specifying the service type
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d("LocationService", "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e("LocationService", "Error starting foreground service: ${e.message}", e)
            // If we can't start as foreground, try to at least show notification
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e("LocationService", "Failed to start foreground service: ${e2.message}")
                stopSelf()
                return
            }
        }

        // 3. Configure Location Request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(false)
        }.build()

        // 4. Request updates
        // Note: You must check permissions in your Activity before starting this service
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationService", "Tracking started")
        } catch (e: SecurityException) {
            Log.e("LocationService", "Permission lost: $e")
            stopSelf()
        }
    }

    private fun stopTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.d("LocationService", "Tracking stopped")
        } catch (e: Exception) {
            Log.e("LocationService", "Error stopping: $e")
        }
    }

    private fun emitLocation(location: Location) {
        Log.d("LocationService", "$familyPersonName Location: ${location.latitude}, ${location.longitude}")
        // Send data to Telegram via the Manager with personalized message
        TdLibManager.sendLocation(location.latitude, location.longitude, familyPersonName)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Sharing Active")
            .setContentText("Your location is being shared with family.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority so it doesn't make sound
            .setOngoing(true) // User cannot dismiss this
            .setAutoCancel(false) // Prevent auto-cancel on tap
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            // Prevent users from disabling this notification
            channel.setShowBadge(false)
            channel.enableVibration(false)
            channel.enableLights(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}