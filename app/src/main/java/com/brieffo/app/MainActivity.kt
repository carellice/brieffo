package com.brieffo.app

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.brieffo.app.ui.GlassNavBar
import com.brieffo.app.ui.captureTo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brieffo.app.data.HealthRepo
import com.brieffo.app.notify.BriefWorker
import com.brieffo.app.ui.AuroraBackground
import com.brieffo.app.ui.BriefActions
import com.brieffo.app.ui.BriefScreen
import com.brieffo.app.ui.LocalPalette
import com.brieffo.app.ui.OnboardingScreen
import com.brieffo.app.ui.SettingsScreen
import com.brieffo.app.ui.paletteFor

class MainActivity : ComponentActivity() {
    private val vm: BriefViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BriefWorker.schedule(this)

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            var themeMode by remember { mutableStateOf(vm.prefs.themeMode) }
            var accent by remember { mutableIntStateOf(vm.prefs.accent) }
            val dark = when (themeMode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            val palette = paletteFor(state.daypart, dark, accent.takeIf { it != 0 }?.let { Color(it) })
            // 0 = brief, 1 = impostazioni.
            var tab by rememberSaveable { mutableIntStateOf(0) }
            val briefScroll = rememberScrollState()
            var onboarding by remember { mutableStateOf(!vm.prefs.onboarded) }

            LaunchedEffect(palette.dark) {
                val bars = if (palette.dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }

            val basics = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                vm.prefs.askedPermissions = true
                vm.refresh()
            }
            val health = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refresh() }
            val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
            val basicPermissions = buildList {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.READ_CALENDAR)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()

            // Durante la presentazione iniziale i permessi li chiede il suo passo dedicato.
            LaunchedEffect(onboarding) {
                if (!onboarding && !vm.prefs.askedPermissions) basics.launch(basicPermissions)
            }
            LifecycleResumeEffect(Unit) {
                vm.refreshIfStale()
                onPauseOrDispose { }
            }

            val actions = remember {
                BriefActions(
                    refresh = { vm.refresh(forceAi = true) },
                    openSettings = { tab = 1 },
                    grantBasics = { basics.launch(basicPermissions) },
                    connectHealth = { health.launch(HealthRepo.permissions) },
                    grantUsage = { runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } },
                    openAppInfo = {
                        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) }
                    },
                    grantContacts = { contacts.launch(Manifest.permission.READ_CONTACTS) },
                    openLink = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                )
            }

            // Lo schema Material deriva dalla tavolozza del brief, così campi, interruttori e chip hanno gli stessi colori.
            val base = if (palette.dark) darkColorScheme() else lightColorScheme()
            val scheme = base.copy(
                primary = palette.accent, onPrimary = palette.onAccent,
                secondaryContainer = palette.accent.copy(alpha = 0.22f), onSecondaryContainer = palette.text,
                surface = palette.bottom, onSurface = palette.text,
                surfaceVariant = palette.text.copy(alpha = 0.08f), onSurfaceVariant = palette.sub,
                surfaceContainerHighest = palette.text.copy(alpha = 0.10f),
                outline = palette.sub.copy(alpha = 0.55f), outlineVariant = palette.text.copy(alpha = 0.12f),
            )
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalPalette provides palette) {
                    val backdrop = rememberGraphicsLayer()
                    Box(Modifier.fillMaxSize()) {
                        // Tutto ciò che sta sotto la barra viene registrato, così lei può sfocarlo come un vetro.
                        Box(Modifier.fillMaxSize().captureTo(backdrop)) {
                            AuroraBackground {
                                if (onboarding) {
                                    OnboardingScreen(
                                        prefs = vm.prefs,
                                        onGrantBasics = actions.grantBasics,
                                        onConnectHealth = actions.connectHealth,
                                        onDone = {
                                            vm.prefs.onboarded = true
                                            onboarding = false
                                            tab = 0
                                            BriefWorker.schedule(this@MainActivity)
                                            vm.refresh()
                                        },
                                    )
                                } else AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        // La pagina nuova entra dal lato della sua voce nella barra, quella vecchia esce dall'altro.
                                        val dir = if (targetState > initialState) 1 else -1
                                        val move = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                                        (slideInHorizontally(move) { it / 4 * dir } + fadeIn(tween(220)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith
                                            (slideOutHorizontally(move) { -it / 4 * dir } + fadeOut(tween(160)) + scaleOut(tween(320), targetScale = 0.96f))
                                    },
                                    label = "page",
                                ) { page ->
                                    if (page == 1) {
                                        SettingsScreen(
                                            prefs = vm.prefs,
                                            themeMode = themeMode,
                                            accent = accent,
                                            onTheme = { themeMode = it; vm.prefs.themeMode = it },
                                            onAccent = { accent = it; vm.prefs.accent = it },
                                            onOpenLink = actions.openLink,
                                            onGrantCalendar = actions.grantBasics,
                                            onReplayOnboarding = { vm.prefs.onboarded = false; onboarding = true },
                                            onBack = { tab = 0 },
                                            onSaved = {
                                                BriefWorker.schedule(this@MainActivity)
                                                vm.refresh()
                                            },
                                        )
                                    } else BriefScreen(state, actions, briefScroll)
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = !onboarding,
                            enter = slideInVertically { it * 2 } + fadeIn(),
                            exit = slideOutVertically { it * 2 } + fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
                        ) { GlassNavBar(tab, { tab = it }, backdrop) }
                    }
                }
            }
        }
    }
}
