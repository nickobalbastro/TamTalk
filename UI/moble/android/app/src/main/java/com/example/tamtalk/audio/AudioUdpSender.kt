package com.example.tamtalk.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class AudioUdpSender(
    private val hostIp: String,
    private val port: Int,
    private val clientId: String,
    inputGain: Float = 1.0f,
    initialTransmitEnabled: Boolean = true,
    private val sampleRate: Int = 48_000,
    private val frameMs: Int = 10
) {
    private val running = AtomicBoolean(false)
    private val transmitEnabled = AtomicBoolean(initialTransmitEnabled)
    @Volatile
    private var inputGainLevel: Float = inputGain.coerceIn(0.0f, 2.0f)
    private var streamThread: Thread? = null
    private var keepaliveThread: Thread? = null
    private var sequence: Int = 0

    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val samplesPerFrame = sampleRate * frameMs / 1000
    private val bytesPerFrame = samplesPerFrame * 2

    private fun createAudioRecord(bufferSize: Int): AudioRecord {
        val preferredSources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )

        for (source in preferredSources) {
            val candidate = AudioRecord(
                source,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }

            candidate.release()
        }

        throw IllegalStateException("AudioRecord init failed")
    }

    fun setTransmitEnabled(enabled: Boolean) {
        transmitEnabled.set(enabled)
    }

    fun setInputGain(newGain: Float) {
        inputGainLevel = newGain.coerceIn(0.0f, 2.0f)
    }

    fun start(onError: (String) -> Unit = {}) {
        if (running.get()) return

        running.set(true)

        streamThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            var recorder: AudioRecord? = null
            var socket: DatagramSocket? = null
            try {
                val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = maxOf(minBuffer, bytesPerFrame * 8)

                recorder = createAudioRecord(bufferSize)

                val address = InetAddress.getByName(hostIp)
                socket = DatagramSocket()
                val pcmBuffer = ByteArray(bytesPerFrame)

                sendControl(socket, address, 3)
                recorder.startRecording()

                while (running.get()) {
                    val read = readExactFrame(recorder, pcmBuffer)
                    if (read != bytesPerFrame) continue

                    if (!transmitEnabled.get()) {
                        continue
                    }

                    applyInputGain(pcmBuffer, read)

                    val payload = pcmBuffer
                    val packet = buildPacket(
                        msgType = 1,
                        sequence = nextSequence(),
                        timestampMicros = System.nanoTime() / 1_000L,
                        clientId = clientId,
                        payload = payload
                    )

                    socket.send(DatagramPacket(packet, packet.size, address, port))
                }
            } catch (ex: Exception) {
                onError(ex.message ?: "Streaming failed")
            } finally {
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                }
                recorder?.release()
                socket?.close()
                running.set(false)
            }
        }.apply {
            name = "TamTalk-AudioUdpSender"
            start()
        }

        keepaliveThread = Thread {
            var socket: DatagramSocket? = null
            try {
                val address = InetAddress.getByName(hostIp)
                socket = DatagramSocket()
                while (running.get()) {
                    sendControl(socket, address, 2)
                    Thread.sleep(1000)
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
            }
        }.apply {
            name = "TamTalk-Keepalive"
            start()
        }
    }

    fun stop() {
        running.set(false)
        streamThread?.join(500)
        keepaliveThread?.join(500)
        streamThread = null
        keepaliveThread = null
    }

    private fun nextSequence(): Int {
        val current = sequence
        sequence += 1
        return current
    }

    private fun sendControl(socket: DatagramSocket, address: InetAddress, msgType: Int) {
        val bytes = buildPacket(
            msgType = msgType,
            sequence = nextSequence(),
            timestampMicros = System.nanoTime() / 1_000L,
            clientId = clientId,
            payload = ByteArray(0)
        )
        socket.send(DatagramPacket(bytes, bytes.size, address, port))
    }

    private fun buildPacket(
        msgType: Int,
        sequence: Int,
        timestampMicros: Long,
        clientId: String,
        payload: ByteArray
    ): ByteArray {
        val idBytes = clientId.toByteArray(Charsets.UTF_8)
        val idLen = idBytes.size.coerceAtMost(65535)
        val totalLength = 20 + idLen + payload.size

        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("TALK".toByteArray(Charsets.US_ASCII))
        buffer.put(1)
        buffer.put(msgType.toByte())
        buffer.putInt(sequence)
        buffer.putLong(timestampMicros)
        buffer.putShort(idLen.toShort())
        buffer.put(idBytes, 0, idLen)
        buffer.put(payload)
        return buffer.array()
    }

    private fun applyInputGain(buffer: ByteArray, bytesRead: Int) {
        val gain = inputGainLevel
        if (gain == 1.0f)
            return

        var index = 0
        while (index + 1 < bytesRead) {
            val lo = buffer[index].toInt() and 0xFF
            val hi = buffer[index + 1].toInt()
            val sample = (hi shl 8) or lo

            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[index] = (scaled and 0xFF).toByte()
            buffer[index + 1] = ((scaled shr 8) and 0xFF).toByte()

            index += 2
        }
    }

    private fun readExactFrame(recorder: AudioRecord, buffer: ByteArray): Int {
        var offset = 0

        while (offset < buffer.size && running.get()) {
            val chunk = recorder.read(buffer, offset, buffer.size - offset)
            if (chunk <= 0)
                return chunk

            offset += chunk
        }

        return offset
    }
}
