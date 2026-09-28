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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlo.app.ParloApp
import com.parlo.app.data.VocabCapture
import com.parlo.app.data.db.SessionEntity
import com.parlo.app.model.Level
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
    val snackbar = remember { SnackbarHostState() }
    // Deletion is deferred until the Undo snackbar times out, so undo is just "don't delete".
    var pendingDelete by remember { mutableStateOf(setOf<Long>()) }
    val visible = sessions.filterNot { it.id in pendingDelete }

    fun deleteWithUndo(id: Long) {
        pendingDelete = pendingDelete + id
        scope.launch {
            val r = snackbar.showSnackbar("Walk deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r != SnackbarResult.ActionPerformed) repo.deleteSession(id)
            pendingDelete = pendingDelete - id
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Past walks") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No walks yet.\nEvery walk's transcript and recap lands here.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val grouped = visible.groupBy { weekLabel(it.startedAt) }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            grouped.forEach { (week, list) ->
                item(key = "week-$week") {
                    Text(week, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
                items(list, key = { it.id }) { s ->
                    SessionCard(s, onClick = { onOpen(s.id) }, onDelete = { deleteWithUndo(s.id) })
                }
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
                Text(
                    "${formatDate(s.startedAt)} · ${formatDuration(s.durationMs)}" + if (s.endedAt == null) " · in progress" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.recap?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.replace('\n', ' '),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete walk", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit) {
    val container = ParloApp.container(LocalContext.current)
    val repo = container.sessions
    val capture = container.vocabCapture
    val data by repo.observeSessionWithTurns(sessionId).collectAsStateWithLifecycle(initialValue = null)
    val mining by capture.mining.collectAsStateWithLifecycle()
    val s = data?.session
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(s?.let { it.dialect.ifBlank { it.language } } ?: "Walk", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val turns = data?.turns.orEmpty()
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s != null) {
                item {
                    Text(
                        "${Level.parse(s.level).label} · ${formatDate(s.startedAt)} · ${formatDuration(s.durationMs)} · ${turns.size} turns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (!s.recap.isNullOrBlank()) {
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Recap", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(s.recap, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
                if (s.endedAt != null) {
                    item {
                        AssistChip(
                            enabled = sessionId !in mining,
                            onClick = {
                                scope.launch {
                                    val msg = when (val o = capture.mine(sessionId, force = true)) {
                                        is VocabCapture.Outcome.Found -> "${o.count} new word${if (o.count == 1) "" else "s"} waiting in Vocabulary"
                                        VocabCapture.Outcome.NothingNew -> "Nothing new to suggest"
                                        VocabCapture.Outcome.TooShort -> "Too short a conversation to mine"
                                        VocabCapture.Outcome.NoApiKey -> "Add a Gemini key in Settings first"
                                        is VocabCapture.Outcome.Failed -> "Couldn't find words: ${o.message}"
                                    }
                                    snackbar.showSnackbar(msg)
                                }
                            },
                            label = { Text(if (sessionId in mining) "Finding words…" else "Find words in this walk") },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
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
    val m = ms / 60_000
    return when {
        m >= 60 -> "${m / 60} h ${m % 60} min"
        m >= 1 -> "$m min"
        else -> "under a minute"
    }
}

private fun weekLabel(ms: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val weekDiff = if (sameYear) now.get(java.util.Calendar.WEEK_OF_YEAR) - then.get(java.util.Calendar.WEEK_OF_YEAR) else Int.MAX_VALUE
    return when {
        weekDiff <= 0 && sameYear -> "This week"
        weekDiff == 1 -> "Last week"
        else -> java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(Date(ms))
    }
}
