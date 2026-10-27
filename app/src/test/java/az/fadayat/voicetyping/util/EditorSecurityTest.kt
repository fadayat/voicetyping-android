package az.fadayat.voicetyping.util

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSecurityTest {
    @Test
    fun detectsTextPasswordVariations() {
        assertTrue(
            EditorSecurity.isPasswordInput(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            ),
        )
        assertTrue(
            EditorSecurity.isPasswordInput(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            ),
        )
        assertTrue(
            EditorSecurity.isPasswordInput(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            ),
        )
    }

    @Test
    fun detectsNumberPassword() {
        assertTrue(
            EditorSecurity.isPasswordInput(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            ),
        )
    }

    @Test
    fun acceptsOrdinaryTextAndNumberFields() {
        assertFalse(EditorSecurity.isPasswordInput(InputType.TYPE_CLASS_TEXT))
        assertFalse(EditorSecurity.isPasswordInput(InputType.TYPE_CLASS_NUMBER))
        assertFalse(
            EditorSecurity.isPasswordInput(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            ),
        )
    }
}
