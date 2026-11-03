// HomeScreen.kt: Home-Screen (Jetpack Compose, Material 3), Gegenstück zu HomeView.swift
//
// Gradle (Versionen prüfen):
//   implementation(platform("androidx.compose:compose-bom:<aktuelle Version>"))
//   implementation("androidx.compose.material3:material3")          // LinearProgressIndicator(progress = { }) ab 1.2
//   implementation("androidx.compose.ui:ui-tooling-preview")
//   debugImplementation("androidx.compose.ui:ui-tooling")
//   implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.x")
//   implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.x")   // collectAsStateWithLifecycle, LocalLifecycleOwner
//
// Abhängigkeiten im Modul: PetCore.kt (engine), PetSync.kt (Kern, Anzeige-Zuordnungen, PetCopy), OSMonWidget.kt,
// ScreenTime.kt, StepsHealthConnect.kt (StepsPrefs), PetScene.kt (Hintergrund, Wochenrückblick), PetProgress.kt,
// PetReaction.kt (Antippen).
//
// Mit Hintergrund-Szene: Bild von oben bis knapp unter die Statusringe, Plattform unter den Füßen der Figur,
// Kopfzeile und Ringe auf Glas-Karten. Antippen der Figur löst eine Reaktion aus (Gesicht, Arme/Beine, Hüpfen,
// Emoji-Partikel, Sprechblase, Haptik). Antippen ändert keine Werte.
//
// Das Wesen wird mit Canvas gezeichnet und hängt nur an (mood, overlays, form, branch):
// später 1:1 durch eine Rive-Animation ersetzbar. Statusringe stehen in einer Reihe unter dem Wesen.
//
// Flags in den SharedPreferences "home", die das Onboarding setzen muss:
//   "feature.screentime", "feature.steps" (Boolean). Ohne Flag (bzw. ohne Nutzungszugriff) zeigt der Ring "–",
//   ein Tipp ruft onMissingPermission auf.

package com.example.osmon.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.osmon.EvolutionJob
import com.example.osmon.EvolutionStore
import com.example.osmon.EvolutionScheduler
import com.example.osmon.PetCopy
import com.example.osmon.PetScheduler
import com.example.osmon.PetSync
import com.example.osmon.PhoneWearBridge
import com.example.osmon.PetWidget
import com.example.osmon.formBadge
import com.example.osmon.formTitle
import com.example.osmon.moodColor
import com.example.osmon.moodTitle
import com.example.osmon.engine.Branch
import com.example.osmon.figure.ActiveReaction
import com.example.osmon.figure.PetFigureCanvas
import com.example.osmon.figure.ReactionKind
import com.example.osmon.figure.ReactionPicker
import com.example.osmon.engine.SceneUnlock
import com.example.osmon.engine.DayPhase
import com.example.osmon.engine.Accessory
import com.example.osmon.engine.Festival
import com.example.osmon.engine.Festivals
import com.example.osmon.engine.SceneTheme
import com.example.osmon.engine.Season
import com.example.osmon.engine.WeeklyReview
import com.example.osmon.figure.PetPose
import com.example.osmon.figure.rememberCurrentFigure
import com.example.osmon.engine.EvoEvent
import com.example.osmon.engine.EvoKind
import com.example.osmon.engine.Evolution
import com.example.osmon.engine.Form
import com.example.osmon.engine.Inputs
import com.example.osmon.engine.Mood
import com.example.osmon.engine.Overlay
import com.example.osmon.engine.PetCore
import com.example.osmon.engine.PetStats
import com.example.osmon.engine.Snapshot
import java.time.LocalDate
import java.time.LocalTime
import java.util.Date
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Anzeige-Zuordnungen (moodTitle, moodColor, formBadge, formTitle) und PetCopy liegen in PetSync.kt.

// ---------- Zustand für die Oberfläche ----------

enum class Sensor { SCREEN_TIME, STEPS }

