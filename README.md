# Spring Declarative Agents

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A Spring Boot starter that allows you to create declarative Large Language Model (LLM) clients using simple Java interfaces and annotations. 
It uses Spring's `WebClient` for making direct HTTP calls to configured LLM API endpoints.

## Features

* **Declarative Clients:** Define LLM interactions using annotated Java interfaces.
* **Annotation-Driven:** Use `@AgentProxy` and `@Prompt` annotations to configure clients and methods.
* **Custom Configuration:** Configure multiple LLM providers via `application.yml`.
* **Argument Substitution:** Easily pass method arguments into your prompts.
* **Structured Output:** Supports basic handling for `String` and `JsonNode` return types.

## Prerequisites

* Java 24
* Spring Boot 3.x+
* Maven or Gradle

## Installation

You need to use maven and install it on local yourself right now.
Eventually, if there is appetite, I can release an artifact for this.

## Configuration
Configure your LLM providers in your `application.yml` or `application.properties` under the `llm.providers` prefix. 
Each key under providers represents a named configuration that you can reference in your annotations.
`Example (application.yml):`
```yaml
    llm:
      providers:
        # Configuration for OpenAI
        OpenAI:
          # Your API key for the provider
          api-key: ${OPENAI_API_KEY} # Use environment variables or secrets management
          # The base URL for the provider's API endpoint
          api-url: https://api.openai.com/v1 # Adjust if using Azure OpenAI, etc.
    
        # Configuration for Anthropic Claude
        Claude:
          api-key: ${CLAUDE_API_KEY}
          api-url: https://api.anthropic.com/v1
    
        # Add other providers as needed
        # MyCustomLM:
        #   api-key: ...
        #   api-url: ...
```

## Usage
1. Define an Interface: 
    * Create a Java interface and annotate it with @AgentProxy. 
    * Annotate methods with @Prompt. The api attribute in @AgentProxy must match a key under llm.providers in your configuration.

    ```java
    package com.myapp.llm;
    
    import com.gruszecki.agents.annotations.AgentProxy; // Use your starter's package
    import com.gruszecki.agents.annotations.Prompt; // Use your starter's package
    import com.fasterxml.jackson.databind.JsonNode;
    import java.util.Map;
    
    @AgentProxy(
        // Selects the configuration block from application.yml (must match a key under llm.providers)
        api = "OpenAI",
    
        // Specifies the model identifier to be sent in the API request payload
        model = "gpt-4o",
    
        // Optional: Provides a system prompt sent along with user prompts for this interface.
        systemPrompt = "You are a helpful assistant."
    )
    public interface MyOpenAiClient {
    
        // Basic prompt, returns a String
        @Prompt("Why is the sky blue?")
        String getSkyExplanation();
    
        // Prompt with dynamic argument substitution
        // Parameter name 'topic' MUST match the placeholder {topic}
        @Prompt("Tell me a short poem about {topic}.")
        String getPoem(String topic);
    
        // Prompt requesting structured output (e.g., JSON)
        // The LLM needs to be instructed to return JSON in the prompt itself.
        @Prompt("Return a JSON object containing the population and area for {city}.")
        JsonNode getCityInfoJson(String city);
    
        // Example passing a complex object (Map) for substitution
        // The map will be serialized to JSON within the prompt string.
        @Prompt("Analyze the sentiment of the following user feedback: {feedbackMap}")
        String analyzeSentiment(Map<String, Object> feedbackMap);
    }
    ```

2. Compile with -parameters:
    * For argument substitution by name (e.g., {topic} matching the topic parameter) to work reliably, ensure your project is compiled with the javac -parameters flag enabled. 
    * This is included in the starter's pom.xml build plugin configuration.

3. Autowire and Use:
    * Inject the interface bean into your Spring components and call its methods. Spring automatically provides the implementation powered by the LLM.
    ```java
    package com.myapp.service;
    
    import com.myapp.llm.MyOpenAiClient;
    import com.fasterxml.jackson.databind.JsonNode;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.stereotype.Service;
    import jakarta.annotation.PostConstruct; // Or use constructor injection
    
    @Service
    public class AiService {
    
        private final MyOpenAiClient openAiClient;
    
        @Autowired // Or use constructor injection
        public AiService(MyOpenAiClient openAiClient) {
            this.openAiClient = openAiClient;
        }
    
        public void performAiTasks() {
            String explanation = openAiClient.getSkyExplanation();
            System.out.println("Sky Explanation: " + explanation);
    
            String poem = openAiClient.getPoem("springtime");
            System.out.println("\nPoem about Springtime:\n" + poem);
    
            try {
                JsonNode londonInfo = openAiClient.getCityInfoJson("London");
                if (londonInfo != null) {
                    System.out.println("\nInfo about London (JSON):");
                    System.out.println("Population: " + londonInfo.path("population").asText("N/A"));
                    System.out.println("Area: " + londonInfo.path("area").asText("N/A"));
                }
            } catch (Exception e) {
                System.err.println("\nFailed to get JSON info for London: " + e.getMessage());
            }
        }
    
        @PostConstruct
        public void runDemo() {
             System.out.println("Running AI Service Demo...");
             performAiTasks();
             System.out.println("AI Service Demo Finished.");
        }
    }
    ```

## How it works

The starter scans for interfaces annotated with `@AgentProxy`. For each interface found, it registers a `FactoryBean` (`LlmFactoryBean`) that creates a dynamic proxy implementing the interface.

When you call a method annotated with `@Prompt` on the injected proxy:

1. The InvocationHandler intercepts the call.
2. It retrieves the LLM provider configuration (apiKey, apiUrl) from the LlmProperties bean based on the api value in the @AgentProxy annotation.
3. It resolves the prompt template from the @Prompt annotation, substituting any {argument} placeholders with the actual method arguments.
4. It constructs the appropriate HTTP request body for the target LLM API, including the model, system prompt, and user prompt. 
   * Note: The exact request body structure needs to be implemented correctly in LlmFactoryBean for each supported API type.
5. It uses Spring's WebClient to make an HTTP POST request to the configured apiUrl, adding necessary headers like Authorization (with the apiKey) and Content-Type.
6. It receives the HTTP response.
7. It parses the response body (typically JSON) to extract the relevant LLM-generated content. (Note: The response parsing logic needs to be implemented correctly in LlmFactoryBean for each API type).
8. It converts the extracted content string to the method's declared return type 
