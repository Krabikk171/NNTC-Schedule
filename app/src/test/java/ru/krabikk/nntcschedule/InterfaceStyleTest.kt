package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceStyleTest {
    @Test fun allStylesSurvivePersistenceRoundTrip() {
        listOf(
            InterfaceStyle.MINIMAL,
            InterfaceStyle.AURORA,
            InterfaceStyle.NEON,
            InterfaceStyle.LAVENDER,
            InterfaceStyle.OCEAN,
            InterfaceStyle.ROUTE, InterfaceStyle.ROUTE_1,
            InterfaceStyle.LAYERS_1, InterfaceStyle.ROUTE_2,
            InterfaceStyle.FOCUS_1, InterfaceStyle.ORBIT_1,
            InterfaceStyle.LAYERS_2, InterfaceStyle.HOLOGRAM_1
        ).forEach { style ->
            assertEquals(style, InterfaceStyle.normalized(style))
        }
    }

    @Test fun unknownOrMissingStyleFallsBackToMinimal() {
        assertEquals(InterfaceStyle.MINIMAL, InterfaceStyle.normalized(null))
        assertEquals(InterfaceStyle.MINIMAL, InterfaceStyle.normalized("random"))
        assertEquals(InterfaceStyle.MINIMAL, InterfaceStyle.normalized(""))
    }
}
