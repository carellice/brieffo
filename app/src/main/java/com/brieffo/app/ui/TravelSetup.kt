package com.brieffo.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.brieffo.app.data.Address
import com.brieffo.app.data.Prefs
import com.brieffo.app.data.SavedPlace
import com.brieffo.app.data.TravelRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * Tutto ciò che serve per i tempi di spostamento: le destinazioni salvate (lavoro, casa, palestra…), ciascuna con
 * l'indirizzo scritto a mano o cercato sulla mappa, i mezzi con cui calcolare il tragitto, anche più d'uno,
 * e la scelta di usare la posizione precisa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TravelSetup(
    places: List<SavedPlace>,
    modes: List<String>,
    onPlaces: (List<SavedPlace>) -> Unit,
    onModes: (List<String>) -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    // Indice della destinazione di cui si sta cercando l'indirizzo sulla mappa, o -1.
    var picking by remember { mutableIntStateOf(-1) }
    // Luogo personalizzato che si sta creando o modificando, o null.
    var editing by remember { mutableStateOf<SavedPlace?>(null) }
    var check by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        check++
        onPauseOrDispose { }
    }
    val precise = remember(check) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val askPrecise = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { check++ }

    // Solo destinazioni preimpostate, ciascuna con l'indirizzo scelto sulla mappa: niente nomi o indirizzi scritti a mano,
    // così ogni tragitto parte da coordinate sicure.
    TravelRepo.PRESETS.forEachIndexed { i, name ->
        val place = places.firstOrNull { it.name == name }
        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.text.copy(alpha = 0.08f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                placeIcon(SavedPlace(name, "")),
                null, Modifier.size(22.dp), tint = if (place != null) p.accent else p.sub,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        place == null -> "Non impostato"
                        place.lat == null -> "${place.address} · da confermare sulla mappa"
                        else -> place.address
                    },
                    fontSize = 12.sp, lineHeight = 16.sp, color = if (place?.lat == null && place != null) p.accent else p.sub,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (place != null) IconButton(onClick = { onPlaces(places - place) }) { Icon(Icons.Rounded.DeleteOutline, "Togli $name", tint = p.sub) }
            OutlinedButton(onClick = { picking = i }) { Text(if (place == null) "Imposta" else "Cambia", color = p.accent) }
        }
    }
    // I luoghi aggiunti a mano, con il loro nome e la loro icona.
    places.filter { it.custom }.forEach { place ->
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.text.copy(alpha = 0.08f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(placeIcon(place), null, Modifier.size(22.dp), tint = p.accent)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(place.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(place.address, fontSize = 12.sp, lineHeight = 16.sp, color = p.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onPlaces(places - place) }) { Icon(Icons.Rounded.DeleteOutline, "Togli ${place.name}", tint = p.sub) }
            OutlinedButton(onClick = { editing = place }) { Text("Modifica", color = p.accent) }
        }
    }
    if (places.count { it.custom } < 8) OutlinedButton(onClick = { editing = SavedPlace("", "", icon = "place") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = p.accent)
        Text("Aggiungi un luogo", color = p.accent, modifier = Modifier.padding(start = 8.dp))
    }
    editing?.let { start ->
        PlaceEditor(
            start = start,
            taken = places.map { it.name }.filter { it != start.name }.toSet() + TravelRepo.PRESETS,
            onDismiss = { editing = null },
            onSave = { saved ->
                onPlaces(places.filter { it.name != start.name } + saved)
                editing = null
            },
        )
    }

    Spacer(Modifier.height(6.dp))
    Note("Per il lavoro, la sera (e la mattina prima di entrare) il brief ti chiede a che ora entri e calcola da solo quando uscire. Le destinazioni in cui ti trovi già non compaiono.")

    Spacer(Modifier.height(10.dp))
    Note("Con quali mezzi (anche più di uno)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TravelRepo.MODES.forEach { (value, label) ->
            FilterChip(
                selected = value in modes,
                // Almeno un mezzo resta sempre scelto.
                onClick = { onModes(if (value in modes) (modes - value).ifEmpty { modes } else TravelRepo.MODES.keys.filter { it in modes || it == value }) },
                label = { Text(label) },
            )
        }
    }
    if ("transit" in modes) Note("I mezzi pubblici usano gli orari reali delle corse: il tempo comprende l'attesa alla fermata e i tratti a piedi.")

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.MyLocation, null, Modifier.size(20.dp), tint = if (precise) p.accent else p.sub)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("Posizione precisa", fontSize = 15.sp)
            Text(
                if (precise) "Attiva: il tragitto parte dal punto esatto in cui ti trovi, che viene inviato ai servizi di calcolo del percorso." else "Facoltativa: senza, il tragitto parte da una posizione approssimata (anche 1-2 km di scarto).",
                fontSize = 12.sp, lineHeight = 16.sp, color = p.sub,
            )
        }
        if (!precise) OutlinedButton(onClick = { askPrecise.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
            Text("Consenti", color = p.accent)
        }
    }

    TravelRepo.PRESETS.getOrNull(picking)?.let { name ->
        val place = places.firstOrNull { it.name == name }
        AddressPicker(
            initial = if (place?.lat != null && place.lon != null) Address(place.address, place.lat, place.lon) else null,
            query = place?.address ?: "",
            onDismiss = { picking = -1 },
            onPick = { a ->
                onPlaces(places.filter { it.name != name } + SavedPlace(name, a.label, a.lat, a.lon))
                picking = -1
            },
        )
    }
}

