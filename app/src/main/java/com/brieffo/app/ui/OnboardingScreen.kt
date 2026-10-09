package com.brieffo.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.brieffo.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.brieffo.app.data.CardKeys
import com.brieffo.app.data.HealthRepo
import com.brieffo.app.data.Prefs

private const val STEPS = 7

/** Cerchio sfumato con un'icona: l'illustrazione in cima a ogni passo. */
@Composable
private fun Illustration(icon: ImageVector) {
    val p = LocalPalette.current
    // Anello sfumato all'esterno, disco pieno nel colore primario all'interno: l'icona resta sempre ben leggibile.
    Box(
        Modifier.size(112.dp).background(Brush.linearGradient(listOf(p.blobs[0], p.blobs[1], p.blobs[2])), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(92.dp).background(p.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(46.dp), tint = p.onAccent)
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector?, title: String, subtitle: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Senza icona si mostra il logo dell'app, su fondo bianco come nell'icona.
        if (icon != null) Illustration(icon) else {
            Image(
                painterResource(R.drawable.logo), "Brieffo",
                modifier = Modifier.size(128.dp).clip(RoundedCornerShape(34.dp)),
            )
        }
        Text(title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 22.dp))
        Text(subtitle, fontSize = 15.sp, lineHeight = 22.sp, color = p.sub, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
    }
}

/** Riquadro traslucido come le schede del brief, senza intestazione. */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 38.dp)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = LocalPalette.current.sub)
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, text: String, granted: Boolean) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 38.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = p.sub)
        }
        if (granted) {
            Box(Modifier.size(24.dp).background(Color(0xFF1F9D61), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Consentito", Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}

/**
 * Presentazione del primo avvio: spiega l'app e raccoglie i dati di base, un passo alla volta.
 * Tutto è facoltativo e si ritrova poi nelle impostazioni; i valori si salvano alla fine.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(prefs: Prefs, onGrantBasics: () -> Unit, onConnectHealth: () -> Unit, onDone: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    var name by remember { mutableStateOf(prefs.name) }
    var city by remember { mutableStateOf(prefs.city) }
    var hidden by remember { mutableStateOf(prefs.hidden) }
    var feeds by remember { mutableStateOf(prefs.feeds) }
    var team by remember { mutableStateOf(prefs.team) }
    var teamRef by remember { mutableStateOf(prefs.teamRef) }
    var work by remember { mutableStateOf(prefs.workAddress) }
    var workPoint by remember { mutableStateOf(prefs.workPoint?.let { "${it.first},${it.second}" } ?: "") }
    var modes by remember { mutableStateOf(prefs.travelModes) }
    var notify by remember { mutableStateOf(prefs.notifyEnabled) }
    var pickTime by remember { mutableStateOf(false) }
    val time = rememberTimePickerState(prefs.notifyHour, prefs.notifyMinute, is24Hour = true)

    // I permessi cambiano mentre l'app è in pausa sulla finestra di sistema: al rientro si ricontrolla.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    fun granted(permission: String) = resumes >= 0 && ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val healthAvailable = remember { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }
    val healthGranted by produceState(false, resumes) {
        value = healthAvailable && runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().any { it in HealthRepo.permissions }
        }.getOrDefault(false)
    }

    val finish = {
        prefs.name = name
        prefs.city = city
        prefs.hidden = hidden
        prefs.feeds = feeds
        prefs.team = team
        prefs.teamRef = teamRef
        prefs.workAddress = work
        prefs.workPoint = workPoint.split(',').mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 2 }?.let { it[0] to it[1] }
        prefs.travelModes = modes
        prefs.notifyEnabled = notify
        prefs.notifyHour = time.hour
        prefs.notifyMinute = time.minute
        prefs.askedPermissions = true
        onDone()
    }
    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp)) {
        // Avanzamento: un segmento per passo.
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(STEPS) { i ->
                val fill by animateFloatAsState(if (i <= step) 1f else 0f, label = "progress")
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(p.text.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(fill).height(4.dp).background(p.accent))
                }
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 3 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 * dir } + fadeOut())
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "step",
        ) { current ->
            val stepScroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalFade(stepScroll).verticalScroll(stepScroll).padding(top = 28.dp, bottom = 12.dp)) {
                when (current) {
                    0 -> {
                        StepHeader(null, "Ciao, sono Brieffo", "Il riepilogo della tua giornata, pronto ogni volta che apri l'app.")
                        Panel {
                            FeatureRow(Icons.Rounded.WbSunny, "Tutto in un colpo d'occhio", "Meteo, agenda, spostamenti e salute riassunti in poche righe.")
                            FeatureRow(Icons.Rounded.Brightness6, "Cambia con l'ora", "Al mattino guarda avanti, la sera tira il bilancio della giornata.")
                            FeatureRow(Icons.Rounded.Tune, "Scegli tu cosa vedere", "Notizie, squadra, mercati, ricorrenze: tieni solo quello che ti serve.")
                            FeatureRow(Icons.Rounded.Lock, "I tuoi dati restano tuoi", "Calendario, salute e posizione non lasciano il telefono.")
                        }
                    }
                    1 -> {
                        StepHeader(Icons.Rounded.WavingHand, "Come ti chiami?", "Serve solo per salutarti. Puoi anche lasciare vuoto.")
                        Panel {
                            Field(name, { name = it }, "Il tuo nome")
                            Field(city, { city = it }, "Città", hint = "Per meteo e allerte. Lascia vuoto per usare la posizione del telefono")
                        }
                    }
                    2 -> {
                        StepHeader(Icons.Rounded.VpnKey, "Qualche permesso", "Servono per leggere i tuoi dati dal telefono. Sono tutti facoltativi: ciò che non consenti resta semplicemente vuoto.")
                        val notifications = android.os.Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
                        Panel {
                            PermissionRow(Icons.Rounded.Place, "Posizione approssimativa", "Per meteo, allerte e tempi di spostamento", granted(Manifest.permission.ACCESS_COARSE_LOCATION))
                            PermissionRow(Icons.Rounded.CalendarMonth, "Calendario", "Per mostrarti gli impegni di oggi e domani", granted(Manifest.permission.READ_CALENDAR))
                            PermissionRow(Icons.Rounded.NotificationsActive, "Notifiche", "Per inviarti il brief ogni giorno", notifications)
                            val all = granted(Manifest.permission.ACCESS_COARSE_LOCATION) && granted(Manifest.permission.READ_CALENDAR) && notifications
                            if (!all) {
                                Button(
                                    onClick = { prefs.askedPermissions = true; onGrantBasics() },
                                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Consenti") }
                            }
                        }
                        if (healthAvailable) {
                            Spacer(Modifier.height(12.dp))
                            Panel {
                                PermissionRow(Icons.Rounded.SelfImprovement, "Salute e attività", "Passi, sonno e battito da Health Connect, in sola lettura", healthGranted)
                                if (!healthGranted) OutlinedButton(onClick = onConnectHealth, modifier = Modifier.fillMaxWidth()) { Text("Collega Health Connect", color = p.accent) }
                            }
                        }
                    }
                    3 -> {
                        StepHeader(Icons.Rounded.Tune, "Cosa vuoi vedere?", "Tocca per accendere o spegnere le schede del tuo brief. Potrai cambiare idea quando vuoi.")
                        Panel {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CardKeys.labels.forEach { (key, label) ->
                                    FilterChip(
                                        selected = key !in hidden,
                                        onClick = { hidden = if (key in hidden) hidden - key else hidden + key },
                                        label = { Text(label.substringBefore(" (")) },
                                    )
                                }
                            }
                        }
                        if (CardKeys.NEWS !in hidden) {
                            Spacer(Modifier.height(12.dp))
                            Panel {
                                Text("Da dove leggi le notizie", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                FeedEditor(feeds) { feeds = it }
                            }
                        }
                    }
                    4 -> {
                        StepHeader(Icons.Rounded.SportsSoccer, "Squadra e spostamenti", "Due dettagli per rendere il brief più tuo. Salta pure quello che non ti interessa.")
                        if (CardKeys.SPORT !in hidden) Panel {
                            Text("La tua squadra", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            TeamPicker(team, teamRef) { t, r -> team = t; teamRef = r }
                        }
                        if (CardKeys.SPORT !in hidden && CardKeys.TRAVEL !in hidden) Spacer(Modifier.height(12.dp))
                        if (CardKeys.TRAVEL !in hidden) Panel {
                            Text("Dove lavori o studi", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            TravelSetup(work, workPoint, modes, "Via, numero e città: ti dirò quanto ci metti ad arrivare", { address, point -> work = address; workPoint = point }) { modes = it }
                        }
                        if (CardKeys.SPORT in hidden && CardKeys.TRAVEL in hidden) Panel {
                            Text("Hai spento sia la squadra sia gli spostamenti: qui non c'è nulla da impostare.", fontSize = 14.sp, color = p.sub)
                        }
                    }
                    5 -> {
                        StepHeader(Icons.Rounded.NotificationsActive, "Il brief ogni giorno", "Posso mandarti una notifica con il riepilogo all'ora che preferisci.")
                        Panel {
                            ToggleRow("Notifica giornaliera", notify) { notify = it }
                            if (notify) {
                                OutlinedButton(onClick = { pickTime = !pickTime }) { Text("Ore %02d:%02d".format(time.hour, time.minute), color = p.accent) }
                                if (pickTime) TimePicker(time, modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Panel {
                            FeatureRow(Icons.Rounded.SmartToy, "Riepilogo scritto dall'IA", "Facoltativo e gratuito: trovi la guida nelle impostazioni.")
                        }
                    }
                    else -> {
                        StepHeader(Icons.Rounded.CheckCircle, if (name.isBlank()) "Tutto pronto!" else "Tutto pronto, ${name.trim()}!", "Il tuo primo brief ti aspetta.")
                        Panel {
                            FeatureRow(Icons.Rounded.Refresh, "Trascina verso il basso", "Per aggiornare il brief in qualsiasi momento.")
                            FeatureRow(Icons.Rounded.Settings, "Impostazioni in alto a destra", "Per cambiare schede, calendari, squadra e tutto il resto.")
                            FeatureRow(Icons.Rounded.VerifiedUser, "Permessi mancanti", "Ogni scheda ti dice cosa le serve e ti porta ad attivarlo.")
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Indietro", color = p.sub) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (step == STEPS - 1) finish() else step++ },
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                modifier = Modifier.height(52.dp),
            ) {
                Text(
                    when (step) { 0 -> "Iniziamo"; STEPS - 1 -> "Apri il mio brief"; else -> "Continua" },
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
    }
}
