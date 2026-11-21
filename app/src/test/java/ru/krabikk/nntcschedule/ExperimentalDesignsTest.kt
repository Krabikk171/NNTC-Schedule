package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalDesignsTest {
    @Test fun allEighteenExperimentalDesignsAreRegisteredAndUnique() {
        val expected = setOf(
            InterfaceStyle.ROUTE, InterfaceStyle.ROUTE_1,
            InterfaceStyle.LAYERS_1, InterfaceStyle.ROUTE_2,
            InterfaceStyle.FOCUS_1, InterfaceStyle.ORBIT_1,
            InterfaceStyle.LAYERS_2, InterfaceStyle.HOLOGRAM_1,
            InterfaceStyle.ORBIT_COSMOS, InterfaceStyle.STREAM_1,
            InterfaceStyle.COMPASS_1, InterfaceStyle.FOCUS_CORAL,
            InterfaceStyle.ORBIT_LIGHT, InterfaceStyle.LAYERS_PRISM,
            InterfaceStyle.ROUTE_CORAL, InterfaceStyle.ORBIT_ARC,
            InterfaceStyle.LAYERS_SPECTRUM, InterfaceStyle.ROUTE_CYAN
        )
        assertEquals(expected, ExperimentalDesigns.available.map { it.style }.toSet())
        assertEquals(18, ExperimentalDesigns.available.size)
        expected.forEach { style ->
            assertTrue(ExperimentalDesigns.isExperimental(style))
            assertEquals(style, InterfaceStyle.normalized(style))
        }
    }

    @Test fun stableStylesRemainOutsideDeveloperCatalog() {
        listOf(
            InterfaceStyle.MINIMAL, InterfaceStyle.AURORA,
            InterfaceStyle.NEON, InterfaceStyle.LAVENDER,
            InterfaceStyle.OCEAN
        ).forEach { style -> assertFalse(ExperimentalDesigns.isExperimental(style)) }
    }
}