data class Quest(
    val id: String,
    val title: String,
    val detail: String,
    val progress: Float,                 // 0..1
    val isLimit: Boolean = false         // "unter Ziel bleiben": voller Balken ist schlecht
) {
    val isDone get() = !isLimit && progress >= 1f
}

data class HomeState(
    val snapshot: Snapshot,
    val evolution: Evolution?,           // null = noch Ei/Baby (vor Tag 11)
    val quests: List<Quest>,
    val updatedAtMs: Long,
    val canPat: Boolean,
    val patReadyAtMs: Long?,
    val focusEndsAtMs: Long?,
    val screenTimeEnabled: Boolean,
    val stepsEnabled: Boolean,
    val evolutionEvent: EvoEvent? = null,    // Entwicklung/Metamorphose, bis der Nutzer sie bestätigt
    val review: WeeklyReview? = null,        // Wochenrückblick, bis weggeklickt
    val sceneStatus: List<SceneUnlock> = emptyList(),
    val newScenes: List<Scene> = emptyList(), // neu freigeschaltete Hintergründe, bis bestätigt
    val sleepPhase: DayPhase = DayPhase.DAY,  // Abend/Nacht: Einladung zur Gute-Nacht-Routine
    val morning: MorningInfo? = null,         // Guten-Morgen-Karte, bis weggeklickt
    val festival: Festival? = null,           // Fest heute (Zubehör, Deko, Gruß)
    val festivalCard: Boolean = false,        // Karte „Feiern“ sichtbar
    val season: Season = Season.AUTUMN,       // Jahreszeit (Tönung, Deko, Teilchen)
    val seasonsOn: Boolean = true
) {
    companion object {
        val Empty = HomeState(
            snapshot = Snapshot(PetStats(100f, 100f, 100f, 100f, 70f), 90f, Mood.RADIANT, emptySet()),
            evolution = null, quests = emptyList(), updatedAtMs = 0L, canPat = true, patReadyAtMs = null,
            focusEndsAtMs = null, screenTimeEnabled = false, stepsEnabled = false
        )
    }
}

