package com.dayflow.ui.goals

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.GoalStatus
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.ConfirmDialog
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    onAddGoal: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onEditGoal: (Long) -> Unit,
    vm: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GoalSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goals") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddGoal, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New goal") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.loading && state.isEmpty) {
                item {
                    EmptyState(
                        "No goals yet",
                        "A goal is what you're working toward over weeks or months. Link the tasks and habits that move it forward to see why your days matter.",
                    )
                }
            }
            items(state.active, key = { it.goal.id }) { s ->
                GoalCard(s, onOpenGoal, onEditGoal, vm::setStatus) { deleting = s }
            }
            if (state.achieved.isNotEmpty()) {
                item { SectionHeader("Achieved") }
                items(state.achieved, key = { it.goal.id }) { s ->
                    GoalCard(s, onOpenGoal, onEditGoal, vm::setStatus) { deleting = s }
                }
            }
            if (state.archived.isNotEmpty()) {
                item {
                    SectionHeader("Archived · ${state.archived.size}") {
                        TextButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "Hide" else "Show") }
                    }
                }
                if (showArchived) {
                    items(state.archived, key = { it.goal.id }) { s ->
                        GoalCard(s, onOpenGoal, onEditGoal, vm::setStatus) { deleting = s }
                    }
                }
            }
        }
    }

    deleting?.let { s ->
        ConfirmDialog(
            title = "Delete ${s.goal.title}?",
            body = "This removes the goal and its milestones. The tasks and habits linked to it are kept.",
            confirm = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { vm.delete(s.goal.id) },
        )
    }
}

@Composable
private fun GoalCard(
    s: GoalSummary,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onStatus: (Long, GoalStatus) -> Unit,
    onDelete: () -> Unit,
) {
    val g = s.goal
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { onOpen(g.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 14.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    g.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options for ${g.title}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(g.id) })
                        GoalStatusMenuItems(g.status) { menu = false; onStatus(g.id, it) }
                        DropdownMenuItem(text = { Text("Delete…") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            GoalProgressLine(s, Modifier.padding(end = 12.dp))
            val details = listOfNotNull(targetText(g, s.daysLeft), s.contents.ifEmpty { null })
            details.forEach {
                Text(
                    it,
                    Modifier.padding(top = 4.dp, end = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The status changes a goal can make from where it is now. */
@Composable
fun GoalStatusMenuItems(status: GoalStatus, onStatus: (GoalStatus) -> Unit) {
    when (status) {
        GoalStatus.ACTIVE -> {
            DropdownMenuItem(text = { Text("Mark as achieved") }, onClick = { onStatus(GoalStatus.ACHIEVED) })
            DropdownMenuItem(text = { Text("Archive") }, onClick = { onStatus(GoalStatus.ARCHIVED) })
        }
        GoalStatus.ACHIEVED -> {
            DropdownMenuItem(text = { Text("Reopen") }, onClick = { onStatus(GoalStatus.ACTIVE) })
            DropdownMenuItem(text = { Text("Archive") }, onClick = { onStatus(GoalStatus.ARCHIVED) })
        }
        GoalStatus.ARCHIVED -> DropdownMenuItem(text = { Text("Restore") }, onClick = { onStatus(GoalStatus.ACTIVE) })
    }
}

/** The bar with its figure beside it: a percentage, "Achieved", or a hint when there is nothing to count yet. */
@Composable
fun GoalProgressLine(s: GoalSummary, modifier: Modifier = Modifier) {
    val label = when {
        s.goal.status == GoalStatus.ACHIEVED -> "Achieved"
        s.progress.isEmpty -> "No steps yet"
        else -> Fmt.percent(s.progress.fraction)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GoalProgressBar(s.shownFraction, Modifier.weight(1f))
        Text(
            label,
            Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (s.progress.isEmpty && s.goal.status != GoalStatus.ACHIEVED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun GoalProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val shown = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f) },
    ) {
        if (shown > 0f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}
