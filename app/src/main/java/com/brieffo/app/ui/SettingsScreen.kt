package com.brieffo.app.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brieffo.app.data.CalendarInfo
import com.brieffo.app.data.CalendarRepo
import com.brieffo.app.data.CardKeys
import com.brieffo.app.data.Feed
import com.brieffo.app.data.GeminiException
import com.brieffo.app.data.LocalVoice
import com.brieffo.app.data.NewsRepo
import com.brieffo.app.data.Prefs
import com.brieffo.app.data.SportRepo
import com.brieffo.app.data.Summary
import com.brieffo.app.data.TeamRef
import com.brieffo.app.data.Updater
import com.brieffo.app.notify.Alarms
import com.brieffo.app.notify.BriefWorker
import com.brieffo.app.notify.MatchReminder
import com.brieffo.app.widget.AgendaWidgetReceiver
import com.brieffo.app.widget.MatchWidgetReceiver
import com.brieffo.app.widget.SummaryWidgetReceiver
import com.brieffo.app.widget.WeatherWidgetReceiver
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        supportingText = hint?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, detail: String? = null, dot: Color? = null, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) {
            Box(Modifier.size(10.dp).background(dot, CircleShape))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            if (detail != null) Text(detail, fontSize = 12.sp, lineHeight = 16.sp, color = p.sub)
        }
        Switch(checked, onChange)
    }
}

@Composable
internal fun Note(text: String) {
    Text(text, fontSize = 12.sp, lineHeight = 17.sp, color = LocalPalette.current.sub)
}

/**
 * Riquadro in fondo alle impostazioni: ogni volta che si apre la pagina controlla se su GitHub è uscita
 * una versione più recente e, se c'è, la scarica e la passa all'installatore di Android.
 */
@Composable
private fun UpdateCard() {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember { Updater.installed(context) }
    var checking by remember { mutableStateOf(true) }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var checks by remember { mutableIntStateOf(0) }

    LaunchedEffect(checks) {
        checking = true
        val found = withContext(Dispatchers.IO) { runCatching { Updater.latest() } }
        release = found.getOrNull()?.takeIf { Updater.isNewer(it.version, installed) }
        message = if (found.isFailure) "Non riesco a controllare gli aggiornamenti: serve la connessione." else null
        checking = false
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = p.card,
        contentColor = p.text,
        border = BorderStroke(1.dp, if (release != null) p.accent else p.stroke),
    ) {
        Column(Modifier.padding(20.dp)) {
            val found = release
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (found != null) "Disponibile Brieffo ${found.version}" else "Brieffo $installed",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            checking -> "Controllo se c'è una versione nuova…"
                            found != null -> "Hai la $installed · %.0f MB da scaricare".format(found.bytes / 1_000_000f)
                            message != null -> "Versione installata"
                            else -> "È la versione più recente"
                        },
                        fontSize = 12.sp, lineHeight = 16.sp, color = p.sub,
                    )
                }
                if (checking) CircularProgressIndicator(Modifier.size(18.dp), color = p.accent, strokeWidth = 2.dp)
                else if (found == null) IconButton(onClick = { checks++ }) { Icon(Icons.Rounded.Refresh, "Controlla di nuovo", tint = p.sub) }
            }
            if (found != null) {
                Button(
                    onClick = {
                        message = null
                        progress = 0
                        scope.launch {
                            val apk = withContext(Dispatchers.IO) {
                                runCatching { Updater.download(context, found, active = { isActive }) { progress = it } }
                            }
                            progress = null
                            apk.onSuccess { file ->
                                runCatching { Updater.install(context, file) }
                                    .onFailure { message = "Non riesco ad aprire l'installatore di Android." }
                            }.onFailure { e ->
                                if (e !is InterruptedException) message = "Aggiornamento non scaricato: ${e.message ?: "connessione non riuscita"}."
                            }
                        }
                    },
                    enabled = progress == null,
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text(progress?.let { "Scarico… $it%" } ?: "Aggiorna") }
                Spacer(Modifier.height(8.dp))
                Note("Finito il download Android chiede conferma. La prima volta va consentito a Brieffo di installare app: poi impostazioni e dati restano come sono.")
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Note(it)
            }
        }
    }
}

/**
 * Sezione delle impostazioni che si apre e si chiude toccando l'intestazione. Da chiusa mostra, sotto il titolo,
 * una riga che riassume cosa c'è dentro, così spesso non serve nemmeno aprirla.
 */
@Composable
internal fun SettingsSection(
    title: String,
    icon: ImageVector,
    summary: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = p.card,
        contentColor = p.text,
        border = BorderStroke(1.dp, p.stroke),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Chiudi $title" else "Apri $title", onClick = onToggle).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).background(p.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = p.onAccent)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (!open && summary.isNotBlank()) Text(summary, fontSize = 12.sp, lineHeight = 16.sp, color = p.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(24.dp).rotate(turn), tint = p.sub)
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) { content() }
            }
        }
    }
}

