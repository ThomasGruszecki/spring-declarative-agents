# TODO List for Spring Declarative Agents

This list outlines planned features and improvements for the `spring-declarative-agents`.

## High Priority

* **Function Calling Support:**
    * Integrate Spring AI's function calling capabilities.
    * Allow defining methods in the interface that correspond to callable functions.
    * Enable the LLM to request function execution based on the prompt.
    * Handle the function execution loop (calling the function and sending results back to the LLM).
    * Potentially use annotations (`@LlmFunction`, `@FunctionTool`) on methods or separate tool
      beans.
    * May also add my own custom version of tool calling

* **Streaming Support:**
    * Add support for streaming responses (`Flux<String>`, `Flux<ChatResponse>`, etc.).
    * Possibly controlled via method return type or an annotation property (`@Prompt(stream=true)`).

* **MCP Support**
    * Of course

## Medium Priority

* **Advanced `ChatOptions` Configuration:**
    * Allow `ChatOptions` (temperature, topP, maxTokens, stop sequences) to be configured via
      `@LargeLanguageModelBean` and/or `@Prompt` annotations.
    * Define clear precedence rules (e.g., `@Prompt` overrides `@LargeLanguageModelBean` overrides
      Spring AI defaults).

* **Enhanced Provider/Client Selection:**
    * Improve support for selecting specific, *named* `ChatClient` beans (when multiple providers
      like OpenAI, Ollama, Anthropic are configured via Spring AI) using the `api` attribute in
      `@LargeLanguageModelBean`.
    * Requires clear documentation on bean naming conventions for `ChatClient` beans.

* **Improved Error Handling:**
    * Provide more specific custom exceptions for different failure scenarios (e.g., API
      authentication errors, rate limiting, prompt resolution failure, response parsing errors).
    * Expose error details from the underlying Spring AI `ChatResponse` metadata where available.

* **POJO Return Type Conversion:**
    * Enhance automatic conversion of LLM responses (expected to be JSON/structured text) to
      arbitrary POJO return types, potentially using the configured `ObjectMapper`.

## Low Priority / Nice-to-Haves

* **Documentation Expansion:**
    * Add more examples covering different LLMs and use cases.
    * Document advanced configuration options and extension points.
    * Provide guidance on prompt engineering best practices within the context of the starter.

* **Testing Coverage:**
    * Increase integration test coverage for various LLM providers (using mocks or test containers).
    * Add tests for error scenarios, streaming, and function calling once implemented.

* **Configuration Flexibility:**
    * Explore ways to configure default `ChatOptions` globally or per-provider via starter-specific
      properties (complementing Spring AI's properties).

* **Async Support:**
    * Investigate providing fully non-blocking options using Project Reactor return types (
      `Mono<T>`) beyond just streaming.