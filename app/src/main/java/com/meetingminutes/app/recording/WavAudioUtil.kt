package com.meetingminutes.app.recording

import java.io.File
import java.io.FileOutputStream

object WavAudioUtil {
    fun pcmToWav(pcmFile: File, wavFile: File, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): File {
        val audioLen = pcmFile.length()
        val byteRate = sampleRate * channels * bitsPerSample / 8
        FileOutputStream(wavFile).use { output ->
            output.write(header(audioLen, sampleRate, channels, byteRate, bitsPerSample))
            pcmFile.inputStream().use { input -> input.copyTo(output) }
        }
        return wavFile
    }

    fun pcmToWavChunks(
        pcmFile: File,
        outputDir: File,
        prefix: String,
        maxPcmBytes: Long = 18L * 1024L * 1024L,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): List<File> {
        outputDir.mkdirs()
        val files = mutableListOf<File>()
        pcmFile.inputStream().use { input ->
            var index = 1
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val chunkFile = File(outputDir, "${prefix}_part_${index}.wav")
                val tempPcm = File(outputDir, "${prefix}_part_${index}.pcm")
                var written = 0L
                tempPcm.outputStream().use { out ->
                    while (written < maxPcmBytes) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), maxPcmBytes - written).toInt())
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        written += read
                    }
                }
                if (written == 0L) {
                    tempPcm.delete()
                    break
                }
                pcmToWav(tempPcm, chunkFile, sampleRate, channels, bitsPerSample)
                tempPcm.delete()
                files += chunkFile
                index += 1
            }
        }
        return files
    }

    private fun header(audioLen: Long, sampleRate: Int, channels: Int, byteRate: Int, bitsPerSample: Int): ByteArray {
        val totalDataLen = audioLen + 36
        val blockAlign = channels * bitsPerSample / 8
        return byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            (totalDataLen and 0xff).toByte(), ((totalDataLen shr 8) and 0xff).toByte(),
            ((totalDataLen shr 16) and 0xff).toByte(), ((totalDataLen shr 24) and 0xff).toByte(),
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
            'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(),
            16, 0, 0, 0,
            1, 0,
            channels.toByte(), 0,
            (sampleRate and 0xff).toByte(), ((sampleRate shr 8) and 0xff).toByte(),
            ((sampleRate shr 16) and 0xff).toByte(), ((sampleRate shr 24) and 0xff).toByte(),
            (byteRate and 0xff).toByte(), ((byteRate shr 8) and 0xff).toByte(),
            ((byteRate shr 16) and 0xff).toByte(), ((byteRate shr 24) and 0xff).toByte(),
            blockAlign.toByte(), 0,
            bitsPerSample.toByte(), 0,
            'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
            (audioLen and 0xff).toByte(), ((audioLen shr 8) and 0xff).toByte(),
            ((audioLen shr 16) and 0xff).toByte(), ((audioLen shr 24) and 0xff).toByte()
        )
    }
}
