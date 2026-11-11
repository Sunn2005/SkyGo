package com.skygo.backend.ai.chat;

import com.skygo.backend.ai.service.AiException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-3)
@RestControllerAdvice(assignableTypes = AiChatController.class)
public class AiChatExceptionHandler {
  public record Detail(String field, String code, String message) {}
  public record Error(String code, String message, List<Detail> details, String requestId) {}
  public record Envelope(Error error) {}

  @ExceptionHandler(AiChatException.class)
  public ResponseEntity<Envelope> domain(AiChatException ex) {
    return error(ex.status(), ex.code(), ex.getMessage(), List.of());
  }
  @ExceptionHandler(AiException.class)
  public ResponseEntity<Envelope> retrieval(AiException ex) {
    int status = switch (ex.code()) {
      case EMBEDDING_RATE_LIMITED -> 429;
      case EMBEDDING_TIMEOUT -> 504;
      case EMBEDDING_AUTHENTICATION_FAILED, EMBEDDING_INVALID_REQUEST, COLLECTION_INCOMPATIBLE, INVALID_CONFIGURATION -> 502;
      default -> 503;
    };
    return error(status, ex.code().name(), ex.getMessage(), List.of());
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Envelope> validation(MethodArgumentNotValidException ex) {
    return error(422, "VALIDATION_ERROR", "Vui lòng kiểm tra câu hỏi và lịch sử hội thoại.",
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new Detail(e.getField(), "INVALID_VALUE", e.getDefaultMessage())).toList());
  }
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Envelope> malformed(HttpMessageNotReadableException ex) {
    return error(422, "VALIDATION_ERROR", "JSON hoặc kiểu dữ liệu câu hỏi không hợp lệ.", List.of());
  }
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<Envelope> unexpected(RuntimeException ex) {
    return error(500, "AI_INTERNAL_ERROR", "Chưa xử lý được câu hỏi. Vui lòng thử lại sau.", List.of());
  }
  private static ResponseEntity<Envelope> error(int status, String code, String message, List<Detail> details) {
    var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
    if (status == 429) response.header("Retry-After", "60");
    return response.body(new Envelope(new Error(code, message, details, UUID.randomUUID().toString())));
  }
}
