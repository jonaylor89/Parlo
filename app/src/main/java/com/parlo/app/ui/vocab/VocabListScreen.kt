package com.parlo.app.ui.vocab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlo.app.ParloApp
import com.parlo.app.data.db.VocabEntity
import com.parlo.app.data.db.VocabSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabListScreen(onBack: () -> Unit) {
    val repo = ParloApp.container(LocalContext.current).vocab
    val all by repo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var flashcardMode by remember { mutableStateOf(false) }
    val suggested = all.filter { it.isSuggested }
    val kept = all.filterNot { it.isSuggested }
    val grouped = kept.groupBy { it.language }.toSortedMap()

    /** Runs [action], then offers Undo for a few seconds. */
    fun undoable(message: String, action: suspend () -> Unit, undo: suspend () -> Unit) {
        scope.launch {
            action()
            val r = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) undo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Vocabulary") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    FilterChip(
                        selected = flashcardMode,
                        onClick = { flashcardMode = !flashcardMode },
                        label = { Text("Flashcards") },
                        leadingIcon = { Icon(Icons.Filled.Style, null) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        if (all.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No words yet.\nWords you ask about or get stuck on are picked up automatically during a walk, and you can always say \"save that word\".",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (suggested.isNotEmpty()) {
                item(key = "header-suggested") {
                    val snapshot = suggested
                    SuggestedHeader(
                        count = suggested.size,
                        onKeepAll = { undoable("Kept ${snapshot.size} words", { repo.keepAllSuggested() }, { repo.unkeep(snapshot) }) },
                        onDismissAll = { undoable("Skipped ${snapshot.size} words", { repo.dismissAllSuggested() }, { repo.restore(snapshot) }) },
                    )
                }
                items(suggested, key = { "s-${it.id}" }) { v ->
                    SuggestedCard(
                        v,
                        onKeep = { undoable("Kept “${v.word}”", { repo.keep(v) }, { repo.unkeep(listOf(v)) }) },
                        onDismiss = { undoable("Skipped “${v.word}”", { repo.delete(v) }, { repo.restore(listOf(v)) }) },
                    )
                }
                if (kept.isNotEmpty()) {
                    item(key = "header-kept") {
                        Text(
                            "Saved",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
            grouped.forEach { (language, words) ->
                item(key = "header-$language") {
                    Text(
                        "$language · ${words.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(words, key = { it.id }) { v ->
                    VocabCard(v, flashcardMode) { undoable("Deleted “${v.word}”", { repo.delete(v) }, { repo.restore(listOf(v)) }) }
                }
            }
        }
    }
}

@Composable
private fun SuggestedHeader(count: Int, onKeepAll: () -> Unit, onDismissAll: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                "  Suggested · $count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Picked up automatically from your walks. Keep what's useful.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismissAll) { Text("Skip all") }
            TextButton(onClick = onKeepAll) { Text("Keep all") }
        }
    }
}

@Composable
private fun SuggestedCard(v: VocabEntity, onKeep: () -> Unit, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().semantics { contentDescription = "Suggested ${v.word}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 8.dp)) {
            Text(v.word, style = MaterialTheme.typography.titleMedium)
            if (v.translation.isNotBlank()) Text(v.translation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (v.exampleSentence.isNotBlank()) {
                Text(v.exampleSentence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                listOf(v.language, v.reason.ifBlank { sourceLabel(v) }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "Skip ${v.word}" }) { Text("Skip") }
                Button(onClick = onKeep, modifier = Modifier.semantics { contentDescription = "Keep ${v.word}" }) { Text("Keep") }
            }
        }
    }
}

private fun sourceLabel(v: VocabEntity) = when (v.source) {
    VocabSource.TUTOR.name -> "Noted by the tutor"
    VocabSource.MINED.name -> "Found in your transcript"
    else -> "Saved by you"
}

@Composable
private fun VocabCard(v: VocabEntity, flashcard: Boolean, onDelete: () -> Unit) {
    var revealed by remember(v.id, flashcard) { mutableStateOf(!flashcard) }
    Card(Modifier.fillMaxWidth().clickable(enabled = flashcard) { revealed = !revealed }) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(v.word, style = MaterialTheme.typography.titleMedium)
                    if (v.source != VocabSource.MANUAL.name) {
                        Icon(
                            Icons.Filled.AutoAwesome, "Captured automatically",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 6.dp).size(14.dp),
                        )
                    }
                }
                AnimatedVisibility(visible = revealed) {
                    Column {
                        if (v.translation.isNotBlank()) Text(v.translation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        if (v.exampleSentence.isNotBlank()) {
                            Text(v.exampleSentence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!revealed) Text("tap to reveal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
