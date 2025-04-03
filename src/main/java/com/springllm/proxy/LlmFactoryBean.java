package com.springllm.proxy;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springllm.annotations.LargeLanguageModelProxy;
import com.springllm.annotations.Prompt;
import com.springllm.config.LlmProperties;
import com.springllm.config.LlmProperties.ProviderConfig;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import reactor.core.publisher.Mono;

/**
 * FactoryBean responsible for creating the dynamic proxy instances for interfaces annotated with
 *
 * @param <T> The type of the interface being proxied.
 * @LargeLanguageModelProxy. Implements InitializingBean to safely access dependencies after context is set.
 */
// @RequiredArgsConstructor // Remove if not using Lombok or if adding non-final fields
@Slf4j
@RequiredArgsConstructor
public class LlmFactoryBean<T> implements FactoryBean<T>,
    InvocationHandler,
    InitializingBean,
    BeanFactoryAware { // Implement InitializingBean and BeanFactoryAware

  @NonNull
  private final Class<T> interfaceClass;

  @NonNull
  private final ApplicationContext applicationContext; // Keep ApplicationContext if needed for other beans

  @Setter // Overrides from BeanFactoryAware
  private BeanFactory beanFactory;

  // Dependencies retrieved after properties set
  private LlmProperties llmProperties;
  private ProviderConfig providerConfig;
  private LargeLanguageModelProxy llmBeanAnnotation;
  private WebClient webClient;
  private ObjectMapper objectMapper;

  /**
   * Invoked by the container after all properties have been set. This is a safe place to retrieve dependencies like
   * LlmProperties.
   */
  @Override
  public void afterPropertiesSet() {
    // Retrieve dependencies safely here
    this.llmProperties = beanFactory.getBean(LlmProperties.class); // Get properties bean
    this.llmBeanAnnotation = AnnotationUtils.findAnnotation(interfaceClass, LargeLanguageModelProxy.class);
    Assert.notNull(this.llmBeanAnnotation, "Interface must be annotated with @LargeLanguageModelProxy");

    // Validate that the provider exists in properties AFTER properties are loaded
    try {
      this.providerConfig = llmProperties.getProvider(llmBeanAnnotation.api());
    } catch (IllegalArgumentException e) {
      // Provide more context in the error message
      throw new IllegalArgumentException(
          String.format("Configuration error for LLM bean '%s' (%s): %s",
              getBeanName(), // Get the bean name for better context
              interfaceClass.getName(),
              e.getMessage()), e);
    }

    log.debug("Initialized LlmFactoryBean for interface {} with provider config for '{}'",
        interfaceClass.getSimpleName(),
        llmBeanAnnotation.api());
  }


  @Override
  public T getObject() {
    // Proxy creation can happen here or be cached after first creation
    // Ensure initialization has happened (Spring calls afterPropertiesSet before getObject)
    if (isNull(this.providerConfig)) {
      // This should ideally not happen if afterPropertiesSet was called correctly
      log.warn("LlmFactoryBean for {} accessed before full initialization. Forcing initialization.",
          interfaceClass.getName());
      afterPropertiesSet();
    }

    return (T) Proxy.newProxyInstance(
        interfaceClass.getClassLoader(),
        new Class<?>[]{interfaceClass},
        this // The InvocationHandler
    );
  }

  // --- invoke() method and helpers remain largely the same ---
  // Ensure getWebClient() and getObjectMapper() use the applicationContext
  // or beanFactory passed in/set earlier.

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // Ensure initialization before invocation (defensive check)
    log.info("Proxy invoked for method {} with args {}", method.getName(), Arrays.toString(args));
    if (isNull(this.providerConfig)) {
      throw new IllegalStateException("LlmFactoryBean for " + interfaceClass.getName()
          + " was not properly initialized.");
    }

    if (method.getReturnType().isPrimitive()) {
      log.error("Primitive return type detected! Failing");
      throw new UnsupportedOperationException("Primitive types not supported by @LargeLanguageModelProxy");
    }

    // Pass standard methods like toString/hashCode to the Object class
    if (method.getDeclaringClass() == Object.class) {
      return method.invoke(this, args);
    }

    Prompt promptAnnotation = AnnotationUtils.findAnnotation(method, Prompt.class);
    if (promptAnnotation == null) {
      log.warn("Method {} in LLM bean {} is not annotated with @Prompt. Cannot invoke LLM.",
          method.getName(), interfaceClass.getSimpleName());
      Map<Class<?>, Object> returnType = Map.of(
          byte.class, 0,
          short.class, 0,
          int.class, 0,
          long.class, 0L,
          float.class, 0.0f,
          double.class, 0.0,
          char.class, '0',
          boolean.class, false
      );
      return returnType.getOrDefault(method.getReturnType(), null);
    }

    String resolvedPrompt = resolvePrompt(promptAnnotation.value(), method.getParameters(), args);
    log.debug("Resolved prompt for {}: {}", method.getName(), resolvedPrompt);

    String requestBody = buildLlmRequestBody(resolvedPrompt);
    log.trace("LLM Request Body: {}", requestBody);

    WebClient client = getWebClient();
    String apiUrl = providerConfig.getApiUrl();
    String apiKey = providerConfig.getApiKey();

    // Simplified API call example - adapt path, headers, body structure
    Mono<String> responseMono = client.post()
        .uri(apiUrl + "/completions") // ADJUST API PATH AS NEEDED
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        // Add provider-specific headers if required
        .bodyValue(requestBody)
        .retrieve()
        .bodyToMono(String.class)
        .doOnError(error -> log.error("LLM API call failed for {}: {}",
            method.getName(),
            error.getMessage(),
            error)) // Log stack trace on error
        .onErrorMap(e -> new RuntimeException(
            "LLM API call failed for method " + method.getName() + ": " + e.getMessage(), e));

    String rawResponse = responseMono.block(); // Consider async handling if needed
    log.trace("LLM Raw Response: {}", rawResponse);

    if (rawResponse == null) {
      throw new RuntimeException("Received null response from LLM API for method " + method.getName());
    }

    return processLlmResponse(rawResponse, method.getReturnType());
  }

  // --- Helper Methods (getWebClient, getObjectMapper, resolvePrompt, buildLlmRequestBody, processLlmResponse) ---
  // These methods should now reliably use the initialized 'providerConfig', 'llmBeanAnnotation', etc.

  private WebClient getWebClient() {
    this.webClient = Optional.ofNullable(this.webClient)
        .orElseGet(() -> {
          try {
            return applicationContext.getBean(Builder.class).build();
          } catch (BeansException e) {
            log.warn("No WebClient.Builder bean found, " +
                    "creating default WebClient instance for {}. " +
                    "Consider providing a configured bean.",
                interfaceClass.getSimpleName());
            return WebClient.create();
          }
        });
    return this.webClient;
  }

  private ObjectMapper getObjectMapper() {
    this.objectMapper = Optional.ofNullable(this.objectMapper)
        .orElseGet(() -> {
          try {
            return applicationContext.getBean(ObjectMapper.class);
          } catch (BeansException e) {
            log.warn("No ObjectMapper bean found, " +
                    "creating default ObjectMapper instance for {}. " +
                    "Consider providing a configured bean.",
                interfaceClass.getSimpleName());
            return new ObjectMapper();
          }
        });
    return this.objectMapper;
  }

  // resolvePrompt, buildLlmRequestBody, processLlmResponse methods remain the same as before
  // They can now safely use this.providerConfig and this.llmBeanAnnotation
  // ... (Include the full implementation of these methods from the previous version) ...

  /**
   * Replaces placeholders like {argName} in the prompt template with actual argument values. ... (implementation from
   * previous version) ...
   */
  private String resolvePrompt(String promptTemplate, Parameter[] parameters, Object[] args) {
    // (Implementation from previous version)
    if (args == null || args.length == 0) {
      return promptTemplate;
    }
    Map<String, Object> argMap = new HashMap<>();
    for (int i = 0; i < parameters.length; i++) {
      String paramName = parameters[i].getName();
      if (paramName.equals("arg" + i) && !parameters[i].isNamePresent()) {
        log.warn("Parameter names not available for method {}. " +
            "Ensure code is compiled with '-parameters' flag for reliable substitution by name. " +
            "Using arg{} fallback.", parameters[i].getDeclaringExecutable().getName(), i);
      }
      argMap.put(paramName, args[i]);
    }
    Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
    Matcher matcher = pattern.matcher(promptTemplate);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String key = matcher.group(1);
      Object value = argMap.get(key);
      if (value == null) {
        log.warn("No argument found for placeholder '{}' in prompt template for method {}",
            key,
            parameters[0].getDeclaringExecutable().getName());
        matcher.appendReplacement(sb, matcher.group(0));
      } else {
        String stringValue;
        if (value instanceof String) {
          stringValue = (String) value;
        } else if (value instanceof Map || value instanceof Iterable || (value != null && !value.getClass()
            .isPrimitive()
            && !value.getClass().getName().startsWith("java.lang"))) {
          try {
            stringValue = getObjectMapper().writeValueAsString(value);
          } catch (JsonProcessingException e) {
            log.warn("Could not serialize argument '{}' to JSON for prompt substitution. Using toString().", key, e);
            stringValue = value.toString();
          }
        } else {
          stringValue = value.toString();
        }
        matcher.appendReplacement(sb, Matcher.quoteReplacement(stringValue));
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * Builds the request body string for the LLM API call. NOTE: Adapt based on the actual API documentation. ...
   * (implementation from previous version) ...
   */
  private String buildLlmRequestBody(String userPrompt) {
    // (Implementation from previous version - Example for OpenAI Chat)
    Map<String, Object> message = new HashMap<>();
    message.put("role", "user");
    message.put("content", userPrompt);
    Map<String, Object> requestBodyMap = new HashMap<>();
    requestBodyMap.put("model", llmBeanAnnotation.model());
    Object[] messages;
    if (StringUtils.hasText(llmBeanAnnotation.systemPrompt())) {
      Map<String, Object> systemMessage = new HashMap<>();
      systemMessage.put("role", "system");
      systemMessage.put("content", llmBeanAnnotation.systemPrompt());
      messages = new Object[]{systemMessage, message};
    } else {
      messages = new Object[]{message};
    }
    requestBodyMap.put("messages", messages);
    // Add other parameters like temperature, max_tokens etc. if needed
    try {
      return getObjectMapper().writeValueAsString(requestBodyMap);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize LLM request body", e);
      throw new RuntimeException("Failed to build LLM request body", e);
    }
  }

  /**
   * Processes the raw JSON response from the LLM and converts it to the target type. NOTE: Adapt based on the LLM API
   * response structure. ... (implementation from previous version) ...
   */
  private Object processLlmResponse(String rawJsonResponse, Class<?> returnType) throws JsonProcessingException {
    // (Implementation from previous version - Example for OpenAI Chat)
    ObjectMapper mapper = getObjectMapper();
    JsonNode rootNode = mapper.readTree(rawJsonResponse);
    JsonNode contentNode = rootNode.path("choices").path(0).path("message").path("content");
    Object result;
    if (contentNode.isMissingNode()) {
      log.error("Could not find 'choices[0].message.content' in LLM response: {}", rawJsonResponse);
      JsonNode errorNode = rootNode.path("error");
      if (!errorNode.isMissingNode()) {
        throw new RuntimeException("LLM API returned an error: " + errorNode.toString());
      }
      throw new RuntimeException("Unexpected LLM response structure. Could not extract content.");
    }
    String llmTextOutput = contentNode.asText();
    if (returnType.isAssignableFrom(String.class)) {
      return llmTextOutput;
    } else if (returnType.isAssignableFrom(JsonNode.class)) {
      try {
        return mapper.readTree(llmTextOutput);
      } catch (JsonProcessingException e) {
        log.error("LLM output was expected to be JSON, but failed to parse: {}", llmTextOutput, e);
        throw new RuntimeException("Failed to parse LLM output as JSON", e);
      }
    } else {
      try {
        return mapper.readValue(llmTextOutput, returnType);
      } catch (JsonProcessingException e) {
        log.warn("Could not directly convert LLM output string to type {}. Output: {}",
            returnType.getName(),
            llmTextOutput,
            e);
        throw new RuntimeException("Cannot convert LLM output to target type " + returnType.getName(), e);
      }
    }
  }

  // --- Standard FactoryBean methods ---

  @Override
  public Class<?> getObjectType() {
    return interfaceClass;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  // Helper to get bean name for logging/error messages
  private String getBeanName() {
    // Try to determine the bean name Spring assigned to this FactoryBean
    // This requires BeanFactoryAware to be implemented and beanFactory set.
    if (beanFactory != null && beanFactory instanceof ConfigurableListableBeanFactory) {
      ConfigurableListableBeanFactory clbf = (ConfigurableListableBeanFactory) beanFactory;
      // Find the bean definition associated with this FactoryBean instance (can be complex)
      // A simpler approach is to rely on the name generated in AutoConfiguration
      // or potentially pass it during construction if needed for errors.
    }
    // Fallback name
    return interfaceClass.getSimpleName() + "FactoryBean";
  }
}
