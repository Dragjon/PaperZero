package dev.olaf.paperzero.ui

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The calculator's arithmetic, as a school calculator does it: × and ÷ before + and −, powers
 * before those (right to left, and before a minus sign: −2² is −4), ² after a number, "×10ˣ" for
 * standard form, π, Ans, and sin, cos, tan in degrees, log (base 10), ln and √. A number before a
 * bracket, π, Ans or a function multiplies it (2π, 3(4+1)); brackets left open at the end close
 * themselves.
 */
object Calc {
    /** [text]'s value, or null if it isn't a whole sum (or comes to something that isn't a number). */
    fun evaluate(text: String, ans: Double = 0.0): Double? = runCatching {
        val p = Parser(text, ans)
        val v = p.expression()
        if (!p.done()) null else v.takeIf { it.isFinite() }
    }.getOrNull()

    /** [v] as a calculator shows it: ten figures at most, standard form when it's very big or small. */
    fun format(v: Double): String {
        if (v == 0.0) return "0"
        val a = abs(v)
        if (a >= 1e10 || a < 1e-6) {
            val exp = floor(log10(a)).toInt()
            val mantissa = v / 10.0.pow(exp)
            return trim(BigDecimal(mantissa).round(MathContext(7)).toPlainString()) + "×10^" + exp
        }
        return trim(BigDecimal(v).round(MathContext(10)).toPlainString())
    }

    private fun trim(s: String) = if ('.' in s) s.trimEnd('0').trimEnd('.') else s

    private class Parser(private val s: String, private val ans: Double) {
        private var i = 0

        fun done() = i >= s.length

        private fun peek(word: String) = s.startsWith(word, i)

        private fun eat(word: String): Boolean = peek(word).also { if (it) i += word.length }

        fun expression(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat("+") -> v + term()
                    eat("−") || eat("-") -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = unary()
            while (true) {
                v = when {
                    eat("×") || eat("*") -> v * unary()
                    eat("÷") || eat("/") -> v / unary()
                    // A number straight before a bracket, π, Ans or a function multiplies it.
                    startsPrimary() -> v * power()
                    else -> return v
                }
            }
        }

        private fun startsPrimary() = !done() && (peek("(") || peek("π") || peek("Ans") || FUNCTIONS.any { peek(it) } || peek("√") || s[i].isDigit() || s[i] == '.')

        private fun unary(): Double = when {
            eat("−") || eat("-") -> -unary()
            eat("+") -> unary()
            else -> power()
        }

        private fun power(): Double {
            val base = postfix()
            return if (eat("^")) base.pow(unary()) else base
        }

        private fun postfix(): Double {
            var v = primary()
            while (eat("²")) v *= v
            return v
        }

        private fun primary(): Double {
            if (eat("(")) return closed(expression())
            if (eat("π")) return Math.PI
            if (eat("Ans")) return ans
            if (eat("√")) return sqrt(if (eat("(")) closed(expression()) else postfix())
            for (f in FUNCTIONS) {
                if (eat(f)) {
                    val x = closed(expression())
                    return when (f) {
                        "sin(" -> sin(Math.toRadians(x))
                        "cos(" -> cos(Math.toRadians(x))
                        "tan(" -> tan(Math.toRadians(x))
                        "log(" -> log10(x)
                        else -> ln(x)
                    }
                }
            }
            return number()
        }

        /** After a bracket's inside: its closing bracket, if it's there (at the end, it needn't be). */
        private fun closed(v: Double): Double {
            if (!eat(")") && !done()) error("unclosed")
            return v
        }

        private fun number(): Double {
            val start = i
            while (!done() && (s[i].isDigit() || s[i] == '.')) i++
            if (i == start) error("number expected at $i")
            var v = s.substring(start, i).toDouble()
            if (eat("×10^")) {
                val negative = eat("−") || eat("-")
                val e0 = i
                while (!done() && s[i].isDigit()) i++
                if (i == e0) error("exponent expected")
                val e = s.substring(e0, i).toInt()
                v *= 10.0.pow(if (negative) -e else e)
            }
            return v
        }

        companion object {
            val FUNCTIONS = listOf("sin(", "cos(", "tan(", "log(", "ln(")
        }
    }
}
