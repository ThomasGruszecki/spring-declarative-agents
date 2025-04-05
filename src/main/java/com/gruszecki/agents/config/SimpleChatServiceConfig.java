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
public class SimpleChatServiceConfig implements ChatServiceConfig {

  List<ChatService> chatServices;

  public SimpleChatServiceConfig(
      @NonNull WebClient.Builder webClient,
      @NonNull ObjectMapper objectMapper,
      @NonNull AgentProxyProperties agentProxyProperties,
      @NonNull PromptResolverService promptResolverService) {
    this.chatServices = agentProxyProperties.getProviders()
        .entrySet()
        .stream()
        .map(entry -> (ChatService) SimpleChatService.builder()
            .supportedApi(entry.getKey())
            .agentProxyApiClient(
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
