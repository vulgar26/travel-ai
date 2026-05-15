package com.travel.ai.tools;

/**
 * Minimal abstraction for tools executed through the agent's governance layer.
 */
public interface GovernedAgentTool {

    String name();

    boolean shouldHandle(String userMessage);

    String resolveInput(String userMessage);

    String observe(String input) throws Exception;
}

