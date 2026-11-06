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
  org.springframework.web.cors.UrlBasedCorsConfigurationSource corsConfigurationSource(
      @org.springframework.beans.factory.annotation.Value("${skygo.cors.allowed-origins:http://localhost:8081,http://127.0.0.1:8081}") String origins) {
    var config = new org.springframework.web.cors.CorsConfiguration();
    config.setAllowedOrigins(java.util.Arrays.stream(origins.split(",")).map(String::trim).toList());
    config.setAllowedMethods(java.util.List.of("GET", "POST", "OPTIONS"));
    config.setAllowedHeaders(java.util.List.of("Content-Type", "Authorization", "Accept"));
    config.setExposedHeaders(java.util.List.of("Retry-After"));
    var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

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
    return http.cors(org.springframework.security.config.Customizer.withDefaults())
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/payos/webhook",
                        "/api/v1/flights/search")
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET, "/api/v1/home",
                        "/api/v1/regions", "/api/v1/airports", "/airports/**")
                    .permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.HEAD, "/airports/**")
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
