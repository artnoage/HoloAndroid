import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioProcessor {

    public static void saveWav(float[] wav, String outputPath, int sampleRate) throws IOException {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, true);
        byte[] byteArray = new byte[wav.length * 2];
        ByteBuffer.wrap(byteArray).order(ByteOrder.BIG_ENDIAN).asShortBuffer().put(floatToShortArray(wav));
        
        try (AudioInputStream audioInputStream = new AudioInputStream(
                new ByteArrayInputStream(byteArray),
                format,
                wav.length);
             FileOutputStream fos = new FileOutputStream(outputPath)) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, fos);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Failed to save WAV file", e);
        }
    }

    public static void playAudio(float[] audio, int sampleRate) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, true);
        try {
            byte[] byteArray = new byte[audio.length * 2];
            ByteBuffer.wrap(byteArray).order(ByteOrder.BIG_ENDIAN).asShortBuffer().put(floatToShortArray(audio));
            
            AudioInputStream audioInputStream = new AudioInputStream(
                new ByteArrayInputStream(byteArray),
                format,
                audio.length
            );
            
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            byte[] buffer = new byte[4096];
            int bytesRead = 0;
            while ((bytesRead = audioInputStream.read(buffer, 0, buffer.length)) != -1) {
                line.write(buffer, 0, bytesRead);
            }

            line.drain();
            line.stop();
            line.close();
            audioInputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        System.out.println("Audio Debug Info:");
        System.out.println("Minimum sample value: " + min);
        System.out.println("Maximum sample value: " + max);
        System.out.println("Average absolute sample value: " + average);
        System.out.println("Number of samples: " + audio.length);
    }

    public static void verifyWavFile(String filePath) {
        try {
            File file = new File(filePath);
            AudioInputStream ais = AudioSystem.getAudioInputStream(file);
            AudioFormat format = ais.getFormat();
            
            System.out.println("WAV File Verification:");
            System.out.println("Sample Rate: " + format.getSampleRate());
            System.out.println("Bits per Sample: " + format.getSampleSizeInBits());
            System.out.println("Channels: " + format.getChannels());
            System.out.println("Frame Size: " + format.getFrameSize());
            System.out.println("Frame Rate: " + format.getFrameRate());
            System.out.println("Big Endian: " + format.isBigEndian());
            
            long frames = ais.getFrameLength();
            System.out.println("Duration (seconds): " + frames / format.getFrameRate());
            
            ais.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}