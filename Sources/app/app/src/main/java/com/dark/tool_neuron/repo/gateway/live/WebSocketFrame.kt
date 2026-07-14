package com.dark.tool_neuron.repo.gateway.live

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.random.Random

// Pure RFC 6455 frame codec (repo has no WebSocket lib); encode/decode is unit-testable off-device, only the socket wiring touches Android.
internal object WebSocketFrame {

    const val OPCODE_CONTINUATION = 0x0
    const val OPCODE_TEXT = 0x1
    const val OPCODE_BINARY = 0x2
    const val OPCODE_CLOSE = 0x8
    const val OPCODE_PING = 0x9
    const val OPCODE_PONG = 0xA

    // Cap on a server-declared frame length: rejects a bogus/huge length before allocating (also guards Int overflow).
    const val MAX_FRAME_BYTES = 16 * 1024 * 1024

    // A server frame that violates RFC 6455 (masked, RSV set, oversized/fragmented control, reserved opcode).
    class ProtocolException(message: String) : Exception(message)

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

    // Empty payload → 1005 (§7.1.5); 1-byte payload, non-wire status code, or invalid-UTF-8 reason → ProtocolException.
    fun parseClose(payload: ByteArray): Pair<Int, String> {
        if (payload.isEmpty()) return 1005 to ""
        if (payload.size == 1) throw ProtocolException("close payload of 1 byte")
        val code = (payload[0].toInt() and 0xFF shl 8) or (payload[1].toInt() and 0xFF)
        if (!isValidReceivedCloseCode(code)) throw ProtocolException("invalid close code $code")
        val reason = if (payload.size > 2) decodeStrictUtf8(payload, 2) else ""
        return code to reason
    }

    // Codes a peer may legitimately send (RFC 6455 §7.4). 1004/1005/1006/1015 and the reserved ranges are excluded.
    fun isValidReceivedCloseCode(code: Int): Boolean =
        code in intArrayOf(1000, 1001, 1002, 1003, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014) || code in 3000..4999

    private fun decodeStrictUtf8(bytes: ByteArray, offset: Int): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        } catch (_: CharacterCodingException) {
            throw ProtocolException("invalid UTF-8 in close reason")
        }
    }

    // Read exactly one frame, validating RFC 6455 server-frame rules. Returns null on a clean stream end.
    fun readFrame(input: InputStream): Frame? {
        val b0 = input.read()
        if (b0 < 0) return null
        // RSV1-3 must be zero (no extension negotiated).
        if (b0 and 0x70 != 0) throw ProtocolException("RSV bits set")
        val fin = b0 and 0x80 != 0
        val opcode = b0 and 0x0F
        when (opcode) {
            OPCODE_CONTINUATION, OPCODE_TEXT, OPCODE_BINARY, OPCODE_CLOSE, OPCODE_PING, OPCODE_PONG -> {}
            else -> throw ProtocolException("reserved opcode $opcode")
        }
        val isControl = opcode and 0x8 != 0
        // Control frames must not be fragmented (RFC 6455 §5.5).
        if (isControl && !fin) throw ProtocolException("fragmented control frame")
        val b1 = input.readOrThrow()
        // Server→client frames MUST NOT be masked (§5.1) — a masked server frame is a protocol violation.
        if (b1 and 0x80 != 0) throw ProtocolException("masked server frame")
        var len = (b1 and 0x7F).toLong()
        if (len == 126L) {
            len = (input.readOrThrow().toLong() shl 8) or input.readOrThrow().toLong()
        } else if (len == 127L) {
            len = 0
            repeat(8) { len = (len shl 8) or input.readOrThrow().toLong() }
        }
        // Control-frame payloads are capped at 125 bytes (§5.5).
        if (isControl && len > 125) throw ProtocolException("control frame payload > 125")
        // Sanity cap: rejects a bogus/huge length (also guards Int overflow → negative → OOM) before allocating.
        if (len < 0 || len > MAX_FRAME_BYTES) throw IOException("frame length out of range: $len")
        val payload = readN(input, len.toInt())
        return Frame(fin, opcode, payload)
    }

    private fun readN(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) throw EOFException("stream closed mid-frame")
            off += r
        }
        return buf
    }

    private fun InputStream.readOrThrow(): Int {
        val v = read()
        if (v < 0) throw EOFException("stream closed mid-frame")
        return v
    }
}
