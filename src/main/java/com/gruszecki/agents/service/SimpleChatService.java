package com.gruszecki.agents.service;

import static java.util.Objects.isNull;
import static lombok.AccessLevel.PRIVATE;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gruszecki.agents.client.AgentProxyApiClient;
import com.gruszecki.agents.domain.AgentProxyArguments;
import com.gruszecki.agents.domain.api.completions.ChatCompletionRequest;
import com.gruszecki.agents.domain.api.completions.ChatCompletionResponse;
import com.gruszecki.agents.domain.api.completions.Choice;
import com.gruszecki.agents.domain.api.completions.Message;
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
  AgentProxyApiClient agentProxyApiClient;

  @NonNull
  ObjectMapper objectMapper;

  @NonNull
  PromptResolverService promptResolverService;

  @NonNull
  @Getter
  String supportedApi;

  public Object handlePrompt(@NonNull final AgentProxyArguments agentProxyArguments) {
    String resolvedPrompt = promptResolverService.resolvePrompt(agentProxyArguments);
    log.debug("Resolved prompt for {}: {}", agentProxyArguments.getName(), resolvedPrompt);

    final ChatCompletionRequest requestBody = buildSimpleLlmRequestBody(agentProxyArguments.getModel(),
        agentProxyArguments.getSystemPrompt(),
        resolvedPrompt);
    log.trace("LLM Request Body: {}", requestBody.toString());

    // Simplified API call
    final Mono<ChatCompletionResponse> responseMono = agentProxyApiClient.createChatCompletion(requestBody);
    final ChatCompletionResponse rawResponse = responseMono.block();
    log.trace("LLM Raw Response: {}", rawResponse);

    if (isNull(rawResponse)) {
      throw new RuntimeException("Received null response from LLM API for method " + agentProxyArguments.getName());
    }

    try {
      return processLlmResponse(rawResponse, agentProxyArguments.getReturnType());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to process Llm Response", e);
    }
  }

  /**
   * Builds the request body string for the LLM API call. NOTE: Adapt based on the actual API documentation. ...
   */
  private ChatCompletionRequest buildSimpleLlmRequestBody(
      @NonNull final String model,
      final String systemPrompt,
      @NonNull final String userPrompt) {
    final List<Message> messages = new ArrayList<>();

    if (hasText(systemPrompt)) {
      messages.add(
          Message.builder()
              .role("system")
              .content(systemPrompt)
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
