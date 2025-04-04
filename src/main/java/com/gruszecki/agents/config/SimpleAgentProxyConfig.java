package com.gruszecki.agents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gruszecki.agents.client.CompletionsClient;
import com.gruszecki.agents.service.ChatService;
import com.gruszecki.agents.service.PromptResolverService;
import com.gruszecki.agents.service.SimpleChatService;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Getter
public class SimpleAgentProxyConfig implements AgentProxyConfig {

  List<ChatService> chatServices;

  public SimpleAgentProxyConfig(
      @NonNull WebClient.Builder webClient,
      @NonNull ObjectMapper objectMapper,
      @NonNull LlmProperties llmProperties,
      @NonNull PromptResolverService promptResolverService) {
    this.chatServices = llmProperties.getProviders()
        .entrySet()
        .stream()
        .map(entry -> (ChatService) SimpleChatService.builder()
            .supportedApi(entry.getKey())
            .llmApiClient(
                CompletionsClient.builder()
                    .webClientBuilder(webClient)
                    .apiKey(entry.getValue().getApiKey())
                    .apiUrl(entry.getValue().getApiUrl())
                    .build()
            )
            .promptResolverService(promptResolverService)
            .objectMapper(objectMapper)
            .build()
        )
        .toList();
  }

}
