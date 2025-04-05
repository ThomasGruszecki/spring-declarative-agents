package com.gruszecki.agents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gruszecki.agents.service.ChatServiceLookup;
import com.gruszecki.agents.service.PromptResolverService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
public class AgentProxyBeansConfig {

  @Bean
  @ConfigurationProperties(prefix = "llm")
  AgentProxyProperties properties() {
    return new AgentProxyProperties();
  }

  @ConditionalOnMissingBean()
  @Bean
  public WebClient.Builder webClientBuilder() {
    log.warn("No WebClient.Builder bean found, " +
        "creating default WebClient instance. " +
        "Consider providing a configured bean.");

    return WebClient.builder()
        .exchangeStrategies(ExchangeStrategies.builder()
            .codecs(configurer ->
                configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)
            ) // 16MB buffer
            .build());
  }

  @ConditionalOnMissingBean
  @Bean
  public ObjectMapper objectMapper() {
    log.warn("No ObjectMapper bean found, " +
        "creating default ObjectMapper instance. " +
        "Consider providing a configured bean.");

    return new ObjectMapper();
  }

  @ConditionalOnMissingBean
  @Bean
  public ChatServiceConfig simpleAgentProxyConfig(
      @NonNull WebClient.Builder webClientBuilder,
      @NonNull ObjectMapper objectMapper,
      @NonNull AgentProxyProperties agentProxyProperties,
      @NonNull PromptResolverService promptResolverService) {
    return new SimpleChatServiceConfig(webClientBuilder, objectMapper, agentProxyProperties, promptResolverService);
  }

  @ConditionalOnMissingBean
  @Bean
  public ChatServiceLookup chatServiceLookup(ChatServiceConfig chatServiceConfig) {
    return ChatServiceLookup.builder()
        .proxyConfig(chatServiceConfig)
        .build();
  }

  @ConditionalOnMissingBean
  @Bean
  public PromptResolverService promptResolverService(ObjectMapper objectMapper) {
    return PromptResolverService.builder()
        .objectMapper(objectMapper)
        .build();
  }

}