// ---------- ViewModel (PetSync -> Anzeige) ----------

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication<Application>()

    private val _state = MutableStateFlow(HomeState.Empty)
    val state: StateFlow<HomeState> = _state

    private companion object {
        const val STEP_QUEST = 5000
        const val FOCUS_QUEST_MIN = 45
    }

    init {
        // Idempotent (KEEP): stellt sicher, dass beide Jobs nach Updates/Neustarts eingeplant sind.
        PetScheduler.start(ctx)
        EvolutionScheduler.start(ctx)
    }

    fun refresh() = launchPublish()

    fun pat() {
        viewModelScope.launch(Dispatchers.Default) {
            if (!PetSync.pat(ctx)) return@launch
            publish(System.currentTimeMillis(), forceWidget = true)   // Widget-Button ändert sich immer
        }
    }

    /** Eine Fokus-Session zählt erst, wenn sie bis zum Ende durchläuft. Live-Benachrichtigung/App-Sperre folgen separat. */
    fun startFocus(minutes: Int) {
        PetSync.startFocus(ctx, minutes)
        launchPublish()
    }

    fun cancelFocus() {
        PetSync.cancelFocus(ctx)
        launchPublish()
    }

    fun ackEvolution() {
        viewModelScope.launch(Dispatchers.Default) {
            EvolutionStore.acknowledge(ctx)
            publish(System.currentTimeMillis())
        }
    }

    fun dismissReview() {
        val r = _state.value.review ?: return
        SceneStore.markReviewSeen(ctx, r.weekStart)
        _state.value = _state.value.copy(review = null)
    }

    fun useScene(s: Scene) {
        SceneStore.select(ctx, s.id, _state.value.sceneStatus)
        ackScenes()
    }

    fun hideFestivalCard() {
        FestivalStore.hideCard(ctx)
        _state.value = _state.value.copy(festivalCard = false)
    }

    fun dismissMorning() {
        val m = _state.value.morning ?: return
        SleepStore.markMorningSeen(ctx, m.day)
        _state.value = _state.value.copy(morning = null)
    }

    fun ackScenes() { _state.value = _state.value.copy(newScenes = emptyList()) }

    private fun launchPublish() {
        viewModelScope.launch(Dispatchers.Default) { publish(System.currentTimeMillis()) }
    }

    /** Ein Update-Zyklus: verpasste Tage abschließen, ticken, Widget bei spürbarer Änderung neu zeichnen. */
    private suspend fun publish(now: Long, forceWidget: Boolean = false) {
        val evo = EvolutionJob.closePastDays(ctx)                    // idempotent, holt verpasste Tage nach
        val t = PetSync.tick(ctx, now)                               // speichert Snapshot + Tageslog
        val readyAt = PetSync.patReadyAt(ctx, now)
        val prog = ProgressData.refresh(ctx)
        val prev = _state.value
        _state.value = HomeState(
            snapshot = t.snapshot,
            evolution = evo.current,
            quests = quests(t.inputs, now),
            updatedAtMs = now,
            canPat = readyAt == null,
            patReadyAtMs = readyAt,
            focusEndsAtMs = PetSync.focusEndsAt(ctx, now),
            screenTimeEnabled = t.screenUsable,
            stepsEnabled = PetSync.flag(ctx, "feature.steps"),
            evolutionEvent = evo.pendingEvent,
            review = prog.review,
            sceneStatus = prog.status,
            newScenes = (prev.newScenes + prog.newlyUnlocked).distinctBy { it.id },
            sleepPhase = SleepStore.phase(ctx),
            morning = SleepStore.morning(ctx, t.inputs.nightMinutes),
            festival = FestivalStore.today(ctx),
            festivalCard = FestivalStore.today(ctx) != null && !FestivalStore.cardHidden(ctx),
            season = SceneTheme.season(LocalDate.now().toEpochDay()),
            seasonsOn = FestivalStore.seasons(ctx)
        )
        PetSync.updateWidgetIfNeeded(ctx, t.snapshot, force = forceWidget)
        PhoneWearBridge.push(ctx, t.snapshot, _state.value.festival?.accessory, evo.current?.form, updateExtras = true)
    }

    private fun quests(i: Inputs, now: Long): List<Quest> = buildList {
        val focus = PetSync.counter(ctx, "focusMinutes", now)
        add(Quest("focus", "Handy $FOCUS_QUEST_MIN Min weglegen", "$focus / $FOCUS_QUEST_MIN Min",
            (focus.toFloat() / FOCUS_QUEST_MIN).coerceIn(0f, 1f)))
        i.steps?.let {
            add(Quest("steps", "5.000 Schritte gehen", "$it / $STEP_QUEST", (it.toFloat() / STEP_QUEST).coerceIn(0f, 1f)))
        }
        val goal = PetSync.config(ctx).screenGoalMin
        i.screenMinutes?.let {
            add(Quest("screen", "Bildschirmzeit unter Ziel bleiben", "${it.toInt()} / ${goal.toInt()} Min",
                (it / goal).coerceIn(0f, 1f), isLimit = true))
        }
    }
}

// ---------- Screen mit Lebenszyklus ----------

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {},
    onOpenBedtime: () -> Unit = {},
    /** Ohne eigene Behandlung öffnet ein Tipp auf einen leeren Ring die Einstellungen. */
    onMissingPermission: (Sensor) -> Unit = { onOpenSettings() },
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val ctxHome = LocalContext.current.applicationContext
    // Beim Sichtbarwerden und danach jede Minute aktualisieren, nur solange der Screen im Vordergrund ist.
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            SleepStore.noteOpen(ctxHome)                 // nachts nach dem Gute-Nacht-Sagen: Öffnen zählen
            while (true) {
                vm.refresh()
                delay(60_000)
            }
        }
    }
    HomeContent(
        state = state,
        onPat = vm::pat,
        onStartFocus = vm::startFocus,
        onCancelFocus = vm::cancelFocus,
        onMissing = onMissingPermission,
        onAckEvolution = vm::ackEvolution,
        onOpenSettings = onOpenSettings,
        onDismissReview = vm::dismissReview,
        onUseScene = vm::useScene,
        onAckScenes = vm::ackScenes,
        onOpenBedtime = onOpenBedtime,
        onDismissMorning = vm::dismissMorning,
        onHideFestival = vm::hideFestivalCard
    )
}

