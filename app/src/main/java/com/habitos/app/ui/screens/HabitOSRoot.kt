package com.habitos.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.Locale

enum class HabitOSTab(val label: String, val section: MainSection) {
    MY_DAY("My Day", MainSection.MY_DAY),
    HABITS("Habits", MainSection.HABITS),
    BIRTHDAYS("Birthdays", MainSection.BIRTHDAYS),
    PLANNED("Planned", MainSection.PLANNED)
}

enum class MainSection(val isSubPage: Boolean) {
    MY_DAY(false),
    HABITS(false),
    BIRTHDAYS(false),
    PLANNED(false),
    IMPORTANT(true),
    ALL_ITEMS(true),
    CUSTOM_LIST(true)
}

@Composable
fun HabitOSLauncherLogo(size: Dp = 38.dp) {
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(ObsidianBg)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(size * 0.28f))
    ) {
        val w = this.size.width
        val c = Offset(w / 2f, w / 2f)
        drawCircle(color = ObsidianCard, radius = w * (34f / 108f), center = c)
        drawCircle(color = EmeraldPrimary, radius = w * (24f / 108f), center = c, style = Stroke(width = w * (4.5f / 108f)))
        drawCircle(color = EmeraldPrimary.copy(alpha = 0.08f), radius = w * (18f / 108f), center = c)
        drawCircle(color = TealSecondary, radius = w * (18f / 108f), center = c, style = Stroke(width = w * (1.5f / 108f)))
        val check = Path().apply {
            moveTo(w * (44f / 108f), w * (54.5f / 108f))
            lineTo(w * (51f / 108f), w * (61.5f / 108f))
            lineTo(w * (65f / 108f), w * (46.5f / 108f))
        }
        drawPath(path = check, color = EmeraldPrimary, style = Stroke(width = w * (4.5f / 108f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color = AmberStreak, radius = w * (3.5f / 108f), center = Offset(w * (71f / 108f), w * (37f / 108f)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitOSRootScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var appState by remember { mutableStateOf(HabitOSStorage.load(context)) }
    var activeSection by remember { mutableStateOf(MainSection.MY_DAY) }
    var lastMainTabSection by remember { mutableStateOf(MainSection.MY_DAY) }
    var activeCustomListId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var showSuggestionsSheet by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var showEodTimeDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = activeSection.isSubPage) {
        activeSection = lastMainTabSection
    }

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
            if (result == SnackbarResult.ActionPerformed) onUndo()
            else snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    fun showInfoSnackbar(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    fun handleToggleTask(task: TaskItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val today = LocalDate.now()
        val todayStr = today.toString()

        if (!task.isCompleted && task.recurrence != RecurrenceType.NONE) {
            val nextDate = RecurrenceEngine.calculateNextDueDate(task, today) ?: today.plusDays(1).toString()
            val advancedTask = task.copy(
                dueDate = nextDate,
                isMyDay = nextDate == todayStr,
                myDayDate = if (nextDate == todayStr) todayStr else "",
                steps = task.steps.map { it.copy(isDone = false) },
                isCompleted = false
            )
            persist(appState.copy(tasks = appState.tasks.map { if (it.id == task.id) advancedTask else it }))
            showInfoSnackbar("Completed! Next occurrence scheduled for ${DateFormatHelper.shortDate(nextDate, today)}")
        } else {
            persist(appState.copy(tasks = appState.tasks.map { if (it.id == task.id) it.copy(isCompleted = !it.isCompleted) else it }))
        }
    }

    fun handlePostponeTask(task: TaskItem, newDueDate: String) {
        val todayStr = LocalDate.now().toString()
        val updated = task.copy(
            dueDate = newDueDate,
            isMyDay = if (newDueDate == todayStr) true else task.isMyDay && task.myDayDate == todayStr,
            myDayDate = if (newDueDate == todayStr) todayStr else if (task.myDayDate == todayStr && newDueDate > todayStr) "" else task.myDayDate
        )
        persist(appState.copy(tasks = appState.tasks.map { if (it.id == task.id) updated else it }))
        showInfoSnackbar("Scheduled \"${task.title}\" for ${DateFormatHelper.shortDate(newDueDate)}")
    }

    fun deleteTask(task: TaskItem) {
        val snapshot = appState.tasks
        persist(appState.copy(tasks = snapshot.filterNot { it.id == task.id }))
        if (selectedTaskId == task.id) selectedTaskId = null
        trigger5SecUndoSnackbar("Deleted \"${task.title}\"") {
            persist(appState.copy(tasks = snapshot))
        }
    }

    fun deleteHabit(habit: HabitItem) {
        val snapshot = appState.habits
        persist(appState.copy(habits = snapshot.filterNot { it.id == habit.id }))
        trigger5SecUndoSnackbar("Deleted habit \"${habit.name}\"") {
            persist(appState.copy(habits = snapshot))
        }
    }

    fun deleteBirthday(person: BirthdayPerson) {
        val snapshot = appState.birthdays
        persist(appState.copy(birthdays = snapshot.filterNot { it.id == person.id }))
        trigger5SecUndoSnackbar("Removed ${person.name}'s birthday") {
            persist(appState.copy(birthdays = snapshot))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = ObsidianNav,
                drawerContentColor = TextPrimary,
                modifier = Modifier.fillMaxHeight().width(312.dp)
            ) {
                HabitOSSidebarDrawerContent(
                    state = appState,
                    activeSection = activeSection,
                    activeCustomListId = activeCustomListId,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onExecuteSearch = {
                        activeSection = MainSection.ALL_ITEMS
                        scope.launch { drawerState.close() }
                    },
                    onSelectSection = { sec, listId ->
                        if (!sec.isSubPage) lastMainTabSection = sec
                        activeSection = sec
                        activeCustomListId = listId
                        scope.launch { drawerState.close() }
                    },
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onOpenEodConfig = { showEodTimeDialog = true },
                    onAddCustomList = { name, colorHex ->
                        persist(appState.copy(customLists = appState.customLists + CustomTaskList(name = name, colorHex = colorHex)))
                    },
                    onDeleteCustomList = { list ->
                        persist(
                            appState.copy(
                                customLists = appState.customLists.filterNot { it.id == list.id },
                                tasks = appState.tasks.map { if (it.listId == list.id) it.copy(listId = null) else it }
                            )
                        )
                        if (activeSection == MainSection.CUSTOM_LIST && activeCustomListId == list.id) {
                            activeSection = lastMainTabSection
                        }
                    },
                    onOpenProfileEdit = { showEditProfileDialog = true }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(snackbarData = data, containerColor = ObsidianCard, contentColor = TextPrimary, actionColor = EmeraldPrimary, shape = RoundedCornerShape(12.dp))
                }
            },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg, titleContentColor = TextPrimary),
                    navigationIcon = {
                        if (activeSection.isSubPage) {
                            IconButton(onClick = { activeSection = lastMainTabSection }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EmeraldPrimary)
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                            }
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HabitOSLauncherLogo(size = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("habit", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = TextPrimary)
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                color = EmeraldPrimary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.7f))
                            ) {
                                Text("OS", color = EmeraldPrimary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = { showEodTimeDialog = true }) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "EOD", tint = AmberStreak, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(String.format(Locale.getDefault(), "%02d:%02d", appState.settings.eodHour, appState.settings.eodMinute), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { showVaultDialog = true }) {
                            Icon(Icons.Default.Shield, contentDescription = "Vault", tint = RoseAlert)
                        }
                    }
                )
            },
            bottomBar = {
                if (!activeSection.isSubPage) {
                    NavigationBar(containerColor = ObsidianNav, tonalElevation = 0.dp, modifier = Modifier.border(BorderStroke(1.dp, ObsidianBorder))) {
                        HabitOSTab.entries.forEach { tab ->
                            val sel = activeSection == tab.section
                            NavigationBarItem(
                                selected = sel,
                                onClick = {
                                    lastMainTabSection = tab.section
                                    activeSection = tab.section
                                },
                                icon = {
                                    val icon = when (tab) {
                                        HabitOSTab.MY_DAY -> Icons.Default.WbSunny
                                        HabitOSTab.HABITS -> Icons.Default.GridOn
                                        HabitOSTab.BIRTHDAYS -> Icons.Default.Cake
                                        HabitOSTab.PLANNED -> Icons.Default.CalendarMonth
                                    }
                                    Icon(icon, contentDescription = tab.label)
                                },
                                label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
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
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(ObsidianBg)) {
                when (activeSection) {
                    MainSection.MY_DAY -> MyDayScreen(
                        state = appState,
                        searchQuery = searchQuery,
                        onOpenSuggestions = { showSuggestionsSheet = true },
                        onNavigateToHabits = {
                            lastMainTabSection = MainSection.HABITS
                            activeSection = MainSection.HABITS
                        },
                        onAddTask = { isHabit, rawTitle, priority, listId, dueDate, cue, dueTime, durationMinutes, deadlineDate, recurrence ->
                            if (isHabit) {
                                persist(appState.copy(habits = appState.habits + HabitItem(name = rawTitle, listId = listId, cue = cue)))
                            } else {
                                val today = LocalDate.now()
                                val todayStr = today.toString()
                                val parsed = SmartTaskParser.parse(rawTitle, today)
                                val finalPrio = if (priority != Priority.P4) priority else (parsed.priority ?: Priority.P4)
                                val finalDueDate = parsed.dueDate ?: dueDate
                                val finalTime = dueTime ?: parsed.dueTime
                                val finalDuration = durationMinutes ?: parsed.durationMinutes
                                val finalRecurrence = if (recurrence != RecurrenceType.NONE) recurrence else (parsed.recurrence ?: RecurrenceType.NONE)

                                val newTask = TaskItem(
                                    title = parsed.cleanTitle,
                                    priority = finalPrio,
                                    isImportant = finalPrio == Priority.P1,
                                    isMyDay = true,
                                    myDayDate = todayStr,
                                    dueDate = finalDueDate,
                                    dueTime = finalTime,
                                    durationMinutes = finalDuration,
                                    deadlineDate = deadlineDate,
                                    recurrence = finalRecurrence,
                                    reminderOffsetMinutes = if (finalTime != null) 0 else null,
                                    listId = listId,
                                    cue = cue,
                                    labels = parsed.labels
                                )
                                persist(appState.copy(tasks = listOf(newTask) + appState.tasks))
                            }
                        },
                        onPostponeTask = ::handlePostponeTask,
                        onToggleTask = ::handleToggleTask,
                        onToggleHabitToday = { habit ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            persist(appState.copy(habits = appState.habits.map { if (it.id == habit.id) it.updatedWithToggle(LocalDate.now().toString()) else it }))
                        },
                        onClickTask = { selectedTaskId = it.id },
                        onDeleteTask = ::deleteTask,
                        onDeleteHabit = ::deleteHabit
                    )

                    MainSection.HABITS -> HabitMatrixScreen(
                        state = appState,
                        onAddHabit = { name -> persist(appState.copy(habits = appState.habits + HabitItem(name = name))) },
                        onToggleHabitDate = { habit, dateStr ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            persist(appState.copy(habits = appState.habits.map { if (it.id == habit.id) it.updatedWithToggle(dateStr) else it }))
                        },
                        onDeleteHabit = ::deleteHabit
                    )

                    MainSection.BIRTHDAYS -> BirthdaysScreen(
                        state = appState,
                        onAddBirthday = { name, relation, day, month, year, phone, customWish ->
                            val bday = BirthdayPerson(name = name, relation = relation, dayOfMonth = day, monthValue = month, birthYear = year, phone = phone, customWish = customWish.ifBlank { null })
                            persist(appState.copy(birthdays = appState.birthdays + bday))
                        },
                        onUpdateCustomWish = { person, newCustom ->
                            persist(appState.copy(birthdays = appState.birthdays.map { if (it.id == person.id) it.copy(customWish = newCustom.ifBlank { null }) else it }))
                        },
                        onMarkWished = { person ->
                            persist(appState.copy(birthdays = appState.birthdays.map { if (it.id == person.id) it.copy(wishedYear = LocalDate.now().year) else it }))
                        },
                        onDeleteBirthday = ::deleteBirthday
                    )

                    MainSection.PLANNED -> PlannedScreen(
                        state = appState,
                        onAddPlannedTask = { rawTitle, priority, dueDateStr, dueTime, durationMinutes, deadlineDate, recurrence ->
                            val today = LocalDate.now()
                            val todayStr = today.toString()
                            val parsed = SmartTaskParser.parse(rawTitle, today)
                            val finalDueDate = parsed.dueDate ?: dueDateStr
                            val finalPrio = if (priority != Priority.P4) priority else (parsed.priority ?: Priority.P4)
                            val finalTime = dueTime ?: parsed.dueTime
                            val finalDuration = durationMinutes ?: parsed.durationMinutes
                            val finalRecurrence = if (recurrence != RecurrenceType.NONE) recurrence else (parsed.recurrence ?: RecurrenceType.NONE)
                            val isToday = finalDueDate == todayStr

                            val newTask = TaskItem(
                                title = parsed.cleanTitle,
                                priority = finalPrio,
                                isImportant = finalPrio == Priority.P1,
                                dueDate = finalDueDate,
                                dueTime = finalTime,
                                durationMinutes = finalDuration,
                                deadlineDate = deadlineDate,
                                recurrence = finalRecurrence,
                                reminderOffsetMinutes = if (finalTime != null) 0 else null,
                                labels = parsed.labels,
                                isMyDay = isToday,
                                myDayDate = if (isToday) todayStr else ""
                            )
                            persist(appState.copy(tasks = listOf(newTask) + appState.tasks))
                        },
                        onPostponeTask = ::handlePostponeTask,
                        onToggleTask = ::handleToggleTask,
                        onClickTask = { selectedTaskId = it.id },
                        onDeleteTask = ::deleteTask
                    )

                    MainSection.IMPORTANT,
                    MainSection.ALL_ITEMS,
                    MainSection.CUSTOM_LIST -> FilteredListSectionScreen(
                        state = appState,
                        section = activeSection,
                        customListId = activeCustomListId,
                        searchQuery = searchQuery,
                        onClearSearch = { searchQuery = "" },
                        onAddTask = { rawTitle, listId, prio ->
                            val today = LocalDate.now()
                            val parsed = SmartTaskParser.parse(rawTitle, today)
                            val finalPrio = if (prio != Priority.P4) prio else (parsed.priority ?: Priority.P4)
                            val newTask = TaskItem(
                                title = parsed.cleanTitle,
                                listId = listId,
                                priority = finalPrio,
                                isImportant = finalPrio == Priority.P1 || activeSection == MainSection.IMPORTANT,
                                dueDate = parsed.dueDate,
                                dueTime = parsed.dueTime,
                                durationMinutes = parsed.durationMinutes,
                                recurrence = parsed.recurrence ?: RecurrenceType.NONE,
                                labels = parsed.labels
                            )
                            persist(appState.copy(tasks = listOf(newTask) + appState.tasks))
                        },
                        onToggleTask = ::handleToggleTask,
                        onToggleHabitToday = { habit ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            persist(appState.copy(habits = appState.habits.map { if (it.id == habit.id) it.updatedWithToggle(LocalDate.now().toString()) else it }))
                        },
                        onClickTask = { selectedTaskId = it.id },
                        onDeleteTask = ::deleteTask,
                        onDeleteHabit = ::deleteHabit
                    )
                }
            }
        }
    }

    val activeTask = appState.tasks.find { it.id == selectedTaskId }
    if (activeTask != null) {
        TaskDetailBottomSheet(
            task = activeTask,
            customLists = appState.customLists,
            onDismiss = { selectedTaskId = null },
            onUpdateTask = { updated -> persist(appState.copy(tasks = appState.tasks.map { if (it.id == updated.id) updated else it })) },
            onDeleteTask = { deleteTask(activeTask); selectedTaskId = null }
        )
    }

    if (showSuggestionsSheet) {
        val todayStr = LocalDate.now().toString()
        val suggestions = appState.tasks.filter {
            !it.isCompleted && !(it.isMyDay && it.myDayDate == todayStr) && it.dueDate != todayStr
        }.sortedWith(compareBy<TaskItem> { it.dueDate ?: "9999-99-99" }.thenBy { it.priority.ordinal })

        SuggestionsBottomSheet(
            suggestions = suggestions,
            customLists = appState.customLists,
            onDismiss = { showSuggestionsSheet = false },
            onAddToMyDay = { task ->
                persist(appState.copy(tasks = appState.tasks.map { if (it.id == task.id) it.copy(isMyDay = true, myDayDate = todayStr) else it }))
                Toast.makeText(context, "Added \"${task.title}\" to My Day", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showVaultDialog) {
        ClearDataVaultDialog(
            currentPin = appState.settings.vaultPin,
            onDismiss = { showVaultDialog = false },
            onUpdatePin = { newPin ->
                persist(appState.copy(settings = appState.settings.copy(vaultPin = newPin)))
                Toast.makeText(context, "Vault PIN updated", Toast.LENGTH_SHORT).show()
            },
            onClearAllData = {
                appState = HabitOSStorage.clearAllResettingKeepPinAndProfile(context, appState.settings.vaultPin, appState.userProfile)
                showVaultDialog = false
                Toast.makeText(context, "All data reset cleanly.", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showEodTimeDialog) {
        EodConfigDialog(
            currentHour = appState.settings.eodHour,
            currentMinute = appState.settings.eodMinute,
            onDismiss = { showEodTimeDialog = false },
            onSave = { h, m ->
                persist(appState.copy(settings = appState.settings.copy(eodHour = h, eodMinute = m)))
                showEodTimeDialog = false
            }
        )
    }

    if (showEditProfileDialog) {
        ProfileEditDialog(
            currentProfile = appState.userProfile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { updatedProfile ->
                persist(appState.copy(userProfile = updatedProfile))
                showEditProfileDialog = false
                Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsBottomSheet(
    suggestions: List<TaskItem>,
    customLists: List<CustomTaskList>,
    onDismiss: () -> Unit,
    onAddToMyDay: (TaskItem) -> Unit
) {
    val todayStr = LocalDate.now().toString()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        contentColor = TextPrimary
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("☀️ My Day Suggestions", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("Pull overdue or backlog tasks into today's focus", fontSize = 12.sp, color = TextMuted)
                }
                Surface(color = EmeraldPrimary.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                    Text("${suggestions.size} available", color = EmeraldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            if (suggestions.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.CheckCircle,
                    title = "All Caught Up!",
                    subtitle = "Every active task is already scheduled in your My Day."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(suggestions, key = { "sug_${it.id}" }) { task ->
                        val isOverdue = !task.dueDate.isNullOrBlank() && task.dueDate < todayStr
                        val listTag = customLists.find { it.id == task.listId }
                        val prioColor = Color(task.priority.colorHex)

                        Surface(
                            color = ObsidianNav,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isOverdue) RoseAlert.copy(alpha = 0.6f) else ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(task.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = prioColor.copy(alpha = 0.16f), shape = RoundedCornerShape(4.dp)) {
                                            Text(task.priority.shortLabel, color = prioColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                        if (listTag != null) {
                                            Text("● ${listTag.name}", color = Color(listTag.colorHex), fontSize = 10.sp, maxLines = 1)
                                        }
                                        if (!task.dueDate.isNullOrBlank()) {
                                            Text(
                                                text = if (isOverdue) "🚨 Overdue (${DateFormatHelper.shortDate(task.dueDate)})" else "📅 ${DateFormatHelper.shortDate(task.dueDate)}",
                                                color = if (isOverdue) RoseAlert else AmberStreak,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Button(
                                    onClick = { onAddToMyDay(task) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+ My Day", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProfileEditDialog(
    currentProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(currentProfile.name) }
    var dob by remember { mutableStateOf(currentProfile.dob) }
    var gender by remember { mutableStateOf(currentProfile.gender) }
    var seed by remember { mutableIntStateOf(currentProfile.avatarSeed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Edit User Profile", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { seed = (seed + 1) % 6 }.padding(4.dp)
                ) {
                    RandomUserAvatarBadge(seed = seed, name = name)
                    Spacer(Modifier.width(6.dp))
                    Text("↻ Color", color = EmeraldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Date of Birth (e.g. 14 Aug 2008)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gender,
                    onValueChange = { gender = it },
                    label = { Text("Status / Role (e.g. Developer)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        currentProfile.copy(
                            isOnboarded = true,
                            name = name.trim(),
                            dob = dob.trim(),
                            gender = gender.trim(),
                            avatarSeed = seed
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Save Profile", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
fun HabitOSSidebarDrawerContent(
    state: PersistedState,
    activeSection: MainSection,
    activeCustomListId: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onSelectSection: (MainSection, String?) -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenEodConfig: () -> Unit,
    onAddCustomList: (String, Long) -> Unit,
    onDeleteCustomList: (CustomTaskList) -> Unit,
    onOpenProfileEdit: () -> Unit
) {
    val todayStr = LocalDate.now().toString()
    val palette = listOf(0xFF3B82F6, 0xFF10B981, 0xFFF43F5E, 0xFFA855F7, 0xFFF59E0B)
    var newListName by remember { mutableStateOf("") }
    var colorIdx by remember { mutableIntStateOf(0) }

    val myDayCount = state.tasks.count { !it.isCompleted && ((it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr) } +
        state.habits.count { !it.completedDates.contains(todayStr) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitOSLauncherLogo(size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("habit", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        Spacer(Modifier.width(4.dp))
                        Surface(color = EmeraldPrimary.copy(alpha = 0.2f), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f))) {
                            Text("OS", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                    Text("● Saved on Device", color = TextMuted, fontSize = 10.sp)
                }
            }
            IconButton(onClick = onCloseDrawer, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
            }
        }

        Spacer(Modifier.height(8.dp))

        Surface(color = ObsidianCard, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 12.sp),
                    cursorBrush = SolidColor(EmeraldPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) Text("Search tasks, @labels, notes...", color = TextMuted, fontSize = 12.sp)
                        inner()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotBlank()) {
                    Text("Go →", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable { onExecuteSearch() })
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            item { SidebarNavRow(Icons.Default.WbSunny, AmberStreak, "My Day", myDayCount, activeSection == MainSection.MY_DAY) { onSelectSection(MainSection.MY_DAY, null) } }
            item { SidebarNavRow(Icons.Default.LocalFireDepartment, EmeraldPrimary, "7-Day Habit Matrix", state.habits.size, activeSection == MainSection.HABITS) { onSelectSection(MainSection.HABITS, null) } }
            item { SidebarNavRow(Icons.Default.Cake, RoseAlert, "Birthdays", state.birthdays.size, activeSection == MainSection.BIRTHDAYS) { onSelectSection(MainSection.BIRTHDAYS, null) } }
            item { SidebarNavRow(Icons.Default.Star, AmberStreak, "Important", state.tasks.count { !it.isCompleted && (it.isImportant || it.priority == Priority.P1) }, activeSection == MainSection.IMPORTANT) { onSelectSection(MainSection.IMPORTANT, null) } }
            item { SidebarNavRow(Icons.Default.CalendarMonth, Color(0xFFA855F7), "Planned", state.tasks.count { !it.isCompleted && !it.dueDate.isNullOrBlank() }, activeSection == MainSection.PLANNED) { onSelectSection(MainSection.PLANNED, null) } }
            item { SidebarNavRow(Icons.Default.CheckBox, Color(0xFF3B82F6), "All Tasks & Habits", state.tasks.size + state.habits.size, activeSection == MainSection.ALL_ITEMS) { onSelectSection(MainSection.ALL_ITEMS, null) } }

            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = ObsidianBorder)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("MY LISTS & CATEGORIES", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${state.customLists.size}", color = EmeraldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            items(state.customLists, key = { it.id }) { list ->
                val count = state.tasks.count { it.listId == list.id && !it.isCompleted } + state.habits.count { it.listId == list.id }
                val isSel = activeSection == MainSection.CUSTOM_LIST && activeCustomListId == list.id
                Surface(
                    color = if (isSel) EmeraldPrimary.copy(alpha = 0.18f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectSection(MainSection.CUSTOM_LIST, list.id) }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(list.colorHex)))
                        Spacer(Modifier.width(10.dp))
                        Text(list.name, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("$count", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = TextMuted.copy(alpha = 0.7f), modifier = Modifier.size(14.dp).clickable { onDeleteCustomList(list) })
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Surface(color = EmeraldPrimary.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(String.format(Locale.getDefault(), "EOD Alert • Daily %02d:%02d", state.settings.eodHour, state.settings.eodMinute), color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Surface(color = EmeraldPrimary, shape = RoundedCornerShape(6.dp), modifier = Modifier.clickable { onOpenEodConfig() }) {
                    Text("Config", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(color = ObsidianCard, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("+ ", color = TextMuted, fontSize = 12.sp)
                    BasicTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        singleLine = true,
                        textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(EmeraldPrimary),
                        decorationBox = { inner ->
                            if (newListName.isEmpty()) Text("New category...", color = TextMuted, fontSize = 12.sp)
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(palette[colorIdx % palette.size])).clickable { colorIdx = (colorIdx + 1) % palette.size })
            Button(
                onClick = {
                    if (newListName.isNotBlank()) {
                        onAddCustomList(newListName.trim(), palette[colorIdx % palette.size])
                        newListName = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Add", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = ObsidianBorder)
        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenProfileEdit() }.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                RandomUserAvatarBadge(seed = state.userProfile.avatarSeed, name = state.userProfile.name)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(state.userProfile.name.ifBlank { "habitOS User" }, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${state.userProfile.dob.ifBlank { "Birthday not set" }} • ${state.userProfile.gender.ifBlank { "Offline" }}", color = TextMuted, fontSize = 10.sp, maxLines = 1)
                }
            }
            IconButton(onClick = onOpenProfileEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Edit Profile", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SidebarNavRow(icon: ImageVector, iconTint: Color, label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) EmeraldPrimary else Color.Transparent, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = if (selected) ObsidianBg else iconTint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = if (selected) ObsidianBg else TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Surface(color = if (selected) ObsidianBg.copy(alpha = 0.2f) else ObsidianCard, shape = CircleShape) {
                Text("$count", color = if (selected) ObsidianBg else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
    }
}

@Composable
fun RandomUserAvatarBadge(seed: Int, name: String) {
    val grads = listOf(listOf(Color(0xFF10B981), Color(0xFF059669)), listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)), listOf(Color(0xFFF59E0B), Color(0xFFD97706)), listOf(Color(0xFFA855F7), Color(0xFF7E22CE)))
    val pair = grads[seed.mod(grads.size)]
    Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(pair)).border(1.5.dp, ObsidianBorder, CircleShape), contentAlignment = Alignment.Center) {
        val initials = name.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("").ifEmpty { "U" }
        Text(initials, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

@Composable
fun ClearDataVaultDialog(currentPin: String, onDismiss: () -> Unit, onUpdatePin: (String) -> Unit, onClearAllData: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    val unlocked = pin == currentPin && confirm == "CLEAR"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("🔒 Security Data Vault", color = RoseAlert, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type current PIN and CLEAR to wipe all local data cleanly.", color = TextMuted, fontSize = 11.sp)
                OutlinedTextField(value = pin, onValueChange = { pin = it.take(4) }, label = { Text("Current 4-Digit PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Type CLEAR in uppercase") }, singleLine = true)
                HorizontalDivider(color = ObsidianBorder)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = newPin, onValueChange = { newPin = it.take(4) }, label = { Text("New PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { if (pin == currentPin && newPin.length == 4) { onUpdatePin(newPin); newPin = "" } }) { Text("Set", color = EmeraldPrimary) }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClearAllData, enabled = unlocked, colors = ButtonDefaults.buttonColors(containerColor = RoseAlert)) {
                Text("Reset All Data", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
fun EodConfigDialog(currentHour: Int, currentMinute: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var hStr by remember { mutableStateOf(currentHour.toString()) }
    var mStr by remember { mutableStateOf(currentMinute.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("Background EOD Alert Time", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = hStr, onValueChange = { hStr = it.take(2) }, label = { Text("Hour (0-23)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = mStr, onValueChange = { mStr = it.take(2) }, label = { Text("Minute (0-59)") }, modifier = Modifier.weight(1f))
            }
        },
        confirmButton = {
            Button(onClick = { onSave((hStr.toIntOrNull() ?: 21).coerceIn(0, 23), (mStr.toIntOrNull() ?: 0).coerceIn(0, 59)) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)) {
                Text("Save", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
fun EmptyStateCard(icon: ImageVector, title: String, subtitle: String) {
    Surface(color = ObsidianCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}