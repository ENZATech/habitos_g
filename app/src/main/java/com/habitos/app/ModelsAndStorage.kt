package com.habitos.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

enum class Priority(val label: String, val shortLabel: String, val colorHex: Long) {
    P1("P1 Urgent", "P1", 0xFFF43F5E),   // Rose/Crimson
    P2("P2 High", "P2", 0xFFF59E0B),     // Amber/Orange
    P3("P3 Medium", "P3", 0xFF3B82F6),   // Blue
    P4("P4 Normal", "P4", 0xFFA8A29E)    // Muted Stone
}

enum class RecurrenceType(val label: String, val shortTag: String) {
    NONE("Does not repeat", "None"),
    DAILY("Every day", "Daily"),
    WEEKDAYS("Every weekday (Mon-Fri)", "Weekdays"),
    WEEKLY("Every week", "Weekly"),
    MONTHLY("Every month", "Monthly"),
    YEARLY("Every year", "Yearly"),
    AFTER_COMPLETION("Every X days after completion", "After Done")
}

enum class TaskSortOption(val label: String) {
    PRIORITY("Priority"),
    DUE_TIME("Time"),
    DURATION("Duration"),
    TITLE("A-Z")
}

enum class ReminderOffset(val minutes: Int?, val label: String) {
    NONE(null, "No reminder"),
    AT_TIME(0, "At due time"),
    MIN_15(15, "15m before"),
    MIN_30(30, "30m before"),
    HOUR_1(60, "1h before"),
    DAY_1(1440, "1 day before");

    companion object {
        fun fromMinutes(min: Int?): ReminderOffset =
            entries.find { it.minutes == min } ?: NONE
    }
}

data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false
)