// ---------- Reine Darstellung (gut für Previews) ----------

@Composable
fun HomeContent(
    state: HomeState,
    onPat: () -> Unit = {},
    onStartFocus: (Int) -> Unit = {},
    onCancelFocus: () -> Unit = {},
    onMissing: (Sensor) -> Unit = {},
    onAckEvolution: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onDismissReview: () -> Unit = {},
    onUseScene: (Scene) -> Unit = {},
    onAckScenes: () -> Unit = {},
    onOpenBedtime: () -> Unit = {},
    onDismissMorning: () -> Unit = {},
    onHideFestival: () -> Unit = {},
    scene: Scene = rememberCurrentScene()
) {
    val snap = state.snapshot
    val tint = moodColor(snap.mood)
    val hasScene = scene.res != null
    val density = LocalDensity.current

    // Lage von Figur und Ringen (Pixel, relativ zur Oberkante des Inhalts) für die Bildlage
    var rootY by remember { mutableFloatStateOf(0f) }
    var stageY by remember { mutableFloatStateOf(0f) }
    var stageH by remember { mutableFloatStateOf(0f) }
    var ringsBottom by remember { mutableFloatStateOf(0f) }
    var ringsH by remember { mutableFloatStateOf(0f) }
    var headerBottom by remember { mutableFloatStateOf(0f) }

    // Antippen
    val picker = remember { ReactionPicker() }
    var reaction by remember { mutableStateOf<ActiveReaction?>(null) }
    val view = LocalView.current
    var celebrateAt by remember { mutableStateOf<Long?>(null) }
    val onCelebrate = {
        val now = SystemClock.uptimeMillis()
        reaction = picker.start(ReactionKind.SPIN, now)
        celebrateAt = System.currentTimeMillis()
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        Unit
    }
    val onTapPet = {
        val now = SystemClock.uptimeMillis()
        val t = LocalTime.now()
        val k = picker.pick(snap.mood, snap.overlays, t.hour + t.minute / 60f, LocalDate.now().toEpochDay(), now)
        reaction = picker.start(k, now)
        view.performHapticFeedback(
            when (k) {
                ReactionKind.DIZZY, ReactionKind.STARTLE -> HapticFeedbackConstants.LONG_PRESS
                ReactionKind.WEAK, ReactionKind.SLEEPY, ReactionKind.NIGHT -> HapticFeedbackConstants.CLOCK_TICK
                else -> HapticFeedbackConstants.CONTEXT_CLICK
            }
        )
        Unit
    }

    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (hasScene) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
                else Brush.verticalGradient(colorStops = arrayOf(0f to tint.copy(alpha = 0.22f), 0.5f to MaterialTheme.colorScheme.background))
            )
            .verticalScroll(rememberScrollState())
    ) {
        if (hasScene && stageH > 0f && ringsBottom > 0f) {
            val layerH = max(with(density) { 200.dp.toPx() }, ringsBottom + with(density) { 14.dp.toPx() })
            val hour = LocalTime.now().let { it.hour + it.minute / 60f }
            SceneBackground(
                scene, snap.mood, hour,
                feetY = stageY + stageH * 0.92f,
                fadePx = ringsH * 0.75f + with(density) { 14.dp.toPx() },
                modifier = Modifier.fillMaxWidth().height(with(density) { layerH.toDp() })
            )
        }
        // Jahreszeit + Fest: Tönung, Requisiten (Tannenbaum, Ballons …) und schwebende Teilchen über dem Bild,
        // mit und ohne Hintergrund, bis knapp unter die Ringe
        if (ringsBottom > 0f && stageH > 0f) {
            val layerMod = Modifier.fillMaxWidth().height(with(density) { max(200.dp.toPx(), ringsBottom).toDp() })
            val day = LocalDate.now().toEpochDay()
            val props = SceneTheme.props(state.festival, state.season, state.seasonsOn,
                Festivals.adventNumber(day, Festivals.firstAdvent(Festivals.ymd(day).y)))
            SceneThemeLayer(props, if (state.seasonsOn && hasScene) SceneTheme.tint(state.season).argb else null,
                feetY = stageY + stageH * 0.92f, garlandY = headerBottom + with(density) { 6.dp.toPx() }, modifier = layerMod)
            SceneTheme.particles(state.festival, state.season, state.seasonsOn)?.let { FestivalDecor(it, celebrateAt, layerMod) }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rootY = it.positionInRoot().y }
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (hasScene) 14.dp else 20.dp)
        ) {
            val figure = rememberCurrentFigure()

            // Kopf: Wohlbefinden-Ring + Stimmung (mit Szene auf Glas-Karte)
            Box(Modifier.onGloballyPositioned { headerBottom = it.positionInRoot().y - rootY + it.size.height }) {
            Glass(hasScene) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusRing("Wohlbefinden", "❤️", snap.wellbeing, tint, diameter = 72.dp, onScene = hasScene)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(moodTitle(snap.mood), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val evo = state.evolution
                        Text(
                            // Figurenname bleibt sichtbar; die Entwicklungsform steht dahinter
                            if (evo != null) "${figure.name} · ${formTitle(evo.form)}" + (evo.hybrid?.let { " ${formBadge(it)}" } ?: "") else figure.name,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            color = if (hasScene) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    onOpenSettings?.let {
                        TextButton(onClick = it, modifier = Modifier.semantics { contentDescription = "Einstellungen" }) {
                            Text("⚙️", fontSize = 22.sp)
                        }
                    }
                }
            }
            }

            // Sprechblase über der Figur: Reaktionstext 2,6 s, sonst Stimmungssatz
            val now by produceState(SystemClock.uptimeMillis(), reaction) {
                while (reaction?.saying(value) == true) { value = SystemClock.uptimeMillis(); delay(200) }
                value = SystemClock.uptimeMillis()
            }
            val say = reaction?.takeIf { it.saying(now) }?.say
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, shadowElevation = if (hasScene) 4.dp else 0.dp) {
                Text(
                    say ?: state.festival?.greeting ?: PetCopy.line(snap),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (say != null) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }

            // Wesen (antippbar) + Partikel
            Box(
                Modifier.onGloballyPositioned {
                    stageY = it.positionInRoot().y - rootY
                    stageH = it.size.height.toFloat()
                },
                contentAlignment = Alignment.Center
            ) {
                PetAvatar(
                    snap.mood, snap.overlays, state.evolution?.form, state.evolution?.branch,
                    energy = snap.stats.e, rest = snap.stats.r,
                    size = if (hasScene) 260.dp else 220.dp,
                    reaction = reaction,
                    accessory = state.festival?.accessory,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClickLabel = "Wesen antippen", onClick = onTapPet
                    )
                )
                reaction?.let { ReactionParticles(it, Modifier.matchParentSize()) }
            }

            // Vier Statusringe (mit Szene auf Glas-Karte)
            Box(Modifier.onGloballyPositioned {
                ringsBottom = it.positionInRoot().y - rootY + it.size.height
                ringsH = it.size.height.toFloat()
            }) {
                Glass(hasScene) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                        StatusRing("Energie", "⚡", snap.stats.e, Color(0xFFF2B600), Modifier.weight(1f), onScene = hasScene)
                        StatusRing(
                            "Ruhe", "🌙", snap.stats.r, Color(0xFF7C8CFF), Modifier.weight(1f), onScene = hasScene,
                            onMissingTap = if (state.screenTimeEnabled) null else ({ onMissing(Sensor.SCREEN_TIME) })
                        )
                        StatusRing("Fitness", "💾", snap.stats.f, Color(0xFFE08A1E), Modifier.weight(1f), onScene = hasScene)
                        StatusRing(
                            "Vitalität", "👟", snap.stats.v, Color(0xFF2E9E5B), Modifier.weight(1f), onScene = hasScene,
                            onMissingTap = if (state.stepsEnabled) null else ({ onMissing(Sensor.STEPS) })
                        )
                    }
                }
            }

            Actions(state, onPat, onStartFocus, onCancelFocus)

            state.festival?.takeIf { state.festivalCard }?.let { FestivalCard(it, onCelebrate, onHideFestival) }
            state.morning?.let { MorningCard(it, onDismissMorning) }
            if (state.sleepPhase == DayPhase.WIND_DOWN || state.sleepPhase == DayPhase.NIGHT) BedtimePromptCard(state.sleepPhase, onOpenBedtime)
            state.review?.let { WeeklyReviewCard(it, onDismissReview) }

            if (state.quests.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Heute", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    state.quests.forEach { QuestCard(it) }
                }
            }

            // Eigene Aufgaben mit Motivation und Miteinander-Tipp (DayTasksUI.kt); das Wesen freut sich mit
            TasksSection(onCheer = { all, line ->
                val now = SystemClock.uptimeMillis()
                reaction = ActiveReaction(if (all) ReactionKind.SPIN else ReactionKind.GIGGLE, now, line)
                if (all) celebrateAt = System.currentTimeMillis()
            })

            if (state.updatedAtMs > 0) {
                val rel = DateUtils.getRelativeTimeSpanString(
                    state.updatedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                )
                Text("Zuletzt aktualisiert: $rel", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Platz, damit die Einblendung unten nichts verdeckt
            if (state.evolutionEvent != null || state.newScenes.isNotEmpty()) Spacer(Modifier.height(96.dp))
        }
    }

    // Hinweise (Entwicklung, neuer Hintergrund) unten als Einblendung: verschieben Kopfzeile und Wesen nicht
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.evolutionEvent?.let { Box(Modifier.shadow(10.dp, RoundedCornerShape(16.dp))) { EvolutionBanner(it, onAckEvolution) } }
        if (state.newScenes.isNotEmpty()) {
            Box(Modifier.shadow(10.dp, RoundedCornerShape(16.dp))) { SceneUnlockBanner(state.newScenes, onUseScene, onAckScenes) }
        }
    }
    }
}

