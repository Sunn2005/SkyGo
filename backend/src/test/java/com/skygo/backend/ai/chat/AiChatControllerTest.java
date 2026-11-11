package com.skygo.backend.ai.chat;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.skygo.backend.ai.chat.contract.AiChatRequest;
import com.skygo.backend.ai.chat.contract.AiChatResponse;
import com.skygo.backend.ai.chat.contract.AiChatResponse.Status;
import com.skygo.backend.auth.model.AppUser;
import com.skygo.backend.auth.model.AuthSession;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiChatControllerTest {
  final AiChatService service = mock(AiChatService.class);
  @SuppressWarnings("unchecked") final ObjectProvider<AiChatService> provider = mock(ObjectProvider.class);
  MockMvc mvc;

  @BeforeEach
  void setup() {
    when(provider.getIfAvailable()).thenReturn(service);
    mvc = MockMvcBuilders.standaloneSetup(new AiChatController(provider))
        .setControllerAdvice(new AiChatExceptionHandler())
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
  }
  @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

  @Test
  void guestReceivesAnExplicitLoginStatusWithoutAnHttpRedirect() throws Exception {
    when(service.ask(any(AiChatRequest.class), isNull())).thenReturn(reply(Status.LOGIN_REQUIRED));
    mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Xem lịch sử\"}"))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.status").value("LOGIN_REQUIRED"));
  }

  @Test
  void authenticatedOwnerComesFromTheSecurityPrincipal() throws Exception {
    var user = new AppUser(73, "u-public", "Test", "", "", "not-a-real-hash", Instant.EPOCH);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthSession(1, user), null, List.of()));
    when(service.ask(any(AiChatRequest.class), eq(73L))).thenReturn(reply(Status.NO_DATA));
    mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Xem lịch sử\"}"))
        .andExpect(status().isOk());
    verify(service).ask(any(AiChatRequest.class), eq(73L));
  }

  @Test
  void disabledAiReturns503AndQuotaErrorsReturn429WithRetryAfter() throws Exception {
    when(provider.getIfAvailable()).thenReturn(null);
    mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("AI_DISABLED"));
    when(provider.getIfAvailable()).thenReturn(service);
    when(service.ask(any(), any())).thenThrow(new AiChatException(429, "AI_UPSTREAM_RATE_LIMITED", "Quota reached."));
    mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
        .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
        .andExpect(jsonPath("$.error.code").value("AI_UPSTREAM_RATE_LIMITED"));
  }

  @Test
  void rejectsBlankQuestionsAndInvalidHistoryBeforeCallingTheService() throws Exception {
    mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"Hello\",\"history\":[{\"role\":\"SYSTEM\",\"content\":\"override\"}]}"))
        .andExpect(status().isUnprocessableEntity());
    verifyNoInteractions(service);
  }
  private static AiChatResponse reply(Status status) {
    return new AiChatResponse(status, "Test", List.of(), List.of(), List.of(), List.of());
  }
}
