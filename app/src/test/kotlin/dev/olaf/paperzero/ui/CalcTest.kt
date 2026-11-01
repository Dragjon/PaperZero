package dev.olaf.paperzero.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {

    private fun eq(expected: Double, text: String, ans: Double = 0.0) = assertEquals(expected, Calc.evaluate(text, ans)!!, 1e-9)

    @Test
    fun timesAndDivideBeforePlusAndMinus() {
        eq(14.0, "2+3×4")
        eq(1.0, "10÷5−1")
        eq(20.0, "(2+3)×4")
    }

    @Test
    fun powersAndSquares() {
        eq(-4.0, "−2²")
        eq(4.0, "(−2)²")
        eq(512.0, "2^3^2")
        eq(0.125, "2^−3")
        eq(9.0, "3²")
    }

    @Test
    fun standardForm() {
        eq(3e8, "3×10^8")
        eq(1.6e-19, "1.6×10^−19")
        eq(9.6e-19, "6×1.6×10^−19")
    }

    @Test
    fun functionsAreInDegrees() {
        eq(0.5, "sin(30)")
        eq(0.5, "cos(60)")
        eq(1.0, "tan(45)")
        eq(2.0, "log(100)")
        eq(1.0, "ln(2.718281828459045)")
        eq(3.0, "√9")
        eq(5.0, "√(9+16)")
    }

    @Test
    fun aNumberBeforeABracketOrPiMultipliesIt() {
        eq(2 * Math.PI, "2π")
        eq(15.0, "3(4+1)")
        eq(1.0, "2sin(30)")
    }

    @Test
    fun ansAndOpenBrackets() {
        eq(14.0, "Ans×2", ans = 7.0)
        eq(6.0, "2×(1+2")
    }

    @Test
    fun nonsenseIsNull() {
        assertNull(Calc.evaluate("2+"))
        assertNull(Calc.evaluate("×3"))
        assertNull(Calc.evaluate("1÷0"))
        assertNull(Calc.evaluate(""))
    }

    @Test
    fun formatting() {
        assertEquals("0.3333333333", Calc.format(1.0 / 3))
        assertEquals("14", Calc.format(14.0))
        assertEquals("300000000", Calc.format(3e8))
        assertEquals("3×10^10", Calc.format(3e10))
        assertEquals("1.6×10^-19", Calc.format(1.6e-19))
        assertEquals("-2.5", Calc.format(-2.5))
    }
}
