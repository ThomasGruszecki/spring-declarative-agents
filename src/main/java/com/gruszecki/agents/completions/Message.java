package com.gruszecki.agents.completions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

  @JsonProperty("role")
  String role;

  @JsonProperty("content")
  String content;

  @JsonProperty("name")
  String name;

  @JsonProperty("tool_calls")
  List<ToolCall> toolCalls;

  @JsonProperty("tool_call_id")
  String toolCallId;
}
