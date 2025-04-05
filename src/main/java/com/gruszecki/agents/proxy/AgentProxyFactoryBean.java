package com.gruszecki.agents.proxy;

import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.Objects.isNull;

import com.gruszecki.agents.annotations.AgentProxy;
import com.gruszecki.agents.annotations.Prompt;
import com.gruszecki.agents.domain.AgentProxyArguments;
import com.gruszecki.agents.domain.AgentProxyArguments.AgentProxyArgument;
import com.gruszecki.agents.service.ChatService;
import com.gruszecki.agents.service.ChatServiceLookup;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;

/**
 * FactoryBean responsible for creating the dynamic proxy instances for interfaces annotated with
 *
 * @param <T> The type of the interface being proxied.
 */
@Slf4j
@RequiredArgsConstructor
public class AgentProxyFactoryBean<T> implements FactoryBean<T>,
    InvocationHandler,
    InitializingBean,
    BeanFactoryAware {

  @NonNull
  private final Class<T> interfaceClass;

  @Setter
  private BeanFactory beanFactory;

  /* --- Post Constructor Fields --- */
  private AgentProxy agentProxyAnnotation;

  private ChatService chatService;

  @Override
  public void afterPropertiesSet() {
    this.agentProxyAnnotation = AnnotationUtils.findAnnotation(interfaceClass, AgentProxy.class);
    Assert.notNull(this.agentProxyAnnotation, "Interface must be annotated with @AgentProxy");

    try {
      final ChatServiceLookup chatServiceLookup = beanFactory.getBean(ChatServiceLookup.class);
      chatService = chatServiceLookup.getChatService(agentProxyAnnotation.api());
    } catch (Exception e) {
      log.warn("Failed to retrieve ChatService for {}. Ensure the provider is configured correctly.",
          agentProxyAnnotation.api());
      throw new BeanCreationException("Failed to create ChatService for " + agentProxyAnnotation.api(), e);
    }

    log.debug("Initialized AgentProxyFactoryBean for interface {} with provider config for '{}'",
        interfaceClass.getSimpleName(),
        agentProxyAnnotation.api());
  }


  @Override
  @SuppressWarnings(value = "unchecked")
  public T getObject() {
    if (isNull(this.chatService)) {
      log.warn("AgentProxyFactoryBean for {} accessed before full initialization. Forcing initialization.",
          interfaceClass.getName());
      afterPropertiesSet();
    }

    return (T) newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[]{interfaceClass}, this);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    log.info("Proxy invoked for method {} with args {}", method.getName(), Arrays.toString(args));

    // Pass standard methods like toString/hashCode to the Object class
    if (method.getDeclaringClass() == Object.class) {
      return method.invoke(this, args);
    }

    final Prompt promptAnnotation = AnnotationUtils.findAnnotation(method, Prompt.class);

    // Ensure initialization before invocation (defensive check)
    if (isNull(this.chatService)) {
      throw new IllegalStateException("AgentProxyFactoryBean for " + interfaceClass.getName()
          + " was not properly initialized.");
    }

    if (method.getReturnType().isPrimitive()) {
      log.error("Primitive return types not supported by @AgentProxy");
      throw new UnsupportedOperationException("Primitive return types not supported by @AgentProxy");
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

    final Parameter[] parameters = method.getParameters();
    final List<AgentProxyArgument> arguments = IntStream.range(0, method.getParameterCount())
        .mapToObj(i ->
            AgentProxyArgument.builder()
                .parameter(parameters[i])
                .value(args[i])
                .build()
        )
        .toList();
    final AgentProxyArguments agentProxyArguments = AgentProxyArguments.builder()
        .model(agentProxyAnnotation.model())
        .systemPrompt(agentProxyAnnotation.systemPrompt())
        .userPrompt(promptAnnotation.value())
        .name(method.getName())
        .returnType(method.getReturnType())
        .arguments(arguments)
        .build();

    return chatService.handlePrompt(agentProxyArguments);
  }

  /* --- Standard FactoryBean methods --- */
  @Override
  public Class<?> getObjectType() {
    return interfaceClass;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
