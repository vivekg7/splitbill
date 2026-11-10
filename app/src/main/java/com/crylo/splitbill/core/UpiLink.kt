package com.crylo.splitbill.core

import java.net.URLEncoder

/**
 * Builds the standard UPI deep link (NPCI "UPI Linking Specification") that every UPI
 * app on the phone registers for, so the app can hand off a payment without talking to
 * any network itself.
 */
object UpiLink {
    private val vpaPattern = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,63}$")

    fun isValidVpa(vpa: String): Boolean = vpaPattern.matches(vpa.trim())

    fun build(vpa: String, payeeName: String, amount: Long, note: String): String {
        val params = listOf(
            "pa" to vpa.trim(),
            "pn" to payeeName,
            "am" to Money.plain(amount),
            "cu" to "INR",
            "tn" to note,
        )
        return "upi://pay?" + params.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
    }

    // URLEncoder does form encoding (space as '+'); several UPI apps show a literal '+'
    // in the payee name or note, so spaces are percent-encoded instead. '@' is legal in a
    // query and is left readable, since some apps match the VPA without decoding it.
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("%40", "@")
}
