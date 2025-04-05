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
public class Error {

  @JsonProperty("message")
  String message;

  @JsonProperty("type")
  String type;

  @JsonProperty("param")
  String param;

  @JsonProperty("code")
  String code;
}