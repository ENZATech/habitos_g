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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

enum class HabitOSTab(val label: String) {
    MY_DAY("My Day"),
    HABITS("Habits"),
    BIRTHDAYS("Birthdays"),
    PLANNED("Planned"),
    LISTS("Lists")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitOSRootScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var appState by remember { mutableStateOf(HabitOSStorage.load(context)) }
    var currentTab by remember { mutableStateOf(HabitOSTab.MY_DAY) }

    // Active Task Detail Sheet ID
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var showSuggestionsSheet by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var showEodTimeDialog by remember { mutableStateOf(false) }

    fun persist(next: PersistedState) {
        appState = next
        HabitOSStorage.save(context, next)
        NotificationScheduler.scheduleAllAlarms(context, next)
    }

    fun trigger5SecUndoSnackbar(message: String, onUndo: () -> Unit) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = withTimeoutOrNull(5000L) {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Indefinite
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                onUndo()
            } else {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }

    fun deleteTaskWithUndo(task: TaskItem) {
        val snapshot = appState.tasks
        persist(appState.copy(tasks = snapshot.filterNot { it.id == task.id }))
        if (selectedTaskId == task.id) selectedTaskId = null
        trigger5SecUndoSnackbar("Deleted task \"${task.title}\"") {
            persist(appState.copy(tasks = snapshot))
        }
    }

    fun deleteHabitWithUndo(habit: HabitItem) {
        val snapshot = appState.habits
        persist(appState.copy(habits = snapshot.filterNot { it.id == habit.id }))
        trigger5SecUndoSnackbar("Deleted habit \"${habit.name}\"") {
            persist(appState.copy(habits = snapshot))
        }
    }

