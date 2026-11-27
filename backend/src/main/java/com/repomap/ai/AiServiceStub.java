package com.repomap.ai;

import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

/** No-op AI service used when AI is disabled via ai.enabled=false. */
@Service
public class AiServiceStub implements AiService {

    @PostConstruct
    void logDisabled() {
        // no-op: AI is intentionally disabled
    }

    @Override
    public String ask(String question, String context) {
        return "AI assistant is disabled. Set AI_ENABLED=true and provide AI_API_KEY to enable.";
    }

    @Override
    public String summarize(String code, String language) {
        return "AI summarisation disabled. Set AI_ENABLED=true to enable.";
    }
}