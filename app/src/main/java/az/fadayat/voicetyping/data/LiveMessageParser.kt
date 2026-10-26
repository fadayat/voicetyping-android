package az.fadayat.voicetyping.data

import org.json.JSONObject

internal object LiveMessageParser {
    sealed interface Event {
        data object SetupComplete : Event
        data class Transcript(val text: String) : Event
        data object TurnComplete : Event
        data class Error(val message: String) : Event
        data object Ignore : Event
    }

    fun parse(raw: String): List<Event> {
        val root = JSONObject(raw)
        if (root.has("setupComplete")) return listOf(Event.SetupComplete)
        root.optJSONObject("error")?.let { error ->
            return listOf(Event.Error(error.optString("message", "Live API error")))
        }
        val content = root.optJSONObject("serverContent") ?: return listOf(Event.Ignore)
        val events = mutableListOf<Event>()
        content.optJSONObject("inputTranscription")
            ?.optString("text")
            ?.takeIf(String::isNotEmpty)
            ?.let { events += Event.Transcript(it) }
        if (content.optBoolean("turnComplete") || content.optBoolean("generationComplete")) {
            events += Event.TurnComplete
        }
        return events.ifEmpty { listOf(Event.Ignore) }
    }
}
