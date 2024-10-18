package ivangka.autocontentbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@PropertySource("classpath:application.properties")
@Configuration
public class TextGenerationAsyncConfig {

    @Value("${api.text.async.uri}")
    private String uri;

    @Value("${api.text.async.uri.check}")
    private String uriCheck;

    @Value("${api.text.async.key}")
    private String apiKey;

    @Value("${api.text.async.folder.id}")
    private String folderId;

    @Value("${api.text.async.model.uri}")
    private String modelUri;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUriCheck() {
        return uriCheck;
    }

    public void setUriCheck(String uriCheck) {
        this.uriCheck = uriCheck;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getModelUri() {
        return modelUri;
    }

    public void setModelUri(String modelUri) {
        this.modelUri = modelUri;
    }

}
