package com.skygo.backend.auth.security;

import com.skygo.backend.auth.service.AuthService;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  Clock authClock() {
    return Clock.systemUTC();
  }

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, AuthService auth) throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login")
                    .permitAll()
                    .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setContentType("application/json");
                      res.getWriter()
                          .write(
                              "{\"error\":{\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication"
                                  + " required.\",\"details\":[],\"requestId\":\""
                                  + java.util.UUID.randomUUID()
                                  + "\"}}");
                    }))
        .addFilterBefore(
            new BearerAuthenticationFilter(auth), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
