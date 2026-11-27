package com.repomap.ai;

/** Interface for AI-powered code assistance. */
public interface AiService {

    /** Ask the AI a question about the codebase. */
    String ask(String question, String context);

    /** Generate a summary of a code element. */
    String summarize(String code, String language);
}