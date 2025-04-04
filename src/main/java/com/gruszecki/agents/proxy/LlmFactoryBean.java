package com.gruszecki.agents.proxy;

import static java.util.Objects.isNull;

import com.gruszecki.agents.annotations.LargeLanguageModelProxy;
import com.gruszecki.agents.annotations.Prompt;
import com.gruszecki.agents.config.LlmProperties;
import com.gruszecki.agents.service.ChatService;
import com.gruszecki.agents.service.ChatServiceLookup;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;

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

  /* --- Constructor Relevant Fields --- */

  @NonNull
  private final Class<T> interfaceClass;

  @NonNull
  private final ApplicationContext applicationContext; // Keep ApplicationContext if needed for other beans

  @Setter // Overrides from BeanFactoryAware
  private BeanFactory beanFactory;

  /* --- Post Constructor Fields --- */

  private LargeLanguageModelProxy llmBeanAnnotation;

  private ChatService chatService;

  /**
   * Invoked by the container after all properties have been set. This is a safe place to retrieve dependencies like
   * LlmProperties.
   */
  @Override
  public void afterPropertiesSet() {
    // Retrieve dependencies safely here
    this.llmBeanAnnotation = AnnotationUtils.findAnnotation(interfaceClass, LargeLanguageModelProxy.class);
    Assert.notNull(this.llmBeanAnnotation, "Interface must be annotated with @LargeLanguageModelProxy");

    // Validate that the provider exists in properties AFTER properties are loaded
    LlmProperties llmProperties = beanFactory.getBean(LlmProperties.class); // Get properties bean
    LlmProperties.ProviderConfig providerConfig;
    try {
      llmProperties.getProvider(llmBeanAnnotation.api());
    } catch (IllegalArgumentException e) {
      // Provide more context in the error message
      throw new IllegalArgumentException(
          String.format("Configuration error for LLM bean %s: %s",
              interfaceClass.getName(),
              e.getMessage()), e);
    }

    final ChatServiceLookup chatServiceLookup = beanFactory.getBean(ChatServiceLookup.class);
    chatService = chatServiceLookup.getChatService(llmBeanAnnotation.api());

    log.debug("Initialized LlmFactoryBean for interface {} with provider config for '{}'",
        interfaceClass.getSimpleName(),
        llmBeanAnnotation.api());
  }


  @Override
  public T getObject() {
    // Proxy creation can happen here or be cached after first creation
    // Ensure initialization has happened (Spring calls afterPropertiesSet before getObject)
    if (isNull(this.chatService)) {
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
    log.info("Proxy invoked for method {} with args {}", method.getName(), Arrays.toString(args));

    // Pass standard methods like toString/hashCode to the Object class
    if (method.getDeclaringClass() == Object.class) {
      return method.invoke(this, args);
    }

    Prompt promptAnnotation = AnnotationUtils.findAnnotation(method, Prompt.class);

    // Ensure initialization before invocation (defensive check)
    if (isNull(this.chatService)) {
      throw new IllegalStateException("LlmFactoryBean for " + interfaceClass.getName()
          + " was not properly initialized.");
    }

    if (method.getReturnType().isPrimitive()) {
      log.error("Primitive return types not supported by @LargeLanguageModelProxy");
      throw new UnsupportedOperationException("Primitive return types not supported by @LargeLanguageModelProxy");
    }

    if (isNull(promptAnnotation)) {
      log.error("Method {} in LLM bean {} is not annotated with @Prompt. Cannot invoke LLM.",
          method.getName(), interfaceClass.getSimpleName());
      throw new UnsupportedOperationException(
          String.format("Method %s in LLM bean %s must have @Prompt annotation!",
              method.getName(),
              interfaceClass.getSimpleName()
          )
      );
    }

    return chatService.handlePrompt(llmBeanAnnotation, promptAnnotation, method, args);
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
}
