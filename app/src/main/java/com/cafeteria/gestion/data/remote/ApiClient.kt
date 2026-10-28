package com.cafeteria.gestion.data.remote

import com.cafeteria.gestion.BuildConfig
import com.cafeteria.gestion.data.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiNotConfiguredException : IllegalStateException(
    "El servicio de la cafetería todavía no está configurado en esta instalación."
)

class ApiClient(private val sessionStore: SessionStore) {
    val isConfigured: Boolean get() = BuildConfig.API_BASE_URL.isNotBlank()
    @Volatile private var cachedService: ApiService? = null

    fun service(): ApiService {
        cachedService?.let { return it }
        synchronized(this) {
            cachedService?.let { return it }
            val baseUrl = BuildConfig.API_BASE_URL.trim()
            if (baseUrl.isBlank()) throw ApiNotConfiguredException()
            require(baseUrl.startsWith("https://")) {
                "La dirección del servicio debe usar HTTPS."
            }
            require(baseUrl.endsWith("/")) { "La dirección del servicio debe terminar en /." }

            val authInterceptor = Interceptor { chain ->
                val token = runBlocking { sessionStore.getToken() }
                val request = chain.request().newBuilder().apply {
                    if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                    header("X-App-Version", "1.0.0")
                }.build()
                chain.proceed(request)
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .build()
            val service = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
            cachedService = service
            return service
        }
    }
}
