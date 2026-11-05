package com.skygo.backend.auth.security;

import com.skygo.backend.auth.service.AuthService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerAuthenticationFilter extends OncePerRequestFilter {
  private final AuthService auth;

  public BearerAuthenticationFilter(AuthService auth) {
    this.auth = auth;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7))
      auth.authenticate(header.substring(7))
          .ifPresent(
              session -> {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(session, null, List.of()));
                SecurityContextHolder.setContext(context);
              });
    chain.doFilter(request, response);
  }
}
