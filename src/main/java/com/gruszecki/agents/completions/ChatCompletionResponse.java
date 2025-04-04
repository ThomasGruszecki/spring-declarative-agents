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
public class ChatCompletionResponse {

  @JsonProperty("id")
  String id;

  @JsonProperty("object")
  String object;

  @JsonProperty("created")
  Long created;

  @JsonProperty("model")
  String model;

  @JsonProperty("choices")
  List<Choice> choices;

  @JsonProperty("usage")
  Usage usage;
}