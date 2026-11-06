package com.dayflow.ui.reflection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.data.db.ReflectionEntity
import com.dayflow.domain.Mood
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.PhotoImage
import com.dayflow.ui.components.SectionHeader
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/** Fixed mood colors so the chart reads the same in every theme. */
val MoodColors = mapOf(
    Mood.HAPPY to Color(0xFFF2B33D),
    Mood.CALM to Color(0xFF8BA33A),
    Mood.MOTIVATED to Color(0xFF4F6BED),
    Mood.TIRED to Color(0xFF9E8FB5),
    Mood.SAD to Color(0xFF7A4A3F),
    Mood.ANXIOUS to Color(0xFF7D766A),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionHistoryScreen(
    onBack: () -> Unit,
    onOpen: (LocalDate) -> Unit,
    vm: ReflectionHistoryViewModel = viewModel(factory = ReflectionHistoryViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Morning reflections") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onOpen(today) }, icon = { Icon(Icons.Filled.Edit, null) }, text = { Text("Today's reflection") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.last30Count > 0) {
                item(key = "moods") { MoodChart(state) }
            }
            if (!state.loading && state.entries.isEmpty()) {
                item { EmptyState("No reflections yet", "Each morning, note how you feel, what you're grateful for, and your main intention for the day.") }
            }
            if (state.entries.isNotEmpty()) item(key = "header") { SectionHeader("All entries") }
            items(state.entries, key = { it.date }) { r -> EntryCard(r, today, vm::photoFile) { onOpen(LocalDate.ofEpochDay(r.date)) } }
        }
    }
}

@Composable
private fun MoodChart(state: ReflectionHistoryState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text("Moods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Last 30 days · ${state.last30Count} reflections",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Bars are scaled to the most common mood so differences are visible; labels show the true share.
            val maxShare = state.moodShare.maxOf { it.second }.coerceAtLeast(0.01f)
            Row(Modifier.fillMaxWidth().height(160.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.moodShare.forEach { (mood, share) ->
                    val percent = (share * 100).roundToInt()
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .semantics { contentDescription = "${mood.label}: $percent percent" },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (share > 0f) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight((share / maxShare).coerceIn(0.2f, 1f))
                                    .clip(RoundedCornerShape(50)).background(MoodColors.getValue(mood)),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Text("$percent%", Modifier.padding(bottom = 8.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.moodShare.forEach { (mood, _) ->
                    Text(
                        "${mood.emoji}\n${mood.label}",
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryCard(r: ReflectionEntity, today: LocalDate, photoFile: (String) -> File, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        r.photoPath?.let { name ->
            PhotoImage(photoFile(name), contentDescription = null, modifier = Modifier.fillMaxWidth().height(160.dp), maxSide = 800)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.relative(LocalDate.ofEpochDay(r.date), today), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                r.mood?.let { Text("${it.emoji} ${it.label}", style = MaterialTheme.typography.bodySmall) }
                r.energy?.let { Text("  ·  Energy $it/5", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (r.intention.isNotBlank()) Text("Intention: ${r.intention}", style = MaterialTheme.typography.bodyMedium)
            if (r.gratitude.isNotBlank()) {
                Text("Grateful for: ${r.gratitude}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (r.notes.isNotBlank()) {
                Text(r.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
