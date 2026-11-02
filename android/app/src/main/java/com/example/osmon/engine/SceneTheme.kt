// SceneTheme.kt: Jahreszeiten und Fest-Dekoration im Hintergrund (reines Kotlin). Gegenstück: SceneTheme.swift.
//
// Jahreszeit (meteorologisch, Nordhalbkugel): Frühling März–Mai, Sommer Juni–August, Herbst September–November,
// Winter Dezember–Februar. Sie färbt das Bild leicht ein, legt kleine Pflanzen/Schnee an den Rand und bestimmt die
// schwebenden Teilchen, wenn kein Fest eigene mitbringt.
//
// Requisiten (Props) stehen links/rechts neben der Figur auf Höhe der Plattform, schweben im Himmel oder hängen
// als Girlande oben. x = Anteil der Breite (0..1), size in dp/pt, dy = Abstand nach oben über der Plattform (dp/pt).

package com.example.osmon.engine

enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

enum class PropKind {
    EMOJI,          // text = Emoji
    BUNTING,        // Wimpelkette oben
    LIGHT_CHAIN,    // Lichterkette oben
    LANTERNS,       // Laternen-Kette oben (St. Martin)
    HEART_GARLAND,  // Herz-Girlande oben
    FIREWORKS,      // Feuerwerk im Himmel (animiert)
    SNOW_GROUND,    // Schneehügel am unteren Rand der Szene
    SUN_GLOW,       // warmes Sonnenlicht oben rechts
    CANDLES         // Adventskerzen, text = Anzahl brennender Kerzen
}

data class Prop(val kind: PropKind, val x: Float = 0.5f, val size: Float = 48f, val dy: Float = 0f, val text: String = "")

/** Leichte Einfärbung des Hintergrunds (ARGB als Long, damit plattformneutral). */
data class SeasonTint(val argb: Long)

object SceneTheme {
    fun season(month: Int): Season = when (month) {
        3, 4, 5 -> Season.SPRING
        6, 7, 8 -> Season.SUMMER
        9, 10, 11 -> Season.AUTUMN
        else -> Season.WINTER
    }

    fun season(epochDay: Long): Season = season(Festivals.ymd(epochDay).m)

    fun title(s: Season) = when (s) {
        Season.SPRING -> "Frühling"; Season.SUMMER -> "Sommer"; Season.AUTUMN -> "Herbst"; Season.WINTER -> "Winter"
    }

    fun tint(s: Season) = when (s) {
        Season.SPRING -> SeasonTint(0x14FF8FC8)   // zartes Rosa
        Season.SUMMER -> SeasonTint(0x14FFC94D)   // warmes Gelb
        Season.AUTUMN -> SeasonTint(0x1FFF8A3D)   // Orange
        Season.WINTER -> SeasonTint(0x24A8D8FF)   // kühles Blau
    }

    /** Teilchen der Jahreszeit (wenn kein Fest eigene hat). */
    fun particles(s: Season): Decor = when (s) {
        Season.SPRING -> Decor.PETALS; Season.SUMMER -> Decor.BUTTERFLIES; Season.AUTUMN -> Decor.LEAVES; Season.WINTER -> Decor.SNOW
    }

    /** Kleine Deko der Jahreszeit ganz außen (x 0,06 / 0,94), damit Fest-Requisiten Platz haben. */
    fun seasonProps(s: Season): List<Prop> = when (s) {
        Season.SPRING -> listOf(Prop(PropKind.EMOJI, 0.06f, 30f, text = "🌷"), Prop(PropKind.EMOJI, 0.94f, 28f, text = "🌼"))
        Season.SUMMER -> listOf(Prop(PropKind.SUN_GLOW), Prop(PropKind.EMOJI, 0.94f, 34f, text = "🌻"))
        Season.AUTUMN -> listOf(Prop(PropKind.EMOJI, 0.06f, 30f, text = "🍂"), Prop(PropKind.EMOJI, 0.94f, 28f, text = "🍁"))
        Season.WINTER -> listOf(Prop(PropKind.SNOW_GROUND))
    }

