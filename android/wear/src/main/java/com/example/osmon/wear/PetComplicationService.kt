// PetComplicationService.kt: Complication fürs Zifferblatt (Wear OS).
// Ring (RANGED_VALUE) = Wohlbefinden mit Stimmungs-Emoji, kurzer Text = Aufgaben x/y, langer Text = Stimmung +
// Aufgaben, kleines Bild = das Wesen. Neu geladen, sobald das Handy einen Stand schickt (WearStore.save).

package com.example.osmon.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.example.osmon.engine.Mood
import com.example.osmon.figure.PetFigureRenderer
import com.example.osmon.wearsync.WearPayload

class PetComplicationService : SuspendingComplicationDataSourceService() {

    private fun text(s: String) = PlainComplicationText.Builder(s).build()

    private fun emoji(m: Mood) = when (m) {
        Mood.RADIANT -> "😄"; Mood.CONTENT -> "🙂"; Mood.TIRED -> "🥱"; Mood.SICK -> "🤒"; Mood.HIBERNATION -> "😴"
    }

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, WearActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, null)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, WearStore.load(this))

    private fun build(type: ComplicationType, p: WearPayload?): ComplicationData? {
        val mood = p?.snapshot?.mood ?: Mood.CONTENT
        val wb = p?.snapshot?.wellbeing ?: 75f
        val prog = p?.progress
        val tasks = prog?.takeIf { it.total > 0 }?.let { "${it.done}/${it.total}" }
        val desc = text("OS-Mon: ${moodTitle(mood)}, Wohlbefinden ${wb.toInt()}")
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(wb, 0f, 100f, desc)
                .setText(text(emoji(mood))).setTapAction(tapIntent()).build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(tasks ?: "${wb.toInt()}"), desc)
                .setTitle(text(emoji(mood))).setTapAction(tapIntent()).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text("${emoji(mood)} ${moodTitle(mood)}" + (tasks?.let { " · Aufgaben $it" } ?: "")), desc)
                .setTitle(text(p?.petName ?: "OS-Mon")).setTapAction(tapIntent()).build()
            ComplicationType.SMALL_IMAGE -> {
                val pose = p?.pose() ?: com.example.osmon.figure.PetPose.Preview
                val bmp = if (p?.growth == com.example.osmon.engine.GrowthStage.EGG) PetFigureRenderer.eggBitmap(figureOf(p!!.figureId), 2, 144)
                    else PetFigureRenderer.bitmap(figureOf(p?.figureId), pose, 144)
                SmallImageComplicationData.Builder(SmallImage.Builder(Icon.createWithBitmap(bmp), SmallImageType.PHOTO).build(), desc)
                    .setTapAction(tapIntent()).build()
            }
            else -> null
        }
    }
}
