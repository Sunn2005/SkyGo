package com.skygo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import javax.net.ssl.SSLHandshakeException;

class GeminiEmbeddingFailureTest {
  @Test
  void exposesOnlyTheHttpCodeFromNestedProviderFailures() {
    var failure = new RuntimeException("request body and key must not leak",
        new ClientException(429, "provider body api-key=SECRET", "untrusted status SECRET"));
    var result = GeminiEmbeddingFailure.sanitized(failure);
    assertThat(result.code()).isEqualTo(AiException.Code.EMBEDDING_RATE_LIMITED);
    assertThat(result.getMessage()).contains("HTTP 429", "quota").doesNotContain("SECRET", "request body", "api-key");
    assertThat(result.getCause()).isNull();
    assertThat(result.getSuppressed()).isEmpty();
  }

  @Test
  void distinguishesAuthenticationInvalidRequestAndServerTimeout() {
    assertThat(GeminiEmbeddingFailure.sanitized(new ClientException(403, "secret", "secret")).code())
        .isEqualTo(AiException.Code.EMBEDDING_AUTHENTICATION_FAILED);
    assertThat(GeminiEmbeddingFailure.sanitized(new ClientException(400, "secret", "secret")).code())
        .isEqualTo(AiException.Code.EMBEDDING_INVALID_REQUEST);
    assertThat(GeminiEmbeddingFailure.sanitized(new ServerException(504, "secret", "secret")).code())
        .isEqualTo(AiException.Code.EMBEDDING_TIMEOUT);
    assertThat(GeminiEmbeddingFailure.sanitized(new ServerException(503, "secret", "secret")).getMessage())
        .contains("HTTP 503").doesNotContain("secret");
  }

  @Test
  void distinguishesNestedClientTimeoutFromDnsFailuresWithoutLeakingAddresses() {
    var timeout = new GenAiIOException("request=SECRET", new SocketTimeoutException("secret URL"));
    var dns = new GenAiIOException("key=SECRET", new UnknownHostException("private host"));
    assertThat(GeminiEmbeddingFailure.sanitized(timeout).code()).isEqualTo(AiException.Code.EMBEDDING_TIMEOUT);
    assertThat(GeminiEmbeddingFailure.sanitized(dns).code()).isEqualTo(AiException.Code.EMBEDDING_NETWORK_ERROR);
    assertThat(GeminiEmbeddingFailure.sanitized(dns).getMessage()).doesNotContain("SECRET", "private host");
  }

  @Test
  void okhttpCallDeadlineIsATimeoutButExplicitInterruptionRemainsDistinct() {
    var timeout = new GenAiIOException("SECRET request", new InterruptedIOException("timeout"));
    assertThat(GeminiEmbeddingFailure.sanitized(timeout).code()).isEqualTo(AiException.Code.EMBEDDING_TIMEOUT);
    assertThat(GeminiEmbeddingFailure.diagnostic(timeout).transport()).isEqualTo(GeminiEmbeddingFailure.Transport.TIMEOUT);
    var interrupted = new InterruptedIOException("interrupted SECRET");
    assertThat(GeminiEmbeddingFailure.sanitized(interrupted).code()).isEqualTo(AiException.Code.EMBEDDING_NETWORK_ERROR);
  }

  @Test
  void diagnosticsRevealTransportCategoriesWithoutKeysHostsOrProviderBodies() {
    var tls = new GenAiIOException("api-key=SECRET", new SSLHandshakeException("private cert SECRET"));
    var diagnostic = GeminiEmbeddingFailure.diagnostic(tls);
    assertThat(diagnostic.transport()).isEqualTo(GeminiEmbeddingFailure.Transport.TLS);
    assertThat(diagnostic.causeTypes()).containsExactly(GenAiIOException.class.getName(), SSLHandshakeException.class.getName());
    assertThat(diagnostic.toString()).doesNotContain("SECRET", "api-key", "private cert");
    assertThat(GeminiEmbeddingFailure.diagnostic(new UnknownHostException("SECRET host")).transport())
        .isEqualTo(GeminiEmbeddingFailure.Transport.DNS);
    assertThat(GeminiEmbeddingFailure.diagnostic(new SocketException("Permission denied: connect SECRET")).transport())
        .isEqualTo(GeminiEmbeddingFailure.Transport.CONNECTION_BLOCKED);
    var http = GeminiEmbeddingFailure.diagnostic(new ClientException(429, "SECRET body", "SECRET status"));
    assertThat(http.transport()).isEqualTo(GeminiEmbeddingFailure.Transport.HTTP);
    assertThat(http.httpStatus()).isEqualTo(429);
    assertThat(http.toString()).doesNotContain("SECRET");
  }

  @Test
  void diagnosticStopsAtCyclicCausesWithoutExposingMessages() {
    var first = new RuntimeException("SECRET first");
    var second = new RuntimeException("SECRET second", first);
    first.initCause(second);
    var diagnostic = GeminiEmbeddingFailure.diagnostic(first);
    assertThat(diagnostic.causeTypes()).hasSize(2);
    assertThat(diagnostic.toString()).doesNotContain("SECRET");
  }

  @Test
  void modelWrapperRetainsSafeDiagnosticsAndDoesNotConvertUnknownErrorsToQuotaErrors() {
    var delegate = mock(EmbeddingModel.class);
    var model = new RetrievalEmbeddingModel(delegate, delegate, 768);
    when(delegate.embed(anyString())).thenThrow(new RuntimeException("secret body",
        new ClientException(429, "secret key", "secret status")));
    assertThat(assertThrows(AiException.class, () -> model.embed("test")).code())
        .isEqualTo(AiException.Code.EMBEDDING_RATE_LIMITED);
    var unknown = GeminiEmbeddingFailure.sanitized(new IllegalStateException("secret"));
    assertThat(unknown.code()).isEqualTo(AiException.Code.EMBEDDING_UNAVAILABLE);
    assertThat(unknown.getMessage()).contains("GEMINI_UNKNOWN_ERROR").doesNotContain("secret");
  }
}
