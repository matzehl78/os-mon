// WardrobeUI.kt: Kleiderschrank – Speicher und Ansicht. Gegenstück: WardrobeUI.swift. Regeln: engine/Wardrobe.kt.
// Getragenes gilt überall: App (2D und 3D), Widget, Hintergrundbild, Uhr.

package com.example.osmon.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.glance.appwidget.updateAll
import com.example.osmon.PetSync
import kotlinx.coroutines.launch
import com.example.osmon.engine.Mood
import com.example.osmon.engine.Outfit
import com.example.osmon.engine.Sfx
import com.example.osmon.engine.Slot
import com.example.osmon.engine.Wardrobe
import com.example.osmon.engine.WearItem
import com.example.osmon.figure.PetFigureCanvas
import com.example.osmon.figure.PetPose
import com.example.osmon.engine.GrowthStage

/** Kleiner Zähler, damit Startseite/Widget nach Anziehen, Kauf oder Spiel neu zeichnen. */
object ExtrasEvents {
    val version = mutableIntStateOf(0)
    fun bump() { version.intValue++ }
}

object WardrobeStore {
    private fun p(ctx: Context) = PetSync.prefs(ctx)

    fun equipped(ctx: Context): List<String> = p(ctx).getString("wr.equipped", "")!!.split(",").filter { it.isNotEmpty() }
    fun maxGoodDays(ctx: Context) = p(ctx).getInt("wr.maxgood", 0)

    /** Höchstwert der Wachstumstage merken (aus AchievementStore.refresh). */
    fun noteGoodDays(ctx: Context, days: Int) {
        if (days > maxGoodDays(ctx)) p(ctx).edit().putInt("wr.maxgood", days).apply()
    }

    fun outfit(ctx: Context): Outfit = Wardrobe.outfit(equipped(ctx), maxGoodDays(ctx), PremiumStore.isPremium(ctx))

    fun toggle(ctx: Context, id: String) {
        p(ctx).edit().putString("wr.equipped", Wardrobe.toggle(equipped(ctx), id).joinToString(",")).apply()
        ExtrasEvents.bump()
        val app = ctx.applicationContext
        kotlinx.coroutines.MainScope().launch { runCatching { com.example.osmon.PetWidget().updateAll(app) } }
        com.example.osmon.PhoneWearBridge.push(app)
    }

    private fun known(ctx: Context) = p(ctx).getStringSet("wr.known", emptySet())!!.toSet()
    /** Neue Teile seit dem letzten Öffnen des Schranks (Punkt am Knopf). */
    fun fresh(ctx: Context): List<WearItem> = Wardrobe.newlyUnlocked(maxGoodDays(ctx), PremiumStore.isPremium(ctx), known(ctx))
    fun markSeen(ctx: Context) {
        val all = Wardrobe.ALL.filter { Wardrobe.isUnlocked(it, maxGoodDays(ctx), PremiumStore.isPremium(ctx)) }.map { it.id }
        p(ctx).edit().putStringSet("wr.known", known(ctx) + all).apply()
        ExtrasEvents.bump()
    }
}

/** Knopf auf der Startseite: „👕 Kleiderschrank“ (mit „neu“, wenn etwas dazugekommen ist). */
@Composable
fun WardrobeButton(onClick: () -> Unit) {
    val ctx = LocalContext.current
    val v = ExtrasEvents.version.intValue
    val fresh = remember(v) { WardrobeStore.fresh(ctx).size }
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = if (fresh > 0) "Kleiderschrank, $fresh neue Teile" else "Kleiderschrank" }) {
        Text("👕 Kleiderschrank" + if (fresh > 0) " · $fresh neu" else "", style = MaterialTheme.typography.labelLarge,
            fontWeight = if (fresh > 0) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WardrobeDialog(figureName: String, stage: GrowthStage?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    val equipped = remember(v) { WardrobeStore.equipped(ctx) }
    val outfit = remember(v) { WardrobeStore.outfit(ctx) }
    val good = remember { WardrobeStore.maxGoodDays(ctx) }
    val premium = remember { PremiumStore.isPremium(ctx) }
    val fresh = remember { WardrobeStore.fresh(ctx).map { it.id }.toSet() }
    Dialog(onDismissRequest = { WardrobeStore.markSeen(ctx); onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👕 Kleiderschrank", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { WardrobeStore.markSeen(ctx); onClose() }) { Text("Fertig") }
                }
                // Vorschau
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PetFigureCanvas(PetPose(Mood.RADIANT, accessory = outfit.hat, glasses = outfit.glasses, stage = stage, energy = 90f, rest = 90f),
                        Modifier.size(200.dp).semantics { contentDescription = "$figureName mit deiner Auswahl" })
                }
                val next = Wardrobe.next(good)
                Text(next?.let { "Noch ${it.goodDays - good} gute ${if (it.goodDays - good == 1) "Tag" else "Tage"} bis: ${it.emoji} ${it.title}" }
                    ?: "Du hast alles mit guten Tagen freigeschaltet! 🎉",
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Text("Gute Tage sind Tage, an denen es $figureName gut ging. Antippen = anziehen, nochmal antippen = ausziehen. An Festtagen trägt $figureName das Fest-Zubehör.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (slot in Slot.entries) {
                    Text(slot.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Wardrobe.ALL.filter { it.slot == slot }.forEach { item ->
                            val open = Wardrobe.isUnlocked(item, good, premium)
                            val on = item.id in equipped && open
                            val miss = Wardrobe.missing(item, good, premium)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (on) Color(0xFFD9F5EE) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (on) BorderStroke(2.dp, Color(0xFF1C9A9A)) else null,
                                modifier = Modifier.width(100.dp).alpha(if (open) 1f else 0.5f)
                                    .clickable(enabled = open, onClickLabel = if (on) "ausziehen" else "anziehen") {
                                        WardrobeStore.toggle(ctx, item.id); SoundPlayer.play(ctx, Sfx.WARDROBE); v++
                                    }
                                    .semantics { contentDescription = item.title + when { on -> ", angezogen"; open -> ""; miss == null -> ", Premium"; else -> ", noch $miss gute Tage" } }
                            ) {
                                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (open) item.emoji else "🔒", fontSize = 30.sp)
                                    Text(item.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                                    Spacer(Modifier.height(2.dp))
                                    Text(when { on -> "trägt es"; item.id in fresh -> "neu!"; open -> ""; miss == null -> "💎 Premium"; else -> "noch $miss Tage" },
                                        style = MaterialTheme.typography.labelSmall, color = if (item.id in fresh) Color(0xFFD81B60) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (!premium) Text("💎 Premium-Teile gibt es im Eltern-Bereich (Einstellungen).", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
