package com.dayflow.ui.reflection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.R
import com.dayflow.domain.Mood
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.ConfirmDialog
import com.dayflow.ui.components.PhotoImage
import com.dayflow.ui.components.SectionHeader

private val energyLabels = listOf("Drained", "Low", "Okay", "Good", "Full of energy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReflectionEditorScreen(
    onClose: () -> Unit,
    vm: ReflectionEditorViewModel = viewModel(factory = ReflectionEditorViewModel.Factory),
) {
    val f = vm.form
    var confirmDelete by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri)
    }
    val launchPicker = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
                actions = {
                    if (vm.exists) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete reflection") }
                    TextButton(onClick = { vm.save(onClose) }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Image(
                painterResource(R.drawable.illustration_sunrise),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text(Fmt.dayHeader(vm.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Morning Reflection", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "A minute to notice how you feel and decide what matters today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("Photo")
            val photo = f.photoPath
            when {
                vm.photoBusy -> Box(
                    Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                photo != null -> Box {
                    PhotoImage(
                        vm.photoFile(photo),
                        contentDescription = "Your photo for this morning",
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(onClick = launchPicker) { Icon(Icons.Filled.Edit, "Change photo") }
                        FilledTonalIconButton(onClick = vm::removePhoto) { Icon(Icons.Filled.Close, "Remove photo") }
                    }
                }
                else -> OutlinedCard(onClick = launchPicker, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.size(14.dp))
                        Column {
                            Text("Add a photo", style = MaterialTheme.typography.bodyLarge)
                            Text("Capture a moment from your morning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (vm.photoError) {
                Text("Couldn't load that photo. Try another one.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }

            SectionHeader("How are you feeling?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mood.entries.forEach { m ->
                    FilterChip(
                        selected = f.mood == m,
                        onClick = { vm.update { it.copy(mood = if (it.mood == m) null else m) } },
                        label = { Text("${m.emoji}  ${m.label}") },
                    )
                }
            }

            SectionHeader("Energy")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..5).forEach { level ->
                    val on = (f.energy ?: 0) >= level
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { vm.update { it.copy(energy = if (it.energy == level) null else level) } }
                            .semantics {
                                contentDescription = "Energy $level of 5, ${energyLabels[level - 1]}"
                                selected = f.energy == level
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$level",
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Text(
                f.energy?.let { energyLabels[it - 1] } ?: "Tap to rate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionHeader("Grateful for")
            OutlinedTextField(
                value = f.gratitude,
                onValueChange = { v -> vm.update { it.copy(gratitude = v) } },
                placeholder = { Text("The warmth of my morning tea…") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Today's main intention")
            OutlinedTextField(
                value = f.intention,
                onValueChange = { v -> vm.update { it.copy(intention = v) } },
                placeholder = { Text("Finish the Java assignment before lunch") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Anything on your mind?")
            OutlinedTextField(
                value = f.notes,
                onValueChange = { v -> vm.update { it.copy(notes = v) } },
                placeholder = { Text("Write freely…") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this reflection?",
            body = "This can't be undone.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { vm.delete(onClose) },
        )
    }
}
