package com.springllm.config;

import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springllm.service.ChatService;
import com.springllm.service.SimpleChatService;
import java.util.Map;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
@Getter
public class SimpleAgentProxyConfig implements AgentProxyConfig {

  Map<String, ChatService> chatServices;

  public SimpleAgentProxyConfig(
      @NonNull WebClient webClient,
      @NonNull ObjectMapper objectMapper,
      @NonNull LlmProperties llmProperties) {
    this.chatServices = llmProperties.getProviders()
        .entrySet()
        .stream()
        .collect(toMap(
            entry -> entry.getKey(),
            entry -> SimpleChatService.builder()
                    .providerConfig(entry.getValue())
                .webClient(webClient)
                .objectMapper(objectMapper)
                .build()
            )
        );
  }

}
