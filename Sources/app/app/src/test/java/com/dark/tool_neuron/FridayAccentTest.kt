package com.dark.tool_neuron

import com.dark.tool_neuron.ui.theme.FridayAccent
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Pure JVM tests for FridayAccent.fromId (FRI-582 P10): id -> enum mapping and
 * unknown/blank-id fallback to the SUN default. No Context/HXS involved —
 * ThemeController's persisted-accent round-trip itself needs AppPreferences
 * (HXS-backed) and is instrumentation-only.
 */
class FridayAccentTest {

    @Test
    fun `fromId resolves each known accent id`() {
        assertEquals(FridayAccent.SUN, FridayAccent.fromId("sun"))
        assertEquals(FridayAccent.EMBER, FridayAccent.fromId("ember"))
        assertEquals(FridayAccent.VIOLET, FridayAccent.fromId("violet"))
        assertEquals(FridayAccent.MINT, FridayAccent.fromId("mint"))
    }

    @Test
    fun `fromId falls back to SUN for unknown id`() {
        assertEquals(FridayAccent.SUN, FridayAccent.fromId("not-a-real-accent"))
    }

    @Test
    fun `fromId falls back to SUN for blank id`() {
        assertEquals(FridayAccent.SUN, FridayAccent.fromId(""))
    }
}