/** Glas-Karte über dem Hintergrundbild (dunkel, halbtransparent, weiße Schrift). Ohne Szene: Inhalt unverändert. */
@Composable
private fun Glass(on: Boolean, content: @Composable () -> Unit) {
    if (!on) { content(); return }
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(22.dp), clip = false)
            .background(Color(0x8C0E1230), RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

/** Emoji-Partikel der Reaktion: steigen auf und verblassen (Dauer = Reaktion + 0,4 s). */
@Composable
private fun ReactionParticles(r: ActiveReaction, modifier: Modifier) {
    var u by remember(r) { mutableFloatStateOf(0f) }
    LaunchedEffect(r) {
        val total = r.kind.durMs + 400f
        while (u < 1f) withFrameMillis { u = ((SystemClock.uptimeMillis() - r.startMs) / total).coerceIn(0f, 1f) }
    }
    if (u >= 1f) return
    Box(modifier) {
        val n = maxOf(3, r.kind.fx.size * 2)
        repeat(n) { i ->
            val e = r.kind.fx[i % r.kind.fx.size]
            val delayU = i * 0.08f
            val p = ((u - delayU) / (1f - delayU)).coerceIn(0f, 1f)
            if (p > 0f) {
                val x = (i - (n - 1) / 2f) * 34f + sin(p * PI.toFloat() * 2 + i) * 8f
                Text(
                    e, fontSize = 22.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = x.dp, y = (-40 - 90 * p).dp)
                        .alpha(if (p < 0.15f) p / 0.15f else 1f - ((p - 0.15f) / 0.85f))
                )
            }
        }
    }
}

