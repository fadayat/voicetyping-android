package az.fadayat.voicetyping.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VoiceAccessibilityService : AccessibilityService() {
    enum class PasteResult {
        SUCCESS,
        NO_FIELD,
        PASSWORD_FIELD,
        NOT_SUPPORTED,
        SERVICE_DISABLED,
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun pasteIntoFocusedField(): PasteResult {
        val root = rootInActiveWindow ?: return PasteResult.NO_FIELD
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root)
            ?: return PasteResult.NO_FIELD
        if (node.isPassword) return PasteResult.PASSWORD_FIELD
        if (!node.isEditable) return PasteResult.NO_FIELD
        val supportsPaste = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_PASTE }
        if (!supportsPaste) return PasteResult.NOT_SUPPORTED
        return if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            PasteResult.SUCCESS
        } else {
            PasteResult.NOT_SUPPORTED
        }
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findEditable(child)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: VoiceAccessibilityService? = null

        fun pasteFromClipboard(): PasteResult =
            instance?.pasteIntoFocusedField() ?: PasteResult.SERVICE_DISABLED
    }
}
