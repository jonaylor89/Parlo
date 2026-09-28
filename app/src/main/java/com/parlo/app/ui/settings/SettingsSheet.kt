package com.parlo.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlo.app.model.VoiceCatalog
import com.parlo.app.model.VoiceGender
import com.parlo.app.ui.MainViewModel
import com.parlo.app.ui.SetupStatus
import com.parlo.app.ui.VoicePreview
import com.parlo.app.ui.main.ComboBox

private const val AI_STUDIO_KEYS = "https://aistudio.google.com/app/apikey"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var apiKeyDraft by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf(!ui.hasApiKey) }
    var showAdvanced by remember { mutableStateOf(ui.config.model.isNotBlank()) }
    val keyFailed = ui.setup == SetupStatus.BAD_KEY

    fun openAiStudio() = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_KEYS)))

    DisposableEffect(Unit) { onDispose { viewModel.stopPreview() } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Text("Gemini", style = MaterialTheme.typography.labelLarge)
            if (editingKey || keyFailed) {
                if (!ui.hasApiKey) {
                    Text(
                        "Parlo talks through your own Gemini key — free from Google AI Studio. It's stored encrypted on this phone and never leaves it except to reach Gemini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text("Gemini API key") },
                    placeholder = { Text("AIza…") },
                    singleLine = true,
                    isError = keyFailed && apiKeyDraft.isBlank(),
                    supportingText = {
                        if (keyFailed && apiKeyDraft.isBlank()) Text("That key didn't work — check it was copied fully, or make a new one.")
                    },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (keyVisible) "Hide key" else "Show key")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_field"),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = ::openAiStudio) {
                        Text("Get a free key")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (ui.hasApiKey && !keyFailed) TextButton(onClick = { editingKey = false }) { Text("Cancel") }
                    Button(
                        onClick = { viewModel.setApiKey(apiKeyDraft); apiKeyDraft = ""; editingKey = false },
                        enabled = apiKeyDraft.isNotBlank(),
                    ) { Text("Save key") }
                }
            } else {
                ConnectionRow(ui.setup, model = ui.config.model.ifBlank { ui.models.firstOrNull().orEmpty() }, onRefresh = viewModel::refreshModels)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { editingKey = true }) { Text("Replace key") }
                }
            }

            HorizontalDivider()
            VoiceSection(
                current = ui.config.voice,
                preview = ui.voicePreview,
                canPreview = ui.hasApiKey && !ui.live.isActive,
                onSelect = { v -> viewModel.updateConfig { it.copy(voice = v) } },
                onPreview = viewModel::previewVoice,
                onStopPreview = viewModel::stopPreview,
            )
            if (ui.live.isActive) {
                Text("Changing the voice or model reconnects the walk; the conversation carries over.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            Text("Appearance", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Match wallpaper colours", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Use Material You instead of Parlo's teal and coral." else "Needs Android 12 or newer.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = ui.dynamicColor, onCheckedChange = viewModel::setDynamicColor, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            }

            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Advanced", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAdvanced = !showAdvanced }) {
                    Icon(if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (showAdvanced) "Hide advanced" else "Show advanced")
                }
            }
            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Parlo picks the newest Gemini voice model automatically. Override it only if a specific model works better for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ComboBox(
                        label = if (ui.config.model.isBlank()) "Automatic" else "Model override",
                        value = ui.config.model,
                        suggestions = ui.models,
                        onValueChange = { m -> viewModel.updateConfig { it.copy(model = m) } },
                    )
                    if (ui.config.model.isNotBlank()) {
                        TextButton(onClick = { viewModel.updateConfig { it.copy(model = "") } }) { Text("Back to automatic") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSection(
    current: String,
    preview: VoicePreview,
    canPreview: Boolean,
    onSelect: (String) -> Unit,
    onPreview: (String) -> Unit,
    onStopPreview: () -> Unit,
) {
    val selected = VoiceCatalog.find(current)
    val busy = preview.loading ?: preview.playing
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Voice", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        when {
            preview.loading != null -> {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Fetching ${preview.loading}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            preview.playing != null -> {
                TextButton(onClick = onStopPreview, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Playing ${preview.playing}")
                }
            }
            selected != null && canPreview -> {
                TextButton(onClick = { onPreview(selected.name) }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.testTag("voice_preview")) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Hear ${selected.name}")
                }
            }
            else -> Text(
                selected?.let { "${it.name} · ${it.character}" } ?: current,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    preview.error?.let {
        Text("Couldn't play a sample — $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    VoiceGender.entries.forEach { gender ->
        Text(gender.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxWidth().testTag("voices_${gender.name.lowercase()}")) {
            VoiceCatalog.byGender(gender).forEach { v ->
                val isSelected = v == selected
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSelect(v.name)
                        if (canPreview) onPreview(v.name)
                    },
                    label = { Text("${v.name} · ${v.character}") },
                    leadingIcon = when {
                        v.name == busy -> ({ Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(FilterChipDefaults.IconSize)) })
                        isSelected -> ({ Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) })
                        else -> null
                    },
                )
            }
        }
    }
    Text(
        if (canPreview) "Tap a voice to hear it greet you in your current language, then start a walk to use it."
        else if (busy == null && preview.error == null) "Add a Gemini key above to hear a sample of each voice."
        else "Applies to your next walk.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConnectionRow(status: SetupStatus, model: String, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("connection_row")) {
        when (status) {
            SetupStatus.CHECKING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            SetupStatus.READY -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
            else -> Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when (status) {
                    SetupStatus.CHECKING -> "Checking your key…"
                    SetupStatus.READY -> "Connected"
                    SetupStatus.OFFLINE -> "Couldn't reach Gemini"
                    SetupStatus.NO_MODEL -> "No voice model found"
                    SetupStatus.BAD_KEY, SetupStatus.NO_KEY -> "Key not working"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                when (status) {
                    SetupStatus.READY -> "Using ${model.removePrefix("models/")} · key encrypted on device"
                    SetupStatus.CHECKING -> "Looking for voice models this key can use"
                    SetupStatus.OFFLINE -> "Check your connection and try again"
                    SetupStatus.NO_MODEL -> "The key works, but Gemini offered no live voice models"
                    else -> "Replace the key below"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (status == SetupStatus.OFFLINE || status == SetupStatus.NO_MODEL || status == SetupStatus.READY) {
            TextButton(onClick = onRefresh, enabled = status != SetupStatus.CHECKING) { Text("Refresh") }
        }
    }
}