data class TaskComment(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false,
    val priority: Priority = Priority.P4,
    val isImportant: Boolean = false,
    val isMyDay: Boolean = false,
    val myDayDate: String = "", // YYYY-MM-DD when added to My Day
    val dueDate: String? = null, // YYYY-MM-DD
    val dueTime: String? = null, // HH:mm
    val durationMinutes: Int? = null,
    val deadlineDate: String? = null, // YYYY-MM-DD
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val customRecurrenceDays: Int = 1,
    val reminderOffsetMinutes: Int? = null,
    val listId: String? = null,
    val cue: String = "", // e.g. "30m"
    val steps: List<SubTask> = emptyList(),
    val comments: List<TaskComment> = emptyList(),
    val labels: List<String> = emptyList(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    @Suppress("SENSELESS_COMPARISON")
    fun sanitized(): TaskItem {
        val safePrio = when {
            priority != null -> priority
            isImportant -> Priority.P1
            else -> Priority.P4
        }
        return copy(
            priority = safePrio,
            isImportant = isImportant || safePrio == Priority.P1,
            recurrence = if (recurrence == null) RecurrenceType.NONE else recurrence,
            customRecurrenceDays = if (customRecurrenceDays <= 0) 1 else customRecurrenceDays,
            myDayDate = myDayDate ?: "",
            cue = cue ?: "",
            steps = steps ?: emptyList(),
            comments = comments ?: emptyList(),
            labels = labels ?: emptyList(),
            notes = notes ?: ""
        )
    }
}

object RecurrenceEngine {
    fun calculateNextDueDate(task: TaskItem, today: LocalDate = LocalDate.now()): String? {
        val baseDate = runCatching {
            if (!task.dueDate.isNullOrBlank()) LocalDate.parse(task.dueDate) else today
        }.getOrDefault(today)

        val effectiveBase = if (baseDate.isBefore(today)) today else baseDate

        val nextDate = when (task.recurrence) {
            RecurrenceType.NONE -> return null
            RecurrenceType.DAILY -> effectiveBase.plusDays(1)
            RecurrenceType.WEEKDAYS -> {
                var cursor = effectiveBase.plusDays(1)
                while (cursor.dayOfWeek == DayOfWeek.SATURDAY || cursor.dayOfWeek == DayOfWeek.SUNDAY) {
                    cursor = cursor.plusDays(1)
                }
                cursor
            }
            RecurrenceType.WEEKLY -> effectiveBase.plusWeeks(1)
            RecurrenceType.MONTHLY -> effectiveBase.plusMonths(1)
            RecurrenceType.YEARLY -> effectiveBase.plusYears(1)
            RecurrenceType.AFTER_COMPLETION -> today.plusDays(task.customRecurrenceDays.toLong().coerceAtLeast(1L))
        }
        return nextDate.toString()
    }
}

object SmartTaskParser {
    data class ParsedTaskResult(
        val cleanTitle: String,
        val priority: Priority?,
        val dueDate: String?,
        val dueTime: String?,
        val durationMinutes: Int?,
        val recurrence: RecurrenceType?,
        val labels: List<String>
    )

    fun parse(rawInput: String, today: LocalDate = LocalDate.now()): ParsedTaskResult {
        var text = rawInput
        var priority: Priority? = null
        var dueDate: String? = null
        var dueTime: String? = null
        var durationMinutes: Int? = null
        var recurrence: RecurrenceType? = null
        val extractedLabels = mutableListOf<String>()

        // 1. Priority tokens: p1, p2, p3, p4
        val pRegex = Regex("\\b(p[1-4])\\b", RegexOption.IGNORE_CASE)
        val pMatch = pRegex.find(text)
        if (pMatch != null) {
            priority = when (pMatch.groupValues[1].lowercase(Locale.getDefault())) {
                "p1" -> Priority.P1
                "p2" -> Priority.P2
                "p3" -> Priority.P3
                else -> Priority.P4
            }
            text = text.replaceFirst(pRegex, "")
        }

        // 2. Duration tokens: "for 30m", "45m", "1h", "2h"
        val durRegex = Regex("\\b(?:for\\s+)?(\\d+)\\s*(m|min|mins|h|hr|hrs)\\b", RegexOption.IGNORE_CASE)
        val durMatch = durRegex.find(text)
        if (durMatch != null) {
            val amount = durMatch.groupValues[1].toIntOrNull() ?: 30
            val unit = durMatch.groupValues[2].lowercase(Locale.getDefault())
            durationMinutes = if (unit.startsWith("h")) amount * 60 else amount
            text = text.replaceFirst(durRegex, "")
        }

        // 3. Time tokens: "at 14:30", "at 5pm", "at 9am"
        val timeRegex = Regex("\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE)
        val timeMatch = timeRegex.find(text)
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: 9
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = timeMatch.groupValues[3].lowercase(Locale.getDefault())
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) {
                dueTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                text = text.replaceFirst(timeRegex, "")
            }
        }

        // 4. Recurrence phrases
        when {
            Regex("\\bevery\\s+weekday\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                recurrence = RecurrenceType.WEEKDAYS
                text = text.replace(Regex("\\bevery\\s+weekday\\b", RegexOption.IGNORE_CASE), "")
            }
            Regex("\\bevery\\s+day\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                recurrence = RecurrenceType.DAILY
                text = text.replace(Regex("\\bevery\\s+day\\b", RegexOption.IGNORE_CASE), "")
            }
            Regex("\\bevery\\s+week\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                recurrence = RecurrenceType.WEEKLY
                text = text.replace(Regex("\\bevery\\s+week\\b", RegexOption.IGNORE_CASE), "")
            }
            Regex("\\bevery\\s+month\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                recurrence = RecurrenceType.MONTHLY
                text = text.replace(Regex("\\bevery\\s+month\\b", RegexOption.IGNORE_CASE), "")
            }
            Regex("\\bevery\\s+year\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                recurrence = RecurrenceType.YEARLY
                text = text.replace(Regex("\\bevery\\s+year\\b", RegexOption.IGNORE_CASE), "")
            }
        }

        // 5. Date keywords: today, tomorrow, next week
        when {
            Regex("\\bnext\\s+week\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                dueDate = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()
                text = text.replace(Regex("\\bnext\\s+week\\b", RegexOption.IGNORE_CASE), "")
            }
            Regex("\\btomorrow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                dueDate = today.plusDays(1).toString()
                text = text.replace(Regex("\\btomorrow\\b", RegexOption.IGNORE_CASE), "")
            }
            Regex("\\btoday\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                dueDate = today.toString()
                text = text.replace(Regex("\\btoday\\b", RegexOption.IGNORE_CASE), "")
            }
        }

        // 6. Labels: @tag
        val labelRegex = Regex("@([a-zA-Z0-9_-]+)")
        labelRegex.findAll(text).forEach { match ->
            extractedLabels.add(match.groupValues[1])
        }
        text = text.replace(labelRegex, "")

        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        return ParsedTaskResult(
            cleanTitle = cleaned.ifBlank { rawInput.trim() },
            priority = priority,
            dueDate = dueDate,
            dueTime = dueTime,
            durationMinutes = durationMinutes,
            recurrence = recurrence,
            labels = extractedLabels.distinct()
        )
    }
}