    /** Requisiten zum Fest. adventCandles = 1..4 (nur Advent). */
    fun festivalProps(f: FestivalId, adventCandles: Int = 1): List<Prop> = when (f) {
        FestivalId.CHRISTMAS -> listOf(Prop(PropKind.EMOJI, 0.13f, 120f, text = "🎄"), Prop(PropKind.EMOJI, 0.84f, 52f, text = "🎁"),
            Prop(PropKind.EMOJI, 0.93f, 38f, text = "🎁"), Prop(PropKind.LIGHT_CHAIN))
        FestivalId.NIKOLAUS -> listOf(Prop(PropKind.EMOJI, 0.13f, 100f, text = "🎄"), Prop(PropKind.EMOJI, 0.87f, 50f, text = "🥾"),
            Prop(PropKind.LIGHT_CHAIN))
        FestivalId.ADVENT -> listOf(Prop(PropKind.EMOJI, 0.12f, 90f, text = "🌲"), Prop(PropKind.CANDLES, 0.86f, 30f, text = adventCandles.coerceIn(1, 4).toString()),
            Prop(PropKind.LIGHT_CHAIN))
        FestivalId.BIRTHDAY, FestivalId.HATCHDAY -> listOf(Prop(PropKind.EMOJI, 0.12f, 70f, dy = 170f, text = "🎈"),
            Prop(PropKind.EMOJI, 0.9f, 64f, dy = 200f, text = "🎈"), Prop(PropKind.EMOJI, 0.87f, 56f, text = "🎂"),
            Prop(PropKind.EMOJI, 0.14f, 44f, text = "🎁"), Prop(PropKind.BUNTING))
        FestivalId.MILESTONE -> listOf(Prop(PropKind.EMOJI, 0.12f, 64f, dy = 180f, text = "🎈"), Prop(PropKind.EMOJI, 0.9f, 60f, dy = 200f, text = "🎈"),
            Prop(PropKind.BUNTING))
        FestivalId.SILVESTER, FestivalId.NEW_YEAR -> listOf(Prop(PropKind.FIREWORKS), Prop(PropKind.EMOJI, 0.88f, 44f, text = "🥂"),
            Prop(PropKind.EMOJI, 0.12f, 44f, text = "🍀"))
        FestivalId.CARNIVAL -> listOf(Prop(PropKind.BUNTING), Prop(PropKind.EMOJI, 0.12f, 50f, text = "🎭"), Prop(PropKind.EMOJI, 0.88f, 48f, text = "🎊"))
        FestivalId.EASTER -> listOf(Prop(PropKind.EMOJI, 0.1f, 40f, text = "🥚"), Prop(PropKind.EMOJI, 0.17f, 34f, text = "🐣"),
            Prop(PropKind.EMOJI, 0.85f, 36f, text = "🥚"), Prop(PropKind.EMOJI, 0.91f, 48f, text = "🐰"))
        FestivalId.HALLOWEEN -> listOf(Prop(PropKind.EMOJI, 0.12f, 64f, text = "🎃"), Prop(PropKind.EMOJI, 0.89f, 50f, text = "🎃"),
            Prop(PropKind.EMOJI, 0.82f, 34f, dy = 240f, text = "🦇"), Prop(PropKind.EMOJI, 0.1f, 40f, dy = 280f, text = "🕸️"))
        FestivalId.ST_MARTIN -> listOf(Prop(PropKind.LANTERNS), Prop(PropKind.EMOJI, 0.88f, 44f, text = "🐴"))
        FestivalId.VALENTINE -> listOf(Prop(PropKind.HEART_GARLAND), Prop(PropKind.EMOJI, 0.88f, 46f, text = "🌹"))
        FestivalId.MOTHERS_DAY -> listOf(Prop(PropKind.HEART_GARLAND), Prop(PropKind.EMOJI, 0.88f, 54f, text = "💐"))
        FestivalId.FATHERS_DAY -> listOf(Prop(PropKind.BUNTING), Prop(PropKind.EMOJI, 0.88f, 46f, text = "🚲"))
    }

    /**
     * Alles für einen Tag: Fest-Requisiten zuerst, dann Jahreszeit (ohne Doppelungen am selben Rand:
     * eine Fest-Girlande oben verdrängt nichts, Bodenschnee bleibt). seasons = Jahreszeiten eingeschaltet.
     */
    fun props(festival: Festival?, season: Season, seasons: Boolean, adventCandles: Int = 1): List<Prop> {
        val fest = festival?.let { festivalProps(it.id, adventCandles) }.orEmpty()
        val sea = if (seasons) seasonProps(season) else emptyList()
        return fest + sea
    }

    /** Teilchen: Fest vor Jahreszeit; ohne beides keine. */
    fun particles(festival: Festival?, season: Season, seasons: Boolean): Decor? =
        festival?.decor ?: if (seasons) particles(season) else null
}
