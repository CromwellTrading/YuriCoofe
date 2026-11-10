package com.cafeteria.gestion.data.transfer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cross-device transfer encryption. The passphrase is a one-time secret shared separately
 * from the file. AES-GCM authenticates the complete envelope; SHA-256 is also included in
 * the document for explicit package-integrity auditing after decryption.
 */
class TransferPackageManager(private val context: Context, private val gson: Gson = Gson()) {
    companion object {
        private const val MAGIC = "YCFB"
        private const val FORMAT_VERSION: Byte = 1
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val KEY_BITS = 256
        private const val PBKDF2_ITERATIONS = 310_000
        private const val MAX_PACKAGE_BYTES = 40 * 1024 * 1024
        private const val KEY_ALIAS = "yuricoofe-transfer-at-rest-v1"
    }

    data class Document<T>(val payload: T, val sha256: String, val formatVersion: Int = 1)

    fun newTransferCode(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun encode(plainJson: String, transferCode: String): ByteArray =
        encodeBytes(plainJson.toByteArray(Charsets.UTF_8), transferCode)

    /** Igual que [encode] pero para bytes arbitrarios (p. ej. JSON ya comprimido para el transporte por QR). */
    fun encodeBytes(plain: ByteArray, transferCode: String): ByteArray {
        validateCode(transferCode)
        require(plain.size <= MAX_PACKAGE_BYTES) { "El paquete supera el límite de 40 MiB." }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val header = ByteBuffer.allocate(4 + 1 + SALT_BYTES + NONCE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC.toByteArray(Charsets.US_ASCII))
            .put(FORMAT_VERSION)
            .put(salt)
            .put(nonce)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(transferCode, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        val encrypted = cipher.doFinal(plain)
        val out = ByteArrayOutputStream(header.size + encrypted.size)
        out.write(header)
        out.write(encrypted)
        val result = out.toByteArray()
        require(result.size <= MAX_PACKAGE_BYTES) { "El paquete cifrado supera el límite de 40 MiB." }
        return result
    }

    fun decode(encryptedPackage: ByteArray, transferCode: String): String =
        decodeBytes(encryptedPackage, transferCode).toString(Charsets.UTF_8)

    fun decodeBytes(encryptedPackage: ByteArray, transferCode: String): ByteArray {
        validateCode(transferCode)
        require(encryptedPackage.size in (4 + 1 + SALT_BYTES + NONCE_BYTES + 16)..MAX_PACKAGE_BYTES) {
            "El archivo de transferencia está vacío, incompleto o supera 40 MiB."
        }
        val headerSize = 4 + 1 + SALT_BYTES + NONCE_BYTES
        val buffer = ByteBuffer.wrap(encryptedPackage).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
        require(magic == MAGIC) { "Este archivo no es un paquete YuriCoofe válido." }
        val version = buffer.get()
        require(version == FORMAT_VERSION) { "La versión del paquete no es compatible con esta aplicación." }
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
        val header = encryptedPackage.copyOfRange(0, headerSize)
        val ciphertext = encryptedPackage.copyOfRange(headerSize, encryptedPackage.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(transferCode, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        return try {
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            throw IllegalArgumentException("No se pudo autenticar el paquete. Comprueba el código de transferencia (o el PIN) o vuelve a copiar el archivo.")
        }
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /** Encrypt imported/exported bundle JSON at rest with a device-bound Android Keystore key. */
    fun sealAtRest(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, atRestKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun openAtRest(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "El historial cifrado de transferencias está dañado." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, atRestKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun validateCode(code: String) {
        require(code.length >= 20) { "El código debe tener al menos 20 caracteres. Genera un código nuevo y compártelo por separado del archivo." }
        require(code.length <= 256) { "El código de transferencia es demasiado largo." }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun atRestKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
