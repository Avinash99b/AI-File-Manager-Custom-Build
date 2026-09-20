package com.aviansh.aifilemanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviansh.aifilemanager.domain.ai.ProviderKind
import com.aviansh.aifilemanager.domain.repository.AiProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal val PresetGeminiModels = listOf(
    "gemini-3.1-flash-lite",
    "gemini-3.1-flash",
    "gemini-3.1-pro",
    "gemini-2.5-flash",
    "gemini-2.5-pro"
)

internal val PresetOpenAiModels = listOf(
    "gpt-4o-mini",
    "gpt-4o",
    "gpt-4.1-mini",
    "gpt-4.1",
    "o4-mini"
)

/** Handy one-tap presets for common OpenAI compatible gateways. */
internal data class EndpointPreset(val label: String, val baseUrl: String)

internal val OpenAiEndpointPresets = listOf(
    EndpointPreset("OpenAI", "https://api.openai.com/v1"),
    EndpointPreset("OpenRouter", "https://openrouter.ai/api/v1"),
    EndpointPreset("Groq", "https://api.groq.com/openai/v1"),
    EndpointPreset("Together", "https://api.together.xyz/v1"),
    EndpointPreset("Ollama (local)", "http://127.0.0.1:11434/v1"),
    EndpointPreset("LM Studio (local)", "http://127.0.0.1:1234/v1")
)

@Immutable
data class AiSettingsUiState(
    val selectedProvider: ProviderKind = ProviderKind.GEMINI,

    // Gemini
    val apiKey: String = "",
    val modelName: String = PresetGeminiModels.first(),
    val customModelEnabled: Boolean = false,
    val customModelText: String = "",

    // OpenAI compatible
    val openAiApiKey: String = "",
    val openAiModelName: String = PresetOpenAiModels.first(),
    val openAiCustomModelEnabled: Boolean = false,
    val openAiCustomModelText: String = "",
    val openAiBaseUrl: String = "https://api.openai.com/v1",

    val isApiKeyVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val isGeminiConfigured: Boolean = false,
    val isOpenAiConfigured: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val connectionResult: String? = null,
    val modified: Boolean = false
) {
    val isGemini: Boolean get() = selectedProvider == ProviderKind.GEMINI

    val effectiveModelName: String
        get() = if (isGemini) {
            if (customModelEnabled) customModelText.trim() else modelName.trim()
        } else {
            if (openAiCustomModelEnabled) openAiCustomModelText.trim() else openAiModelName.trim()
        }

    val effectiveApiKey: String
        get() = if (isGemini) apiKey.trim() else openAiApiKey.trim()

    val isConfigured: Boolean
        get() = if (isGemini) isGeminiConfigured else isOpenAiConfigured

    /** OpenAI compatible endpoints may be keyless (Ollama, LM Studio, vLLM, ...). */
    private val apiKeySatisfied: Boolean
        get() = if (isGemini) apiKey.isNotBlank() else true

    private val baseUrlSatisfied: Boolean
        get() = isGemini || openAiBaseUrl.isNotBlank()

    val canSave: Boolean
        get() = !isLoading && !isSaving && !isTesting && apiKeySatisfied && baseUrlSatisfied &&
            effectiveModelName.isNotBlank() && modified

    val canTest: Boolean
        get() = !isLoading && !isSaving && !isTesting && apiKeySatisfied && baseUrlSatisfied &&
            effectiveModelName.isNotBlank()

    val providerTitle: String
        get() = if (isGemini) "Gemini" else "OpenAI compatible"
}

