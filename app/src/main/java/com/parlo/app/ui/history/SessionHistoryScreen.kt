package com.parlo.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlo.app.ParloApp
import com.parlo.app.data.db.SessionEntity
import com.parlo.app.model.Level
import com.parlo.app.model.Scenario
import com.parlo.app.model.Speaker
import com.parlo.app.model.TranscriptTurn
import com.parlo.app.ui.main.TurnBubble
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val repo = ParloApp.container(LocalContext.current).sessions
    val sessions by repo.observeSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session history") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No walks yet.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sessions, key = { it.id }) { s ->
                SessionCard(s, onClick = { onOpen(s.id) }, onDelete = { scope.launch { repo.deleteSession(s.id) } })
            }
        }
    }
}

@Composable
private fun SessionCard(s: SessionEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.elevatedCardColors()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${s.dialect.ifBlank { s.language }} · ${Level.parse(s.level).label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(Scenario.parse(s.scenario).label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${formatDate(s.startedAt)} · ${formatDuration(s.durationMs)}" + if (s.endedAt == null) " · in progress" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit) {
    val repo = ParloApp.container(LocalContext.current).sessions
    val data by repo.observeSessionWithTurns(sessionId).collectAsStateWithLifecycle(initialValue = null)
    val s = data?.session

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.let { "${it.dialect.ifBlank { it.language }} · ${Level.parse(it.level).label}" } ?: "Session") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val turns = data?.turns.orEmpty()
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s != null) {
                item {
                    Text(
                        "${Scenario.parse(s.scenario).label} · ${formatDate(s.startedAt)} · ${formatDuration(s.durationMs)} · ${turns.size} turns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (!s.recap.isNullOrBlank()) {
                    item {
                        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Recap", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(s.recap, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
            }
            items(turns, key = { it.id }) { t ->
                TurnBubble(
                    TranscriptTurn(
                        id = t.id,
                        speaker = runCatching { Speaker.valueOf(t.speaker) }.getOrDefault(Speaker.SYSTEM),
                        text = t.text,
                        timestampMs = t.timestamp,
                    ),
                )
            }
        }
    }
}

private fun formatDate(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m ${s}s"
}
