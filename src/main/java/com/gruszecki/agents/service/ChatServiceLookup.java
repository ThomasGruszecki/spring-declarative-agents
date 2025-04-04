package com.gruszecki.agents.service;

import static lombok.AccessLevel.PRIVATE;

import com.gruszecki.agents.config.AgentProxyConfig;
import java.util.NoSuchElementException;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class ChatServiceLookup {

  @NonNull
  AgentProxyConfig proxyConfig;

  public ChatService getChatService(@NonNull final String api) {
    return proxyConfig.getChatServices().stream()
        .filter(service -> StringUtils.equals(service.getSupportedApi(), api))
        .findFirst()
        .orElseThrow(
            () -> new NoSuchElementException("No chat service for " + api)
        );
  }

}
