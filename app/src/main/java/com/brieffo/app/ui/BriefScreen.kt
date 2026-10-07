package com.brieffo.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brieffo.app.data.BriefState
import com.brieffo.app.data.CardKeys
import com.brieffo.app.data.Daypart
import com.brieffo.app.data.NewsRepo
import com.brieffo.app.data.Prefs
import java.time.format.DateTimeFormatter
import java.util.Locale

class BriefActions(
    val refresh: () -> Unit,
    val openSettings: () -> Unit,
    val grantBasics: () -> Unit,
    val connectHealth: () -> Unit,
    val grantUsage: () -> Unit,
    val openAppInfo: () -> Unit,
    val openLink: (String) -> Unit,
    val grantContacts: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefScreen(s: BriefState, actions: BriefActions, scroll: ScrollState = rememberScrollState()) {
    val p = LocalPalette.current
    // Dove comincia ogni scheda nella pagina (pixel) e quanto è alta: serve all'indice laterale.
    val bounds = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    var shown by remember { mutableStateOf(emptyList<String>()) }
    PullToRefreshBox(isRefreshing = s.loading, onRefresh = actions.refresh, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalFade(scroll)
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        s.now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() } + " · " + s.daypart.title,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = p.sub,
                    )
                    Text(
                        s.daypart.greeting + if (s.name.isNotBlank()) ",\n${s.name}" else "",
                        fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            val all = mapOf<String, @Composable () -> Unit>(
                CardKeys.WEATHER to {
                    WeatherCard(
                        s.weather, s.weatherLoaded, s.alerts, s.shows(CardKeys.POLLEN), s.weatherAnimated,
                        if (s.weatherLoaded && s.weather == null) actions.openSettings else actions.grantBasics,
                    )
                },
                CardKeys.AGENDA to { AgendaCard(s, actions.grantBasics) },
                CardKeys.TRAVEL to { TravelCard(s, actions.openSettings) },
                CardKeys.HEALTH to { HealthCard(s, actions.connectHealth) },
                CardKeys.USAGE to { UsageCard(s, actions.grantUsage, actions.openAppInfo) },
                CardKeys.NEWS to { NewsCard(s.news, s.loading, s.newsConfigured, actions.openSettings, actions.openLink) },
                CardKeys.SPORT to { SportCard(s, actions.openSettings) },
                CardKeys.MARKETS to { MarketsCard(s.markets) },
                CardKeys.OCCASIONS to { OccasionsCard(s, actions.grantContacts) },
                CardKeys.EXTRAS to { ExtrasCard(s.onThisDay, s.moon, s.battery, s.nextAlarm) },
            )
            // Senza un ordine scelto a mano si segue il momento della giornata: al mattino si guarda avanti, la sera si tira il bilancio.
            val order = if (s.cardOrder.isNotEmpty()) CardKeys.ordered(s.cardOrder) else with(CardKeys) {
                when (s.daypart) {
                    Daypart.MORNING -> listOf(WEATHER, TRAVEL, AGENDA, HEALTH, NEWS, SPORT, OCCASIONS, MARKETS, EXTRAS, USAGE)
                    Daypart.MIDDAY -> listOf(AGENDA, TRAVEL, WEATHER, HEALTH, NEWS, SPORT, MARKETS, USAGE, OCCASIONS, EXTRAS)
                    Daypart.EVENING, Daypart.NIGHT -> listOf(HEALTH, USAGE, AGENDA, WEATHER, SPORT, NEWS, MARKETS, OCCASIONS, EXTRAS)
                }
            }
            val keys = listOf(SUMMARY_SECTION) + order.filter { s.shows(it) }
            SideEffect { if (shown != keys) shown = keys }
            keys.forEachIndexed { i, key ->
                Box(Modifier.onGloballyPositioned { c -> bounds[key] = c.positionInParent().y.toInt() to c.size.height }) {
                    Appear(i) { if (key == SUMMARY_SECTION) SummaryCard(s) else all.getValue(key)() }
                }
            }

            // Spazio per la barra di navigazione fluttuante.
            Spacer(Modifier.height(NavBarSpace))
        }
        // Le schede che in questo momento non mostrano nulla non entrano nell'indice.
        val sections = shown.mapNotNull { key ->
            bounds[key]?.takeIf { it.second > 0 }?.let { Section(key, sectionLabel(key), sectionIcon(key), it.first) }
        }
        SectionRail(sections, scroll, s.railRight, Modifier.align(if (s.railRight) Alignment.CenterEnd else Alignment.CenterStart).padding(bottom = NavBarSpace / 2))
    }
}