@Composable
private fun Actions(state: HomeState, onPat: () -> Unit, onStartFocus: (Int) -> Unit, onCancelFocus: () -> Unit) {
    val end = state.focusEndsAtMs
    if (end != null) {
        val now by produceState(System.currentTimeMillis(), end) {
            while (true) { value = System.currentTimeMillis(); delay(1000) }
        }
        val remainingSec = ((end - now) / 1000).coerceAtLeast(0)
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🌙 ${DateUtils.formatElapsedTime(remainingSec)}", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onCancelFocus) { Text("Beenden") }
            }
        }
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        var menu by remember { mutableStateOf(false) }
        Box(Modifier.weight(1f)) {
            Button(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("🌙 Fokus starten") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(15, 30, 60).forEach { m ->
                    DropdownMenuItem(text = { Text("$m Minuten") }, onClick = { menu = false; onStartFocus(m) })
                }
            }
        }
        OutlinedButton(onClick = onPat, enabled = state.canPat, modifier = Modifier.weight(1f)) {
            Text("✋ Streicheln")
        }
    }
    state.patReadyAtMs?.let {
        val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(it))
        Text("Streicheln wieder ab $time", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Bausteine ----------

@Composable
private fun EvolutionBanner(event: EvoEvent, onAck: () -> Unit) {
    val text = when (event.kind) {
        EvoKind.EVOLVED -> "Dein Wesen hat sich entwickelt: ${formBadge(event.to)} ${formTitle(event.to)}!"
        EvoKind.METAMORPHOSIS ->
            "Metamorphose! Aus ${event.from?.let { formTitle(it) } ?: "deinem Wesen"} wird ${formBadge(event.to)} ${formTitle(event.to)}."
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✨ $text", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAck) { Text("Okay") }
        }
    }
}

