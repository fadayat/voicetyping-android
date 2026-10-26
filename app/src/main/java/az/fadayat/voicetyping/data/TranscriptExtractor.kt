package az.fadayat.voicetyping.data

import org.json.JSONObject

internal object TranscriptExtractor {
    fun fromInteraction(raw: String): String {
        val root = JSONObject(raw)
        root.optString("output_text")
            .takeIf(String::isNotBlank)
            ?.let { return it.trim() }

        val directOutputs = root.optJSONArray("outputs")
        if (directOutputs != null) {
            extractTextBlocks(directOutputs).takeIf(String::isNotBlank)?.let { return it }
        }

        val steps = root.optJSONArray("steps") ?: return ""
        return buildString {
            for (stepIndex in 0 until steps.length()) {
                val content = steps.optJSONObject(stepIndex)?.optJSONArray("content") ?: continue
                append(extractTextBlocks(content))
            }
        }.trim()
    }

    fun fromGenerateContent(raw: String): String {
        val root = JSONObject(raw)
        val candidates = root.optJSONArray("candidates") ?: return ""
        val parts = candidates.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return ""
        return buildString {
            for (index in 0 until parts.length()) {
                append(parts.optJSONObject(index)?.optString("text").orEmpty())
            }
        }.trim().removeSurrounding("\"")
    }

    private fun extractTextBlocks(blocks: org.json.JSONArray): String = buildString {
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            if (block.optString("type") == "text") append(block.optString("text"))
        }
    }.trim()
}