data class HabitItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val listId: String? = null,
    val cue: String = "",
    val completedDates: Set<String> = emptySet(), // Set of YYYY-MM-DD
    val recordStreak: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun currentStreak(today: LocalDate = LocalDate.now()): Int {
        var streak = 0
        var cursor = if (completedDates.contains(today.toString())) today else today.minusDays(1)
        while (completedDates.contains(cursor.toString())) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun updatedWithToggle(dateStr: String): HabitItem {
        val nextSet = completedDates.toMutableSet()
        if (nextSet.contains(dateStr)) nextSet.remove(dateStr) else nextSet.add(dateStr)
        val temp = copy(completedDates = nextSet)
        val cur = temp.currentStreak()
        return temp.copy(recordStreak = maxOf(recordStreak, cur))
    }
}

data class BirthdayPerson(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relation: String = "Friend", // Stored privately for custom wishes
    val dayOfMonth: Int,
    val monthValue: Int, // 1..12
    val birthYear: Int = 2008,
    val phone: String = "",
    val customWish: String? = null, // Dedicated custom wish for this specific person
    val wishedYear: Int = 0
) {
    fun hasCustomWish(): Boolean = !customWish.isNullOrBlank()

    fun isBirthdayOn(date: LocalDate): Boolean =
        date.monthValue == monthValue && date.dayOfMonth == dayOfMonth

    fun daysUntilNext(today: LocalDate = LocalDate.now()): Long {
        var next = LocalDate.of(
            today.year,
            monthValue,
            dayOfMonth.coerceAtMost(safeDayOfMonth(today.year, monthValue, dayOfMonth))
        )
        if (next.isBefore(today)) {
            val nextYear = today.year + 1
            next = LocalDate.of(
                nextYear,
                monthValue,
                dayOfMonth.coerceAtMost(safeDayOfMonth(nextYear, monthValue, dayOfMonth))
            )
        }
        return ChronoUnit.DAYS.between(today, next)
    }

    fun turningAge(today: LocalDate = LocalDate.now()): Int? {
        if (birthYear !in 1900..today.year) return null
        var nextYear = today.year
        val thisYearBday = LocalDate.of(
            today.year,
            monthValue,
            dayOfMonth.coerceAtMost(safeDayOfMonth(today.year, monthValue, dayOfMonth))
        )
        if (thisYearBday.isBefore(today.minusDays(1))) {
            nextYear += 1
        }
        return (nextYear - birthYear).coerceAtLeast(0)
    }

    private fun safeDayOfMonth(year: Int, month: Int, day: Int): Int {
        return if (month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong())) 28 else day
    }
}

object BirthdayWishTemplates {
    const val TEMPLATE_COUNT = 6

