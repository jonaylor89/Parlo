package com.parlo.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlo.app.model.ConnectionState
import com.parlo.app.model.CorrectionStyle
import com.parlo.app.model.ErrorAction
import com.parlo.app.model.LanguageCatalog
import com.parlo.app.model.Level
import com.parlo.app.model.SessionConfig
import com.parlo.app.model.SessionError
import com.parlo.app.model.Speaker
import com.parlo.app.model.TranscriptTurn
import com.parlo.app.ui.MainViewModel
import com.parlo.app.ui.SetupStatus
import com.parlo.app.ui.settings.SettingsSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    val neededPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startSession(context)
        } else {
            scope.launch {
                val r = snackbar.showSnackbar("Parlo can't hear you without the microphone.", actionLabel = "Allow in Settings", duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }

    fun launchPermissions() = permissionLauncher.launch(neededPermissions.toTypedArray())

    fun requestStart() {
        if (!ui.ready) { showSettings = true; return }
        val missing = neededPermissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing) showPermissionRationale = true else viewModel.startSession(context)
    }

    // Ending a walk is a single tap; the recap can be skipped from the snackbar instead of a hidden long-press.
    LaunchedEffect(live.connection) {
        if (live.connection == ConnectionState.ENDING) {
            val r = snackbar.showSnackbar("Walk ended · Writing your recap…", actionLabel = "Skip", duration = SnackbarDuration.Indefinite)
            if (r == SnackbarResult.ActionPerformed) viewModel.endSession(skipRecap = true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Parlo", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = onOpenVocab) {
                        BadgedBox(badge = { if (ui.suggestedVocab > 0) Badge { Text(ui.suggestedVocab.toString()) } }) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, "Vocabulary")
                        }
                    }
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
            val error = live.error
            if (error != null) {
                ErrorCard(
                    error = error,
                    onAction = {
                        when (error.action) {
                            ErrorAction.UPDATE_KEY, ErrorAction.OPEN_SETTINGS -> showSettings = true
                            ErrorAction.REFRESH_MODELS -> { viewModel.refreshModels(); viewModel.dismissError() }
                            ErrorAction.RETRY, ErrorAction.START_AGAIN -> { viewModel.dismissError(); requestStart() }
                        }
                    },
                    onDismiss = viewModel::dismissError,
                )
                Spacer(Modifier.height(8.dp))
            } else if (!live.isActive && !ui.ready) {
                SetupCard(status = ui.setup, onOpenSettings = { showSettings = true }, onRefresh = viewModel::refreshModels)
                Spacer(Modifier.height(8.dp))
            }

            AudioIndicator(
                state = live.audio,
                active = live.isActive,
                idleLabel = if (ui.ready) "Ready · tap Start and talk" else "Finish setup to begin",
                modifier = Modifier.padding(top = 4.dp),
            )
            if (live.statusText.isNotBlank() && live.error == null) {
                Text(live.statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            SessionButton(
                active = live.isActive,
                ready = ui.ready,
                ending = live.connection == ConnectionState.ENDING,
                connecting = live.connection == ConnectionState.CONNECTING,
                muted = live.muted,
                onStart = ::requestStart,
                onEnd = { viewModel.endSession(skipRecap = false) },
                onToggleMute = viewModel::toggleMute,
            )
            Spacer(Modifier.height(12.dp))

            WalkConfigCard(cfg = cfg, onClick = { showConfig = true })
            Spacer(Modifier.height(12.dp))

            if (!live.isActive && live.transcript.isEmpty() && ui.suggestedVocab > 0) {
                ReviewCard(count = ui.suggestedVocab, onReview = onOpenVocab)
                Spacer(Modifier.height(12.dp))
            }

            Transcript(turns = live.transcript, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Before your first walk") },
            text = {
                Text(
                    "Parlo needs your microphone to hear you, Bluetooth to use your earbuds' buttons, and notifications to show walk controls on your lock screen.",
                )
            },
            confirmButton = {
                Button(onClick = { showPermissionRationale = false; launchPermissions() }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showPermissionRationale = false }) { Text("Not now") } },
        )
    }
    if (showSettings) {
        SettingsSheet(viewModel = viewModel, onDismiss = { showSettings = false })
    }
    if (showConfig) {
        WalkConfigSheet(
            viewModel = viewModel,
            onPickLanguage = { showLanguagePicker = true },
            onDismiss = { showConfig = false },
        )
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

/** One-line "what am I about to practise" card; tapping opens [WalkConfigSheet]. */
@Composable
private fun WalkConfigCard(cfg: SessionConfig, onClick: () -> Unit) {
    val entry = LanguageCatalog.find(cfg.language)
    val dialectEntry = entry?.dialects?.firstOrNull { it.name.equals(cfg.dialect, true) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("walk_config"),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Flag(dialectEntry?.flag ?: entry?.flag)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(cfg.language.ifBlank { "Choose a language" }, cfg.dialect.takeIf { it.isNotBlank() && !it.equals(cfg.language, true) }.orEmpty())
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${cfg.level.label} · ${cfg.correctionStyle.label} corrections",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Adjust today's walk", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Scrollable sheet with everything that shapes the walk. Nothing on the main screen changes height. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalkConfigSheet(viewModel: MainViewModel, onPickLanguage: () -> Unit, onDismiss: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val cfg = ui.config
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Today's walk", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            LanguageCard(language = cfg.language, dialect = cfg.dialect, onClick = onPickLanguage)
            Text("Level", style = MaterialTheme.typography.labelMedium)
            SegmentedPicker(options = Level.entries, selected = cfg.level, label = { it.shortLabel }, onSelect = { l -> viewModel.updateConfig { it.copy(level = l) } })
            Text("${cfg.level.label} — ${cfg.level.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Corrections", style = MaterialTheme.typography.labelMedium)
            SegmentedPicker(options = CorrectionStyle.entries, selected = cfg.correctionStyle, label = { it.label }, onSelect = { s -> viewModel.updateConfig { it.copy(correctionStyle = s) } })
            if (ui.recentCombos.size >= 2) {
                Text("Recent", style = MaterialTheme.typography.labelMedium)
                ChipRow(
                    options = ui.recentCombos,
                    selected = ui.recentCombos.firstOrNull { it == cfg.languageCombo },
                    label = { it.label },
                    onSelect = viewModel::selectCombo,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (ui.live.isActive) {
                Text("Changes apply to the current walk right away.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SetupCard(status: SetupStatus, onOpenSettings: () -> Unit, onRefresh: () -> Unit) {
    val (title, body, action) = when (status) {
        SetupStatus.NO_KEY -> Triple("Connect Gemini to start", "Parlo talks through your own free Gemini key. It stays encrypted on this phone.", "Add key")
        SetupStatus.CHECKING -> Triple("Checking your key…", "Looking for voice models this key can use.", null)
        SetupStatus.BAD_KEY -> Triple("That key didn't work", "Check it was copied fully, or make a new one in AI Studio.", "Update key")
        SetupStatus.OFFLINE -> Triple("Couldn't reach Gemini", "Check your connection, then try again.", "Try again")
        SetupStatus.NO_MODEL -> Triple("Can't find a voice model", "Your key works, but no live voice models showed up. Try refreshing.", "Refresh")
        SetupStatus.READY -> return
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("setup_card")) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            when {
                status == SetupStatus.CHECKING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                action != null -> FilledTonalButton(onClick = if (status == SetupStatus.NO_MODEL || status == SetupStatus.OFFLINE) onRefresh else onOpenSettings) { Text(action) }
            }
        }
    }
}

@Composable
private fun ReviewCard(count: Int, onReview: () -> Unit) {
    OutlinedCard(onClick = onReview, modifier = Modifier.fillMaxWidth().testTag("review_card")) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Text(
                "$count new word${if (count == 1) "" else "s"} from your last walk",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) { Text("Review") }
        }
    }
}

@Composable
private fun SessionButton(
    active: Boolean,
    ready: Boolean,
    ending: Boolean,
    connecting: Boolean,
    muted: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!active) {
            if (ready) {
                Button(onClick = onStart, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(20.dp)) {
                    Text("Start Walk Session", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                FilledTonalButton(onClick = onStart, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(20.dp)) {
                    Text("Finish setup", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            Button(
                onClick = onEnd,
                enabled = !ending,
                modifier = Modifier.weight(1f).height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                if (connecting || ending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(if (ending) "Writing recap…" else "Connecting…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("End Walk Session", style = MaterialTheme.typography.titleMedium)
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

/** What happened + what to do, one primary action, raw detail behind "Details". */
@Composable
private fun ErrorCard(error: SessionError, onAction: () -> Unit, onDismiss: () -> Unit) {
    var showDetail by remember(error) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth().testTag("error_card")) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Text(error.title, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleSmall)
            }
            Text(error.body, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            AnimatedVisibility(visible = showDetail && error.detail.isNotBlank()) {
                Text(error.detail, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (error.detail.isNotBlank()) {
                    TextButton(onClick = { showDetail = !showDetail }) { Text(if (showDetail) "Hide details" else "Details") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                FilledTonalButton(onClick = onAction) { Text(error.action.label) }
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
