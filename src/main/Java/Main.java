import ai.onnxruntime.OrtException;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    private static final String VITS_MODEL_FILE = "/vits_model.onnx";
    private static final String PHONEMIZER_MODEL_FILE = "/phonemizer_model.onnx";
    private static final String GEMINI_API_KEY = "AIzaSyBzw4vQsyQFcpA9hr4ncge4BYZW0CcT_ng";

    private VitsOnnxSynthesizer synthesizer;
    private Phonemizer phonemizer;
    private Tokenizer tokenizer;
    private ApiCall apiCall;

    public Main() {
        initializeModels();
        apiCall = new ApiCall(GEMINI_API_KEY);
    }

    private void initializeModels() {
        try {
            InputStream vitsStream = getClass().getResourceAsStream(VITS_MODEL_FILE);
            if (vitsStream == null) {
                throw new IOException("VITS model file not found: " + VITS_MODEL_FILE);
            }
            synthesizer = new VitsOnnxSynthesizer(vitsStream);
    
            InputStream phonemizerStream = getClass().getResourceAsStream(PHONEMIZER_MODEL_FILE);
            if (phonemizerStream == null) {
                throw new IOException("Phonemizer model file not found: " + PHONEMIZER_MODEL_FILE);
            }
            
            Properties config = new Properties();
            try (InputStream configStream = getClass().getResourceAsStream("/tokenizer_config.properties")) {
                if (configStream == null) {
                    throw new IOException("Tokenizer config file not found: /tokenizer_config.properties");
                }
                config.load(configStream);
            }
            
            HashMap<String, Integer> tokenToIdx = new HashMap<>();
            // Set up tokenToIdx map (you'll need to implement this based on your requirements)
            
            phonemizer = new Phonemizer(config, tokenToIdx);
            Phonemizer.initialize(phonemizerStream);
    
            tokenizer = new Tokenizer();
        } catch (IOException | OrtException e) {
            System.err.println("Error initializing models: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private List<String> splitTextIntoChunks(String text) {
        return Arrays.asList(text.split("(?<=[.!?])\\s+"));
    }

    private float[] concatenateAudio(List<float[]> audioChunks) {
        int totalLength = audioChunks.stream().mapToInt(chunk -> chunk.length).sum();
        float[] result = new float[totalLength];
        int currentIndex = 0;
        for (float[] chunk : audioChunks) {
            System.arraycopy(chunk, 0, result, currentIndex, chunk.length);
            currentIndex += chunk.length;
        }
        return result;
    }

    public void processUserInput(String input, int speakerId) {
        try {
            // Text to audio
            float[] inputAudio = synthesizer.tts(input, speakerId);
            
            // Save input audio
            String inputAudioFile = "input_audio.wav";
            AudioProcessor.saveWav(inputAudio, inputAudioFile, synthesizer.getSampleRate());
            System.out.println("Input audio saved to: " + inputAudioFile);
            
            // Send audio to FastAPI and get text response
            String apiResponseText = apiCall.sendAudioToApi(Paths.get(inputAudioFile), speakerId);
            System.out.println("API Response Text: " + apiResponseText);
    
            // Split the response text into chunks
            List<String> textChunks = splitTextIntoChunks(apiResponseText);
            
            // Synthesize each chunk
            List<float[]> audioChunks = new ArrayList<>();
            for (String chunk : textChunks) {
                float[] chunkAudio = synthesizer.tts(chunk, speakerId);
                audioChunks.add(chunkAudio);
            }
            
            // Concatenate all audio chunks
            float[] responseAudio = concatenateAudio(audioChunks);
    
            // Debug audio data
            AudioProcessor.debugAudioData(responseAudio);
    
            // Save the final output audio
            String outputFile = "output_audio.wav";
            AudioProcessor.saveWav(responseAudio, outputFile, synthesizer.getSampleRate());
            System.out.println("Final audio output saved to: " + outputFile);
    
            // Verify the saved WAV file
            AudioProcessor.verifyWavFile(outputFile);
    
            // Play the audio
            AudioProcessor.playAudio(responseAudio, synthesizer.getSampleRate());
    
        } catch (IOException e) {
            System.out.println("Error communicating with the API: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Error processing input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Example method to use the Tokenizer
    public List<Integer> tokenizeText(String text) {
        return tokenizer.textToIds(text);
    }

    public static void main(String[] args) {
        Main main = new Main();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Enter speaker number (0-4) or 'exit' to quit: ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                break;
            }

            int speakerId;
            try {
                speakerId = Integer.parseInt(input);
                if (speakerId < 0 || speakerId > 4) {
                    System.out.println("Invalid speaker number. Please enter a number between 0 and 4.");
                    continue;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number between 0 and 4 or 'exit'.");
                continue;
            }

            System.out.print("Enter text: ");
            String textInput = scanner.nextLine().trim();

            if (!textInput.isEmpty()) {
                main.processUserInput(textInput, speakerId);
            } else {
                System.out.println("Input was empty. Please try again.");
            }
        }

        System.out.println("Exiting program.");
        scanner.close();
    }
}