package com.gruszecki.agents.client;

import com.gruszecki.agents.completions.ChatCompletionChunk;
import com.gruszecki.agents.completions.ChatCompletionRequest;
import com.gruszecki.agents.completions.ChatCompletionResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LlmApiClient {

  /**
   * Sends a chat completion request to the OpenAI API
   *
   * @param request The chat completion request
   * @return A Mono containing the chat completion response
   */
  Mono<ChatCompletionResponse> createChatCompletion(ChatCompletionRequest request);

  /**
   * Sends a streaming chat completion request to the OpenAI API
   *
   * @param request The chat completion request (stream should be set to true)
   * @return A Flux of streaming chat completion chunks
   */
  default Flux<ChatCompletionChunk> createChatCompletionStream(ChatCompletionRequest request) {
    throw new UnsupportedOperationException("Streaming response not supported for " + this.getClass().getName());
  }

}
