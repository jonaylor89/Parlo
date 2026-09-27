package com.parlo.app.ui.vocab

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlo.app.ParloApp
import com.parlo.app.data.db.VocabEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabListScreen(onBack: () -> Unit) {
    val repo = ParloApp.container(LocalContext.current).vocab
    val all by repo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var flashcardMode by remember { mutableStateOf(false) }
    val grouped = all.groupBy { it.language }.toSortedMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vocab (${all.size})") },
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
                    "No words yet.\nSay \"save that word\" during a walk and the tutor will add it here.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    VocabCard(v, flashcardMode) { scope.launch { repo.delete(v) } }
                }
            }
        }
    }
}

@Composable
private fun VocabCard(v: VocabEntity, flashcard: Boolean, onDelete: () -> Unit) {
    var revealed by remember(v.id, flashcard) { mutableStateOf(!flashcard) }
    Card(Modifier.fillMaxWidth().clickable(enabled = flashcard) { revealed = !revealed }) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(v.word, style = MaterialTheme.typography.titleMedium)
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
