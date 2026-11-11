package com.dayflow.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dayflow.R
import com.dayflow.domain.Gender

/** Answers from the welcome flow. Only the feature switches and the optional profile are saved. */
data class OnboardingResult(
    val name: String,
    val gender: Gender?,
    val periodTracking: Boolean,
    val prayerTracking: Boolean,
)

/** Genders for which period tracking is offered right away. Anyone can still turn it on in Settings. */
private val periodGenders = setOf(Gender.FEMALE, Gender.NON_BINARY, Gender.SELF_DESCRIBED)

private const val WELCOME = 0
private const val NAME = 1
private const val GENDER = 2
private const val FAITH = 3
private const val DONE = 4
private const val QUESTIONS = 3

/**
 * First-run welcome, one question per page. Every question is optional. Period tracking is offered
 * once a gender it usually applies to is chosen, and the prayer tracker once the person says they
 * are Muslim. The answer about religion is only used to offer the prayer tracker and is never stored.
 */
@Composable
fun OnboardingScreen(onDone: (OnboardingResult) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(WELCOME) }
    var forward by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf<Gender?>(null) }
    var periodTracking by rememberSaveable { mutableStateOf(true) }
    var muslim by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var prayerTracking by rememberSaveable { mutableStateOf(true) }

    val result = OnboardingResult(
        name = name.trim(),
        gender = gender,
        periodTracking = gender in periodGenders && periodTracking,
        prayerTracking = muslim == true && prayerTracking,
    )
    fun go(to: Int) {
        forward = to > step
        step = to
    }
    BackHandler(enabled = step > WELCOME) { go(step - 1) }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (step in NAME..FAITH) {
                TopProgress(
                    step = step,
                    onBack = { go(step - 1) },
                    onSkip = { go(DONE) },
                )
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally { it / 3 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 * dir } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboardingStep",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    when (s) {
                        WELCOME -> WelcomePage()
                        NAME -> NamePage(name, onName = { name = it }, onNext = { go(GENDER) })
                        GENDER -> GenderPage(gender, periodTracking, onGender = { gender = it }, onPeriod = { periodTracking = it })
                        FAITH -> FaithPage(muslim, prayerTracking, onMuslim = { muslim = it }, onPrayer = { prayerTracking = it })
                        else -> DonePage(result)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Button(
                    onClick = { if (step == DONE) onDone(result) else go(step + 1) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        when (step) {
                            WELCOME -> "Get started"
                            DONE -> "Start my day"
                            else -> "Continue"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (step == WELCOME) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "No account needed. Your data stays on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopProgress(step: Int, onBack: () -> Unit, onSkip: () -> Unit) {
    val progress by animateFloatAsState(step.toFloat() / QUESTIONS, label = "onboardingProgress")
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(6.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        TextButton(onClick = onSkip) { Text("Skip") }
    }
}

@Composable
private fun WelcomePage() {
    Column(Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(148.dp).background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh),
                ),
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.logo_mark), contentDescription = "Dayflow logo", modifier = Modifier.size(96.dp))
        }
        Text(
            "Dayflow",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "Plan your day. Build habits.\nGrow a little every day.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(Modifier.fillMaxWidth().padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Highlight(Icons.Filled.TaskAlt, "Plan your day", "Tasks, priorities and a clear daily progress ring")
            Highlight(Icons.Filled.Loop, "Build habits", "Streaks, busy-day minimums and gentle reminders")
            Highlight(Icons.Filled.Insights, "See your progress", "Charts and history that show how far you've come")
        }
    }
}

@Composable
private fun Highlight(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size / 2))
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
    )
}

@Composable
private fun NamePage(name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    PageTitle("What should we call you?", "We'll use it to greet you each morning. You can leave it empty.")
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        placeholder = { Text("Your name") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.titleMedium,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GenderPage(gender: Gender?, period: Boolean, onGender: (Gender?) -> Unit, onPeriod: (Boolean) -> Unit) {
    PageTitle("How do you identify?", "Optional. It's only saved if you choose one.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Gender.entries.forEach { g -> Choice(g.label, gender == g) { onGender(g) } }
        Choice("Prefer not to say", gender == null) { onGender(null) }
    }
    AnimatedVisibility(gender in periodGenders, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        FeatureCard(
            icon = Icons.Filled.WaterDrop,
            title = "Track your period",
            body = "A private Cycle tab with a period calendar, predictions and PMS days. On period days, prayers are marked as excused.",
            checked = period,
            onCheckedChange = onPeriod,
        )
    }
}

@Composable
private fun FaithPage(muslim: Boolean?, prayer: Boolean, onMuslim: (Boolean?) -> Unit, onPrayer: (Boolean) -> Unit) {
    PageTitle("Are you Muslim?", "Optional. Only used to offer the prayer tracker. Your answer isn't saved.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice("Yes", muslim == true) { onMuslim(true) }
        Choice("No", muslim == false) { onMuslim(false) }
        Choice("Prefer not to say", muslim == null) { onMuslim(null) }
    }
    AnimatedVisibility(muslim == true, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        FeatureCard(
            icon = Icons.Filled.Mosque,
            title = "Track your five prayers",
            body = "Mark Fajr, Dhuhr, Asr, Maghrib and Isha as on time, late or missed, and see your history on a chart.",
            checked = prayer,
            onCheckedChange = onPrayer,
        )
    }
}

@Composable
private fun DonePage(result: OnboardingResult) {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        }
        Text(
            if (result.name.isBlank()) "You're all set!" else "You're all set, ${result.name}!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "Next, a quick tour shows you where everything is.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Card(
            Modifier.fillMaxWidth().padding(top = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your Dayflow includes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Included(Icons.Filled.TaskAlt, "Tasks & daily plan")
                Included(Icons.Filled.Loop, "Habits & streaks")
                Included(Icons.Filled.Insights, "Progress & focus timer")
                Included(Icons.Filled.Bedtime, "Sleep tracker")
                if (result.prayerTracking) Included(Icons.Filled.Mosque, "Prayer tracker")
                if (result.periodTracking) Included(Icons.Filled.WaterDrop, "Period tracking")
            }
        }
    }
}

@Composable
private fun Included(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
    }
}

/** A large tappable option, like the choice lists in big apps. */
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else null, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
