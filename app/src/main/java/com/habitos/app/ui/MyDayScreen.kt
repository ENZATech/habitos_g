/*
app/src/main/java/com/habitos/app/
│
├── MainActivity.kt
│
├── data/
│   ├── models/
│   │   ├── Priority.kt             # Priority enum (P1–P4, colors, labels)
│   │   ├── Recurrence.kt           # RecurrenceType enum
│   │   ├── TaskModels.kt           # SubTask, TaskItem
│   │   ├── HabitModels.kt          # HabitItem
│   │   ├── BirthdayModels.kt       # BirthdayPerson, BirthdayWishTemplates
│   │   └── AppStateModels.kt       # CustomTaskList, UserProfile, AppSettings, PersistedState
│   │
│   └── storage/
│       └── HabitOSStorage.kt       # SharedPreferences + Gson persistence & data wipe
│
├── engine/
│   ├── WeekHelper.kt               # 7-day matrix math, 65% consistency streak calculation
│   └── DateFormatHelper.kt         # Date parsing & short formatting ("Today", "Tomorrow")
│
├── receiver/
│   ├── NotificationScheduler.kt    # AlarmManager setup, exact alarm channels & scheduling
│   ├── HabitOSAlarmReceiver.kt     # EOD check-in & midnight birthday push notifications
│   └── BootCompletedReceiver.kt   # Reschedules alarms on device reboot
│
└── ui/
    ├── theme/
    │   └── HabitOSTheme.kt         # Obsidian colors & Material3 dark color scheme
    │
    ├── components/
    │   ├── HabitOSLauncherLogo.kt  # Custom Canvas vector app logo
    │   ├── EmptyStateCard.kt       # Generic empty state UI placeholder
    │   └── AppDialogs.kt           # ClearDataVaultDialog, EodConfigDialog, AvatarBadge
    │
    └── screens/
        ├── HabitOSRootScreen.kt    # Root Scaffold, TopBar, BottomNav, Sidebar Drawer
        ├── MyDayScreen.kt          # Daily progress, unified add input, TaskRowCard
        ├── HabitMatrixScreen.kt    # 7-day grid, weekly trend chart, HabitMatrixCard
        ├── PlannedScreen.kt        # Overdue rescue, quick postpone chips (+1d, weekend)
        ├── BirthdaysScreen.kt      # Midnight/Belated rescue cards & wish generator dialogs
        ├── TaskDetailBottomSheet.kt# Todoist-style subtasks, priority pills, recurrence selector
        └── FilteredListSectionScreen.kt # Important, All Items, and Custom Categories
 */

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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDayScreen(
    state: PersistedState,
    searchQuery: String,
    onOpenSuggestions: () -> Unit,
    onNavigateToHabits: () -> Unit,
    onAddTask: (
        isHabit: Boolean,
        title: String,
        priority: Priority,
        listId: String?,
        dueDate: String?,
        cue: String,
        dueTime: String?,
        durationMinutes: Int?,
        deadlineDate: String?,
        recurrence: RecurrenceType
    ) -> Unit,
    onPostponeTask: (TaskItem, String) -> Unit,
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
    var selectedTimeStr by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDurationMin by remember { mutableStateOf<Int?>(null) }
    var selectedDeadlineStr by remember { mutableStateOf<String?>(null) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var selectedRecurrence by remember { mutableStateOf(RecurrenceType.NONE) }
    var showRecurrenceDropdown by remember { mutableStateOf(false) }
    var cueText by remember { mutableStateOf("") }

    var sortOption by remember { mutableStateOf(TaskSortOption.PRIORITY) }
    var showCompletedSection by remember { mutableStateOf(false) }

    val overdueTasks = state.tasks.filter {
        !it.isCompleted && !it.dueDate.isNullOrBlank() && it.dueDate < todayStr &&
            (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.labels.any { l -> l.contains(searchQuery, ignoreCase = true) })
    }.sortedBy { it.priority.ordinal }

    val myDayTasks = state.tasks.filter {
        (it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr
    }

    val doneMyDayTasks = myDayTasks.count { it.isCompleted }
    val doneTodayHabits = state.habits.count { it.completedDates.contains(todayStr) }
    val doneCombined = doneMyDayTasks + doneTodayHabits
    val totalCombined = myDayTasks.size + state.habits.size
    val myDayPct = if (totalCombined == 0) 0 else (doneCombined * 100) / totalCombined

    val totalScheduledMinutes = myDayTasks
        .filter { !it.isCompleted }
        .sumOf { it.durationMinutes ?: 0 }

    val consistencyDays = WeekHelper.consistencyStreakDays(state.habits, today)
    val recordStreak = state.habits.maxOfOrNull { maxOf(it.recordStreak, it.currentStreak(today)) } ?: 0
    val totalWeekChecks = weekDays.sumOf { d -> state.habits.count { it.completedDates.contains(d.toString()) } }
    val possibleWeekChecks = state.habits.size * 7
    val weeklyPct = if (possibleWeekChecks == 0) 0 else (totalWeekChecks * 100) / possibleWeekChecks
    val perfectDaysCount = weekDays.count { WeekHelper.isPerfectDay(state.habits, it) }

    val matchingMyDayTasks = myDayTasks.filter {
        searchQuery.isBlank() ||
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.notes.contains(searchQuery, ignoreCase = true) ||
            it.labels.any { l -> l.contains(searchQuery, ignoreCase = true) }
    }

    val activeMyDayTasks = when (sortOption) {
        TaskSortOption.PRIORITY -> matchingMyDayTasks.filter { !it.isCompleted }.sortedWith(compareBy<TaskItem> { it.priority.ordinal }.thenBy { it.dueTime ?: "99:99" })
        TaskSortOption.DUE_TIME -> matchingMyDayTasks.filter { !it.isCompleted }.sortedWith(compareBy<TaskItem> { it.dueTime ?: "99:99" }.thenBy { it.priority.ordinal })
        TaskSortOption.DURATION -> matchingMyDayTasks.filter { !it.isCompleted }.sortedWith(compareBy<TaskItem> { it.durationMinutes ?: Int.MAX_VALUE }.thenBy { it.priority.ordinal })
        TaskSortOption.TITLE -> matchingMyDayTasks.filter { !it.isCompleted }.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }

    val completedMyDayTasks = matchingMyDayTasks.filter { it.isCompleted }
    val filteredHabits = state.habits.filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }

    val durationPresets = listOf(15, 30, 45, 60, 90, 120)

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
                            if (totalScheduledMinutes > 0) {
                                Spacer(Modifier.height(4.dp))
                                Surface(color = TealSecondary.copy(alpha = 0.16f), shape = RoundedCornerShape(5.dp)) {
                                    Text(
                                        "⏱️ ${DateFormatHelper.formatWorkload(totalScheduledMinutes)} scheduled today",
                                        color = EmeraldPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
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

        // Quick-Add Card
        item {
            Surface(color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Surface(color = ObsidianBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(color = if (!isHabitMode) EmeraldPrimary else Color.Transparent, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).clickable { isHabitMode = false }) {
                                Text("☑️ One-Time / Recurring Task", color = if (!isHabitMode) ObsidianBg else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
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
                                if (inputText.isEmpty()) {
                                    Text(
                                        if (isHabitMode) "Enter daily habit name..."
                                        else "Task title (e.g. Revise C pointers at 5pm 45m p1 @code)...",
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                inner()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onAddTask(
                                        isHabitMode,
                                        inputText.trim(),
                                        selectedPriority,
                                        selectedListId,
                                        if (isHabitMode) null else selectedDateStr,
                                        cueText.trim(),
                                        if (isHabitMode) null else selectedTimeStr,
                                        if (isHabitMode) null else selectedDurationMin,
                                        if (isHabitMode) null else selectedDeadlineStr,
                                        if (isHabitMode) RecurrenceType.NONE else selectedRecurrence
                                    )
                                    inputText = ""
                                    cueText = ""
                                    selectedTimeStr = null
                                    selectedDurationMin = null
                                    selectedDeadlineStr = null
                                    selectedRecurrence = RecurrenceType.NONE
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
                                Text("📁 ${activeList?.name ?: "Category"} ▾", color = TextPrimary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
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
                                        if (cueText.isEmpty()) Text("Cue / Context", color = TextMuted, fontSize = 11.sp)
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

                            Surface(
                                color = if (selectedTimeStr != null) TealSecondary.copy(alpha = 0.18f) else ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (selectedTimeStr != null) EmeraldPrimary else ObsidianBorder),
                                modifier = Modifier.weight(1f).clickable { showTimePicker = true }
                            ) {
                                Text(
                                    text = if (selectedTimeStr != null) "🕒 $selectedTimeStr" else "🕒 Set Time",
                                    color = if (selectedTimeStr != null) EmeraldPrimary else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
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

                        Spacer(Modifier.height(6.dp))

                        // Duration Quick-Chips + Deadline + Recurrence Row
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            durationPresets.forEach { mins ->
                                val sel = selectedDurationMin == mins
                                val label = DateFormatHelper.formatWorkload(mins)
                                Surface(
                                    color = if (sel) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianNav,
                                    shape = RoundedCornerShape(7.dp),
                                    border = BorderStroke(1.dp, if (sel) EmeraldPrimary else ObsidianBorder),
                                    modifier = Modifier.clickable { selectedDurationMin = if (sel) null else mins }
                                ) {
                                    Text("⏱️ $label", color = if (sel) EmeraldPrimary else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                                }
                            }

                            Surface(
                                color = if (selectedDeadlineStr != null) RoseAlert.copy(alpha = 0.16f) else ObsidianNav,
                                shape = RoundedCornerShape(7.dp),
                                border = BorderStroke(1.dp, if (selectedDeadlineStr != null) RoseAlert else ObsidianBorder),
                                modifier = Modifier.clickable { showDeadlinePicker = true }
                            ) {
                                Text(
                                    text = if (selectedDeadlineStr != null) "🎯 Deadline: ${DateFormatHelper.shortDate(selectedDeadlineStr)}" else "🎯 Deadline",
                                    color = if (selectedDeadlineStr != null) RoseAlert else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Box {
                                val hasRec = selectedRecurrence != RecurrenceType.NONE
                                Surface(
                                    color = if (hasRec) AmberStreak.copy(alpha = 0.16f) else ObsidianNav,
                                    shape = RoundedCornerShape(7.dp),
                                    border = BorderStroke(1.dp, if (hasRec) AmberStreak else ObsidianBorder),
                                    modifier = Modifier.clickable { showRecurrenceDropdown = true }
                                ) {
                                    Text(
                                        text = if (hasRec) "🔁 ${selectedRecurrence.label}" else "🔁 Repeat",
                                        color = if (hasRec) AmberStreak else TextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                    }
                }
            }
        }

        // Universal Overdue Rescue Section on MyDayScreen
        if (overdueTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚨 OVERDUE (${overdueTasks.size})", color = RoseAlert, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Surface(
                        color = RoseAlert.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            overdueTasks.forEach { onPostponeTask(it, todayStr) }
                        }
                    ) {
                        Text("Reschedule All to Today", color = RoseAlert, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            items(overdueTasks, key = { "myday_ov_${it.id}" }) { task ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TaskRowCard(
                        task = task,
                        customLists = state.customLists,
                        onToggle = { onToggleTask(task) },
                        onClick = { onClickTask(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PostponeChip("Today") { onPostponeTask(task, todayStr) }
                        PostponeChip("+1 Day") { onPostponeTask(task, today.plusDays(1).toString()) }
                        PostponeChip("This Weekend") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()) }
                        PostponeChip("Next Week") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()) }
                    }
                }
            }
        }

        // Daily Habits Section
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

        // My Day Tasks + View Sort Header
        if (matchingMyDayTasks.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("MY DAY TASKS (${completedMyDayTasks.size}/${matchingMyDayTasks.size})", color = AmberStreak, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sort:", color = TextMuted, fontSize = 10.sp)
                            TaskSortOption.entries.forEach { opt ->
                                val sel = sortOption == opt
                                Surface(
                                    color = if (sel) EmeraldPrimary.copy(alpha = 0.18f) else ObsidianNav,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (sel) EmeraldPrimary else ObsidianBorder),
                                    modifier = Modifier.clickable { sortOption = opt }
                                ) {
                                    Text(opt.label, color = if (sel) EmeraldPrimary else TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }

            items(activeMyDayTasks, key = { "myday_t_${it.id}" }) { task ->
                TaskRowCard(
                    task = task,
                    customLists = state.customLists,
                    onToggle = { onToggleTask(task) },
                    onClick = { onClickTask(task) },
                    onDelete = { onDeleteTask(task) }
                )
            }

            // Collapsible Completed Tasks Section
            if (completedMyDayTasks.isNotEmpty()) {
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
                            Text("✓ Completed (${completedMyDayTasks.size})", color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (showCompletedSection) "Hide ▲" else "Show ▼", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (showCompletedSection) {
                    items(completedMyDayTasks, key = { "myday_done_${it.id}" }) { task ->
                        TaskRowCard(
                            task = task,
                            customLists = state.customLists,
                            onToggle = { onToggleTask(task) },
                            onClick = { onClickTask(task) },
                            onDelete = { onDeleteTask(task) }
                        )
                    }
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
                    pickerState.selectedDateMillis?.let { selectedDateStr = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString() }
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
        val timePickerState = rememberTimePickerState(initialHour = 14, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = ObsidianCard,
            title = { Text("Select Task Start Time", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
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

@Composable
fun TaskRowCard(
    task: TaskItem,
    customLists: List<CustomTaskList>,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val today = LocalDate.now()
    val listTag = customLists.find { it.id == task.listId }
    val prioColor = Color(task.priority.colorHex)
    val timeBlockText = DateFormatHelper.formatTimeBlock(task.dueTime, task.durationMinutes)
    val deadlineInfo = DateFormatHelper.deadlineBadgeInfo(task.deadlineDate, today)

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
                colors = CheckboxDefaults.colors(
                    checkedColor = if (task.priority != Priority.P4) prioColor else EmeraldPrimary,
                    uncheckedColor = if (task.priority != Priority.P4) prioColor else TextMuted,
                    checkmarkColor = ObsidianBg
                )
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = task.title,
                    color = if (task.isCompleted) TextMuted else TextPrimary,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    if (task.priority != Priority.P4) {
                        Surface(color = prioColor.copy(alpha = 0.16f), shape = RoundedCornerShape(4.dp)) {
                            Text(task.priority.shortLabel, color = prioColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    if (listTag != null) {
                        Text("● ${listTag.name}", color = Color(listTag.colorHex), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    if (!task.dueDate.isNullOrBlank()) {
                        val isOverdue = !task.isCompleted && task.dueDate < today.toString()
                        Text(
                            text = "📅 ${DateFormatHelper.shortDate(task.dueDate, today)}",
                            color = if (isOverdue) RoseAlert else AmberStreak,
                            fontSize = 11.sp,
                            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                    if (timeBlockText != null) {
                        Surface(color = TealSecondary.copy(alpha = 0.16f), shape = RoundedCornerShape(4.dp)) {
                            Text(timeBlockText, color = EmeraldPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                    if (deadlineInfo != null) {
                        val (dlLabel, isUrgent) = deadlineInfo
                        val dlColor = if (isUrgent) RoseAlert else AmberStreak
                        Surface(color = dlColor.copy(alpha = 0.16f), shape = RoundedCornerShape(4.dp), border = BorderStroke(0.5.dp, dlColor)) {
                            Text(dlLabel, color = dlColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                    if (task.recurrence != RecurrenceType.NONE) {
                        Text("🔁 ${task.recurrence.label}", color = AmberStreak, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (task.steps.isNotEmpty()) {
                        Text("☑ ${task.steps.count { it.isDone }}/${task.steps.size}", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (task.comments.isNotEmpty()) {
                        Text("💬 ${task.comments.size}", color = TextMuted, fontSize = 10.sp)
                    }
                    task.labels.forEach { lbl ->
                        Surface(color = ObsidianNav, shape = RoundedCornerShape(4.dp), border = BorderStroke(0.5.dp, ObsidianBorder)) {
                            Text("@$lbl", color = EmeraldPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun PostponeChip(label: String, onClick: () -> Unit) {
    Surface(
        color = ObsidianNav,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}