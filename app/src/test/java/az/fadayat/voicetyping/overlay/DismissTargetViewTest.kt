package az.fadayat.voicetyping.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DismissTargetViewTest {
    @Test
    fun acceptsPointNearCenterAndRejectsPointOutsideTarget() {
        assertTrue(
            DismissTargetView.containsPoint(
                rawX = 540f,
                rawY = 1_800f,
                left = 492f,
                top = 1_752f,
                width = 96f,
                height = 96f,
            ),
        )
        assertFalse(
            DismissTargetView.containsPoint(
                rawX = 540f,
                rawY = 1_690f,
                left = 492f,
                top = 1_752f,
                width = 96f,
                height = 96f,
            ),
        )
    }
}
