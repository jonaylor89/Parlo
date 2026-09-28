package com.parlo.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parlo.app.model.Dialect
import com.parlo.app.model.Language
import com.parlo.app.model.LanguageCatalog

/** Summary card on the main screen. Tapping opens [LanguagePickerSheet]. */
@Composable
fun LanguageCard(
    language: String,
    dialect: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = LanguageCatalog.find(language)
    val dialectEntry = entry?.dialects?.firstOrNull { it.name.equals(dialect, true) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("language_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Flag(dialectEntry?.flag ?: entry?.flag)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildString {
                        append(language.ifBlank { "Choose a language" })
                        entry?.nativeName?.takeIf { it != language }?.let { append("  ·  \u2068").append(it).append('\u2069') }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dialectEntry?.let { "${it.name}  ·  ${it.region}" } ?: dialect.ifBlank { "Any accent" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Change language and accent", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class Step { LANGUAGE, DIALECT }

/**
 * Two-step picker: language (search + popular + by region) → accent (search + list).
 * Both steps end with a "Custom…" escape hatch so nothing is off-limits.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LanguagePickerSheet(
    language: String,
    dialect: String,
    onSelect: (language: String, dialect: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by rememberSaveable { mutableStateOf(Step.LANGUAGE) }
    var chosen by rememberSaveable { mutableStateOf(language) }
    var query by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf(false) }

    fun pick(lang: String, d: String) { onSelect(lang, d); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(Modifier.fillMaxHeight(0.94f)) {
            // Header
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step == Step.DIALECT) {
                    IconButton(onClick = { step = Step.LANGUAGE; query = ""; custom = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to languages")
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (step == Step.LANGUAGE) "Language" else "Accent",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    )
                    if (step == Step.DIALECT) {
                        val entry = LanguageCatalog.find(chosen)
                        Text(
                            entry?.let { "${it.flag} ${it.name} · ${it.dialects.size} accents" } ?: chosen,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it; custom = false },
                placeholder = { Text(if (step == Step.LANGUAGE) "Search languages, accents, places" else "Search accents in $chosen") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear search") } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (query.isNotBlank() && step == Step.DIALECT) pick(chosen, query.trim())
                }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("language_search"),
            )

            when (step) {
                Step.LANGUAGE -> LanguageList(
                    query = query,
                    current = language,
                    onLanguage = { l -> chosen = l.name; query = ""; step = Step.DIALECT },
                    onDialect = { l, d -> pick(l.name, d.name) },
                    onCustom = { name -> pick(name, name) },
                )
                Step.DIALECT -> DialectList(
                    language = chosen,
                    query = query,
                    current = dialect,
                    onDialect = { d -> pick(chosen, d) },
                )
            }
        }
    }
}

@Composable
private fun LanguageList(
    query: String,
    current: String,
    onLanguage: (Language) -> Unit,
    onDialect: (Language, Dialect) -> Unit,
    onCustom: (String) -> Unit,
) {
    val q = query.trim()
    LazyColumn(Modifier.fillMaxWidth()) {
        if (q.isEmpty()) {
            LanguageCatalog.find(current)?.let { l ->
                item(key = "current-hdr") { SectionHeader("Current") }
                item(key = "current-" + l.name) { LanguageRow(l, selected = true) { onLanguage(l) } }
            }
            item { SectionHeader("Popular") }
            items(LanguageCatalog.popular, key = { "pop-" + it.name }) { l -> LanguageRow(l, l.name == current) { onLanguage(l) } }
            LanguageCatalog.byRegion().forEach { (region, list) ->
                if (list.isEmpty()) return@forEach
                item(key = "hdr-" + region.name) { SectionHeader(region.label) }
                items(list, key = { it.name }) { l -> LanguageRow(l, l.name == current) { onLanguage(l) } }
            }
        } else {
            val hits = LanguageCatalog.search(q)
            if (hits.isEmpty()) {
                item { EmptyHint("No match in the catalog — you can still use \"$q\" as a custom language.") }
            }
            items(hits, key = { (it.language.name + "/" + (it.dialect?.name ?: "")) }) { hit ->
                val d = hit.dialect
                if (d == null) LanguageRow(hit.language, hit.language.name == current) { onLanguage(hit.language) }
                else DialectHitRow(hit.language, d) { onDialect(hit.language, d) }
            }
        }
        item(key = "custom") {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            CustomRow(
                title = if (q.isBlank()) "Custom language…" else "Use \"$q\" as the language",
                subtitle = "Any language or variety, in your own words",
                onClick = { if (q.isNotBlank()) onCustom(q) },
                query = q,
                onSubmit = onCustom,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DialectList(
    language: String,
    query: String,
    current: String,
    onDialect: (String) -> Unit,
) {
    val q = query.trim()
    val all = LanguageCatalog.dialectsOf(language)
    val shown = if (q.isEmpty()) all else all.filter { it.name.contains(q, true) || it.region.contains(q, true) }
    LazyColumn(Modifier.fillMaxWidth()) {
        if (all.isEmpty()) {
            item { EmptyHint("No accents catalogued for $language yet — type one below, or just use the language name.") }
            item { DialectRow(name = language, region = "Neutral / any accent", flag = null, selected = current.equals(language, true)) { onDialect(language) } }
        }
        if (q.isNotEmpty() && shown.isEmpty()) {
            item { EmptyHint("No catalogued accent matches \"$q\".") }
        }
        items(shown, key = { it.name }) { d ->
            DialectRow(name = d.name, region = d.region, flag = d.flag, selected = d.name.equals(current, true)) { onDialect(d.name) }
        }
        item(key = "custom") {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            CustomRow(
                title = if (q.isBlank()) "Custom accent…" else "Use \"$q\" as the accent",
                subtitle = "e.g. \"Rural Galician-accented Spanish\" or a city that isn't listed",
                onClick = { if (q.isNotBlank()) onDialect(q) },
                query = q,
                onSubmit = onDialect,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LanguageRow(l: Language, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(l.name, fontWeight = if (selected) FontWeight.SemiBold else null) },
        supportingContent = {
            Text(
                if (l.nativeName != l.name) "\u2068${l.nativeName}\u2069  ·  ${l.dialects.size} accents" else "${l.dialects.size} accents",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { Flag(l.flag) },
        trailingContent = {
            if (selected) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DialectHitRow(l: Language, d: Dialect, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(d.name) },
        supportingContent = { Text("${l.name}  ·  ${d.region}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Flag(d.flag) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DialectRow(name: String, region: String, flag: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name, fontWeight = if (selected) FontWeight.SemiBold else null) },
        supportingContent = { Text(region, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Flag(flag) },
        trailingContent = { if (selected) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CustomRow(
    title: String,
    subtitle: String,
    query: String,
    onClick: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    if (editing && query.isBlank()) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(title.removeSuffix("…")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (draft.isNotBlank()) onSubmit(draft.trim()) }),
            trailingIcon = {
                TextButton(onClick = { if (draft.isNotBlank()) onSubmit(draft.trim()) }, enabled = draft.isNotBlank()) { Text("Use") }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("custom_field"),
        )
    } else {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.clickable { if (query.isBlank()) editing = true else onClick() }.testTag("custom_row"),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
internal fun Flag(flag: String?) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (flag.isNullOrBlank()) Icon(Icons.Filled.Translate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(flag, style = MaterialTheme.typography.titleLarge)
        }
    }
}
