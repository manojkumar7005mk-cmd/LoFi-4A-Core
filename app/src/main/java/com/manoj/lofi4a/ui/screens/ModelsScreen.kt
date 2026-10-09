package com.manoj.lofi4a.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manoj.lofi4a.core.ModelDefinition
import com.manoj.lofi4a.core.ModelState
import com.manoj.lofi4a.ui.models.ModelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit, vm: ModelsViewModel = viewModel()) {
    val status by vm.status.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val models by vm.models.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Models", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Download both models once. After that StudyMate works fully offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(models, key = { it.id }) { def ->
                ModelCard(
                    def = def,
                    state = status.stateFor(def.type),
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
    state: ModelState,
    progress: Float?,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit
) {
    val downloading = progress != null
    val (label, dot) = when {
        downloading -> "Downloading" to Color(0xFFF9AB00)
        state == ModelState.LOADED -> "Ready" to Color(0xFF34A853)
        state == ModelState.DOWNLOADED -> "Downloaded" to Color(0xFF4285F4)
        else -> "Not downloaded" to Color(0xFF9AA0A6)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        def.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        def.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        def.sizeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                downloading -> {
                    val p = (progress ?: 0f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Downloading ${(p * 100).toInt()}%  ·  keep the app open",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state == ModelState.LOADED -> {
                    OutlinedButton(onClick = onUnload, modifier = Modifier.fillMaxWidth()) {
                        Text("Unload from memory")
                    }
                }
                state == ModelState.DOWNLOADED -> {
                    FilledTonalButton(onClick = onLoad, modifier = Modifier.fillMaxWidth()) {
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
