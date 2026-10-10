package com.manoj.lofi4a

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manoj.lofi4a.ui.AppNavHost
import com.manoj.lofi4a.ui.theme.LoFiTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crashFile = File(filesDir, LoFiApp.CRASH_FILE)

        setContent {
            LoFiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var crash by remember {
                        mutableStateOf(if (crashFile.exists()) crashFile.readText() else null)
                    }
                    val text = crash
                    if (text != null) {
                        CrashScreen(
                            text = text,
                            onDismiss = {
                                crashFile.delete()
                                crash = null
                            }
                        )
                    } else {
                        AppNavHost()
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashScreen(text: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "The app crashed last time. Long-press the text below to copy it and send it to me.",
            style = MaterialTheme.typography.titleMedium
        )
        Button(onClick = onDismiss) { Text("Dismiss and continue") }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
