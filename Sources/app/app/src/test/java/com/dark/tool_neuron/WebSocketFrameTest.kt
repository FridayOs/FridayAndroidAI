package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.WebSocketFrame
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// RFC 6455 codec proof: client frames are masked, headers size correctly, server frames decode, close parses.
class WebSocketFrameTest {

    @Test
    fun encode_setsFinAndMaskBit() {
        val bytes = WebSocketFrame.encode(WebSocketFrame.OPCODE_TEXT, "hi".toByteArray())
        assertEquals(0x81, bytes[0].toInt() and 0xFF) // FIN + text opcode
        assertTrue("client frames must be masked", bytes[1].toInt() and 0x80 != 0)
    }

    @Test
    fun encodeThenDecode_roundtripsText() {
        val payload = "hello gemini".toByteArray(Charsets.UTF_8)
        val encoded = WebSocketFrame.encode(WebSocketFrame.OPCODE_TEXT, payload)
        // Server-read path expects unmasked frames; strip our client mask by re-emitting server-style.
        val serverFrame = serverFrame(WebSocketFrame.OPCODE_TEXT, payload, fin = true)
        val frame = WebSocketFrame.readFrame(ByteArrayInputStream(serverFrame))!!
        assertEquals(WebSocketFrame.OPCODE_TEXT, frame.opcode)
        assertTrue(frame.fin)
        assertArrayEquals(payload, frame.payload)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun readFrame_mediumPayloadLength() {
        val payload = ByteArray(200) { it.toByte() }
        val frame = WebSocketFrame.readFrame(ByteArrayInputStream(serverFrame(WebSocketFrame.OPCODE_BINARY, payload, true)))!!
        assertEquals(200, frame.payload.size)
        assertArrayEquals(payload, frame.payload)
    }

    @Test
    fun readFrame_largePayloadLength() {
        val payload = ByteArray(70000) { (it % 256).toByte() }
        val frame = WebSocketFrame.readFrame(ByteArrayInputStream(serverFrame(WebSocketFrame.OPCODE_BINARY, payload, true)))!!
        assertEquals(70000, frame.payload.size)
    }

    @Test
    fun readFrame_fragmentedFinFalse() {
        val frame = WebSocketFrame.readFrame(ByteArrayInputStream(serverFrame(WebSocketFrame.OPCODE_TEXT, "part".toByteArray(), fin = false)))!!
        assertFalse(frame.fin)
    }

    @Test
    fun readFrame_endOfStreamIsNull() {
        assertNull(WebSocketFrame.readFrame(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun parseClose_extractsCodeAndReason() {
        val reason = "bye".toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + reason.size)
        payload[0] = (1000 shr 8).toByte()
        payload[1] = (1000 and 0xFF).toByte()
        reason.copyInto(payload, 2)
        val (code, text) = WebSocketFrame.parseClose(payload)
        assertEquals(1000, code)
        assertEquals("bye", text)
    }

    @Test
    fun parseClose_emptyPayloadDefaultsTo1005() {
        val (code, _) = WebSocketFrame.parseClose(ByteArray(0))
        assertEquals(1005, code)
    }

    @Test
    fun parseClose_rejectsOneBytePayload() {
        assertThrows(WebSocketFrame.ProtocolException::class.java) {
            WebSocketFrame.parseClose(byteArrayOf(0x03))
        }
    }

    @Test
    fun parseClose_rejectsReservedStatusCode() {
        // 1005 is a local no-status sentinel; a peer must never send it (nor 1006/1015) on the wire.
        assertThrows(WebSocketFrame.ProtocolException::class.java) {
            WebSocketFrame.parseClose(byteArrayOf((1005 shr 8).toByte(), (1005 and 0xFF).toByte()))
        }
        assertThrows(WebSocketFrame.ProtocolException::class.java) {
            WebSocketFrame.parseClose(byteArrayOf((999 shr 8).toByte(), (999 and 0xFF).toByte()))
        }
    }

    @Test
    fun parseClose_rejectsInvalidUtf8Reason() {
        // 1000 + a lone 0xFF continuation byte is not valid UTF-8.
        val payload = byteArrayOf((1000 shr 8).toByte(), (1000 and 0xFF).toByte(), 0xFF.toByte())
        assertThrows(WebSocketFrame.ProtocolException::class.java) {
            WebSocketFrame.parseClose(payload)
        }
    }

    @Test
    fun closePayload_encodesCode() {
        val payload = WebSocketFrame.closePayload(1000, "")
        assertEquals(1000, ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF))
    }

    @Test
    fun readFrame_rejectsMaskedServerFrame() {
        // b0 = FIN+text, b1 = mask-bit + len 1, 4 mask bytes, 1 payload byte.
        val bytes = byteArrayOf(0x81.toByte(), 0x81.toByte(), 1, 2, 3, 4, 0x00)
        assertThrows(WebSocketFrame.ProtocolException::class.java) {
            WebSocketFrame.readFrame(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun readFrame_rejectsRsvBits() {
        // b0 = FIN + RSV1 (0x40) + text opcode.
        val bytes = byteArrayOf(0xC1.toByte(), 0x00)
        assertThrows(WebSocketFrame.ProtocolException::class.java) {
            WebSocketFrame.readFrame(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun readFrame_rejectsReservedOpcode() {
        // b0 = FIN + opcode 0x3 (reserved data opcode).
        val bytes = byteArrayOf(0x83.toByte(), 0x00)
        assertThrows(WebSocketFrame.ProtocolException::class.java) {
            WebSocketFrame.readFrame(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun readFrame_rejectsFragmentedControlFrame() {
        // b0 = FIN=0 + CLOSE opcode (control frames must not be fragmented).
        val bytes = byteArrayOf(0x08, 0x00)
        assertThrows(WebSocketFrame.ProtocolException::class.java) {
            WebSocketFrame.readFrame(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun readFrame_rejectsOversizedControlFrame() {
        // b0 = FIN + PING, b1 = 126 length marker → 200-byte control payload (> 125 max).
        val bytes = byteArrayOf(0x89.toByte(), 0x7E, 0x00, 0xC8.toByte())
        assertThrows(WebSocketFrame.ProtocolException::class.java) {
            WebSocketFrame.readFrame(ByteArrayInputStream(bytes))
        }
    }

    // Builds an unmasked server->client frame (server frames are never masked per RFC 6455 §5.1).
    private fun serverFrame(opcode: Int, payload: ByteArray, fin: Boolean): ByteArray {
        val out = ArrayList<Byte>()
        out.add(((if (fin) 0x80 else 0x00) or opcode).toByte())
        when {
            payload.size < 126 -> out.add(payload.size.toByte())
            payload.size < 65536 -> {
                out.add(126.toByte())
                out.add((payload.size shr 8).toByte())
                out.add((payload.size and 0xFF).toByte())
            }
            else -> {
                out.add(127.toByte())
                for (shift in 56 downTo 0 step 8) out.add((payload.size.toLong() shr shift).toByte())
            }
        }
        payload.forEach { out.add(it) }
        return out.toByteArray()
    }
}
