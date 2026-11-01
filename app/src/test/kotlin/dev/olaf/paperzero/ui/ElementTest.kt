package dev.olaf.paperzero.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElementTest {

    private fun el(symbol: String) = Element.all.first { it.symbol == symbol }

    @Test
    fun allOfThemInOrder() {
        assertEquals(118, Element.all.size)
        Element.all.forEachIndexed { i, e -> assertEquals(i + 1, e.z) }
        assertEquals("Og", Element.all.last().symbol)
    }

    @Test
    fun eachIsInItsPlace() {
        assertEquals(1 to 1, el("H").period to el("H").group)
        assertEquals(1 to 18, el("He").period to el("He").group)
        assertEquals(3 to 17, el("Cl").period to el("Cl").group)
        assertEquals(2 to 13, el("B").period to el("B").group)
        assertEquals(4 to 8, el("Fe").period to el("Fe").group)
        assertEquals(5 to 11, el("Ag").period to el("Ag").group)
        assertEquals(6 to 4, el("Hf").period to el("Hf").group)
        assertEquals(6 to 14, el("Pb").period to el("Pb").group)
        assertEquals(6 to 18, el("Rn").period to el("Rn").group)
        assertEquals(7 to 2, el("Ra").period to el("Ra").group)
        assertEquals(7 to 18, el("Og").period to el("Og").group)
        assertNull(el("La").group)
        assertNull(el("U").group)
    }

    @Test
    fun noTwoShareACell() {
        val placed = Element.all.filter { it.group != null }.map { it.period to it.group }
        assertEquals(placed.size, placed.toSet().size)
    }

    @Test
    fun massesAsOnTheDataSheet() {
        assertEquals("35.5", el("Cl").mass)
        assertEquals("64", el("Cu").mass)
        assertNull(el("Tc").mass)
    }
}
