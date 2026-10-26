package az.fadayat.voicetyping.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptExtractorTest {
    @Test
    fun extractsTextFromInteractionSteps() {
        val response = """
            {
              "status": "completed",
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {"type": "text", "text": "Salam dünya"}
                  ]
                }
              ]
            }
        """.trimIndent()

        assertEquals("Salam dünya", TranscriptExtractor.fromInteraction(response))
    }

    @Test
    fun extractsTextFromInteractionOutputs() {
        val response = """
            {
              "outputs": [
                {"type": "text", "text": "Alternativ cavab"}
              ]
            }
        """.trimIndent()

        assertEquals("Alternativ cavab", TranscriptExtractor.fromInteraction(response))
    }

    @Test
    fun joinsTextPartsAndTrimsOuterQuotes() {
        val response = """
            {
              "candidates": [{
                "content": {
                  "parts": [{"text":"\"Salam "},{"text":"dünya\""}]
                }
              }]
            }
        """.trimIndent()

        assertEquals("Salam dünya", TranscriptExtractor.fromGenerateContent(response))
    }

    @Test
    fun returnsEmptyTextForMissingCandidate() {
        assertEquals("", TranscriptExtractor.fromGenerateContent("{}"))
    }
}