    fun generateWish(person: BirthdayPerson, isBelated: Boolean, variantIndex: Int = 0): String {
        val rel = person.relation.ifBlank { "Friend" }
        val name = person.name
        val agePart = person.turningAge()?.let { " (turning $it!)" } ?: ""

        if (isBelated) {
            val belatedList = when (rel) {
                "Brother" -> listOf(
                    "Hey bro $name! 🚨 Happy Belated Birthday! Sorry I missed it yesterday—wishing my brother non-stop success, strength, and an awesome year ahead! 🎂🔥",
                    "Happy Belated Birthday $name bhai! 🎉 Running a day late, but you know you're always #1. Hope you had a killer celebration! 👑✨",
                    "Belated Happy Birthday bro $name! 💪 Sorry for the late text, hope yesterday was legendary. Let's catch up and celebrate properly soon! 🚀🎂",
                    "Yo $name! 🎂 A day late, but the brotherhood is forever! Wishing you massive wins, top health, and unstoppable energy this year! 🔥🙌",
                    "Happy Belated Birthday to my brother $name! 🏆 Hope your special day was packed with great food, laughs, and power moves! 🎉✨",
                    "Sorry I'm late bro $name! 🚨 Wishing you a Belated Happy Birthday filled with new milestones, happiness, and peace! 🎂👑"
                )
                "Sister" -> listOf(
                    "Happy Belated Birthday to the best sister ever, $name! 🚨🎂 Sorry I'm a day late—hope your day was as special and bright as you are! 💖✨",
                    "Hey $name! 🌸 Belated Happy Birthday sis! Wishing you endless happiness, big achievements, and smiles all year round! 🎉",
                    "So sorry I'm a day late, $name! 🎂💖 Happy Belated Birthday to my amazing sister—may all your biggest dreams come true this year! ✨🌟",
                    "Belated Happy Birthday sis $name! 🥳 Hope you were spoiled with cake and gifts yesterday. Owe you a treat soon! 🍰💝",
                    "Sending the warmest Belated Birthday wishes to my sister $name! 🌸 Stay fearless, keep shining, and have a magical year ahead! 🎉✨",
                    "Happy Belated Birthday $name! 💫 Even a day late, I'm wishing my sister double the joy, success, and laughter this year! 🎂💖"
                )
                "Best Friend" -> listOf(
                    "Yo $name! 🚨 Worst best friend award goes to me for being a day late 😂 Happy Belated Birthday legend! Let's celebrate soon! 🎂🔥",
                    "Happy Belated Birthday to my ride-or-die $name! 🎉 Sorry for the late text, hope you had an unforgettable blast yesterday! 🚀✨",
                    "Bro $name! 💀 I'm officially late, which means the party isn't over yet! Happy Belated Birthday to my absolute best friend! 🎂🥳",
                    "Belated Happy Birthday $name! 🏆 Sorry for the delay, hope yesterday was epic—here's to another year of crazy memories together! 🔥🙌",
                    "Hey $name! 🚨 Better late than never! Happy Belated Birthday bestie, wishing you big wins, zero stress, and non-stop fun! 🎉✨",
                    "Happy Belated Birthday $name! 🎂 Sorry I missed the clock yesterday, but you know I've always got your back. Let's hang out soon! 🚀💥"
                )
                "Mother", "Father" -> listOf(
                    "Happy Belated Birthday $name! 🙏🎂 Sorry for the delay—wishing you wonderful health, peace, and endless happiness always! ❤️✨",
                    "Warmest Belated Birthday wishes to you, $name! 🌸 Thank you for everything you do—wishing you a healthy and blessed year ahead! 🙏",
                    "Happy Belated Birthday $name! ❤️ So grateful for your love and guidance every day. May this year bring you good health and joy! 🎂✨",
                    "Wishing you a very Happy Belated Birthday, $name! 🙏🌸 Hope your day yesterday was peaceful, joyful, and surrounded by love! ❤️",
                    "Belated Birthday blessings to you, $name! 🎂 Thank you for always being our biggest support—wishing you a long, happy, and healthy life! 🙏✨",
                    "Happy Belated Birthday $name! ❤️ Sorry for the late wish, sending you lots of love, respect, and warm wishes for the year ahead! 🌸🎂"
                )
                "Partner" -> listOf(
                    "Happy Belated Birthday my favorite person, $name! ❤️🎂 So sorry I'm a day late, let me make it up to you! Wishing you all the love in the world! ✨",
                    "Hey $name 💖 Belated Happy Birthday! Hope yesterday was magical—here's to making amazing memories together this year! 🥂✨",
                    "Happy Belated Birthday $name! 🌹 Even a day late, my wish for you is endless happiness, love, and every dream coming true! ❤️🎂",
                    "So sorry for the late message, $name! 💖 Happy Belated Birthday—you deserve the absolute best today and every single day! ✨🥂",
                    "Belated Happy Birthday to the one who makes every day brighter, $name! ❤️ Let's celebrate properly this week! 🎂💫",
                    "Happy Belated Birthday $name! 💝 Sending you extra love today to make up for yesterday—wishing you a truly unforgettable year! 🌸✨"
                )
                else -> listOf(
                    "Hey $name! 🚨 Happy Belated Birthday! Sorry I'm a day late, hope you had an incredible celebration and wishing you an amazing year ahead! 🎂✨",
                    "Belated Happy Birthday $name! 🎉 Hope your special day yesterday was packed with fun, laughter, and great memories! 🙌✨",
                    "Happy Belated Birthday $name! 🥳 Running a day behind, but wishing you a fantastic year full of success, good health, and big wins! 🎂🔥",
                    "Hey $name! 🎂 Sorry for the late wish—hope you had an awesome birthday yesterday and have an even better year ahead! ✨🚀",
                    "Wishing you a very Happy Belated Birthday, $name! 🎉 May the coming year bring you exciting opportunities and non-stop happiness! 🙌🌟",
                    "Belated Happy Birthday $name! 🍰 Hope you enjoyed your day to the fullest yesterday—cheers to a great year ahead! 🥳✨"
                )
            }
            return belatedList[variantIndex.mod(belatedList.size)]
        } else {
            val onTimeList = when (rel) {
                "Brother" -> listOf(
                    "Happy Birthday bro $name!$agePart 🎂🔥 Wishing my brother a power-packed year full of big wins, good health, and unstoppable hustle! 👑🚀",
                    "Many happy returns of the day, $name bhai! 🎉 Proud to have you as my brother—keep shining and crushing your goals! 💪✨",
                    "Happy Birthday to my brother $name!$agePart 🏆 May this year bring you massive success, strength, and legendary memories! 🎂⚡",
                    "Yo $name bro! 🎉 Happy Birthday! Stay fearless, keep levelling up, and have an absolute blast today! 🔥👑",
                    "Wishing the happiest birthday to my brother $name!$agePart 🙌 Always got your back—here's to a year of big achievements! 🎂🚀",
                    "Happy Birthday $name bhai! 🥳 Hope today is filled with great food, good vibes, and everything you've been working toward! 💪✨"
                )
                "Sister" -> listOf(
                    "Happy Birthday to my amazing sister $name!$agePart 🎂💖 May your year be filled with joy, big dreams coming true, and endless smiles! 🌸✨",
                    "Wishing the happiest birthday to my sister $name! 🎉 Stay awesome, keep smiling, and have the best celebration today! 👑💫",
                    "Happy Birthday sis $name!$agePart 🌟 You bring positivity everywhere you go—wishing you a magical and successful year ahead! 🎂💖",
                    "Many happy returns of the day to the coolest sister, $name! 🥳 May this year bring you exciting adventures and pure happiness! 🌸🎉",
                    "Happy Birthday $name! 💝 So lucky to have a sister like you—hope your special day is packed with cake, gifts, and laughter! 🎂✨",
                    "Wishing my wonderful sister $name a very Happy Birthday!$agePart 👑 Keep chasing your dreams and shining bright! 🌟💖"
                )
                "Best Friend" -> listOf(
                    "Happy Birthday to my absolute legend of a best friend, $name!$agePart 🎂🔥 Here's to another year of crazy memories and big wins together! 🚀🎉",
                    "Yo $name! 🎂 Happy Birthday bestie! Wishing you top-tier success, good vibes, and an unforgettable party today! 🏆✨",
                    "Happy Birthday $name!$agePart 🙌 Life would be boring without a best friend like you—keep killing it and let's celebrate soon! 🔥🥳",
                    "Wishing a massive Happy Birthday to my ride-or-die, $name! 🎉 May this year bring you everything you've been grinding for! 🚀👑",
                    "Happy Birthday best friend $name!$agePart 🎂 Thanks for always having my back—hope your day today is 10/10! 💯✨",
                    "Cheers to another year of chaotic fun and big goals, $name! 🥳 Happy Birthday legend, have an epic day! 🎂🔥"
                )
                "Mother", "Father" -> listOf(
                    "Happy Birthday $name! 🙏🎂 Wishing you great health, peace, and boundless happiness today and always. Thank you for being my pillar of strength! ❤️✨",
                    "Warmest Birthday wishes to you, $name! 🌸 May this year bring you wonderful health, joy, and countless blessings! 🙏🎉",
                    "Happy Birthday $name! ❤️ Thank you for your endless support, wisdom, and love—wishing you a truly peaceful and happy year ahead! 🎂✨",
                    "Many happy returns of the day, $name! 🙏🌸 May you always be blessed with good health, long life, andConstant smiles! ❤️",
                    "Wishing a very Happy Birthday to the most inspiring person in my life, $name! 🎂 Thank you for everything you do for us! 🙏✨",
                    "Happy Birthday $name! ❤️ Hope your special day today is filled with warmth, relaxation, and all your favorite things! 🌸🎉"
                )
                "Partner" -> listOf(
                    "Happy Birthday to my favorite person, $name!$agePart ❤️🎂 You make every single day brighter—wishing you all the happiness and love in the world! ✨🥂",
                    "Happy Birthday $name! 💖 So grateful to have you in my life. Hope today brings you as much joy as you bring me! 🎂🌹",
                    "Wishing the happiest birthday to someone truly special, $name!$agePart ❤️ May all your wishes come true this year! ✨💫",
                    "Happy Birthday $name! 🥰 Here's to celebrating you today and making unforgettable memories together all year long! 🎂🥂",
                    "To my favorite human, $name—Happy Birthday! 💖 Keep shining, keep smiling, and have the most magical day today! 🌸✨",
                    "Happy Birthday $name!$agePart ❤️ Every moment with you is a gift—wishing you endless love, success, and happiness! 🎂🌟"
                )
                else -> listOf(
                    "Happy Birthday $name!$agePart 🎂🎉 Wishing you an awesome year ahead filled with great health, big wins, and happiness! ✨",
                    "Many happy returns of the day, $name! 🥳 Hope your birthday today is packed with great vibes, laughter, and success! 🙌🎂",
                    "Happy Birthday $name!$agePart 🚀 May this new year of your life bring exciting opportunities, peace, and nonstop joy! 🎉✨",
                    "Wishing you a fantastic Birthday, $name! 🎂 Stay awesome, keep crushing your goals, and enjoy your special day to the fullest! 🔥🙌",
                    "Happy Birthday $name! 🌟 Hope today brings you great memories, good food, and a brilliant start to the year ahead! 🍰🥳",
                    "Cheers to you on your birthday, $name!$agePart 🎉 Wishing you 365 days of good health, positivity, and big achievements! 🎂✨"
                )
            }
            return onTimeList[variantIndex.mod(onTimeList.size)]
        }
    }
}

