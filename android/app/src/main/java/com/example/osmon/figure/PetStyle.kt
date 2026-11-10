// PetStyle.kt: Darstellung des Wesens – 2D (gezeichnet), 3D (WebGL) oder realistisch (Naturdoku-Bilder).
// Gegenstück: PetStyle.swift. Gilt für Home-Screen und Widget; Live-Hintergrund und Uhr bleiben 2D.
// Speicher: SharedPreferences "home", Schlüssel "pet.style" ("2d" | "3d" | "real"). Ältere Stände ohne diesen
// Schlüssel übernehmen die frühere 3D-Einstellung "pet.3d" (Standard an → 3D).

package com.example.osmon.figure

import android.content.Context
import com.example.osmon.PetSync

enum class PetStyle(val key: String, val title: String) {
    FLAT("2d", "2D"), THREE_D("3d", "3D"), REAL("real", "Realistisch");

    companion object {
        const val KEY = "pet.style"
        const val LEGACY_3D_KEY = "pet.3d"
        const val FAILED_3D_KEY = "pet.3d.failed"

        /** Gespeicherter Wert (null/leer = noch nicht gewählt) → Darstellung. legacy3D = frühere Einstellung "pet.3d". */
        fun resolve(raw: String?, legacy3D: Boolean?): PetStyle =
            entries.firstOrNull { it.key == raw } ?: if (legacy3D ?: true) THREE_D else FLAT

        fun current(ctx: Context): PetStyle {
            val p = PetSync.prefs(ctx)
            return resolve(p.getString(KEY, null), if (p.contains(LEGACY_3D_KEY)) p.getBoolean(LEGACY_3D_KEY, true) else null)
        }

        fun set(ctx: Context, style: PetStyle) {
            PetSync.prefs(ctx).edit()
                .putString(KEY, style.key)
                .putBoolean(LEGACY_3D_KEY, style == THREE_D)          // ältere Teile lesen noch "pet.3d"
                .apply { if (style == THREE_D) putBoolean(FAILED_3D_KEY, false) }
                .apply()
        }
    }
}
