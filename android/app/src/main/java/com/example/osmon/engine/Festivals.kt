// Festivals.kt: Feste, Geburtstag und Schlüpftag (reines Kotlin). Gegenstück: Festivals.swift, gleiche Tests.
//
// Alles wird auf dem Gerät aus dem Datum berechnet. Vorrang (höchster zuerst):
//   Geburtstag (Tag + Monat, freiwillig) > Schlüpftag des Wesens (jährlich) > Meilenstein (7/30/100/200/500 Tage)
//   > Weihnachten 24.–26.12. > Silvester 31.12. > Neujahr 1.1. > Nikolaus 6.12. > St. Martin 11.11.
//   > Halloween 31.10. > Valentinstag 14.2. > Fasching (Weiberfastnacht bis Faschingsdienstag)
//   > Ostern (Karfreitag bis Ostermontag) > Muttertag (2. Sonntag im Mai) > Vatertag (Christi Himmelfahrt)
//   > Advent (1. Advent bis 23.12.)
// Tage: epochDay = Tage seit 1970-01-01 (lokales Datum).

package com.example.osmon.engine

enum class FestivalId { BIRTHDAY, HATCHDAY, MILESTONE, CHRISTMAS, SILVESTER, NEW_YEAR, NIKOLAUS, ST_MARTIN, HALLOWEEN,
    VALENTINE, CARNIVAL, EASTER, MOTHERS_DAY, FATHERS_DAY, ADVENT }

/** Zubehör, das die Figur trägt (Zeichnung: figure/PetAccessory.kt). */
enum class Accessory { PARTY_HAT, SANTA_HAT, BUNNY_EARS, WITCH_HAT, CROWN, FLOWERS, LANTERN, JESTER,
    BEANIE, CAP, BOW, TOP_HAT, WIZARD_HAT, PIRATE_HAT }   // ab BEANIE: Kleiderschrank (Wardrobe.kt)

/** Was über dem Hintergrund schwebt. */
enum class Decor { CONFETTI, SNOW, HEARTS, EGGS, PUMPKINS, STARS, LIGHTS, PETALS, LEAVES, BUTTERFLIES }

data class Festival(
    val id: FestivalId,
    val title: String,                 // Kartentitel
    val greeting: String,              // Satz des Wesens (Sprechblase)
    val accessory: Accessory?,
    val decor: Decor,
    val emoji: String,
    val song: Boolean = false          // „Zum Geburtstag viel Glück“ anbieten
)

data class Ymd(val y: Int, val m: Int, val d: Int)

object Festivals {
    val MILESTONES = listOf(7, 30, 100, 200, 500)

    // ---- Kalender (proleptisch gregorianisch, ohne Plattform-Bibliothek; Algorithmus nach H. Hinnant)

    fun epochDay(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = Math.floorDiv(yy, 400)
        val yoe = yy - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }

    fun ymd(epochDay: Long): Ymd {
        val z = epochDay + 719468
        val era = Math.floorDiv(z, 146097L)
        val doe = (z - era * 146097).toInt()
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Ymd((yoe + era * 400 + if (m <= 2) 1 else 0).toInt(), m, d)
    }

    /** 0 = Sonntag … 6 = Samstag (1970-01-01 war ein Donnerstag). */
    fun weekday(epochDay: Long): Int = Math.floorMod(epochDay + 4, 7L).toInt()

    /** Ostersonntag (Gauß/Anonymus-Algorithmus, gregorianisch). */
    fun easter(y: Int): Long {
        val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
        return epochDay(y, month, day)
    }

    /** 1. Advent: vierter Sonntag vor dem 25.12. */
    fun firstAdvent(y: Int): Long {
        val xmas = epochDay(y, 12, 25)
        val wd = weekday(xmas)
        val lastSundayBefore = xmas - (if (wd == 0) 7 else wd)
        return lastSundayBefore - 21
    }

    /** 2. Sonntag im Mai. */
    fun mothersDay(y: Int): Long {
        val may1 = epochDay(y, 5, 1)
        val firstSunday = may1 + Math.floorMod(7 - weekday(may1), 7)
        return firstSunday + 7
    }

