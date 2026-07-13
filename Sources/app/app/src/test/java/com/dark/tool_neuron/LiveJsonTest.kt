package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.LiveJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The Live codec's pure JSON layer (org.json is stubbed in JVM tests): escaping is correct and round-trips parse.
class LiveJsonTest {

    @Test
    fun escapesControlAndQuotes() {
        val out = LiveJson.escape("a\"b\\c\nd\te")
        assertTrue(out.contains("\\\""))
        assertTrue(out.contains("\\\\"))
        assertTrue(out.contains("\\n"))
        assertTrue(out.contains("\\t"))
    }

    @Test
    fun writeThenParseRoundTrips() {
        val json = LiveJson.write(
            LiveJson.obj(
                "setup" to LiveJson.obj(
                    "model" to LiveJson.str("models/gemini-2.0-flash-live-001"),
                    "flag" to LiveJson.bool(true),
                    "list" to LiveJson.arr(LiveJson.str("AUDIO")),
                )
            )
        )
        val root = LiveJson.parse(json) as LiveJson.JsonValue.Obj
        val setup = root.obj("setup")!!
        assertEquals("models/gemini-2.0-flash-live-001", setup.str("model"))
        assertEquals(true, setup.bool("flag"))
        assertEquals(1, setup.arr("list")!!.items.size)
    }

    @Test
    fun parseHandlesNestedServerFrame() {
        val frame = """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"data":"AAAA"}}]},"turnComplete":true}}"""
        val root = LiveJson.parse(frame) as LiveJson.JsonValue.Obj
        val content = root.obj("serverContent")!!
        assertEquals(true, content.bool("turnComplete"))
        val part = content.obj("modelTurn")!!.arr("parts")!!.items[0] as LiveJson.JsonValue.Obj
        assertEquals("AAAA", part.obj("inlineData")!!.str("data"))
    }

    @Test
    fun parseTolerantOfWhitespace() {
        val root = LiveJson.parse("  {  \"a\" : \"b\" }  ") as LiveJson.JsonValue.Obj
        assertEquals("b", root.str("a"))
    }
}
