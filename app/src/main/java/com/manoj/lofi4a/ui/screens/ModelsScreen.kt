package com.manoj.lofi4a.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manoj.lofi4a.core.ModelDefinition
import com.manoj.lofi4a.core.ModelType
import com.manoj.lofi4a.ui.models.ModelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit, vm: ModelsViewModel = viewModel()) {
    val status by vm.status.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val models by vm.models.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Models") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(models, key = { it.id }) { def ->
                ModelCard(
                    def = def,
                    status = status.stateFor(def.type),
                    progress = progress[def.id],
                    onDownload = { vm.download(def) },
                    onLoad = { vm.load(def) },
                    onUnload = { vm.unload(def.type) }
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    def: ModelDefinition,
    status: com.manoj.lofi4a.core.ModelState,
    progress: Float?,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(def.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${def.sizeLabel} · ${def.licenseName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            when {
                progress != null && progress < 1f -> {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Downloading ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                status == com.manoj.lofi4a.core.ModelState.LOADED -> {
                    Button(onClick = onUnload, modifier = Modifier.fillMaxWidth()) {
                        Text("Unload")
                    }
                }
                status == com.manoj.lofi4a.core.ModelState.LOADING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading...", style = MaterialTheme.typography.labelMedium)
                    }
                }
                status == com.manoj.lofi4a.core.ModelState.DOWNLOADED -> {
                    Button(onClick = onLoad, modifier = Modifier.fillMaxWidth()) {
                        Text("Load")
                    }
                }
                else -> {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Download")
                    }
                }
            }
        }
    }
}
