package com.habitos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDayScreen(
    state: PersistedState,
    searchQuery: String,
    onOpenSuggestions: () -> Unit,
    onNavigateToHabits: () -> Unit,
    onAddTask: (isHabit: Boolean, title: String, priority: Priority, listId: String?, dueDate: String?, cue: String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleHabitToday: (HabitItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onDeleteHabit: (HabitItem) -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()
    val weekDays = remember { WeekHelper.currentWeekDays(today) }

    var inputText by remember { mutableStateOf("") }
    var isHabitMode by remember { mutableStateOf(false) }
    var selectedPriority by remember { mutableStateOf(Priority.P4) }
    var selectedListId by remember { mutableStateOf(state.customLists.firstOrNull()?.id) }
    var showListDropdown by remember { mutableStateOf(false) }
    var showPriorityDropdown by remember { mutableStateOf(false) }
    var selectedDateStr by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }
    var cueText by remember { mutableStateOf("") }

    val myDayTasks = state.tasks.filter {
        (it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr
    }.sortedBy { it.priority.ordinal }

    val doneMyDayTasks = myDayTasks.count { it.isCompleted }
    val doneTodayHabits = state.habits.count { it.completedDates.contains(todayStr) }
    val doneCombined = doneMyDayTasks + doneTodayHabits
    val totalCombined = myDayTasks.size + state.habits.size
    val myDayPct = if (totalCombined == 0) 0 else (doneCombined * 100) / totalCombined

    val consistencyDays = WeekHelper.consistencyStreakDays(state.habits, today)
    val recordStreak = state.habits.maxOfOrNull { maxOf(it.recordStreak, it.currentStreak(today)) } ?: 0
    val totalWeekChecks = weekDays.sumOf { d -> state.habits.count { it.completedDates.contains(d.toString()) } }
    val possibleWeekChecks = state.habits.size * 7
    val weeklyPct = if (possibleWeekChecks == 0) 0 else (totalWeekChecks * 100) / possibleWeekChecks
    val perfectDaysCount = weekDays.count { WeekHelper.isPerfectDay(state.habits, it) }

    val filteredTasks = myDayTasks.filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) }
    val filteredHabits = state.habits.filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.weight(1f).clickable { onOpenSuggestions() }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("MY DAY PROGRESS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.WbSunny, contentDescription = null, tint = AmberStreak, modifier = Modifier.size(15.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$doneCombined/$totalCombined", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.width(6.dp))
                                Text("$myDayPct% done", color = EmeraldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("$doneMyDayTasks/${myDayTasks.size} tasks • $doneTodayHabits/${state.habits.size} habits", color = TextMuted, fontSize = 10.sp, maxLines = 1)
                        }
                    }

                    Surface(
                        color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.weight(1f).clickable { onNavigateToHabits() }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("CONSISTENCY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Record: ${recordStreak}d", color = AmberStreak, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$consistencyDays", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.width(5.dp))
                                Text("Streak Days", color = AmberStreak, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("Days hitting ≥65% habits", color = TextMuted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }

                Surface(
                    color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToHabits() }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("WEEKLY HABIT SCORE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Surface(color = EmeraldPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                    Text("👑 $perfectDaysCount Perfect", color = EmeraldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$weeklyPct%", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.width(6.dp))
                                Text("$totalWeekChecks/$possibleWeekChecks checks • Open Matrix", color = TextMuted, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        Surface(color = EmeraldPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.padding(8.dp).size(20.dp))
                        }
                    }
                }
            }
        }

        item {
            Surface(color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Surface(color = ObsidianBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(color = if (!isHabitMode) EmeraldPrimary else Color.Transparent, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).clickable { isHabitMode = false }) {
                                Text("☑️ One-Time Task", color = if (!isHabitMode) ObsidianBg else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                            }
                            Surface(color = if (isHabitMode) EmeraldPrimary else Color.Transparent, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).clickable { isHabitMode = true }) {
                                Text("🔄 Daily Habit", color = if (isHabitMode) ObsidianBg else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            singleLine = true,
                            textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(EmeraldPrimary),
                            decorationBox = { inner ->
                                if (inputText.isEmpty()) Text(if (isHabitMode) "Enter daily habit name..." else "Enter task title...", color = TextMuted, fontSize = 13.sp)
                                inner()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onAddTask(isHabitMode, inputText.trim(), selectedPriority, selectedListId, if (isHabitMode) null else selectedDateStr, cueText.trim())
                                    inputText = ""
                                    cueText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Add", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = ObsidianBorder)
                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            val activeList = state.customLists.find { it.id == selectedListId }
                            Surface(color = ObsidianNav, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth().clickable { showListDropdown = true }) {
                                Text("📁 ${activeList?.name ?: "Category"} ▾", color = TextPrimary, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                            }
                            DropdownMenu(expanded = showListDropdown, onDismissRequest = { showListDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                                state.customLists.forEach { list ->
                                    DropdownMenuItem(text = { Text(list.name, color = TextPrimary, fontSize = 12.sp) }, onClick = { selectedListId = list.id; showListDropdown = false })
                                }
                            }
                        }

                        Surface(color = ObsidianNav, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🎯 ", fontSize = 11.sp)
                                BasicTextField(
                                    value = cueText,
                                    onValueChange = { cueText = it },
                                    singleLine = true,
                                    textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 11.sp),
                                    cursorBrush = SolidColor(EmeraldPrimary),
                                    decorationBox = { inner ->
                                        if (cueText.isEmpty()) Text("Cue (e.g. 30m)", color = TextMuted, fontSize = 11.sp)
                                        inner()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (!isHabitMode) {
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(color = ObsidianNav, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.weight(1f).clickable { showDatePicker = true }) {
                                Text("📅 ${DateFormatHelper.shortDate(selectedDateStr)}", color = AmberStreak, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = Color(selectedPriority.colorHex).copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(selectedPriority.colorHex)),
                                    modifier = Modifier.fillMaxWidth().clickable { showPriorityDropdown = true }
                                ) {
                                    Text("🚩 ${selectedPriority.shortLabel}", color = Color(selectedPriority.colorHex), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
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
                        }
                    }
                }
            }
        }

        if (filteredHabits.isNotEmpty()) {
            item { Text("TODAY'S DAILY HABITS (${filteredHabits.count { it.completedDates.contains(todayStr) }}/${filteredHabits.size})", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            items(filteredHabits, key = { "myday_h_${it.id}" }) { habit ->
                val done = habit.completedDates.contains(todayStr)
                Surface(color = ObsidianCard, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth().clickable { onToggleHabitToday(habit) }) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = done, onCheckedChange = { onToggleHabitToday(habit) }, colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary, uncheckedColor = TextMuted, checkmarkColor = ObsidianBg))
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(habit.name, color = if (done) TextMuted else TextPrimary, textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("🔥 Streak: ${habit.currentStreak(today)}d${if (habit.cue.isNotBlank()) " • 🎯 ${habit.cue}" else ""}", color = AmberStreak, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onDeleteHabit(habit) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert) }
                    }
                }
            }
        }

        if (filteredTasks.isNotEmpty()) {
            item { Text("MY DAY TASKS (${filteredTasks.count { it.isCompleted }}/${filteredTasks.size})", color = AmberStreak, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            items(filteredTasks, key = { "myday_t_${it.id}" }) { task ->
                TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selectedDateStr = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString() }
                    showDatePicker = false
                }) { Text("Select", color = EmeraldPrimary) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextMuted) } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
fun TaskRowCard(
    task: TaskItem,
    customLists: List<CustomTaskList>,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val listTag = customLists.find { it.id == task.listId }
    val prioColor = Color(task.priority.colorHex)

    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (task.priority == Priority.P1 && !task.isCompleted) prioColor.copy(alpha = 0.6f) else ObsidianBorder),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = if (task.priority != Priority.P4) prioColor else EmeraldPrimary, uncheckedColor = if (task.priority != Priority.P4) prioColor else TextMuted, checkmarkColor = ObsidianBg)
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = task.title,
                    color = if (task.isCompleted) TextMuted else TextPrimary,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (task.priority != Priority.P4) {
                        Surface(color = prioColor.copy(alpha = 0.16f), shape = RoundedCornerShape(4.dp)) {
                            Text(task.priority.shortLabel, color = prioColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (listTag != null) {
                        Text("● ${listTag.name}", color = Color(listTag.colorHex), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (!task.dueDate.isNullOrBlank()) {
                        Text("📅 ${DateFormatHelper.shortDate(task.dueDate)}", color = AmberStreak, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert, modifier = Modifier.size(18.dp))
            }
        }
    }
}