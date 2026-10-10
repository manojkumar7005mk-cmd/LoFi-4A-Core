package com.manoj.lofi4a.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manoj.lofi4a.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.SemiBold) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.studymate_logo),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("StudyMate", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Your offline study buddy",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                "StudyMate explains lessons, solves maths step by step, and reads pages from your books. " +
                    "Everything runs on your phone, so your questions and photos never leave the device.",
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Text("How it works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("• StudyMate Distilled Qwen writes the answers and teaches.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "• LightOnOCR-2 reads photos: it picks out the text, maths and notes on the page, " +
                    "then passes those notes to StudyMate.",
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Text("Open-source credits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Qwen3 (Alibaba Cloud) — Apache 2.0", style = MaterialTheme.typography.bodySmall)
            Text("LightOnOCR-2 (LightOn) — Apache 2.0", style = MaterialTheme.typography.bodySmall)
            Text("llama.cpp and ggml — MIT", style = MaterialTheme.typography.bodySmall)
            Text("ONNX Runtime (Microsoft) — MIT", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))
            Text(
                "A student project by Manoj Kumar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
