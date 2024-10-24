package ivangka.autocontentbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ivangka.autocontentbot.config.ImageGenerationAsyncConfig;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class ImageGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(ImageGenerationService.class);

    private final ImageGenerationAsyncConfig imageGenerationAsyncConfig;

    @Autowired
    public ImageGenerationService(ImageGenerationAsyncConfig imageGenerationAsyncConfig) {
        this.imageGenerationAsyncConfig = imageGenerationAsyncConfig;
    }

    public File generateImage(long userId, String userPrompt) throws IOException, InterruptedException {
        logger.debug("[User ID: {}] Starting image generation for userId: {} with prompt: {}", userId,
                userId, userPrompt);

        String prompt = userPrompt + ". Фотореализм";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("modelUri", imageGenerationAsyncConfig.getModelUri());

        Map<String, Object> generationOptions = new HashMap<>();
        Random rand = new Random();
        generationOptions.put("seed", rand.nextInt());

        Map<String, String> aspectRatio = new HashMap<>();
        aspectRatio.put("widthRatio", "1");
        aspectRatio.put("heightRatio", "1");
        generationOptions.put("aspectRatio", aspectRatio);

        requestBody.put("generationOptions", generationOptions);

        Map<String, String> message = new HashMap<>();
        message.put("weight", "1");
        message.put("text", prompt);

        requestBody.put("messages", new Map[]{message});

        // sending post request
        logger.debug("[User ID: {}] Sending POST request to URI: {}", userId, imageGenerationAsyncConfig.getUri());
        HttpURLConnection connection = (HttpURLConnection) new URL(
                imageGenerationAsyncConfig.getUri()).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Api-key " + imageGenerationAsyncConfig.getApiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        connection.setConnectTimeout(5000); // 5 seconds connection timeout
        connection.setReadTimeout(10000); // 10 seconds read timeout

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        logger.debug("[User ID: {}] Request sent successfully, awaiting response...", userId);

        // getting id operation
        InputStream responseStream;
        for (int i = 0; true; i++) {
            try {
                responseStream = connection.getInputStream();
                break;
            } catch (Exception e) {
                logger.error("[User ID: {}] Failed to get input stream from connection: {}", userId, e.getMessage(), e);
                if (i == 1) {
                    return null;
                }
                Thread.sleep(7000);
            }
        }
        String response = IOUtils.toString(responseStream, StandardCharsets.UTF_8);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        String operationId = (String) responseMap.get("id");
        logger.debug("[User ID: {}] Received operation ID: {}", userId, operationId);

        // waiting for operation to complete
        for (int i = 0; i < 20; i++) {
            logger.debug("Checking operation status, attempt {} [Image Generation Async]", i + 1);

            Thread.sleep(2000); // waiting before next request
            HttpURLConnection checkConnection = (HttpURLConnection) new URL(
                    imageGenerationAsyncConfig.getUriCheck() + operationId).openConnection();
            checkConnection.setRequestProperty("Authorization",
                    "Api-key " + imageGenerationAsyncConfig.getApiKey());
            checkConnection.setRequestMethod("GET");

            InputStream checkStream = checkConnection.getInputStream();
            String checkResponse = IOUtils.toString(checkStream, StandardCharsets.UTF_8);
            Map<String, Object> checkResponseMap = objectMapper.readValue(checkResponse, Map.class);

            if ((boolean) checkResponseMap.get("done")) {
                logger.debug("[User ID: {}] Image generation completed for operation ID: {}", userId, operationId);

                // getting base64 string and decoding
                Map<String, Object> responseResult = (Map<String, Object>) checkResponseMap.get("response");
                String base64Image = (String) responseResult.get("image");
                byte[] imageBytes = Base64.getDecoder().decode(base64Image);

                // saving the image
                String folderPath = "src/main/resources/static/images/";
                String fileName = "image" + userId + ".jpeg";
                String fileFullPath = folderPath + fileName;
                new File(folderPath).mkdirs();
                try (FileOutputStream fos = new FileOutputStream(fileFullPath)) {
                    fos.write(imageBytes);
                }
                logger.info("[User ID: {}] Image saved successfully at {}", userId, fileFullPath);

                return new File(fileFullPath);
            }

        }

        logger.warn("[User ID: {}] Image generation failed for userId: {} after multiple attempts", userId, userId);
        return null;
    }

}
