package com.springllm.service;

import static java.util.Objects.isNull;
import static lombok.AccessLevel.PRIVATE;

import com.springllm.config.AgentProxyConfig;
import java.util.NoSuchElementException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ChatServiceLookup {

  @NonNull
  AgentProxyConfig proxyConfig;

  public ChatService getChatService(String name) {
    final ChatService chatService = proxyConfig.getChatServices().get(name);
    if (isNull(chatService)) {
      throw new NoSuchElementException("No chat service for " + name);
    }
    return chatService;
  }

}
