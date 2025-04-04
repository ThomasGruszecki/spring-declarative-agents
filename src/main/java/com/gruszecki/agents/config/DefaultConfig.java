package com.gruszecki.agents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gruszecki.agents.service.ChatServiceLookup;
import com.gruszecki.agents.service.PromptResolverService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class DefaultConfig {

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
  public AgentProxyConfig simpleAgentProxyConfig(
      @NonNull WebClient.Builder webClientBuilder,
      @NonNull ObjectMapper objectMapper,
      @NonNull LlmProperties llmProperties,
      @NonNull PromptResolverService promptResolverService) {
    return new SimpleAgentProxyConfig(webClientBuilder, objectMapper, llmProperties, promptResolverService);
  }

  @ConditionalOnMissingBean
  @Bean
  public ChatServiceLookup chatServiceLookup(AgentProxyConfig agentProxyConfig) {
    return ChatServiceLookup.builder()
        .proxyConfig(agentProxyConfig)
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
