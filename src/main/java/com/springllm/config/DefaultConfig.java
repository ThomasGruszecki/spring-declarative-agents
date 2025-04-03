package com.springllm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springllm.service.ChatServiceLookup;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class DefaultConfig {

  @ConditionalOnMissingBean
  @Bean
  public WebClient webClient() {
    log.warn("No WebClient.Builder bean found, " +
        "creating default WebClient instance. " +
        "Consider providing a configured bean.");
    return WebClient.create();
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
      @NonNull WebClient webClient,
      @NonNull ObjectMapper objectMapper,
      @NonNull LlmProperties llmProperties) {
    return new SimpleAgentProxyConfig(webClient, objectMapper, llmProperties);
  }

  @ConditionalOnMissingBean
  @Bean
  public ChatServiceLookup chatServiceLookup(AgentProxyConfig agentProxyConfig) {
    return new ChatServiceLookup(agentProxyConfig);
  }

}
