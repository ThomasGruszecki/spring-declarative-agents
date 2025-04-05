package com.gruszecki.agents.domain.api.completions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Usage {

  @JsonProperty("prompt_tokens")
  Integer promptTokens;

  @JsonProperty("completion_tokens")
  Integer completionTokens;

  @JsonProperty("total_tokens")
  Integer totalTokens;
}