/** Icone tra cui scegliere per un luogo personalizzato, con il nome con cui vengono salvate. */
internal val PLACE_ICONS = linkedMapOf(
    "place" to Icons.Rounded.Place, "family" to Icons.Rounded.FamilyRestroom, "favorite" to Icons.Rounded.Favorite,
    "restaurant" to Icons.Rounded.Restaurant, "cafe" to Icons.Rounded.LocalCafe, "shop" to Icons.Rounded.ShoppingCart,
    "health" to Icons.Rounded.LocalHospital, "park" to Icons.Rounded.Park, "pets" to Icons.Rounded.Pets,
    "library" to Icons.Rounded.LocalLibrary, "church" to Icons.Rounded.Church, "sport" to Icons.Rounded.SportsSoccer,
    "music" to Icons.Rounded.MusicNote, "beach" to Icons.Rounded.BeachAccess, "star" to Icons.Rounded.Star,
)

/** L'icona di una destinazione: fissa per casa, lavoro, scuola e palestra, scelta dall'utente per le altre. */
internal fun placeIcon(place: SavedPlace?): ImageVector = when (place?.name) {
    "Casa" -> Icons.Rounded.Home
    "Lavoro" -> Icons.Rounded.Work
    "Scuola" -> Icons.Rounded.School
    "Palestra" -> Icons.Rounded.FitnessCenter
    else -> PLACE_ICONS[place?.icon] ?: Icons.Rounded.Place
}

/** Finestra per creare o modificare un luogo personalizzato: nome, icona e indirizzo scelto sulla mappa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceEditor(start: SavedPlace, taken: Set<String>, onDismiss: () -> Unit, onSave: (SavedPlace) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(start.name) }
    var icon by remember { mutableStateOf(start.icon.ifBlank { "place" }) }
    var address by remember { mutableStateOf(if (start.lat != null && start.lon != null) Address(start.address, start.lat, start.lon) else null) }
    var picking by remember { mutableStateOf(false) }
    val clash = name.trim() in taken
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.name.isBlank()) "Nuovo luogo" else "Modifica luogo") },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it.take(30) }, label = { Text("Nome") }, singleLine = true,
                    supportingText = { if (clash) Text("C'è già un luogo con questo nome") },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                Text("Icona", fontSize = 13.sp, color = p.sub, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PLACE_ICONS.forEach { (key, vector) ->
                        val on = key == icon
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) p.accent else p.text.copy(alpha = 0.07f))
                                .clickable(onClickLabel = key) { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vector, null, Modifier.size(22.dp), tint = if (on) p.onAccent else p.sub) }
                    }
                }
                Text(address?.label ?: "Nessun indirizzo scelto", fontSize = 13.sp, lineHeight = 18.sp, color = p.sub, modifier = Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Icon(Icons.Rounded.Map, null, Modifier.size(18.dp), tint = p.accent)
                    Text(if (address == null) "Scegli sulla mappa" else "Cambia sulla mappa", color = p.accent, modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { address?.let { onSave(SavedPlace(name.trim(), it.label, it.lat, it.lon, icon)) } },
                enabled = name.isNotBlank() && !clash && address != null,
            ) { Text("Salva", color = if (name.isNotBlank() && !clash && address != null) p.accent else p.sub) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = p.sub) } },
    )
    if (picking) AddressPicker(initial = address, query = address?.label ?: "", onDismiss = { picking = false }, onPick = { address = it; picking = false })
}

/**
 * Ricerca dell'indirizzo a tutto schermo: si scrive, si sceglie tra i suggerimenti e si controlla il punto sulla mappa.
 * Toccando la mappa il segnaposto si sposta lì. Mappa e ricerca vengono da OpenStreetMap, senza chiavi né account.
 */
