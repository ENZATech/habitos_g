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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailBottomSheet(
    task: TaskItem,
    customLists: List<CustomTaskList>,
    onDismiss: () -> Unit,
    onUpdateTask: (TaskItem) -> Unit,
    onDeleteTask: () -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()

    var stepInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var commentInput by remember { mutableStateOf("") }

    var showRecurrenceDropdown by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showReminderDropdown by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val durationPresets = listOf(15, 30, 45, 60, 90, 120)
    val suggestedLabels = listOf("deepwork", "quick", "code", "offline", "morning")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDeleteTask) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert)
                }
            }

            // 4-Tier Priority Selector
            Text("Priority", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Priority.entries.forEach { prio ->
                    val sel = task.priority == prio
                    val col = Color(prio.colorHex)
                    Surface(
                        color = if (sel) col.copy(alpha = 0.2f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (sel) col else ObsidianBorder),
                        modifier = Modifier.weight(1f).clickable {
                            onUpdateTask(task.copy(priority = prio, isImportant = prio == Priority.P1))
                        }
                    ) {
                        Text(
                            text = prio.shortLabel,
                            color = if (sel) col else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            // 1-Tap Postpone & Reschedule Bar
            Text("Quick Schedule / 1-Tap Postpone", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PostponeChip("☀️ Today") {
                    onUpdateTask(task.copy(dueDate = todayStr, isMyDay = true, myDayDate = todayStr))
                }
                PostponeChip("+1 Day") {
                    val next = today.plusDays(1).toString()
                    onUpdateTask(task.copy(dueDate = next, isMyDay = false, myDayDate = ""))
                }
                PostponeChip("This Weekend") {
                    val sat = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()
                    onUpdateTask(task.copy(dueDate = sat, isMyDay = false, myDayDate = ""))
                }
                PostponeChip("Next Week") {
                    val mon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()
                    onUpdateTask(task.copy(dueDate = mon, isMyDay = false, myDayDate = ""))
                }
                Surface(
                    color = AmberStreak.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AmberStreak),
                    modifier = Modifier.clickable { showDueDatePicker = true }
                ) {
                    Text(
                        text = if (!task.dueDate.isNullOrBlank()) "📅 Due: ${DateFormatHelper.shortDate(task.dueDate)}" else "📅 Pick Date",
                        color = AmberStreak,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // My Day, Category & Recurrence Row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val inMyDay = task.isMyDay && task.myDayDate == todayStr
                FilterChip(
                    selected = inMyDay,
                    onClick = { onUpdateTask(task.copy(isMyDay = !inMyDay, myDayDate = if (!inMyDay) todayStr else "")) },
                    label = { Text(if (inMyDay) "In My Day" else "Add to My Day", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )

                // Category Selector
                Box {
                    val activeList = customLists.find { it.id == task.listId }
                    Surface(
                        color = ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.clickable { showCategoryDropdown = true }
                    ) {
                        Text(
                            text = "📁 ${activeList?.name ?: "No Category"} ▾",
                            color = if (activeList != null) Color(activeList.colorHex) else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                    DropdownMenu(expanded = showCategoryDropdown, onDismissRequest = { showCategoryDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                        DropdownMenuItem(
                            text = { Text("None", color = TextMuted, fontSize = 12.sp) },
                            onClick = { onUpdateTask(task.copy(listId = null)); showCategoryDropdown = false }
                        )
                        customLists.forEach { list ->
                            DropdownMenuItem(
                                text = { Text(list.name, color = Color(list.colorHex), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                onClick = { onUpdateTask(task.copy(listId = list.id)); showCategoryDropdown = false }
                            )
                        }
                    }
                }

                // Recurrence Selector
                Box {
                    Surface(
                        color = if (task.recurrence != RecurrenceType.NONE) AmberStreak.copy(alpha = 0.16f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (task.recurrence != RecurrenceType.NONE) AmberStreak else ObsidianBorder),
                        modifier = Modifier.clickable { showRecurrenceDropdown = true }
                    ) {
                        Text(
                            text = "🔁 ${task.recurrence.label}",
                            color = if (task.recurrence != RecurrenceType.NONE) AmberStreak else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                    DropdownMenu(expanded = showRecurrenceDropdown, onDismissRequest = { showRecurrenceDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                        RecurrenceType.entries.forEach { rec ->
                            DropdownMenuItem(
                                text = { Text(rec.label, color = TextPrimary, fontSize = 12.sp) },
                                onClick = { onUpdateTask(task.copy(recurrence = rec)); showRecurrenceDropdown = false }
                            )
                        }
                    }
                }
            }

            // Custom Recurrence Days Stepper (when AFTER_COMPLETION is selected)
            if (task.recurrence == RecurrenceType.AFTER_COMPLETION) {
                Surface(color = ObsidianNav, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Repeat every ${task.customRecurrenceDays} day(s) after completion", color = AmberStreak, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onUpdateTask(task.copy(customRecurrenceDays = (task.customRecurrenceDays - 1).coerceAtLeast(1))) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(28.dp)
                            ) { Text("-", color = TextPrimary) }
                            OutlinedButton(
                                onClick = { onUpdateTask(task.copy(customRecurrenceDays = task.customRecurrenceDays + 1)) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(28.dp)
                            ) { Text("+", color = EmeraldPrimary) }
                        }
                    }
                }
            }

            // Time Block, Duration, Hard Deadline & Custom Reminder
            Text("Time Block, Duration, Deadline & Reminder", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (!task.dueTime.isNullOrBlank()) TealSecondary.copy(alpha = 0.18f) else ObsidianNav,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (!task.dueTime.isNullOrBlank()) EmeraldPrimary else ObsidianBorder),
                    modifier = Modifier.clickable { showTimePicker = true }
                ) {
                    Text(
                        text = if (!task.dueTime.isNullOrBlank()) "🕒 Start: ${task.dueTime}" else "🕒 Set Start Time",
                        color = if (!task.dueTime.isNullOrBlank()) EmeraldPrimary else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    color = if (!task.deadlineDate.isNullOrBlank()) RoseAlert.copy(alpha = 0.16f) else ObsidianNav,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (!task.deadlineDate.isNullOrBlank()) RoseAlert else ObsidianBorder),
                    modifier = Modifier.clickable { showDeadlinePicker = true }
                ) {
                    val dlLabel = DateFormatHelper.deadlineBadgeInfo(task.deadlineDate, today)?.first ?: "🎯 Set Hard Deadline"
                    Text(
                        text = dlLabel,
                        color = if (!task.deadlineDate.isNullOrBlank()) RoseAlert else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Box {
                    val currentRem = ReminderOffset.fromMinutes(task.reminderOffsetMinutes)
                    val hasRem = currentRem != ReminderOffset.NONE
                    Surface(
                        color = if (hasRem) AmberStreak.copy(alpha = 0.16f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (hasRem) AmberStreak else ObsidianBorder),
                        modifier = Modifier.clickable { showReminderDropdown = true }
                    ) {
                        Text(
                            text = "🔔 ${currentRem.label}",
                            color = if (hasRem) AmberStreak else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    DropdownMenu(expanded = showReminderDropdown, onDismissRequest = { showReminderDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                        ReminderOffset.entries.forEach { offset ->
                            DropdownMenuItem(
                                text = { Text(offset.label, color = TextPrimary, fontSize = 12.sp) },
                                onClick = {
                                    val defaultDate = if (offset.minutes != null && task.dueDate.isNullOrBlank()) todayStr else task.dueDate
                                    val defaultTime = if (offset.minutes != null && task.dueTime.isNullOrBlank()) "09:00" else task.dueTime
                                    onUpdateTask(task.copy(reminderOffsetMinutes = offset.minutes, dueDate = defaultDate, dueTime = defaultTime))
                                    showReminderDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Duration Presets
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Duration:", color = TextMuted, fontSize = 11.sp)
                durationPresets.forEach { mins ->
                    val sel = task.durationMinutes == mins
                    Surface(
                        color = if (sel) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianNav,
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, if (sel) EmeraldPrimary else ObsidianBorder),
                        modifier = Modifier.clickable {
                            onUpdateTask(task.copy(durationMinutes = if (sel) null else mins))
                        }
                    ) {
                        Text(
                            text = DateFormatHelper.formatWorkload(mins),
                            color = if (sel) EmeraldPrimary else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = ObsidianBorder)

            // Multi-Labels (@tags) Section
            Text("Labels (@tags)", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allLabels = (suggestedLabels + task.labels).distinct()
                allLabels.forEach { lbl ->
                    val active = task.labels.contains(lbl)
                    Surface(
                        color = if (active) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianNav,
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, if (active) EmeraldPrimary else ObsidianBorder),
                        modifier = Modifier.clickable {
                            val next = if (active) task.labels - lbl else task.labels + lbl
                            onUpdateTask(task.copy(labels = next))
                        }
                    ) {
                        Text(
                            text = "@$lbl",
                            color = if (active) EmeraldPrimary else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it.replace("@", "").trim() },
                    placeholder = { Text("Add custom @label...", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        if (labelInput.isNotBlank() && !task.labels.contains(labelInput)) {
                            onUpdateTask(task.copy(labels = task.labels + labelInput))
                            labelInput = ""
                        }
                    }
                ) {
                    Text("+ Tag", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = ObsidianBorder)

            // Subtasks (Steps)
            Text("Subtasks (${task.steps.count { it.isDone }}/${task.steps.size})", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            task.steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = step.isDone,
                        onCheckedChange = {
                            onUpdateTask(task.copy(steps = task.steps.map { s -> if (s.id == step.id) s.copy(isDone = !s.isDone) else s }))
                        }
                    )
                    Text(
                        text = step.title,
                        color = if (step.isDone) TextMuted else TextPrimary,
                        textDecoration = if (step.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onUpdateTask(task.copy(steps = task.steps.filterNot { it.id == step.id })) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = stepInput,
                    onValueChange = { stepInput = it },
                    placeholder = { Text("Add next step...", color = TextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (stepInput.isNotBlank()) {
                            onUpdateTask(task.copy(steps = task.steps + SubTask(title = stepInput.trim())))
                            stepInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Add", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = ObsidianBorder)

            // Static Notes
            Text("Description / Static Notes", color = AmberStreak, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            OutlinedTextField(
                value = task.notes,
                onValueChange = { onUpdateTask(task.copy(notes = it)) },
                placeholder = { Text("Auto-saving notes, links, or instructions...", color = TextMuted) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = ObsidianBorder)

            // Timestamped Comments & Activity Log
            Text("Comments & Activity Log (${task.comments.size})", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (task.comments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.comments.forEach { comment ->
                        Surface(
                            color = ObsidianNav,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = DateFormatHelper.formatCommentTime(comment.createdAtMillis),
                                        color = AmberStreak,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(comment.text, color = TextPrimary, fontSize = 13.sp)
                                }
                                IconButton(
                                    onClick = { onUpdateTask(task.copy(comments = task.comments.filterNot { it.id == comment.id })) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete Comment", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    placeholder = { Text("Post a progress update...", color = TextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (commentInput.isNotBlank()) {
                            onUpdateTask(task.copy(comments = task.comments + TaskComment(text = commentInput.trim())))
                            commentInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Post", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDueDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                        onUpdateTask(task.copy(dueDate = picked, isMyDay = picked == todayStr, myDayDate = if (picked == todayStr) todayStr else ""))
                    }
                    showDueDatePicker = false
                }) { Text("Save Date", color = EmeraldPrimary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onUpdateTask(task.copy(dueDate = null))
                    showDueDatePicker = false
                }) { Text("Clear", color = TextMuted) }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showDeadlinePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                        onUpdateTask(task.copy(deadlineDate = picked))
                    }
                    showDeadlinePicker = false
                }) { Text("Set Deadline", color = RoseAlert) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onUpdateTask(task.copy(deadlineDate = null))
                    showDeadlinePicker = false
                }) { Text("Clear", color = TextMuted) }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val existingTime = runCatching { LocalTime.parse(task.dueTime) }.getOrNull()
        val timePickerState = rememberTimePickerState(
            initialHour = existingTime?.hour ?: 14,
            initialMinute = existingTime?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = ObsidianCard,
            title = { Text("Set Start Time", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val formatted = String.format(Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)
                        onUpdateTask(task.copy(dueTime = formatted))
                        showTimePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Save Time", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onUpdateTask(task.copy(dueTime = null))
                    showTimePicker = false
                }) { Text("Clear", color = TextMuted) }
            }
        )
    }
}

@Composable
fun FilteredListSectionScreen(
    state: PersistedState,
    section: MainSection,
    customListId: String?,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onAddTask: (String, String?, Priority) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleHabitToday: (HabitItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onDeleteHabit: (HabitItem) -> Unit
) {
    val todayStr = LocalDate.now().toString()
    val activeList = state.customLists.find { it.id == customListId }
    val title = when (section) {
        MainSection.IMPORTANT -> "⭐ Important Tasks"
        MainSection.ALL_ITEMS -> "☑️ All Tasks & Habits"
        MainSection.CUSTOM_LIST -> activeList?.name ?: "Category"
        else -> "Tasks"
    }
    var newTaskTitle by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(TaskSortOption.PRIORITY) }
    var showCompletedSection by remember { mutableStateOf(false) }

    val baseTasks = when (section) {
        MainSection.IMPORTANT -> state.tasks.filter { it.isImportant || it.priority == Priority.P1 }
        MainSection.ALL_ITEMS -> state.tasks
        MainSection.CUSTOM_LIST -> state.tasks.filter { it.listId == customListId }
        else -> state.tasks
    }
    val matchingTasks = baseTasks.filter {
        searchQuery.isBlank() ||
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.notes.contains(searchQuery, ignoreCase = true) ||
            it.labels.any { l -> l.contains(searchQuery, ignoreCase = true) }
    }

    val activeTasks = when (sortOption) {
        TaskSortOption.PRIORITY -> matchingTasks.filter { !it.isCompleted }.sortedBy { it.priority.ordinal }
        TaskSortOption.DUE_TIME -> matchingTasks.filter { !it.isCompleted }.sortedBy { it.dueTime ?: "99:99" }
        TaskSortOption.DURATION -> matchingTasks.filter { !it.isCompleted }.sortedBy { it.durationMinutes ?: Int.MAX_VALUE }
        TaskSortOption.TITLE -> matchingTasks.filter { !it.isCompleted }.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }
    val completedTasks = matchingTasks.filter { it.isCompleted }

    val habits = if (section == MainSection.ALL_ITEMS || section == MainSection.CUSTOM_LIST) {
        state.habits.filter {
            (section == MainSection.ALL_ITEMS || it.listId == customListId) &&
                (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true))
        }
    } else emptyList()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Tap top-left ← to return Home", fontSize = 11.sp, color = TextMuted)
            }
            if (searchQuery.isNotBlank()) {
                AssistChip(onClick = onClearSearch, label = { Text("Clear: \"$searchQuery\"", color = EmeraldPrimary, fontSize = 11.sp) })
            }
        }

        Surface(color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    singleLine = true,
                    textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(EmeraldPrimary),
                    decorationBox = { inner ->
                        if (newTaskTitle.isEmpty()) Text("Add task to $title (supports p1, 30m, @tag)...", color = TextMuted, fontSize = 13.sp)
                        inner()
                    },
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            onAddTask(
                                newTaskTitle.trim(),
                                if (section == MainSection.CUSTOM_LIST) customListId else null,
                                if (section == MainSection.IMPORTANT) Priority.P1 else Priority.P4
                            )
                            newTaskTitle = ""
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = ObsidianBg)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sort by:", color = TextMuted, fontSize = 11.sp)
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

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (habits.isNotEmpty()) {
                item { Text("DAILY HABITS (${habits.size})", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(habits, key = { "sub_h_${it.id}" }) { habit ->
                    val done = habit.completedDates.contains(todayStr)
                    Surface(color = ObsidianCard, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth().clickable { onToggleHabitToday(habit) }) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = done, onCheckedChange = { onToggleHabitToday(habit) }, colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary, uncheckedColor = TextMuted, checkmarkColor = ObsidianBg))
                            Text(habit.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            IconButton(onClick = { onDeleteHabit(habit) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert) }
                        }
                    }
                }
            }
            if (activeTasks.isNotEmpty()) {
                item { Text("ACTIVE TASKS (${activeTasks.size})", color = AmberStreak, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(activeTasks, key = { "sub_t_${it.id}" }) { task ->
                    TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                }
            }
            if (completedTasks.isNotEmpty()) {
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
                            Text("✓ Completed (${completedTasks.size})", color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (showCompletedSection) "Hide ▲" else "Show ▼", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (showCompletedSection) {
                    items(completedTasks, key = { "sub_done_${it.id}" }) { task ->
                        TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                    }
                }
            }
        }
    }
}