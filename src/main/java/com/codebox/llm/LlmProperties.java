package com.codebox.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek (OpenAI-compatible) connection settings.
 * The API key is read from the DEEPSEEK_API_KEY environment variable by default,
 * so no secret is ever committed to the repository.
 */
@ConfigurationProperties(prefix = "codebox.llm")
public class LlmProperties {

    /** When false, the LLM layer is disabled and the app degrades to keyword-only search. */
    private boolean enabled = true;

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private int timeoutSeconds = 60;

    /** Model used for embeddings; DeepSeek has no public embedding endpoint, so this
     *  points at an OpenAI-compatible provider (e.g. SiliconFlow / DashScope). */
    private String embeddingModel = "";
    private String embeddingBaseUrl = "";
    private String embeddingApiKey = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public String getEmbeddingBaseUrl() { return embeddingBaseUrl; }
    public void setEmbeddingBaseUrl(String embeddingBaseUrl) { this.embeddingBaseUrl = embeddingBaseUrl; }

    public String getEmbeddingApiKey() { return embeddingApiKey; }
    public void setEmbeddingApiKey(String embeddingApiKey) { this.embeddingApiKey = embeddingApiKey; }

    public boolean hasChatKey() { return enabled && apiKey != null && !apiKey.isBlank(); }
    public boolean hasEmbeddingEndpoint() {
        return enabled && embeddingModel != null && !embeddingModel.isBlank()
                && embeddingBaseUrl != null && !embeddingBaseUrl.isBlank();
    }
}
