// Wardrobe.kt: Kleiderschrank (reines Kotlin). Gegenstück: Wardrobe.swift (gleiche Liste, gleiche Tests).
//
// Mützen und Brillen werden durch gute Tage freigeschaltet (Höchstwert der Wachstumstage, geht also durch
// einen Rückfall nicht wieder verloren). Premium-Teile gibt es nur mit dem Einmalkauf. Pro Platz (Kopf, Augen)
// trägt das Wesen höchstens ein Teil. An Festtagen hat das Fest-Zubehör auf dem Kopf Vorrang.

package com.example.osmon.engine

enum class Glasses { ROUND, SUN, HEART, STAR, MONOCLE }
enum class Slot(val title: String) { HAT("Mützen & Hüte"), GLASSES("Brillen") }

data class WearItem(
    val id: String, val emoji: String, val title: String, val slot: Slot, val goodDays: Int,
    val premium: Boolean = false,
    val hat: Accessory? = null, val glasses: Glasses? = null
)

/** Was das Wesen trägt (null = nichts auf diesem Platz). */
data class Outfit(val hat: Accessory? = null, val glasses: Glasses? = null) {
    val isEmpty get() = hat == null && glasses == null
}

object Wardrobe {
    val ALL = listOf(
        WearItem("beanie", "🧶", "Wollmütze", Slot.HAT, 2, hat = Accessory.BEANIE),
        WearItem("round", "👓", "Runde Brille", Slot.GLASSES, 5, glasses = Glasses.ROUND),
        WearItem("cap", "🧢", "Kappe", Slot.HAT, 10, hat = Accessory.CAP),
        WearItem("sun", "🕶️", "Sonnenbrille", Slot.GLASSES, 16, glasses = Glasses.SUN),
        WearItem("bow", "🎀", "Schleife", Slot.HAT, 24, hat = Accessory.BOW),
        WearItem("heart", "💕", "Herzbrille", Slot.GLASSES, 34, glasses = Glasses.HEART),
        WearItem("tophat", "🎩", "Zylinder", Slot.HAT, 46, hat = Accessory.TOP_HAT),
        WearItem("star", "⭐", "Sternbrille", Slot.GLASSES, 60, glasses = Glasses.STAR),
        // Premium (Einmalkauf): sofort verfügbar, keine guten Tage nötig
        WearItem("wizard", "🧙", "Zauberhut", Slot.HAT, 0, premium = true, hat = Accessory.WIZARD_HAT),
        WearItem("pirate", "🏴‍☠️", "Piratenhut", Slot.HAT, 0, premium = true, hat = Accessory.PIRATE_HAT),
        WearItem("monocle", "🧐", "Monokel", Slot.GLASSES, 0, premium = true, glasses = Glasses.MONOCLE)
    )

    fun byId(id: String?) = ALL.firstOrNull { it.id == id }

    fun isUnlocked(item: WearItem, maxGoodDays: Int, premium: Boolean) =
        if (item.premium) premium else maxGoodDays >= item.goodDays

    /** Fehlende gute Tage bis zum Teil (0 = frei, null = nur mit Premium). */
    fun missing(item: WearItem, maxGoodDays: Int, premium: Boolean): Int? = when {
        item.premium -> if (premium) 0 else null
        else -> (item.goodDays - maxGoodDays).coerceAtLeast(0)
    }

    /** Neu freigeschaltete Teile (für „Neu im Kleiderschrank“), bereits bekannte ausgenommen. */
    fun newlyUnlocked(maxGoodDays: Int, premium: Boolean, known: Set<String>) =
        ALL.filter { it.id !in known && isUnlocked(it, maxGoodDays, premium) }

    /** Nächstes Teil, das durch gute Tage kommt (für den Hinweis „Noch 3 gute Tage bis zur Kappe“). */
    fun next(maxGoodDays: Int) = ALL.filter { !it.premium && it.goodDays > maxGoodDays }.minByOrNull { it.goodDays }

    /** Getragene Teile → Outfit. Gesperrte (z. B. Premium nach Rückerstattung) und doppelte Plätze fallen weg. */
    fun outfit(equipped: Collection<String>, maxGoodDays: Int, premium: Boolean): Outfit {
        val items = equipped.mapNotNull { byId(it) }.filter { isUnlocked(it, maxGoodDays, premium) }
        return Outfit(
            hat = items.lastOrNull { it.slot == Slot.HAT }?.hat,
            glasses = items.lastOrNull { it.slot == Slot.GLASSES }?.glasses
        )
    }

    /** Anziehen/Ausziehen: gleiches Teil → aus, sonst ersetzt es das Teil auf demselben Platz. */
    fun toggle(equipped: List<String>, id: String): List<String> {
        val item = byId(id) ?: return equipped
        if (id in equipped) return equipped - id
        return equipped.filter { byId(it)?.slot != item.slot } + id
    }

    /** Kopf: Fest-Zubehör hat Vorrang, sonst die Mütze aus dem Schrank. */
    fun hat(festival: Accessory?, outfit: Outfit): Accessory? = festival ?: outfit.hat
}
