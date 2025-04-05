package com.gruszecki.agents.autoconfig;


import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import com.gruszecki.agents.annotations.AgentProxy;
import com.gruszecki.agents.annotations.Prompt;
import com.gruszecki.agents.config.AgentProxyBeansConfig;
import com.gruszecki.agents.config.AgentProxyProperties;
import com.gruszecki.agents.proxy.AgentProxyFactoryBean;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration class for the Spring LLM Starter. Scans for @AgentProxy interfaces and registers FactoryBeans.
 */
@AutoConfiguration
@ConditionalOnClass({AgentProxyProperties.class, AgentProxyFactoryBean.class})
@Import(AgentProxyBeansConfig.class)
@Slf4j
public class SpringDeclarativeAgentsAutoConfig implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

  private ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
    log.info("Scanning for @AgentProxy interfaces...");
    final ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return super.isCandidateComponent(beanDefinition) || beanDefinition.getMetadata().isAbstract();
          }
        };
    scanner.addIncludeFilter(new AnnotationTypeFilter(AgentProxy.class));
    final Integer count = Stream.of(determineBasePackages())
        .map(basePackage -> Objects.equals(basePackage, "*") ? "" : basePackage)
        .flatMap(basePackage -> scanner.findCandidateComponents(basePackage).stream())
        .mapToInt(beanDefinition -> registerFromBeanDefinition(registry, beanDefinition))
        .sum();

    log.info("Finished scanning. Registered {} LLM FactoryBean definitions.", count);
  }

  private Integer registerFromBeanDefinition(final BeanDefinitionRegistry registry,
      final BeanDefinition beanDefinition) {
    try {
      if (isNull(beanDefinition)) {
        log.info("Null bean definition detected");
        return 0;
      }

      final Class<?> interfaceClass = ClassUtils.forName(
          requireNonNull(beanDefinition.getBeanClassName()),
          applicationContext.getClassLoader()
      );

      if (!interfaceClass.isInterface()) {
        log.warn("Skipping non-interface class annotated with @AgentProxy: {}", interfaceClass.getName());
        return 0;
      }

      AgentProxy annotation = interfaceClass.getAnnotation(AgentProxy.class);
      if (isNull(annotation)) {
        return 0;
      }

      Stream.of(interfaceClass.getMethods())
          .forEach(method -> {
            Prompt prompt = method.getAnnotation(Prompt.class);
            if (isNull(prompt)) {
              log.warn("{} has method '{}' without @Prompt!", interfaceClass.getName(), method.getName());
            }
          });

      final GenericBeanDefinition factoryBeanDefinition = new GenericBeanDefinition();
      final String beanName = Optional.ofNullable(annotation.beanName())
          .filter(StringUtils::hasText)
          .orElseGet(() -> StringUtils.uncapitalize(interfaceClass.getSimpleName()) + "LlmProxyBean");

      factoryBeanDefinition.setBeanClass(AgentProxyFactoryBean.class);
      factoryBeanDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, interfaceClass);
      factoryBeanDefinition.setAttribute("factoryBeanObjectType", interfaceClass);

      log.info("Registering LLM FactoryBean definition '{}' for interface {}", beanName, interfaceClass.getName());
      if (registry.containsBeanDefinition(beanName)) {
        log.warn("Bean definition for '{}' already exists. Overwriting.", beanName);
      }

      registry.registerBeanDefinition(beanName, factoryBeanDefinition);
    } catch (Exception e) {
      log.error("Could not load class for LLM bean definition: {}", beanDefinition.getBeanClassName(), e);
      throw new BeanCreationException("Could not load class for LLM bean definition: "
          + beanDefinition.getBeanClassName(), e);
    }

    return 1;
  }

  /**
   * Determines the base packages to scan for @AgentProxy interfaces. Uses 'spring.llm.scan-packages' property, falls
   * back to the main application package, or scans all packages as a last resort.
   *
   * @return Array of base packages to scan.
   */
  private String[] determineBasePackages() {
    final String[] basePackages = Optional.ofNullable(
            applicationContext.getEnvironment().getProperty("spring.llm.scan-packages", String[].class)
        )
        .filter(ArrayUtils::isNotEmpty)
        .filter(bp -> Stream.of(bp).anyMatch(StringUtils::hasText))
        .or(() -> {
          log.warn(
              "Property 'spring.llm.scan-packages' not set. Attempting to derive base package from @SpringBootApplication.");
          try {
            final String mainClassName = applicationContext.getBeansWithAnnotation(SpringBootApplication.class)
                .values().stream()
                .findFirst()
                .map(Object::getClass)
                .map(Class::getName)
                .orElseThrow(() -> new RuntimeException("No base packages for Spring Application!"));
            final String nonProxyClassName =
                ClassUtils.getUserClass(ClassUtils.forName(mainClassName, applicationContext.getClassLoader()))
                    .getName();
            final String packageName = ClassUtils.getPackageName(nonProxyClassName);
            log.info("Using base package derived from @SpringBootApplication: {}", packageName);
            return Optional.of(new String[]{packageName});
          } catch (Exception e) {
            log.warn("Could not determine base package from @SpringBootApplication. ", e);
            return Optional.empty();
          }
        })
        .orElseGet(() -> {
          log.info("Falling back to scanning all packages. This might be slow.");
          return new String[]{"*"};
        });

    log.info("Using these base packages for scanning: {}", (Object) basePackages); // Cast to Object for varargs log

    return basePackages;
  }


  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    // No action needed here for this implementation
  }
}
