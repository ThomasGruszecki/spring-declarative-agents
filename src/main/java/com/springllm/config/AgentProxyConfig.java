package com.springllm.config;

import com.springllm.service.ChatService;
import java.util.Map;

public interface AgentProxyConfig {

  Map<String, ChatService> getChatServices();

}
