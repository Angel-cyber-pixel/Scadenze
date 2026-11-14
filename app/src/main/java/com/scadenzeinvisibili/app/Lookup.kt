package com.scadenzeinvisibili.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private fun cleanField(s: String): String {
    val t = s.trim()
    return if (t.equals("null", ignoreCase = true)) "" else t
}

/**
 * Cerca il nome del prodotto su Open Food Facts (database pubblico, soprattutto alimenti e cosmetici).
 * onResult viene chiamato sul thread principale: nome trovato, oppure null.
 */
fun fetchProductName(code: String, onResult: (String?) -> Unit) {
    Thread {
        var result: String? = null
        try {
            val encoded = URLEncoder.encode(code, "UTF-8")
            val url = URL(
                "https://world.openfoodfacts.org/api/v2/product/$encoded.json" +
                    "?fields=product_name,product_name_it,brands"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "ScadenzeInvisibili/1.0 (Android)")
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.optInt("status", 0) == 1) {
                    val p = json.getJSONObject("product")
                    var name = cleanField(p.optString("product_name_it"))
                    if (name.isEmpty()) name = cleanField(p.optString("product_name"))
                    val brand = cleanField(p.optString("brands")).split(",").first().trim()
                    result = when {
                        name.isEmpty() -> null
                        brand.isEmpty() || name.contains(brand, ignoreCase = true) -> name
                        else -> "$name ($brand)"
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            result = null
        }
        val finalResult = result
        Handler(Looper.getMainLooper()).post { onResult(finalResult) }
    }.start()
}

private val MONTHS = mapOf(
    "GEN" to 1, "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4,
    "MAG" to 5, "MAY" to 5, "GIU" to 6, "JUN" to 6, "LUG" to 7, "JUL" to 7,
    "AGO" to 8, "AUG" to 8, "SET" to 9, "SEP" to 9, "OTT" to 10, "OCT" to 10,
    "NOV" to 11, "DIC" to 12, "DEC" to 12
)

private val RE_FULL = Regex("""(\d{1,2})\s*[/.\-]\s*(\d{1,2})\s*[/.\-]\s*(\d{4}|\d{2})""")
private val RE_MONTH_YEAR = Regex("""(?<![\d/.\-])(\d{1,2})\s*[/.\-]\s*(\d{4})""")
private val RE_NAMED = Regex(
    """(\d{1,2})?\s*\b(GEN|FEB|MAR|APR|MAG|GIU|LUG|AGO|SET|OTT|NOV|DIC|JAN|MAY|JUN|JUL|AUG|SEP|OCT|DEC)[A-Z]*\.?\s*(\d{4}|\d{2})"""
)

/**
 * Cerca date nel testo letto dalla confezione e restituisce la più lontana nel tempo
 * tra quelle plausibili (di solito la scadenza, mentre le altre sono produzione o lotto).
 */
fun parseExpiry(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
    val t = text.uppercase(Locale.ROOT)
    val found = mutableListOf<LocalDate>()

    fun add(day: Int?, month: Int, rawYear: Int) {
        try {
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            val date = if (day == null) YearMonth.of(year, month).atEndOfMonth()
            else LocalDate.of(year, month, day)
            found.add(date)
        } catch (e: Exception) {
            // data non valida: la ignoro
        }
    }

    for (m in RE_FULL.findAll(t)) {
        add(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }
    for (m in RE_MONTH_YEAR.findAll(t)) {
        add(null, m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }
    for (m in RE_NAMED.findAll(t)) {
        val month = MONTHS[m.groupValues[2]] ?: continue
        add(m.groupValues[1].toIntOrNull(), month, m.groupValues[3].toInt())
    }

    val plausible = found.filter {
        it.isAfter(today.minusYears(1)) && it.isBefore(today.plusYears(15))
    }
    return plausible.maxByOrNull { it.toEpochDay() }
}