@Composable
private fun AddressPicker(initial: Address?, query: String, onDismiss: () -> Unit, onPick: (Address) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val near = remember { Prefs(context).lastPlace }
    var text by remember { mutableStateOf(query) }
    var chosen by remember { mutableStateOf(initial) }
    var results by remember { mutableStateOf(emptyList<Address>()) }
    var searching by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // Dopo una scelta il testo cambia da solo: quella volta non si deve ripartire con la ricerca.
    var skipSearch by remember { mutableStateOf(initial != null) }

    LaunchedEffect(text) {
        if (skipSearch) { skipSearch = false; return@LaunchedEffect }
        if (text.trim().length < 3) { results = emptyList(); return@LaunchedEffect }
        delay(450)
        searching = true
        val found = withContext(Dispatchers.IO) { runCatching { TravelRepo.search(text, near) } }
        results = found.getOrDefault(emptyList())
        problem = if (found.isFailure) "Ricerca non riuscita: controlla la connessione." else null
        searching = false
    }
    // Con "cerca" sulla tastiera parte la ricerca precisa dell'indirizzo completo, che va in cima ai suggerimenti.
    val searchExact = {
        focus.clearFocus()
        if (text.trim().length >= 3) scope.launch {
            searching = true
            val found = withContext(Dispatchers.IO) { runCatching { TravelRepo.searchExact(text) } }
            results = (found.getOrDefault(emptyList()) + results).distinctBy { it.label }
            problem = when {
                found.isFailure -> "Ricerca non riuscita: controlla la connessione."
                results.isEmpty() -> "Nessun indirizzo trovato: prova ad aggiungere la città, oppure tocca il punto sulla mappa."
                else -> null
            }
            searching = false
        }
        Unit
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = p.bottom, contentColor = p.text) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cerca l'indirizzo", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Chiudi", tint = p.sub) }
                }
                OutlinedTextField(
                    text, { text = it }, label = { Text("Via, numero, città") }, singleLine = true,
                    trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(20.dp), color = p.accent, strokeWidth = 2.dp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchExact() }),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                if (results.isNotEmpty()) Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(p.text.copy(alpha = 0.06f)),
                ) {
                    results.take(5).forEach { r ->
                        Text(
                            r.label, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable {
                                keyboard?.hide()
                                chosen = r
                                skipSearch = true
                                text = r.label
                                results = emptyList()
                                focus.clearFocus()
                            }.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                problem?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = p.sub, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
                    OsmMap(chosen, near?.let { it.lat to it.lon }) { lat, lon ->
                        // Tocco sulla mappa: il segnaposto va lì e l'indirizzo si ricava dal punto.
                        chosen = Address(chosen?.label ?: text, lat, lon)
                        scope.launch {
                            val found = withContext(Dispatchers.IO) { runCatching { TravelRepo.reverse(lat, lon) }.getOrNull() }
                            if (found != null && chosen?.lat == lat && chosen?.lon == lon) {
                                chosen = Address(found, lat, lon)
                                skipSearch = true
                                text = found
                                results = emptyList()
                            }
                        }
                    }
                    Text(
                        "© OpenStreetMap", fontSize = 10.sp, color = Color(0xFF333333),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    chosen?.label ?: "Scrivi l'indirizzo e scegli un risultato, oppure tocca il punto sulla mappa.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = p.sub, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = { chosen?.let(onPick) }, enabled = chosen != null,
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Usa questo indirizzo") }
            }
        }
    }
}

/** Mappa di OpenStreetMap con un segnaposto su [pin]; [onTap] riceve il punto toccato. */
@Composable
private fun OsmMap(pin: Address?, fallback: Pair<Double, Double>?, onTap: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val tap = remember { mutableStateOf(onTap) }.also { it.value = onTap }
    val map = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
            // Chi offre le mappe chiede che ogni app si presenti con un nome riconoscibile.
            userAgentValue = "Brieffo/1.0 (Android; github.com/carellice/brieffo)"
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                    tap.value(point.latitude, point.longitude)
                    return true
                }
                override fun longPressHelper(point: GeoPoint) = false
            }))
            // Senza un indirizzo si parte dalla zona dell'utente, o dall'Italia intera.
            val start = pin?.let { GeoPoint(it.lat, it.lon) } ?: fallback?.let { GeoPoint(it.first, it.second) }
            controller.setZoom(if (pin != null) 17.0 else if (start != null) 12.0 else 5.5)
            controller.setCenter(start ?: GeoPoint(42.0, 12.6))
        }
    }
    val accent = LocalPalette.current.accent
    val marker = remember {
        // Segnaposto disegnato qui: un punto nel colore dell'app con il bordo bianco, centrato sull'indirizzo.
        val px = (26 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.WHITE
            drawCircle(px / 2f, px / 2f, px / 2f, paint)
            paint.color = accent.toArgb()
            drawCircle(px / 2f, px / 2f, px * 0.36f, paint)
        }
        Marker(map).apply {
            icon = BitmapDrawable(context.resources, bitmap)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        }
    }
    DisposableEffect(Unit) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map }, modifier = Modifier.fillMaxSize(),
        update = {
            if (pin == null) it.overlays.remove(marker) else {
                val point = GeoPoint(pin.lat, pin.lon)
                val moved = marker.position?.let { old -> old.latitude != point.latitude || old.longitude != point.longitude } ?: true
                marker.position = point
                if (marker !in it.overlays) it.overlays.add(marker)
                if (moved) {
                    if (it.zoomLevelDouble < 15.0) it.controller.setZoom(17.0)
                    it.controller.animateTo(point)
                }
            }
            it.invalidate()
        },
    )
}
