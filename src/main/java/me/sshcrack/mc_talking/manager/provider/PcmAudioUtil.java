package me.sshcrack.mc_talking.manager.provider;

import me.sshcrack.mc_talking.util.AudioHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class PcmAudioUtil {
    private PcmAudioUtil() {
    }

    public static byte[] toLittleEndianBytes(short[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    public static short[] fromLittleEndianBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[bytes.length / Short.BYTES];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort();
        }
        return samples;
    }

    public static short[] resample(short[] samples, int sourceRate, int targetRate) {
        if (sourceRate == targetRate) return samples;
        return AudioHelper.resampleAudio(samples, sourceRate, targetRate);
    }

    public static byte[] toWav(short[] samples, int sampleRate) throws AiProviderException {
        try {
            byte[] pcm = toLittleEndianBytes(samples);
            ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
            writeAscii(out, "RIFF");
            writeIntLE(out, 36 + pcm.length);
            writeAscii(out, "WAVE");
            writeAscii(out, "fmt ");
            writeIntLE(out, 16);
            writeShortLE(out, 1);
            writeShortLE(out, 1);
            writeIntLE(out, sampleRate);
            writeIntLE(out, sampleRate * 2);
            writeShortLE(out, 2);
            writeShortLE(out, 16);
            writeAscii(out, "data");
            writeIntLE(out, pcm.length);
            out.write(pcm);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AiProviderException("Failed to encode WAV audio", e);
        }
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeShortLE(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }
}
