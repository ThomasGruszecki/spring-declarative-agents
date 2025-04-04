package com.gruszecki.agents.completions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest {

  @JsonProperty("model")
  String model;

  @JsonProperty("messages")
  List<Message> messages;

  @JsonProperty("temperature")
  Double temperature;

  @JsonProperty("top_p")
  Double topP;

  @JsonProperty("n")
  Integer n;

  @JsonProperty("stream")
  Boolean stream;

  @JsonProperty("stop")
  List<String> stop;

  @JsonProperty("max_tokens")
  Integer maxTokens;

  @JsonProperty("presence_penalty")
  Double presencePenalty;

  @JsonProperty("frequency_penalty")
  Double frequencyPenalty;

  @JsonProperty("logit_bias")
  Map<String, Integer> logitBias;

  @JsonProperty("user")
  String user;

  @JsonProperty("tools")
  List<Tool> tools;

  @JsonProperty("tool_choice")
  ToolChoice toolChoice;
}