@Composable
fun StatusRing(
    title: String,
    symbol: String,
    value: Float?,                          // null = Sensor/Erlaubnis fehlt
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 60.dp,
    onMissingTap: (() -> Unit)? = null,
    onScene: Boolean = false                // auf Glas-Karte: kräftigere Ringe, weiße Schrift
) {
    val sweep by animateFloatAsState(
        targetValue = ((value ?: 0f).coerceIn(0f, 100f) / 100f) * 360f,
        animationSpec = tween(600), label = "ring"
    )
    val desc = when {
        value != null -> "$title ${value.roundToInt()} Prozent"
        onMissingTap != null -> "$title: keine Daten. Tippen, um die Berechtigung zu erteilen."
        else -> "$title: keine Daten"
    }
    val click = if (value == null && onMissingTap != null) Modifier.clickable(onClick = onMissingTap) else Modifier

    Column(
        modifier.then(click).clearAndSetSemantics { contentDescription = desc },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = (if (onScene) 9 else 8).dp.toPx()
                val topLeft = Offset(w / 2, w / 2)
                val arc = Size(this.size.width - w, this.size.height - w)
                drawArc(color.copy(alpha = if (onScene) 0.3f else 0.18f), 0f, 360f, false, topLeft = topLeft, size = arc, style = Stroke(w))
                if (value != null) {
                    drawArc(color, -90f, sweep, false, topLeft = topLeft, size = arc,
                        style = Stroke(w, cap = StrokeCap.Round))
                } else {
                    drawArc(Color.Gray.copy(alpha = 0.5f), 0f, 360f, false, topLeft = topLeft, size = arc,
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 14f))))
                }
            }
            Text(symbol, fontSize = if (diameter > 64.dp) 24.sp else 18.sp,
                color = if (value == null) Color.Gray else Color.Unspecified)
        }
        Text(value?.let { "${it.roundToInt()}" } ?: "–", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelSmall,
            color = if (onScene) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QuestCard(q: Quest) {
    Surface(
        shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (q.isDone) "✅" else "⚪")
                Spacer(Modifier.width(8.dp))
                Text(q.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(q.detail, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { q.progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (q.isLimit && q.progress >= 0.8f) Color(0xFFE08A1E) else MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ---------- Das Wesen (gezeichnet; später durch Rive ersetzbar) ----------

/** Das Wesen in der gewählten Figur (PetFigure.kt). Gleiche Schnittstelle wie bisher, plus Energie/Ruhe. */
@Composable
fun PetAvatar(
    mood: Mood,
    overlays: Set<Overlay>,
    form: Form?,
    branch: Branch?,
    modifier: Modifier = Modifier,
    energy: Float? = 100f,
    rest: Float? = 100f,
    charging: Boolean = false,
    petted: Boolean = false,
    size: Dp = 220.dp,
    reaction: ActiveReaction? = null,
    accessory: Accessory? = null
) {
    PetFigureCanvas(
        PetPose(mood, overlays, form, branch, energy, rest, charging, petted, accessory = accessory),
        modifier.size(size).semantics { contentDescription = "Dein Wesen: ${moodTitle(mood)}. Antippen für eine Reaktion." },
        reaction = reaction
    )
}

// ---------- Previews ----------

private fun sample(
    mood: Mood,
    overlays: Set<Overlay> = emptySet(),
    form: Form? = Form.CHATTERLING,
    branch: Branch = Branch.STANDARD
): HomeState {
    val stats = when (mood) {
        Mood.RADIANT -> PetStats(90f, 85f, 95f, 90f, 80f)
        Mood.CONTENT -> PetStats(70f, 65f, 60f, 50f, 60f)
        Mood.TIRED -> PetStats(40f, 45f, 50f, 30f, 40f)
        Mood.SICK -> PetStats(22f, 30f, 25f, 20f, 30f)
        Mood.HIBERNATION -> PetStats(8f, 12f, 10f, 5f, 10f)
    }
    return HomeState.Empty.copy(
        snapshot = Snapshot(stats, PetCore.wellbeing(stats), mood, overlays),
        evolution = form?.let { Evolution(it, Form.BINGEON, branch) },
        screenTimeEnabled = true,
        stepsEnabled = true,
        updatedAtMs = System.currentTimeMillis() - 120_000,
        quests = listOf(
            Quest("focus", "Handy 45 Min weglegen", "30 / 45 Min", 0.66f),
            Quest("steps", "5.000 Schritte gehen", "5200 / 5000", 1f),
            Quest("screen", "Bildschirmzeit unter Ziel bleiben", "150 / 180 Min", 0.83f, isLimit = true)
        )
    )
}

@Preview(showBackground = true, heightDp = 900) @Composable
private fun PreviewRadiant() = MaterialTheme { HomeContent(sample(Mood.RADIANT, branch = Branch.RADIANT)) }

@Preview(showBackground = true, heightDp = 900) @Composable
private fun PreviewTiredYawning() = MaterialTheme { HomeContent(sample(Mood.TIRED, setOf(Overlay.YAWNING))) }

@Preview(showBackground = true, heightDp = 900) @Composable
private fun PreviewStressedChubby() =
    MaterialTheme { HomeContent(sample(Mood.TIRED, setOf(Overlay.STRESSED, Overlay.CHUBBY))) }

@Preview(showBackground = true, heightDp = 900) @Composable
private fun PreviewSickSluggish() =
    MaterialTheme { HomeContent(sample(Mood.SICK, setOf(Overlay.SLUGGISH), branch = Branch.WILD)) }

@Preview(showBackground = true, heightDp = 900) @Composable
private fun PreviewHibernation() = MaterialTheme { HomeContent(sample(Mood.HIBERNATION, form = null)) }

@Preview(showBackground = true, heightDp = 900) @Composable
private fun PreviewMissingPermissions() = MaterialTheme {
    val base = sample(Mood.CONTENT)
    HomeContent(
        base.copy(
            snapshot = base.snapshot.copy(stats = base.snapshot.stats.copy(r = null, v = null)),
            screenTimeEnabled = false, stepsEnabled = false
        )
    )
}