object DateFormatHelper {
    private val commentFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())

    fun shortDate(dateStr: String?, today: LocalDate = LocalDate.now()): String {
        if (dateStr.isNullOrBlank()) return ""
        val parsed = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return dateStr
        return when (parsed) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> "${parsed.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${parsed.dayOfMonth}"
        }
    }

    fun formatTimeBlock(dueTime: String?, durationMinutes: Int?): String? {
        val hasTime = !dueTime.isNullOrBlank()
        val hasDur = durationMinutes != null && durationMinutes > 0
        if (!hasTime && !hasDur) return null

        val durLabel = if (hasDur) formatWorkload(durationMinutes!!) else ""
        if (hasTime && hasDur) {
            val start = runCatching { LocalTime.parse(dueTime) }.getOrNull()
            if (start != null) {
                val end = start.plusMinutes(durationMinutes!!.toLong())
                val endStr = String.format(Locale.getDefault(), "%02d:%02d", end.hour, end.minute)
                return "🕒 $dueTime–$endStr ($durLabel)"
            }
            return "🕒 $dueTime ($durLabel)"
        }
        return if (hasTime) "🕒 $dueTime" else "⏱️ $durLabel"
    }

    fun formatWorkload(totalMinutes: Int): String {
        if (totalMinutes <= 0) return "0m"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun deadlineBadgeInfo(deadlineStr: String?, today: LocalDate = LocalDate.now()): Pair<String, Boolean>? {
        if (deadlineStr.isNullOrBlank()) return null
        val parsed = runCatching { LocalDate.parse(deadlineStr) }.getOrNull() ?: return null
        val days = ChronoUnit.DAYS.between(today, parsed)
        return when {
            days < 0 -> "🚨 ${-days}d overdue" to true
            days == 0L -> "🚨 Due Today" to true
            days == 1L -> "🎯 1d left" to true
            else -> "🎯 ${days}d left" to (days <= 2)
        }
    }

    fun formatCommentTime(millis: Long): String {
        return runCatching {
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(commentFormatter)
        }.getOrDefault("")
    }
}

