package com.gruszecki.agents.completions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionDefinition {

  @JsonProperty("name")
  String name;

  @JsonProperty("description")
  String description;

  @JsonProperty("parameters")
  Map<String, Object> parameters;
}
