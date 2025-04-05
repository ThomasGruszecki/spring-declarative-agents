package com.gruszecki.agents.client;

import static lombok.AccessLevel.PRIVATE;

import com.gruszecki.agents.domain.api.completions.ChatCompletionChunk;
import com.gruszecki.agents.domain.api.completions.ChatCompletionRequest;
import com.gruszecki.agents.domain.api.completions.ChatCompletionResponse;
import lombok.Builder;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class CompletionsClient implements AgentProxyApiClient {

  WebClient.Builder webClientBuilder;

  String apiKey;

  String apiUrl;

  /**
   * Sends a chat completion request to the OpenAI API
   *
   * @param request The chat completion request
   * @return A Mono containing the chat completion response
   */
  public Mono<ChatCompletionResponse> createChatCompletion(ChatCompletionRequest request) {
    return getWebClient()
        .post()
        .uri("/chat/completions")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(ChatCompletionResponse.class)
        .doOnError(e -> log.error("Error calling OpenAI API: {}", e.getMessage()));
  }

  /**
   * Sends a streaming chat completion request to the OpenAI API
   *
   * @param request The chat completion request (stream should be set to true)
   * @return A Flux of streaming chat completion chunks
   */
  public Flux<ChatCompletionChunk> createChatCompletionStream(ChatCompletionRequest request) {
    // Ensure stream is set to true
    Assert.isTrue(request.getStream(), "Streaming response MUST have stream set to true");

    return getWebClient()
        .post()
        .uri("/chat/completions")
        .bodyValue(request)
        .retrieve()
        .bodyToFlux(ChatCompletionChunk.class)
        .doOnError(e -> log.error("Error streaming from OpenAI API: {}", e.getMessage()));
  }

  private WebClient getWebClient() {
    return webClientBuilder
        .baseUrl(apiUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .build();
  }

}
