package app.wavelet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wavelet.data.DefaultSources
import app.wavelet.data.SourceConfig
import app.wavelet.source.Engines

@Composable
fun SourcesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.appState.collectAsStateWithLifecycle()
    val repoView by vm.repoView.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SourceConfig?>(null) }
    var choosingServer by rememberSaveable { mutableStateOf(false) }
    var addingRepo by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Sources", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Sources supply music, lyrics and links. Turn them on or off, add your own servers, or install sources from a repo.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        items(state.sources, key = { it.instanceId }) { cfg ->
            SourceCard(
                cfg = cfg,
                onToggle = { enabled ->
                    if (enabled && Engines.missingOptions(cfg).isNotEmpty()) editing = cfg.copy(enabled = true)
                    else vm.setEnabled(cfg.instanceId, enabled)
                },
                onEdit = { editing = cfg },
                onRemove = if (cfg.instanceId in DefaultSources.ids) null else ({ vm.removeSource(cfg.instanceId) }),
            )
        }
        item {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { choosingServer = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add server")
                }
                OutlinedButton(onClick = { addingRepo = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add repo")
                }
            }
        }
        if (state.repos.isNotEmpty()) {
            item {
                Text("Repos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }
            items(state.repos, key = { "repo-$it" }) { url ->
                ListItem(
                    headlineContent = { Text(url, maxLines = 2) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.openRepo(url) }) { Icon(Icons.Rounded.Refresh, contentDescription = "Open repo") }
                            IconButton(onClick = { vm.removeRepo(url) }) { Icon(Icons.Rounded.Delete, contentDescription = "Remove repo") }
                        }
                    },
                )
            }
        }
    }

    editing?.let { cfg ->
        SourceSettingsDialog(
            initial = cfg,
            onDismiss = { editing = null },
            onSave = {
                vm.saveSource(it)
                editing = null
            },
        )
    }

    if (choosingServer) {
        AlertDialog(
            onDismissRequest = { choosingServer = false },
            title = { Text("Add a server") },
            text = {
                Column {
                    Engines.all.filter { it.multiInstance }.forEach { info ->
                        ListItem(
                            headlineContent = { Text(info.displayName) },
                            supportingContent = { Text(info.description) },
                            modifier = Modifier.padding(vertical = 2.dp),
                            trailingContent = {
                                TextButton(onClick = {
                                    choosingServer = false
                                    editing = vm.newServerConfig(info.engine)
                                }) { Text("Choose") }
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingServer = false }) { Text("Cancel") } },
        )
    }

    if (addingRepo) {
        var url by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingRepo = false },
            title = { Text("Add a repo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste the link to a repo's sources.json file.")
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        placeholder = { Text("https://example.com/sources.json") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = url.isNotBlank(), onClick = {
                    addingRepo = false
                    vm.openRepo(url)
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { addingRepo = false }) { Text("Cancel") } },
        )
    }

    repoView?.let { view ->
        val installed = state.sources.map { it.instanceId }.toSet()
        AlertDialog(
            onDismissRequest = vm::closeRepo,
            title = { Text(view.index?.name ?: "Repo") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    when {
                        view.loading -> CircularProgressIndicator()
                        view.error != null -> Text("Couldn't open this repo: ${view.error}", color = MaterialTheme.colorScheme.error)
                        else -> {
                            val all = view.index?.sources.orEmpty()
                            val usable = all.filter { it.unsupportedReason == null && Engines.info(it.engine) != null }
                            val unusable = all - usable.toSet()
                            view.index?.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
                            if (usable.size > 1) {
                                TextButton(onClick = { vm.installAllFromRepo(view.url, usable) }) { Text("Install all that need no setup") }
                            }
                            if (usable.isNotEmpty()) {
                                Text("Available in Wavelet", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                            }
                            usable.forEach { entry ->
                                ListItem(
                                    headlineContent = { Text(entry.name) },
                                    supportingContent = { Text(entry.description ?: Engines.info(entry.engine)!!.description) },
                                    trailingContent = {
                                        TextButton(onClick = {
                                            val cfg = vm.installFromRepo(view.url, entry)
                                            if (cfg != null && Engines.missingOptions(cfg).isNotEmpty()) {
                                                vm.closeRepo()
                                                editing = cfg
                                            }
                                        }) { Text(if (entry.id in installed) "Update" else "Install") }
                                    },
                                )
                            }
                            if (unusable.isNotEmpty()) {
                                Text(
                                    "Not available (${unusable.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                unusable.forEach { entry ->
                                    ListItem(
                                        headlineContent = { Text(entry.name, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        supportingContent = {
                                            Text(
                                                entry.unsupportedReason ?: "Needs a newer version of Wavelet",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::closeRepo) { Text("Done") } },
        )
    }
}

@Composable
private fun SourceCard(
    cfg: SourceConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val info = Engines.info(cfg.engine)
    val missing = Engines.missingOptions(cfg)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cfg.name, style = MaterialTheme.typography.titleSmall)
                val detail = when {
                    info == null -> "Unknown source type \"${cfg.engine}\". Update Wavelet to use it."
                    cfg.enabled && missing.isNotEmpty() -> "Needs setup: ${missing.joinToString { it.label }}"
                    else -> info.description
                }
                Text(detail, style = MaterialTheme.typography.bodySmall)
                val origin = listOfNotNull(
                    cfg.options["url"]?.takeIf { it.isNotBlank() },
                    cfg.fromRepo?.let { "From repo" + (cfg.version?.let { v -> " · v$v" } ?: "") },
                ).joinToString(" · ")
                if (origin.isNotEmpty()) Text(origin, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (info != null && (info.options.isNotEmpty() || info.multiInstance)) {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Settings, contentDescription = "Settings for ${cfg.name}") }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, contentDescription = "Remove ${cfg.name}") }
            }
            Switch(checked = cfg.enabled, onCheckedChange = onToggle, enabled = info != null)
        }
    }
}

@Composable
private fun SourceSettingsDialog(initial: SourceConfig, onDismiss: () -> Unit, onSave: (SourceConfig) -> Unit) {
    val info = Engines.info(initial.engine) ?: return
    var name by remember(initial.instanceId) { mutableStateOf(initial.name) }
    val values = remember(initial.instanceId) {
        mutableStateMapOf<String, String>().apply { putAll(initial.options) }
    }
    val complete = name.isNotBlank() && info.options.all { !values[it.key].isNullOrBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.displayName) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(info.description, style = MaterialTheme.typography.bodySmall)
                if (info.multiInstance) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                }
                info.options.forEach { opt ->
                    OutlinedTextField(
                        value = values[opt.key].orEmpty(),
                        onValueChange = { values[opt.key] = it.trim() },
                        label = { Text(opt.label) },
                        placeholder = { if (opt.hint.isNotEmpty()) Text(opt.hint) },
                        singleLine = true,
                        visualTransformation = if (opt.secret) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when {
                                opt.secret -> KeyboardType.Password
                                opt.key == "url" -> KeyboardType.Uri
                                else -> KeyboardType.Text
                            },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = complete, onClick = {
                onSave(initial.copy(name = name.trim(), options = values.toMap(), enabled = true))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
