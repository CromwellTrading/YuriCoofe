package com.cafeteria.gestion.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cafeteria.gestion.CafeteriaApplication
import com.cafeteria.gestion.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Displays announcement pushes while the app is open and refreshes changed FCM tokens. */
class CafeMessagingService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Aviso de la cafetería"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: return
        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = application as? CafeteriaApplication ?: return
        if (!app.apiClient.isConfigured) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val session = app.sessionStore.getSession()
                if (session == null) return@runCatching
                if (session.role == com.cafeteria.gestion.data.Roles.CUSTOMER && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@CafeMessagingService, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return@runCatching
                }
                val deviceId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ).orEmpty()
                app.repository.registerPushToken(token, deviceId)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Avisos y ofertas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Novedades, ofertas y disponibilidad de la cafetería"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "cafeteria_announcements"
    }
}
