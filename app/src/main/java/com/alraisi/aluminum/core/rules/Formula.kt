package com.alraisi.aluminum.core.rules

/**
 * محرك معادلات بسيط وآمن يتيح لصاحب الورشة تعديل قواعد القص لاحقاً
 * دون إعادة بناء التطبيق. المتغيرات تُمرر كخريطة.
 * العمليات: + - * / ^ والأقواس، والدوال:
 * min max ceil floor round abs sqrt sin cos asin pow
 */
object Formula {

    private enum class TT { NUM, ID, FUNC, OP, LP, RP, COMMA }
    private data class Tok(val type: TT, val text: String)

    private fun tokenize(src: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val j = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    out += Tok(TT.NUM, src.substring(j, i))
                }
                c.isLetter() || c == '_' -> {
                    val j = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    val id = src.substring(j, i)
                    var k = i
                    while (k < src.length && src[k].isWhitespace()) k++
                    if (k < src.length && src[k] == '(') { out += Tok(TT.FUNC, id); i = k + 1 }
                    else out += Tok(TT.ID, id)
                }
                c == '(' -> { out += Tok(TT.LP, "("); i++ }
                c == ')' -> { out += Tok(TT.RP, ")"); i++ }
                c == ',' -> { out += Tok(TT.COMMA, ","); i++ }
                else -> { out += Tok(TT.OP, c.toString()); i++ }
            }
        }
        return out
    }

    fun eval(expr: String, vars: Map<String, Double>): Double {
        if (expr.isBlank()) return 0.0
        val toks = tokenize(expr)
        var pos = 0
        fun peek(): Tok? = toks.getOrNull(pos)
        fun next(): Tok? = if (pos < toks.size) toks[pos++] else null

        fun parseExpr(): Double {
            var v = parseTerm()
            while (peek()?.let { it.type == TT.OP && (it.text == "+" || it.text == "-") } == true) {
                val op = next()!!.text
                val r = parseTerm()
                v = if (op == "+") v + r else v - r
            }
            return v
        }
        fun parseTerm(): Double {
            var v = parseUnary()
            while (peek()?.let { it.type == TT.OP && (it.text == "*" || it.text == "/") } == true) {
                val op = next()!!.text
                val r = parseUnary()
                v = if (op == "*") v * r else v / r
            }
            return v
        }
        fun parseUnary(): Double {
            if (peek()?.let { it.type == TT.OP && it.text == "-" } == true) { next(); return -parseUnary() }
            if (peek()?.let { it.type == TT.OP && it.text == "+" } == true) { next(); return parseUnary() }
            return parsePow()
        }
        fun parsePow(): Double {
            val base = parseAtom()
            if (peek()?.let { it.type == TT.OP && it.text == "^" } == true) {
                next(); return Math.pow(base, parseUnary())
            }
            return base
        }
        fun parseAtom(): Double {
            val t = next() ?: throw IllegalArgumentException("صيغة غير مكتملة: $expr")
            return when (t.type) {
                TT.NUM -> t.text.toDoubleOrNull() ?: 0.0
                TT.ID -> vars[t.text] ?: 0.0
                TT.LP -> { val v = parseExpr(); next(); v }
                TT.FUNC -> {
                    val args = mutableListOf<Double>()
                    if (peek()?.type != TT.RP) {
                        args += parseExpr()
                        while (peek()?.type == TT.COMMA) { next(); args += parseExpr() }
                    }
                    next() // )
                    when (t.text) {
                        "min" -> args.minOrNull() ?: 0.0
                        "max" -> args.maxOrNull() ?: 0.0
                        "ceil" -> Math.ceil(args.getOrElse(0) { 0.0 })
                        "floor" -> Math.floor(args.getOrElse(0) { 0.0 })
                        "round" -> Math.round(args.getOrElse(0) { 0.0 }).toDouble()
                        "abs" -> Math.abs(args.getOrElse(0) { 0.0 })
                        "sqrt" -> Math.sqrt(args.getOrElse(0) { 0.0 })
                        "sin" -> Math.sin(Math.toRadians(args.getOrElse(0) { 0.0 }))
                        "cos" -> Math.cos(Math.toRadians(args.getOrElse(0) { 0.0 }))
                        "asin" -> Math.toDegrees(Math.asin(args.getOrElse(0) { 0.0 }))
                        "pow" -> Math.pow(args.getOrElse(0) { 0.0 }, args.getOrElse(1) { 0.0 })
                        else -> 0.0
                    }
                }
                else -> throw IllegalArgumentException("رمز غير متوقع: ${t.text}")
            }
        }

        val result = parseExpr()
        return if (result.isNaN() || result.isInfinite()) 0.0 else result
    }

    fun eval(expr: String, vararg pairs: Pair<String, Double>): Double =
        eval(expr, pairs.toMap())
}
