package com.b2bmarket.authentication.internal;

import com.b2bmarket.users.CurrentUser;
import com.b2bmarket.users.UserService;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers declare a {@link CurrentUser} parameter. The account is created on the person's
 * first request after sign-in.
 */
@Component
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  private final UserService users;

  CurrentUserArgumentResolver(UserService users) {
    this.users = users;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return CurrentUser.class.equals(parameter.getParameterType());
  }

  @Override
  public CurrentUser resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token)) {
      // Security config only routes authenticated requests here; reaching this is a wiring bug.
      throw new IllegalStateException("CurrentUser requested on an unauthenticated endpoint");
    }
    TokenIdentity identity = TokenIdentity.from(token.getToken());
    return users.provision(identity.subject(), identity.email());
  }
}
