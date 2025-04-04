package com.gruszecki.agents.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static lombok.AccessLevel.PRIVATE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gruszecki.agents.annotations.LargeLanguageModelProxy;
import com.gruszecki.agents.annotations.Prompt;
import com.gruszecki.agents.client.LlmApiClient;
import com.gruszecki.agents.completions.ChatCompletionRequest;
import com.gruszecki.agents.completions.ChatCompletionResponse;
import com.gruszecki.agents.completions.Choice;
import com.gruszecki.agents.completions.Message;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SequencedCollection;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class SimpleChatService implements ChatService {

  @NonNull
  LlmApiClient llmApiClient;

  @NonNull
  ObjectMapper objectMapper;

  @NonNull
  PromptResolverService promptResolverService;

  @NonNull
  @Getter
  String supportedApi;

  public Object handlePrompt(
      LargeLanguageModelProxy llmBeanAnnotation,
      Prompt promptAnnotation,
      Method method,
      Object[] args) {
    String resolvedPrompt = promptResolverService.resolvePrompt(promptAnnotation.value(), method.getParameters(), args);
    log.debug("Resolved prompt for {}: {}", method.getName(), resolvedPrompt);

    final ChatCompletionRequest requestBody = buildSimpleLlmRequestBody(resolvedPrompt, llmBeanAnnotation);
    log.trace("LLM Request Body: {}", requestBody.toString());

    // Simplified API call
    final Mono<ChatCompletionResponse> responseMono = llmApiClient.createChatCompletion(requestBody);
    final ChatCompletionResponse rawResponse = responseMono.block();
    log.trace("LLM Raw Response: {}", rawResponse);

    if (isNull(rawResponse)) {
      throw new RuntimeException("Received null response from LLM API for method " + method.getName());
    }

    try {
      return processLlmResponse(rawResponse, method.getReturnType());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to process Llm Response", e);
    }
  }

  /**
   * Builds the request body string for the LLM API call. NOTE: Adapt based on the actual API documentation. ...
   * (implementation from previous version) ...
   */
  private ChatCompletionRequest buildSimpleLlmRequestBody(String userPrompt,
      LargeLanguageModelProxy llmBeanAnnotation) {
    final String model = llmBeanAnnotation.model();
    final List<Message> messages = new ArrayList<>();

    if (nonNull(llmBeanAnnotation.systemPrompt())) {
      messages.add(
          Message.builder()
              .role("system")
              .content(llmBeanAnnotation.systemPrompt())
              .build()
      );
    }

    messages.add(
        Message.builder()
            .role("user")
            .content(userPrompt)
            .build()
    );

    return ChatCompletionRequest.builder()
        .model(model)
        .messages(messages)
        .build();
  }

  /**
   * Processes the raw JSON response from the LLM and converts it to the target type.
   */
  private Object processLlmResponse(ChatCompletionResponse response, Class<?> returnType)
      throws JsonProcessingException {
    final String responseContent = Optional.ofNullable(response.getChoices())
        .map(SequencedCollection::getFirst)
        .map(Choice::getMessage)
        .map(Message::getContent)
        .orElseThrow(() -> new RuntimeException("Unexpected LLM response structure. Could not extract content."));

    return returnType.isAssignableFrom(String.class)
        ? responseContent
        : returnType.isAssignableFrom(JsonNode.class)
            ? objectMapper.readTree(responseContent)
            : objectMapper.readValue(responseContent, returnType);
  }

}
