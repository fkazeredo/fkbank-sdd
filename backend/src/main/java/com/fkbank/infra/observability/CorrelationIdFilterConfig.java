package com.fkbank.infra.observability;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Runs {@link CorrelationIdFilter} ahead of every other filter, Spring Security included, so
 * even a request Security rejects still gets a correlation id on its response and in the log
 * lines that describe the rejection.
 */
@Configuration(proxyBeanMethods = false)
public class CorrelationIdFilterConfig {

  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
    FilterRegistrationBean<CorrelationIdFilter> registration =
        new FilterRegistrationBean<>(new CorrelationIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
