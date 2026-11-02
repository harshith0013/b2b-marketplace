package com.b2bmarket.authentication.internal;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class CurrentUserWebConfig implements WebMvcConfigurer {

  private final CurrentUserArgumentResolver currentUserResolver;

  CurrentUserWebConfig(CurrentUserArgumentResolver currentUserResolver) {
    this.currentUserResolver = currentUserResolver;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentUserResolver);
  }
}
