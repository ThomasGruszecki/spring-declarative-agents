package com.gruszecki.agents.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a method within an interface marked with @LargeLanguageModelProxy. Specifies the prompt template to be sent
 * to the LLM. Method arguments can be injected into the template using curly braces {argName}. The argument name must
 * match the parameter name in the method signature.
 */
@Target(ElementType.METHOD) // Applicable only to methods
@Retention(RetentionPolicy.RUNTIME) // Needed for introspection at runtime
public @interface Prompt {

  /**
   * The prompt template string. Use {parameterName} for dynamic substitution. Example: "Translate the following text to
   * {targetLanguage}: {text}"
   *
   * @return The prompt template.
   */
  String value();
}