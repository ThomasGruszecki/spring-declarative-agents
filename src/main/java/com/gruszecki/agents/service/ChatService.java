package com.gruszecki.agents.service;

import com.gruszecki.agents.domain.AgentProxyArguments;

public interface ChatService {

  Object handlePrompt(AgentProxyArguments agentProxyArguments);

  String getSupportedApi();
}
