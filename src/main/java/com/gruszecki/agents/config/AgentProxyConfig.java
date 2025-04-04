package com.gruszecki.agents.config;

import com.gruszecki.agents.service.ChatService;
import java.util.List;

public interface AgentProxyConfig {

  List<ChatService> getChatServices();

}
