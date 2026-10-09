package com.brieffo.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brieffo.app.data.AlertsRepo
import com.brieffo.app.data.BriefState
import com.brieffo.app.data.CardKeys
import com.brieffo.app.data.Event
import com.brieffo.app.data.Occasions
import com.brieffo.app.data.OccasionsRepo
import com.brieffo.app.data.SportRepo
import com.brieffo.app.data.TravelRepo
import com.brieffo.app.data.CalendarRepo
import com.brieffo.app.data.Daypart
import com.brieffo.app.data.DeviceRepo
import com.brieffo.app.data.ExtrasRepo
import com.brieffo.app.data.HealthRepo
import com.brieffo.app.data.HealthState
import com.brieffo.app.data.NewsRepo
import com.brieffo.app.data.Prefs
import com.brieffo.app.data.Summary
import com.brieffo.app.data.UsageRepo
import com.brieffo.app.data.UsageState
import com.brieffo.app.data.WeatherRepo
import com.brieffo.app.widget.WidgetStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class BriefViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)

    private val _state = MutableStateFlow(BriefState(daypart = Daypart.of(LocalDateTime.now().hour), now = LocalDateTime.now()))
    val state = _state.asStateFlow()

    private var job: Job? = null
    private var lastRefresh = 0L

    init {
        refresh()
    }

    /** Al ritorno in primo piano il brief si ricarica da solo soltanto se è più vecchio di [MAX_AGE]. */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastRefresh > MAX_AGE) refresh()
    }

    /**
     * Ricarica i dati del brief. Il riepilogo di Gemini viene riscritto solo se quello salvato è più vecchio
     * di [MAX_AGE] oppure se lo chiede l'utente trascinando verso il basso ([forceAi]): così le richieste non si accumulano.
     */
    fun refresh(forceAi: Boolean = false) {
        lastRefresh = System.currentTimeMillis()
        job?.cancel()
        job = viewModelScope.launch {
            val app = getApplication<Application>()
            val now = LocalDateTime.now()
            val key = prefs.geminiKey
            val savedAi = prefs.aiSummary.takeIf { !forceAi && it.isNotBlank() && System.currentTimeMillis() - prefs.aiSummaryAt < MAX_AGE }
            _state.update {
                it.copy(
                    loading = true, summaryLoading = key.isNotBlank() && savedAi == null, now = now, daypart = Daypart.of(now.hour),
                    name = prefs.name, stepGoal = prefs.stepGoal,
                    calendarGranted = CalendarRepo.granted(app),
                    moon = DeviceRepo.moon(), battery = DeviceRepo.battery(app),
                )
            }
            val hidden = prefs.hidden
            fun on(key: String) = key !in hidden
            _state.update {
                it.copy(
                    hidden = hidden,
                    cardOrder = prefs.cardOrder,
                    summaryCollapsed = prefs.summaryCollapsed,
                    railRight = prefs.railRight,
                    weatherAnimated = prefs.weatherAnimated,
                    travelConfigured = prefs.workAddress.isNotBlank(),
                    sportConfigured = prefs.team.isNotBlank() || prefs.teamRef.isNotBlank(),
                    newsConfigured = prefs.feeds.any { f -> f.enabled },
                    nextAlarm = OccasionsRepo.nextAlarm(app),
                )
            }
            // Ogni sezione si carica per conto suo: una fonte lenta o guasta non blocca le altre.
            // Le schede nascoste non vengono nemmeno scaricate.
            coroutineScope {
                val place = async(Dispatchers.IO) { runCatching { WeatherRepo.resolvePlace(app, prefs) }.getOrNull() }
                val events = async(Dispatchers.IO) {
                    runCatching { CalendarRepo.load(app) }.getOrDefault(emptyList<Event>() to emptyList())
                }
                launch(Dispatchers.IO) {
                    val w = if (on(CardKeys.WEATHER)) runCatching { place.await()?.let(WeatherRepo::fetch) }.getOrNull() else null
                    _state.update { it.copy(weather = w ?: it.weather.takeIf { on(CardKeys.WEATHER) }, weatherLoaded = true) }
                }
                launch(Dispatchers.IO) {
                    val a = if (on(CardKeys.ALERTS)) runCatching { place.await()?.let(AlertsRepo::load) }.getOrNull() else null
                    _state.update { it.copy(alerts = a ?: emptyList()) }
                }
                launch(Dispatchers.IO) {
                    val (today, tomorrow) = events.await()
                    val read = runCatching { CalendarRepo.selected(app) }.getOrDefault(emptyList())
                    _state.update {
                        it.copy(
                            today = today, tomorrow = tomorrow,
                            calendars = read.map { c -> c.name }.distinct(),
                            // Scelti ma senza eventi sul telefono: vanno segnalati, altrimenti sembrano vuoti.
                            calendarsOff = read.filter { c -> c.empty }.map { c -> c.name }.distinct(),
                        )
                    }
                }
                launch(Dispatchers.IO) {
                    val t = if (on(CardKeys.TRAVEL)) runCatching { WeatherRepo.travelOrigin(app, prefs)?.let { TravelRepo.load(prefs, it, events.await().first) } }.getOrNull() else null
                    _state.update { it.copy(travel = t ?: emptyList()) }
                }
                if (on(CardKeys.HEALTH)) launch(Dispatchers.IO) {
                    val h = runCatching { HealthRepo.load(app) }.getOrDefault(HealthState.Unavailable)
                    _state.update { it.copy(health = h) }
                }
                if (on(CardKeys.USAGE)) launch(Dispatchers.IO) {
                    val u = runCatching { UsageRepo.load(app) }.getOrDefault(UsageState.NeedsPermission)
                    _state.update { it.copy(usage = u) }
                }
                if (on(CardKeys.NEWS)) launch(Dispatchers.IO) {
                    val n = runCatching { NewsRepo.load(prefs.feeds) }.getOrDefault(emptyList())
                    _state.update { it.copy(news = n) }
                }
                if (on(CardKeys.MARKETS)) launch(Dispatchers.IO) {
                    val m = runCatching { ExtrasRepo.markets() }.getOrDefault(emptyList())
                    _state.update { it.copy(markets = m.ifEmpty { it.markets }) }
                }
                if (on(CardKeys.EXTRAS)) launch(Dispatchers.IO) {
                    val o = runCatching { ExtrasRepo.onThisDay() }.getOrNull()
                    _state.update { it.copy(onThisDay = o ?: it.onThisDay) }
                }
                launch(Dispatchers.IO) {
                    val sp = if (on(CardKeys.SPORT)) runCatching { SportRepo.load(prefs) }.getOrNull() else null
                    _state.update { it.copy(sport = sp) }
                }
                if (on(CardKeys.OCCASIONS)) launch(Dispatchers.IO) {
                    val o = Occasions(
                        saints = runCatching { OccasionsRepo.saints() }.getOrDefault(_state.value.occasions.saints),
                        birthdays = runCatching { OccasionsRepo.birthdays(app) }.getOrDefault(emptyList()),
                        holiday = runCatching { OccasionsRepo.holiday() }.getOrNull() ?: _state.value.occasions.holiday,
                        contactsGranted = OccasionsRepo.contactsGranted(app),
                    )
                    _state.update { it.copy(occasions = o) }
                }
            }
            if (key.isBlank()) {
                _state.update { it.copy(loading = false, summary = Summary.rules(it), summaryByAi = false, summaryLoading = false, aiError = null) }
            } else if (savedAi != null) {
                _state.update { it.copy(loading = false, summary = savedAi, summaryByAi = true, summaryLoading = false, aiError = null) }
            } else {
                // Il riepilogo dell'app resta di riserva: finché Gemini scrive, la scheda mostra il caricamento.
                _state.update { it.copy(loading = false, aiError = null) }
                val snapshot = _state.value
                runCatching { withContext(Dispatchers.IO) { Summary.gemini(key, snapshot, prefs.aiSpeed) } }
                    .onSuccess { text ->
                        prefs.aiSummary = text
                        _state.update { it.copy(summary = text, summaryByAi = true, summaryLoading = false) }
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        _state.update {
                            it.copy(summary = Summary.rules(it), summaryByAi = false, summaryLoading = false, aiError = e.message ?: "connessione non riuscita")
                        }
                    }
            }
            // I widget della schermata Home mostrano gli stessi dati appena caricati.
            val shown = _state.value
            withContext(Dispatchers.IO) { runCatching { WidgetStore.update(app, shown) } }
        }
    }

    private companion object {
        /** Ogni quanto il brief, e con lui il riepilogo di Gemini, si aggiorna da solo: quattro ore. */
        const val MAX_AGE = 4 * 60 * 60 * 1000L
    }
}
