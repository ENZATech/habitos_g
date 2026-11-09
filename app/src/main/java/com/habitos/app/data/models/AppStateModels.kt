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