    /**
     * Fest am Tag `day`. birthday = (Monat, Tag) oder null, hatchDay = Schlüpftag oder null,
     * pet = Name der Figur (für die Texte).
     */
    fun today(day: Long, birthday: Pair<Int, Int>?, hatchDay: Long?, pet: String): Festival? {
        val (y, m, d) = ymd(day)
        if (birthday != null && birthday.first == m && birthday.second == d) {
            return Festival(FestivalId.BIRTHDAY, "Alles Gute zum Geburtstag!", "Happy Birthday! Ich hab extra meinen Partyhut auf. 🎂",
                Accessory.PARTY_HAT, Decor.CONFETTI, "🎂", song = true)
        }
        if (hatchDay != null && day > hatchDay) {
            val h = ymd(hatchDay)
            if (h.m == m && h.d == d) {
                val years = y - h.y
                return Festival(FestivalId.HATCHDAY, "$pet hat Schlüpftag!",
                    "Heute vor ${if (years == 1) "einem Jahr" else "$years Jahren"} bin ich geschlüpft. Danke, dass du da bist!",
                    Accessory.CROWN, Decor.CONFETTI, "🥚", song = true)
            }
            val age = (day - hatchDay).toInt()
            if (age in MILESTONES) {
                return Festival(FestivalId.MILESTONE, "$age Tage zusammen!", "Schon $age Tage sind wir ein Team. Das feiern wir!",
                    Accessory.PARTY_HAT, Decor.STARS, "⭐")
            }
        }
        val e = easter(y)
        val adv = firstAdvent(y)
        return when {
            m == 12 && d in 24..26 -> Festival(FestivalId.CHRISTMAS, "Frohe Weihnachten!", "Frohe Weihnachten! Zeit für Familie, Kekse und wenig Handy.",
                Accessory.SANTA_HAT, Decor.SNOW, "🎄")
            m == 12 && d == 31 -> Festival(FestivalId.SILVESTER, "Guten Rutsch!", "Heute ist Silvester! Kommst du gut ins neue Jahr?",
                Accessory.PARTY_HAT, Decor.CONFETTI, "🎆")
            m == 1 && d == 1 -> Festival(FestivalId.NEW_YEAR, "Frohes neues Jahr!", "Frohes neues Jahr! Was nehmen wir uns zusammen vor?",
                Accessory.PARTY_HAT, Decor.CONFETTI, "🥂")
            m == 12 && d == 6 -> Festival(FestivalId.NIKOLAUS, "Nikolaustag", "Hast du deine Stiefel geputzt? Heute ist Nikolaus!",
                Accessory.SANTA_HAT, Decor.SNOW, "🎅")
            m == 11 && d == 11 -> Festival(FestivalId.ST_MARTIN, "St. Martin", "Laterne, Laterne! Heute teilen wir, wie St. Martin.",
                Accessory.LANTERN, Decor.LIGHTS, "🏮")
            m == 10 && d == 31 -> Festival(FestivalId.HALLOWEEN, "Halloween", "Buh! Hab ich dich erschreckt? Süßes oder Saures!",
                Accessory.WITCH_HAT, Decor.PUMPKINS, "🎃")
            m == 2 && d == 14 -> Festival(FestivalId.VALENTINE, "Valentinstag", "Heute ist Valentinstag. Sag jemandem, dass du ihn magst!",
                null, Decor.HEARTS, "💗")
            day in (e - 52)..(e - 47) -> Festival(FestivalId.CARNIVAL, "Fasching", "Helau und Alaaf! Ich hab mich verkleidet.",
                Accessory.JESTER, Decor.CONFETTI, "🎭")
            day in (e - 2)..(e + 1) -> Festival(FestivalId.EASTER, "Frohe Ostern!", "Frohe Ostern! Hast du schon Eier gefunden?",
                Accessory.BUNNY_EARS, Decor.EGGS, "🐣")
            day == mothersDay(y) -> Festival(FestivalId.MOTHERS_DAY, "Muttertag", "Heute ist Muttertag. Ein Anruf oder eine Umarmung freut sehr!",
                Accessory.FLOWERS, Decor.HEARTS, "💐")
            day == e + 39 -> Festival(FestivalId.FATHERS_DAY, "Vatertag", "Heute ist Vatertag. Zeit für einen Ausflug ohne Handy?",
                null, Decor.STARS, "🧢")
            day >= adv && m == 12 && d <= 23 || day >= adv && m == 11 -> Festival(FestivalId.ADVENT,
                "${adventNumber(day, adv)}. Advent", "Es ist Adventszeit. Ich liebe Kerzenlicht und Plätzchen!", null, Decor.SNOW, "🕯️")
            else -> null
        }
    }

    /** Welche Adventswoche (1–4) an diesem Tag läuft. */
    fun adventNumber(day: Long, firstAdvent: Long): Int = (((day - firstAdvent) / 7) + 1).toInt().coerceIn(1, 4)
}
