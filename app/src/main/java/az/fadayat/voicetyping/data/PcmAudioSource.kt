package az.fadayat.voicetyping.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import az.fadayat.voicetyping.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class PcmAudioSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val audioDispatcher: CoroutineDispatcher,
) {
    private val active = AtomicBoolean(false)
    private val currentRecorder = AtomicReference<AudioRecord?>()

    fun stream(
        onStarted: () -> Unit = {},
        onStopped: () -> Unit = {},
    ): Flow<ByteArray> = flow {
        check(active.compareAndSet(false, true)) { "Audio capture is already active" }
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        check(minimum > 0) { "Audio input is unavailable" }
        val recorder = createRecorder(maxOf(minimum * 2, CHUNK_BYTES * 4))
        currentRecorder.set(recorder)
        var totalBytes = 0
        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Microphone did not start"
            }
            onStarted()
            while (active.get() && totalBytes < MAX_RECORDING_BYTES) {
                val buffer = ByteArray(minOf(CHUNK_BYTES, MAX_RECORDING_BYTES - totalBytes))
                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                when {
                    read > 0 -> {
                        totalBytes += read
                        emit(if (read == buffer.size) buffer else buffer.copyOf(read))
                    }
                    read == AudioRecord.ERROR_DEAD_OBJECT -> error("Microphone disconnected")
                    read < 0 && active.get() -> error("Microphone read failed: $read")
                }
            }
        } finally {
            active.set(false)
            currentRecorder.compareAndSet(recorder, null)
            runCatching { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
            recorder.release()
            onStopped()
        }
    }.flowOn(audioDispatcher)

    fun stop() {
        active.set(false)
        currentRecorder.get()?.let { recorder ->
            runCatching { recorder.stop() }
        }
    }

    private fun createRecorder(bufferSize: Int): AudioRecord {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Microphone permission is not granted")
        }
        return AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_BYTES = 3_200
        private const val MAX_RECORDING_BYTES = SAMPLE_RATE * 2 * 60 * 7
    }
}
