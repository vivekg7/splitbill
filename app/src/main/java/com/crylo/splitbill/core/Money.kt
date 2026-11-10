package com.crylo.splitbill.core

/**
 * Money is a [Long] count of the currency's minor unit (paise for ₹) everywhere in the
 * app. Floating point never touches an amount: 0.1 + 0.2 != 0.3 is a bug report waiting
 * to happen in an app whose whole job is making totals add up.
 *
 * The same two-decimal fixed-point parser also reads percentages (as basis points,
 * 12.5% = 1250) and shares (as hundredths, 1.5 shares = 150), so every split input is an
 * exact integer.
 */
object Money {
    /** ₹1,000 crore. Keeps `amount * weight` in split maths far below [Long.MAX_VALUE]. */
    const val MAX_AMOUNT = 1_000_000_000_000L

    /** Largest share weight (1,000 shares) or percentage (100%) a field may hold. */
    const val MAX_WEIGHT = 100_000L

    /**
     * Parses user input such as `1,234.5` or `₹ 99` into minor units. Returns null for
     * blank, negative, malformed, more than two decimals, or more than [max].
     */
    fun parse(text: String, max: Long = MAX_AMOUNT): Long? {
        if (text.contains('-')) return null
        val cleaned = text.trim().filterNot { it == ',' || it == ' ' || it == ' ' }
            .trimStart { !it.isDigit() && it != '.' }
        if (cleaned.isEmpty() || cleaned == ".") return null
        val parts = cleaned.split('.')
        if (parts.size > 2) return null
        val whole = parts[0]
        val frac = parts.getOrElse(1) { "" }
        if (!whole.all(Char::isDigit) || !frac.all(Char::isDigit) || frac.length > 2) return null
        if (whole.length > 13) return null
        val wholeValue = if (whole.isEmpty()) 0L else whole.toLong()
        val fracValue = frac.padEnd(2, '0').toLong()
        val value = wholeValue * 100 + fracValue
        return if (value > max) null else value
    }

    /** `123450` -> `1234.50`: for edit fields and the UPI `am` parameter. */
    fun plain(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /** `123450` -> `1234.5`, `1200` -> `12`: compact form for percentages and shares. */
    fun compact(minor: Long): String = plain(minor).trimEnd('0').trimEnd('.')

    /**
     * `12345678` with `₹` -> `₹1,23,456.78`. Rupee amounts use Indian lakh/crore digit
     * grouping because that is how Indian users read money; anything else uses groups of
     * three.
     */
    fun format(minor: Long, symbol: String): String {
        val abs = kotlin.math.abs(minor)
        val whole = (abs / 100).toString()
        val grouped = if (isRupee(symbol)) groupIndian(whole) else groupWestern(whole)
        val sign = if (minor < 0) "-" else ""
        return "$sign$symbol$grouped.${(abs % 100).toString().padStart(2, '0')}"
    }

    fun isRupee(symbol: String): Boolean =
        symbol.trim().let { it == "₹" || it.equals("INR", true) || it.equals("Rs", true) || it.equals("Rs.", true) }

    private fun groupWestern(digits: String): String =
        digits.reversed().chunked(3).joinToString(",").reversed()

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$last3"
    }
}
