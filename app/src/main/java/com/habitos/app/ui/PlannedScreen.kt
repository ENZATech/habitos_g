
package com.habitos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedScreen(
    state: PersistedState,
    onAddPlannedTask: (
        title: String,
        priority: Priority,
        dueDateStr: String,
        dueTime: String?,
        durationMinutes: Int?,
        deadlineDate: String?,
        recurrence: RecurrenceType
    ) -> Unit,
    onPostponeTask: (task: TaskItem, newDueDate: String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()

    var title by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(todayStr) }
    var selectedPriority by remember { mutableStateOf(Priority.P4) }
    var selectedTimeStr by remember { mutableStateOf<String?>(null) }
    var selectedDurationMin by remember { mutableStateOf<Int?>(null) }
    var selectedDeadlineStr by remember { mutableStateOf<String?>(null) }
    var selectedRecurrence by remember { mutableStateOf(RecurrenceType.NONE) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var showPriorityDropdown by remember { mutableStateOf(false) }
    var showRecurrenceDropdown by remember { mutableStateOf(false) }

    var sortOption by remember { mutableStateOf(TaskSortOption.PRIORITY) }
    var showCompletedSection by remember { mutableStateOf(false) }

    val durationPresets = listOf(15, 30, 45, 60, 90, 120)

    fun List<TaskItem>.applySort(): List<TaskItem> = when (sortOption) {
        TaskSortOption.PRIORITY -> sortedWith(compareBy<TaskItem> { it.priority.ordinal }.thenBy { it.dueTime ?: "99:99" })
        TaskSortOption.DUE_TIME -> sortedWith(compareBy<TaskItem> { it.dueTime ?: "99:99" }.thenBy { it.priority.ordinal })
        TaskSortOption.DURATION -> sortedWith(compareBy<TaskItem> { it.durationMinutes ?: Int.MAX_VALUE }.thenBy { it.priority.ordinal })
        TaskSortOption.TITLE -> sortedBy { it.title.lowercase(Locale.getDefault()) }
    }

    val plannedTasks = state.tasks.filter { !it.dueDate.isNullOrBlank() || !it.deadlineDate.isNullOrBlank() }
    val activePlanned = plannedTasks.filter { !it.isCompleted }
    val completedPlanned = plannedTasks.filter { it.isCompleted }

    val overdue = activePlanned.filter { !it.dueDate.isNullOrBlank() && it.dueDate < todayStr }.applySort()
    val dueToday = activePlanned.filter { it.dueDate == todayStr }.applySort()
    val upcoming = activePlanned.filter { !it.dueDate.isNullOrBlank() && it.dueDate > todayStr }
        .sortedWith(compareBy<TaskItem> { it.dueDate }.thenBy { it.priority.ordinal })

    val totalPlannedTodayMinutes = dueToday.sumOf { it.durationMinutes ?: 0 }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Planned & Time-Blocked", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Overdue • Today • Upcoming • Deadlines", fontSize = 12.sp, color = TextMuted)
            }
            if (totalPlannedTodayMinutes > 0) {
                Surface(color = TealSecondary.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f))) {
                    Text(
                        "⏱️ ${DateFormatHelper.formatWorkload(totalPlannedTodayMinutes)} today",
                        color = EmeraldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Scheduled Task Quick-Add Card
        Surface(color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(EmeraldPrimary),
                        decorationBox = { inner ->
                            if (title.isEmpty()) Text("Schedule task (e.g. Lab Viva tomorrow at 10am 1h p1)...", color = TextMuted, fontSize = 12.sp, maxLines = 1)
                            inner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                onAddPlannedTask(
                                    title.trim(),
                                    selectedPriority,
                                    selectedDate,
                                    selectedTimeStr,
                                    selectedDurationMin,
                                    selectedDeadlineStr,
                                    selectedRecurrence
                                )
                                title = ""
                                selectedTimeStr = null
                                selectedDurationMin = null
                                selectedDeadlineStr = null
                                selectedRecurrence = RecurrenceType.NONE
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = ObsidianBg)
                    }
                }

                HorizontalDivider(color = ObsidianBorder)

                // Row 1: Date, Time, Priority, Repeat
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AmberStreak.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { showDatePicker = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = AmberStreak, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(DateFormatHelper.shortDate(selectedDate), color = AmberStreak, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = if (selectedTimeStr != null) TealSecondary.copy(alpha = 0.18f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selectedTimeStr != null) EmeraldPrimary else ObsidianBorder),
                        modifier = Modifier.clickable { showTimePicker = true }
                    ) {
                        Text(
                            text = if (selectedTimeStr != null) "🕒 $selectedTimeStr" else "🕒 Time",
                            color = if (selectedTimeStr != null) EmeraldPrimary else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Box {
                        val prioCol = Color(selectedPriority.colorHex)
                        Surface(
                            color = prioCol.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, prioCol),
                            modifier = Modifier.clickable { showPriorityDropdown = true }
                        ) {
                            Text("🚩 ${selectedPriority.shortLabel}", color = prioCol, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                        DropdownMenu(expanded = showPriorityDropdown, onDismissRequest = { showPriorityDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                            Priority.entries.forEach { prio ->
                                DropdownMenuItem(
                                    text = { Text(prio.label, color = Color(prio.colorHex), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    onClick = { selectedPriority = prio; showPriorityDropdown = false }
                                )
                            }
                        }
                    }

                    Surface(
                        color = if (selectedDeadlineStr != null) RoseAlert.copy(alpha = 0.16f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selectedDeadlineStr != null) RoseAlert else ObsidianBorder),
                        modifier = Modifier.clickable { showDeadlinePicker = true }
                    ) {
                        Text(
                            text = if (selectedDeadlineStr != null) "🎯 ${DateFormatHelper.shortDate(selectedDeadlineStr)}" else "🎯 Deadline",
                            color = if (selectedDeadlineStr != null) RoseAlert else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Box {
                        val hasRec = selectedRecurrence != RecurrenceType.NONE
                        Surface(
                            color = if (hasRec) AmberStreak.copy(alpha = 0.16f) else ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (hasRec) AmberStreak else ObsidianBorder),
                            modifier = Modifier.clickable { showRecurrenceDropdown = true }
                        ) {
                            Text(
                                text = if (hasRec) "🔁 ${selectedRecurrence.label}" else "🔁 Repeat",
                                color = if (hasRec) AmberStreak else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                        DropdownMenu(expanded = showRecurrenceDropdown, onDismissRequest = { showRecurrenceDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                            RecurrenceType.entries.forEach { rec ->
                                DropdownMenuItem(
                                    text = { Text(rec.label, color = TextPrimary, fontSize = 12.sp) },
                                    onClick = { selectedRecurrence = rec; showRecurrenceDropdown = false }
                                )
                            }
                        }
                    }
                }

                // Row 2: Duration Quick-Chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Duration:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    durationPresets.forEach { mins ->
                        val sel = selectedDurationMin == mins
                        Surface(
                            color = if (sel) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianNav,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (sel) EmeraldPrimary else ObsidianBorder),
                            modifier = Modifier.clickable { selectedDurationMin = if (sel) null else mins }
                        ) {
                            Text(
                                DateFormatHelper.formatWorkload(mins),
                                color = if (sel) EmeraldPrimary else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Sort Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sort Tasks:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TaskSortOption.entries.forEach { opt ->
                    val sel = sortOption == opt
                    Surface(
                        color = if (sel) EmeraldPrimary.copy(alpha = 0.18f) else ObsidianNav,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (sel) EmeraldPrimary else ObsidianBorder),
                        modifier = Modifier.clickable { sortOption = opt }
                    ) {
                        Text(opt.label, color = if (sel) EmeraldPrimary else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (overdue.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("🚨 OVERDUE (${overdue.size})", color = RoseAlert, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        Surface(
                            color = RoseAlert.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable {
                                overdue.forEach { onPostponeTask(it, todayStr) }
                            }
                        ) {
                            Text("Reschedule All to Today", color = RoseAlert, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
                items(overdue, key = { "ov_${it.id}" }) { task ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PostponeChip("Today") { onPostponeTask(task, todayStr) }
                            PostponeChip("+1 Day") { onPostponeTask(task, today.plusDays(1).toString()) }
                            PostponeChip("This Weekend") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()) }
                            PostponeChip("Next Week") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()) }
                        }
                    }
                }
            }

            if (dueToday.isNotEmpty()) {
                item { Text("☀️ TODAY (${dueToday.size})", color = AmberStreak, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(dueToday, key = { "td_${it.id}" }) { task ->
                    TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                }
            }

            if (upcoming.isNotEmpty()) {
                item { Text("📅 UPCOMING (${upcoming.size})", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(upcoming, key = { "up_${it.id}" }) { task ->
                    TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                }
            }

            if (completedPlanned.isNotEmpty()) {
                item {
                    Surface(
                        color = ObsidianNav,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth().clickable { showCompletedSection = !showCompletedSection }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✓ Completed Scheduled Tasks (${completedPlanned.size})", color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (showCompletedSection) "Hide ▲" else "Show ▼", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (showCompletedSection) {
                    items(completedPlanned, key = { "pl_done_${it.id}" }) { task ->
                        TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                    }
                }
            }

            if (plannedTasks.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.CalendarMonth,
                        title = "0 Planned Tasks",
                        subtitle = "Schedule tasks with dates, time blocks, or hard deadlines above."
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString() }
                    showDatePicker = false
                }) { Text("Select", color = EmeraldPrimary) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextMuted) } }
        ) { DatePicker(state = pickerState) }
    }

    if (showDeadlinePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selectedDeadlineStr = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString() }
                    showDeadlinePicker = false
                }) { Text("Set Deadline", color = RoseAlert) }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedDeadlineStr = null
                    showDeadlinePicker = false
                }) { Text("Clear", color = TextMuted) }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = ObsidianCard,
            title = { Text("Select Scheduled Time", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedTimeStr = String.format(Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Set Time", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedTimeStr = null
                    showTimePicker = false
                }) { Text("Clear", color = TextMuted) }
            }
        )
    }
}