package com.dayflow.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dayflow.R
import kotlinx.coroutines.launch

private data class TourPage(
    val icon: ImageVector?,
    val where: String,
    val title: String,
    val body: String,
    val tips: List<String>,
)

private fun pages(prayers: Boolean, cycle: Boolean): List<TourPage> = listOfNotNull(
    TourPage(
        null, "Welcome", "A quick look around",
        "Here's where everything lives in Dayflow. Swipe through, or skip and explore on your own.",
        listOf("Takes about a minute", "Replay it any time from Settings → App tour"),
    ),
    TourPage(
        Icons.Filled.WbSunny, "Today tab", "Your day at a glance",
        "Everything planned for today, with a progress ring that fills as you go.",
        listOf(
            "Tap + Add to create a task or a habit",
            "Star up to three tasks to make them today's priorities",
            "Start each morning with a short reflection",
        ),
    ),
    if (prayers) TourPage(
        Icons.Filled.Mosque, "Today tab · Prayers", "Your five daily prayers",
        "Mark Fajr, Dhuhr, Asr, Maghrib and Isha as on time, late or missed.",
        listOfNotNull(
            "Use ‹ › on the card to fill in earlier days",
            "The flame counts days in a row with all five prayed",
            "Tap History to see your prayers on a chart",
            if (cycle) "On logged period days, prayers are marked as excused" else null,
        ),
    ) else null,
    TourPage(
        Icons.Filled.Loop, "Habits tab", "Build habits that last",
        "Create habits for any days of the week, with a goal and an optional busy-day minimum.",
        listOf(
            "Tap a habit on Today to check it off",
            "Reaching the minimum keeps your streak going",
            "Need a break? Use ⋮ on Today to make it a rest day",
        ),
    ),
    TourPage(
        Icons.Filled.CalendarMonth, "Calendar tab", "Plan ahead",
        "See your week or month and move tasks to a day that fits.",
        listOf("Tap a day to see or add its tasks", "Use ⋮ on a task to reschedule it"),
    ),
    TourPage(
        Icons.Filled.Timer, "Focus tab", "Deep work, one session at a time",
        "A focus timer with short and long breaks. Link it to a task to keep track of your time.",
        listOf("Sessions keep running when you leave the app", "See past sessions in Focus history"),
    ),
    TourPage(
        Icons.Filled.Insights, "Progress tab", "See how far you've come",
        "Daily completion, habit rates and a heatmap of your consistency.",
        listOf("Switch between week and month", "Small steps add up"),
    ),
    if (cycle) TourPage(
        Icons.Filled.WaterDrop, "Cycle tab", "Know your cycle",
        "Log your period and symptoms. Dayflow predicts your next period and PMS days.",
        listOf("Everything stays private on this phone", "Lock it with your fingerprint in Settings"),
    ) else null,
    TourPage(
        Icons.Filled.Settings, "Settings (gear icon on Today)", "Make it yours",
        "Change themes, reminders and which features are turned on.",
        listOf(
            "Turn prayer or period tracking on or off",
            "Lock the app with your fingerprint or PIN",
            "Export your data any time",
        ),
    ),
)

/** A swipeable guide for first-time users. Pages for features that are turned off are left out. */
@Composable
fun TourScreen(prayers: Boolean, cycle: Boolean, onFinish: () -> Unit) {
    val pages = pages(prayers, cycle)
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${pager.currentPage + 1} of ${pages.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp).weight(1f),
                )
                if (!last) TextButton(onClick = onFinish) { Text("Skip tour") }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { i -> Page(pages[i]) }
            Dots(pages.size, pager.currentPage)
            Button(
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (last) "Let's go" else "Next", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Page(page: TourPage) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(160.dp).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)),
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (page.icon == null) {
                Image(painterResource(R.drawable.logo_mark), contentDescription = "Dayflow logo", modifier = Modifier.size(100.dp))
            } else {
                Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
            }
        }
        Text(
            page.where.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Card(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                page.tips.forEach { tip ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(Modifier.padding(horizontal = 4.dp).size(width = width, height = 8.dp).background(color, CircleShape))
        }
    }
}
