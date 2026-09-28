package com.parlo.app.ui.main

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlo.app.model.ConnectionState
import com.parlo.app.model.CorrectionStyle
import com.parlo.app.model.Level
import com.parlo.app.model.Scenario
import com.parlo.app.model.Speaker
import com.parlo.app.model.TranscriptTurn
import com.parlo.app.ui.MainViewModel
import com.parlo.app.ui.settings.SettingsSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenVocab: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val live = ui.live
    val cfg = ui.config
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showPickers by remember { mutableStateOf(true) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startSession(context)
        } else {
            Toast.makeText(context, "Parlo needs the microphone to hear you speak.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestStart() {
        if (!ui.hasApiKey) { showSettings = true; return }
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(live.isActive) { if (live.isActive) showPickers = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parlo", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenVocab) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Vocab list") }
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.History, "Session history") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (live.error != null) {
                ErrorBanner(message = live.error.message, onDismiss = viewModel::dismissError, onSettings = { showSettings = true })
                Spacer(Modifier.height(8.dp))
            }

            AudioIndicator(state = live.audio, active = live.isActive, modifier = Modifier.padding(top = 8.dp))
            if (live.statusText.isNotBlank() && live.error == null) {
                Text(live.statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            SessionButton(
                active = live.isActive,
                ending = live.connection == ConnectionState.ENDING,
                connecting = live.connection == ConnectionState.CONNECTING,
                muted = live.muted,
                onStart = ::requestStart,
                onEnd = { viewModel.endSession(skipRecap = false) },
                onEndNow = {
                    viewModel.endSession(skipRecap = true)
                    Toast.makeText(context, "Ended without recap", Toast.LENGTH_SHORT).show()
                },
                onToggleMute = viewModel::toggleMute,
            )
            Spacer(Modifier.height(12.dp))

            // Quick-switch chips
            if (ui.recentCombos.isNotEmpty()) {
                ChipRow(
                    options = ui.recentCombos,
                    selected = ui.recentCombos.firstOrNull { it == cfg.languageCombo },
                    label = { it.label },
                    onSelect = viewModel::selectCombo,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${cfg.dialect.ifBlank { cfg.language }} · ${cfg.level.label} · ${cfg.scenario.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showPickers = !showPickers }) { Text(if (showPickers) "Hide" else "Adjust") }
            }

            AnimatedVisibility(visible = showPickers) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LanguageCard(language = cfg.language, dialect = cfg.dialect, onClick = { showLanguagePicker = true })
                    Text("Level", style = MaterialTheme.typography.labelMedium)
                    ChipFlow(options = Level.entries, selected = cfg.level, label = { it.label }, onSelect = { l -> viewModel.updateConfig { it.copy(level = l) } })
                    Text(cfg.level.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Scenario", style = MaterialTheme.typography.labelMedium)
                    ChipRow(options = Scenario.entries, selected = cfg.scenario, label = { it.label }, onSelect = { s -> viewModel.updateConfig { it.copy(scenario = s) } })
                    Text("Corrections", style = MaterialTheme.typography.labelMedium)
                    SegmentedPicker(options = CorrectionStyle.entries, selected = cfg.correctionStyle, label = { it.label }, onSelect = { s -> viewModel.updateConfig { it.copy(correctionStyle = s) } })
                }
            }

            Spacer(Modifier.height(12.dp))
            Transcript(turns = live.transcript, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }

    if (showSettings) {
        SettingsSheet(viewModel = viewModel, onDismiss = { showSettings = false })
    }
    if (showLanguagePicker) {
        LanguagePickerSheet(
            language = cfg.language,
            dialect = cfg.dialect,
            onSelect = { lang, dialect -> viewModel.updateConfig { it.copy(language = lang, dialect = dialect) } },
            onDismiss = { showLanguagePicker = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionButton(
    active: Boolean,
    ending: Boolean,
    connecting: Boolean,
    muted: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onEndNow: () -> Unit,
    onToggleMute: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!active) {
            Button(onClick = onStart, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(20.dp)) {
                Text("Start Walk Session", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(20.dp))
                    .combinedClickable(onClick = onEnd, onLongClick = onEndNow),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (connecting || ending) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.widthIn(8.dp))
                                Text(if (ending) "  Recap… (hold to end now)" else "  Connecting…", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        } else {
                            Text("End Walk Session", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("hold to skip recap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            FilledTonalButton(
                onClick = onToggleMute,
                modifier = Modifier.height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = if (muted) ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.filledTonalButtonColors(),
            ) {
                Icon(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (muted) "Unmute" else "Mute")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, onSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onSettings) { Text("Settings") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
fun Transcript(turns: List<TranscriptTurn>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size, turns.lastOrNull()?.text?.length) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex)
    }
    if (turns.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Your conversation will appear here.\nPut in your earbuds, tap Start, and just talk.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(turns, key = { it.id }) { turn -> TurnBubble(turn) }
    }
}

@Composable
fun TurnBubble(turn: TranscriptTurn) {
    val scheme = MaterialTheme.colorScheme
    when (turn.speaker) {
        Speaker.SYSTEM -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AssistChip(onClick = {}, label = { Text(turn.text, style = MaterialTheme.typography.labelSmall) }, enabled = false)
        }
        else -> {
            val isUser = turn.speaker == Speaker.USER
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                Box(
                    Modifier.widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp))
                        .background(
                            when {
                                !turn.isFinal -> (if (isUser) scheme.primaryContainer else scheme.surfaceVariant).copy(alpha = 0.55f)
                                isUser -> scheme.primaryContainer
                                else -> scheme.surfaceVariant
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(turn.text, style = MaterialTheme.typography.bodyMedium, color = if (isUser) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                }
            }
        }
    }
}
