package com.cafeteria.gestion.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.sessionDataStore by preferencesDataStore(name = "cafeteria_session")

class SessionStore(private val context: Context) {
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ROLE = stringPreferencesKey("role")
        val TOKEN_ENCRYPTED = stringPreferencesKey("access_token_encrypted")
        val BUSINESS_ID = stringPreferencesKey("business_id")
        val BUSINESS_TIME_ZONE = stringPreferencesKey("business_time_zone")
    }

    suspend fun save(session: UserSession) {
        val encryptedToken = encrypt(session.accessToken)
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.DISPLAY_NAME] = session.displayName
            prefs[Keys.ROLE] = session.role
            prefs[Keys.TOKEN_ENCRYPTED] = encryptedToken
            session.businessId?.let { prefs[Keys.BUSINESS_ID] = it } ?: prefs.remove(Keys.BUSINESS_ID)
            prefs[Keys.BUSINESS_TIME_ZONE] = session.businessTimeZone
        }
    }

    suspend fun getSession(): UserSession? {
        val prefs = context.sessionDataStore.data.first()
        val id = prefs[Keys.USER_ID] ?: return null
        val encryptedToken = prefs[Keys.TOKEN_ENCRYPTED] ?: return null
        val token = runCatching { decrypt(encryptedToken) }.getOrNull() ?: return null
        val role = prefs[Keys.ROLE] ?: return null
        return UserSession(
            userId = id,
            displayName = prefs[Keys.DISPLAY_NAME].orEmpty(),
            role = role,
            accessToken = token,
            businessId = prefs[Keys.BUSINESS_ID],
            businessTimeZone = prefs[Keys.BUSINESS_TIME_ZONE] ?: "America/Havana"
        )
    }

    suspend fun getToken(): String? = context.sessionDataStore.data.first()[Keys.TOKEN_ENCRYPTED]
        ?.let { runCatching { decrypt(it) }.getOrNull() }

    suspend fun clear() = context.sessionDataStore.edit { it.clear() }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "El token guardado no tiene un formato válido." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "cafeteria_session_aes_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
