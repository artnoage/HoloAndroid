import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioProcessor {
    private static final String TAG = "AudioProcessor";

    public static void saveWav(float[] wav, String outputPath, int sampleRate) throws IOException {
        byte[] header = createWavHeader(wav.length * 2, sampleRate);
        byte[] data = new byte[wav.length * 2];
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(floatToShortArray(wav));

        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            out.write(header);
            out.write(data);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save WAV file", e);
            throw e;
        }
    }

    public static void playAudio(float[] audio, int sampleRate) {
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();

        short[] shortAudio = floatToShortArray(audio);
        audioTrack.play();
        audioTrack.write(shortAudio, 0, shortAudio.length);
        audioTrack.stop();
        audioTrack.release();
    }

    private static short[] floatToShortArray(float[] input) {
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            float sample = input[i];
            sample = Math.max(-1.0f, Math.min(1.0f, sample));
            output[i] = (short) (sample * Short.MAX_VALUE);
        }
        return output;
    }

    public static void debugAudioData(float[] audio) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        float sum = 0;
        for (float sample : audio) {
            min = Math.min(min, sample);
            max = Math.max(max, sample);
            sum += Math.abs(sample);
        }
        float average = sum / audio.length;
        Log.d(TAG, "Audio Debug Info:");
        Log.d(TAG, "Minimum sample value: " + min);
        Log.d(TAG, "Maximum sample value: " + max);
        Log.d(TAG, "Average absolute sample value: " + average);
        Log.d(TAG, "Number of samples: " + audio.length);
    }

    private static byte[] createWavHeader(int dataSize, int sampleRate) {
        byte[] header = new byte[44];
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1); // PCM audio format
        buffer.putShort((short) 1); // Mono channel
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2); // Byte rate
        buffer.putShort((short) 2); // Block align
        buffer.putShort((short) 16); // Bits per sample
        buffer.put("data".getBytes());
        buffer.putInt(dataSize);

        return header;
    }

    // Note: verifyWavFile method is removed as Android doesn't have built-in WAV file parsing
}