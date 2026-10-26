package az.fadayat.voicetyping.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import az.fadayat.voicetyping.VoiceTypingScreen
import az.fadayat.voicetyping.ui.theme.VoiceTypingTheme
import org.junit.Rule
import org.junit.Test

class VoiceTypingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startButtonDisabledUntilSetupIsComplete() {
        composeRule.setContent {
            VoiceTypingTheme {
                VoiceTypingScreen(
                    state = MainUiState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onSaveApiKey = {},
                    onClearApiKey = {},
                    onRequestMicrophone = {},
                    onRequestOverlay = {},
                    onRequestAccessibility = {},
                    onToggleService = {},
                )
            }
        }

        composeRule.onNodeWithText("Gboard qalır. Üzən düyməyə toxun, danış və mətni aktiv xanaya yerləşdir.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Üzən düyməni başlat").assertIsNotEnabled()
    }

    @Test
    fun startButtonEnabledWhenSetupIsComplete() {
        composeRule.setContent {
            VoiceTypingTheme {
                VoiceTypingScreen(
                    state = MainUiState(
                        hasApiKey = true,
                        hasMicrophonePermission = true,
                        hasOverlayPermission = true,
                        hasAccessibilityService = true,
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onSaveApiKey = {},
                    onClearApiKey = {},
                    onRequestMicrophone = {},
                    onRequestOverlay = {},
                    onRequestAccessibility = {},
                    onToggleService = {},
                )
            }
        }

        composeRule.onNodeWithText("Üzən düyməni başlat").assertIsEnabled()
    }
}
