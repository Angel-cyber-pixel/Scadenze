package com.scadenzeinvisibili.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ---------- Utilità ----------

private val DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

fun fmtDate(d: LocalDate): String = DATE_FMT.format(d)

fun statusColor(days: Long): Color = when {
    days < 0L -> Color(0xFFD32F2F)
    days <= 7L -> Color(0xFFF57C00)
    else -> Color(0xFF388E3C)
}

fun statusText(days: Long): String = when {
    days < -1L -> "Scaduto da ${-days} giorni"
    days == -1L -> "Scaduto ieri"
    days == 0L -> "Scade oggi"
    days == 1L -> "Scade domani"
    days < 60L -> "Tra $days giorni"
    days < 730L -> "Tra ${days / 30} mesi"
    else -> "Tra ${days / 365} anni"
}

fun loadPhoto(ctx: Context, name: String): ImageBitmap? {
    if (name.isBlank()) return null
    return try {
        BitmapFactory.decodeFile(File(ctx.filesDir, name).absolutePath)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// ---------- Radice dell'app ----------

@Composable
fun App() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Store.load(ctx)) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun persist(newList: List<Item>) {
        list = newList
        Store.save(ctx, newList)
    }

    if (adding || editing != null) {
        BackHandler {
            adding = false
            editing = null
        }
        EditScreen(
            initial = editing,
            onSave = { item ->
                val exists = list.any { it.id == item.id }
                if (exists) {
                    persist(list.map { if (it.id == item.id) item else it })
                } else {
                    persist(list + item)
                }
                adding = false
                editing = null
            },
            onDelete = { item ->
                if (item.photo.isNotBlank()) {
                    File(ctx.filesDir, item.photo).delete()
                }
                persist(list.filter { it.id != item.id })
                adding = false
                editing = null
            },
            onCancel = {
                adding = false
                editing = null
            }
        )
    } else {
        ListScreen(
            list = list,
            onAdd = { adding = true },
            onOpen = { editing = it },
            onRenew = { item ->
                val newExpiry = LocalDate.now().plusDays(item.cycleDays.toLong()).toEpochDay()
                persist(
                    list.map {
                        if (it.id == item.id) it.copy(expiryDay = newExpiry, lastNotifiedDay = -1L) else it
                    }
                )
            }
        )
    }
}

// ---------- Elenco ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    list: List<Item>,
    onAdd: () -> Unit,
    onOpen: (Item) -> Unit,
    onRenew: (Item) -> Unit
) {
    val sorted = list.sortedBy { it.expiryDay }
    val urgent = sorted.count { it.daysLeft() <= 7L }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scadenze Invisibili") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Aggiungi") }
            )
        }
    ) { pad ->
        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nessuna scadenza ancora.\nTocca «Aggiungi» e parti da un esempio: filtro dell'acqua, pile, garanzie…",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        if (urgent == 0) "Tutto sotto controllo 👍"
                        else if (urgent == 1) "1 cosa da sistemare presto"
                        else "$urgent cose da sistemare presto",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(sorted, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        onOpen = { onOpen(item) },
                        onRenew = { onRenew(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun ItemCard(item: Item, onOpen: () -> Unit, onRenew: () -> Unit) {
    val ctx = LocalContext.current
    val days = item.daysLeft()
    val color = statusColor(days)
    val thumb = remember(item.photo) { loadPhoto(ctx, item.photo) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(color)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Text(item.emoji, fontSize = 32.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(statusText(days), color = color, fontWeight = FontWeight.Medium)
                    Text(
                        fmtDate(LocalDate.ofEpochDay(item.expiryDay)),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.cycleDays > 0) {
                    TextButton(onClick = onRenew) { Text("Rinnovato") }
                }
            }
        }
    }
}

// ---------- Aggiungi / modifica ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    initial: Item?,
    onSave: (Item) -> Unit,
    onDelete: (Item) -> Unit,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    val id = remember { initial?.id ?: UUID.randomUUID().toString() }

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "⏳") }
    var expiry by remember {
        mutableStateOf(
            if (initial != null) LocalDate.ofEpochDay(initial.expiryDay)
            else LocalDate.now().plusDays(30)
        )
    }
    var cycle by remember {
        mutableStateOf(
            if (initial != null && initial.cycleDays > 0) initial.cycleDays.toString() else ""
        )
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: "") }
    var photo by remember { mutableStateOf(initial?.photo ?: "") }
    var photoTick by remember { mutableStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            val fileName = "photo_$id.jpg"
            try {
                ctx.openFileOutput(fileName, Context.MODE_PRIVATE).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                photo = fileName
                photoTick += 1
            } catch (e: Exception) {
                Toast.makeText(ctx, "Non sono riuscito a salvare la foto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val scanner = remember { GmsBarcodeScanning.getClient(ctx) }
    val preview = remember(photo, photoTick) { loadPhoto(ctx, photo) }
    val daysLeft = expiry.toEpochDay() - LocalDate.now().toEpochDay()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Nuova scadenza" else "Modifica") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina")
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (initial == null) {
                Text("Parti da un esempio", fontWeight = FontWeight.Medium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PRESETS) { p ->
                        OutlinedButton(onClick = {
                            name = p.name
                            emoji = p.emoji
                            expiry = LocalDate.now().plusDays(p.days.toLong())
                            cycle = if (p.recurring) p.days.toString() else ""
                        }) {
                            Text("${p.emoji} ${p.name}")
                        }
                    }
                }
                Text(
                    "Le durate sono indicative: controlla sempre le indicazioni del prodotto.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Cosa scade?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EMOJIS) { e ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (e == emoji) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(e, fontSize = 24.sp)
                    }
                }
            }

            OutlinedButton(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scade il ${fmtDate(expiry)}  ·  ${statusText(daysLeft)}")
            }

            OutlinedTextField(
                value = cycle,
                onValueChange = { v -> cycle = v.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Si ripete ogni (giorni), vuoto = una volta sola") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Note (dove l'hai comprato, modello…)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    try {
                        takePhoto.launch(null)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Fotocamera non disponibile", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("📷 Foto")
                }
                OutlinedButton(onClick = {
                    scanner.startScan()
                        .addOnSuccessListener { b -> barcode = b.rawValue ?: "" }
                        .addOnFailureListener {
                            Toast.makeText(ctx, "Scanner non disponibile", Toast.LENGTH_SHORT).show()
                        }
                }) {
                    Text("▥ Codice a barre")
                }
            }

            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            if (barcode.isNotBlank()) {
                Text(
                    "Codice: $barcode",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val expiryDay = expiry.toEpochDay()
                    onSave(
                        Item(
                            id = id,
                            name = name.trim(),
                            emoji = emoji.ifBlank { "⏳" },
                            expiryDay = expiryDay,
                            cycleDays = cycle.toIntOrNull() ?: 0,
                            notes = notes.trim(),
                            barcode = barcode,
                            photo = photo,
                            lastNotifiedDay =
                                if (initial != null && initial.expiryDay == expiryDay) initial.lastNotifiedDay
                                else -1L
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salva")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = expiry.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis
                    if (ms != null) {
                        expiry = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Annulla") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare?") },
            text = { Text("«${initial.name}» verrà rimosso dall'elenco.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(initial)
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            }
        )
    }
}
