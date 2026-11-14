package com.scadenzeinvisibili.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class Item(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "⏳",
    /** Giorno di scadenza come LocalDate.toEpochDay() */
    val expiryDay: Long,
    /** Ogni quanti giorni si ripete (0 = una volta sola) */
    val cycleDays: Int = 0,
    val notes: String = "",
    val barcode: String = "",
    /** Nome del file della foto dentro filesDir (vuoto = nessuna foto) */
    val photo: String = "",
    val lastNotifiedDay: Long = -1L
) {
    fun daysLeft(today: LocalDate = LocalDate.now()): Long = expiryDay - today.toEpochDay()
}

object Store {
    private const val PREFS = "scadenze"
    private const val KEY = "items"

    fun load(context: Context): List<Item> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<Item>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(
                    Item(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        emoji = o.optString("emoji", "⏳"),
                        expiryDay = o.getLong("expiryDay"),
                        cycleDays = o.optInt("cycleDays", 0),
                        notes = o.optString("notes", ""),
                        barcode = o.optString("barcode", ""),
                        photo = o.optString("photo", ""),
                        lastNotifiedDay = o.optLong("lastNotifiedDay", -1L)
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, list: List<Item>) {
        val arr = JSONArray()
        for (item in list) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("name", item.name)
            o.put("emoji", item.emoji)
            o.put("expiryDay", item.expiryDay)
            o.put("cycleDays", item.cycleDays)
            o.put("notes", item.notes)
            o.put("barcode", item.barcode)
            o.put("photo", item.photo)
            o.put("lastNotifiedDay", item.lastNotifiedDay)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }
}
