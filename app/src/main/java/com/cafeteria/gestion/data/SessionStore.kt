package com.cafeteria.gestion.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

private val Context.sessionDataStore by preferencesDataStore(name = "cafeteria_session")

/** Registration data kept locally until the public customer endpoint accepts it. */
data class PendingCustomerRegistration(
    val localUserId: String,
    val displayName: String,
    val identifier: String,
    val password: String
)

class SessionStore(private val context: Context) {
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ROLE = stringPreferencesKey("role")
        val TOKEN_ENCRYPTED = stringPreferencesKey("access_token_encrypted")
        val BUSINESS_ID = stringPreferencesKey("business_id")
        val BUSINESS_TIME_ZONE = stringPreferencesKey("business_time_zone")
        val CUSTOMER_IDENTIFIER = stringPreferencesKey("customer_identifier")
        val CUSTOMER_DISPLAY_NAME = stringPreferencesKey("customer_display_name")
        val CUSTOMER_LOCAL_USER_ID = stringPreferencesKey("customer_local_user_id")
        val CUSTOMER_SALT = stringPreferencesKey("customer_password_salt")
        val CUSTOMER_HASH = stringPreferencesKey("customer_password_hash")
        val PENDING_CUSTOMER_PASSWORD = stringPreferencesKey("pending_customer_password_encrypted")
        val PENDING_CUSTOMER_USER_ID = stringPreferencesKey("pending_customer_user_id")
        val PENDING_CUSTOMER_IDENTIFIER = stringPreferencesKey("pending_customer_identifier")
        val PENDING_CUSTOMER_DISPLAY_NAME = stringPreferencesKey("pending_customer_display_name")
        val STATION_AUTH_CODE_ENCRYPTED = stringPreferencesKey("station_auth_code_encrypted")
        val STATION_AUTH_BUSINESS_ID = stringPreferencesKey("station_auth_business_id")
        val STATION_AUTH_ID = stringPreferencesKey("station_auth_id")
        val LOCAL_STATION_ID = stringPreferencesKey("local_station_id")
        val LOCAL_STATION_SECRET_ENCRYPTED = stringPreferencesKey("local_station_secret_encrypted")
    }

    suspend fun save(session: UserSession) {
        val encryptedToken = encrypt(session.accessToken)
        context.sessionDataStore.edit { prefs ->
            writeSession(prefs, session, encryptedToken)
            if (session.role != Roles.LOCAL_STATION) {
                prefs.remove(Keys.LOCAL_STATION_ID)
                prefs.remove(Keys.LOCAL_STATION_SECRET_ENCRYPTED)
            }
        }
    }

    /** Stores the owner-generated local station pairing code encrypted on the owner's device. */
    suspend fun saveStationAuthorization(code: String, businessId: String, stationId: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.STATION_AUTH_CODE_ENCRYPTED] = encrypt(code)
            prefs[Keys.STATION_AUTH_BUSINESS_ID] = businessId
            prefs[Keys.STATION_AUTH_ID] = stationId
        }
    }

    suspend fun getStationAuthorization(): String? {
        val prefs = context.sessionDataStore.data.first()
        val encrypted = prefs[Keys.STATION_AUTH_CODE_ENCRYPTED] ?: return null
        return runCatching { decrypt(encrypted) }.getOrNull()
    }

    suspend fun revokeStationAuthorization() = context.sessionDataStore.edit { prefs ->
        prefs.remove(Keys.STATION_AUTH_CODE_ENCRYPTED)
        prefs.remove(Keys.STATION_AUTH_BUSINESS_ID)
        prefs.remove(Keys.STATION_AUTH_ID)
    }

    /** Activates a restricted local-only identity; it has no backend access token or worker/owner role. */
    suspend fun activateLocalStation(businessId: String, stationId: String, secret: String): UserSession {
        val session = UserSession(
            userId = "local-station-$stationId",
            displayName = "Estación local",
            role = Roles.LOCAL_STATION,
            accessToken = "",
            businessId = businessId,
            businessTimeZone = "America/Havana"
        )
        context.sessionDataStore.edit { prefs ->
            writeSession(prefs, session, encrypt(""))
            prefs[Keys.LOCAL_STATION_ID] = stationId
            prefs[Keys.LOCAL_STATION_SECRET_ENCRYPTED] = encrypt(secret)
        }
        return session
    }

    suspend fun getLocalStationSecret(): String? {
        val encrypted = context.sessionDataStore.data.first()[Keys.LOCAL_STATION_SECRET_ENCRYPTED] ?: return null
        return runCatching { decrypt(encrypted) }.getOrNull()
    }

    suspend fun getLocalStationId(): String? = context.sessionDataStore.data.first()[Keys.LOCAL_STATION_ID]

    /** Save a customer profile immediately, even with no network. The verifier is salted PBKDF2;
     * the temporary password needed for first server registration is encrypted by Android Keystore. */
    suspend fun saveLocalCustomerAccount(displayName: String, identifier: String, password: String): UserSession {
        val userId = "local-customer-${java.util.UUID.randomUUID()}"
        val (salt, hash) = withContext(Dispatchers.Default) { passwordVerifier(password) }
        val session = UserSession(userId, displayName, Roles.CUSTOMER, "")
        val encryptedToken = encrypt("")
        val encryptedPendingPassword = encrypt(password)
        context.sessionDataStore.edit { prefs ->
            writeSession(prefs, session, encryptedToken)
            prefs[Keys.CUSTOMER_IDENTIFIER] = normalize(displayName)
            prefs[Keys.CUSTOMER_DISPLAY_NAME] = displayName
            prefs[Keys.CUSTOMER_LOCAL_USER_ID] = userId
            prefs[Keys.CUSTOMER_SALT] = salt
            prefs[Keys.CUSTOMER_HASH] = hash
            prefs[Keys.PENDING_CUSTOMER_PASSWORD] = encryptedPendingPassword
            prefs[Keys.PENDING_CUSTOMER_USER_ID] = userId
            prefs[Keys.PENDING_CUSTOMER_IDENTIFIER] = identifier.trim()
            prefs[Keys.PENDING_CUSTOMER_DISPLAY_NAME] = displayName.trim()
        }
        return session
    }

    /** Store an offline verifier after a successful online customer login or registration. */
    suspend fun saveCustomerCredentials(session: UserSession, identifier: String, password: String) {
        val (salt, hash) = withContext(Dispatchers.Default) { passwordVerifier(password) }
        val encryptedToken = encrypt(session.accessToken)
        context.sessionDataStore.edit { prefs ->
            writeSession(prefs, session, encryptedToken)
            // Keep the only local offline profile intact while its signup is still pending.
            // A second customer may sign in on this phone, but must not overwrite the
            // verifier/password queue belonging to the not-yet-synced account.
            if (prefs[Keys.PENDING_CUSTOMER_USER_ID].isNullOrBlank()) {
                prefs[Keys.CUSTOMER_IDENTIFIER] = normalize(identifier)
                prefs[Keys.CUSTOMER_DISPLAY_NAME] = session.displayName
                prefs[Keys.CUSTOMER_LOCAL_USER_ID] = session.userId
                prefs[Keys.CUSTOMER_SALT] = salt
                prefs[Keys.CUSTOMER_HASH] = hash
            }
        }
    }

    suspend fun verifyOfflineCustomer(identifier: String, password: String): UserSession? {
        val prefs = context.sessionDataStore.data.first()
        val savedIdentifier = prefs[Keys.CUSTOMER_IDENTIFIER] ?: return null
        val salt = prefs[Keys.CUSTOMER_SALT] ?: return null
        val expected = prefs[Keys.CUSTOMER_HASH] ?: return null
        if (savedIdentifier != normalize(identifier)) return null
        val actual = withContext(Dispatchers.Default) { derivePassword(password, Base64.decode(salt, Base64.NO_WRAP)) }
        if (!MessageDigest.isEqual(actual, Base64.decode(expected, Base64.NO_WRAP))) return null
        val displayName = prefs[Keys.CUSTOMER_DISPLAY_NAME] ?: identifier.trim()
        val active = getSession()
        val userId = prefs[Keys.CUSTOMER_LOCAL_USER_ID]
            ?: active?.takeIf { it.role == Roles.CUSTOMER && it.displayName == displayName }?.userId
            ?: "local-customer-${java.util.UUID.randomUUID()}"
        return UserSession(userId, displayName, Roles.CUSTOMER, "")
    }

    suspend fun pendingCustomerRegistration(): PendingCustomerRegistration? {
        val prefs = context.sessionDataStore.data.first()
        val encryptedPassword = prefs[Keys.PENDING_CUSTOMER_PASSWORD] ?: return null
        val userId = prefs[Keys.PENDING_CUSTOMER_USER_ID] ?: return null
        val identifier = prefs[Keys.PENDING_CUSTOMER_IDENTIFIER] ?: return null
        val displayName = prefs[Keys.PENDING_CUSTOMER_DISPLAY_NAME] ?: return null
        val password = runCatching { decrypt(encryptedPassword) }.getOrNull() ?: return null
        return PendingCustomerRegistration(userId, displayName, identifier, password)
    }

    /** Finish registration without replacing an unrelated staff/customer session. */
    suspend fun completePendingCustomerRegistration(localUserId: String, serverSession: UserSession) {
        val encryptedToken = encrypt(serverSession.accessToken)
        context.sessionDataStore.edit { prefs ->
            val currentId = prefs[Keys.USER_ID]
            val localProfileId = prefs[Keys.CUSTOMER_LOCAL_USER_ID]
            if (prefs[Keys.PENDING_CUSTOMER_USER_ID] == localUserId) clearPendingCustomer(prefs)
            // A person may have switched accounts while this signup was waiting for network.
            // Update only the local profile that owns this pending registration.
            if (localProfileId == localUserId) {
                prefs[Keys.CUSTOMER_LOCAL_USER_ID] = serverSession.userId
                if (currentId == localUserId) writeSession(prefs, serverSession, encryptedToken)
            }
        }
    }

    suspend fun hasPendingCustomerRegistration(): Boolean =
        context.sessionDataStore.data.first()[Keys.PENDING_CUSTOMER_USER_ID] != null

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

    /** Logout removes only the active session. The local customer verifier and any unsubmitted
     * registration remain available; a queued password is never discarded by a normal logout. */
    suspend fun clear() = context.sessionDataStore.edit { prefs ->
        prefs.remove(Keys.USER_ID)
        prefs.remove(Keys.DISPLAY_NAME)
        prefs.remove(Keys.ROLE)
        prefs.remove(Keys.TOKEN_ENCRYPTED)
        prefs.remove(Keys.BUSINESS_ID)
        prefs.remove(Keys.BUSINESS_TIME_ZONE)
        prefs.remove(Keys.LOCAL_STATION_ID)
        prefs.remove(Keys.LOCAL_STATION_SECRET_ENCRYPTED)
    }

    private fun writeSession(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        session: UserSession,
        encryptedToken: String
    ) {
        prefs[Keys.USER_ID] = session.userId
        prefs[Keys.DISPLAY_NAME] = session.displayName
        prefs[Keys.ROLE] = session.role
        prefs[Keys.TOKEN_ENCRYPTED] = encryptedToken
        session.businessId?.let { prefs[Keys.BUSINESS_ID] = it } ?: prefs.remove(Keys.BUSINESS_ID)
        prefs[Keys.BUSINESS_TIME_ZONE] = session.businessTimeZone
    }

    private fun clearPendingCustomer(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs.remove(Keys.PENDING_CUSTOMER_PASSWORD)
        prefs.remove(Keys.PENDING_CUSTOMER_USER_ID)
        prefs.remove(Keys.PENDING_CUSTOMER_IDENTIFIER)
        prefs.remove(Keys.PENDING_CUSTOMER_DISPLAY_NAME)
    }

    private fun passwordVerifier(password: String): Pair<String, String> {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derivePassword(password, salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP) to Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun derivePassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun normalize(value: String) = value.trim().lowercase(java.util.Locale.ROOT)

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
        require(parts.size == 2) { "El dato cifrado no tiene un formato válido." }
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
        const val PBKDF2_ITERATIONS = 160_000
    }
}