sealed interface AiSettingsEvent {
    data class Snackbar(val message: String) : AiSettingsEvent
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val repository: AiProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AiSettingsEvent>()
    val events: SharedFlow<AiSettingsEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, connectionResult = null) }

            val selected = repository.getSelectedProvider()

            val apiKey = repository.getApiKey().orEmpty()
            val modelName = repository.getModelName().ifBlank { PresetGeminiModels.first() }
            val isCustom = modelName !in PresetGeminiModels

            val openAiKey = repository.getOpenAiApiKey().orEmpty()
            val openAiModel = repository.getOpenAiModelName().ifBlank { PresetOpenAiModels.first() }
            val openAiCustom = openAiModel !in PresetOpenAiModels
            val openAiBaseUrl = repository.getOpenAiBaseUrl()

            _uiState.update {
                it.copy(
                    selectedProvider = selected,
                    apiKey = apiKey,
                    modelName = if (isCustom) PresetGeminiModels.first() else modelName,
                    customModelEnabled = isCustom,
                    customModelText = if (isCustom) modelName else "",
                    openAiApiKey = openAiKey,
                    openAiModelName = if (openAiCustom) PresetOpenAiModels.first() else openAiModel,
                    openAiCustomModelEnabled = openAiCustom,
                    openAiCustomModelText = if (openAiCustom) openAiModel else "",
                    openAiBaseUrl = openAiBaseUrl,
                    isGeminiConfigured = repository.hasConfiguration(),
                    isOpenAiConfigured = repository.hasOpenAiConfiguration(),
                    isLoading = false,
                    modified = false
                )
            }
        }
    }

    fun onProviderChange(kind: ProviderKind) {
        if (_uiState.value.selectedProvider == kind) return
        _uiState.update { it.copy(selectedProvider = kind, connectionResult = null, modified = true) }
        // Persist immediately so the agent uses the newly picked provider even if the user
        // leaves the screen without pressing save.
        viewModelScope.launch {
            runCatching { repository.setSelectedProvider(kind) }
        }
    }

    // ---- Gemini inputs ----

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value, modified = true, connectionResult = null) }
    }

    fun onModelChange(value: String) {
        _uiState.update {
            it.copy(
                modelName = value,
                customModelEnabled = false,
                customModelText = "",
                modified = true,
                connectionResult = null
            )
        }
    }

    fun onCustomModelEnabledChange(enabled: Boolean) {
        _uiState.update {
            it.copy(
                customModelEnabled = enabled,
                customModelText = if (enabled && it.customModelText.isBlank()) it.modelName else it.customModelText,
                modified = true,
                connectionResult = null
            )
        }
    }

    fun onCustomModelTextChange(value: String) {
        _uiState.update { it.copy(customModelText = value, modified = true, connectionResult = null) }
    }

    // ---- OpenAI compatible inputs ----

    fun onOpenAiApiKeyChange(value: String) {
        _uiState.update { it.copy(openAiApiKey = value, modified = true, connectionResult = null) }
    }

    fun onOpenAiModelChange(value: String) {
        _uiState.update {
            it.copy(
                openAiModelName = value,
                openAiCustomModelEnabled = false,
                openAiCustomModelText = "",
                modified = true,
                connectionResult = null
            )
        }
    }

    fun onOpenAiCustomModelEnabledChange(enabled: Boolean) {
        _uiState.update {
            it.copy(
                openAiCustomModelEnabled = enabled,
                openAiCustomModelText = if (enabled && it.openAiCustomModelText.isBlank()) {
                    it.openAiModelName
                } else {
                    it.openAiCustomModelText
                },
                modified = true,
                connectionResult = null
            )
        }
    }

    fun onOpenAiCustomModelTextChange(value: String) {
        _uiState.update { it.copy(openAiCustomModelText = value, modified = true, connectionResult = null) }
    }

    fun onOpenAiBaseUrlChange(value: String) {
        _uiState.update { it.copy(openAiBaseUrl = value, modified = true, connectionResult = null) }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
    }

    fun showDeleteDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteDialog = show) }
    }

    fun save() {
        val state = _uiState.value
        val modelName = state.effectiveModelName

        if (state.isGemini && state.apiKey.isBlank()) {
            emitSnackbar("API key cannot be empty.")
            return
        }

        if (!state.isGemini && state.openAiBaseUrl.isBlank()) {
            emitSnackbar("Base URL cannot be empty.")
            return
        }

        if (!state.isGemini && !isValidHttpUrl(state.openAiBaseUrl)) {
            emitSnackbar("Base URL must start with http:// or https://")
            return
        }

        if (modelName.isBlank()) {
            emitSnackbar("Model name cannot be empty.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, connectionResult = null) }
            try {
                repository.setSelectedProvider(state.selectedProvider)

                if (state.isGemini) {
                    repository.save(apiKey = state.apiKey.trim(), modelName = modelName)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isGeminiConfigured = true,
                            modified = false,
                            connectionResult = "Saved successfully."
                        )
                    }
                } else {
                    repository.saveOpenAi(
                        apiKey = state.openAiApiKey.trim(),
                        modelName = modelName,
                        baseUrl = state.openAiBaseUrl.trim()
                    )
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isOpenAiConfigured = true,
                            modified = false,
                            connectionResult = "Saved successfully."
                        )
                    }
                }
                emitSnackbar("${state.providerTitle} configuration saved.")
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                emitSnackbar(e.message ?: "Failed to save configuration.")
            }
        }
    }

    fun delete() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, showDeleteDialog = false, connectionResult = null) }
            try {
                if (state.isGemini) {
                    repository.clearGemini()
                    _uiState.update {
                        it.copy(
                            apiKey = "",
                            modelName = PresetGeminiModels.first(),
                            customModelEnabled = false,
                            customModelText = "",
                            isSaving = false,
                            isGeminiConfigured = false,
                            modified = false,
                            connectionResult = "Configuration deleted."
                        )
                    }
                } else {
                    repository.clearOpenAi()
                    _uiState.update {
                        it.copy(
                            openAiApiKey = "",
                            openAiModelName = PresetOpenAiModels.first(),
                            openAiCustomModelEnabled = false,
                            openAiCustomModelText = "",
                            openAiBaseUrl = "https://api.openai.com/v1",
                            isSaving = false,
                            isOpenAiConfigured = false,
                            modified = false,
                            connectionResult = "Configuration deleted."
                        )
                    }
                }
                emitSnackbar("${state.providerTitle} configuration deleted.")
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                emitSnackbar(e.message ?: "Failed to delete configuration.")
            }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        val modelName = state.effectiveModelName

        if (state.isGemini && state.apiKey.isBlank()) {
            emitSnackbar("API key and model are required.")
            return
        }

        if (!state.isGemini && (state.openAiBaseUrl.isBlank() || !isValidHttpUrl(state.openAiBaseUrl))) {
            emitSnackbar("A valid base URL is required.")
            return
        }

        if (modelName.isBlank()) {
            emitSnackbar("API key and model are required.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, connectionResult = null) }
            try {
                val provider = if (state.isGemini) {
                    com.aviansh.aifilemanager.domain.ai.providers.GeminiAIProvider(
                        apiKey = state.apiKey.trim(),
                        modelName = modelName
                    )
                } else {
                    com.aviansh.aifilemanager.domain.ai.providers.OpenAICompatibleProvider(
                        apiKey = state.openAiApiKey.trim(),
                        modelName = modelName,
                        rawBaseUrl = state.openAiBaseUrl.trim()
                    )
                }

                val success = provider.test()
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        connectionResult = if (success) "Connection successful." else "Connection failed."
                    )
                }
                emitSnackbar(
                    if (success) "${state.providerTitle} connection looks good."
                    else "${state.providerTitle} test failed."
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        connectionResult = e.message ?: "Connection failed."
                    )
                }
                emitSnackbar(e.message ?: "Connection failed.")
            }
        }
    }

    private fun isValidHttpUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            _events.emit(AiSettingsEvent.Snackbar(message))
        }
    }
}

