package com.cafeteria.gestion.data.transfer

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Transporte por códigos QR animados (multiparte) para traspasos sin internet.
 *
 * Flujo: JSON del paquete -> deflate -> AES-GCM (TransferPackageManager) -> fragmentos -> un QR por fragmento.
 * Cada fragmento es texto ASCII: `YQ1.<sesion>.<indice>.<total>.<datos base64url>`.
 * Esta clase no depende de Android para poder probarse en una JVM normal.
 */
object QrTransferCodec {
    const val PREFIX = "YQ1"

    /** 260 bytes -> ~367 caracteres -> QR versión 15 con corrección M: legible con cámaras modestas. */
    const val DEFAULT_CHUNK_BYTES = 260
    const val MAX_FRAMES = 600
    const val MAX_UNCOMPRESSED_BYTES = 16 * 1024 * 1024

    private val random = SecureRandom()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()

    data class Frame(val sessionId: String, val index: Int, val total: Int, val data: ByteArray)

    fun newSessionId(): String {
        val bytes = ByteArray(6).also { random.nextBytes(it) }
        return urlEncoder.encodeToString(bytes)
    }

    fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(maxOf(64, data.size / 4))
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    fun decompress(data: ByteArray, maxBytes: Int = MAX_UNCOMPRESSED_BYTES): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(maxOf(64, data.size * 4))
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = try {
                    inflater.inflate(buffer)
                } catch (e: java.util.zip.DataFormatException) {
                    throw IllegalArgumentException("Los datos recibidos están dañados.")
                }
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw IllegalArgumentException("Los datos recibidos están incompletos.")
                }
                out.write(buffer, 0, n)
                require(out.size() <= maxBytes) { "El paquete descomprimido es demasiado grande." }
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    fun frameCountFor(payloadSize: Int, chunkBytes: Int = DEFAULT_CHUNK_BYTES): Int =
        maxOf(1, (payloadSize + chunkBytes - 1) / chunkBytes)

    fun encodeFrames(sessionId: String, payload: ByteArray, chunkBytes: Int = DEFAULT_CHUNK_BYTES): List<String> {
        require(chunkBytes in 64..600) { "Tamaño de fragmento no válido." }
        val total = frameCountFor(payload.size, chunkBytes)
        require(total <= MAX_FRAMES) {
            "Hay demasiadas operaciones para enviar por QR ($total códigos). Usa el archivo cifrado o haz el traspaso más seguido."
        }
        return List(total) { i ->
            val from = i * chunkBytes
            val to = minOf(payload.size, from + chunkBytes)
            val chunk = payload.copyOfRange(from, to)
            "$PREFIX.$sessionId.$i.$total.${urlEncoder.encodeToString(chunk)}"
        }
    }

    fun parseFrame(text: String): Frame? {
        val parts = text.trim().split('.', limit = 5)
        if (parts.size != 5 || parts[0] != PREFIX) return null
        val index = parts[2].toIntOrNull() ?: return null
        val total = parts[3].toIntOrNull() ?: return null
        if (parts[1].length !in 6..16 || total !in 1..MAX_FRAMES || index !in 0 until total) return null
        val data = try {
            urlDecoder.decode(parts[4])
        } catch (_: IllegalArgumentException) {
            return null
        }
        return Frame(parts[1], index, total, data)
    }
}

/** Recompone el paquete a partir de fragmentos en cualquier orden; ignora duplicados y otras sesiones. */
class QrFrameAssembler {
    enum class Result { ACCEPTED, DUPLICATE, INVALID, OTHER_SESSION }

    private val parts = HashMap<Int, ByteArray>()
    private var session: String? = null
    private var expectedTotal = 0

    @Synchronized fun accept(text: String): Result {
        val frame = QrTransferCodec.parseFrame(text) ?: return Result.INVALID
        val current = session
        if (current == null) {
            session = frame.sessionId
            expectedTotal = frame.total
        } else if (current != frame.sessionId || expectedTotal != frame.total) {
            return Result.OTHER_SESSION
        }
        if (parts.containsKey(frame.index)) return Result.DUPLICATE
        parts[frame.index] = frame.data
        return Result.ACCEPTED
    }

    @Synchronized fun total(): Int = expectedTotal
    @Synchronized fun receivedCount(): Int = parts.size
    @Synchronized fun isComplete(): Boolean = expectedTotal > 0 && parts.size == expectedTotal
    @Synchronized fun has(index: Int): Boolean = parts.containsKey(index)

    @Synchronized fun assemble(): ByteArray {
        check(isComplete()) { "Faltan fragmentos por escanear." }
        val out = ByteArrayOutputStream()
        for (i in 0 until expectedTotal) out.write(parts.getValue(i))
        return out.toByteArray()
    }

    @Synchronized fun reset() {
        parts.clear()
        session = null
        expectedTotal = 0
    }
}
