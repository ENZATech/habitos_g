package com.habitos.app.ui

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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

enum class HabitOSTab(val label: String) {
    MY_DAY("My Day"),
    HABITS("Habits"),
    BIRTHDAYS("Birthdays"),
    PLANNED("Planned"),
    LISTS("Lists")
}

enum class MainSection {
    MY_DAY,
    HABITS,
    BIRTHDAYS,
    PLANNED,
    IMPORTANT,
    ALL_ITEMS,
    CUSTOM_LIST
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
    var activeCustomListId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Sheets & Modals
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var showSuggestionsSheet by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var showEodTimeDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

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

    // LEFT SLIDING SIDEBAR DRAWER (Matches habitos_v2 + Profile Footer at bottom)
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = ObsidianNav,
                drawerContentColor = TextPrimary,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(310.dp)
            ) {
                HabitOSSidebarDrawerContent(
                    state = appState,
                    activeSection = activeSection,
                    activeCustomListId = activeCustomListId,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onSelectSection = { sec, listId ->
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Sidebar", tint = TextPrimary)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "habit",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = TextPrimary
                            )
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                color = EmeraldPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, EmeraldPrimary)
                            ) {
                                Text(
                                    text = "OS",
                                    color = EmeraldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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
                NavigationBar(
                    containerColor = ObsidianNav,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(BorderStroke(1.dp, ObsidianBorder))
                ) {
                    HabitOSTab.entries.forEach { tab ->
                        val selected = when (tab) {
                            HabitOSTab.MY_DAY -> activeSection == MainSection.MY_DAY
                            HabitOSTab.HABITS -> activeSection == MainSection.HABITS
                            HabitOSTab.BIRTHDAYS -> activeSection == MainSection.BIRTHDAYS
                            HabitOSTab.PLANNED -> activeSection == MainSection.PLANNED
                            HabitOSTab.LISTS -> drawerState.isOpen ||
                                activeSection in listOf(
                                    MainSection.IMPORTANT,
                                    MainSection.ALL_ITEMS,
                                    MainSection.CUSTOM_LIST
                                )
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                when (tab) {
                                    HabitOSTab.MY_DAY -> activeSection = MainSection.MY_DAY
                                    HabitOSTab.HABITS -> activeSection = MainSection.HABITS
                                    HabitOSTab.BIRTHDAYS -> activeSection = MainSection.BIRTHDAYS
                                    HabitOSTab.PLANNED -> activeSection = MainSection.PLANNED
                                    HabitOSTab.LISTS -> {
                                        // Opens the left sidebar drawer just like habitos_v2!
                                        scope.launch {
                                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                        }
                                    }
                                }
                            },
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
                when (activeSection) {
                    MainSection.MY_DAY -> MyDayWebDashboardScreen(
                        state = appState,
                        searchQuery = searchQuery,
                        onOpenSuggestions = { showSuggestionsSheet = true },
                        onNavigateToHabits = { activeSection = MainSection.HABITS },
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
                        onOpenDrawer = { scope.launch { drawerState.open() } },
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
                        onClickTask = { selectedTaskId = it.id },
                        onDeleteTask = ::deleteTaskWithUndo
                    )
                }
            }
        }
    }

    // 1. FIRST-LAUNCH USER ONBOARDING POPUP (Name, DOB, Gender + Random Profile Photo)
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

    // 2. NATIVE PERMISSIONS & MIUI BACKGROUND ALARM SETUP DIALOG (Right after Onboarding)
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

    // Task Detail Bottom Sheet
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
// LEFT SIDEBAR DRAWER (Matches habitos_v2.jpeg + Profile Card at Bottom)
// =========================================================================
@Composable
fun HabitOSSidebarDrawerContent(
    state: PersistedState,
    activeSection: MainSection,
    activeCustomListId: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
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

    val myDayCount = state.tasks.count { !it.isCompleted && ((it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr) }
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
        // Top Header: hv Logo + habit OS + Saved on Device + X button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, EmeraldPrimary.copy(alpha = 0.6f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "hv",
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("habit", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                "OS",
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
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

        // Search Bar
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
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
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Search tasks, steps, note", color = TextMuted, fontSize = 13.sp)
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Scrollable Middle Navigation + Custom Lists
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SidebarNavRow(
                icon = Icons.Default.WbSunny,
                iconTint = AmberStreak,
                label = "My Day",
                count = myDayCount,
                selected = activeSection == MainSection.MY_DAY,
                onClick = { onSelectSection(MainSection.MY_DAY, null) }
            )
            SidebarNavRow(
                icon = Icons.Default.LocalFireDepartment,
                iconTint = EmeraldPrimary,
                label = "7-Day Habit Matrix & Chart",
                count = habitsCount,
                selected = activeSection == MainSection.HABITS,
                onClick = { onSelectSection(MainSection.HABITS, null) }
            )
            SidebarNavRow(
                icon = Icons.Default.Cake,
                iconTint = RoseAlert,
                label = "Birthdays",
                count = bdaysCount,
                selected = activeSection == MainSection.BIRTHDAYS,
                onClick = { onSelectSection(MainSection.BIRTHDAYS, null) }
            )
            SidebarNavRow(
                icon = Icons.Outlined.StarBorder,
                iconTint = RoseAlert,
                label = "Important",
                count = importantCount,
                selected = activeSection == MainSection.IMPORTANT,
                onClick = { onSelectSection(MainSection.IMPORTANT, null) }
            )
            SidebarNavRow(
                icon = Icons.Default.CalendarMonth,
                iconTint = Color(0xFFA855F7),
                label = "Planned",
                count = plannedCount,
                selected = activeSection == MainSection.PLANNED,
                onClick = { onSelectSection(MainSection.PLANNED, null) }
            )
            SidebarNavRow(
                icon = Icons.Default.CheckBox,
                iconTint = Color(0xFF3B82F6),
                label = "All Tasks & Habits",
                count = allCount,
                selected = activeSection == MainSection.ALL_ITEMS,
                onClick = { onSelectSection(MainSection.ALL_ITEMS, null) }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
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
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            state.customLists.forEach { list ->
                val count = state.tasks.count { it.listId == list.id && !it.isCompleted }
                val isSel = activeSection == MainSection.CUSTOM_LIST && activeCustomListId == list.id
                Surface(
                    color = if (isSel) EmeraldPrimary.copy(alpha = 0.18f) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSection(MainSection.CUSTOM_LIST, list.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Auto EOD Reminder Card (Matches habitos_v2.jpeg)
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

        // + New list bar (Matches habitos_v2.jpeg)
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

            // Color cycling square
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
        Spacer(Modifier.height(8.dp))

        // USER PROFILE FOOTER AT THE VERY BOTTOM OF THE LIST SIDEBAR (Matches image_a01cfb.png)
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
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
        // Stylized geometric avatar portrait + initial
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
// TAB 1: MY DAY SCREEN (Matches habitos_web.png Dashboard + Unified Bar)
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

    // Unified Command Bar States (Matching habitos_web.png)
    var inputText by remember { mutableStateOf("") }
    var isHabitMode by remember { mutableStateOf(false) } // false = Task, true = Daily Habit (Matrix)
    var selectedListId by remember { mutableStateOf(state.customLists.firstOrNull()?.id) }
    var showListDropdown by remember { mutableStateOf(false) }
    var selectedDateStr by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }
    var cueText by remember { mutableStateOf("") }
    var isMyDaySelected by remember { mutableStateOf(true) }
    var isImportantSelected by remember { mutableStateOf(false) }

    // KPI Calculations
    val myDayTasks = state.tasks.filter {
        (it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr
    }
    val doneMyDay = myDayTasks.count { it.isCompleted }
    val totalMyDay = myDayTasks.size
    val myDayPct = if (totalMyDay == 0) 0 else (doneMyDay * 100) / totalMyDay

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
        // 1. TOP KPI DASHBOARD CARDS (Matches habitos_web.png)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: MY DAY PROGRESS
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
                                    "$doneMyDay/$totalMyDay",
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
                            Text("Tap for task suggestions", color = TextMuted, fontSize = 10.sp)
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
                            Text("Days hitting ≥65% habits", color = TextMuted, fontSize = 10.sp)
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
                        Column {
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
                                    "$totalWeekChecks/$possibleWeekChecks checks • Tap to open 7-Day Matrix",
                                    color = TextMuted,
                                    fontSize = 11.sp
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

        // 2. UNIFIED COMMAND BAR (Matches habitos_web.png)
        item {
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Top Input Row + Add Button
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
                                        "Add a task or daily habit to habitOS...",
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
                                        selectedDateStr,
                                        cueText.trim(),
                                        isMyDaySelected,
                                        isImportantSelected
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

                    // Bottom Metadata Chips Row (Type Toggle, Category, Date, Cue, My Day, Important)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chip 1: Task vs Daily Habit (Matrix)
                        Surface(
                            color = ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isHabitMode) EmeraldPrimary else ObsidianBorder),
                            modifier = Modifier.clickable { isHabitMode = !isHabitMode }
                        ) {
                            Text(
                                text = if (isHabitMode) "🔄 Daily Habit (Matrix)" else "☑️ Task Item",
                                color = if (isHabitMode) EmeraldPrimary else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        // Chip 2: Category / List selector
                        Box {
                            val activeList = state.customLists.find { it.id == selectedListId }
                            Surface(
                                color = ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.clickable { showListDropdown = true }
                            ) {
                                Text(
                                    text = "📁 ${activeList?.name?.take(14) ?: "Category"} ▾",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
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

                        // Chip 3: Date Picker
                        Surface(
                            color = ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.clickable { showDatePicker = true }
                        ) {
                            Text(
                                text = "📅 $selectedDateStr",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chip 4: Cue input (e.g. 30m)
                        Surface(
                            color = ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
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
                                            Text("Cue (e.g. 30m)", color = TextMuted, fontSize = 11.sp)
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Chip 5: My Day Toggle
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        // Chip 6: Important Toggle
                        Surface(
                            color = if (isImportantSelected) RoseAlert.copy(alpha = 0.16f) else ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isImportantSelected) RoseAlert else ObsidianBorder),
                            modifier = Modifier.clickable { isImportantSelected = !isImportantSelected }
                        ) {
                            Text(
                                text = "☆ Important",
                                color = if (isImportantSelected) RoseAlert else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. WORKSPACE CONTENT (Clean state matching habitos_web.png OR active items)
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
// TAB 2: 7-DAY INTERACTIVE HABIT MATRIX (GAP FIXED!)
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Modifier.weight(1f) prevents the badge from squishing the title and creating a huge vertical gap!
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
                        text = "Mon–Sun Check-In • Haptic Streak Engine",
                        fontSize = 12.sp,
                        color = TextMuted,
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

        // Weekly Completion Bar / Trend Chart Card
        item {
            WeeklyCompletionBarChart(habits = state.habits, weekDays = weekDays, today = today)
        }

        // Compact Single-Line Add New Habit Bar
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
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
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
        contentPadding = PaddingValues(vertical = 10.dp)
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
                        fontSize = 22.sp,
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
// TAB 4: PLANNED SCREEN (Single-line compact input fixed!)
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
// SIDEBAR FILTERED SECTIONS (Important, All Tasks & Habits, Custom List)
// =========================================================================
@Composable
fun FilteredListSectionScreen(
    state: PersistedState,
    section: MainSection,
    customListId: String?,
    onOpenDrawer: () -> Unit,
    onAddTask: (String, String?, Boolean) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleImportant: (TaskItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit
) {
    val activeList = state.customLists.find { it.id == customListId }
    val title = when (section) {
        MainSection.IMPORTANT -> "Important Tasks"
        MainSection.ALL_ITEMS -> "All Tasks & Habits"
        MainSection.CUSTOM_LIST -> activeList?.name ?: "Custom List"
        else -> "Tasks"
    }
    var newTaskTitle by remember { mutableStateOf("") }

    val tasks = when (section) {
        MainSection.IMPORTANT -> state.tasks.filter { it.isImportant }
        MainSection.ALL_ITEMS -> state.tasks
        MainSection.CUSTOM_LIST -> state.tasks.filter { it.listId == customListId }
        else -> state.tasks
    }

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
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            OutlinedButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sidebar", color = TextPrimary, fontSize = 12.sp)
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
                            Text("Add task to $title...", color = TextMuted, fontSize = 14.sp)
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

        if (tasks.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.FormatListBulleted,
                title = "No Tasks in $title",
                subtitle = "Add a task above or switch lists using the Left Sidebar."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
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

// =========================================================================
// ONE-TIME FIRST LAUNCH USER PROFILE POPUP & NATIVE PERMISSIONS DIALOG
// =========================================================================
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
    val genders = listOf("Male", "Female", "Other")

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
                        text = "Shown at the bottom of your Lists sidebar",
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
                    label = { Text("Your Full Name") },
                    placeholder = { Text("e.g. Aayush Kumar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Date of Birth (DD/MM/YYYY)") },
                    placeholder = { Text("e.g. 14/08/2008") },
                    singleLine = true,
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