package com.scadenzeinvisibili.app

/** Esempi da cui partire. Le durate sono indicative: si possono sempre modificare. */
data class Preset(
    val emoji: String,
    val name: String,
    val days: Int,
    val recurring: Boolean = true
)

val PRESETS = listOf(
    Preset("💧", "Filtro brocca dell'acqua", 30),
    Preset("🪥", "Spazzolino", 90),
    Preset("🔋", "Pile rilevatore di fumo", 365),
    Preset("🧽", "Spugna della cucina", 14),
    Preset("💊", "Medicina o collirio aperto", 30, recurring = false),
    Preset("🧴", "Crema solare aperta", 365, recurring = false),
    Preset("🩹", "Controllo kit pronto soccorso", 180),
    Preset("🧊", "Filtro del frigorifero", 180),
    Preset("🛡️", "Garanzia elettrodomestico", 730, recurring = false)
)

val EMOJIS = listOf("⏳", "💧", "🔋", "💊", "🧴", "🛡️", "🧽", "🪥", "🧊", "🔧", "🍼", "🐾")
