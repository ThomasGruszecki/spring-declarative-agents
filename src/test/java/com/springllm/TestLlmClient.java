package com.springllm;

import com.springllm.annotations.LargeLanguageModelProxy;
import com.springllm.annotations.Prompt;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * A test interface annotated with @LargeLanguageModelProxy to be implemented
 * dynamically by the starter's proxy mechanism during integration tests.
 */
@LargeLanguageModelProxy(
        api = "TestLM", // Must match a provider key in application-test.yml
        model = "test-model-v1",
        systemPrompt = "You are a helpful test assistant."
        // beanName = "myCustomTestClient" // Optional custom bean name
)
public interface TestLlmClient {

    /**
     * Simple prompt without arguments.
     */
    @Prompt("Tell me a testing joke.")
    String getJoke();

    /**
     * Prompt with a dynamic argument.
     * @param topic The topic for the fact.
     * @return A fact about the topic.
     */
    @Prompt("Give me a fun fact about {topic}.")
    String getFact(String topic);

    /**
     * Prompt expecting a structured JSON response.
     * @param dataMap A map to be converted to JSON by the LLM.
     * @return A JsonNode representing the structured response.
     */
    @Prompt("Convert the following map to a JSON object: {dataMap}")
    JsonNode convertMapToJson(Map<String, Object> dataMap);

    /**
     * Method without @Prompt annotation to test handling.
     */
    String methodWithoutPrompt();

    /**
//     * Method with void return type.
     */
    @Prompt("Acknowledge this: {message}")
    void acknowledge(String message);
}