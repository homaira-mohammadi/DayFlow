package com.dayflow.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayflow.DayFlowApp
import com.dayflow.security.AppLock
import com.dayflow.security.findFragmentActivity

/**
 * Shows [content] only after the user unlocks, when [required]. Asks automatically once on entry.
 * With [cover], the content stays composed underneath (keeping navigation state) and the lock
 * screen covers it; otherwise private content isn't even loaded until unlocked.
 */
@Composable
fun LockGate(required: Boolean, title: String = "Unlock Dayflow", cover: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lock = (context.applicationContext as DayFlowApp).container.appLock
    val unlocked by lock.unlocked.collectAsStateWithLifecycle()
    if (!required || unlocked) {
        content()
        return
    }
    if (cover) {
        Box(Modifier.fillMaxSize()) {
            content()
            LockScreen(context, lock, title)
        }
    } else {
        LockScreen(context, lock, title)
    }
}

@Composable
private fun LockScreen(context: Context, lock: AppLock, title: String) {
    var error by remember { mutableStateOf<String?>(null) }
    val prompt = {
        val activity = context.findFragmentActivity()
        if (activity == null || !AppLock.isAvailable(context)) {
            // The phone's screen lock was removed; there is nothing to verify against, so don't trap the user.
            lock.markUnlocked()
        } else {
            AppLock.authenticate(activity, title, onSuccess = lock::markUnlocked, onFailure = { error = it })
        }
    }
    LaunchedEffect(Unit) { prompt() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "This part of Dayflow is private. Unlock with your fingerprint, face or phone PIN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = prompt) { Text("Unlock") }
        }
    }
}