    fun deleteBirthdayWithUndo(person: BirthdayPerson) {
        val snapshot = appState.birthdays
        persist(appState.copy(birthdays = snapshot.filterNot { it.id == person.id }))
        trigger5SecUndoSnackbar("Removed ${person.name}'s birthday") {
            persist(appState.copy(birthdays = snapshot))
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = ObsidianCard,
                    contentColor = TextPrimary,
                    actionColor = EmeraldPrimary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBg,
                    titleContentColor = TextPrimary
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(EmeraldPrimary, TealSecondary)
                                    )
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "habitOS",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = ObsidianCard,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Text(
                                text = "NATIVE",
                                color = EmeraldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // EOD Notification Clock button
                    TextButton(onClick = { showEodTimeDialog = true }) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = "EOD Alert Time",
                            tint = AmberStreak,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "%02d:%02d",
                                appState.settings.eodHour,
                                appState.settings.eodMinute
                            ),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Security-Protected Clear Data Vault Button
                    IconButton(onClick = { showVaultDialog = true }) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Security Data Vault",
                            tint = RoseAlert
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianNav,
                tonalElevation = 0.dp,
                modifier = Modifier.border(BorderStroke(1.dp, ObsidianBorder))
            ) {
                HabitOSTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            val icon = when (tab) {
                                HabitOSTab.MY_DAY -> Icons.Default.WbSunny
                                HabitOSTab.HABITS -> Icons.Default.GridOn
                                HabitOSTab.BIRTHDAYS -> Icons.Default.Cake
                                HabitOSTab.PLANNED -> Icons.Default.CalendarMonth
                                HabitOSTab.LISTS -> Icons.Default.FormatListBulleted
                            }
                            Icon(icon, contentDescription = tab.label)
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldPrimary,
                            selectedTextColor = EmeraldPrimary,
                            indicatorColor = EmeraldPrimary.copy(alpha = 0.15f),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBg)
        ) {
            when (currentTab) {
                HabitOSTab.MY_DAY -> MyDayScreen(
                    state = appState,
                    onOpenSuggestions = { showSuggestionsSheet = true },
                    onAddTask = { title, important ->
                        val todayStr = LocalDate.now().toString()
                        val newTask = TaskItem(
                            title = title,
                            isImportant = important,
                            isMyDay = true,
                            myDayDate = todayStr
                        )
                        persist(appState.copy(tasks = listOf(newTask) + appState.tasks))
                    },
                    onToggleTask = { task ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        persist(
                            appState.copy(
                                tasks = appState.tasks.map {
                                    if (it.id == task.id) it.copy(isCompleted = !it.isCompleted) else it
                                }
                            )
                        )
                    },
                    onToggleImportant = { task ->
                        persist(
                            appState.copy(
                                tasks = appState.tasks.map {
                                    if (it.id == task.id) it.copy(isImportant = !it.isImportant) else it
                                }
                            )
                        )
                    },
                    onClickTask = { selectedTaskId = it.id },
                    onDeleteTask = ::deleteTaskWithUndo
                )

                HabitOSTab.HABITS -> HabitGridScreen(
                    state = appState,
                    onAddHabit = { name ->
                        val newHabit = HabitItem(name = name)
                        persist(appState.copy(habits = appState.habits + newHabit))
                    },
                    onToggleHabitDate = { habit, dateStr ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        persist(
                            appState.copy(
                                habits = appState.habits.map {
                                    if (it.id == habit.id) it.updatedWithToggle(dateStr) else it
                                }
                            )
                        )
                    },
                    onDeleteHabit = ::deleteHabitWithUndo
                )

                HabitOSTab.BIRTHDAYS -> BirthdaysScreen(
                    state = appState,
                    onAddBirthday = { name, day, month, phone ->
                        val person = BirthdayPerson(
                            name = name,
                            dayOfMonth = day,
                            monthValue = month,
                            phone = phone
                        )
                        persist(appState.copy(birthdays = appState.birthdays + person))
                    },
                    onMarkWished = { person ->
                        val yr = LocalDate.now().year
                        persist(
                            appState.copy(
                                birthdays = appState.birthdays.map {
                                    if (it.id == person.id) it.copy(wishedYear = yr) else it
                                }
                            )
                        )
                    },
                    onDeleteBirthday = ::deleteBirthdayWithUndo
                )

                HabitOSTab.PLANNED -> PlannedScreen(
                    state = appState,
                    onAddPlannedTask = { title, dueDateStr ->
                        val isToday = dueDateStr == LocalDate.now().toString()
                        val newTask = TaskItem(
                            title = title,
                            dueDate = dueDateStr,
                            isMyDay = isToday,
                            myDayDate = if (isToday) dueDateStr else ""
                        )
                        persist(appState.copy(tasks = listOf(newTask) + appState.tasks))
                    },
                    onToggleTask = { task ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        persist(
                            appState.copy(
                                tasks = appState.tasks.map {
                                    if (it.id == task.id) it.copy(isCompleted = !it.isCompleted) else it
                                }
                            )
                        )
                    },
                    onToggleImportant = { task ->
                        persist(
                            appState.copy(
                                tasks = appState.tasks.map {
                                    if (it.id == task.id) it.copy(isImportant = !it.isImportant) else it
                                }
                            )
                        )
                    },
                    onClickTask = { selectedTaskId = it.id },
                    onDeleteTask = ::deleteTaskWithUndo
                )

                HabitOSTab.LISTS -> CustomListsScreen(
                    state = appState,
                    onAddList = { name, colorHex ->
                        val newList = CustomTaskList(name = name, colorHex = colorHex)
                        persist(appState.copy(customLists = appState.customLists + newList))
                    },
                    onDeleteList = { list ->
                        persist(
                            appState.copy(
                                customLists = appState.customLists.filterNot { it.id == list.id },
                                tasks = appState.tasks.map {
                                    if (it.listId == list.id) it.copy(listId = null) else it
                                }
                            )
                        )
                    },
                    onAddTaskToList = { title, listId ->
                        val newTask = TaskItem(title = title, listId = listId)
                        persist(appState.copy(tasks = listOf(newTask) + appState.tasks))
                    },
                    onToggleTask = { task ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        persist(
                            appState.copy(
                                tasks = appState.tasks.map {
                                    if (it.id == task.id) it.copy(isCompleted = !it.isCompleted) else it
                                }
                            )
                        )
                    },
                    onToggleImportant = { task ->
                        persist(
                            appState.copy(
                                tasks = appState.tasks.map {
                                    if (it.id == task.id) it.copy(isImportant = !it.isImportant) else it
                                }
                            )
                        )
                    },
                    onClickTask = { selectedTaskId = it.id },
                    onDeleteTask = ::deleteTaskWithUndo
                )
            }
        }
    }

    // Task Detail Bottom Sheet (Subtasks + Auto-saving Notes)
    val activeTask = appState.tasks.find { it.id == selectedTaskId }
    if (activeTask != null) {
        TaskDetailBottomSheet(
            task = activeTask,
            customLists = appState.customLists,
            onDismiss = { selectedTaskId = null },
            onUpdateTask = { updated ->
                persist(
                    appState.copy(
                        tasks = appState.tasks.map { if (it.id == updated.id) updated else it }
                    )
                )
            },
            onDeleteTask = {
                deleteTaskWithUndo(activeTask)
                selectedTaskId = null
            }
        )
    }

    // My Day Suggestions Bottom Sheet
    if (showSuggestionsSheet) {
        val todayStr = LocalDate.now().toString()
        val suggestions = appState.tasks.filter {
            !it.isCompleted && !(it.isMyDay && it.myDayDate == todayStr)
        }
        SuggestionsBottomSheet(
            suggestions = suggestions,
            onDismiss = { showSuggestionsSheet = false },
            onAddToMyDay = { task ->
                persist(
                    appState.copy(
                        tasks = appState.tasks.map {
                            if (it.id == task.id) it.copy(isMyDay = true, myDayDate = todayStr) else it
                        }
                    )
                )
            }
        )
    }

    // Security-Protected Clear Data Vault Dialog
    if (showVaultDialog) {
        ClearDataVaultDialog(
            currentPin = appState.settings.vaultPin,
            onDismiss = { showVaultDialog = false },
            onUpdatePin = { newPin ->
                persist(appState.copy(settings = appState.settings.copy(vaultPin = newPin)))
                Toast.makeText(context, "Vault PIN updated", Toast.LENGTH_SHORT).show()
            },
            onClearAllData = {
                appState = HabitOSStorage.clearAllResettingKeepPin(context, appState.settings.vaultPin)
                showVaultDialog = false
                Toast.makeText(context, "All data & streaks reset to a clean slate.", Toast.LENGTH_LONG).show()
            }
        )
    }

    // EOD Notification Time Configuration Dialog
    if (showEodTimeDialog) {
        EodConfigDialog(
            currentHour = appState.settings.eodHour,
            currentMinute = appState.settings.eodMinute,
            onDismiss = { showEodTimeDialog = false },
            onSave = { h, m ->
                persist(
                    appState.copy(
                        settings = appState.settings.copy(eodHour = h, eodMinute = m)
                    )
                )
                showEodTimeDialog = false
                Toast.makeText(
                    context,
                    String.format(Locale.getDefault(), "EOD Reminder set to %02d:%02d", h, m),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

// =========================================================================
// TAB 1: MY DAY SCREEN
// =========================================================================
@Composable
fun MyDayScreen(
    state: PersistedState,
    onOpenSuggestions: () -> Unit,
    onAddTask: (String, Boolean) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleImportant: (TaskItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()
    var newTaskTitle by remember { mutableStateOf("") }
    var markImportant by remember { mutableStateOf(false) }

    val myDayTasks = state.tasks.filter {
        (it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr
    }
    val unfinishedSuggestionsCount = state.tasks.count {
        !it.isCompleted && !(it.isMyDay && it.myDayDate == todayStr)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header + Suggestions button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "My Day",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${
                        today.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    } ${today.dayOfMonth}",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }

            OutlinedButton(
                onClick = onOpenSuggestions,
                border = BorderStroke(1.dp, if (unfinishedSuggestionsCount > 0) AmberStreak else ObsidianBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = ObsidianCard)
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = "Suggestions",
                    tint = AmberStreak,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Suggestions ($unfinishedSuggestionsCount)",
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            }
        }

        // Quick Add Task Bar
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text("Add a task to My Day...", color = TextMuted, fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { markImportant = !markImportant }) {
                    Icon(
                        imageVector = if (markImportant) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Important",
                        tint = if (markImportant) AmberStreak else TextMuted
                    )
                }
                FilledIconButton(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            onAddTask(newTaskTitle.trim(), markImportant)
                            newTaskTitle = ""
                            markImportant = false
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = ObsidianBg)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (myDayTasks.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.WbSunny,
                title = "Clean Slate for Today",
                subtitle = "Add your focus tasks above or pull unfinished items from Suggestions."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(myDayTasks, key = { it.id }) { task ->
                    TaskRowCard(
                        task = task,
                        customLists = state.customLists,
                        onToggle = { onToggleTask(task) },
                        onToggleImportant = { onToggleImportant(task) },
                        onClick = { onClickTask(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskRowCard(
    task: TaskItem,
    customLists: List<CustomTaskList>,
    onToggle: () -> Unit,
    onToggleImportant: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val listTag = customLists.find { it.id == task.listId }
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (task.isImportant && !task.isCompleted) AmberStreak.copy(alpha = 0.5f) else ObsidianBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = EmeraldPrimary,
                    uncheckedColor = TextMuted,
                    checkmarkColor = ObsidianBg
                )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = task.title,
                    color = if (task.isCompleted) TextMuted else TextPrimary,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
                val metaParts = mutableListOf<String>()
                if (task.steps.isNotEmpty()) {
                    val doneSteps = task.steps.count { it.isDone }
                    metaParts.add("$doneSteps/${task.steps.size} steps")
                }
                if (!task.dueDate.isNullOrBlank()) {
                    metaParts.add("Due ${task.dueDate}")
                }
                if (task.notes.isNotBlank()) {
                    metaParts.add("📝 Note")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (listTag != null) {
                        Text(
                            text = "● ${listTag.name}",
                            color = Color(listTag.colorHex),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" • "),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            IconButton(onClick = onToggleImportant) {
                Icon(
                    imageVector = if (task.isImportant) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Star",
                    tint = if (task.isImportant) AmberStreak else TextMuted
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete Task",
                    tint = RoseAlert
                )
            }
        }
    }
}

// =========================================================================
// TAB 2: 7-DAY INTERACTIVE HABIT MATRIX & WEEKLY STREAK CHART
// =========================================================================
@Composable
fun HabitGridScreen(
    state: PersistedState,
    onAddHabit: (String) -> Unit,
    onToggleHabitDate: (HabitItem, String) -> Unit,
    onDeleteHabit: (HabitItem) -> Unit
) {
    val weekDays = remember { WeekHelper.currentWeekDays() }
    val today = LocalDate.now()
    var newHabitName by remember { mutableStateOf("") }

    val perfectDaysCount = weekDays.count { WeekHelper.isPerfectDay(state.habits, it) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "7-Day Habit Matrix",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Mon–Sun Check-In • Haptic Streak Engine",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberStreak.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "👑", fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$perfectDaysCount Perfect",
                            color = AmberStreak,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Weekly Completion Bar / Trend Chart Card
        item {
            WeeklyCompletionBarChart(habits = state.habits, weekDays = weekDays, today = today)
        }

        // Add New Habit Bar
        item {
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newHabitName,
                        onValueChange = { newHabitName = it },
                        placeholder = { Text("New daily habit (e.g. Code 1 hr, Pull-ups)...", color = TextMuted, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = {
                            if (newHabitName.isNotBlank()) {
                                onAddHabit(newHabitName.trim())
                                newHabitName = ""
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Habit", tint = ObsidianBg)
                    }
                }
            }
        }

        if (state.habits.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.GridOn,
                    title = "0 Preloaded Habits",
                    subtitle = "Add your first daily habit above to unlock the 7-day matrix, streaks, and Perfect Day crowns (👑)."
                )
            }
        } else {
            items(state.habits, key = { it.id }) { habit ->
                HabitMatrixCard(
                    habit = habit,
                    weekDays = weekDays,
                    today = today,
                    onToggleDate = { dateStr -> onToggleHabitDate(habit, dateStr) },
                    onDelete = { onDeleteHabit(habit) }
                )
            }
        }
    }
}

@Composable
fun WeeklyCompletionBarChart(
    habits: List<HabitItem>,
    weekDays: List<LocalDate>,
    today: LocalDate
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly Completion Trend",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                val totalPossible = (habits.size * 7).coerceAtLeast(1)
                val totalDone = weekDays.sumOf { d ->
                    habits.count { it.completedDates.contains(d.toString()) }
                }
                val weeklyPct = if (habits.isEmpty()) 0 else (totalDone * 100) / totalPossible
                Text(
                    text = "$weeklyPct% This Week",
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weekDays.forEach { day ->
                    val doneCount = habits.count { it.completedDates.contains(day.toString()) }
                    val ratio = if (habits.isEmpty()) 0f else doneCount.toFloat() / habits.size.toFloat()
                    val isPerfect = WeekHelper.isPerfectDay(habits, day)
                    val isToday = day == today

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isPerfect) "👑" else "${(ratio * 100).toInt()}%",
                            fontSize = 10.sp,
                            color = if (isPerfect) AmberStreak else TextMuted
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(50.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ObsidianNav),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(ratio.coerceIn(0.06f, 1f))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isPerfect) {
                                            Brush.verticalGradient(listOf(AmberStreak, EmeraldPrimary))
                                        } else {
                                            Brush.verticalGradient(listOf(EmeraldPrimary, TealSecondary))
                                        }
                                    )
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                            fontSize = 11.sp,
                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (isToday) EmeraldPrimary else TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HabitMatrixCard(
    habit: HabitItem,
    weekDays: List<LocalDate>,
    today: LocalDate,
    onToggleDate: (String) -> Unit,
    onDelete: () -> Unit
) {
    val activeStreak = habit.currentStreak(today)
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "🔥 Streak: ${activeStreak}d",
                            color = AmberStreak,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "🏆 Record: ${maxOf(habit.recordStreak, activeStreak)}d",
                            color = EmeraldPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete Habit",
                        tint = RoseAlert
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 7-Day Interactive Check-In Row (Mon - Sun)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekDays.forEach { date ->
                    val dateStr = date.toString()
                    val checked = habit.completedDates.contains(dateStr)
                    val isToday = date == today

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleDate(dateStr) }
                            .padding(4.dp)
                    ) {
                        Text(
                            text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            fontSize = 11.sp,
                            color = if (isToday) EmeraldPrimary else TextMuted,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (checked) EmeraldPrimary else ObsidianNav
                                )
                                .border(
                                    width = 1.dp,
                                    color = when {
                                        checked -> EmeraldPrimary
                                        isToday -> AmberStreak
                                        else -> ObsidianBorder
                                    },
                                    shape = RoundedCornerShape(9.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (checked) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Done",
                                    tint = ObsidianBg,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 3: FRIENDS' BIRTHDAYS & BELATED RESCUE
// =========================================================================
@Composable
fun BirthdaysScreen(
    state: PersistedState,
    onAddBirthday: (String, Int, Int, String) -> Unit,
    onMarkWished: (BirthdayPerson) -> Unit,
    onDeleteBirthday: (BirthdayPerson) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    var showAddDialog by remember { mutableStateOf(false) }
    var activeWishTarget by remember { mutableStateOf<Pair<BirthdayPerson, Boolean>?>(null) }

    val missedYesterday = state.birthdays.filter {
        it.isBirthdayOn(yesterday) && it.wishedYear != today.year
    }
    val todayBirthdays = state.birthdays.filter {
        it.isBirthdayOn(today)
    }
    val upcomingBirthdays = state.birthdays
        .filter { !it.isBirthdayOn(today) }
        .sortedBy { it.daysUntilNext(today) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Birthdays & Rescue",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Day-before & 12:00 Midnight native alerts active",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianBg)
                    Spacer(Modifier.width(4.dp))
                    Text("Add", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 🚨 YESTERDAY - Missed! Section
        if (missedYesterday.isNotEmpty()) {
            item {
                Text(
                    text = "🚨 YESTERDAY - Missed! (Belated Rescue)",
                    color = RoseAlert,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
            items(missedYesterday, key = { "missed_${it.id}" }) { person ->
                BirthdayCard(
                    person = person,
                    badgeText = "🚨 YESTERDAY - Missed!",
                    badgeColor = RoseAlert,
                    actionLabel = "1-Tap Belated Rescue",
                    onWishClick = { activeWishTarget = person to true },
                    onDelete = { onDeleteBirthday(person) }
                )
            }
        }

        // 🎂 TODAY Section
        if (todayBirthdays.isNotEmpty()) {
            item {
                Text(
                    text = "🎂 TODAY'S BIRTHDAYS",
                    color = AmberStreak,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
            items(todayBirthdays, key = { "today_${it.id}" }) { person ->
                val wished = person.wishedYear == today.year
                BirthdayCard(
                    person = person,
                    badgeText = if (wished) "✓ Wished Today" else "🎂 TODAY!",
                    badgeColor = if (wished) EmeraldPrimary else AmberStreak,
                    actionLabel = "Send Birthday Wish",
                    onWishClick = { activeWishTarget = person to false },
                    onDelete = { onDeleteBirthday(person) }
                )
            }
        }

        // 📅 UPCOMING Section
        if (upcomingBirthdays.isNotEmpty()) {
            item {
                Text(
                    text = "📅 UPCOMING BIRTHDAYS",
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            items(upcomingBirthdays, key = { "up_${it.id}" }) { person ->
                val daysLeft = person.daysUntilNext(today)
                BirthdayCard(
                    person = person,
                    badgeText = "In $daysLeft day(s)",
                    badgeColor = TealSecondary,
                    actionLabel = "Preview Wish",
                    onWishClick = { activeWishTarget = person to false },
                    onDelete = { onDeleteBirthday(person) }
                )
            }
        }

        if (state.birthdays.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Cake,
                    title = "0 Sample Birthdays",
                    subtitle = "Add your friends' birthdays for 1-day-prior alerts, 12:00 Midnight reminders, and 1-tap Belated WhatsApp Rescue."
                )
            }
        }
    }

    if (showAddDialog) {
        AddBirthdayDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, day, month, phone ->
                onAddBirthday(name, day, month, phone)
                showAddDialog = false
            }
        )
    }

    val target = activeWishTarget
    if (target != null) {
        val (person, isBelated) = target
        BirthdayWishGeneratorDialog(
            person = person,
            isBelated = isBelated,
            onDismiss = { activeWishTarget = null },
            onCopyText = { msg ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Birthday Wish", msg))
                onMarkWished(person)
                Toast.makeText(context, "Wish copied to clipboard!", Toast.LENGTH_SHORT).show()
                activeWishTarget = null
            },
            onSendWhatsApp = { msg ->
                onMarkWished(person)
                val encoded = URLEncoder.encode(msg, "UTF-8")
                val cleanPhone = person.phone.filter { it.isDigit() }
                val url = if (cleanPhone.isNotEmpty()) {
                    "https://wa.me/$cleanPhone?text=$encoded"
                } else {
                    "https://wa.me/?text=$encoded"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                }
                activeWishTarget = null
            }
        )
    }
}

@Composable
fun BirthdayCard(
    person: BirthdayPerson,
    badgeText: String,
    badgeColor: Color,
    actionLabel: String,
    onWishClick: () -> Unit,
    onDelete: () -> Unit
) {
    val monthName = Month.of(person.monthValue).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = person.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "$monthName ${person.dayOfMonth}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete Birthday",
                        tint = RoseAlert
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onWishClick,
                colors = ButtonDefaults.buttonColors(containerColor = badgeColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =========================================================================
// TAB 4: PLANNED SCREEN (Overdue, Today, Upcoming)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedScreen(
    state: PersistedState,
    onAddPlannedTask: (String, String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleImportant: (TaskItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()

    var title by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }

    val plannedTasks = state.tasks.filter { !it.dueDate.isNullOrBlank() }
    val overdue = plannedTasks.filter { !it.isCompleted && it.dueDate!! < todayStr }
    val dueToday = plannedTasks.filter { it.dueDate == todayStr }
    val upcoming = plannedTasks.filter { it.dueDate!! > todayStr }.sortedBy { it.dueDate }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Planned",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        Text(
            text = "Overdue • Today • Upcoming",
            fontSize = 13.sp,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Add Planned Task Bar
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Add scheduled task...", color = TextMuted, fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = AmberStreak, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(selectedDate, color = AmberStreak, fontSize = 12.sp)
                }
                FilledIconButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            onAddPlannedTask(title.trim(), selectedDate)
                            title = ""
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = ObsidianBg)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (plannedTasks.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.CalendarMonth,
                title = "No Planned Deadlines",
                subtitle = "Schedule tasks with a due date to organize them into Overdue, Today, and Upcoming."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (overdue.isNotEmpty()) {
                    item {
                        Text("OVERDUE (${overdue.size})", color = RoseAlert, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    items(overdue, key = { "ov_${it.id}" }) { task ->
                        TaskRowCard(
                            task = task,
                            customLists = state.customLists,
                            onToggle = { onToggleTask(task) },
                            onToggleImportant = { onToggleImportant(task) },
                            onClick = { onClickTask(task) },
                            onDelete = { onDeleteTask(task) }
                        )
                    }
                }
                if (dueToday.isNotEmpty()) {
                    item {
                        Text("TODAY (${dueToday.size})", color = AmberStreak, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    items(dueToday, key = { "td_${it.id}" }) { task ->
                        TaskRowCard(
                            task = task,
                            customLists = state.customLists,
                            onToggle = { onToggleTask(task) },
                            onToggleImportant = { onToggleImportant(task) },
                            onClick = { onClickTask(task) },
                            onDelete = { onDeleteTask(task) }
                        )
                    }
                }
                if (upcoming.isNotEmpty()) {
                    item {
                        Text("UPCOMING (${upcoming.size})", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    items(upcoming, key = { "up_${it.id}" }) { task ->
                        TaskRowCard(
                            task = task,
                            customLists = state.customLists,
                            onToggle = { onToggleTask(task) },
                            onToggleImportant = { onToggleImportant(task) },
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
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                                .toString()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", color = EmeraldPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// =========================================================================
// TAB 5: CUSTOM LISTS WITH COLOR TAGS
// =========================================================================
@Composable
fun CustomListsScreen(
    state: PersistedState,
    onAddList: (String, Long) -> Unit,
    onDeleteList: (CustomTaskList) -> Unit,
    onAddTaskToList: (String, String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleImportant: (TaskItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit
) {
    val palette = listOf(0xFF10B981, 0xFF0D9488, 0xFFF59E0B, 0xFFF43F5E, 0xFF3B82F6, 0xFFA855F7)
    var newListName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(palette.first()) }
    var selectedListId by remember { mutableStateOf<String?>(state.customLists.firstOrNull()?.id) }
    var newTaskInList by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Custom Lists",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )

        // Create Custom List Card
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        placeholder = { Text("Create new list (e.g. Uni, Projects)...", color = TextMuted, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = {
                            if (newListName.isNotBlank()) {
                                onAddList(newListName.trim(), selectedColor)
                                newListName = ""
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create List", tint = ObsidianBg)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    palette.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (selectedColor == hex) 2.5.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.customLists.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.customLists, key = { it.id }) { list ->
                    val isSelected = selectedListId == list.id
                    Surface(
                        color = if (isSelected) Color(list.colorHex).copy(alpha = 0.2f) else ObsidianCard,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isSelected) Color(list.colorHex) else ObsidianBorder),
                        modifier = Modifier.clickable { selectedListId = list.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(list.colorHex))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(list.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete List",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        onDeleteList(list)
                                        if (selectedListId == list.id) {
                                            selectedListId = state.customLists.firstOrNull { it.id != list.id }?.id
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val activeList = state.customLists.find { it.id == selectedListId } ?: state.customLists.first()
            val listTasks = state.tasks.filter { it.listId == activeList.id }

            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTaskInList,
                        onValueChange = { newTaskInList = it },
                        placeholder = { Text("Add task to ${activeList.name}...", color = TextMuted, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = {
                            if (newTaskInList.isNotBlank()) {
                                onAddTaskToList(newTaskInList.trim(), activeList.id)
                                newTaskInList = ""
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(activeList.colorHex))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = ObsidianBg)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(listTasks, key = { it.id }) { task ->
                    TaskRowCard(
                        task = task,
                        customLists = state.customLists,
                        onToggle = { onToggleTask(task) },
                        onToggleImportant = { onToggleImportant(task) },
                        onClick = { onClickTask(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                }
            }
        } else {
            EmptyStateCard(
                icon = Icons.Default.FormatListBulleted,
                title = "No Custom Lists Yet",
                subtitle = "Create color-tagged lists above to group your tasks."
            )
        }
    }
}

// =========================================================================
// SHEETS & DIALOGS (Task Details, Suggestions, Birthdays, Security Vault)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailBottomSheet(
    task: TaskItem,
    customLists: List<CustomTaskList>,
    onDismiss: () -> Unit,
    onUpdateTask: (TaskItem) -> Unit,
    onDeleteTask: () -> Unit
) {
    val todayStr = LocalDate.now().toString()
    var stepInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = task.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDeleteTask) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Task", tint = RoseAlert)
                }
            }

            // Toggle My Day Chip
            val inMyDay = task.isMyDay && task.myDayDate == todayStr
            FilterChip(
                selected = inMyDay,
                onClick = {
                    onUpdateTask(
                        task.copy(
                            isMyDay = !inMyDay,
                            myDayDate = if (!inMyDay) todayStr else ""
                        )
                    )
                },
                label = { Text(if (inMyDay) "Added to My Day" else "Add to My Day") },
                leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            Spacer(Modifier.height(10.dp))
            Text("Subtasks (Steps)", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

            task.steps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = step.isDone,
                        onCheckedChange = {
                            val updatedSteps = task.steps.map {
                                if (it.id == step.id) it.copy(isDone = !it.isDone) else it
                            }
                            onUpdateTask(task.copy(steps = updatedSteps))
                        }
                    )
                    Text(
                        text = step.title,
                        color = if (step.isDone) TextMuted else TextPrimary,
                        textDecoration = if (step.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onUpdateTask(task.copy(steps = task.steps.filterNot { it.id == step.id }))
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove Step", tint = TextMuted, modifier = Modifier.size(16.dp))
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
                    Text("Add", color = ObsidianBg)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Auto-Saving Notes", color = AmberStreak, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = task.notes,
                onValueChange = { newNotes ->
                    onUpdateTask(task.copy(notes = newNotes))
                },
                placeholder = { Text("Type notes here (saves automatically)...", color = TextMuted) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsBottomSheet(
    suggestions: List<TaskItem>,
    onDismiss: () -> Unit,
    onAddToMyDay: (TaskItem) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text("Unfinished Task Suggestions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Pull unfinished tasks into today's My Day view", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            if (suggestions.isEmpty()) {
                Text("All caught up! Zero pending tasks outside My Day.", color = EmeraldPrimary, fontSize = 14.sp)
            } else {
                suggestions.forEach { task ->
                    Surface(
                        color = ObsidianNav,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(task.title, color = TextPrimary, modifier = Modifier.weight(1f))
                            FilledIconButton(
                                onClick = { onAddToMyDay(task) },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldPrimary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add to My Day", tint = ObsidianBg)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AddBirthdayDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dayStr by remember { mutableStateOf("14") }
    var monthStr by remember { mutableStateOf("8") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("Add Friend's Birthday", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friend's Name") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dayStr,
                        onValueChange = { dayStr = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Day (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = monthStr,
                        onValueChange = { monthStr = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Month (1-12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("WhatsApp Phone (Optional, e.g. 9198...)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = (dayStr.toIntOrNull() ?: 1).coerceIn(1, 31)
                    val m = (monthStr.toIntOrNull() ?: 1).coerceIn(1, 12)
                    if (name.isNotBlank()) onSave(name.trim(), d, m, phone.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Save & Schedule Midnight Alert", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
fun BirthdayWishGeneratorDialog(
    person: BirthdayPerson,
    isBelated: Boolean,
    onDismiss: () -> Unit,
    onCopyText: (String) -> Unit,
    onSendWhatsApp: (String) -> Unit
) {
    val defaultMessage = if (isBelated) {
        "Hey ${person.name}! 🚨 Happy Belated Birthday! Sorry I'm a day late, hope you had an incredible celebration and wishing you an amazing year ahead! 🎂✨"
    } else {
        "Happy Birthday ${person.name}! 🎂🎉 Wishing you an awesome year ahead filled with great health, big wins, and happiness! ✨"
    }
    var messageText by remember { mutableStateOf(defaultMessage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Text(
                text = if (isBelated) "🚨 Belated Rescue Generator" else "🎂 1-Tap Birthday Wish",
                color = if (isBelated) RoseAlert else EmeraldPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onCopyText(messageText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy Text", color = TextPrimary)
                    }
                    Button(
                        onClick = { onSendWhatsApp(messageText) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("WhatsApp", color = ObsidianBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
        }
    )
}

@Composable
fun ClearDataVaultDialog(
    currentPin: String,
    onDismiss: () -> Unit,
    onUpdatePin: (String) -> Unit,
    onClearAllData: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var confirmWord by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val unlocked = pinInput == currentPin && confirmWord == "CLEAR"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Text("🔒 Security Data Vault", color = RoseAlert, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "To wipe all tasks, habits, streaks, and birthdays back to a clean initial state, enter your 4-digit PIN (default 1234) and type CLEAR in uppercase.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it.filter { c -> c.isDigit() }.take(4)
                        errorMsg = null
                    },
                    label = { Text("4-Digit Security PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmWord,
                    onValueChange = {
                        confirmWord = it
                        errorMsg = null
                    },
                    label = { Text("Type CLEAR in uppercase") },
                    singleLine = true
                )

                Divider(color = ObsidianBorder)

                Text("Customize 4-Digit PIN (requires current PIN above):", color = TextMuted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { newPinInput = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("New 4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            if (pinInput == currentPin && newPinInput.length == 4) {
                                onUpdatePin(newPinInput)
                                newPinInput = ""
                            } else {
                                errorMsg = "Enter current PIN & a valid 4-digit new PIN."
                            }
                        }
                    ) {
                        Text("Set PIN", color = EmeraldPrimary)
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = RoseAlert, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (unlocked) onClearAllData()
                    else errorMsg = "Invalid PIN or confirmation word is not 'CLEAR'."
                },
                enabled = unlocked,
                colors = ButtonDefaults.buttonColors(containerColor = RoseAlert)
            ) {
                Text("Reset All Data", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
fun EodConfigDialog(
    currentHour: Int,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var hourStr by remember { mutableStateOf(currentHour.toString()) }
    var minStr by remember { mutableStateOf(currentMinute.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("Background EOD Alert Time", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Native AlarmManager fires at this time (24-hour format) if any habit, My Day task, or birthday wish is incomplete.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hourStr,
                        onValueChange = { hourStr = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Hour (0-23)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minStr,
                        onValueChange = { minStr = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Minute (0-59)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = (hourStr.toIntOrNull() ?: 21).coerceIn(0, 23)
                    val m = (minStr.toIntOrNull() ?: 0).coerceIn(0, 59)
                    onSave(h, m)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Save Alarm", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}