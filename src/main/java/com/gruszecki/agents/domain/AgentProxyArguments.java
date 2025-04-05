package com.gruszecki.agents.domain;

import java.lang.reflect.Parameter;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class AgentProxyArguments {

  @NonNull
  String model; // The model identifier to be used by the agent.

  @NonNull
  String systemPrompt; // The system prompt to initialize the agent's context.

  @NonNull
  String userPrompt; // The user prompt to be processed by the agent.

  @NonNull
  String name;

  @NonNull
  Class<?> returnType;

  @NonNull
  List<AgentProxyArgument> arguments;

  @Value
  @Builder
  public static class AgentProxyArgument {

    Parameter parameter;
    Object value;
  }
}
