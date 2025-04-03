package com.springllm.service;

import static lombok.AccessLevel.PRIVATE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springllm.annotations.LargeLanguageModelProxy;
import com.springllm.annotations.Prompt;
import com.springllm.config.LlmProperties;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class SimpleChatService implements ChatService {

  @NonNull
  WebClient webClient;

  @NonNull
  ObjectMapper objectMapper;

  @NonNull
  LlmProperties.ProviderConfig providerConfig;

  public Object handlePrompt(
      LargeLanguageModelProxy llmBeanAnnotation,
      Prompt promptAnnotation,
      Method method,
      Object[] args) {
    final String resolvedPrompt = resolvePrompt(promptAnnotation.value(), method.getParameters(), args);
    log.debug("Resolved prompt for {}: {}", method.getName(), resolvedPrompt);

    final String requestBody = buildLlmRequestBody(resolvedPrompt, llmBeanAnnotation);
    log.trace("LLM Request Body: {}", requestBody);

    final String apiUrl = providerConfig.getApiUrl();
    final String apiKey = providerConfig.getApiKey();

    // Simplified API call example - adapt path, headers, body structure
    Mono<String> responseMono = webClient.post()
        .uri(apiUrl + "/completions") // ADJUST API PATH AS NEEDED
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        // Add provider-specific headers if required
        .bodyValue(requestBody)
        .retrieve()
        .bodyToMono(String.class)
        .doOnError(error -> log.error("LLM API call failed for {}: {}",
            method.getName(),
            error.getMessage(),
            error)) // Log stack trace on error
        .onErrorMap(e -> new RuntimeException(
            "LLM API call failed for method " + method.getName() + ": " + e.getMessage(), e));

    String rawResponse = responseMono.block(); // Consider async handling if needed
    log.trace("LLM Raw Response: {}", rawResponse);

    if (rawResponse == null) {
      throw new RuntimeException("Received null response from LLM API for method " + method.getName());
    }

    try {
      return processLlmResponse(rawResponse, method.getReturnType());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to process Llm Response", e);
    }
  }

  // --- Helper Methods (getWebClient, getObjectMapper, resolvePrompt, buildLlmRequestBody, processLlmResponse) ---
  // These methods should now reliably use the initialized 'providerConfig', 'llmBeanAnnotation', etc.

  // resolvePrompt, buildLlmRequestBody, processLlmResponse methods remain the same as before
  // They can now safely use this.providerConfig and this.llmBeanAnnotation
  // ... (Include the full implementation of these methods from the previous version) ...

  /**
   * Replaces placeholders like {argName} in the prompt template with actual argument values. ... (implementation from
   * previous version) ...
   */
  private String resolvePrompt(String promptTemplate, Parameter[] parameters, Object[] args) {
    // (Implementation from previous version)
    if (args == null || args.length == 0) {
      return promptTemplate;
    }
    Map<String, Object> argMap = new HashMap<>();
    for (int i = 0; i < parameters.length; i++) {
      String paramName = parameters[i].getName();
      if (paramName.equals("arg" + i) && !parameters[i].isNamePresent()) {
        log.warn("Parameter names not available for method {}. " +
            "Ensure code is compiled with '-parameters' flag for reliable substitution by name. " +
            "Using arg{} fallback.", parameters[i].getDeclaringExecutable().getName(), i);
      }
      argMap.put(paramName, args[i]);
    }
    Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
    Matcher matcher = pattern.matcher(promptTemplate);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String key = matcher.group(1);
      Object value = argMap.get(key);
      if (value == null) {
        log.warn("No argument found for placeholder '{}' in prompt template for method {}",
            key,
            parameters[0].getDeclaringExecutable().getName());
        matcher.appendReplacement(sb, matcher.group(0));
      } else {
        String stringValue;
        if (value instanceof String) {
          stringValue = (String) value;
        } else if (value instanceof Map || value instanceof Iterable || (value != null && !value.getClass()
            .isPrimitive()
            && !value.getClass().getName().startsWith("java.lang"))) {
          try {
            stringValue = objectMapper.writeValueAsString(value);
          } catch (JsonProcessingException e) {
            log.warn("Could not serialize argument '{}' to JSON for prompt substitution. Using toString().", key, e);
            stringValue = value.toString();
          }
        } else {
          stringValue = value.toString();
        }
        matcher.appendReplacement(sb, Matcher.quoteReplacement(stringValue));
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * Builds the request body string for the LLM API call. NOTE: Adapt based on the actual API documentation. ...
   * (implementation from previous version) ...
   */
  private String buildLlmRequestBody(String userPrompt, LargeLanguageModelProxy llmBeanAnnotation) {
    // (Implementation from previous version - Example for OpenAI Chat)
    Map<String, Object> message = new HashMap<>();
    message.put("role", "user");
    message.put("content", userPrompt);
    Map<String, Object> requestBodyMap = new HashMap<>();
    requestBodyMap.put("model", llmBeanAnnotation.model());
    Object[] messages;
    if (StringUtils.hasText(llmBeanAnnotation.systemPrompt())) {
      Map<String, Object> systemMessage = new HashMap<>();
      systemMessage.put("role", "system");
      systemMessage.put("content", llmBeanAnnotation.systemPrompt());
      messages = new Object[]{systemMessage, message};
    } else {
      messages = new Object[]{message};
    }
    requestBodyMap.put("messages", messages);
    // Add other parameters like temperature, max_tokens etc. if needed
    try {
      return objectMapper.writeValueAsString(requestBodyMap);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize LLM request body", e);
      throw new RuntimeException("Failed to build LLM request body", e);
    }
  }

  /**
   * Processes the raw JSON response from the LLM and converts it to the target type. NOTE: Adapt based on the LLM API
   * response structure. ... (implementation from previous version) ...
   */
  private Object processLlmResponse(String rawJsonResponse, Class<?> returnType) throws JsonProcessingException {
    // (Implementation from previous version - Example for OpenAI Chat)
    ObjectMapper mapper = objectMapper;
    JsonNode rootNode = mapper.readTree(rawJsonResponse);
    JsonNode contentNode = rootNode.path("choices").path(0).path("message").path("content");
    Object result;
    if (contentNode.isMissingNode()) {
      log.error("Could not find 'choices[0].message.content' in LLM response: {}", rawJsonResponse);
      JsonNode errorNode = rootNode.path("error");
      if (!errorNode.isMissingNode()) {
        throw new RuntimeException("LLM API returned an error: " + errorNode.toString());
      }
      throw new RuntimeException("Unexpected LLM response structure. Could not extract content.");
    }
    String llmTextOutput = contentNode.asText();
    if (returnType.isAssignableFrom(String.class)) {
      return llmTextOutput;
    } else if (returnType.isAssignableFrom(JsonNode.class)) {
      try {
        return mapper.readTree(llmTextOutput);
      } catch (JsonProcessingException e) {
        log.error("LLM output was expected to be JSON, but failed to parse: {}", llmTextOutput, e);
        throw new RuntimeException("Failed to parse LLM output as JSON", e);
      }
    } else {
      try {
        return mapper.readValue(llmTextOutput, returnType);
      } catch (JsonProcessingException e) {
        log.warn("Could not directly convert LLM output string to type {}. Output: {}",
            returnType.getName(),
            llmTextOutput,
            e);
        throw new RuntimeException("Cannot convert LLM output to target type " + returnType.getName(), e);
      }
    }
  }

}
