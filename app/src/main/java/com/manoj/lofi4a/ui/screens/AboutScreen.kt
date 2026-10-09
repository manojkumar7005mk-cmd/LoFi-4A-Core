package com.manoj.lofi4a.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class LicenseEntry(val name: String, val license: String, val note: String)

private val backendModels = listOf(
    Triple("Text", "Gemma 3 1B Instruct (GGUF Q4_K_M)", "Gemma Terms of Use"),
    Triple("Vision", "LFM2.5-VL 450M (GGUF + mmproj)", "LFM Open License"),
    Triple("Speech", "Whisper Base (GGUF)", "MIT")
)

private val tech = listOf(
    "llama.cpp + mtmd — text & vision inference (MIT)",
    "whisper.cpp — speech recognition (MIT)",
    "Android Jetpack Compose — UI (Apache 2.0)",
    "Kotlin — programming language (Apache 2.0)"
)

private val licenses = listOf(
    LicenseEntry("llama.cpp", "MIT", "https://github.com/ggml-org/llama.cpp"),
    LicenseEntry("whisper.cpp", "MIT", "https://github.com/ggml-org/whisper.cpp"),
    LicenseEntry("Qwen3", "Apache 2.0", "https://huggingface.co/Qwen/Qwen3-1.7B"),
    LicenseEntry("LFM2.5-VL", "LFM Open License", "https://www.liquid.ai/"),
    LicenseEntry("OpenAI Whisper", "MIT", "https://github.com/openai/whisper"),
    LicenseEntry("ggml", "MIT", "https://github.com/ggml-org/ggml")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About StudyMate AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🎓 StudyMate AI", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Your friendly offline study buddy. It explains lessons, solves sums step by step, " +
                    "and reads pages from your books, all on your phone.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Designed and developed by Manoj Kumar, a student.",
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Text("Backend Models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            backendModels.forEach { (role, model, lic) ->
                Column {
                    Text(role, style = MaterialTheme.typography.labelLarge)
                    Text(model, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "License: $lic",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            Text("Technologies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            tech.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }

            HorizontalDivider()

            Text(
                "Third-Party Licenses & Attributions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            licenses.forEach { l ->
                Column {
                    Text(l.name, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${l.license} — ${l.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "All models run fully on-device. No data leaves your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
