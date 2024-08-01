import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ApiCall {
    private static final String API_URL = "http://35.224.101.18:8000/talk_to_agents/";
    private final String geminiApiKey;
    private final ObjectMapper objectMapper;

    public ApiCall(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
        this.objectMapper = new ObjectMapper();
    }

    public String sendAudioToApi(Path audioFile, int speakerId) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("audio_file", audioFile.toFile(), ContentType.create("audio/wav"), "audio.wav");

            // Add pickled history file
            File historyFile = new File("updated_history.pickle");
            if (!historyFile.exists()) {
                historyFile = new File("zero_history.pickle");
            }
            builder.addBinaryBody("history_file", historyFile, ContentType.create("application/octet-stream"), historyFile.getName());

            // Add speaker number and Gemini API key
            builder.addTextBody("agent_number", String.valueOf(speakerId));
            builder.addTextBody("gemini_api_key", geminiApiKey);

            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                System.out.println("Response status code: " + statusCode);
                System.out.println("Response body: " + responseBody);

                if (statusCode == 200) {
                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    String narration = rootNode.get("narration").asText();
                    String updatedHistory = rootNode.get("updated_history").asText();

                    // Save the updated history
                    saveUpdatedHistory(updatedHistory);

                    return narration;
                } else {
                    throw new IOException("API request failed. Status code: " + statusCode + ", Body: " + responseBody);
                }
            }
        }
    }

    private void saveUpdatedHistory(String updatedHistory) throws IOException {
        File historyFile = new File("updated_history.pickle");
        objectMapper.writeValue(historyFile, updatedHistory);
        System.out.println("Updated history saved to: " + historyFile.getAbsolutePath());
    }
}