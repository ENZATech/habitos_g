package com.habitos.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NotificationScheduler {
    const val CHANNEL_ID = "habitos_alerts_channel"
    const val ACTION_EOD_CHECK = "com.habitos.app.ACTION_EOD_CHECK"
    const val ACTION_BDAY_ALERT = "com.habitos.app.ACTION_BDAY_ALERT"
    const val ACTION_TASK_REMINDER = "com.habitos.app.ACTION_TASK_REMINDER"
    const val ACTION_COMPLETE_TASK_FROM_NOTIF = "com.habitos.app.ACTION_COMPLETE_TASK_FROM_NOTIF"
    const val ACTION_SNOOZE_TASK_FROM_NOTIF = "com.habitos.app.ACTION_SNOOZE_TASK_FROM_NOTIF"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "habitOS Reminders & Birthdays",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Task reminders, EOD habit/task alerts, and Midnight/Day-before birthday reminders"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun scheduleAllAlarms(context: Context, state: PersistedState) {
        ensureChannel(context)
        scheduleDailyEodAlarm(context, state.settings.eodHour, state.settings.eodMinute)
        scheduleBirthdayAlarms(context, state.birthdays)
        scheduleTaskReminders(context, state.tasks)
    }

    fun scheduleDailyEodAlarm(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
            action = ACTION_EOD_CHECK
        }
        val pi = PendingIntent.getBroadcast(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var target = LocalDateTime.now().withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (target.isBefore(LocalDateTime.now())) {
            target = target.plusDays(1)
        }
        val triggerMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setSafeExactAlarm(am, triggerMillis, pi)
    }

    fun scheduleBirthdayAlarms(context: Context, birthdays: List<BirthdayPerson>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()

        birthdays.forEach { person ->
            // 1) Exact 12:00 Midnight (00:00) on the Birthday
            val bdayMidnight = nextOccurrenceDateTime(person.monthValue, person.dayOfMonth, 0, 0, now)
            val midnightIntent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                action = ACTION_BDAY_ALERT
                putExtra("person_name", person.name)
                putExtra("is_day_before", false)
            }
            val midnightPi = PendingIntent.getBroadcast(
                context,
                (person.id.hashCode() and 0xFFFFFFF) + 1,
                midnightIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setSafeExactAlarm(
                am,
                bdayMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                midnightPi
            )

            // 2) 1 Day Before Reminder (at 09:00 AM the day before)
            var dayBefore = bdayMidnight.minusDays(1).withHour(9).withMinute(0)
            if (dayBefore.isBefore(now)) {
                dayBefore = dayBefore.plusYears(1)
            }
            val beforeIntent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                action = ACTION_BDAY_ALERT
                putExtra("person_name", person.name)
                putExtra("is_day_before", true)
            }
            val beforePi = PendingIntent.getBroadcast(
                context,
                (person.id.hashCode() and 0xFFFFFFF) + 2,
                beforeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setSafeExactAlarm(
                am,
                dayBefore.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                beforePi
            )
        }
    }

    fun scheduleTaskReminders(context: Context, tasks: List<TaskItem>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()

        tasks.forEach { task ->
            val reqCode = (task.id.hashCode() and 0xFFFFFFF) + 500
            val intent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                action = ACTION_TASK_REMINDER
                putExtra("task_id", task.id)
                putExtra("task_title", task.title)
                putExtra("task_prio", task.priority.shortLabel)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (task.isCompleted || task.dueDate.isNullOrBlank() || task.dueTime.isNullOrBlank() || task.reminderOffsetMinutes == null) {
                am.cancel(pi)
                return@forEach
            }

            val parsedDate = runCatching { LocalDate.parse(task.dueDate) }.getOrNull() ?: return@forEach
            val parsedTime = runCatching { LocalTime.parse(task.dueTime) }.getOrNull() ?: return@forEach
            val triggerDateTime = LocalDateTime.of(parsedDate, parsedTime)
                .minusMinutes(task.reminderOffsetMinutes.toLong())

            if (triggerDateTime.isAfter(now)) {
                val triggerMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                setSafeExactAlarm(am, triggerMillis, pi)
            }
        }
    }

    fun scheduleSnoozeAlarm(context: Context, taskId: String, taskTitle: String, taskPrio: String, snoozeMinutes: Int = 15) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reqCode = (taskId.hashCode() and 0xFFFFFFF) + 500
        val intent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra("task_id", taskId)
            putExtra("task_title", taskTitle)
            putExtra("task_prio", taskPrio)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerMillis = System.currentTimeMillis() + snoozeMinutes * 60_000L
        setSafeExactAlarm(am, triggerMillis, pi)
    }

    private fun nextOccurrenceDateTime(
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        now: LocalDateTime
    ): LocalDateTime {
        val safeDay = if (month == 2 && day == 29 && !java.time.Year.isLeap(now.year.toLong())) 28 else day
        var candidate = LocalDateTime.of(now.year, month, safeDay, hour, minute, 0)
        if (candidate.isBefore(now)) {
            val nextYear = now.year + 1
            val safeDayNext = if (month == 2 && day == 29 && !java.time.Year.isLeap(nextYear.toLong())) 28 else day
            candidate = LocalDateTime.of(nextYear, month, safeDayNext, hour, minute, 0)
        }
        return candidate
    }

    private fun setSafeExactAlarm(am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }
}

class HabitOSAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationScheduler.ensureChannel(context)
        val state = HabitOSStorage.load(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPi = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        when (intent.action) {
            NotificationScheduler.ACTION_EOD_CHECK -> {
                val today = LocalDate.now()
                val todayStr = today.toString()
                val yesterday = today.minusDays(1)

                val pendingHabits = state.habits.count { !it.completedDates.contains(todayStr) }
                val pendingMyDay = state.tasks.count {
                    !it.isCompleted && ((it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr)
                }
                val pendingBdays = state.birthdays.count {
                    (it.isBirthdayOn(today) || it.isBirthdayOn(yesterday)) && it.wishedYear != today.year
                }

                if (pendingHabits > 0 || pendingMyDay > 0 || pendingBdays > 0) {
                    val summaryParts = mutableListOf<String>()
                    if (pendingHabits > 0) summaryParts.add("$pendingHabits habit(s)")
                    if (pendingMyDay > 0) summaryParts.add("$pendingMyDay My Day task(s)")
                    if (pendingBdays > 0) summaryParts.add("$pendingBdays birthday wish(es)")

                    val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle("habitOS • Evening Check-In")
                        .setContentText("Still incomplete: ${summaryParts.joinToString(", ")}. Keep your streak alive!")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(contentPi)
                        .setAutoCancel(true)
                        .build()

                    nm.notify(9001, notification)
                }
                NotificationScheduler.scheduleDailyEodAlarm(
                    context,
                    state.settings.eodHour,
                    state.settings.eodMinute
                )
            }

            NotificationScheduler.ACTION_BDAY_ALERT -> {
                val name = intent.getStringExtra("person_name") ?: "A friend"
                val isDayBefore = intent.getBooleanExtra("is_day_before", false)
                val title = if (isDayBefore) "🎂 Birthday Tomorrow: $name" else "🎉 It's $name's Birthday Today! (Midnight)"
                val body = if (isDayBefore) {
                    "Heads up! $name's birthday is tomorrow. Keep it in mind!"
                } else {
                    "It's 12:00 Midnight! Open habitOS to send a 1-tap wish to $name."
                }

                val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(contentPi)
                    .setAutoCancel(true)
                    .build()

                nm.notify(name.hashCode(), notification)
                NotificationScheduler.scheduleBirthdayAlarms(context, state.birthdays)
            }

            NotificationScheduler.ACTION_TASK_REMINDER -> {
                val taskId = intent.getStringExtra("task_id") ?: return
                val taskTitle = intent.getStringExtra("task_title") ?: "Scheduled Task"
                val taskPrio = intent.getStringExtra("task_prio") ?: "P4"
                val notifId = (taskId.hashCode() and 0xFFFFFFF) + 500

                val completeIntent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                    action = NotificationScheduler.ACTION_COMPLETE_TASK_FROM_NOTIF
                    putExtra("task_id", taskId)
                    putExtra("notif_id", notifId)
                }
                val completePi = PendingIntent.getBroadcast(
                    context,
                    notifId + 1,
                    completeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val snoozeIntent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                    action = NotificationScheduler.ACTION_SNOOZE_TASK_FROM_NOTIF
                    putExtra("task_id", taskId)
                    putExtra("task_title", taskTitle)
                    putExtra("task_prio", taskPrio)
                    putExtra("notif_id", notifId)
                }
                val snoozePi = PendingIntent.getBroadcast(
                    context,
                    notifId + 2,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("⏰ Task Reminder • [$taskPrio]")
                    .setContentText(taskTitle)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(contentPi)
                    .addAction(android.R.drawable.checkbox_on_background, "✓ Complete", completePi)
                    .addAction(android.R.drawable.ic_lock_idle_alarm, "💤 Snooze 15m", snoozePi)
                    .setAutoCancel(true)
                    .build()

                nm.notify(notifId, notification)
            }

            NotificationScheduler.ACTION_COMPLETE_TASK_FROM_NOTIF -> {
                val taskId = intent.getStringExtra("task_id") ?: return
                val notifId = intent.getIntExtra("notif_id", (taskId.hashCode() and 0xFFFFFFF) + 500)
                val today = LocalDate.now()
                val todayStr = today.toString()

                val updatedTasks = state.tasks.map { task ->
                    if (task.id != taskId) task
                    else if (task.recurrence != RecurrenceType.NONE) {
                        val nextDate = RecurrenceEngine.calculateNextDueDate(task, today) ?: today.plusDays(1).toString()
                        task.copy(
                            dueDate = nextDate,
                            isMyDay = nextDate == todayStr,
                            myDayDate = if (nextDate == todayStr) todayStr else "",
                            steps = task.steps.map { it.copy(isDone = false) },
                            isCompleted = false
                        )
                    } else {
                        task.copy(isCompleted = true)
                    }
                }
                val nextState = state.copy(tasks = updatedTasks)
                HabitOSStorage.save(context, nextState)
                NotificationScheduler.scheduleAllAlarms(context, nextState)
                nm.cancel(notifId)
            }

            NotificationScheduler.ACTION_SNOOZE_TASK_FROM_NOTIF -> {
                val taskId = intent.getStringExtra("task_id") ?: return
                val taskTitle = intent.getStringExtra("task_title") ?: "Scheduled Task"
                val taskPrio = intent.getStringExtra("task_prio") ?: "P4"
                val notifId = intent.getIntExtra("notif_id", (taskId.hashCode() and 0xFFFFFFF) + 500)
                nm.cancel(notifId)
                NotificationScheduler.scheduleSnoozeAlarm(context, taskId, taskTitle, taskPrio, 15)
            }
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val state = HabitOSStorage.load(context)
            NotificationScheduler.scheduleAllAlarms(context, state)
        }
    }
}