package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownGroupNamesTest {
    @Test fun initialCatalogIsUniqueAndIncludesLegacy() {
        val names = KnownGroupNames.names
        assertEquals(32, names.size)
        assertEquals(names.size, names.distinct().size)
        assertTrue("2РПУ-25-1" in names)
        assertTrue("26-1 ЭМ" in names)
    }
}
