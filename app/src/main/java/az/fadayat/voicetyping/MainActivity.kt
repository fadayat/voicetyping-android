package az.fadayat.voicetyping

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.fadayat.voicetyping.ui.MainUiEvent
import az.fadayat.voicetyping.ui.MainUiState
import az.fadayat.voicetyping.ui.MainViewModel
import az.fadayat.voicetyping.ui.theme.VoiceTypingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceTypingTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val microphoneLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { viewModel.refresh() }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is MainUiEvent.Message -> snackbarHostState.showSnackbar(event.value)
                        }
                    }
                }

                VoiceTypingScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onSaveApiKey = viewModel::saveApiKey,
                    onClearApiKey = viewModel::clearApiKey,
                    onRequestMicrophone = {
                        microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onOpenKeyboardSettings = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onChooseKeyboard = {
                        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTypingScreen(
    state: MainUiState,
    snackbarHostState: SnackbarHostState,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onChooseKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "VoiceTyping",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Gboard silinmir. Səsli yazarkən VoiceTyping klaviaturasına keç; mətn yazıldıqdan sonra Gboard-a qayıt.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StatusCard(state)

            SectionCard(title = "1. Gemini API açarı") {
                if (state.hasApiKey) {
                    Text("API açarı şifrəli saxlanır və APK-yə daxil edilmir.")
                    TextButton(onClick = onClearApiKey) { Text("Açarı sil") }
                } else {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it.trim() },
                        label = { Text("Gemini API açarı") },
                        supportingText = { Text("Açar yalnız bu cihazda Android Keystore ilə qorunur.") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onSaveApiKey(apiKey)
                            apiKey = ""
                        },
                        enabled = apiKey.length >= 20,
                    ) {
                        Text("Yadda saxla")
                    }
                }
            }

            SectionCard(title = "2. Telefon hazırlığı") {
                SetupRow(
                    title = "Mikrofon",
                    explanation = "Səs yalnız klaviaturadakı düyməyə toxunduqda yazılır.",
                    ready = state.hasMicrophonePermission,
                    actionLabel = "İcazə ver",
                    onClick = onRequestMicrophone,
                )
                SetupRow(
                    title = "VoiceTyping klaviaturası",
                    explanation = "Android ayarlarında VoiceTyping-i aktiv et. Gboard aktiv qalır.",
                    ready = state.isImeEnabled,
                    actionLabel = "Ayarlara keç",
                    onClick = onOpenKeyboardSettings,
                )
                SetupRow(
                    title = "Aktiv klaviatura",
                    explanation = "Yazı sahəsində VoiceTyping-i seç. Sonra avtomatik geri qayıda bilərsən.",
                    ready = state.isImeSelected,
                    actionLabel = "Seç",
                    onClick = onChooseKeyboard,
                    actionEnabled = state.canChooseKeyboard,
                )
            }

            Button(
                onClick = onChooseKeyboard,
                enabled = state.canChooseKeyboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(if (state.isImeSelected) "Klaviaturanı dəyiş" else "VoiceTyping klaviaturasını seç")
            }

            Text(
                text = "VoiceTyping overlay və Accessibility icazəsi istəmir. Səs transkripsiya üçün Gemini xidmətinə göndərilir. Parol xanalarında səs yazısı bloklanır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(state: MainUiState, modifier: Modifier = Modifier) {
    val (title, subtitle) = when {
        state.isReady -> "Hazırdır" to "İstənilən yazı sahəsində VoiceTyping ilə danışa bilərsən."
        state.canChooseKeyboard -> "Klaviaturanı seç" to "Aşağıdakı düymədən VoiceTyping-i aktiv klaviatura et."
        else -> "Hazırlıq lazımdır" to "Aşağıdakı addımları tamamla."
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

@Composable
private fun SetupRow(
    title: String,
    explanation: String,
    ready: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(12.dp),
            shape = CircleShape,
            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            content = {},
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClick, enabled = !ready && actionEnabled) {
            Text(if (ready) "Hazırdır" else actionLabel)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceTypingScreenPreview() {
    VoiceTypingTheme {
        VoiceTypingScreen(
            state = MainUiState(hasApiKey = true, hasMicrophonePermission = true),
            snackbarHostState = remember { SnackbarHostState() },
            onSaveApiKey = {},
            onClearApiKey = {},
            onRequestMicrophone = {},
            onOpenKeyboardSettings = {},
            onChooseKeyboard = {},
        )
    }
}