/** Impostazioni a tutto schermo, con lo stesso sfondo e le stesse schede del brief. Uscendo si salva. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: Prefs,
    themeMode: String,
    accent: Int,
    onTheme: (String) -> Unit,
    onAccent: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    onGrantCalendar: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current

    var name by remember { mutableStateOf(prefs.name) }
    var city by remember { mutableStateOf(prefs.city) }
    var goal by remember { mutableStateOf(prefs.stepGoal.toString()) }
    var hidden by remember { mutableStateOf(prefs.hidden) }
    var order by remember { mutableStateOf(prefs.cardOrder) }
    var feeds by remember { mutableStateOf(prefs.feeds) }
    var team by remember { mutableStateOf(prefs.team) }
    var teamRef by remember { mutableStateOf(prefs.teamRef) }
    var work by remember { mutableStateOf(prefs.workAddress) }
    var mode by remember { mutableStateOf(prefs.travelMode) }
    var notify by remember { mutableStateOf(prefs.notifyEnabled) }
    var speechSpeed by remember { mutableIntStateOf(prefs.speechSpeed) }
    var speechVoice by remember { mutableStateOf(prefs.speechVoice) }
    var speechEngine by remember { mutableStateOf(prefs.speechEngine) }
    var localVoice by remember { mutableStateOf(prefs.localVoice) }
    var voicesVersion by remember { mutableIntStateOf(0) }
    // Le sezioni partono tutte chiuse: si apre solo quella che serve.
    var openSections by remember { mutableStateOf(emptySet<String>()) }
    var pickTime by remember { mutableStateOf(false) }
    var notifyCheck by remember { mutableIntStateOf(0) }
    var testSent by remember { mutableStateOf(false) }
    val time = rememberTimePickerState(prefs.notifyHour, prefs.notifyMinute, is24Hour = true)
    var key by remember { mutableStateOf(prefs.geminiKey) }
    var collapsed by remember { mutableStateOf(prefs.summaryCollapsed) }
    var aiSpeed by remember { mutableStateOf(prefs.aiSpeed) }
    var railRight by remember { mutableStateOf(prefs.railRight) }
    var weatherAnimated by remember { mutableStateOf(prefs.weatherAnimated) }
    var testingKey by remember { mutableStateOf(false) }
    var keyTest by remember { mutableStateOf<String?>(null) }
    var calOn by remember { mutableStateOf(prefs.calendarsOn) }
    var calOff by remember { mutableStateOf(prefs.calendarsOff) }
    var calendarsVersion by remember { mutableIntStateOf(0) }
    val calendars = remember(calendarsVersion) { runCatching { CalendarRepo.calendars(context) }.getOrDefault(emptyList()) }
    var syncing by remember { mutableStateOf(emptySet<Long>()) }
    var pendingSync by remember { mutableStateOf(emptyList<CalendarInfo>()) }
    var forcing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val pageScope = rememberCoroutineScope()
    fun startSync(list: List<CalendarInfo>) {
        val done = list.filter { CalendarRepo.enableSync(context, it) }
        syncing = syncing + done.map { it.id }
        calendarsVersion++
    }
    val writeCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSync(pendingSync)
        pendingSync = emptyList()
    }
    fun download(list: List<CalendarInfo>) {
        if (CalendarRepo.canWrite(context)) startSync(list) else {
            pendingSync = list
            writeCalendar.launch(Manifest.permission.WRITE_CALENDAR)
        }
    }

    val save = {
        prefs.name = name
        prefs.city = city
        prefs.stepGoal = goal.toIntOrNull() ?: 8000
        prefs.hidden = hidden
        prefs.cardOrder = order
        prefs.feeds = feeds
        prefs.team = team
        prefs.teamRef = teamRef
        prefs.workAddress = work
        prefs.travelMode = mode
        prefs.notifyEnabled = notify
        prefs.notifyHour = time.hour
        prefs.notifyMinute = time.minute
        prefs.geminiKey = key
        prefs.summaryCollapsed = collapsed
        prefs.aiSpeed = aiSpeed
        prefs.railRight = railRight
        prefs.weatherAnimated = weatherAnimated
        prefs.calendarsOn = calOn
        prefs.calendarsOff = calOff
    }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            // Prima si scrivono le modifiche ancora aperte in questa pagina, così il file le contiene.
            save()
            val ok = runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(prefs.export().toByteArray()) } }.isSuccess
            backupMessage = if (ok) "Configurazione esportata. Il file contiene anche la chiave di Gemini: non condividerlo." else "Non sono riuscito a scrivere il file."
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text != null && prefs.import(text)) {
                // La pagina rilegge tutto: uscendo salverebbe altrimenti i valori di prima sopra quelli importati.
                name = prefs.name
                city = prefs.city
                goal = prefs.stepGoal.toString()
                hidden = prefs.hidden
                order = prefs.cardOrder
                feeds = prefs.feeds
                team = prefs.team
                teamRef = prefs.teamRef
                work = prefs.workAddress
                mode = prefs.travelMode
                notify = prefs.notifyEnabled
                time.hour = prefs.notifyHour
                time.minute = prefs.notifyMinute
                key = prefs.geminiKey
                collapsed = prefs.summaryCollapsed
                aiSpeed = prefs.aiSpeed
                railRight = prefs.railRight
                weatherAnimated = prefs.weatherAnimated
                speechSpeed = prefs.speechSpeed
                speechVoice = prefs.speechVoice
                speechEngine = prefs.speechEngine
                localVoice = prefs.localVoice
                onTheme(prefs.themeMode)
                onAccent(prefs.accent)
                backupMessage = "Configurazione importata. I calendari vanno scelti di nuovo: su ogni telefono sono diversi."
            } else backupMessage = "Questo file non è un backup di Brieffo."
        }
    }

    // Niente pulsante Salva: le modifiche si scrivono quando si lascia la pagina, poi il brief si ricarica.
    val latestSave by rememberUpdatedState(save)
    DisposableEffect(Unit) {
        onDispose {
            latestSave()
            onSaved()
        }
    }
    BackHandler(onBack = onBack)
    LifecycleResumeEffect(Unit) {
        notifyCheck++
        onPauseOrDispose { }
    }
    val pageScroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalFade(pageScroll)
            .verticalScroll(pageScroll)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
            Text("Le modifiche si salvano da sole", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = p.sub)
            Text("Impostazioni", fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }

        SettingsSection("Aspetto", Icons.Rounded.Palette, listOf(when (themeMode) { "light" -> "Tema chiaro"; "dark" -> "Tema scuro"; else -> "Tema automatico" }, if (railRight) "indice a destra" else "indice a sinistra").joinToString(" · "), "Aspetto" in openSections, { openSections = if ("Aspetto" in openSections) openSections - "Aspetto" else openSections + "Aspetto" }) {
            Note("Tema")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Automatico", "light" to "Chiaro", "dark" to "Scuro").forEach { (value, label) ->
                    FilterChip(selected = themeMode == value, onClick = { onTheme(value) }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Note("Indice delle schede (i puntini sul bordo del brief)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "A sinistra", true to "A destra").forEach { (value, label) ->
                    FilterChip(selected = railRight == value, onClick = { railRight = value }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(6.dp))
            ToggleRow("Meteo animato", weatherAnimated, detail = "Sole, pioggia, neve e lampi si muovono nella scheda del meteo") { weatherAnimated = it }
            Spacer(Modifier.height(10.dp))
            Note("Colore primario")
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Il primo campione è "automatico": il colore segue il momento della giornata.
                // Il secondo è il neutro: bianco con il tema scuro, nero con quello chiaro.
                val mono = monoAccent(p.dark)
                (listOf("Automatico" to null, (if (p.dark) "Bianco" else "Nero") to mono) + AccentChoices).forEach { (label, color) ->
                    val value = color?.toArgb() ?: 0
                    val selected = if (color == mono) accent != 0 && isMonoAccent(Color(accent)) else accent == value
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(color?.let { SolidColor(it) } ?: Brush.sweepGradient(AccentChoices.map { it.second } + AccentChoices.first().second))
                            .border(if (selected) 3.dp else 1.dp, p.text.copy(alpha = if (selected) 1f else 0.22f), CircleShape)
                            .then(if (selected) Modifier.padding(3.dp).border(2.dp, p.bottom, CircleShape) else Modifier)
                            .clickable(onClickLabel = label) { onAccent(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, label, Modifier.size(20.dp), tint = color?.let(::onColor) ?: Color.White)
                        else if (color == null) Text("A", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val accentName = when {
                accent == 0 -> null
                isMonoAccent(Color(accent)) -> if (p.dark) "Bianco (diventa nero con il tema chiaro)" else "Nero (diventa bianco con il tema scuro)"
                else -> AccentChoices.firstOrNull { it.second.toArgb() == accent }?.first ?: "personalizzato"
            }
            Note(if (accentName == null) "Automatico: il colore cambia con il momento della giornata." else "Colore fisso: $accentName.")
        }

        SettingsSection("Profilo", Icons.Rounded.Person, listOf(name, city.ifBlank { "posizione del telefono" }).filter { it.isNotBlank() }.joinToString(" · "), "Profilo" in openSections, { openSections = if ("Profilo" in openSections) openSections - "Profilo" else openSections + "Profilo" }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "Il tuo nome")
                Field(city, { city = it }, "Città", hint = "Lascia vuoto per usare la posizione del telefono")
                Field(goal, { v -> goal = v.filter { it.isDigit() }.take(5) }, "Obiettivo passi", keyboard = KeyboardType.Number)
            }
        }

        SettingsSection("Schede da mostrare", Icons.Rounded.Dashboard, "${CardKeys.labels.keys.count { it !in hidden }} accese su ${CardKeys.labels.size}", "Schede da mostrare" in openSections, { openSections = if ("Schede da mostrare" in openSections) openSections - "Schede da mostrare" else openSections + "Schede da mostrare" }) {
            CardKeys.labels.forEach { (cardKey, label) ->
                ToggleRow(label, cardKey !in hidden) { show -> hidden = if (show) hidden - cardKey else hidden + cardKey }
            }
            Spacer(Modifier.height(8.dp))
            Note("Il riepilogo in cima resta sempre visibile. Le schede spente non scaricano dati.")
        }

        SettingsSection("Ordine delle schede", Icons.Rounded.SwapVert, if (order.isEmpty()) "Automatico" else "Scelto da te", "Ordine delle schede" in openSections, { openSections = if ("Ordine delle schede" in openSections) openSections - "Ordine delle schede" else openSections + "Ordine delle schede" }) {
            ToggleRow("Ordine automatico", order.isEmpty(), detail = "Cambia con il momento della giornata") { auto ->
                order = if (auto) emptyList() else CardKeys.sortable
            }
            if (order.isNotEmpty()) {
                val full = CardKeys.ordered(order)
                val shown = full.filter { it !in hidden }
                // Le frecce scambiano la scheda con quella visibile più vicina, saltando quelle spente.
                fun swap(a: String, b: String) {
                    order = full.map { if (it == a) b else if (it == b) a else it }
                }
                Spacer(Modifier.height(4.dp))
                shown.forEachIndexed { i, cardKey ->
                    val label = CardKeys.labels[cardKey]?.substringBefore(" (") ?: cardKey
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.sub, modifier = Modifier.width(26.dp))
                        Text(label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { swap(cardKey, shown[i - 1]) }, enabled = i > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Sposta su $label", tint = if (i > 0) p.accent else p.sub.copy(alpha = 0.4f))
                        }
                        IconButton(onClick = { swap(cardKey, shown[i + 1]) }, enabled = i < shown.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Sposta giù $label", tint = if (i < shown.lastIndex) p.accent else p.sub.copy(alpha = 0.4f))
                        }
                    }
                }
                Note("Il riepilogo resta sempre in cima. Le schede spente non compaiono in questo elenco.")
            }
        }

        if (CardKeys.AGENDA !in hidden) SettingsSection("Calendari", Icons.Rounded.CalendarMonth, calendars.count { CalendarRepo.isSelected(it, calOn, calOff) }.let { n -> if (calendars.isEmpty()) "Accesso non concesso" else "$n accesi su ${calendars.size}" }, "Calendari" in openSections, { openSections = if ("Calendari" in openSections) openSections - "Calendari" else openSections + "Calendari" }) {
            if (calendars.isEmpty()) {
                Hint("Non vedo calendari: consenti l'accesso al calendario per sceglierli.")
                ActionButton("Consenti", onGrantCalendar)
            } else {
                calendars.forEach { c ->
                    val id = c.id.toString()
                    val on = CalendarRepo.isSelected(c, calOn, calOff)
                    val status = when {
                        c.id in syncing -> "download degli eventi richiesto…"
                        c.empty && on -> "eventi non ancora scaricati sul telefono"
                        c.empty -> "non scaricato sul telefono"
                        else -> null
                    }
                    ToggleRow(
                        label = c.name,
                        checked = on,
                        detail = listOfNotNull(c.account.takeIf { it.isNotBlank() && it != c.name }, status).joinToString(" · ").ifEmpty { null },
                        dot = Color(c.color).copy(alpha = 1f),
                    ) { turnOn ->
                        calOn = if (turnOn) calOn + id else calOn - id
                        calOff = if (turnOn) calOff - id else calOff + id
                        // Accendere un calendario che Android non scarica non basterebbe: si avvia anche il download.
                        if (turnOn && !c.synced) download(listOf(c))
                    }
                }
                val missing = calendars.filter { it.empty && CalendarRepo.isSelected(it, calOn, calOff) && it.id !in syncing }
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { download(missing) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Scarica gli eventi dei calendari accesi", color = p.accent)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val started = CalendarRepo.forceSync(calendars.filter { CalendarRepo.isSelected(it, calOn, calOff) })
                        if (started == 0) syncMessage = "Nessun calendario acceso da sincronizzare: quelli salvati solo sul telefono sono già aggiornati."
                        else {
                            forcing = true
                            syncMessage = null
                            pageScope.launch {
                                // Android non avvisa quando ha finito: si aspetta qualche secondo e si rilegge l'elenco.
                                delay(6000)
                                calendarsVersion++
                                forcing = false
                                syncMessage = "Sincronizzazione richiesta ad Android: tornando al brief trovi l'agenda aggiornata."
                            }
                        }
                    },
                    enabled = !forcing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (forcing) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = p.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (forcing) "Sincronizzo…" else "Sincronizza ora", color = p.accent)
                }
                syncMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Note(it)
                }
                Spacer(Modifier.height(8.dp))
                Note(
                    "Google Calendar mostra tutti i calendari, ma Android ne salva sul telefono solo alcuni: le altre app possono leggere solo quelli. " +
                        "Quando ne accendi uno non scaricato, Brieffo chiede ad Android di scaricarlo (serve il permesso di modifica del calendario, " +
                        "usato solo per questo). Gli eventi arrivano dopo qualche istante: torna al brief e trascina verso il basso per aggiornare."
                )
            }
        }

        if (CardKeys.NEWS !in hidden) SettingsSection("Notizie", Icons.Rounded.Newspaper, feeds.count { it.enabled }.let { n -> if (n == 0) "Nessuna fonte" else if (n == 1) "1 fonte" else "$n fonti" }, "Notizie" in openSections, { openSections = if ("Notizie" in openSections) openSections - "Notizie" else openSections + "Notizie" }) {
            FeedEditor(feeds) { feeds = it }
        }

        if (CardKeys.SPORT !in hidden) SettingsSection("La tua squadra", Icons.Rounded.SportsSoccer, (TeamRef.decode(teamRef)?.name ?: team).ifBlank { "Non scelta" }, "La tua squadra" in openSections, { openSections = if ("La tua squadra" in openSections) openSections - "La tua squadra" else openSections + "La tua squadra" }) {
            TeamPicker(team, teamRef) { t, r -> team = t; teamRef = r }
        }

        if (CardKeys.TRAVEL !in hidden) SettingsSection("Spostamenti", Icons.Rounded.Commute, work.ifBlank { "Indirizzo non impostato" }, "Spostamenti" in openSections, { openSections = if ("Spostamenti" in openSections) openSections - "Spostamenti" else openSections + "Spostamenti" }) {
            Field(work, { work = it }, "Indirizzo del lavoro", hint = "Via, numero e città. Lascia vuoto se non ti serve")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("car" to "In auto", "bike" to "In bici", "foot" to "A piedi").forEach { (value, label) ->
                    FilterChip(selected = mode == value, onClick = { mode = value }, label = { Text(label) })
                }
            }
        }

        SettingsSection("Lettura ad alta voce", Icons.Rounded.RecordVoiceOver, when (speechEngine) { "local" -> "Voce locale"; "gemini" -> "Voce di Gemini"; else -> "Voce del telefono" } + " · %.1f×".format(speechSpeed / 100f), "Lettura ad alta voce" in openSections, { openSections = if ("Lettura ad alta voce" in openSections) openSections - "Lettura ad alta voce" else openSections + "Lettura ad alta voce" }) {
            // Voce e velocità si salvano subito, così "Ascolta un esempio" usa già la scelta appena fatta.
            Text("Velocità: %.1f×".format(speechSpeed / 100f), fontSize = 15.sp)
            Slider(
                value = speechSpeed.toFloat(),
                onValueChange = { v -> speechSpeed = (v / 10).roundToInt() * 10; prefs.speechSpeed = speechSpeed },
                valueRange = 60f..160f, steps = 9,
            )
            Note("Chi legge")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("local" to "Voce locale", "gemini" to "Gemini", "phone" to "Telefono").forEach { (value, label) ->
                    FilterChip(selected = speechEngine == value, onClick = { speechEngine = value; prefs.speechEngine = value }, label = { Text(label) })
                }
            }
            when (speechEngine) {
                "local" -> {
                    Note("Voce")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LocalVoice.VOICES.forEach { v ->
                            FilterChip(selected = localVoice == v.id, onClick = { localVoice = v.id; prefs.localVoice = v.id }, label = { Text(v.label) })
                        }
                    }
                }
                "gemini" -> {
                    Note("Voce")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Summary.VOICES.forEach { (voice, character) ->
                            FilterChip(
                                selected = speechVoice == voice,
                                onClick = { speechVoice = voice; prefs.speechVoice = voice },
                                label = { Text("$voice · $character") },
                            )
                        }
                    }
                }
            }
            val localReady = remember(voicesVersion) { LocalVoice.installed(context) }
            val speaker = rememberSpeaker()
            OutlinedButton(
                onClick = {
                    // La chiave scritta in questa pagina si salva solo uscendo: per l'esempio serve subito.
                    prefs.geminiKey = key
                    speaker.toggle("Ciao, sono la voce che ti leggerà il riepilogo della giornata.")
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    when (speaker.status) {
                        Speaker.Status.LOADING -> "Preparo la voce…"
                        Speaker.Status.PLAYING -> "Ferma"
                        Speaker.Status.IDLE -> "Ascolta un esempio"
                    },
                    color = p.accent,
                )
            }
            speaker.note?.let {
                Spacer(Modifier.height(6.dp))
                Note(it)
            }
            Spacer(Modifier.height(8.dp))
            Note(
                when (speechEngine) {
                    "local" -> "Gratuita e senza limiti: la voce gira tutta sul telefono e il testo non esce da qui. Il modello (${LocalVoice.MEGABYTES} MB, uno solo per tutte le voci) si scarica al primo ascolto, poi funziona anche senza rete." + (if (localReady) " Già scaricato." else "")
                    "gemini" -> "La voce più naturale, ma serve la chiave di Gemini: il testo viene inviato a Google e il piano gratuito ha un numero limitato di letture al giorno."
                    else -> "La sintesi vocale di Android: sempre disponibile, meno naturale. Si regola solo la velocità."
                } + " Il tasto Ascolta è nella scheda del riepilogo."
            )
            if (speechEngine == "local" && localReady) {
                OutlinedButton(onClick = { LocalVoice.deleteAll(context); voicesVersion++ }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Elimina la voce scaricata", color = p.accent)
                }
            }
            // Finito un ascolto la voce può essere appena stata scaricata: le etichette si aggiornano.
            LaunchedEffect(speaker.status) { if (speaker.status == Speaker.Status.IDLE) voicesVersion++ }
        }

        SettingsSection("Notifiche", Icons.Rounded.NotificationsActive, if (notify) "Brief ogni giorno alle %02d:%02d".format(time.hour, time.minute) else "Brief giornaliero spento", "Notifiche" in openSections, { openSections = if ("Notifiche" in openSections) openSections - "Notifiche" else openSections + "Notifiche" }) {
            val whenFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM 'alle' HH:mm", Locale.ITALIAN) }
            val allowed = remember(notifyCheck) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            Note("Brieffo manda solo queste due notifiche, più quella di prova qui sotto.")
            if (!allowed) {
                Spacer(Modifier.height(8.dp))
                Text("Le notifiche di Brieffo sono bloccate da Android: finché restano così non arriva nulla.", fontSize = 14.sp, lineHeight = 19.sp, color = Color(0xFFE5484D))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text("Apri le impostazioni delle notifiche", color = p.accent) }
            }
            Spacer(Modifier.height(10.dp))

            Text("Brief giornaliero", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.sub)
            ToggleRow("Ricevi il brief ogni giorno", notify, detail = "Il riepilogo della giornata, all'ora che scegli") { notify = it }
            if (notify) {
                // Il quadrante si apre solo su richiesta: un campo orario prenderebbe il focus aprendo subito la tastiera.
                OutlinedButton(onClick = { pickTime = !pickTime }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Ore %02d:%02d".format(time.hour, time.minute), color = p.accent)
                }
                if (pickTime) TimePicker(time, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
                val now = LocalDateTime.now()
                val next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).let { if (it.isAfter(now)) it else it.plusDays(1) }
                Spacer(Modifier.height(6.dp))
                Note(
                    "Prossima: ${next.format(whenFormat)}." +
                        if (Alarms.exact(context)) "" else " Android non concede a Brieffo le sveglie precise: può arrivare con qualche minuto di ritardo."
                )
            }
            Spacer(Modifier.height(14.dp))

            Text("Promemoria della partita", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.sub)
            val reminderAt = remember(notifyCheck) { prefs.matchReminderAt.takeIf { prefs.matchReminder.isNotBlank() && it > System.currentTimeMillis() } }
            if (reminderAt != null) {
                val at = Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(prefs.matchReminderTitle, fontSize = 15.sp)
                        Text("Arriva ${at.format(whenFormat)}", fontSize = 12.sp, lineHeight = 16.sp, color = p.sub)
                    }
                    OutlinedButton(onClick = { MatchReminder.cancel(context); notifyCheck++ }) { Text("Togli", color = p.accent) }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Note("Nessuno in attesa. Si accende dal brief, con \"Avvisami ${MatchReminder.MINUTES_BEFORE} minuti prima\" sotto la prossima partita.")
            }
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    BriefWorker.runNow(context, test = true)
                    testSent = true
                    notifyCheck++
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Invia una notifica di prova", color = p.accent) }
            if (testSent) {
                Spacer(Modifier.height(6.dp))
                Note(if (allowed) "In arrivo tra qualche secondo: è il brief di adesso, come quello giornaliero." else "Inviata, ma con le notifiche bloccate non comparirà.")
            }
        }

        SettingsSection("Widget", Icons.Rounded.Widgets, "Partita, meteo, agenda e riepilogo sulla Home", "Widget" in openSections, { openSections = if ("Widget" in openSections) openSections - "Widget" else openSections + "Widget" }) {
            val manager = remember { AppWidgetManager.getInstance(context) }
            val canPin = remember { manager.isRequestPinAppWidgetSupported }
            listOf(
                Triple("Prossima partita", "La prossima gara della tua squadra, o il punteggio di quella in corso", MatchWidgetReceiver::class.java),
                Triple("Meteo", "Il tempo di adesso con minima, massima e pioggia", WeatherWidgetReceiver::class.java),
                Triple("Agenda", "I prossimi impegni di oggi e di domani", AgendaWidgetReceiver::class.java),
                Triple("Riepilogo", "Il riepilogo della giornata, come in cima al brief", SummaryWidgetReceiver::class.java),
            ).forEach { (label, detail, receiver) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(label, fontSize = 15.sp)
                        Text(detail, fontSize = 12.sp, lineHeight = 16.sp, color = p.sub)
                    }
                    if (canPin) OutlinedButton(onClick = { runCatching { manager.requestPinAppWidget(ComponentName(context, receiver), null, null) } }) {
                        Text("Aggiungi", color = p.accent)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Note(
                (if (canPin) "\"Aggiungi\" chiede alla schermata Home di mettere lì il widget. " else "") +
                    "Li trovi anche tenendo premuto su un punto vuoto della Home, alla voce Widget > Brieffo. " +
                    "Si aggiornano ogni ora circa e ogni volta che apri l'app; un tocco sul widget apre il brief."
            )
        }

        SettingsSection("Backup della configurazione", Icons.Rounded.ImportExport, "Esporta o importa le impostazioni", "Backup della configurazione" in openSections, { openSections = if ("Backup della configurazione" in openSections) openSections - "Backup della configurazione" else openSections + "Backup della configurazione" }) {
            Note("Per cambiare telefono: esporta la configurazione in un file, portalo sul nuovo telefono e importalo da qui.")
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { exportBackup.launch("brieffo-backup.json") }, modifier = Modifier.weight(1f)) {
                    Text("Esporta", color = p.accent)
                }
                OutlinedButton(onClick = { importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                    Text("Importa", color = p.accent)
                }
            }
            backupMessage?.let {
                Spacer(Modifier.height(8.dp))
                Note(it)
            }
        }

        SettingsSection("Riepilogo con IA", Icons.Rounded.AutoAwesome, if (key.isBlank()) "Scritto dall'app" else "Scritto da Gemini", "Riepilogo con IA" in openSections, { openSections = if ("Riepilogo con IA" in openSections) openSections - "Riepilogo con IA" else openSections + "Riepilogo con IA" }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Note("Con una chiave API di Google il riepilogo viene scritto da Gemini. È gratuita e non serve la carta di credito.")
                Column(
                    Modifier.fillMaxWidth().background(p.text.copy(alpha = 0.06f), RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Come ottenere la chiave", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    listOf(
                        "Tocca il pulsante qui sotto per aprire Google AI Studio.",
                        "Accedi con il tuo account Google e, se richiesto, accetta i termini.",
                        "Tocca \"Crea chiave API\" (Create API key) e conferma.",
                        "Copia la chiave che compare.",
                        "Torna in Brieffo e incollala nel campo qui sotto.",
                    ).forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.onAccent, textAlign = TextAlign.Center,
                                modifier = Modifier.size(22.dp).background(p.accent, CircleShape).wrapContentHeight(),
                            )
                            Text(step, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    OutlinedButton(onClick = { onOpenLink("https://aistudio.google.com/apikey") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Apri Google AI Studio", color = p.accent)
                    }
                }
                Column {
                    Note("Velocità di scrittura")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Summary.SPEED_CHOICES.forEach { (value, label, _) ->
                            FilterChip(selected = aiSpeed == value, onClick = { aiSpeed = value; keyTest = null }, label = { Text(label) })
                        }
                    }
                    Note(Summary.SPEED_CHOICES.first { it.first == aiSpeed }.third + " \"Prova la chiave\" qui sotto usa la velocità scelta e ti dice quanto ci ha messo.")
                }
                ToggleRow("Riepilogo chiuso all'apertura", collapsed, detail = "Mostra le prime due righe: il resto si apre con un tocco") { collapsed = it }
                Field(key, { key = it; keyTest = null }, "Chiave API Gemini (facoltativa)", secret = true)
                OutlinedButton(
                    onClick = {
                        testingKey = true
                        keyTest = null
                        pageScope.launch {
                            keyTest = withContext(Dispatchers.IO) {
                                runCatching {
                                    val start = System.currentTimeMillis()
                                    val model = Summary.testKey(key, aiSpeed)
                                    "La chiave funziona: ha risposto $model in %.1f secondi.".format((System.currentTimeMillis() - start) / 1000f)
                                }.getOrElse { e ->
                                    val detail = (e as? GeminiException)?.detail?.let { "\nRisposta di Google: $it" }.orEmpty()
                                    "La chiave non funziona: ${e.message ?: "errore sconosciuto"}.$detail"
                                }
                            }
                            testingKey = false
                        }
                    },
                    enabled = key.isNotBlank() && !testingKey,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (testingKey) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = p.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (testingKey) "Chiedo a Gemini…" else "Prova la chiave", color = if (key.isNotBlank()) p.accent else p.sub)
                }
                keyTest?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp) }
                Note(
                    "Con la chiave attiva, i dati di tutte le schede accese (compresi impegni, app più usate e nomi dei compleanni) vengono inviati a Google per generare il testo, al massimo ogni quattro ore o quando aggiorni tu. " +
                        "Senza chiave tutto resta sul telefono e il riepilogo è composto dall'app. La chiave è salvata solo su questo dispositivo."
                )
            }
        }

        OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
            Text("Rivedi la presentazione iniziale", color = p.accent)
        }
        UpdateCard()
        Note("Fonti dei dati: Open-Meteo, MeteoAlarm, BCE, CoinGecko, Wikipedia, OpenStreetMap, ESPN, TheSportsDB.")
        Spacer(Modifier.height(NavBarSpace))
    }
}

/** Campo per il nome della squadra con ricerca nel catalogo: [onChange] riceve il nome e la squadra scelta (vuota se nessuna). */
@Composable
internal fun TeamPicker(team: String, teamRef: String, onChange: (String, String) -> Unit) {
    val p = LocalPalette.current
    var found by remember { mutableStateOf<List<TeamRef>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val chosen = TeamRef.decode(teamRef)
    val search = {
        if (team.isNotBlank() && !searching) {
            focus.clearFocus()
            keyboard?.hide()
            searching = true
            scope.launch {
                found = withContext(Dispatchers.IO) { runCatching { SportRepo.search(team) }.getOrNull() }
                searching = false
            }
        }
    }
    // La ricerca parte dalla lente dentro il campo oppure dal tasto Cerca della tastiera.
    OutlinedTextField(
        team, { onChange(it, "") }, label = { Text("Nome della squadra") }, singleLine = true,
        supportingText = if (chosen == null) ({ Text("Scrivi il nome e cerca per scegliere quella giusta") }) else null,
        trailingIcon = {
            if (searching) CircularProgressIndicator(Modifier.size(20.dp), color = p.accent, strokeWidth = 2.dp)
            else IconButton(onClick = search, enabled = team.isNotBlank()) {
                Icon(Icons.Rounded.Search, "Cerca", tint = if (team.isNotBlank()) p.accent else p.sub.copy(alpha = 0.5f))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search() }),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    // Squadra scelta: stemma e nome a sinistra, logo e nome della lega a destra.
    if (chosen != null && found == null) {
        val logos by produceState<Pair<String?, String?>>(null to null, teamRef, p.dark) {
            value = withContext(Dispatchers.IO) { runCatching { SportRepo.logos(chosen, p.dark) }.getOrDefault(null to null) }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(p.accent.copy(alpha = 0.12f))
                .border(1.dp, p.accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (logos.first != null) AsyncImage(logos.first, null, Modifier.size(44.dp))
                else IconBadge(Icons.Rounded.SportsSoccer, size = 40.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(chosen.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("La tua squadra", fontSize = 12.sp, color = p.sub)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (logos.second != null) AsyncImage(logos.second, null, Modifier.size(30.dp))
                Text(chosen.subtitle, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = p.sub, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    found?.let { results ->
        if (results.isEmpty()) Note("Nessuna squadra trovata con questo nome nel catalogo principale: salvando, Brieffo la cercherà comunque nel catalogo di riserva.")
        results.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(p.text.copy(alpha = 0.06f))
                    .clickable {
                        onChange(r.name, r.encode())
                        found = null
                        // Scelta fatta: il campo perde il focus e la tastiera si chiude.
                        focus.clearFocus()
                        keyboard?.hide()
                    }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(r.subtitle, fontSize = 12.sp, color = p.sub)
                }
                Text("Scegli", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.accent)
            }
        }
    }
}

/**
 * Elenco delle fonti di notizie: ognuna si accende, si spegne o si elimina. In fondo il campo per aggiungerne una,
 * scrivendo l'indirizzo di un feed RSS oppure quello del sito (il feed viene cercato nella pagina).
 */
@Composable
internal fun FeedEditor(feeds: List<Feed>, onChange: (List<Feed>) -> Unit) {
    val p = LocalPalette.current
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val latest by rememberUpdatedState(feeds)

    val add = {
        if (input.isNotBlank() && !busy) {
            focus.clearFocus()
            keyboard?.hide()
            busy = true
            message = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { NewsRepo.resolve(input) }.getOrNull() }
                val found = result?.first
                when {
                    found == null -> message = "Non trovo un feed su questo sito. Cerca \"RSS\" sul sito e incolla qui l'indirizzo del feed."
                    latest.any { it.url == found.url } -> message = "Questa fonte è già nell'elenco."
                    else -> {
                        // Due fonti con lo stesso nome si confonderebbero nei filtri: alla seconda si aggiunge un numero.
                        var name = found.name
                        var n = 2
                        while (latest.any { it.name == name }) name = "${found.name} ${n++}"
                        onChange(latest + found.copy(name = name))
                        input = ""
                        message = if (result.second) "Aggiunta: $name" else "Aggiunta: $name. Al momento non ha notizie degli ultimi 3 giorni, quindi nel brief non comparirà."
                    }
                }
                busy = false
            }
        }
    }

    if (feeds.isEmpty()) Note("Nessuna fonte per ora. Brieffo non include giornali: scegli tu quali leggere.")
    feeds.forEach { f ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(topicColor(f.name), CircleShape))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(f.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(f.url.removePrefix("https://").removePrefix("http://").removePrefix("www."), fontSize = 12.sp, color = p.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onChange(feeds - f) }) { Icon(Icons.Rounded.DeleteOutline, "Elimina ${f.name}", tint = p.sub) }
            Switch(f.enabled, { on -> onChange(feeds.map { if (it === f) it.copy(enabled = on) else it }) })
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        input, { input = it; message = null }, label = { Text("Aggiungi una fonte") }, singleLine = true,
        placeholder = { Text("sito o indirizzo del feed RSS") },
        supportingText = { Text(message ?: "Scrivi il sito di un giornale o di un blog, oppure incolla l'indirizzo del suo feed RSS.") },
        trailingIcon = {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = p.accent, strokeWidth = 2.dp)
            else IconButton(onClick = add, enabled = input.isNotBlank()) {
                Icon(Icons.Rounded.Add, "Aggiungi", tint = if (input.isNotBlank()) p.accent else p.sub.copy(alpha = 0.5f))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
