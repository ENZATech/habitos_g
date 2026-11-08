package com.habitos.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BirthdaysScreen(
    state: PersistedState,
    onAddBirthday: (name: String, relation: String, day: Int, month: Int, year: Int, phone: String, customWish: String) -> Unit,
    onUpdateCustomWish: (BirthdayPerson, String) -> Unit,
    onMarkWished: (BirthdayPerson) -> Unit,
    onDeleteBirthday: (BirthdayPerson) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    var showAddDialog by remember { mutableStateOf(false) }
    var activeWishTargetId by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val missedYesterday = state.birthdays.filter { it.isBirthdayOn(yesterday) && it.wishedYear != today.year }
    val todayBirthdays = state.birthdays.filter { it.isBirthdayOn(today) }
    val upcomingBirthdays = state.birthdays.filter { !it.isBirthdayOn(today) }.sortedBy { it.daysUntilNext(today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text("Birthdays & Rescue", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Midnight & 1-day prior alerts", fontSize = 12.sp, color = TextMuted)
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (missedYesterday.isNotEmpty()) {
            item { Text("🚨 YESTERDAY - Missed! (Belated Rescue)", color = RoseAlert, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
            items(missedYesterday, key = { "missed_${it.id}" }) { person ->
                BirthdayCard(person = person, today = today, badgeText = "Missed!", badgeColor = RoseAlert, actionLabel = "Send Belated Birthday Wish", onWishClick = { activeWishTargetId = person.id to true }, onDelete = { onDeleteBirthday(person) })
            }
        }

        if (todayBirthdays.isNotEmpty()) {
            item { Text("🎂 TODAY'S BIRTHDAYS", color = AmberStreak, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
            items(todayBirthdays, key = { "today_${it.id}" }) { person ->
                val wished = person.wishedYear == today.year
                BirthdayCard(person = person, today = today, badgeText = if (wished) "✓ Wished" else "🎂 TODAY!", badgeColor = if (wished) EmeraldPrimary else AmberStreak, actionLabel = "Send Birthday Wish", onWishClick = { activeWishTargetId = person.id to false }, onDelete = { onDeleteBirthday(person) })
            }
        }

        if (upcomingBirthdays.isNotEmpty()) {
            item { Text("📅 UPCOMING BIRTHDAYS (${upcomingBirthdays.size})", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            items(upcomingBirthdays, key = { "up_${it.id}" }) { person ->
                val daysLeft = person.daysUntilNext(today)
                BirthdayCard(person = person, today = today, badgeText = "In ${daysLeft}d", badgeColor = TealSecondary, actionLabel = "Send Birthday Wish", onWishClick = { activeWishTargetId = person.id to false }, onDelete = { onDeleteBirthday(person) })
            }
        }

        if (state.birthdays.isEmpty()) {
            item {
                EmptyStateCard(icon = Icons.Default.Cake, title = "0 Birthdays", subtitle = "Tap '+ Add' to save friends & family.")
            }
        }
    }

    if (showAddDialog) {
        AddBirthdayDialog(onDismiss = { showAddDialog = false }, onSave = { n, r, d, m, y, p, w -> onAddBirthday(n, r, d, m, y, p, w); showAddDialog = false })
    }

    val targetPair = activeWishTargetId
    val targetPerson = targetPair?.let { (id, _) -> state.birthdays.find { it.id == id } }
    if (targetPerson != null && targetPair != null) {
        val isBelated = targetPair.second
        BirthdayWishGeneratorDialog(
            person = targetPerson,
            isBelated = isBelated,
            onDismiss = { activeWishTargetId = null },
            onSaveCustomWish = { onUpdateCustomWish(targetPerson, it); Toast.makeText(context, "Saved custom wish!", Toast.LENGTH_SHORT).show() },
            onCopyText = { msg ->
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Wish", msg))
                onMarkWished(targetPerson)
                Toast.makeText(context, "Wish copied!", Toast.LENGTH_SHORT).show()
                activeWishTargetId = null
            },
            onSendWhatsApp = { msg ->
                onMarkWished(targetPerson)
                val clean = targetPerson.phone.filter { it.isDigit() }
                val url = if (clean.isNotEmpty()) "https://wa.me/$clean?text=${URLEncoder.encode(msg, "UTF-8")}" else "https://wa.me/?text=${URLEncoder.encode(msg, "UTF-8")}"
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show() }
                activeWishTargetId = null
            }
        )
    }
}

@Composable
fun BirthdayCard(
    person: BirthdayPerson,
    today: LocalDate,
    badgeText: String,
    badgeColor: Color,
    actionLabel: String,
    onWishClick: () -> Unit,
    onDelete: () -> Unit
) {
    val monthName = Month.of(person.monthValue).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val turningAge = person.turningAge(today)

    Surface(color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(person.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (person.hasCustomWish()) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = AmberStreak.copy(alpha = 0.16f), shape = RoundedCornerShape(5.dp)) {
                                Text("✨ Custom", color = AmberStreak, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                    }
                    val dateAge = "$monthName ${person.dayOfMonth}${if (person.birthYear in 1900..today.year) ", ${person.birthYear}" else ""}${if (turningAge != null && turningAge > 0) " • Turning $turningAge" else ""}"
                    Text(dateAge, color = TextMuted, fontSize = 12.sp, maxLines = 1)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.7f))) {
                        Text(badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(onClick = onWishClick, colors = ButtonDefaults.buttonColors(containerColor = badgeColor), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(38.dp)) {
                Icon(Icons.Default.Send, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBirthdayDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, relation: String, day: Int, month: Int, year: Int, phone: String, customWish: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Friend") }
    var dayStr by remember { mutableStateOf("") }
    var monthStr by remember { mutableStateOf("") }
    var yearStr by remember { mutableStateOf("2008") }
    var phone by remember { mutableStateOf("") }
    var customWish by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }

    val relRows = listOf(listOf("Friend", "Best Friend", "Brother"), listOf("Sister", "Mother", "Father"), listOf("Partner", "Cousin", "Other"))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Add Birthday", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Surface(color = AmberStreak.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp), modifier = Modifier.clickable { showCalendar = true }) {
                    Text("Calendar 📅", color = AmberStreak, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errorMsg != null) Text(errorMsg!!, color = RoseAlert, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(value = name, onValueChange = { name = it; errorMsg = null }, label = { Text("Person's Name *", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("Relation (private • tailors wish style)", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    relRows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { r ->
                                val sel = relation == r
                                Surface(
                                    color = if (sel) EmeraldPrimary else ObsidianBg, shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (sel) EmeraldPrimary else ObsidianBorder),
                                    modifier = Modifier.weight(1f).clickable { relation = r }
                                ) {
                                    Text(r, color = if (sel) ObsidianBg else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                                }
                            }
                        }
                    }
                }

                Text("Date & Year of Birth *", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dayStr, onValueChange = { dayStr = it.take(2); errorMsg = null }, label = { Text("Day") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = monthStr, onValueChange = { monthStr = it.take(2); errorMsg = null }, label = { Text("Month") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = yearStr, onValueChange = { yearStr = it.take(4); errorMsg = null }, label = { Text("Year") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1.2f))
                }

                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("WhatsApp Phone (Optional)", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())

                Surface(
                    color = if (showCustomInput) AmberStreak.copy(alpha = 0.14f) else ObsidianBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (showCustomInput) AmberStreak else ObsidianBorder),
                    modifier = Modifier.fillMaxWidth().clickable { showCustomInput = !showCustomInput }
                ) {
                    Text(if (showCustomInput) "✨ Custom Wish Active" else "+ Add Custom Wish (Optional)", color = if (showCustomInput) AmberStreak else EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
                }

                if (showCustomInput) {
                    OutlinedTextField(value = customWish, onValueChange = { customWish = it }, placeholder = { Text("Write personal wish...", color = TextMuted) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = dayStr.toIntOrNull()
                    val m = monthStr.toIntOrNull()
                    val y = yearStr.toIntOrNull() ?: 2008
                    if (name.isBlank()) errorMsg = "Enter person's name!"
                    else if (d == null || d !in 1..31) errorMsg = "Enter valid Day (1-31)"
                    else if (m == null || m !in 1..12) errorMsg = "Enter valid Month (1-12)"
                    else onSave(name.trim(), relation, d, m, y, phone.trim(), customWish.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Birthday", color = ObsidianBg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = TextMuted) } }
    )

    if (showCalendar) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val dt = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                        dayStr = "${dt.dayOfMonth}"
                        monthStr = "${dt.monthValue}"
                        yearStr = "${dt.year}"
                    }
                    showCalendar = false
                }) { Text("Use Date", color = EmeraldPrimary) }
            }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
fun BirthdayWishGeneratorDialog(
    person: BirthdayPerson,
    isBelated: Boolean,
    onDismiss: () -> Unit,
    onSaveCustomWish: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onSendWhatsApp: (String) -> Unit
) {
    var templateIdx by remember { mutableIntStateOf(0) }
    var usingCustom by remember(person.customWish) { mutableStateOf(person.hasCustomWish()) }
    var messageText by remember(templateIdx, usingCustom, person.customWish) {
        mutableStateOf(if (usingCustom && person.hasCustomWish()) person.customWish.orEmpty() else BirthdayWishTemplates.generateWish(person, isBelated, templateIdx))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Column {
                Text(if (isBelated) "🚨 Belated Rescue" else "🎂 Birthday Wish Generator", color = if (isBelated) RoseAlert else EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("For ${person.name} (${(templateIdx.mod(BirthdayWishTemplates.TEMPLATE_COUNT)) + 1}/${BirthdayWishTemplates.TEMPLATE_COUNT})", color = TextMuted, fontSize = 11.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(color = EmeraldPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { onSaveCustomWish(messageText.trim()); usingCustom = true }) {
                        Text("💾 Save Custom", color = EmeraldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                    Surface(color = AmberStreak.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { usingCustom = false; templateIdx = (templateIdx + 1) % BirthdayWishTemplates.TEMPLATE_COUNT }) {
                        Text("↻ Shuffle (1/6)", color = AmberStreak, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                }
                OutlinedTextField(value = messageText, onValueChange = { messageText = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onCopyText(messageText) }, modifier = Modifier.weight(1f)) { Text("Copy", color = TextPrimary) }
                    Button(onClick = { onSendWhatsApp(messageText) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary), modifier = Modifier.weight(1f)) { Text("WhatsApp", color = ObsidianBg, fontWeight = FontWeight.Bold) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) } }
    )
}