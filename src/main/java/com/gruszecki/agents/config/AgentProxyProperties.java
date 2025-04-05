package com.gruszecki.agents.config;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for LLM providers. Maps properties under the 'llm' prefix in application.yml/properties.
 * Example:
 * <pre>
 *   {@code
 *    llm:
 *      OpenAI:
 *        api-key: sk-xxxxxxxx
 *        api-url: https://api.openai.com/v1
 *      Claude:
 *        api-key: sk-ant-xxxxxxxx
 *        api-url: https://api.anthropic.com/v1
 *  }
 * </pre>
 *
 */
@Data
@Validated
public class AgentProxyProperties {

  /**
   * A map where the key is the provider name (e.g., "OpenAI", "Claude") and the value contains the specific
   * configuration for that provider.
   */
  private Map<String, ProviderConfig> providers = Map.of();

  @Data
  public static class ProviderConfig {

    /**
     * The API key for the LLM provider.
     */
    @NotBlank(message = "API key must not be blank")
    private String apiKey;

    /**
     * The base URL for the LLM provider's API.
     */
    @NotBlank(message = "API URL must not be blank")
    private String apiUrl;

    // Add other common provider-specific properties if needed (e.g., default timeout)
    // private Integer timeoutSeconds = 60;
  }

  /**
   * Retrieves the configuration for a specific provider.
   *
   * @param providerName The name of the provider (e.g., "OpenAI").
   * @return The ProviderConfig for the given name.
   * @throws IllegalArgumentException if the provider configuration is not found.
   */
  public ProviderConfig getProvider(String providerName) {
    return Optional.of(providers)
        .map(p -> p.get(providerName))
        .orElseThrow(() -> new IllegalArgumentException("Configuration for LLM provider '"
            + providerName + "' not found under 'llm' properties."));
  }
}