data class CustomTaskList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: Long = 0xFF10B981
)

data class UserProfile(
    val isOnboarded: Boolean = false,
    val name: String = "",
    val dob: String = "",
    val gender: String = "",
    val avatarSeed: Int = Random.nextInt(0, 6),
    val permissionsPromptShown: Boolean = false
)

data class AppSettings(
    val vaultPin: String = "1234",
    val eodHour: Int = 21,
    val eodMinute: Int = 0
)

data class PersistedState(
    val tasks: List<TaskItem> = emptyList(),
    val habits: List<HabitItem> = emptyList(),
    val birthdays: List<BirthdayPerson> = emptyList(),
    val customLists: List<CustomTaskList> = listOf(
        CustomTaskList(id = "default_routine", name = "Daily Routine & Mindfulness", colorHex = 0xFF10B981),
        CustomTaskList(id = "default_code", name = "C Language & Web Dev", colorHex = 0xFF3B82F6),
        CustomTaskList(id = "default_fitness", name = "Fitness & Health", colorHex = 0xFFF43F5E),
        CustomTaskList(id = "default_goals", name = "Personal & Goals", colorHex = 0xFFA855F7)
    ),
    val userProfile: UserProfile = UserProfile(),
    val settings: AppSettings = AppSettings()
)

object HabitOSStorage {
    private const val PREF_NAME = "habitos_native_vault"
    private const val KEY_STATE = "persisted_state_v2"
    private val gson = Gson()

