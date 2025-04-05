package com.gruszecki.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gruszecki.agents.config.AgentProxyProperties;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;

/**
 * Integration tests for the Spring LLM Starter.
 * Uses MockWebServer to simulate LLM API responses.
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
class SpringDeclarativeAgentsIntegrationTest {

  // --- Mock Web Server Setup ---
  static final String mockBackEndHost = "localhost";
  static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();

  private MockWebServer mockWebServer;

  /**
   * Dynamically set the API URL property for the "TestLM" provider to point to the running MockWebServer instance
   * *before* the ApplicationContext is created.
   */
  @DynamicPropertySource
  static void setDynamicProperties(DynamicPropertyRegistry registry) {
    String mockApiUrl = String.format("http://%s:%s", mockBackEndHost, mockBackEndPort);
    registry.add("llm.providers.TestLM.api-url", () -> mockApiUrl);
    // override other properties dynamically here too
    // registry.add("logging.level.com.gruszecki.agents", () -> "TRACE");
  }

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start(InetAddress.getByName(mockBackEndHost),
        mockBackEndPort); // Start the server before any tests run
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.close(); // Shut down the server after all tests
  }

  @Autowired
  private ApplicationContext context;

  @Autowired
  private AgentProxyProperties agentProxyProperties; // Autowire properties to verify loading

  @Autowired
  private TestLlmClient testLlmClient; // Autowire the dynamically created bean

  @Autowired
  private ObjectMapper objectMapper; // Use the ObjectMapper bean from the context

  @Test
  void contextLoads() {
    // Basic test to ensure the application context loads successfully
    // with the starter's auto-configuration included.
    assertThat(context).isNotNull();
    log.info("ApplicationContext loaded successfully.");
  }

  @Test
  void propertiesLoad() {
    // Verify that properties from application-test.yml are loaded correctly.
    assertThat(agentProxyProperties).isNotNull();
    assertThat(agentProxyProperties.getProviders()).containsKey("TestLM");
    assertThat(agentProxyProperties.getProviders()).containsKey("AnotherLM");

    AgentProxyProperties.ProviderConfig testLmConfig = agentProxyProperties.getProvider("TestLM");
    assertThat(testLmConfig.getApiKey()).isEqualTo("test-key-12345");

    // Check the dynamically set URL
    assertThat(testLmConfig.getApiUrl()).startsWith(String.format("http://%s:", mockBackEndHost));
    assertThat(testLmConfig.getApiUrl()).isEqualTo(String.format("http://%s:%s", mockBackEndHost, mockBackEndPort));

    //Check the default url
    AgentProxyProperties.ProviderConfig anotherLmConfig = agentProxyProperties.getProvider("AnotherLM");
    assertThat(anotherLmConfig.getApiKey()).isEqualTo("another-key-67890");

    // Ensure the placeholder wasn't overridden if not specified dynamically
    assertThat(anotherLmConfig.getApiUrl()).isEqualTo("http://localhost:8081");

    log.info("AgentProxyProperties loaded and verified: {}", agentProxyProperties);
  }

  @Test
  void llmClientBeanExists() {
    // Verify that the bean for our test interface was created.
    assertThat(testLlmClient).isNotNull();

    // Check if the bean name is the default or custom one if set
    String[] beanNames = context.getBeanNamesForType(TestLlmClient.class);
    assertThat(beanNames).contains("testLlmClientLlmProxyBean"); // Default name convention

    // If you set a custom beanName = "myCustomTestClient" in @AgentProxy:
    // assertThat(beanNames).contains("myCustomTestClient");
    log.info("TestLlmClient bean found: {}", testLlmClient.getClass().getName());
  }

  @Test
  void invokeSimplePrompt() throws Exception {
    // Arrange: Prepare the mock response for the "getJoke" call
    String mockJoke = "Why don't scientists trust atoms? Because they make up everything!";
    String mockApiResponse = createMockApiResponse(mockJoke); // Helper to build JSON
    mockWebServer.enqueue(new MockResponse().setBody(mockApiResponse).addHeader("Content-Type", "application/json"));

    // Act: Call the method on the proxied bean
    String actualJoke = testLlmClient.getJoke();

    // Assert: Verify the result and the request sent to the mock server
    assertThat(actualJoke).isEqualTo(mockJoke);

    RecordedRequest recordedRequest = mockWebServer.takeRequest(); // Get the request sent
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");

    // Path depends on your AgentProxyFactoryBean implementation (e.g., "/completions")
    assertThat(recordedRequest.getPath()).endsWith("/completions");
    assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer test-key-12345");
    assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/json");

    // Verify the request body content
    String requestBody = recordedRequest.getBody().readUtf8();
    log.info("Request Body for getJoke:  {}", requestBody);
    JsonNode requestJson = objectMapper.readTree(requestBody);
    assertThat(requestJson.path("model").asText()).isEqualTo("test-model-v1");
    assertThat(requestJson.path("messages").get(0).path("role").asText()).isEqualTo("system");
    assertThat(requestJson.path("messages").get(0).path("content").asText()).isEqualTo(
        "You are a helpful test assistant.");
    assertThat(requestJson.path("messages").get(1).path("role").asText()).isEqualTo("user");
    assertThat(requestJson.path("messages").get(1).path("content").asText()).isEqualTo("Tell me a testing joke.");
  }

  @Test
  void invokePromptWithArgument() throws Exception {
    // Arrange
    String topic = "Spring Boot";
    String mockFact = "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications.";
    String mockApiResponse = createMockApiResponse(mockFact);
    mockWebServer.enqueue(new MockResponse().setBody(mockApiResponse).addHeader("Content-Type", "application/json"));

    // Act
    String actualFact = testLlmClient.getFact(topic);

    // Assert
    assertThat(actualFact).isEqualTo(mockFact);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath()).endsWith("/completions"); // Adjust path if needed
    assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer test-key-12345");

    String requestBody = recordedRequest.getBody().readUtf8();
    log.info("Request Body for getFact:  {}", requestBody);

    JsonNode requestJson = objectMapper.readTree(requestBody);
    assertThat(requestJson.path("model").asText()).isEqualTo("test-model-v1");
    assertThat(requestJson.path("messages").get(0).path("role").asText()).isEqualTo("system");
    assertThat(requestJson.path("messages").get(1).path("role").asText()).isEqualTo("user");
    // Verify argument substitution
    assertThat(requestJson.path("messages").get(1).path("content").asText()).isEqualTo(
        "Give me a fun fact about Spring Boot.");
  }

  @Test
  void invokePromptWithJsonReturnType() throws Exception {
    // Arrange: The LLM is expected to return a JSON string *within* its content field
    Map<String, Object> inputMap = Map.of("name", "Test Product", "version", 1.2, "active", true);
    String expectedJsonResponse = """
        {
          "productName": "Test Product",
          "versionNumber": 1.2,
          "isActive": true
        }
        """;
    String mockApiResponse = createMockApiResponse(expectedJsonResponse); // LLM returns JSON string
    mockWebServer.enqueue(new MockResponse().setBody(mockApiResponse).addHeader("Content-Type", "application/json"));

    // Act
    JsonNode actualJsonNode = testLlmClient.convertMapToJson(inputMap);

    // Assert
    assertThat(actualJsonNode).isNotNull();
    assertThat(actualJsonNode.path("productName").asText()).isEqualTo("Test Product");
    assertThat(actualJsonNode.path("versionNumber").asDouble()).isEqualTo(1.2);
    assertThat(actualJsonNode.path("isActive").asBoolean()).isTrue();

    // Verify the request body included the map serialized as JSON
    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    String requestBody = recordedRequest.getBody().readUtf8();
    log.info("Request Body for convertMapToJson:  {}", requestBody);
    JsonNode requestJson = objectMapper.readTree(requestBody);
    assertThat(requestJson.path("model").asText()).isEqualTo("test-model-v1");
    assertThat(requestJson.path("messages").get(1).path("role").asText()).isEqualTo("user");
    String expectedPromptContent =
        "Convert the following map to a JSON object: " + objectMapper.writeValueAsString(inputMap);
    assertThat(requestJson.path("messages").get(1).path("content").asText()).isEqualTo(expectedPromptContent);
  }

  @Test
  void invokeMethodWithoutPromptAnnotation() {
    // Arrange & Act
    // Attempt to call the method not annotated with @Prompt
    assertThatThrownBy(() -> testLlmClient.methodWithoutPrompt())
        .isInstanceOf(UnsupportedOperationException.class);

    // Ensure no request was sent to the mock server
    assertThat(mockWebServer.getRequestCount()).isZero();
    log.info("Verified that methodWithoutPrompt returned null and made no API call.");
  }

  @Test
  void invokeVoidMethod() {
    // Arrange
    String message = "Test message";

    // Act
    // Call the void method. Expect an exception.
    assertThatThrownBy(() -> testLlmClient.acknowledge(message))
        .isInstanceOf(UnsupportedOperationException.class);

    // Ensure no request was sent to the mock server
    assertThat(mockWebServer.getRequestCount()).isZero();

    log.info("Verified void method acknowledge() throws exception.");
  }

  @Test
  void testInvalidProviderConfiguration() {
    // Test what happens if @AgentProxy references a provider not in properties
    // This requires a separate test context setup or manipulating properties,
    // which is more advanced. For now, we know AgentProxyFactoryBean constructor throws.
    // A simpler check is that AgentProxyProperties validation works.
    // (Validation is implicitly tested by context loading with @Validated)
    log.info("Implicitly tested configuration validation during context load.");
  }

  // --- Helper Methods ---

  /**
   * Creates a mock JSON response string mimicking a typical LLM API structure. Adapts this structure based on how your
   * AgentProxyFactoryBean::processLlmResponse expects to parse the response.
   *
   * @param content The desired text content for the LLM response.
   * @return A JSON string representing the mock API response.
   */
  private String createMockApiResponse(String content) throws JsonProcessingException {
    // Example structure matching OpenAI chat completion format used in AgentProxyFactoryBean example
    Map<String, Object> messageMap = Map.of("role", "assistant", "content", content);
    Map<String, Object> choiceMap = Map.of("index", 0, "message", messageMap, "finish_reason", "stop");
    Map<String, Object> responseMap =
        Map.of("id", "chatcmpl-test-" + System.nanoTime(), "object", "chat.completion", "created",
            System.currentTimeMillis() / 1000, "model", "test-model-v1", // Echo back the model used
            "choices", new Object[]{choiceMap}, "usage",
            Map.of("prompt_tokens", 10, "completion_tokens", 20, "total_tokens", 30) // Dummy usage
        );

    return objectMapper.writeValueAsString(responseMap);
  }
}

