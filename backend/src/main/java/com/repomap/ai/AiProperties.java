package com.repomap.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI service configuration, read from environment variables. */
@ConfigurationProperties(prefix = "ai")
@Component
public class AiProperties {

    /** Enable/disable the AI assistant feature. */
    private boolean enabled = false;

    /** Base URL of the AI provider API (e.g. https://api.openai.com/v1) */
    private String baseUrl;

    /** API key for the AI provider (must not be hardcoded; read from env). */
    private String apiKey;

    /** AI model name (e.g. gpt-4o-mini, claude-3-haiku). */
    private String model = "gpt-4o-mini";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}