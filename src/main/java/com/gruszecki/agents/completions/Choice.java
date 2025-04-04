package com.gruszecki.agents.completions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Choice {

  @JsonProperty("index")
  Integer index;

  @JsonProperty("message")
  Message message;

  @JsonProperty("finish_reason")
  String finishReason;
}