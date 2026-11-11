package com.habitos.app.ui

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.habitos.app.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.random.Random

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

        drawCircle(
            color = ObsidianCard,
            radius = w * (34f / 108f),
            center = c
        )
        drawCircle(
            color = EmeraldPrimary,
            radius = w * (24f / 108f),
            center = c,
            style = Stroke(width = w * (4.5f / 108f))
        )
        drawCircle(
            color = EmeraldPrimary.copy(alpha = 0.08f),
            radius = w * (18f / 108f),
            center = c
        )
        drawCircle(
            color = TealSecondary,
            radius = w * (18f / 108f),
            center = c,
            style = Stroke(width = w * (1.5f / 108f))
        )
        val checkPath = Path().apply {
            moveTo(w * (44f / 108f), w * (54.5f / 108f))
            lineTo(w * (51f / 108f), w * (61.5f / 108f))
            lineTo(w * (65f / 108f), w * (46.5f / 108f))
        }
        drawPath(
            path = checkPath,
            color = EmeraldPrimary,
            style = Stroke(
                width = w * (4.5f / 108f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawCircle(
            color = AmberStreak,
            radius = w * (3.5f / 108f),
            center = Offset(w * (71f / 108f), w * (37f / 108f))
        )
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
            if (result == SnackbarResult.ActionPerformed) {
                onUndo()
            } else {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
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
            persist(
                appState.copy(
                    tasks = appState.tasks.map {
                        if (it.id == task.id) it.copy(isCompleted = !it.isCompleted) else it
                    }
                )
            )
        }
    }

    fun handleToggleImportant(task: TaskItem) {
        val nextImp = !task.isImportant
        val nextPrio = when {
            nextImp -> Priority.P1
            task.priority == Priority.P1 -> Priority.P4
            else -> task.priority
        }
        persist(
            appState.copy(
                tasks = appState.tasks.map {
                    if (it.id == task.id) it.copy(isImportant = nextImp, priority = nextPrio) else it
                }
            )
        )
    }

    fun handlePostponeTask(task: TaskItem, newDueDate: String) {
        val todayStr = LocalDate.now().toString()
        val updated = task.copy(
            dueDate = newDueDate,
            isMyDay = if (newDueDate == todayStr) true else (task.isMyDay && task.myDayDate == todayStr && newDueDate <= todayStr),
            myDayDate = if (newDueDate == todayStr) todayStr else if (task.myDayDate == todayStr && newDueDate > todayStr) "" else task.myDayDate
        )
        persist(appState.copy(tasks = appState.tasks.map { if (it.id == task.id) updated else it }))
        showInfoSnackbar("Rescheduled \"${task.title}\" to ${DateFormatHelper.shortDate(newDueDate)}")
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = ObsidianNav,
                drawerContentColor = TextPrimary,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(312.dp)
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
                        val created = CustomTaskList(name = name, colorHex = colorHex)
                        persist(appState.copy(customLists = appState.customLists + created))
                    },
                    onDeleteCustomList = { list ->
                        persist(
                            appState.copy(
                                customLists = appState.customLists.filterNot { it.id == list.id },
                                tasks = appState.tasks.map {
                                    if (it.listId == list.id) it.copy(listId = null) else it
                                }
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
                    navigationIcon = {
                        if (activeSection.isSubPage) {
                            IconButton(onClick = { activeSection = lastMainTabSection }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Home",
                                    tint = EmeraldPrimary
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Sidebar", tint = TextPrimary)
                            }
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HabitOSLauncherLogo(size = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "habit",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = TextPrimary
                            )
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                color = EmeraldPrimary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.7f))
                            ) {
                                Text(
                                    text = "OS",
                                    color = EmeraldPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    },
                    actions = {
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
                if (!activeSection.isSubPage) {
                    NavigationBar(
                        containerColor = ObsidianNav,
                        tonalElevation = 0.dp,
                        modifier = Modifier.border(BorderStroke(1.dp, ObsidianBorder))
                    ) {
                        HabitOSTab.entries.forEach { tab ->
                            val selected = activeSection == tab.section
                            NavigationBarItem(
                                selected = selected,
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
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(ObsidianBg)
            ) {
                when (activeSection) {
                    MainSection.MY_DAY -> MyDayWebDashboardScreen(
                        state = appState,
                        searchQuery = searchQuery,
                        onOpenSuggestions = { showSuggestionsSheet = true },
                        onNavigateToHabits = {
                            lastMainTabSection = MainSection.HABITS
                            activeSection = MainSection.HABITS
                        },
                        onNavigateToPlanned = {
                            lastMainTabSection = MainSection.PLANNED
                            activeSection = MainSection.PLANNED
                        },
                        onAddUnifiedItem = { isHabit, rawTitle, listId, dueDate, cue, isMyDay, priority, dueTime, durationMin, deadlineDate, recurrence ->
                            if (isHabit) {
                                val newHabit = HabitItem(
                                    name = rawTitle,
                                    listId = listId,
                                    cue = cue
                                )
                                persist(appState.copy(habits = appState.habits + newHabit))
                            } else {
                                val today = LocalDate.now()
                                val todayStr = today.toString()
                                val parsed = SmartTaskParser.parse(rawTitle, today)
                                val finalPrio = if (priority != Priority.P4) priority else (parsed.priority ?: Priority.P4)
                                val finalDue = parsed.dueDate ?: dueDate
                                val finalTime = dueTime ?: parsed.dueTime
                                val finalDur = durationMin ?: parsed.durationMinutes
                                val finalRec = if (recurrence != RecurrenceType.NONE) recurrence else (parsed.recurrence ?: RecurrenceType.NONE)

                                val newTask = TaskItem(
                                    title = parsed.cleanTitle,
                                    priority = finalPrio,
                                    isImportant = finalPrio == Priority.P1,
                                    isMyDay = isMyDay,
                                    myDayDate = if (isMyDay) todayStr else "",
                                    dueDate = finalDue,
                                    dueTime = finalTime,
                                    durationMinutes = finalDur,
                                    deadlineDate = deadlineDate,
                                    recurrence = finalRec,
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
                        onToggleImportant = ::handleToggleImportant,
                        onToggleHabitToday = { habit ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val todayStr = LocalDate.now().toString()
                            persist(
                                appState.copy(
                                    habits = appState.habits.map {
                                        if (it.id == habit.id) it.updatedWithToggle(todayStr) else it
                                    }
                                )
                            )
                        },
                        onClickTask = { selectedTaskId = it.id },
                        onDeleteTask = ::deleteTaskWithUndo,
                        onDeleteHabit = ::deleteHabitWithUndo
                    )

                    MainSection.HABITS -> HabitGridScreen(
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

                    MainSection.BIRTHDAYS -> BirthdaysScreen(
                        state = appState,
                        onAddBirthday = { name, relation, day, month, year, phone, customWish ->
                            val person = BirthdayPerson(
                                name = name,
                                relation = relation,
                                dayOfMonth = day,
                                monthValue = month,
                                birthYear = year,
                                phone = phone,
                                customWish = customWish.ifBlank { null }
                            )
                            persist(appState.copy(birthdays = appState.birthdays + person))
                        },
                        onUpdateCustomWish = { person, newCustomWish ->
                            persist(
                                appState.copy(
                                    birthdays = appState.birthdays.map {
                                        if (it.id == person.id) it.copy(customWish = newCustomWish.ifBlank { null }) else it
                                    }
                                )
                            )
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

                    MainSection.PLANNED -> PlannedScreen(
                        state = appState,
                        onAddPlannedTask = { rawTitle, dueDateStr, priority, dueTime, durationMin, deadlineDate, recurrence ->
                            val today = LocalDate.now()
                            val todayStr = today.toString()
                            val parsed = SmartTaskParser.parse(rawTitle, today)
                            val finalDue = parsed.dueDate ?: dueDateStr
                            val finalPrio = if (priority != Priority.P4) priority else (parsed.priority ?: Priority.P4)
                            val finalTime = dueTime ?: parsed.dueTime
                            val finalDur = durationMin ?: parsed.durationMinutes
                            val finalRec = if (recurrence != RecurrenceType.NONE) recurrence else (parsed.recurrence ?: RecurrenceType.NONE)
                            val isToday = finalDue == todayStr

                            val newTask = TaskItem(
                                title = parsed.cleanTitle,
                                priority = finalPrio,
                                isImportant = finalPrio == Priority.P1,
                                dueDate = finalDue,
                                dueTime = finalTime,
                                durationMinutes = finalDur,
                                deadlineDate = deadlineDate,
                                recurrence = finalRec,
                                reminderOffsetMinutes = if (finalTime != null) 0 else null,
                                labels = parsed.labels,
                                isMyDay = isToday,
                                myDayDate = if (isToday) finalDue else ""
                            )
                            persist(appState.copy(tasks = listOf(newTask) + appState.tasks))
                        },
                        onPostponeTask = ::handlePostponeTask,
                        onToggleTask = ::handleToggleTask,
                        onToggleImportant = ::handleToggleImportant,
                        onClickTask = { selectedTaskId = it.id },
                        onDeleteTask = ::deleteTaskWithUndo
                    )

                    MainSection.IMPORTANT,
                    MainSection.ALL_ITEMS,
                    MainSection.CUSTOM_LIST -> FilteredListSectionScreen(
                        state = appState,
                        section = activeSection,
                        customListId = activeCustomListId,
                        searchQuery = searchQuery,
                        onClearSearch = { searchQuery = "" },
                        onAddTask = { rawTitle, listId, important ->
                            val today = LocalDate.now()
                            val parsed = SmartTaskParser.parse(rawTitle, today)
                            val finalPrio = if (important) Priority.P1 else (parsed.priority ?: Priority.P4)
                            val newTask = TaskItem(
                                title = parsed.cleanTitle,
                                listId = listId,
                                priority = finalPrio,
                                isImportant = important || finalPrio == Priority.P1,
                                dueDate = parsed.dueDate,
                                dueTime = parsed.dueTime,
                                durationMinutes = parsed.durationMinutes,
                                recurrence = parsed.recurrence ?: RecurrenceType.NONE,
                                labels = parsed.labels
                            )
                            persist(appState.copy(tasks = listOf(newTask) + appState.tasks))
                        },
                        onToggleTask = ::handleToggleTask,
                        onToggleImportant = ::handleToggleImportant,
                        onToggleHabitToday = { habit ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val todayStr = LocalDate.now().toString()
                            persist(
                                appState.copy(
                                    habits = appState.habits.map {
                                        if (it.id == habit.id) it.updatedWithToggle(todayStr) else it
                                    }
                                )
                            )
                        },
                        onClickTask = { selectedTaskId = it.id },
                        onDeleteTask = ::deleteTaskWithUndo,
                        onDeleteHabit = ::deleteHabitWithUndo
                    )
                }
            }
        }
    }

    if (!appState.userProfile.isOnboarded || showEditProfileDialog) {
        FirstLaunchUserOnboardingDialog(
            initialProfile = appState.userProfile,
            isEditing = showEditProfileDialog,
            onDismiss = { showEditProfileDialog = false },
            onSaveProfile = { name, dob, gender, seed ->
                val updatedProfile = appState.userProfile.copy(
                    isOnboarded = true,
                    name = name,
                    dob = dob,
                    gender = gender,
                    avatarSeed = seed
                )
                persist(appState.copy(userProfile = updatedProfile))
                showEditProfileDialog = false
            }
        )
    }

    if (appState.userProfile.isOnboarded && !appState.userProfile.permissionsPromptShown) {
        NativePermissionSetupDialog(
            context = context,
            onDone = {
                persist(
                    appState.copy(
                        userProfile = appState.userProfile.copy(permissionsPromptShown = true)
                    )
                )
            }
        )
    }

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

    if (showSuggestionsSheet) {
        val todayStr = LocalDate.now().toString()
        val suggestions = appState.tasks.filter {
            !it.isCompleted && !(it.isMyDay && it.myDayDate == todayStr) && it.dueDate != todayStr
        }.sortedWith(compareBy<TaskItem> { it.dueDate ?: "9999-99-99" }.thenBy { it.priority.ordinal })

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

    if (showVaultDialog) {
        ClearDataVaultDialog(
            currentPin = appState.settings.vaultPin,
            onDismiss = { showVaultDialog = false },
            onUpdatePin = { newPin ->
                persist(appState.copy(settings = appState.settings.copy(vaultPin = newPin)))
                Toast.makeText(context, "Vault PIN updated", Toast.LENGTH_SHORT).show()
            },
            onClearAllData = {
                appState = HabitOSStorage.clearAllResettingKeepPinAndProfile(
                    context,
                    appState.settings.vaultPin,
                    appState.userProfile
                )
                showVaultDialog = false
                Toast.makeText(context, "All data & streaks reset to a clean slate.", Toast.LENGTH_LONG).show()
            }
        )
    }

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
// LEFT SIDEBAR DRAWER (Spacious Category List + Sleek EOD Bar)
// =========================================================================
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

    val pendingMyDayTasks = state.tasks.count { !it.isCompleted && ((it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr) }
    val pendingMyDayHabits = state.habits.count { !it.completedDates.contains(todayStr) }
    val myDayCount = pendingMyDayTasks + pendingMyDayHabits
    val habitsCount = state.habits.size
    val bdaysCount = state.birthdays.size
    val importantCount = state.tasks.count { !it.isCompleted && (it.isImportant || it.priority == Priority.P1) }
    val plannedCount = state.tasks.count { !it.isCompleted && (!it.dueDate.isNullOrBlank() || !it.deadlineDate.isNullOrBlank()) }
    val allCount = state.tasks.size + state.habits.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitOSLauncherLogo(size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("habit", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f))
                        ) {
                            Text(
                                "OS",
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Saved on Device", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
            IconButton(onClick = onCloseDrawer, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Sidebar", tint = TextMuted)
            }
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        if (searchQuery.isEmpty()) {
                            Text("Search tasks, @labels, notes...", color = TextMuted, fontSize = 12.sp)
                        }
                        inner()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "Go →",
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clickable { onExecuteSearch() }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            item {
                SidebarNavRow(
                    icon = Icons.Default.WbSunny,
                    iconTint = AmberStreak,
                    label = "My Day",
                    count = myDayCount,
                    selected = activeSection == MainSection.MY_DAY,
                    onClick = { onSelectSection(MainSection.MY_DAY, null) }
                )
            }
            item {
                SidebarNavRow(
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = EmeraldPrimary,
                    label = "7-Day Habit Matrix",
                    count = habitsCount,
                    selected = activeSection == MainSection.HABITS,
                    onClick = { onSelectSection(MainSection.HABITS, null) }
                )
            }
            item {
                SidebarNavRow(
                    icon = Icons.Default.Cake,
                    iconTint = RoseAlert,
                    label = "Birthdays",
                    count = bdaysCount,
                    selected = activeSection == MainSection.BIRTHDAYS,
                    onClick = { onSelectSection(MainSection.BIRTHDAYS, null) }
                )
            }
            item {
                SidebarNavRow(
                    icon = Icons.Outlined.StarBorder,
                    iconTint = RoseAlert,
                    label = "Important",
                    count = importantCount,
                    selected = activeSection == MainSection.IMPORTANT,
                    onClick = { onSelectSection(MainSection.IMPORTANT, null) }
                )
            }
            item {
                SidebarNavRow(
                    icon = Icons.Default.CalendarMonth,
                    iconTint = Color(0xFFA855F7),
                    label = "Planned",
                    count = plannedCount,
                    selected = activeSection == MainSection.PLANNED,
                    onClick = { onSelectSection(MainSection.PLANNED, null) }
                )
            }
            item {
                SidebarNavRow(
                    icon = Icons.Default.CheckBox,
                    iconTint = Color(0xFF3B82F6),
                    label = "All Tasks & Habits",
                    count = allCount,
                    selected = activeSection == MainSection.ALL_ITEMS,
                    onClick = { onSelectSection(MainSection.ALL_ITEMS, null) }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = ObsidianBorder)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MY LISTS & CATEGORIES",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${state.customLists.size}",
                        color = EmeraldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(state.customLists, key = { it.id }) { list ->
                val count = state.tasks.count { it.listId == list.id && !it.isCompleted } +
                    state.habits.count { it.listId == list.id }
                val isSel = activeSection == MainSection.CUSTOM_LIST && activeCustomListId == list.id
                Surface(
                    color = if (isSel) EmeraldPrimary.copy(alpha = 0.18f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSection(MainSection.CUSTOM_LIST, list.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(list.colorHex))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = list.name,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$count",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete List",
                            tint = TextMuted.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDeleteCustomList(list) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Surface(
            color = EmeraldPrimary.copy(alpha = 0.08f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = String.format(
                            Locale.getDefault(),
                            "EOD Alert • Daily at %02d:%02d",
                            state.settings.eodHour,
                            state.settings.eodMinute
                        ),
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = EmeraldPrimary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { onOpenEodConfig() }
                ) {
                    Text(
                        "Config",
                        color = ObsidianBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+ ", color = TextMuted, fontSize = 12.sp)
                    BasicTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        singleLine = true,
                        textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(EmeraldPrimary),
                        decorationBox = { inner ->
                            if (newListName.isEmpty()) {
                                Text("New category...", color = TextMuted, fontSize = 12.sp)
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(palette[colorIdx % palette.size]))
                    .clickable { colorIdx = (colorIdx + 1) % palette.size }
            )

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOpenProfileEdit() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                RandomUserAvatarBadge(
                    seed = state.userProfile.avatarSeed,
                    name = state.userProfile.name
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = state.userProfile.name.ifBlank { "habitOS User" },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = buildString {
                        if (state.userProfile.dob.isNotBlank()) append(state.userProfile.dob)
                        if (state.userProfile.gender.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(state.userProfile.gender)
                        }
                        if (isEmpty()) append("Pro • Offline")
                    }
                    Text(
                        text = sub,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onOpenProfileEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Edit Profile",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SidebarNavRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) EmeraldPrimary else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) ObsidianBg else iconTint,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = if (selected) ObsidianBg else TextPrimary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (selected) ObsidianBg.copy(alpha = 0.2f) else ObsidianCard,
                shape = CircleShape
            ) {
                Text(
                    text = "$count",
                    color = if (selected) ObsidianBg else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun RandomUserAvatarBadge(seed: Int, name: String) {
    val gradients = listOf(
        listOf(Color(0xFF10B981), Color(0xFF059669)),
        listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
        listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
        listOf(Color(0xFFA855F7), Color(0xFF7E22CE)),
        listOf(Color(0xFFF43F5E), Color(0xFFBE123C)),
        listOf(Color(0xFF14B8A6), Color(0xFF0F766E))
    )
    val pair = gradients[seed.mod(gradients.size)]
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(pair))
            .border(1.5.dp, ObsidianBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = size.minDimension * 0.22f,
                center = center.copy(y = size.height * 0.36f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = size.minDimension * 0.38f,
                center = center.copy(y = size.height * 0.88f)
            )
        }
        val initials = name.trim().split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifEmpty { "U" }
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp
        )
    }
}

// =========================================================================
// TAB 1: MY DAY SCREEN (With Dedicated TIME-BLOCK WORKLOAD Tray + Todoist Chips)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDayWebDashboardScreen(
    state: PersistedState,
    searchQuery: String,
    onOpenSuggestions: () -> Unit,
    onNavigateToHabits: () -> Unit,
    onNavigateToPlanned: () -> Unit,
    onAddUnifiedItem: (
        isHabit: Boolean,
        title: String,
        listId: String?,
        dueDate: String?,
        cue: String,
        isMyDay: Boolean,
        priority: Priority,
        dueTime: String?,
        durationMinutes: Int?,
        deadlineDate: String?,
        recurrence: RecurrenceType
    ) -> Unit,
    onPostponeTask: (TaskItem, String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleImportant: (TaskItem) -> Unit,
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
    var selectedListId by remember { mutableStateOf(state.customLists.firstOrNull()?.id) }
    var showListDropdown by remember { mutableStateOf(false) }
    var selectedDateStr by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }
    var cueText by remember { mutableStateOf("") }
    var isMyDaySelected by remember { mutableStateOf(true) }

    var selectedPriority by remember { mutableStateOf(Priority.P4) }
    var showPriorityDropdown by remember { mutableStateOf(false) }
    var selectedTimeStr by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDurationMin by remember { mutableStateOf<Int?>(null) }
    var showDurationDropdown by remember { mutableStateOf(false) }
    var selectedDeadlineStr by remember { mutableStateOf<String?>(null) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var selectedRecurrence by remember { mutableStateOf(RecurrenceType.NONE) }
    var showRecurrenceDropdown by remember { mutableStateOf(false) }

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

    val scheduledActiveMinutes = myDayTasks.filter { !it.isCompleted }.sumOf { it.durationMinutes ?: 0 }
    val scheduledDoneMinutes = myDayTasks.filter { it.isCompleted }.sumOf { it.durationMinutes ?: 0 }
    val urgentP1Count = myDayTasks.count { !it.isCompleted && (it.priority == Priority.P1 || it.isImportant) }
    val activeDeadlinesCount = state.tasks.count { !it.isCompleted && !it.deadlineDate.isNullOrBlank() }

    val consistencyDays = WeekHelper.consistencyStreakDays(state.habits, today)
    val recordStreak = state.habits.maxOfOrNull { maxOf(it.recordStreak, it.currentStreak(today)) } ?: 0

    val totalWeekChecks = weekDays.sumOf { d ->
        state.habits.count { it.completedDates.contains(d.toString()) }
    }
    val possibleWeekChecks = state.habits.size * 7
    val weeklyPct = if (possibleWeekChecks == 0) 0 else (totalWeekChecks * 100) / possibleWeekChecks
    val perfectDaysCount = weekDays.count { WeekHelper.isPerfectDay(state.habits, it) }

    val filteredTasks = myDayTasks.filter {
        searchQuery.isBlank() ||
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.notes.contains(searchQuery, ignoreCase = true) ||
            it.labels.any { l -> l.contains(searchQuery, ignoreCase = true) }
    }

    val activeMyDayTasks = when (sortOption) {
        TaskSortOption.PRIORITY -> filteredTasks.filter { !it.isCompleted }.sortedWith(compareBy<TaskItem> { it.priority.ordinal }.thenBy { it.dueTime ?: "99:99" })
        TaskSortOption.DUE_TIME -> filteredTasks.filter { !it.isCompleted }.sortedWith(compareBy<TaskItem> { it.dueTime ?: "99:99" }.thenBy { it.priority.ordinal })
        TaskSortOption.DURATION -> filteredTasks.filter { !it.isCompleted }.sortedWith(compareBy<TaskItem> { it.durationMinutes ?: Int.MAX_VALUE }.thenBy { it.priority.ordinal })
        TaskSortOption.TITLE -> filteredTasks.filter { !it.isCompleted }.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }
    val completedMyDayTasks = filteredTasks.filter { it.isCompleted }

    val filteredHabits = state.habits.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
    }

    val durationPresets = listOf(15, 30, 45, 60, 90, 120)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // 1. TOP KPI DASHBOARD CARDS + DEDICATED TIME-BLOCK WORKLOAD TRAY
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = ObsidianCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenSuggestions() }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("MY DAY PROGRESS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.WbSunny, contentDescription = null, tint = AmberStreak, modifier = Modifier.size(15.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "$doneCombined/$totalCombined",
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "$myDayPct% done",
                                    color = EmeraldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "$doneMyDayTasks/${myDayTasks.size} tasks • $doneTodayHabits/${state.habits.size} habits",
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        color = ObsidianCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToHabits() }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("CONSISTENCY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Record: ${recordStreak}d",
                                    color = AmberStreak,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "$consistencyDays",
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "Streak Days",
                                    color = AmberStreak,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Days hitting ≥65% habits",
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Tray 3: Weekly Habit Score
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToHabits() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("WEEKLY HABIT SCORE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = EmeraldPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "👑 $perfectDaysCount Perfect",
                                        color = EmeraldPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "$weeklyPct%",
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "$totalWeekChecks/$possibleWeekChecks checks • Open Matrix",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                    }
                }

                // Tray 4 (NEW Dedicated Todoist Pro Tray): TIME-BLOCK WORKLOAD & PRIORITY OVERVIEW
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPlanned() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("TIME-BLOCK WORKLOAD", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                if (urgentP1Count > 0) {
                                    Surface(color = RoseAlert.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "🚩 $urgentP1Count P1 Urgent",
                                            color = RoseAlert,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (activeDeadlinesCount > 0) {
                                    Surface(color = AmberStreak.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "🎯 $activeDeadlinesCount Deadlines",
                                            color = AmberStreak,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = DateFormatHelper.formatWorkload(scheduledActiveMinutes),
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "remaining today • ${DateFormatHelper.formatWorkload(scheduledDoneMinutes)} completed",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            color = TealSecondary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TealSecondary.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. CONTEXT-AWARE UNIFIED COMMAND CARD
        item {
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Surface(
                        color = ObsidianBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = if (!isHabitMode) EmeraldPrimary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isHabitMode = false }
                            ) {
                                Text(
                                    text = "☑️ One-Time Task",
                                    color = if (!isHabitMode) ObsidianBg else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                )
                            }
                            Surface(
                                color = if (isHabitMode) EmeraldPrimary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isHabitMode = true }
                            ) {
                                Text(
                                    text = "🔄 Daily Habit",
                                    color = if (isHabitMode) ObsidianBg else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
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
                                        text = if (isHabitMode) "Enter daily habit (e.g. Read 20 mins)..."
                                        else "Enter task (supports p1, 30m, at 5pm, @tag)...",
                                        color = TextMuted,
                                        fontSize = 13.sp,
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
                                    onAddUnifiedItem(
                                        isHabitMode,
                                        inputText.trim(),
                                        selectedListId,
                                        if (isHabitMode) null else selectedDateStr,
                                        cueText.trim(),
                                        isMyDaySelected,
                                        if (isHabitMode) Priority.P4 else selectedPriority,
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

                    // Row 1: Category + Cue
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            val activeList = state.customLists.find { it.id == selectedListId }
                            Surface(
                                color = ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showListDropdown = true }
                            ) {
                                Text(
                                    text = "📁 ${activeList?.name ?: "Category"} ▾",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showListDropdown,
                                onDismissRequest = { showListDropdown = false },
                                modifier = Modifier.background(ObsidianCard)
                            ) {
                                state.customLists.forEach { list ->
                                    DropdownMenuItem(
                                        text = { Text(list.name, color = TextPrimary, fontSize = 12.sp) },
                                        onClick = {
                                            selectedListId = list.id
                                            showListDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Surface(
                            color = ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎯 ", fontSize = 11.sp)
                                BasicTextField(
                                    value = cueText,
                                    onValueChange = { cueText = it },
                                    singleLine = true,
                                    textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 11.sp),
                                    cursorBrush = SolidColor(EmeraldPrimary),
                                    decorationBox = { inner ->
                                        if (cueText.isEmpty()) {
                                            Text(
                                                "Cue (e.g. 30m)",
                                                color = TextMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Row 2: Single-Line Horizontally Scrollable Todoist Chip Bar (Zero Text Slicing!)
                    if (!isHabitMode) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Due Date Chip
                            Surface(
                                color = ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.clickable { showDatePicker = true }
                            ) {
                                Text(
                                    text = "📅 ${DateFormatHelper.shortDate(selectedDateStr, today)}",
                                    color = AmberStreak,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            // 2. Priority Chip (P1-P4)
                            Box {
                                val prioCol = Color(selectedPriority.colorHex)
                                Surface(
                                    color = if (selectedPriority != Priority.P4) prioCol.copy(alpha = 0.16f) else ObsidianNav,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (selectedPriority != Priority.P4) prioCol else ObsidianBorder),
                                    modifier = Modifier.clickable { showPriorityDropdown = true }
                                ) {
                                    Text(
                                        text = "🚩 ${selectedPriority.shortLabel}",
                                        color = if (selectedPriority != Priority.P4) prioCol else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showPriorityDropdown,
                                    onDismissRequest = { showPriorityDropdown = false },
                                    modifier = Modifier.background(ObsidianCard)
                                ) {
                                    Priority.entries.forEach { prio ->
                                        DropdownMenuItem(
                                            text = { Text(prio.label, color = Color(prio.colorHex), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                selectedPriority = prio
                                                showPriorityDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 3. Time Block Chip
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
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            // 4. Duration Chip
                            Box {
                                val hasDur = selectedDurationMin != null
                                Surface(
                                    color = if (hasDur) EmeraldPrimary.copy(alpha = 0.18f) else ObsidianNav,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (hasDur) EmeraldPrimary else ObsidianBorder),
                                    modifier = Modifier.clickable { showDurationDropdown = true }
                                ) {
                                    Text(
                                        text = if (hasDur) "⏱️ ${DateFormatHelper.formatWorkload(selectedDurationMin!!)}" else "⏱️ Duration",
                                        color = if (hasDur) EmeraldPrimary else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showDurationDropdown,
                                    onDismissRequest = { showDurationDropdown = false },
                                    modifier = Modifier.background(ObsidianCard)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("No duration", color = TextMuted, fontSize = 12.sp) },
                                        onClick = { selectedDurationMin = null; showDurationDropdown = false }
                                    )
                                    durationPresets.forEach { mins ->
                                        DropdownMenuItem(
                                            text = { Text(DateFormatHelper.formatWorkload(mins), color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            onClick = { selectedDurationMin = mins; showDurationDropdown = false }
                                        )
                                    }
                                }
                            }

                            // 5. Recurrence Chip
                            Box {
                                val hasRec = selectedRecurrence != RecurrenceType.NONE
                                Surface(
                                    color = if (hasRec) AmberStreak.copy(alpha = 0.16f) else ObsidianNav,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (hasRec) AmberStreak else ObsidianBorder),
                                    modifier = Modifier.clickable { showRecurrenceDropdown = true }
                                ) {
                                    Text(
                                        text = if (hasRec) "🔁 ${selectedRecurrence.shortTag}" else "🔁 Repeat",
                                        color = if (hasRec) AmberStreak else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showRecurrenceDropdown,
                                    onDismissRequest = { showRecurrenceDropdown = false },
                                    modifier = Modifier.background(ObsidianCard)
                                ) {
                                    RecurrenceType.entries.forEach { rec ->
                                        DropdownMenuItem(
                                            text = { Text(rec.label, color = TextPrimary, fontSize = 12.sp) },
                                            onClick = { selectedRecurrence = rec; showRecurrenceDropdown = false }
                                        )
                                    }
                                }
                            }

                            // 6. Hard Deadline Chip
                            Surface(
                                color = if (selectedDeadlineStr != null) RoseAlert.copy(alpha = 0.16f) else ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (selectedDeadlineStr != null) RoseAlert else ObsidianBorder),
                                modifier = Modifier.clickable { showDeadlinePicker = true }
                            ) {
                                Text(
                                    text = if (selectedDeadlineStr != null) "🎯 ${DateFormatHelper.shortDate(selectedDeadlineStr, today)}" else "🎯 Deadline",
                                    color = if (selectedDeadlineStr != null) RoseAlert else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            // 7. My Day Toggle Chip
                            Surface(
                                color = if (isMyDaySelected) AmberStreak.copy(alpha = 0.16f) else ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isMyDaySelected) AmberStreak else ObsidianBorder),
                                modifier = Modifier.clickable { isMyDaySelected = !isMyDaySelected }
                            ) {
                                Text(
                                    text = "☀️ My Day",
                                    color = if (isMyDaySelected) AmberStreak else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. OVERDUE RESCUE SECTION ON MY DAY
        if (overdueTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🚨 OVERDUE (${overdueTasks.size})",
                        color = RoseAlert,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = RoseAlert.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            overdueTasks.forEach { onPostponeTask(it, todayStr) }
                        }
                    ) {
                        Text(
                            text = "Reschedule All",
                            color = RoseAlert,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            items(overdueTasks, key = { "myday_ov_${it.id}" }) { task ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TaskRowCard(
                        task = task,
                        customLists = state.customLists,
                        onToggle = { onToggleTask(task) },
                        onToggleImportant = { onToggleImportant(task) },
                        onClick = { onClickTask(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PostponeChip("☀️ Today") { onPostponeTask(task, todayStr) }
                        PostponeChip("+1d") { onPostponeTask(task, today.plusDays(1).toString()) }
                        PostponeChip("Weekend") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()) }
                        PostponeChip("Next Wk") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()) }
                    }
                }
            }
        }

        // 4. WORKSPACE CONTENT
        if (filteredTasks.isEmpty() && filteredHabits.isEmpty() && overdueTasks.isEmpty()) {
            item {
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 28.dp, horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Your habitOS workspace is clean and ready!",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Type your first task or daily habit in the bar above to start building your real streak.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (filteredHabits.isNotEmpty()) {
                item {
                    Text(
                        "TODAY'S DAILY HABITS (${filteredHabits.count { it.completedDates.contains(todayStr) }}/${filteredHabits.size})",
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                items(filteredHabits, key = { "myday_h_${it.id}" }) { habit ->
                    val doneToday = habit.completedDates.contains(todayStr)
                    Surface(
                        color = ObsidianCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleHabitToday(habit) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = doneToday,
                                onCheckedChange = { onToggleHabitToday(habit) },
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
                                    text = habit.name,
                                    color = if (doneToday) TextMuted else TextPrimary,
                                    textDecoration = if (doneToday) TextDecoration.LineThrough else TextDecoration.None,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "🔥 Streak: ${habit.currentStreak(today)}d" +
                                        if (habit.cue.isNotBlank()) " • 🎯 ${habit.cue}" else "",
                                    color = AmberStreak,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { onDeleteHabit(habit) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert)
                            }
                        }
                    }
                }
            }

            if (filteredTasks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "MY DAY TASKS (${completedMyDayTasks.size}/${filteredTasks.size})",
                            color = AmberStreak,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TaskSortOption.entries.forEach { opt ->
                                val sel = sortOption == opt
                                Surface(
                                    color = if (sel) EmeraldPrimary.copy(alpha = 0.18f) else ObsidianNav,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (sel) EmeraldPrimary else ObsidianBorder),
                                    modifier = Modifier.clickable { sortOption = opt }
                                ) {
                                    Text(
                                        text = opt.label,
                                        color = if (sel) EmeraldPrimary else TextMuted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
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
                        onToggleImportant = { onToggleImportant(task) },
                        onClick = { onClickTask(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                }

                if (completedMyDayTasks.isNotEmpty()) {
                    item {
                        Surface(
                            color = ObsidianNav,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCompletedSection = !showCompletedSection }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✓ Completed (${completedMyDayTasks.size})",
                                    color = EmeraldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (showCompletedSection) "Hide ▲" else "Show ▼",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    if (showCompletedSection) {
                        items(completedMyDayTasks, key = { "myday_done_${it.id}" }) { task ->
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
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            selectedDateStr = Instant.ofEpochMilli(millis)
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

    if (showDeadlinePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            selectedDeadlineStr = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                                .toString()
                        }
                        showDeadlinePicker = false
                    }
                ) {
                    Text("Set Deadline", color = RoseAlert)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedDeadlineStr = null
                        showDeadlinePicker = false
                    }
                ) {
                    Text("Clear", color = TextMuted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
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
                TextButton(
                    onClick = {
                        selectedTimeStr = null
                        showTimePicker = false
                    }
                ) {
                    Text("Clear", color = TextMuted)
                }
            }
        )
    }
}

// =========================================================================
// TASK ROW CARD (Single-Line Short Date + Todoist Metadata — Never Wraps!)
// =========================================================================
@Composable
fun TaskRowCard(
    task: TaskItem,
    customLists: List<CustomTaskList>,
    onToggle: () -> Unit,
    onToggleImportant: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val today = LocalDate.now()
    val listTag = customLists.find { it.id == task.listId }
    val prioColor = Color(task.priority.colorHex)
    val timeBlock = DateFormatHelper.formatTimeBlock(task.dueTime, task.durationMinutes)
    val deadlineInfo = DateFormatHelper.deadlineBadgeInfo(task.deadlineDate, today)

    val metaParts = mutableListOf<String>()
    if (!task.dueDate.isNullOrBlank()) {
        metaParts.add("📅 ${DateFormatHelper.shortDate(task.dueDate, today)}")
    }
    if (timeBlock != null) {
        metaParts.add(timeBlock)
    }
    if (deadlineInfo != null) {
        metaParts.add(deadlineInfo.first)
    }
    if (task.recurrence != RecurrenceType.NONE) {
        metaParts.add("🔁 ${task.recurrence.shortTag}")
    }
    if (task.steps.isNotEmpty()) {
        val doneSteps = task.steps.count { it.isDone }
        metaParts.add("$doneSteps/${task.steps.size} steps")
    }
    if (task.cue.isNotBlank()) {
        metaParts.add("🎯 ${task.cue}")
    }
    if (task.comments.isNotEmpty()) {
        metaParts.add("💬 ${task.comments.size}")
    } else if (task.notes.isNotBlank()) {
        metaParts.add("📝")
    }
    if (task.labels.isNotEmpty()) {
        metaParts.add(task.labels.joinToString(" ") { "@$it" })
    }

    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            when {
                !task.isCompleted && (task.priority == Priority.P1 || task.isImportant) -> RoseAlert.copy(alpha = 0.55f)
                !task.isCompleted && task.priority == Priority.P2 -> AmberStreak.copy(alpha = 0.5f)
                else -> ObsidianBorder
            }
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
                    checkedColor = if (task.priority != Priority.P4) prioColor else EmeraldPrimary,
                    uncheckedColor = if (task.priority != Priority.P4) prioColor else TextMuted,
                    checkmarkColor = ObsidianBg
                )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.priority != Priority.P4) {
                        Surface(
                            color = prioColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = task.priority.shortLabel,
                                color = prioColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = task.title,
                        color = if (task.isCompleted) TextMuted else TextPrimary,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (listTag != null || metaParts.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        if (listTag != null) {
                            Text(
                                text = "● ${listTag.name}",
                                color = Color(listTag.colorHex),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (listTag != null && metaParts.isNotEmpty()) {
                            Text(
                                text = "  •  ",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (metaParts.isNotEmpty()) {
                            Text(
                                text = metaParts.joinToString(" • "),
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onToggleImportant,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (task.isImportant || task.priority == Priority.P1) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Star",
                    tint = if (task.isImportant || task.priority == Priority.P1) AmberStreak else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete Task",
                    tint = RoseAlert,
                    modifier = Modifier.size(20.dp)
                )
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
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
// =========================================================================
// TAB 2: 7-DAY HABIT MATRIX (Refined Copy & Single-Line Navigator!)
// =========================================================================
@Composable
fun HabitGridScreen(
    state: PersistedState,
    onAddHabit: (String) -> Unit,
    onToggleHabitDate: (HabitItem, String) -> Unit,
    onDeleteHabit: (HabitItem) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    var weekOffset by remember { mutableIntStateOf(0) }
    val weekDays = remember(weekOffset, today) { WeekHelper.weekDaysForOffset(weekOffset, today) }
    var newHabitName by remember { mutableStateOf("") }

    val perfectDaysCount = weekDays.count { WeekHelper.isPerfectDay(state.habits, it) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "7-Day Habit Matrix",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "🔒 Today-only check-in lock",
                        fontSize = 11.sp,
                        color = EmeraldPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        Text(text = "👑", fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$perfectDaysCount Perfect",
                            color = AmberStreak,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            WeeklyCompletionBarChart(
                habits = state.habits,
                weekDays = weekDays,
                today = today,
                weekOffset = weekOffset,
                onChangeWeekOffset = { weekOffset = it }
            )
        }

        item {
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = newHabitName,
                        onValueChange = { newHabitName = it },
                        singleLine = true,
                        textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(EmeraldPrimary),
                        decorationBox = { inner ->
                            if (newHabitName.isEmpty()) {
                                Text(
                                    "New daily habit (e.g. Code 1 hr)...",
                                    color = TextMuted,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (newHabitName.isNotBlank()) {
                                onAddHabit(newHabitName.trim())
                                newHabitName = ""
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.size(36.dp)
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
                    onToggleDate = { clickedDate ->
                        if (clickedDate == today) {
                            onToggleHabitDate(habit, clickedDate.toString())
                        } else {
                            val msg = if (clickedDate.isBefore(today)) {
                                "🔒 Day has ended! Past habits are locked to protect streak integrity."
                            } else {
                                "🔒 Future days cannot be marked in advance!"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
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
    today: LocalDate,
    weekOffset: Int,
    onChangeWeekOffset: (Int) -> Unit
) {
    val startDay = weekDays.first()
    val endDay = weekDays.last()
    val rangeLabel = "${startDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${startDay.dayOfMonth} – " +
        "${endDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${endDay.dayOfMonth}"

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
                    text = "Weekly Trend",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                val totalPossible = (habits.size * 7).coerceAtLeast(1)
                val totalDone = weekDays.sumOf { d ->
                    habits.count { it.completedDates.contains(d.toString()) }
                }
                val weeklyPct = if (habits.isEmpty()) 0 else (totalDone * 100) / totalPossible
                Surface(
                    color = EmeraldPrimary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$weeklyPct% • ${if (weekOffset == 0) "This Week" else rangeLabel}",
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                color = ObsidianNav,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = " ◀ ",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onChangeWeekOffset(weekOffset - 1) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rangeLabel,
                            color = AmberStreak,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (weekOffset != 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = EmeraldPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { onChangeWeekOffset(0) }
                            ) {
                                Text(
                                    text = "Today",
                                    color = EmeraldPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val canGoNext = weekOffset < 0
                    Text(
                        text = " ▶ ",
                        color = if (canGoNext) TextPrimary else TextMuted.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = canGoNext) { onChangeWeekOffset(weekOffset + 1) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
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
                                .height(48.dp)
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
    onToggleDate: (LocalDate) -> Unit,
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
                            .clickable { onToggleDate(date) }
                            .alpha(if (isToday || checked) 1f else 0.45f)
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
                                    width = if (isToday) 1.5.dp else 1.dp,
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
                                    color = if (isToday) TextPrimary else TextMuted,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
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
// TAB 3: BIRTHDAYS (Custom Wish Support + 6 Wishes + Refined Copy!)
// =========================================================================
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = "Birthdays & Rescue",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Midnight & 1-day prior alerts",
                        fontSize = 12.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            item {
                Text(
                    text = "🚨 YESTERDAY - Missed! (Belated Rescue)",
                    color = RoseAlert,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
            items(missedYesterday, key = { "missed_${it.id}" }) { person ->
                BirthdayCard(
                    person = person,
                    today = today,
                    badgeText = "Missed!",
                    badgeColor = RoseAlert,
                    actionLabel = "Send Belated Birthday Wish",
                    onWishClick = { activeWishTargetId = person.id to true },
                    onDelete = { onDeleteBirthday(person) }
                )
            }
        }

        if (todayBirthdays.isNotEmpty()) {
            item {
                Text(
                    text = "🎂 TODAY'S BIRTHDAYS",
                    color = AmberStreak,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
            items(todayBirthdays, key = { "today_${it.id}" }) { person ->
                val wished = person.wishedYear == today.year
                BirthdayCard(
                    person = person,
                    today = today,
                    badgeText = if (wished) "✓ Wished" else "🎂 TODAY!",
                    badgeColor = if (wished) EmeraldPrimary else AmberStreak,
                    actionLabel = "Send Birthday Wish",
                    onWishClick = { activeWishTargetId = person.id to false },
                    onDelete = { onDeleteBirthday(person) }
                )
            }
        }

        if (upcomingBirthdays.isNotEmpty()) {
            item {
                Text(
                    text = "📅 UPCOMING BIRTHDAYS (${upcomingBirthdays.size})",
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            items(upcomingBirthdays, key = { "up_${it.id}" }) { person ->
                val daysLeft = person.daysUntilNext(today)
                BirthdayCard(
                    person = person,
                    today = today,
                    badgeText = "In ${daysLeft}d",
                    badgeColor = TealSecondary,
                    actionLabel = "Send Birthday Wish",
                    onWishClick = { activeWishTargetId = person.id to false },
                    onDelete = { onDeleteBirthday(person) }
                )
            }
        }

        if (state.birthdays.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Cake,
                    title = "0 Sample Birthdays",
                    subtitle = "Tap '+ Add' above to track birthdays with 1-day-prior alerts, 12:00 Midnight reminders, and custom wishes."
                )
            }
        }
    }

    if (showAddDialog) {
        AddBirthdayDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, relation, day, month, year, phone, customWish ->
                onAddBirthday(name, relation, day, month, year, phone, customWish)
                showAddDialog = false
            }
        )
    }

    val targetPair = activeWishTargetId
    val targetPerson = targetPair?.let { (id, _) -> state.birthdays.find { it.id == id } }
    if (targetPerson != null && targetPair != null) {
        val isBelated = targetPair.second
        BirthdayWishGeneratorDialog(
            person = targetPerson,
            isBelated = isBelated,
            onDismiss = { activeWishTargetId = null },
            onSaveCustomWish = { newCustom ->
                onUpdateCustomWish(targetPerson, newCustom)
                Toast.makeText(
                    context,
                    if (newCustom.isBlank()) "Reverted to auto-wishes for ${targetPerson.name}"
                    else "Custom wish saved for ${targetPerson.name}!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onCopyText = { msg ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Birthday Wish", msg))
                onMarkWished(targetPerson)
                Toast.makeText(context, "Wish copied to clipboard!", Toast.LENGTH_SHORT).show()
                activeWishTargetId = null
            },
            onSendWhatsApp = { msg ->
                onMarkWished(targetPerson)
                val encoded = URLEncoder.encode(msg, "UTF-8")
                val cleanPhone = targetPerson.phone.filter { it.isDigit() }
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

    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = person.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (person.hasCustomWish()) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = AmberStreak.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    text = "✨ Custom",
                                    color = AmberStreak,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    val dateAndAge = buildString {
                        append("$monthName ${person.dayOfMonth}")
                        if (person.birthYear in 1900..today.year) {
                            append(", ${person.birthYear}")
                        }
                        if (turningAge != null && turningAge > 0) {
                            append(" • Turning $turningAge")
                        }
                    }
                    Text(
                        text = dateAndAge,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete Birthday",
                            tint = RoseAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onWishClick,
                colors = ButtonDefaults.buttonColors(containerColor = badgeColor),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// =========================================================================
// TAB 4: PLANNED SCREEN (With Todoist Chips, 1-Tap Postpone & Sort Bar)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedScreen(
    state: PersistedState,
    onAddPlannedTask: (
        title: String,
        dueDateStr: String,
        priority: Priority,
        dueTime: String?,
        durationMinutes: Int?,
        deadlineDate: String?,
        recurrence: RecurrenceType
    ) -> Unit,
    onPostponeTask: (TaskItem, String) -> Unit,
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

    var selectedPriority by remember { mutableStateOf(Priority.P4) }
    var showPriorityDropdown by remember { mutableStateOf(false) }
    var selectedTimeStr by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDurationMin by remember { mutableStateOf<Int?>(null) }
    var showDurationDropdown by remember { mutableStateOf(false) }
    var selectedDeadlineStr by remember { mutableStateOf<String?>(null) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var selectedRecurrence by remember { mutableStateOf(RecurrenceType.NONE) }
    var showRecurrenceDropdown by remember { mutableStateOf(false) }

    var sortOption by remember { mutableStateOf(TaskSortOption.PRIORITY) }
    var showCompletedSection by remember { mutableStateOf(false) }

    val durationPresets = listOf(15, 30, 45, 60, 90, 120)

    val parsedSelected = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
    val shortDateLabel = if (parsedSelected != null) {
        "${parsedSelected.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${parsedSelected.dayOfMonth}"
    } else selectedDate

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Planned",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Overdue • Today • Upcoming",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskSortOption.entries.forEach { opt ->
                    val sel = sortOption == opt
                    Surface(
                        color = if (sel) EmeraldPrimary.copy(alpha = 0.18f) else ObsidianNav,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (sel) EmeraldPrimary else ObsidianBorder),
                        modifier = Modifier.clickable { sortOption = opt }
                    ) {
                        Text(
                            text = opt.label,
                            color = if (sel) EmeraldPrimary else TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(EmeraldPrimary),
                        decorationBox = { inner ->
                            if (title.isEmpty()) {
                                Text(
                                    "Add scheduled task (supports p1, 30m, at 5pm)...",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AmberStreak.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { showDatePicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = AmberStreak, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(shortDateLabel, color = AmberStreak, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    FilledIconButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                onAddPlannedTask(
                                    title.trim(),
                                    selectedDate,
                                    selectedPriority,
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

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = ObsidianBorder)
                Spacer(Modifier.height(6.dp))

                // Single-Line Scrollable Todoist Chip Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        val prioCol = Color(selectedPriority.colorHex)
                        Surface(
                            color = if (selectedPriority != Priority.P4) prioCol.copy(alpha = 0.16f) else ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selectedPriority != Priority.P4) prioCol else ObsidianBorder),
                            modifier = Modifier.clickable { showPriorityDropdown = true }
                        ) {
                            Text(
                                text = "🚩 ${selectedPriority.shortLabel}",
                                color = if (selectedPriority != Priority.P4) prioCol else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
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
                        color = if (selectedTimeStr != null) TealSecondary.copy(alpha = 0.18f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selectedTimeStr != null) EmeraldPrimary else ObsidianBorder),
                        modifier = Modifier.clickable { showTimePicker = true }
                    ) {
                        Text(
                            text = if (selectedTimeStr != null) "🕒 $selectedTimeStr" else "🕒 Time",
                            color = if (selectedTimeStr != null) EmeraldPrimary else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Box {
                        val hasDur = selectedDurationMin != null
                        Surface(
                            color = if (hasDur) EmeraldPrimary.copy(alpha = 0.18f) else ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (hasDur) EmeraldPrimary else ObsidianBorder),
                            modifier = Modifier.clickable { showDurationDropdown = true }
                        ) {
                            Text(
                                text = if (hasDur) "⏱️ ${DateFormatHelper.formatWorkload(selectedDurationMin!!)}" else "⏱️ Duration",
                                color = if (hasDur) EmeraldPrimary else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        DropdownMenu(expanded = showDurationDropdown, onDismissRequest = { showDurationDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                            DropdownMenuItem(
                                text = { Text("No duration", color = TextMuted, fontSize = 12.sp) },
                                onClick = { selectedDurationMin = null; showDurationDropdown = false }
                            )
                            durationPresets.forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text(DateFormatHelper.formatWorkload(mins), color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    onClick = { selectedDurationMin = mins; showDurationDropdown = false }
                                )
                            }
                        }
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
                                text = if (hasRec) "🔁 ${selectedRecurrence.shortTag}" else "🔁 Repeat",
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

                    Surface(
                        color = if (selectedDeadlineStr != null) RoseAlert.copy(alpha = 0.16f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selectedDeadlineStr != null) RoseAlert else ObsidianBorder),
                        modifier = Modifier.clickable { showDeadlinePicker = true }
                    ) {
                        Text(
                            text = if (selectedDeadlineStr != null) "🎯 ${DateFormatHelper.shortDate(selectedDeadlineStr, today)}" else "🎯 Deadline",
                            color = if (selectedDeadlineStr != null) RoseAlert else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (plannedTasks.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.CalendarMonth,
                title = "No Planned Deadlines",
                subtitle = "Schedule tasks with a due date, time block, or deadline to organize them into Overdue, Today, and Upcoming."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (overdue.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("OVERDUE (${overdue.size})", color = RoseAlert, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Surface(
                                color = RoseAlert.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    overdue.forEach { onPostponeTask(it, todayStr) }
                                }
                            ) {
                                Text(
                                    text = "Reschedule All",
                                    color = RoseAlert,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    items(overdue, key = { "ov_${it.id}" }) { task ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TaskRowCard(
                                task = task,
                                customLists = state.customLists,
                                onToggle = { onToggleTask(task) },
                                onToggleImportant = { onToggleImportant(task) },
                                onClick = { onClickTask(task) },
                                onDelete = { onDeleteTask(task) }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PostponeChip("☀️ Today") { onPostponeTask(task, todayStr) }
                                PostponeChip("+1d") { onPostponeTask(task, today.plusDays(1).toString()) }
                                PostponeChip("Weekend") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()) }
                                PostponeChip("Next Wk") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()) }
                            }
                        }
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
                if (completedPlanned.isNotEmpty()) {
                    item {
                        Surface(
                            color = ObsidianNav,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCompletedSection = !showCompletedSection }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✓ Completed (${completedPlanned.size})",
                                    color = EmeraldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (showCompletedSection) "Hide ▲" else "Show ▼",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    if (showCompletedSection) {
                        items(completedPlanned, key = { "pl_done_${it.id}" }) { task ->
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

    if (showDeadlinePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            selectedDeadlineStr = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                                .toString()
                        }
                        showDeadlinePicker = false
                    }
                ) {
                    Text("Set Deadline", color = RoseAlert)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedDeadlineStr = null
                        showDeadlinePicker = false
                    }
                ) {
                    Text("Clear", color = TextMuted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
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
                TextButton(
                    onClick = {
                        selectedTimeStr = null
                        showTimePicker = false
                    }
                ) {
                    Text("Clear", color = TextMuted)
                }
            }
        )
    }
}

// =========================================================================
// FULL-SCREEN SUB-PAGES
// =========================================================================
@Composable
fun FilteredListSectionScreen(
    state: PersistedState,
    section: MainSection,
    customListId: String?,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onAddTask: (String, String?, Boolean) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleImportant: (TaskItem) -> Unit,
    onToggleHabitToday: (HabitItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onDeleteHabit: (HabitItem) -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    text = "Tap top-left ← arrow to return Home",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            if (searchQuery.isNotBlank()) {
                AssistChip(
                    onClick = onClearSearch,
                    label = { Text("Filter: \"$searchQuery\" ✕", color = EmeraldPrimary, fontSize = 11.sp) }
                )
            }
        }

        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    singleLine = true,
                    textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(EmeraldPrimary),
                    decorationBox = { inner ->
                        if (newTaskTitle.isEmpty()) {
                            Text("Add task to $title (supports p1, 30m, @tag)...", color = TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
                                section == MainSection.IMPORTANT
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

        Spacer(Modifier.height(12.dp))

        if (matchingTasks.isEmpty() && habits.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.FormatListBulleted,
                title = "No Items in $title",
                subtitle = "Add a task above or tap the top-left ← arrow to return to your main dashboard."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (habits.isNotEmpty()) {
                    item {
                        Text("DAILY HABITS (${habits.size})", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    items(habits, key = { "sub_h_${it.id}" }) { habit ->
                        val doneToday = habit.completedDates.contains(todayStr)
                        Surface(
                            color = ObsidianCard,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleHabitToday(habit) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = doneToday,
                                    onCheckedChange = { onToggleHabitToday(habit) },
                                    colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary, uncheckedColor = TextMuted, checkmarkColor = ObsidianBg)
                                )
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    Text(habit.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("🔥 Streak: ${habit.currentStreak(today)}d", color = AmberStreak, fontSize = 11.sp)
                                }
                                IconButton(onClick = { onDeleteHabit(habit) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert)
                                }
                            }
                        }
                    }
                }
                if (matchingTasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TASKS (${activeTasks.size})", color = AmberStreak, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TaskSortOption.entries.forEach { opt ->
                                    val sel = sortOption == opt
                                    Surface(
                                        color = if (sel) EmeraldPrimary.copy(alpha = 0.18f) else ObsidianNav,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (sel) EmeraldPrimary else ObsidianBorder),
                                        modifier = Modifier.clickable { sortOption = opt }
                                    ) {
                                        Text(
                                            text = opt.label,
                                            color = if (sel) EmeraldPrimary else TextMuted,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(activeTasks, key = { "sub_t_${it.id}" }) { task ->
                        TaskRowCard(
                            task = task,
                            customLists = state.customLists,
                            onToggle = { onToggleTask(task) },
                            onToggleImportant = { onToggleImportant(task) },
                            onClick = { onClickTask(task) },
                            onDelete = { onDeleteTask(task) }
                        )
                    }
                    if (completedTasks.isNotEmpty()) {
                        item {
                            Surface(
                                color = ObsidianNav,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCompletedSection = !showCompletedSection }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "✓ Completed (${completedTasks.size})",
                                        color = EmeraldPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (showCompletedSection) "Hide ▲" else "Show ▼",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        if (showCompletedSection) {
                            items(completedTasks, key = { "sub_done_${it.id}" }) { task ->
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
        }
    }
}

// =========================================================================
// REDESIGNED ADD BIRTHDAY POPUP (3-Column Grid + Level Date Boxes + Custom Wish)
// =========================================================================
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
    var showCustomWishInput by remember { mutableStateOf(false) }
    var customWishText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCalendarPicker by remember { mutableStateOf(false) }

    val relationRows = listOf(
        listOf("Friend", "Best Friend", "Brother"),
        listOf("Sister", "Mother", "Father"),
        listOf("Partner", "Cousin", "Other")
    )

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = ObsidianBg,
        unfocusedContainerColor = ObsidianBg,
        focusedBorderColor = EmeraldPrimary,
        unfocusedBorderColor = ObsidianBorder,
        focusedLabelColor = EmeraldPrimary,
        unfocusedLabelColor = TextMuted,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = EmeraldPrimary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cake, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Birthday", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Surface(
                    color = AmberStreak.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AmberStreak.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { showCalendarPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = AmberStreak, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Calendar", color = AmberStreak, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = RoseAlert.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RoseAlert),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = RoseAlert, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(errorMessage!!, color = RoseAlert, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Person's Name *", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Chitrarth, Vignesh", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Relation (private • tailors wish style)",
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    relationRows.forEach { rowList ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowList.forEach { rel ->
                                val selected = relation == rel
                                Surface(
                                    color = if (selected) EmeraldPrimary else ObsidianBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (selected) EmeraldPrimary else ObsidianBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { relation = rel }
                                ) {
                                    Text(
                                        text = rel,
                                        color = if (selected) ObsidianBg else TextPrimary,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    "Date & Year of Birth *",
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Day", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                        OutlinedTextField(
                            value = dayStr,
                            onValueChange = {
                                dayStr = it.filter { c -> c.isDigit() }.take(2)
                                errorMessage = null
                            },
                            placeholder = { Text("DD", color = TextMuted, fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Month", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                        OutlinedTextField(
                            value = monthStr,
                            onValueChange = {
                                monthStr = it.filter { c -> c.isDigit() }.take(2)
                                errorMessage = null
                            },
                            placeholder = { Text("MM", color = TextMuted, fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("Year", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                        OutlinedTextField(
                            value = yearStr,
                            onValueChange = {
                                yearStr = it.filter { c -> c.isDigit() }.take(4)
                                errorMessage = null
                            },
                            placeholder = { Text("YYYY", color = TextMuted, fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("WhatsApp Phone (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. 919876543210", color = TextMuted, fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = if (showCustomWishInput) AmberStreak.copy(alpha = 0.14f) else ObsidianBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (showCustomWishInput) AmberStreak else ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomWishInput = !showCustomWishInput }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (showCustomWishInput) "✨ Custom Wish for This Person" else "+ Add Custom Birthday Wish (Optional)",
                            color = if (showCustomWishInput) AmberStreak else EmeraldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Icon(
                            imageVector = if (showCustomWishInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = if (showCustomWishInput) AmberStreak else EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (showCustomWishInput) {
                    OutlinedTextField(
                        value = customWishText,
                        onValueChange = { customWishText = it },
                        placeholder = {
                            Text(
                                "Write a personal wish exclusively for ${name.ifBlank { "this person" }}...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        },
                        minLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = TextMuted, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        val cleanName = name.trim()
                        val d = dayStr.toIntOrNull()
                        val m = monthStr.toIntOrNull()
                        val y = yearStr.toIntOrNull() ?: 2008

                        when {
                            cleanName.isEmpty() -> {
                                errorMessage = "Please enter the person's name!"
                            }
                            d == null || d !in 1..31 -> {
                                errorMessage = "Enter a valid Day (1–31)!"
                            }
                            m == null || m !in 1..12 -> {
                                errorMessage = "Enter a valid Month (1–12)!"
                            }
                            y !in 1900..LocalDate.now().year -> {
                                errorMessage = "Enter a valid Year (1900–${LocalDate.now().year})!"
                            }
                            else -> {
                                onSave(cleanName, relation, d, m, y, phone.trim(), customWishText.trim())
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Text("Save Birthday", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }
            }
        },
        dismissButton = {}
    )

    if (showCalendarPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showCalendarPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            dayStr = picked.dayOfMonth.toString()
                            monthStr = picked.monthValue.toString()
                            yearStr = picked.year.toString()
                            errorMessage = null
                        }
                        showCalendarPicker = false
                    }
                ) {
                    Text("Use Date", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarPicker = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// =========================================================================
// BIRTHDAY WISH GENERATOR (6 Wishes + Save Custom Wish Per Person!)
// =========================================================================
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
        mutableStateOf(
            if (usingCustom && person.hasCustomWish()) {
                person.customWish.orEmpty()
            } else {
                BirthdayWishTemplates.generateWish(person, isBelated, templateIdx)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Column {
                Text(
                    text = if (isBelated) "🚨 Belated Birthday Rescue" else "🎂 Birthday Wish Generator",
                    color = if (isBelated) RoseAlert else EmeraldPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (usingCustom && person.hasCustomWish()) {
                        "✨ Saved Custom Wish for ${person.name}"
                    } else {
                        "For ${person.name} • Style ${(templateIdx.mod(BirthdayWishTemplates.TEMPLATE_COUNT)) + 1} of ${BirthdayWishTemplates.TEMPLATE_COUNT}"
                    },
                    color = if (usingCustom && person.hasCustomWish()) AmberStreak else TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = EmeraldPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            onSaveCustomWish(messageText.trim())
                            usingCustom = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.BookmarkAdded, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Save as Custom Wish",
                                color = EmeraldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = AmberStreak.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AmberStreak.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            usingCustom = false
                            templateIdx = (templateIdx + 1) % BirthdayWishTemplates.TEMPLATE_COUNT
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = AmberStreak, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Shuffle (${(templateIdx.mod(BirthdayWishTemplates.TEMPLATE_COUNT)) + 1}/${BirthdayWishTemplates.TEMPLATE_COUNT})",
                                color = AmberStreak,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    minLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onCopyText(messageText) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy Text", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onSendWhatsApp(messageText) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(10.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstLaunchUserOnboardingDialog(
    initialProfile: UserProfile,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSaveProfile: (name: String, dob: String, gender: String, avatarSeed: Int) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile.name) }
    var dob by remember { mutableStateOf(initialProfile.dob) }
    var gender by remember { mutableStateOf(initialProfile.gender.ifBlank { "Male" }) }
    var seed by remember { mutableIntStateOf(initialProfile.avatarSeed) }
    var showDobPicker by remember { mutableStateOf(false) }
    val genders = listOf("Male", "Female", "Other")

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = ObsidianBg,
        unfocusedContainerColor = ObsidianBg,
        focusedBorderColor = EmeraldPrimary,
        unfocusedBorderColor = EmeraldPrimary.copy(alpha = 0.5f),
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    AlertDialog(
        onDismissRequest = { if (isEditing) onDismiss() },
        containerColor = ObsidianCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RandomUserAvatarBadge(seed = seed, name = name.ifBlank { "H" })
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isEditing) "Edit Your Profile" else "Welcome to habitOS",
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Displayed at the bottom of your sidebar",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = { seed = Random.nextInt(0, 100) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Shuffle Random Avatar", color = EmeraldPrimary, fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Full Name *") },
                    placeholder = { Text("e.g. Aayush Kumar") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Date of Birth (DD/MM/YYYY)") },
                    placeholder = { Text("e.g. 14/08/2008") },
                    trailingIcon = {
                        IconButton(onClick = { showDobPicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick DOB", tint = AmberStreak)
                        }
                    },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Gender", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genders.forEach { g ->
                        val selected = gender == g
                        Surface(
                            color = if (selected) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) EmeraldPrimary else ObsidianBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { gender = g }
                        ) {
                            Text(
                                text = g,
                                color = if (selected) EmeraldPrimary else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSaveProfile(name.trim(), dob.trim(), gender, seed)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Save Profile", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
            }
        }
    )

    if (showDobPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDobPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val d = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            dob = String.format(Locale.getDefault(), "%02d/%02d/%04d", d.dayOfMonth, d.monthValue, d.year)
                        }
                        showDobPicker = false
                    }
                ) {
                    Text("Select", color = EmeraldPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDobPicker = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun NativePermissionSetupDialog(
    context: Context,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = ObsidianCard,
        title = {
            Text(
                "🔔 Enable Native Background Alerts",
                color = EmeraldPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "To make sure 12:00 Midnight Birthday Reminders and 21:00 EOD Check-Ins fire reliably on your phone:",
                    color = TextPrimary,
                    fontSize = 12.sp
                )
                Button(
                    onClick = {
                        NotificationScheduler.ensureChannel(context)
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val n = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_popup_reminder)
                            .setContentTitle("habitOS • Notifications Active! ✅")
                            .setContentText("Midnight birthday & 21:00 EOD background alerts are ready.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build()
                        nm.notify(7777, n)
                        Toast.makeText(context, "Test notification fired! Check your status bar.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1. Fire Test Notification Now", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("2. Open Notification Settings", color = TextPrimary)
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("3. Allow Autostart / No Battery Limit", color = AmberStreak)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("All Set, Continue", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// =========================================================================
// UPGRADED TASK DETAIL BOTTOM SHEET (Todoist Pro Union + Activity Log)
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

            // 1. 4-Tier Priority Selector
            Text("Priority", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Priority.entries.forEach { prio ->
                    val sel = task.priority == prio
                    val col = Color(prio.colorHex)
                    Surface(
                        color = if (sel) col.copy(alpha = 0.2f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (sel) col else ObsidianBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onUpdateTask(task.copy(priority = prio, isImportant = prio == Priority.P1))
                            }
                    ) {
                        Text(
                            text = "🚩 ${prio.shortLabel}",
                            color = if (sel) col else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            // 2. 1-Tap Postpone & Quick Schedule Row
            Text("1-Tap Postpone & Schedule", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PostponeChip("☀️ Today") {
                    onUpdateTask(task.copy(dueDate = todayStr, isMyDay = true, myDayDate = todayStr))
                }
                PostponeChip("+1d") {
                    val next = today.plusDays(1).toString()
                    onUpdateTask(task.copy(dueDate = next, isMyDay = false, myDayDate = ""))
                }
                PostponeChip("Weekend") {
                    val sat = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()
                    onUpdateTask(task.copy(dueDate = sat, isMyDay = false, myDayDate = ""))
                }
                PostponeChip("Next Wk") {
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
                        text = if (!task.dueDate.isNullOrBlank()) "📅 Due: ${DateFormatHelper.shortDate(task.dueDate, today)}" else "📅 Pick Date",
                        color = AmberStreak,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 3. My Day, Category & Recurrence Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    label = { Text(if (inMyDay) "In My Day" else "Add to My Day", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )

                Box {
                    val activeList = customLists.find { it.id == task.listId }
                    Surface(
                        color = ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.clickable { showCategoryDropdown = true }
                    ) {
                        Text(
                            text = "📁 ${activeList?.name ?: "Category"} ▾",
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

                Box {
                    val hasRec = task.recurrence != RecurrenceType.NONE
                    Surface(
                        color = if (hasRec) AmberStreak.copy(alpha = 0.16f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (hasRec) AmberStreak else ObsidianBorder),
                        modifier = Modifier.clickable { showRecurrenceDropdown = true }
                    ) {
                        Text(
                            text = "🔁 ${task.recurrence.label}",
                            color = if (hasRec) AmberStreak else TextMuted,
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

            if (task.recurrence == RecurrenceType.AFTER_COMPLETION) {
                Surface(
                    color = ObsidianNav,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Repeat every ${task.customRecurrenceDays} day(s) after completion",
                            color = AmberStreak,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
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

            // 4. Time Block, Deadline & Reminder Row
            Text("Time Block, Hard Deadline & Reminder", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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
                        text = if (!task.dueTime.isNullOrBlank()) "🕒 Start: ${task.dueTime}" else "🕒 Set Time",
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
                    val dlLabel = DateFormatHelper.deadlineBadgeInfo(task.deadlineDate, today)?.first ?: "🎯 Hard Deadline"
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

            // 5. Duration Quick-Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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

            // 6. Multi-Labels (@tags)
            Text("Labels (@tags)", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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

            // 7. Subtasks (Steps)
            Text("Subtasks (${task.steps.count { it.isDone }}/${task.steps.size})", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

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
                    Text("Add", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = ObsidianBorder)

            // 8. Auto-Saving Notes
            Text("Auto-Saving Notes", color = AmberStreak, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            OutlinedTextField(
                value = task.notes,
                onValueChange = { newNotes ->
                    onUpdateTask(task.copy(notes = newNotes))
                },
                placeholder = { Text("Type notes here (saves automatically)...", color = TextMuted) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = ObsidianBorder)

            // 9. Timestamped Comments & Activity Log
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
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            val picked = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                            onUpdateTask(task.copy(dueDate = picked, isMyDay = picked == todayStr, myDayDate = if (picked == todayStr) todayStr else ""))
                        }
                        showDueDatePicker = false
                    }
                ) { Text("Save Date", color = EmeraldPrimary) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onUpdateTask(task.copy(dueDate = null))
                        showDueDatePicker = false
                    }
                ) { Text("Clear", color = TextMuted) }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showDeadlinePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            val picked = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                            onUpdateTask(task.copy(deadlineDate = picked))
                        }
                        showDeadlinePicker = false
                    }
                ) { Text("Set Deadline", color = RoseAlert) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onUpdateTask(task.copy(deadlineDate = null))
                        showDeadlinePicker = false
                    }
                ) { Text("Clear", color = TextMuted) }
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
                TextButton(
                    onClick = {
                        onUpdateTask(task.copy(dueTime = null))
                        showTimePicker = false
                    }
                ) { Text("Clear", color = TextMuted) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsBottomSheet(
    suggestions: List<TaskItem>,
    onDismiss: () -> Unit,
    onAddToMyDay: (TaskItem) -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()

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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    items(suggestions, key = { "sug_${it.id}" }) { task ->
                        val isOverdue = !task.dueDate.isNullOrBlank() && task.dueDate < todayStr
                        val prioCol = Color(task.priority.colorHex)
                        Surface(
                            color = ObsidianNav,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isOverdue) RoseAlert.copy(alpha = 0.5f) else ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(task.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (task.priority != Priority.P4) {
                                            Text(task.priority.shortLabel, color = prioCol, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        if (!task.dueDate.isNullOrBlank()) {
                                            Text(
                                                text = if (isOverdue) "🚨 Overdue (${DateFormatHelper.shortDate(task.dueDate, today)})" else "📅 ${DateFormatHelper.shortDate(task.dueDate, today)}",
                                                color = if (isOverdue) RoseAlert else AmberStreak,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
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
            }
            Spacer(Modifier.height(24.dp))
        }
    }
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

                HorizontalDivider(color = ObsidianBorder)

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
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}