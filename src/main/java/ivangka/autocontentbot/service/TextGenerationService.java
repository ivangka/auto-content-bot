package ivangka.autocontentbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ivangka.autocontentbot.config.TextGenerationAsyncConfig;
import ivangka.autocontentbot.config.TextGenerationSyncConfig;
import ivangka.autocontentbot.model.Channel;
import ivangka.autocontentbot.model.Post;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class TextGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(TextGenerationService.class);

    private final TextGenerationAsyncConfig textGenerationAsyncConfig;
    private final TextGenerationSyncConfig textGenerationSyncConfig;
    private final String responseStyle;

    @Autowired
    public TextGenerationService(TextGenerationAsyncConfig textGenerationAsyncConfig,
                                 TextGenerationSyncConfig textGenerationSyncConfig) {

        this.textGenerationAsyncConfig = textGenerationAsyncConfig;
        this.textGenerationSyncConfig = textGenerationSyncConfig;
        this.responseStyle = "Стиль ответа: " +
                "в первой строчке жирным шрифтом придуманное тобой название темы, " +
                "далее через одну пустую строку само содержание, " +
                "а в конце, через одну пустую строчку, 2-4 хэштега " +
                "(пост должен содержать меньше 1000 символов).";

    }

    public String generateFullText(Channel channel, String topic) {
        logger.debug("Generating full text for channel: {}, topic: {}", channel.getName(), topic);

        String prompt = "Ты — опытный копирайтер, автор телеграмм канала. " +
                "Напиши пост с учётом тематики канала и заданной темы. " + responseStyle;

        String userRequestText;
        if (channel.getTags().contains(",")) {
            Random rand = new Random();
            String[] fullTags = channel.getTags().split(", ");
            StringBuilder currTags = new StringBuilder(fullTags[rand.nextInt(fullTags.length)]);
            for (int i = 0; i < rand.nextInt(fullTags.length / 2); i++) {
                currTags.append(", ");
                currTags.append(fullTags[i]);
            }
            if (topic == null) {
                topic = "Придумай сам наугад";
            }
            userRequestText = "Тематика канала: " + currTags + ". Тема поста: " + topic;
        } else {
            userRequestText = "Тематика канала: " + channel.getTags() + ". Тема поста: " + topic;
        }

        logger.debug("User request text for generation: {}", userRequestText);

        return generateTextWithRetries(channel, prompt, userRequestText);
    }

    public String editFullText(Post post, String edits) {
        logger.debug("Editing full text for post: {}, edits: {}", post.getTitle(), edits);

        String prompt = "Ты — опытный копирайтер, автор телеграмм канала. " +
                "Отредактируй пост - " + edits + " " + responseStyle;
        String userRequestText = post.getFullText();

        return generateTextWithRetries(post.getChannel(), prompt, userRequestText);
    }

    private String httpRequestAsync(Channel channel, String prompt, String userRequestText)
            throws IOException, InterruptedException {
        logger.debug("Sending async HTTP request for text generation, channel: {}, prompt: {}",
                channel.getName(), prompt);

        // creating request body
        Map<String, Object> completionOptions = new HashMap<>();
        completionOptions.put("stream", false);
        completionOptions.put("temperature", channel.getTemperature());
        completionOptions.put("maxTokens", "500");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("modelUri", textGenerationAsyncConfig.getModelUri());
        requestBody.put("completionOptions", completionOptions);
        requestBody.put("messages", new Object[]{
                Map.of("role", "system", "text", userRequestText),
                Map.of("role", "user", "text", prompt)
        });

        // sending post request
        HttpURLConnection connection = (HttpURLConnection) new URL(textGenerationAsyncConfig.getUri()).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Api-Key " + textGenerationAsyncConfig.getApiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("x-folder-id", textGenerationAsyncConfig.getFolderId());
        connection.setDoOutput(true);

        connection.setConnectTimeout(5000); // 5 seconds connection timeout
        connection.setReadTimeout(10000); // 10 seconds read timeout

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        logger.debug("Request sent successfully, awaiting response...");

        // getting id of the operation
        InputStream responseStream;
        try {
            responseStream = connection.getInputStream();
        } catch (Exception e) {
            logger.error("Failed to get input stream from connection: {}", e.getMessage(), e);
            return null;
        }
        String response = IOUtils.toString(responseStream, StandardCharsets.UTF_8);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        String operationId = (String) responseMap.get("id");
        logger.debug("Received operation ID: {}", operationId);

        // waiting for operation to complete
        for (int i = 0; i < 20; i++) {
            logger.debug("Checking operation status, attempt {} [LLM API Async]", i + 1);

            Thread.sleep(2000); // waiting before next request
            HttpURLConnection checkConnection =
                    (HttpURLConnection) new URL(textGenerationAsyncConfig.getUriCheck() +
                            operationId).openConnection();
            checkConnection.setRequestProperty("Authorization", "Api-Key " + textGenerationAsyncConfig.getApiKey());
            checkConnection.setRequestMethod("GET");

            InputStream checkStream = checkConnection.getInputStream();
            String checkResponse = IOUtils.toString(checkStream, StandardCharsets.UTF_8);
            Map<String, Object> checkResponseMap = objectMapper.readValue(checkResponse, Map.class);

            if ((boolean) checkResponseMap.get("done")) {
                logger.debug("Text generation completed for operation ID: {}", operationId);

                // getting "text" from response
                String text;
                try {
                    Map<String, Object> responseResult = (Map<String, Object>) checkResponseMap.get("response");
                    List<Map<String, Object>> alternatives =
                            (List<Map<String, Object>>) responseResult.get("alternatives");
                    text = (String) ((Map<String, Object>) alternatives.get(0).get("message")).get("text");
                } catch (ClassCastException | NullPointerException e) {
                    logger.error("Failed to extract text from response: {}", e.getMessage(), e);
                    return null;
                }

                logger.debug("Extracted text from response: {}", text);
                return text;
            }

        }

        logger.warn("Text generation failed after multiple attempts.");
        return null;
    }

    private String httpRequestSync(Channel channel, String prompt, String userRequestText)
            throws IOException {
        logger.debug("Sending sync HTTP request for text generation, channel: {}, prompt: {}",
                channel.getName(), prompt);

        // Creating request body
        Map<String, Object> completionOptions = new HashMap<>();
        completionOptions.put("stream", false);
        completionOptions.put("temperature", channel.getTemperature());
        completionOptions.put("maxTokens", "500");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("modelUri", textGenerationSyncConfig.getModelUri());
        requestBody.put("completionOptions", completionOptions);
        requestBody.put("messages", new Object[]{
                Map.of("role", "system", "text", prompt),
                Map.of("role", "user", "text", userRequestText)
        });

        // Sending POST request
        HttpURLConnection connection = (HttpURLConnection) new URL(textGenerationSyncConfig.getUri()).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Api-Key " + textGenerationSyncConfig.getApiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("x-folder-id", textGenerationSyncConfig.getFolderId());
        connection.setDoOutput(true);

        connection.setConnectTimeout(5000); // 5 seconds connection timeout
        connection.setReadTimeout(10000); // 10 seconds read timeout

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        logger.debug("Request sent successfully, awaiting response...");

        // Getting the response
        InputStream responseStream;
        try {
            responseStream = connection.getInputStream();
        } catch (Exception e) {
            logger.error("Failed to get input stream from connection: {}", e.getMessage(), e);
            return null;
        }

        String response = IOUtils.toString(responseStream, StandardCharsets.UTF_8);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        // Extracting "text" from response
        String text;
        try {
            Map<String, Object> result = (Map<String, Object>) responseMap.get("result");
            List<Map<String, Object>> alternatives = (List<Map<String, Object>>) result.get("alternatives");
            text = (String) ((Map<String, Object>) alternatives.get(0).get("message")).get("text");
        } catch (ClassCastException | NullPointerException e) {
            logger.error("Failed to extract text from response: {}", e.getMessage(), e);
            return null;
        }

        logger.debug("Extracted text from response: {}", text);
        return text;
    }

    private String generateTextWithRetries(Channel channel, String prompt, String userRequestText) {

        String textFromResponse;
        for (int i = 0; i < 2; i++) {
            try {
                textFromResponse = httpRequestAsync(channel, prompt, userRequestText);
            } catch (IOException | InterruptedException e) {
                logger.error("Exception while generating text for the post [LLM API Async]", e);
                continue;
            }
            if (textFromResponse == null) {
                break;
            }
            if (textFromResponse.length() <= 1000) { // additional filter. post length must be <= 1024 symbols
                logger.debug("Generated text (async) is within the acceptable length: {} [LLM API Async]",
                        textFromResponse.length());
                return textFromResponse;
            }
            logger.debug("Generated text exceeded 1000 symbols (number of symbols in the text: {}), " +
                    "retrying... (Attempt {}) [LLM API Async]", textFromResponse.length(), i + 1);
        }
        logger.warn("Failed to generate text to acceptable length after all attempts with LLM API Async.");

        // if all attempts with async api failed
        for (int i = 0; i < 3; i++) {
            try {
                textFromResponse = httpRequestSync(channel, prompt, userRequestText);
            } catch (IOException e) {
                logger.error("Exception while generating text for the post [LLM API Sync]", e);
                return null;
            }
            if (textFromResponse == null) {
                continue;
            }
            if (textFromResponse.length() <= 1000) { // additional filter. post length must be <= 1024 symbols
                logger.debug("Generated text is within the acceptable length: {} [LLM API Sync]",
                        textFromResponse.length());
                return textFromResponse;
            }
            logger.debug("Generated text exceeded 1000 symbols (number of symbols in the text: {}), " +
                    "retrying... (Attempt {}) [LLM API Sync]", textFromResponse.length(), i + 1);
        }
        logger.warn("Failed to generate text to acceptable length after all attempts with LLM API Sync.");

        logger.warn("Failed to generate text to acceptable length after all attempts.");
        return null;
    }

}
