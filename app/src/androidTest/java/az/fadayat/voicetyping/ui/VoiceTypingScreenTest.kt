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
    fun keyboardButtonDisabledUntilSetupIsComplete() {
        composeRule.setContent {
            VoiceTypingTheme {
                VoiceTypingScreen(
                    state = MainUiState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onSaveApiKey = {},
                    onClearApiKey = {},
                    onRequestMicrophone = {},
                    onOpenKeyboardSettings = {},
                    onChooseKeyboard = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Gboard silinmir. Səsli yazarkən VoiceTyping klaviaturasına keç; mətn yazıldıqdan sonra Gboard-a qayıt.",
        )
            .assertIsDisplayed()
        composeRule.onNodeWithText("VoiceTyping klaviaturasını seç").assertIsNotEnabled()
    }

    @Test
    fun keyboardButtonEnabledWhenSetupIsComplete() {
        composeRule.setContent {
            VoiceTypingTheme {
                VoiceTypingScreen(
                    state = MainUiState(
                        hasApiKey = true,
                        hasMicrophonePermission = true,
                        isImeEnabled = true,
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onSaveApiKey = {},
                    onClearApiKey = {},
                    onRequestMicrophone = {},
                    onOpenKeyboardSettings = {},
                    onChooseKeyboard = {},
                )
            }
        }

        composeRule.onNodeWithText("VoiceTyping klaviaturasını seç").assertIsEnabled()
    }
}
