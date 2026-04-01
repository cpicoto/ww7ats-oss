package net.wwats.ww7ats.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates and plays Morse code audio for CW ID transmission.
 * 700 Hz sidetone, configurable WPM, PARIS standard timing.
 */
class MorseCodePlayer {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TONE_FREQ = 700.0
        private const val RAMP_MS = 5 // 5ms ramp to avoid clicks

        private val MORSE_TABLE = mapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..",
            'E' to ".", 'F' to "..-.", 'G' to "--.", 'H' to "....",
            'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
            'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.",
            'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
            'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
            'Y' to "-.--", 'Z' to "--..",
            '0' to "-----", '1' to ".----", '2' to "..---",
            '3' to "...--", '4' to "....-", '5' to ".....",
            '6' to "-....", '7' to "--...", '8' to "---..",
            '9' to "----.",
            '/' to "-..-.", '.' to ".-.-.-", ',' to "--..--",
            '?' to "..--.."
        )
    }

    data class CharacterBuffer(
        val character: String,
        val samples: ShortArray
    )

    /**
     * Generate per-character audio buffers for the callsign.
     * Each buffer contains the Morse audio for one character plus inter-char gap.
     * Sign-off: long dash + extended trailing silence.
     */
    fun generateCharacterBuffers(callsign: String, wpm: Int): List<CharacterBuffer> {
        val ditDuration = 1.2 / wpm
        val dahDuration = ditDuration * 3
        val intraCharGap = ditDuration
        val interCharGap = ditDuration * 3
        val interWordGap = ditDuration * 7

        val buffers = mutableListOf<CharacterBuffer>()
        val upper = callsign.uppercase()

        for (char in upper) {
            if (char == ' ') {
                buffers.add(CharacterBuffer(" ", generateSilence(interWordGap)))
                continue
            }
            val pattern = MORSE_TABLE[char] ?: continue
            val samples = mutableListOf<Short>()

            for ((i, symbol) in pattern.withIndex()) {
                when (symbol) {
                    '.' -> samples.addAll(generateTone(ditDuration).toList())
                    '-' -> samples.addAll(generateTone(dahDuration).toList())
                }
                if (i < pattern.length - 1) {
                    samples.addAll(generateSilence(intraCharGap).toList())
                }
            }
            // Inter-character gap
            samples.addAll(generateSilence(interCharGap).toList())
            buffers.add(CharacterBuffer(char.toString(), samples.toShortArray()))
        }

        // Sign-off: inter-char gap + long dash
        buffers.add(CharacterBuffer("—", buildList {
            addAll(generateSilence(interCharGap).toList())
            addAll(generateTone(dahDuration).toList())
        }.toShortArray()))

        // Extended trailing silence (3× inter-word gap)
        val trailingSilence = generateSilence(interWordGap * 3)
        buffers.add(CharacterBuffer(" ", trailingSilence))

        return buffers
    }

    private fun generateTone(durationSec: Double): ShortArray {
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val rampSamples = (SAMPLE_RATE * RAMP_MS / 1000.0).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            var amplitude = sin(2.0 * PI * TONE_FREQ * i / SAMPLE_RATE)

            // Attack ramp
            if (i < rampSamples) {
                amplitude *= i.toDouble() / rampSamples
            }
            // Release ramp
            if (i >= numSamples - rampSamples) {
                amplitude *= (numSamples - i).toDouble() / rampSamples
            }

            samples[i] = (amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateSilence(durationSec: Double): ShortArray {
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        return ShortArray(numSamples)
    }

    /**
     * Plays generated character buffers synchronously, invoking onCharacter for each.
     */
    fun playCharacterBuffers(
        buffers: List<CharacterBuffer>,
        onCharacter: (String) -> Unit
    ) {
        // Compute total sample count to allocate a properly-sized AudioTrack buffer
        val totalSamples = buffers.sumOf { it.samples.size }
        val totalBytes = totalSamples * 2 // 16-bit PCM = 2 bytes per sample
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // Use at least the total audio size so writes don't underrun
        val bufferSize = maxOf(minBufferSize, totalBytes)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        // Write all samples into the static buffer first
        val allSamples = ShortArray(totalSamples)
        var offset = 0
        val charPositions = mutableListOf<Triple<String, Int, Int>>() // char, startSample, endSample
        for (entry in buffers) {
            charPositions.add(Triple(entry.character, offset, offset + entry.samples.size))
            entry.samples.copyInto(allSamples, offset)
            offset += entry.samples.size
        }
        audioTrack.write(allSamples, 0, allSamples.size)
        audioTrack.play()

        // Track playback progress and fire character callbacks at the right time
        for ((char, startSample, _) in charPositions) {
            if (char != "—" && char != " ") {
                onCharacter(char)
            }
            // Wait until playback head reaches the end of this character's samples
            val endFrame = if (charPositions.last().first == char) totalSamples
                else charPositions[charPositions.indexOfFirst { it.first == char && it.second == startSample } + 1].second
            while (audioTrack.playbackHeadPosition < endFrame) {
                Thread.sleep(10)
            }
        }

        audioTrack.stop()
        audioTrack.release()
    }
}
