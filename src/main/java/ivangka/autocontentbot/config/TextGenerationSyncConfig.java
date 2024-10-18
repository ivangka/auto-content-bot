package ivangka.autocontentbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@PropertySource("classpath:application.properties")
@Configuration
public class TextGenerationSyncConfig {

    @Value("${api.text.sync.uri}")
    private String uri;

    @Value("${api.text.sync.key}")
    private String apiKey;

    @Value("${api.text.sync.folder.id}")
    private String folderId;

    @Value("${api.text.sync.model.uri}")
    private String modelUri;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
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