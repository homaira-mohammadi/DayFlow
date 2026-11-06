package com.dayflow.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.data.prefs.ThemeMode
import com.dayflow.domain.Gender
import com.dayflow.security.AppLock
import com.dayflow.security.AppLockMode
import com.dayflow.security.findFragmentActivity
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.ConfirmDialog
import com.dayflow.ui.components.TimePickerDialogFor
import com.dayflow.ui.theme.AccentPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class SettingsDialog { NAME, ACCENT, WEEK_START, MORNING, EVENING, ABOUT, HELP, DELETE, GENDER, CYCLE_LENGTH, PERIOD_LENGTH, PMS_DAYS, EXPORT, DELETE_CYCLE, APP_LOCK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenReflections: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val systemDark = isSystemInDarkTheme()
    val isDark = when (s.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    var exportCycle by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(context.contentResolver, uri, exportCycle)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.update { it.copy(notifications = granted) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            ProfileCard(s.name) { dialog = SettingsDialog.NAME }

            SettingsGroup("Appearance") {
                SettingsRow(Icons.Filled.DarkMode, "Dark mode", trailing = {
                    Switch(checked = isDark, onCheckedChange = { on -> vm.update { it.copy(themeMode = if (on) ThemeMode.DARK else ThemeMode.LIGHT) } })
                })
                RowDivider()
                SettingsRow(Icons.Filled.BrightnessAuto, "Match system theme", subtitle = "Switch automatically with your phone", trailing = {
                    Switch(
                        checked = s.themeMode == ThemeMode.SYSTEM,
                        onCheckedChange = { on ->
                            vm.update { it.copy(themeMode = if (on) ThemeMode.SYSTEM else if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) }
                        },
                    )
                })
                RowDivider()
                SettingsRow(
                    Icons.Filled.Palette, "Accent color",
                    value = if (s.dynamicColor) "Wallpaper" else null,
                    leadingValue = { if (!s.dynamicColor) Box(Modifier.size(18.dp).clip(CircleShape).background(AccentPalette.getOrElse(s.accent) { AccentPalette[0] })) },
                    onClick = { dialog = SettingsDialog.ACCENT },
                )
            }

            SettingsGroup("Preferences") {
                SettingsRow(
                    Icons.Filled.CalendarMonth, "Week starts on",
                    value = s.weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    onClick = { dialog = SettingsDialog.WEEK_START },
                )
                RowDivider()
                SettingsRow(Icons.Filled.Schedule, "24-hour time", subtitle = "${Fmt.time(14 * 60 + 30, true)} instead of ${Fmt.time(14 * 60 + 30, false)}", trailing = {
                    Switch(checked = s.use24h, onCheckedChange = { v -> vm.update { it.copy(use24h = v) } })
                })
                RowDivider()
                SettingsRow(
                    Icons.Filled.Bolt, "Minimum version counts as done",
                    subtitle = "Busy-day minimums keep your streak going",
                    trailing = { Switch(checked = s.minimumCounts, onCheckedChange = { v -> vm.update { it.copy(minimumCounts = v) } }) },
                )
            }

            SettingsGroup("Profile (optional)") {
                SettingsRow(
                    Icons.Filled.Person, "Gender",
                    subtitle = "Only saved if you choose one. It doesn't change which features you can use.",
                    value = s.gender?.label ?: "Not set",
                    onClick = { dialog = SettingsDialog.GENDER },
                )
            }

            SettingsGroup("Prayers") {
                SettingsRow(Icons.Filled.Mosque, "Prayer tracking", subtitle = "Shows Fajr, Dhuhr, Asr, Maghrib and Isha on Today, to mark as on time, late or missed", trailing = {
                    Switch(checked = s.prayerTracking, onCheckedChange = { v -> vm.update { it.copy(prayerTracking = v) } })
                })
            }

            SettingsGroup("Period tracking") {
                SettingsRow(Icons.Filled.WaterDrop, "Enable period tracking", subtitle = "Adds the Cycle tab: period calendar, predictions and PMS days. Turn it on or off any time.", trailing = {
                    Switch(checked = s.cycleTracking, onCheckedChange = { v -> vm.update { it.copy(cycleTracking = v) } })
                })
                if (s.cycleTracking) {
                    RowDivider()
                    SettingsRow(Icons.Filled.Autorenew, "Typical cycle length", subtitle = "Used until you've logged a few cycles",
                        value = "${s.cycleLength} days", onClick = { dialog = SettingsDialog.CYCLE_LENGTH })
                    RowDivider()
                    SettingsRow(Icons.Filled.Event, "Typical period length", value = "${s.periodLength} days", onClick = { dialog = SettingsDialog.PERIOD_LENGTH })
                    RowDivider()
                    SettingsRow(Icons.Filled.Mood, "PMS days", subtitle = "Days before a period to treat as PMS days",
                        value = "${s.pmsDays}", onClick = { dialog = SettingsDialog.PMS_DAYS })
                    RowDivider()
                    SettingsRow(Icons.Filled.Spa, "Show fertile window", subtitle = "Estimated ovulation and fertile days", trailing = {
                        Switch(checked = s.showFertility, onCheckedChange = { v -> vm.update { it.copy(showFertility = v) } })
                    })
                    RowDivider()
                    SettingsRow(Icons.Filled.NotificationsActive, "Cycle reminders", subtitle = "Content is hidden on the lock screen", trailing = {
                        Switch(checked = s.cycleReminders, onCheckedChange = { v -> vm.update { it.copy(cycleReminders = v) } })
                    })
                    RowDivider()
                    SettingsRow(Icons.Filled.VisibilityOff, "Show cycle on Today & Calendar", subtitle = "Off keeps cycle details inside the Cycle tab only", trailing = {
                        Switch(checked = s.cycleHints, onCheckedChange = { v -> vm.update { it.copy(cycleHints = v) } })
                    })
                }
                RowDivider()
                SettingsRow(Icons.Filled.DeleteForever, "Delete period data", subtitle = "Removes period and symptom records only",
                    danger = true, onClick = { dialog = SettingsDialog.DELETE_CYCLE })
            }

            SettingsGroup("Notifications") {
                SettingsRow(Icons.Filled.Notifications, "Reminders", subtitle = "Habits, tasks, morning plan and evening review", trailing = {
                    Switch(checked = s.notifications, onCheckedChange = { v ->
                        val needsPermission = v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.update { it.copy(notifications = v) }
                    })
                })
                RowDivider()
                SettingsRow(
                    Icons.Filled.WbSunny, "Morning plan & reflection",
                    subtitle = s.morningPlanMinutes?.let { Fmt.time(it, s.use24h) } ?: "Off",
                    enabled = s.notifications,
                    onClick = { dialog = SettingsDialog.MORNING },
                    trailing = {
                        Switch(checked = s.morningPlanMinutes != null, enabled = s.notifications,
                            onCheckedChange = { on -> vm.update { it.copy(morningPlanMinutes = if (on) 8 * 60 else null) } })
                    },
                )
                RowDivider()
                SettingsRow(
                    Icons.Filled.Bedtime, "Evening review",
                    subtitle = s.eveningReviewMinutes?.let { Fmt.time(it, s.use24h) } ?: "Off",
                    enabled = s.notifications,
                    onClick = { dialog = SettingsDialog.EVENING },
                    trailing = {
                        Switch(checked = s.eveningReviewMinutes != null, enabled = s.notifications,
                            onCheckedChange = { on -> vm.update { it.copy(eveningReviewMinutes = if (on) 21 * 60 else null) } })
                    },
                )
            }

            SettingsGroup("Privacy & security") {
                SettingsRow(
                    Icons.Filled.Lock, "App lock",
                    subtitle = "Fingerprint, face or phone PIN. Content is also hidden in recent apps.",
                    value = s.appLock.label,
                    onClick = { dialog = SettingsDialog.APP_LOCK },
                )
            }

            SettingsGroup("Your data") {
                SettingsRow(Icons.Filled.AutoStories, "Morning reflections", onClick = onOpenReflections)
                RowDivider()
                SettingsRow(Icons.Filled.FileDownload, "Export data", subtitle = "Save a JSON backup of everything",
                    onClick = { dialog = SettingsDialog.EXPORT })
                RowDivider()
                SettingsRow(Icons.Filled.DeleteForever, "Delete all data", danger = true, onClick = { dialog = SettingsDialog.DELETE })
            }

            SettingsGroup("About") {
                SettingsRow(Icons.Filled.Explore, "App tour", subtitle = "A quick guide to every part of the app", onClick = { vm.update { it.copy(tourSeen = false) } })
                RowDivider()
                SettingsRow(Icons.Filled.Info, "About Dayflow", onClick = { dialog = SettingsDialog.ABOUT })
                RowDivider()
                SettingsRow(Icons.AutoMirrored.Filled.HelpOutline, "Help / FAQ", onClick = { dialog = SettingsDialog.HELP })
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        SettingsDialog.NAME -> NameDialog(s.name, onDismiss = { dialog = null }) { n -> vm.update { it.copy(name = n) } }
        SettingsDialog.ACCENT -> AccentDialog(s.accent, s.dynamicColor, onDismiss = { dialog = null }, onAccent = { i ->
            vm.update { it.copy(accent = i, dynamicColor = false) }
        }, onDynamic = { d -> vm.update { it.copy(dynamicColor = d) } })
        SettingsDialog.WEEK_START -> ChoiceDialog(
            title = "Week starts on",
            options = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            selected = s.weekStart,
            label = { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
            onDismiss = { dialog = null },
        ) { d -> vm.update { it.copy(weekStart = d) } }
        SettingsDialog.MORNING -> TimePickerDialogFor(s.morningPlanMinutes ?: (8 * 60), s.use24h, { dialog = null }) { m ->
            vm.update { it.copy(morningPlanMinutes = m) }
        }
        SettingsDialog.EVENING -> TimePickerDialogFor(s.eveningReviewMinutes ?: (21 * 60), s.use24h, { dialog = null }) { m ->
            vm.update { it.copy(eveningReviewMinutes = m) }
        }
        SettingsDialog.GENDER -> ChoiceDialog(
            title = "Gender (optional)",
            options = listOf<Gender?>(null) + Gender.entries,
            selected = s.gender,
            label = { it?.label ?: "Prefer not to say (nothing saved)" },
            onDismiss = { dialog = null },
        ) { g -> vm.update { it.copy(gender = g) } }
        SettingsDialog.APP_LOCK -> ChoiceDialog(
            title = "App lock",
            options = AppLockMode.entries,
            selected = s.appLock,
            label = {
                when (it) {
                    AppLockMode.OFF -> "Off"
                    AppLockMode.PRIVATE -> "Journal & Cycle only"
                    AppLockMode.WHOLE_APP -> "Whole app"
                }
            },
            onDismiss = { dialog = null },
        ) { mode ->
            val activity = context.findFragmentActivity()
            when {
                mode == s.appLock -> Unit
                !AppLock.isAvailable(context) || activity == null ->
                    vm.showMessage("Set up a screen lock (PIN, pattern, fingerprint or face) on your phone first")
                // Confirm it's really the owner, both to turn the lock on and to turn it off.
                else -> AppLock.authenticate(
                    activity, "Confirm it's you",
                    onSuccess = {
                        vm.markUnlocked()
                        vm.update { it.copy(appLock = mode) }
                    },
                )
            }
        }
        SettingsDialog.EXPORT -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Export data") },
            text = {
                Column {
                    Text("Saves your tasks, habits, reflections and focus sessions as a JSON file.")
                    if (s.cycleTracking || exportCycle) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp).clickable { exportCycle = !exportCycle },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = exportCycle, onCheckedChange = { exportCycle = it })
                            Text("Include period data")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    exportLauncher.launch("dayflow-${LocalDate.now()}.json")
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        SettingsDialog.DELETE_CYCLE -> ConfirmDialog(
            title = "Delete period data?",
            body = "This permanently removes all period days and symptom logs. Your tasks, habits and other records are not affected.",
            confirm = "Delete",
            onDismiss = { dialog = null },
            onConfirm = vm::deleteCycleData,
        )
        SettingsDialog.CYCLE_LENGTH -> NumberDialog("Typical cycle length", s.cycleLength, 20..45, "days", { dialog = null }) { n ->
            vm.update { it.copy(cycleLength = n) }
        }
        SettingsDialog.PERIOD_LENGTH -> NumberDialog("Typical period length", s.periodLength, 1..10, "days", { dialog = null }) { n ->
            vm.update { it.copy(periodLength = n) }
        }
        SettingsDialog.PMS_DAYS -> NumberDialog("PMS days", s.pmsDays, 1..10, "days before a period", { dialog = null }) { n ->
            vm.update { it.copy(pmsDays = n) }
        }
        SettingsDialog.ABOUT -> InfoDialog("About Dayflow", onDismiss = { dialog = null }) {
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
            }
            Text("Version $version", fontWeight = FontWeight.Medium)
            Text("A calm planner and habit tracker. Everything is stored only on this device: there are no accounts, the app has no internet access, and personal records are left out of Google cloud backup. They only move with a direct phone-to-phone transfer.")
            Text("How numbers are calculated", fontWeight = FontWeight.Medium)
            Text(
                "Daily completion = completed ÷ planned. Cancelled tasks, skipped habits and habits on rest days are left out, " +
                    "so they never count as missed. Habits are only expected on their scheduled days, and streaks skip unscheduled days. " +
                    "Changing a habit's schedule only affects today and later.",
            )
        }
        SettingsDialog.HELP -> InfoDialog("Help / FAQ", onDismiss = { dialog = null }) {
            faq.forEach { (q, a) ->
                Text(q, fontWeight = FontWeight.Medium)
                Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingsDialog.DELETE -> ConfirmDialog(
            title = "Delete all data?",
            body = "This permanently removes every task, habit, reflection and record. Settings are kept. Consider exporting first.",
            confirm = "Delete everything",
            onDismiss = { dialog = null },
            onConfirm = vm::deleteAll,
        )
        null -> Unit
    }
}

private val faq = listOf(
    "How do I complete a habit?" to "Tap the circle on the right of a habit. For amount or duration habits, tap the habit itself to enter how much you did.",
    "What is a minimum version?" to "A smaller target for busy days, like 5 minutes of reading instead of 20. It keeps your streak going, and the app still records that it was the minimum.",
    "What happens to unfinished tasks?" to "They appear under \"Unfinished from earlier\" on Today. Use the ⋮ menu to move each one to today, tomorrow, next week or any date. Nothing is moved automatically.",
    "What is a rest day?" to "A planned break. Habits aren't expected on rest days, so they don't break streaks. Set one from the ⋮ menu on Today or from the Calendar.",
    "I missed a check-in yesterday. Can I fix it?" to "Yes. Open the habit and tap the day in its history, or pick the date in the Calendar.",
    "Who can use period tracking?" to "Anyone. Turn it on in Settings → Period tracking; your gender choice is optional and never controls it. Period records stay in the Cycle tab and are left out of Progress and exports unless you choose otherwise.",
    "How are period predictions made?" to "From the periods you log: the next period is your average cycle length after the last one started. PMS days are the few days before it, and ovulation is estimated about 14 days before. Predictions are estimates, not contraception.",
    "How does the app lock work?" to "It uses your phone's own fingerprint, face or screen-lock PIN, so Dayflow never stores a password. It locks again 30 seconds after you leave the app. Choose to lock the whole app or only the journal and cycle pages.",
    "Why don't I get reminders?" to "Make sure Reminders is on here and that notifications are allowed for Dayflow in your phone's settings.",
)

@Composable
private fun ProfileCard(name: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "☀" },
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Add your name" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Your data stays on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Edit name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 52.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** One line in a settings card: icon, title, optional detail, and a chevron or a custom control. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    leadingValue: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val color = when {
        danger -> MaterialTheme.colorScheme.error
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        leadingValue()
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumberDialog(title: String, value: Int, range: IntRange, unit: String, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var n by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { n = (n - 1).coerceAtLeast(range.first) }, modifier = Modifier.semantics { contentDescription = "Decrease" }) {
                    Text("−", style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { n = (n + 1).coerceAtMost(range.last) }, modifier = Modifier.semantics { contentDescription = "Increase" }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                supportingText = { Text("Used for the greeting on Today") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentDialog(accent: Int, dynamic: Boolean, onDismiss: () -> Unit, onAccent: (Int) -> Unit, onDynamic: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accent color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentPalette.forEachIndexed { i, color ->
                        val selected = accent == i && !dynamic
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { onAccent(i) }
                                .semantics { contentDescription = "Accent color ${i + 1}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White)
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Use wallpaper colors")
                            Text("Material You dynamic color", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = dynamic, onCheckedChange = onDynamic)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = o == selected, role = Role.RadioButton) { onSelect(o); onDismiss() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(o))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InfoDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