    fun load(context: Context): PersistedState {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null) ?: return PersistedState()
        return try {
            val type = object : TypeToken<PersistedState>() {}.type
            val loaded: PersistedState? = gson.fromJson(raw, type)
            if (loaded != null) {
                loaded.copy(tasks = (loaded.tasks ?: emptyList()).map { it.sanitized() })
            } else {
                PersistedState()
            }
        } catch (e: Exception) {
            PersistedState()
        }
    }

    fun save(context: Context, state: PersistedState) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STATE, gson.toJson(state)).apply()
    }

    fun clearAllResettingKeepPinAndProfile(
        context: Context,
        currentPin: String,
        currentProfile: UserProfile
    ): PersistedState {
        val clean = PersistedState(
            userProfile = currentProfile,
            settings = AppSettings(vaultPin = currentPin)
        )
        save(context, clean)
        return clean
    }
}

object WeekHelper {
    fun currentWeekDays(today: LocalDate = LocalDate.now()): List<LocalDate> =
        weekDaysForOffset(0, today)

    fun weekDaysForOffset(weekOffset: Int, today: LocalDate = LocalDate.now()): List<LocalDate> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(weekOffset.toLong())
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    fun isPerfectDay(habits: List<HabitItem>, date: LocalDate): Boolean {
        if (habits.isEmpty()) return false
        val key = date.toString()
        return habits.all { it.completedDates.contains(key) }
    }

    fun consistencyStreakDays(habits: List<HabitItem>, today: LocalDate = LocalDate.now()): Int {
        if (habits.isEmpty()) return 0
        var streak = 0
        fun hit65(d: LocalDate): Boolean {
            val done = habits.count { it.completedDates.contains(d.toString()) }
            return (done.toDouble() / habits.size.toDouble()) >= 0.65
        }
        var cursor = if (hit65(today)) today else today.minusDays(1)
        while (hit65(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}