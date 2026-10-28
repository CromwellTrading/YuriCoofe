package com.cafeteria.gestion

import android.app.Application
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.cafeteria.gestion.data.CafeRepository
import com.cafeteria.gestion.data.SessionStore
import com.cafeteria.gestion.data.local.CafeDatabase
import com.cafeteria.gestion.data.remote.ApiClient
import com.cafeteria.gestion.sync.SalesSyncScheduler

class CafeteriaApplication : Application() {
    lateinit var database: CafeDatabase
        private set
    lateinit var sessionStore: SessionStore
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var repository: CafeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = CafeDatabase.get(this)
        sessionStore = SessionStore(this)
        apiClient = ApiClient(sessionStore)
        repository = CafeRepository(database, sessionStore, apiClient, contentResolver, this)
        // Initialize the default Firebase app during Application startup so push messages can
        // be handled even when Android starts the app process in the background.
        configuredFirebaseApp()
        SalesSyncScheduler.ensurePeriodic(this)
    }

    private fun configuredFirebaseApp(): FirebaseApp? {
        if (BuildConfig.FIREBASE_API_KEY.isBlank() || BuildConfig.FIREBASE_APP_ID.isBlank() ||
            BuildConfig.FIREBASE_PROJECT_ID.isBlank() || BuildConfig.FIREBASE_SENDER_ID.isBlank()) return null
        return runCatching {
            FirebaseApp.getApps(this).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(
                    this,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .build()
                )
        }.getOrNull()
    }

    /** Returns false until both HTTPS API and Firebase app configuration are available. */
    fun registerPushTokenIfConfigured(): Boolean {
        if (!apiClient.isConfigured) return false
        if (configuredFirebaseApp() == null) return false
        return try {
            // FirebaseMessaging.getInstance(FirebaseApp) is not public in the current SDK.
            // configuredFirebaseApp() ensures the application's default FirebaseApp exists.
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching {
                        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
                        repository.registerPushToken(token, deviceId)
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
