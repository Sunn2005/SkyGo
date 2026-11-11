package com.skygo.backend.ai.chat;

import com.skygo.backend.ai.chat.contract.AiChatRequest;
import com.skygo.backend.ai.chat.contract.AiChatResponse;
import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiChatController {
  private final ObjectProvider<AiChatService> service;
  public AiChatController(ObjectProvider<AiChatService> service) { this.service = service; }

  @PostMapping("/api/v1/ai/chat")
  public ResponseEntity<DataResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request,
      @AuthenticationPrincipal AuthSession session) {
    var chat = service.getIfAvailable();
    if (chat == null) throw new AiChatException(503, "AI_DISABLED", "Chức năng AI chưa được bật trên máy chủ.");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(chat.ask(request, session == null ? null : session.user().id())));
  }
}
