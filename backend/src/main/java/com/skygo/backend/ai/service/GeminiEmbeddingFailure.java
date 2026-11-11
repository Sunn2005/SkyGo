package com.skygo.backend.ai.service;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;

/** Retain actionable categories, never provider messages, request bodies, URLs, keys or causes. */
public final class GeminiEmbeddingFailure {
  private GeminiEmbeddingFailure() {}

  public static AiException sanitized(Throwable failure) {
    boolean timeout = false;
    boolean network = false;
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = failure;
    for (int depth = 0; current != null && depth < 32 && seen.add(current); depth++, current = current.getCause()) {
      if (current instanceof ApiException api) return http(api.code());
      if (isTimeout(current)) timeout = true;
      if (current instanceof IOException || current instanceof GenAiIOException) network = true;
      if (current instanceof InterruptedException) Thread.currentThread().interrupt();
    }
    if (timeout) return new AiException(AiException.Code.EMBEDDING_TIMEOUT,
        "[GEMINI_TIMEOUT] Hết thời gian chờ Gemini tạo embedding. Kiểm tra mạng hoặc tăng SKYGO_AI_TIMEOUT (tối đa 2m), rồi chạy lại đồng bộ.");
    if (network) return new AiException(AiException.Code.EMBEDDING_NETWORK_ERROR,
        "[GEMINI_NETWORK_ERROR] Không kết nối được Gemini khi tạo embedding. Kiểm tra Internet, DNS, proxy hoặc chứng chỉ TLS, rồi chạy lại đồng bộ.");
    return new AiException(AiException.Code.EMBEDDING_UNAVAILABLE,
        "[GEMINI_UNKNOWN_ERROR] Không tạo được embedding; không nhận được mã HTTP hoặc loại lỗi kết nối xác định.");
  }

  public enum Transport { HTTP, TIMEOUT, TLS, DNS, CONNECTION_BLOCKED, CONNECTION_RESET, CONNECTION_REFUSED, NETWORK, UNKNOWN }
  /** Contains class names and fixed categories only, never exception messages. */
  public record Diagnostic(Transport transport, Integer httpStatus, List<String> causeTypes) {}

  public static Diagnostic diagnostic(Throwable failure) {
    var types = new ArrayList<String>();
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    boolean timeout = false, tls = false, dns = false, network = false;
    Integer status = null;
    Transport connection = null;
    Throwable current = failure;
    for (int depth = 0; current != null && depth < 32 && seen.add(current); depth++, current = current.getCause()) {
      types.add(current.getClass().getName());
      if (status == null && current instanceof ApiException api) status = api.code();
      timeout |= isTimeout(current);
      tls |= current instanceof SSLException;
      dns |= current instanceof UnknownHostException;
      network |= current instanceof IOException || current instanceof GenAiIOException;
      if (current instanceof SocketException) {
        // Inspect known OS phrases to emit a fixed category; never emit the message.
        String message = String.valueOf(current.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("permission denied") || message.contains("access permissions") || message.contains("not permitted"))
          connection = Transport.CONNECTION_BLOCKED;
        else if (message.contains("connection reset") || message.contains("broken pipe"))
          connection = Transport.CONNECTION_RESET;
        else if (current instanceof ConnectException && message.contains("refused"))
          connection = Transport.CONNECTION_REFUSED;
      }
    }
    Transport transport = status != null ? Transport.HTTP : timeout ? Transport.TIMEOUT
        : tls ? Transport.TLS : dns ? Transport.DNS : connection != null ? connection
        : network ? Transport.NETWORK : Transport.UNKNOWN;
    return new Diagnostic(transport, status, List.copyOf(types));
  }

  private static boolean isTimeout(Throwable failure) {
    if (failure instanceof SocketTimeoutException || failure instanceof HttpTimeoutException
        || failure instanceof TimeoutException) return true;
    // OkHttp's whole-call deadline is an InterruptedIOException("timeout"),
    // which must not be confused with a generic network failure or cancellation.
    if (failure instanceof InterruptedIOException) {
      String message = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT).strip();
      return message.equals("timeout") || message.contains("timed out");
    }
    return false;
  }

  private static AiException http(int status) {
    return switch (status) {
      case 429 -> new AiException(AiException.Code.EMBEDDING_RATE_LIMITED,
          "[HTTP 429] Gemini đã giới hạn tốc độ hoặc hết quota embedding. Kiểm tra quota trong Google AI Studio; chờ giới hạn hồi phục rồi chạy lại. Retry không khắc phục được quota ngày đã hết.");
      case 401, 403 -> new AiException(AiException.Code.EMBEDDING_AUTHENTICATION_FAILED,
          "[HTTP " + status + "] Gemini từ chối xác thực hoặc quyền truy cập. Kiểm tra API key, hạn chế của key và quyền sử dụng model embedding.");
      case 400, 404, 413, 422 -> new AiException(AiException.Code.EMBEDDING_INVALID_REQUEST,
          "[HTTP " + status + "] Gemini từ chối yêu cầu embedding. Kiểm tra tên model, cấu hình, độ dài tài liệu và kích thước batch.");
      case 408, 504 -> new AiException(AiException.Code.EMBEDDING_TIMEOUT,
          "[HTTP " + status + "] Gemini không hoàn tất embedding trong thời gian cho phép. Thử lại sau hoặc giảm kích thước batch.");
      default -> new AiException(AiException.Code.EMBEDDING_UNAVAILABLE,
          status >= 400 && status <= 599
              ? "[HTTP " + status + "] Gemini chưa xử lý được yêu cầu embedding. Thử lại sau; những document đã ghi được giữ lại."
              : "[GEMINI_UNKNOWN_ERROR] Gemini trả về lỗi embedding không xác định.");
    };
  }
}
