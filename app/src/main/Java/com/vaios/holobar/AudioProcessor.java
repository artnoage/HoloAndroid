package com.vaios.holobar;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;


public class AudioProcessor {
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
}