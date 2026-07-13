package com.dark.tool_neuron.repo.gateway.live

import java.io.InputStream
import kotlin.random.Random

// Pure RFC 6455 frame codec — the repo has no WebSocket library (:networking is GET-only curl, HttpURLConnection
// can't upgrade), so the Gemini Live transport frames itself over a raw TLS socket. Encode/decode is unit-testable
// off-device; only the socket wiring in LiveWebSocketTransport touches Android.
internal object WebSocketFrame {

    const val OPCODE_CONTINUATION = 0x0
    const val OPCODE_TEXT = 0x1
    const val OPCODE_BINARY = 0x2
    const val OPCODE_CLOSE = 0x8
    const val OPCODE_PING = 0x9
    const val OPCODE_PONG = 0xA

    // Sanity cap on a server-declared frame length: rejects a bogus/huge length before allocating (a >2GB length
    // would also overflow Int and throw NegativeArraySizeException). Gemini Live frames are far under this.
    const val MAX_FRAME_BYTES = 16 * 1024 * 1024

    data class Frame(val fin: Boolean, val opcode: Int, val payload: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Frame && fin == other.fin && opcode == other.opcode && payload.contentEquals(other.payload)

        override fun hashCode(): Int = (if (fin) 1 else 0) * 961 + opcode * 31 + payload.contentHashCode()
    }

    // Client→server frames MUST be masked (RFC 6455 §5.3). rng is injectable so the mask is deterministic in tests.
    fun encode(opcode: Int, payload: ByteArray, rng: Random = Random.Default): ByteArray {
        val out = ArrayList<Byte>(payload.size + 14)
        out.add((0x80 or opcode).toByte())
        val len = payload.size
        when {
            len < 126 -> out.add((0x80 or len).toByte())
            len <= 0xFFFF -> {
                out.add((0x80 or 126).toByte())
                out.add((len ushr 8 and 0xFF).toByte())
                out.add((len and 0xFF).toByte())
            }
            else -> {
                out.add((0x80 or 127).toByte())
                for (shift in 56 downTo 0 step 8) out.add((len.toLong() ushr shift and 0xFF).toByte())
            }
        }
        val mask = ByteArray(4).also { rng.nextBytes(it) }
        mask.forEach { out.add(it) }
        for (idx in payload.indices) out.add((payload[idx].toInt() xor mask[idx % 4].toInt()).toByte())
        return out.toByteArray()
    }

    // 2-byte big-endian status code + UTF-8 reason (RFC 6455 §5.5.1).
    fun closePayload(code: Int, reason: String): ByteArray {
        val r = reason.toByteArray(Charsets.UTF_8)
        val out = ByteArray(2 + r.size)
        out[0] = (code ushr 8 and 0xFF).toByte()
        out[1] = (code and 0xFF).toByte()
        r.copyInto(out, 2)
        return out
    }

    // Empty close payload → 1005 (no status present), per RFC 6455 §7.1.5.
    fun parseClose(payload: ByteArray): Pair<Int, String> {
        if (payload.size < 2) return 1005 to ""
        val code = (payload[0].toInt() and 0xFF shl 8) or (payload[1].toInt() and 0xFF)
        val reason = if (payload.size > 2) String(payload, 2, payload.size - 2, Charsets.UTF_8) else ""
        return code to reason
    }

    // Read exactly one frame. Server→client frames are never masked. Returns null on a clean stream end.
    fun readFrame(input: InputStream): Frame? {
        val b0 = input.read()
        if (b0 < 0) return null
        val fin = b0 and 0x80 != 0
        val opcode = b0 and 0x0F
        val b1 = input.readOrThrow()
        val masked = b1 and 0x80 != 0
        var len = (b1 and 0x7F).toLong()
        if (len == 126L) {
            len = (input.readOrThrow().toLong() shl 8) or input.readOrThrow().toLong()
        } else if (len == 127L) {
            len = 0
            repeat(8) { len = (len shl 8) or input.readOrThrow().toLong() }
        }
        // Sanity cap: a server frame larger than this is a fault, not a legit Gemini message. Guards against
        // a negative Int from toInt() overflow and an OOM allocation on a hostile/corrupt length.
        if (len < 0 || len > MAX_FRAME_BYTES) throw java.io.IOException("frame length out of range: $len")
        val mask = if (masked) ByteArray(4) { input.readOrThrow().toByte() } else null
        val payload = readN(input, len.toInt())
        if (mask != null) {
            for (idx in payload.indices) payload[idx] = (payload[idx].toInt() xor mask[idx % 4].toInt()).toByte()
        }
        return Frame(fin, opcode, payload)
    }

    private fun readN(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) throw java.io.EOFException("stream closed mid-frame")
            off += r
        }
        return buf
    }

    private fun InputStream.readOrThrow(): Int {
        val v = read()
        if (v < 0) throw java.io.EOFException("stream closed mid-frame")
        return v
    }
}
