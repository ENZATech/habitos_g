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
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
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
                        onAddUnifiedItem = { isHabit, title, listId, dueDate, cue, isMyDay, isImportant ->
                            if (isHabit) {
                                val newHabit = HabitItem(
                                    name = title,
                                    listId = listId,
                                    cue = cue
                                )
                                persist(appState.copy(habits = appState.habits + newHabit))
                            } else {
                                val todayStr = LocalDate.now().toString()
                                val newTask = TaskItem(
                                    title = title,
                                    isImportant = isImportant,
                                    isMyDay = isMyDay,
                                    myDayDate = if (isMyDay) todayStr else "",
                                    dueDate = dueDate,
                                    listId = listId,
                                    cue = cue
                                )
                                persist(appState.copy(tasks = listOf(newTask) + appState.tasks))
                            }
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
                        onAddBirthday = { name, relation, day, month, year, phone ->
                            val person = BirthdayPerson(
                                name = name,
                                relation = relation,
                                dayOfMonth = day,
                                monthValue = month,
                                birthYear = year,
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

                    MainSection.PLANNED -> PlannedScreen(
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

                    MainSection.IMPORTANT,
                    MainSection.ALL_ITEMS,
                    MainSection.CUSTOM_LIST -> FilteredListSectionScreen(
                        state = appState,
                        section = activeSection,
                        customListId = activeCustomListId,
                        searchQuery = searchQuery,
                        onClearSearch = { searchQuery = "" },
                        onBackToHome = { activeSection = lastMainTabSection },
                        onAddTask = { title, listId, important ->
                            val newTask = TaskItem(
                                title = title,
                                listId = listId,
                                isImportant = important
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
// LEFT SIDEBAR DRAWER
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
    val importantCount = state.tasks.count { !it.isCompleted && it.isImportant }
    val plannedCount = state.tasks.count { !it.isCompleted && !it.dueDate.isNullOrBlank() }
    val allCount = state.tasks.size + state.habits.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitOSLauncherLogo(size = 42.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("habit", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
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
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Saved on Device", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
            IconButton(onClick = onCloseDrawer) {
                Icon(Icons.Default.Close, contentDescription = "Close Sidebar", tint = TextMuted)
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(EmeraldPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Search tasks, steps, notes...", color = TextMuted, fontSize = 12.sp)
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

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                    label = "7-Day Habit Matrix & Chart",
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
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = ObsidianBorder)
                Spacer(Modifier.height(6.dp))
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${state.customLists.size}",
                        color = EmeraldPrimary,
                        fontSize = 11.sp,
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSection(MainSection.CUSTOM_LIST, list.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(list.colorHex))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = list.name,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$count",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete List",
                            tint = TextMuted.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(15.dp)
                                .clickable { onDeleteCustomList(list) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Surface(
            color = EmeraldPrimary.copy(alpha = 0.08f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Auto EOD Reminder",
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            String.format(
                                Locale.getDefault(),
                                "Daily at %02d:%02d",
                                state.settings.eodHour,
                                state.settings.eodMinute
                            ),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                Surface(
                    color = EmeraldPrimary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onOpenEodConfig() }
                ) {
                    Text(
                        "Config",
                        color = ObsidianBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+ ", color = TextMuted, fontSize = 13.sp)
                    BasicTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        singleLine = true,
                        textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(EmeraldPrimary),
                        decorationBox = { inner ->
                            if (newListName.isEmpty()) {
                                Text("New list (e.g. C...", color = TextMuted, fontSize = 12.sp)
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Add", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = ObsidianBorder)
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOpenProfileEdit() }
                .padding(horizontal = 4.dp, vertical = 4.dp),
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
                        fontSize = 14.sp,
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
            IconButton(onClick = onOpenProfileEdit) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Edit Profile",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
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
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) ObsidianBg else iconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = if (selected) ObsidianBg else TextPrimary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
            .size(38.dp)
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
            fontSize = 13.sp
        )
    }
}

// =========================================================================
// TAB 1: MY DAY SCREEN (Combined Progress + Context-Aware Segmented Bar!)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDayWebDashboardScreen(
    state: PersistedState,
    searchQuery: String,
    onOpenSuggestions: () -> Unit,
    onNavigateToHabits: () -> Unit,
    onAddUnifiedItem: (
        isHabit: Boolean,
        title: String,
        listId: String?,
        dueDate: String?,
        cue: String,
        isMyDay: Boolean,
        isImportant: Boolean
    ) -> Unit,
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
    var isImportantSelected by remember { mutableStateOf(false) }

    // COMBINED MY DAY PROGRESS (Today's Tasks + Today's Daily Habits!)
    val myDayTasks = state.tasks.filter {
        (it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr
    }
    val doneMyDayTasks = myDayTasks.count { it.isCompleted }
    val doneTodayHabits = state.habits.count { it.completedDates.contains(todayStr) }

    val doneCombined = doneMyDayTasks + doneTodayHabits
    val totalCombined = myDayTasks.size + state.habits.size
    val myDayPct = if (totalCombined == 0) 0 else (doneCombined * 100) / totalCombined

    val consistencyDays = WeekHelper.consistencyStreakDays(state.habits, today)
    val recordStreak = state.habits.maxOfOrNull { maxOf(it.recordStreak, it.currentStreak(today)) } ?: 0

    val totalWeekChecks = weekDays.sumOf { d ->
        state.habits.count { it.completedDates.contains(d.toString()) }
    }
    val possibleWeekChecks = state.habits.size * 7
    val weeklyPct = if (possibleWeekChecks == 0) 0 else (totalWeekChecks * 100) / possibleWeekChecks
    val perfectDaysCount = weekDays.count { WeekHelper.isPerfectDay(state.habits, it) }

    val filteredTasks = myDayTasks.filter {
        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.notes.contains(searchQuery, ignoreCase = true)
    }
    val filteredHabits = state.habits.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // 1. TOP KPI DASHBOARD CARDS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: MY DAY PROGRESS (Tasks + Habits Combined)
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

                    // Card 2: CONSISTENCY STREAK
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

                // Card 3: WEEKLY HABIT SCORE
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
                                    "$totalWeekChecks/$possibleWeekChecks checks • Tap for 7-Day Matrix",
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
                    // Clean 2-Tab Segmented Switcher at the Top (Zero Truncation!)
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

                    // Text Input Row + Add Button
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
                                        else "Enter task title...",
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
                                        if (isHabitMode) false else isImportantSelected
                                    )
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

                    // Metadata Row 1: Category + Cue (Always shown for both Task & Habit)
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

                    // Metadata Row 2: ONLY shown when in "One-Time Task" Mode!
                    // (Daily Habits do NOT show Due Date, My Day, or Mark Important)
                    if (!isHabitMode) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val parsedDue = runCatching { LocalDate.parse(selectedDateStr) }.getOrNull()
                            val shortDue = if (parsedDue != null) {
                                "${parsedDue.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${parsedDue.dayOfMonth}"
                            } else selectedDateStr

                            Surface(
                                color = ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showDatePicker = true }
                            ) {
                                Text(
                                    text = "📅 Due: $shortDue",
                                    color = AmberStreak,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                color = if (isMyDaySelected) AmberStreak.copy(alpha = 0.16f) else ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isMyDaySelected) AmberStreak else ObsidianBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isMyDaySelected = !isMyDaySelected }
                            ) {
                                Text(
                                    text = "☀️ My Day",
                                    color = if (isMyDaySelected) AmberStreak else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                color = if (isImportantSelected) RoseAlert.copy(alpha = 0.16f) else ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isImportantSelected) RoseAlert else ObsidianBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isImportantSelected = !isImportantSelected }
                            ) {
                                Text(
                                    text = "☆ Important",
                                    color = if (isImportantSelected) RoseAlert else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. WORKSPACE CONTENT
        if (filteredTasks.isEmpty() && filteredHabits.isEmpty()) {
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
                    Text(
                        "MY DAY TASKS (${filteredTasks.count { it.isCompleted }}/${filteredTasks.size})",
                        color = AmberStreak,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                items(filteredTasks, key = { "myday_t_${it.id}" }) { task ->
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
                if (task.cue.isNotBlank()) {
                    metaParts.add("🎯 ${task.cue}")
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
// TAB 2: 7-DAY HABIT MATRIX (Multi-Week History + Anti-Cheat Today-Only Lock!)
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
                        text = "🔒 Anti-Cheat Lock: Only Today can be marked",
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

        // Weekly Completion Trend Card with Multi-Week History Navigator
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
                    text = "$weeklyPct% (${if (weekOffset == 0) "This Week" else rangeLabel})",
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Multi-Week Navigator Bar (< Prev Week | Date Range | Next Week >)
            Surface(
                color = ObsidianNav,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "◀ Prev Week",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
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
                            fontWeight = FontWeight.Bold
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
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val canGoNext = weekOffset < 0
                    Text(
                        text = "Next Week ▶",
                        color = if (canGoNext) TextPrimary else TextMuted.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
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
// TAB 3: BIRTHDAYS (Private Relation + Clean Single-Line Cards + Tight Dialog)
// =========================================================================
@Composable
fun BirthdaysScreen(
    state: PersistedState,
    onAddBirthday: (name: String, relation: String, day: Int, month: Int, year: Int, phone: String) -> Unit,
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
                        text = "Day-before & 12:00 Midnight native alerts",
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
                    onWishClick = { activeWishTarget = person to true },
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
                    onWishClick = { activeWishTarget = person to false },
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
                    subtitle = "Tap '+ Add' above to track birthdays with 1-day-prior alerts, 12:00 Midnight reminders, and custom wishes."
                )
            }
        }
    }

    if (showAddDialog) {
        AddBirthdayDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, relation, day, month, year, phone ->
                onAddBirthday(name, relation, day, month, year, phone)
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
    today: LocalDate,
    badgeText: String,
    badgeColor: Color,
    actionLabel: String,
    onWishClick: () -> Unit,
    onDelete: () -> Unit
) {
    val monthName = Month.of(person.monthValue).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val turningAge = person.turningAge(today)

    // Privacy Protected: Relation is NEVER shown on the card face or button!
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
                    Text(
                        text = person.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
// TAB 4: PLANNED SCREEN (Short Date Pill so Input Never Truncates!)
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

    val parsedSelected = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
    val shortDateLabel = if (parsedSelected != null) {
        "${parsedSelected.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${parsedSelected.dayOfMonth}"
    } else selectedDate

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
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
        Text(
            text = "Overdue • Today • Upcoming",
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 10.dp)
        )

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
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(EmeraldPrimary),
                    decorationBox = { inner ->
                        if (title.isEmpty()) {
                            Text(
                                "Add scheduled task...",
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
                            onAddPlannedTask(title.trim(), selectedDate)
                            title = ""
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
// FULL-SCREEN SUB-PAGES
// =========================================================================
@Composable
fun FilteredListSectionScreen(
    state: PersistedState,
    section: MainSection,
    customListId: String?,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onBackToHome: () -> Unit,
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

    val baseTasks = when (section) {
        MainSection.IMPORTANT -> state.tasks.filter { it.isImportant }
        MainSection.ALL_ITEMS -> state.tasks
        MainSection.CUSTOM_LIST -> state.tasks.filter { it.listId == customListId }
        else -> state.tasks
    }
    val tasks = baseTasks.filter {
        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.notes.contains(searchQuery, ignoreCase = true)
    }
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
                    text = "Use top-left ← arrow to return to Home",
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
                            Text("Add task to $title...", color = TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

        if (tasks.isEmpty() && habits.isEmpty()) {
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
                if (tasks.isNotEmpty()) {
                    item {
                        Text("TASKS (${tasks.size})", color = AmberStreak, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    items(tasks, key = { "sub_t_${it.id}" }) { task ->
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

// =========================================================================
// REDESIGNED COMPACT ADD BIRTHDAY POPUP (2x4 Grid, Zero Empty Gaps!)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBirthdayDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, relation: String, day: Int, month: Int, year: Int, phone: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Friend") }
    var dayStr by remember { mutableStateOf("") }
    var monthStr by remember { mutableStateOf("") }
    var yearStr by remember { mutableStateOf("2008") }
    var phone by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCalendarPicker by remember { mutableStateOf(false) }

    val relationsRow1 = listOf("Friend", "Best Friend", "Brother", "Sister")
    val relationsRow2 = listOf("Mother", "Father", "Partner", "Cousin")

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

                // 1. Compact Person's Name Field
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

                // 2. Clean 2-Row Grid of Relations (Hidden on Card, Used for Custom Wishes)
                Text(
                    "Relation (kept private • used for wish style)",
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(relationsRow1, relationsRow2).forEach { rowList ->
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
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Date & Year of Birth (3 Equal Boxes)
                Text(
                    "Date & Year of Birth *",
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = dayStr,
                        onValueChange = {
                            dayStr = it.filter { c -> c.isDigit() }.take(2)
                            errorMessage = null
                        },
                        label = { Text("Day", fontSize = 11.sp) },
                        placeholder = { Text("DD", color = TextMuted, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = monthStr,
                        onValueChange = {
                            monthStr = it.filter { c -> c.isDigit() }.take(2)
                            errorMessage = null
                        },
                        label = { Text("Month", fontSize = 11.sp) },
                        placeholder = { Text("MM", color = TextMuted, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = {
                            yearStr = it.filter { c -> c.isDigit() }.take(4)
                            errorMessage = null
                        },
                        label = { Text("Year", fontSize = 11.sp) },
                        placeholder = { Text("YYYY", color = TextMuted, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                // 4. WhatsApp Number (Clean & Fully Visible)
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
            }
        },
        confirmButton = {
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
                            errorMessage = "Enter a valid Birth Year (1900–${LocalDate.now().year})!"
                        }
                        else -> {
                            onSave(cleanName, relation, d, m, y, phone.trim())
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save & Schedule Midnight Alert", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = TextMuted, fontSize = 12.sp)
            }
        }
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

@Composable
fun BirthdayWishGeneratorDialog(
    person: BirthdayPerson,
    isBelated: Boolean,
    onDismiss: () -> Unit,
    onCopyText: (String) -> Unit,
    onSendWhatsApp: (String) -> Unit
) {
    var templateIdx by remember { mutableIntStateOf(0) }
    var messageText by remember(templateIdx) {
        mutableStateOf(BirthdayWishTemplates.generateWish(person, isBelated, templateIdx))
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
                    fontSize = 17.sp
                )
                Text(
                    text = "Tailored for ${person.name} • Tap Shuffle to switch styles",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { templateIdx += 1 },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AmberStreak, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Shuffle Wish Style", color = AmberStreak, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
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