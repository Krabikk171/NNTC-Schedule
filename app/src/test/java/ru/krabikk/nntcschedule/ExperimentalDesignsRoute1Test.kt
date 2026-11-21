package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ensures that the original Route and the new amber Route1 coexist. */
class ExperimentalDesignsRoute1Test {
    @Test
    fun amberRoute1IsNormalizedAndListedInDeveloperCatalog() {
        assertEquals(InterfaceStyle.ROUTE_1, InterfaceStyle.normalized(InterfaceStyle.ROUTE_1))
        assertTrue(ExperimentalDesigns.isExperimental(InterfaceStyle.ROUTE_1))
        assertTrue(ExperimentalDesigns.available.any {
            it.style == InterfaceStyle.ROUTE_1 && it.title.contains("МАРШРУТ 1")
        })
    }

    @Test
    fun originalRouteIsStillSelectable() {
        assertEquals(InterfaceStyle.ROUTE, InterfaceStyle.normalized(InterfaceStyle.ROUTE))
        assertTrue(ExperimentalDesigns.isExperimental(InterfaceStyle.ROUTE))
    }
}
