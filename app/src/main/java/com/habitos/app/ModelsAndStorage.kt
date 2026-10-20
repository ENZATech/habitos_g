package com.habitos.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.random.Random

data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false
)

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false,
    val isImportant: Boolean = false,
    val isMyDay: Boolean = false,
    val myDayDate: String = "", // YYYY-MM-DD when added to My Day
    val dueDate: String? = null, // YYYY-MM-DD
    val listId: String? = null,
    val cue: String = "", // e.g. "30m"
    val steps: List<SubTask> = emptyList(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

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
    val relation: String = "Friend", // Brother, Sister, Best Friend, Friend, Mother, Father, Partner, Cousin
    val dayOfMonth: Int,
    val monthValue: Int, // 1..12
    val birthYear: Int = 2008, // Year of Birth
    val phone: String = "",
    val wishedYear: Int = 0
) {
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
    fun generateWish(person: BirthdayPerson, isBelated: Boolean, variantIndex: Int = 0): String {
        val rel = person.relation.ifBlank { "Friend" }
        val name = person.name
        val agePart = person.turningAge()?.let { " (turning $it!)" } ?: ""

        if (isBelated) {
            val belatedList = when (rel) {
                "Brother" -> listOf(
                    "Hey bro $name! 🚨 Happy Belated Birthday! Sorry I missed it yesterday—wishing my brother non-stop success, strength, and an awesome year ahead! 🎂🔥",
                    "Happy Belated Birthday $name bhai! 🎉 Running a day late, but you know you're always #1. Hope you had a killer celebration! 👑✨"
                )
                "Sister" -> listOf(
                    "Happy Belated Birthday to the best sister ever, $name! 🚨🎂 Sorry I'm a day late—hope your day was as special and bright as you are! 💖✨",
                    "Hey $name! 🌸 Belated Happy Birthday sis! Wishing you endless happiness, big achievements, and smiles all year round! 🎉"
                )
                "Best Friend" -> listOf(
                    "Yo $name! 🚨 Worst best friend award goes to me for being a day late 😂 Happy Belated Birthday legend! Let's celebrate soon! 🎂🔥",
                    "Happy Belated Birthday to my ride-or-die $name! 🎉 Sorry for the late text, hope you had an unforgettable blast yesterday! 🚀✨"
                )
                "Mother", "Father" -> listOf(
                    "Happy Belated Birthday $name! 🙏🎂 Sorry for the delay—wishing you wonderful health, peace, and endless happiness always! ❤️✨",
                    "Warmest Belated Birthday wishes to you, $name! 🌸 Thank you for everything you do—wishing you a healthy and blessed year ahead! 🙏"
                )
                "Partner" -> listOf(
                    "Happy Belated Birthday my favorite person, $name! ❤️🎂 So sorry I'm a day late, let me make it up to you! Wishing you all the love in the world! ✨",
                    "Hey $name 💖 Belated Happy Birthday! Hope yesterday was magical—here's to making amazing memories together this year! 🥂✨"
                )
                else -> listOf(
                    "Hey $name! 🚨 Happy Belated Birthday! Sorry I'm a day late, hope you had an incredible celebration and wishing you an amazing year ahead! 🎂✨",
                    "Belated Happy Birthday $name! 🎉 Hope your special day yesterday was packed with fun, laughter, and great memories! 🙌✨"
                )
            }
            return belatedList[variantIndex.mod(belatedList.size)]
        } else {
            val onTimeList = when (rel) {
                "Brother" -> listOf(
                    "Happy Birthday bro $name!$agePart 🎂🔥 Wishing my brother a power-packed year full of big wins, good health, and unstoppable hustle! 👑🚀",
                    "Many happy returns of the day, $name bhai! 🎉 Proud to have you as my brother—keep shining and crushing your goals! 💪✨"
                )
                "Sister" -> listOf(
                    "Happy Birthday to my amazing sister $name!$agePart 🎂💖 May your year be filled with joy, big dreams coming true, and endless smiles! 🌸✨",
                    "Wishing the happiest birthday to my sister $name! 🎉 Stay awesome, keep smiling, and have the best celebration today! 👑💫"
                )
                "Best Friend" -> listOf(
                    "Happy Birthday to my absolute legend of a best friend, $name!$agePart 🎂🔥 Here's to another year of crazy memories and big wins together! 🚀🎉",
                    "Yo $name! 🎂 Happy Birthday bestie! Wishing you top-tier success, good vibes, and an unforgettable party today! 🏆✨"
                )
                "Mother", "Father" -> listOf(
                    "Happy Birthday $name! 🙏🎂 Wishing you great health, peace, and boundless happiness today and always. Thank you for being my pillar of strength! ❤️✨",
                    "Warmest Birthday wishes to you, $name! 🌸 May this year bring you wonderful health, joy, and countless blessings! 🙏🎉"
                )
                "Partner" -> listOf(
                    "Happy Birthday to my favorite person, $name!$agePart ❤️🎂 You make every single day brighter—wishing you all the happiness and love in the world! ✨🥂",
                    "Happy Birthday $name! 💖 So grateful to have you in my life. Hope today brings you as much joy as you bring me! 🎂🌹"
                )
                else -> listOf(
                    "Happy Birthday $name!$agePart 🎂🎉 Wishing you an awesome year ahead filled with great health, big wins, and happiness! ✨",
                    "Many happy returns of the day, $name! 🥳 Hope your birthday today is packed with great vibes, laughter, and success! 🙌🎂"
                )
            }
            return onTimeList[variantIndex.mod(onTimeList.size)]
        }
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
            loaded ?: PersistedState()
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
    fun currentWeekDays(today: LocalDate = LocalDate.now()): List<LocalDate> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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