import ai.onnxruntime.*;
import java.io.InputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Phonemizer {
    private static OrtEnvironment env;
    private static OrtSession session;
    private final int maxSeqLen;
    private final boolean lowercase;
    private final Map<String, Integer> tokenToIdx;
    private final String languageCodeEn;
    private final String endToken;
    private final Map<String, List<Integer>> cache;

    public static void initialize(InputStream modelStream) throws OrtException, IOException {
        env = OrtEnvironment.getEnvironment();
        byte[] modelBytes = modelStream.readAllBytes();
        session = env.createSession(modelBytes, new OrtSession.SessionOptions());
    }

    public Phonemizer(Properties config, Map<String, Integer> tokenToIdx) {
        this.maxSeqLen = Integer.parseInt(config.getProperty("max_seq_len"));
        this.lowercase = Boolean.parseBoolean(config.getProperty("lowercase"));
        this.languageCodeEn = config.getProperty("language_code_en");
        this.endToken = config.getProperty("end_token");
        this.tokenToIdx = new HashMap<>(tokenToIdx);
        this.cache = new ConcurrentHashMap<>();
    }

    private List<Integer> tokenize(String word) {
        if (lowercase) {
            word = word.toLowerCase();
        }
        List<Integer> tokens = new ArrayList<>();
        tokens.add(tokenToIdx.get(languageCodeEn));
        
        for (char ch : word.toCharArray()) {
            String charStr = String.valueOf(ch);
            if (tokenToIdx.containsKey(charStr)) {
                int tokenId = tokenToIdx.get(charStr);
                for (int i = 0; i < 3; i++) {
                    tokens.add(tokenId);
                }
            }
        }
        
        tokens.add(tokenToIdx.get(endToken));
        return tokens;
    }

    private List<Integer> addThrees(List<Integer> phonemeList) {
        List<Integer> result = new ArrayList<>(phonemeList.size() * 2 + 1);
        result.add(3);
        for (int num : phonemeList) {
            result.add(num);
            result.add(3);
        }
        return result;
    }

    public List<Integer> infer(String sentence) {
        String[] words = sentence.split("\\s+");
        List<List<Integer>> wordOutputs = new ArrayList<>();
    
        for (String word : words) {
            wordOutputs.add(inferWord(word));
        }
    
        return combineWordOutputs(wordOutputs);
    }

    private List<Integer> inferWord(String word) {
        return cache.computeIfAbsent(word, w -> {
            List<Integer> tokenizedWord = tokenize(w);
            return runOnnxInference(tokenizedWord);
        });
    }

    private List<Integer> runOnnxInference(List<Integer> tokenizedWord) {
        long[][] paddedInput = new long[1][maxSeqLen];
        for (int i = 0; i < tokenizedWord.size(); i++) {
            paddedInput[0][i] = tokenizedWord.get(i);
        }

        try {
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, paddedInput);
            OrtSession.Result result = session.run(Collections.singletonMap("input", inputTensor));
            float[][][] rawOutput = (float[][][]) result.get(0).getValue();

            List<Integer> processedOutput = new ArrayList<>();
            for (int i = 1; i < rawOutput[0].length; i++) {
                int maxIndex = 0;
                for (int j = 1; j < rawOutput[0][i].length; j++) {
                    if (rawOutput[0][i][j] > rawOutput[0][i][maxIndex]) {
                        maxIndex = j;
                    }
                }
                if (maxIndex == 3) break;
                if (maxIndex != 0) {
                    processedOutput.add(maxIndex);
                }
            }
            return processedOutput;
        } catch (OrtException e) {
            throw new PhonemizerInferenceException("Failed to run ONNX inference", e);
        }
    }

    private List<Integer> combineWordOutputs(List<List<Integer>> wordOutputs) {
        List<Integer> combinedOutput = new ArrayList<>();
        for (int i = 0; i < wordOutputs.size(); i++) {
            if (i > 0) {
                combinedOutput.add(2);
            }
            combinedOutput.addAll(wordOutputs.get(i));
        }

        List<Integer> outputWithoutDuplicates = new ArrayList<>();
        outputWithoutDuplicates.add(combinedOutput.get(0));
        for (int i = 1; i < combinedOutput.size(); i++) {
            if (combinedOutput.get(i) != outputWithoutDuplicates.get(outputWithoutDuplicates.size() - 1) || combinedOutput.get(i) == 2) {
                outputWithoutDuplicates.add(combinedOutput.get(i));
            }
        }

        return addThrees(outputWithoutDuplicates);
    }

    public static void close() throws OrtException {
        if (session != null) {
            session.close();
        }
        if (env != null) {
            env.close();
        }
    }

    public static class PhonemizerInitializationException extends RuntimeException {
        public PhonemizerInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class PhonemizerInferenceException extends RuntimeException {
        public PhonemizerInferenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class PhonemizerCloseException extends RuntimeException {
        public PhonemizerCloseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}