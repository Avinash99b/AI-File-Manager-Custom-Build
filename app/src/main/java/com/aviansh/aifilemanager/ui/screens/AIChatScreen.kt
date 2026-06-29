package com.aviansh.aifilemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aviansh.aifilemanager.domain.ai.providers.GeminiAIProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen() {

    val aiProvider = GeminiAIProvider()
    var prompt by remember {
        mutableStateOf("")
    }

    val messages = remember {
        mutableStateListOf<String>()
    }

    Scaffold(

        topBar = {

            TopAppBar(
                title = {
                    Text("AI File Manager")
                }
            )

        }

    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {

                items(messages) {

                    Text(
                        text = it,
                        modifier = Modifier.padding(8.dp)
                    )

                }

            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {

                OutlinedTextField(

                    modifier = Modifier.weight(1f),

                    value = prompt,

                    onValueChange = {
                        prompt = it
                    },

                    placeholder = {
                        Text("Ask AI...")
                    }

                )

                Spacer(Modifier.width(8.dp))

                FilledIconButton(

                    onClick = {

                        if (prompt.isBlank())
                            return@FilledIconButton

                        messages += "👤 $prompt"

                        // TODO
//                         val response = aiEngine.startInteraction(prompt)
//
//                         messages += "🤖 ${response.message}"

                        prompt = ""

                    }

                ) {

                    Icon(
                        Icons.Default.Send,
                        null
                    )

                }

            }

        }

    }

}