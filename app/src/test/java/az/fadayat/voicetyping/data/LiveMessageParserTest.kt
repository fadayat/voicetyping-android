package az.fadayat.voicetyping.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMessageParserTest {
    @Test
    fun parsesSetupCompletion() {
        assertEquals(
            listOf(LiveMessageParser.Event.SetupComplete),
            LiveMessageParser.parse("{\"setupComplete\":{}}"),
        )
    }

    @Test
    fun parsesTranscriptAndTurnCompletionTogether() {
        val events = LiveMessageParser.parse(
            """{"serverContent":{"inputTranscription":{"text":"Salam dünya"},"turnComplete":true}}""",
        )

        assertEquals(LiveMessageParser.Event.Transcript("Salam dünya"), events[0])
        assertEquals(LiveMessageParser.Event.TurnComplete, events[1])
    }

    @Test
    fun parsesServerErrorWithoutLoggingPayload() {
        val events = LiveMessageParser.parse("""{"error":{"message":"quota"}}""")
        assertTrue(events.single() is LiveMessageParser.Event.Error)
    }
}
