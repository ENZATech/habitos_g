package com.habitos.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitMatrixScreen(
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

    val perfectDays = weekDays.count { WeekHelper.isPerfectDay(state.habits, it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("7-Day Habit Matrix", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("🔒 Today-only check-in lock", fontSize = 11.sp, color = EmeraldPrimary)
                }
                Surface(color = ObsidianCard, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, AmberStreak.copy(alpha = 0.6f))) {
                    Text("👑 $perfectDays Perfect", color = AmberStreak, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }

        item {
            WeeklyCompletionBarChart(habits = state.habits, weekDays = weekDays, today = today, weekOffset = weekOffset) { weekOffset = it }
        }

        item {
            Surface(color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = newHabitName,
                        onValueChange = { newHabitName = it },
                        singleLine = true,
                        textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(EmeraldPrimary),
                        decorationBox = { inner ->
                            if (newHabitName.isEmpty()) Text("New daily habit (e.g. Code 1 hr)...", color = TextMuted, fontSize = 14.sp)
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
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = ObsidianBg)
                    }
                }
            }
        }

        if (state.habits.isEmpty()) {
            item {
                EmptyStateCard(icon = Icons.Default.Add, title = "0 Habits", subtitle = "Add a habit above to unlock streaks and crowns.")
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
                            val msg = if (clickedDate.isBefore(today)) "🔒 Past days are locked to protect streak integrity."
                            else "🔒 Future days cannot be marked in advance!"
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
    val rangeLabel = "${startDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${startDay.dayOfMonth} – ${endDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${endDay.dayOfMonth}"

    Surface(color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Weekly Trend", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                val totalPossible = (habits.size * 7).coerceAtLeast(1)
                val totalDone = weekDays.sumOf { d -> habits.count { it.completedDates.contains(d.toString()) } }
                val pct = if (habits.isEmpty()) 0 else (totalDone * 100) / totalPossible
                Surface(color = EmeraldPrimary.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                    Text("$pct% • ${if (weekOffset == 0) "This Week" else rangeLabel}", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(color = ObsidianNav, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(" ◀ ", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onChangeWeekOffset(weekOffset - 1) }.padding(horizontal = 8.dp, vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rangeLabel, color = AmberStreak, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (weekOffset != 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = EmeraldPrimary.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp), modifier = Modifier.clickable { onChangeWeekOffset(0) }) {
                                Text("Today", color = EmeraldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text(" ▶ ", color = if (weekOffset < 0) TextPrimary else TextMuted.copy(alpha = 0.35f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = weekOffset < 0) { onChangeWeekOffset(weekOffset + 1) }.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                weekDays.forEach { day ->
                    val doneCount = habits.count { it.completedDates.contains(day.toString()) }
                    val ratio = if (habits.isEmpty()) 0f else doneCount.toFloat() / habits.size.toFloat()
                    val isPerfect = WeekHelper.isPerfectDay(habits, day)
                    val isToday = day == today

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(1f)) {
                        Text(if (isPerfect) "👑" else "${(ratio * 100).toInt()}%", fontSize = 10.sp, color = if (isPerfect) AmberStreak else TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Box(modifier = Modifier.width(22.dp).height(48.dp).clip(RoundedCornerShape(6.dp)).background(ObsidianNav), contentAlignment = Alignment.BottomCenter) {
                            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(ratio.coerceIn(0.06f, 1f)).clip(RoundedCornerShape(6.dp)).background(if (isPerfect) Brush.verticalGradient(listOf(AmberStreak, EmeraldPrimary)) else Brush.verticalGradient(listOf(EmeraldPrimary, TealSecondary))))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3), fontSize = 11.sp, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal, color = if (isToday) EmeraldPrimary else TextMuted)
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
    val streak = habit.currentStreak(today)
    Surface(color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(habit.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🔥 Streak: ${streak}d", color = AmberStreak, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("🏆 Record: ${maxOf(habit.recordStreak, streak)}d", color = EmeraldPrimary, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert) }
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekDays.forEach { date ->
                    val checked = habit.completedDates.contains(date.toString())
                    val isToday = date == today
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onToggleDate(date) }.alpha(if (isToday || checked) 1f else 0.45f).padding(4.dp)
                    ) {
                        Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), fontSize = 11.sp, color = if (isToday) EmeraldPrimary else TextMuted, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(if (checked) EmeraldPrimary else ObsidianNav).border(width = if (isToday) 1.5.dp else 1.dp, color = if (checked) EmeraldPrimary else if (isToday) AmberStreak else ObsidianBorder, shape = RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(18.dp))
                            else Text("${date.dayOfMonth}", color = if (isToday) TextPrimary else TextMuted, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}