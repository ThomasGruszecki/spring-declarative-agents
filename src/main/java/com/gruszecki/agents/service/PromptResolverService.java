package com.gruszecki.agents.service;

import static java.util.regex.Matcher.quoteReplacement;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PRIVATE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class PromptResolverService {

  @NonNull
  ObjectMapper objectMapper;

  /**
   * Replaces placeholders like {argName} in the prompt template with actual argument values. ... (implementation from
   * previous version) ...
   */
  public String resolvePrompt(String promptTemplate, Parameter[] parameters, Object[] args) {

    // No args = no replacement
    if (ArrayUtils.isEmpty(args)) {
      return promptTemplate;
    }

    // Warn if any parameter names are not present (e.g., not compiled with -parameters)
    Stream.of(parameters)
        .filter(param -> !param.isNamePresent())
        .map(Parameter::getDeclaringExecutable)
        .map(Executable::getName)
        .distinct()
        .forEach(methodName -> log.warn("Parameter names might not be available for method {}. " +
            "Ensure code is compiled with '-parameters' flag for reliable substitution by name.", methodName));

    final Map<String, Object> argMap = IntStream.range(0, parameters.length)
        .boxed()
        .collect(
            toMap(
                i -> parameters[i].getName(),
                i -> args[i]
            )
        );

    final Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
    final Matcher matcher = pattern.matcher(promptTemplate);
    return matcher.replaceAll(matchResult -> {
      final String key = matchResult.group(1);
      final String value = argMap.containsKey(key)
          ? quoteReplacement(serializeObject(argMap.get(key)))
          : matchResult.group(0);
      log.info("Replacing key {} with value {}", key, value);
      return value;
    });
  }

  private String serializeObject(Object value) {
    //Simple case -- try to parse it with object mapper, otherwise use to string
    try {
      return switch (value) {
        case String stringValue -> stringValue;
        case null -> "null";
        default -> objectMapper.writeValueAsString(value);
      };
    } catch (Exception e) {
      log.warn("Could not serialize argument '{}' to dynamically. Using toString().", value, e);
      return toString();
    }
  }
}
