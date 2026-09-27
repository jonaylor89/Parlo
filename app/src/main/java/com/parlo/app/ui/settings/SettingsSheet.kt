package com.parlo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlo.app.model.Defaults
import com.parlo.app.ui.MainViewModel
import com.parlo.app.ui.main.ComboBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var apiKeyDraft by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf(!ui.hasApiKey) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Text("Gemini API key", style = MaterialTheme.typography.labelLarge)
            if (editingKey) {
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text("Paste key from aistudio.google.com") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (ui.hasApiKey) TextButton(onClick = { editingKey = false }) { Text("Cancel") }
                    Button(onClick = { viewModel.setApiKey(apiKeyDraft); apiKeyDraft = ""; editingKey = false }, enabled = apiKeyDraft.isNotBlank()) { Text("Save key") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Key saved (encrypted on device)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editingKey = true }) { Text("Replace") }
                }
            }

            Text("Voice", style = MaterialTheme.typography.labelLarge)
            ComboBox(
                label = "Prebuilt voice",
                value = ui.config.voice,
                suggestions = Defaults.voices,
                onValueChange = { v -> viewModel.updateConfig { it.copy(voice = v) } },
            )
            if (ui.live.isActive) {
                Text("Changing voice or model reconnects the session (context is resumed).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Live model", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (ui.modelsLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                IconButton(onClick = viewModel::refreshModels, enabled = ui.hasApiKey && !ui.modelsLoading) { Icon(Icons.Filled.Refresh, "Refresh models") }
            }
            ComboBox(
                label = if (ui.config.model.isBlank()) "Auto (${ui.models.firstOrNull() ?: "none discovered"})" else "Model override",
                value = ui.config.model,
                suggestions = ui.models,
                onValueChange = { m -> viewModel.updateConfig { it.copy(model = m) } },
            )
            if (ui.config.model.isNotBlank()) {
                TextButton(onClick = { viewModel.updateConfig { it.copy(model = "") } }) { Text("Use auto-detected model") }
            }
            ui.modelsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Models are discovered from the Gemini API (filtered to those supporting bidiGenerateContent). Newest native-audio Live model is chosen by default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