@Composable
fun AiSettingsRoute(
    viewModel: AiSettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AiSettingsEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    AiSettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onProviderChange = viewModel::onProviderChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onModelChange = viewModel::onModelChange,
        onCustomModelEnabledChange = viewModel::onCustomModelEnabledChange,
        onCustomModelTextChange = viewModel::onCustomModelTextChange,
        onOpenAiApiKeyChange = viewModel::onOpenAiApiKeyChange,
        onOpenAiModelChange = viewModel::onOpenAiModelChange,
        onOpenAiCustomModelEnabledChange = viewModel::onOpenAiCustomModelEnabledChange,
        onOpenAiCustomModelTextChange = viewModel::onOpenAiCustomModelTextChange,
        onOpenAiBaseUrlChange = viewModel::onOpenAiBaseUrlChange,
        onToggleApiKeyVisibility = viewModel::toggleApiKeyVisibility,
        onSave = viewModel::save,
        onTest = viewModel::testConnection,
        onDeleteClick = { viewModel.showDeleteDialog(true) },
        onConfirmDelete = viewModel::delete,
        onDismissDelete = { viewModel.showDeleteDialog(false) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    uiState: AiSettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onProviderChange: (ProviderKind) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onCustomModelEnabledChange: (Boolean) -> Unit,
    onCustomModelTextChange: (String) -> Unit,
    onOpenAiApiKeyChange: (String) -> Unit,
    onOpenAiModelChange: (String) -> Unit,
    onOpenAiCustomModelEnabledChange: (Boolean) -> Unit,
    onOpenAiCustomModelTextChange: (String) -> Unit,
    onOpenAiBaseUrlChange: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI provider",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Gemini or any OpenAI compatible endpoint",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading AI settings...")
                }
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroCard(
                    providerTitle = uiState.providerTitle,
                    configured = uiState.isConfigured,
                    modelName = uiState.effectiveModelName,
                    connection = uiState.connectionResult
                )

                // ---------- Provider picker ----------
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SectionTitle(
                            icon = Icons.Outlined.Cloud,
                            title = "Provider",
                            subtitle = "Pick which backend the AI agent talks to"
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = uiState.selectedProvider == ProviderKind.GEMINI,
                                onClick = { onProviderChange(ProviderKind.GEMINI) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Gemini")
                            }
                            SegmentedButton(
                                selected = uiState.selectedProvider == ProviderKind.OPENAI_COMPATIBLE,
                                onClick = { onProviderChange(ProviderKind.OPENAI_COMPATIBLE) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("OpenAI compatible")
                            }
                        }

                        Text(
                            text = if (uiState.isGemini) {
                                "Uses Google's Gemini API with your Google AI Studio key."
                            } else {
                                "Works with OpenAI, OpenRouter, Groq, Together, Azure-style gateways, " +
                                    "Ollama, LM Studio, vLLM — anything exposing /chat/completions."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ---------- Endpoint (OpenAI compatible only) ----------
                AnimatedVisibility(visible = !uiState.isGemini) {
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SectionTitle(
                                icon = Icons.Outlined.Link,
                                title = "Endpoint",
                                subtitle = "Base URL of the OpenAI compatible API"
                            )

                            OutlinedTextField(
                                value = uiState.openAiBaseUrl,
                                onValueChange = onOpenAiBaseUrlChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Base URL") },
                                placeholder = { Text("https://api.openai.com/v1") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                                supportingText = {
                                    Text("\"/v1\" is added automatically when missing. Requests go to <base>/chat/completions.")
                                },
                                shape = RoundedCornerShape(18.dp)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OpenAiEndpointPresets.chunked(2).forEach { rowPresets ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowPresets.forEach { preset ->
                                            AssistChip(
                                                onClick = { onOpenAiBaseUrlChange(preset.baseUrl) },
                                                label = {
                                                    Text(
                                                        text = preset.label,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------- API key ----------
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SectionTitle(
                            icon = Icons.Outlined.Lock,
                            title = "API key",
                            subtitle = if (uiState.isGemini) {
                                "Kept encrypted on this device"
                            } else {
                                "Kept encrypted on this device — leave empty for local servers"
                            }
                        )

                        val keyValue = if (uiState.isGemini) uiState.apiKey else uiState.openAiApiKey
                        val onKeyChange: (String) -> Unit =
                            if (uiState.isGemini) onApiKeyChange else onOpenAiApiKeyChange

                        OutlinedTextField(
                            value = keyValue,
                            onValueChange = onKeyChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (uiState.isGemini) "Gemini API key" else "API key") },
                            placeholder = { Text("Paste your API key") },
                            singleLine = true,
                            visualTransformation = if (uiState.isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        val clip = clipboardManager.getText()?.text.orEmpty()
                                        if (clip.isNotBlank()) onKeyChange(clip)
                                    }) {
                                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste")
                                    }
                                    IconButton(onClick = onToggleApiKeyVisibility) {
                                        Icon(
                                            imageVector = if (uiState.isApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (uiState.isApiKeyVisible) "Hide API key" else "Show API key"
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(18.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AssistChip(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text.orEmpty()
                                    if (clip.isNotBlank()) onKeyChange(clip)
                                },
                                label = { Text("Paste") },
                                leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) }
                            )
                            AssistChip(
                                onClick = { onKeyChange("") },
                                label = { Text("Clear") },
                                leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) }
                            )
                        }
                    }
                }

                // ---------- Model ----------
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SectionTitle(
                            icon = Icons.Outlined.Science,
                            title = "Model",
                            subtitle = "Choose a preset or use a custom model name"
                        )

                        val presets = if (uiState.isGemini) PresetGeminiModels else PresetOpenAiModels
                        val customEnabled =
                            if (uiState.isGemini) uiState.customModelEnabled else uiState.openAiCustomModelEnabled
                        val customText =
                            if (uiState.isGemini) uiState.customModelText else uiState.openAiCustomModelText
                        val presetModel =
                            if (uiState.isGemini) uiState.modelName else uiState.openAiModelName
                        val onPresetSelected: (String) -> Unit =
                            if (uiState.isGemini) onModelChange else onOpenAiModelChange
                        val onCustomEnabled: (Boolean) -> Unit =
                            if (uiState.isGemini) onCustomModelEnabledChange else onOpenAiCustomModelEnabledChange
                        val onCustomText: (String) -> Unit =
                            if (uiState.isGemini) onCustomModelTextChange else onOpenAiCustomModelTextChange

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = if (customEnabled) customText else presetModel,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                label = { Text(if (uiState.isGemini) "Gemini model" else "Model") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                shape = RoundedCornerShape(18.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                presets.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            expanded = false
                                            onCustomEnabled(false)
                                            onPresetSelected(model)
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Use custom model…") },
                                    onClick = {
                                        expanded = false
                                        onCustomEnabled(true)
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(visible = customEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = customText,
                                    onValueChange = onCustomText,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Custom model name") },
                                    placeholder = {
                                        Text(
                                            if (uiState.isGemini) "e.g. gemini-3.5-pro-preview"
                                            else "e.g. llama-3.3-70b-versatile"
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                Text(
                                    text = "Great for preview models, self-hosted models or private model IDs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilterChip(
                            selected = uiState.isConfigured,
                            onClick = { },
                            label = { Text(if (uiState.isConfigured) "Configured" else "Not configured") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (uiState.isConfigured) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }

                // ---------- Actions ----------
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SectionTitle(
                            icon = Icons.Outlined.Bolt,
                            title = "Actions",
                            subtitle = "Validate, save, or remove the current configuration"
                        )

                        uiState.connectionResult?.let { result ->
                            StatusBanner(
                                text = result,
                                success = result.contains("success", ignoreCase = true) ||
                                    result.contains("saved", ignoreCase = true) ||
                                    result.contains("deleted", ignoreCase = true)
                            )
                        }

                        Button(
                            onClick = onTest,
                            enabled = uiState.canTest,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (uiState.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Testing connection…")
                            } else {
                                Text("Test connection")
                            }
                        }

                        Button(
                            onClick = onSave,
                            enabled = uiState.canSave,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Saving…")
                            } else {
                                Icon(Icons.Outlined.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save configuration")
                            }
                        }

                        OutlinedButton(
                            onClick = onDeleteClick,
                            enabled = uiState.isConfigured,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete configuration")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = onDismissDelete,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = { Text("Delete ${uiState.providerTitle} configuration?") },
                    text = {
                        Text("This removes the saved API key, endpoint and model name from this device.")
                    },
                    confirmButton = {
                        Button(
                            onClick = onConfirmDelete,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = onDismissDelete) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (uiState.isSaving || uiState.isTesting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HeroCard(
    providerTitle: String,
    configured: Boolean,
    modelName: String,
    connection: String?
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$providerTitle provider",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Centralized AI configuration for the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = { },
                    label = { Text(if (configured) "Ready" else "Needs setup") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (configured) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null
                        )
                    }
                )
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = modelName.ifBlank { "No model selected" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Science, contentDescription = null) }
                )
            }

            if (connection != null) {
                Text(
                    text = connection,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text = "API key stays hidden unless you reveal it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBanner(
    text: String,
    success: Boolean
) {
    val background = if (success) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val foreground = if (success) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = foreground
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = foreground,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Legacy entry point kept so older navigation code keeps compiling; it now shows the
 * multi-provider settings screen.
 */
@Composable
fun GeminiSettingsRoute(
    onBackClick: () -> Unit
) = AiSettingsRoute(onBackClick = onBackClick)
