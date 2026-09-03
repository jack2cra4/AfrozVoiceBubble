package com.afroz.voicebubble.ai;

/**
 * Provider abstraction for AI backends.
 *
 * JARVIS routes a task through the appropriate provider (local model, a
 * configured cloud provider, or a custom endpoint) and falls back to a local
 * engine if a cloud call fails. Implementations run off the UI thread.
 */
public interface AIProvider {

    /** Human-readable provider name (e.g. "OpenAI", "Gemini", "LOCAL"). */
    String name();

    /**
     * Optionally configured model; empty for providers without a model picker.
     */
    String model();

    /** True if this provider has the credentials/config it needs. */
    boolean isConfigured();

    /**
     * Produce an answer for the given prompt.
     *
     * @param prompt       task/prompt text to answer.
     * @param systemPrompt optional system instructions (agent role), may be null.
     * @return the textual answer, or null on failure (caller falls back).
     */
    String complete(String prompt, String systemPrompt);
}
