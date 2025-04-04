package com.gruszecki.agents.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

/**
 * Marks an interface as a target for dynamic LLM proxy implementation. Spring will create a bean implementing this
 * interface, delegating method calls annotated with @Prompt to the configured LLM.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface LargeLanguageModelProxy {

  /**
   * The name of the LLM provider to use (e.g., "OpenAI", "Claude"). This must match a key under the 'llm.providers'
   * configuration.
   *
   * @return The LLM provider name.
   */
  String api();

  /**
   * The specific model to use for this bean (e.g., "gpt-4o", "claude-3-opus-20240229").
   *
   * @return The model identifier.
   */
  String model();

  /**
   * An optional system prompt to provide context or instructions to the LLM for all prompts handled by this bean.
   *
   * @return The system prompt string. Defaults to an empty string.
   */
  String systemPrompt() default "";

  /**
   * Optional bean name for the generated proxy. If not specified, a default name will be generated.
   *
   * @return The desired bean name.
   */
  String beanName() default "";
}

