import { Platform } from "react-native";

export const API_BASE_URL = (
  process.env.EXPO_PUBLIC_API_URL ||
  (Platform.OS === "android" ? "http://10.0.2.2:8080" : "http://localhost:8080")
).replace(/\/$/, "");

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: { field: string; message: string }[] = [],
    public lockedUntil?: string,
    public retryAfterSeconds?: number,
  ) {
    super(message);
  }
}

export async function apiRequest<T>(
  path: string,
  options: {
    method?: string;
    body?: unknown;
    token?: string;
    signal?: AbortSignal;
    unwrap?: boolean;
    headers?: Record<string, string>;
    timeoutMs?: number;
  } = {},
): Promise<T> {
  const controller = new AbortController();
  const abort = () => controller.abort();
  options.signal?.addEventListener("abort", abort);
  if (options.signal?.aborted) controller.abort();
  let timedOut = false;
  const timeout = setTimeout(() => {
    timedOut = true;
    abort();
  }, options.timeoutMs ?? 15000);
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method: options.method ?? "GET",
      headers: {
        Accept: "application/json",
        ...options.headers,
        ...(options.body !== undefined
          ? { "Content-Type": "application/json" }
          : {}),
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      },
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
    });
    if (response.status === 204) return undefined as T;
    let payload;
    try {
      payload = await response.json();
    } catch {
      // A failed proxy/tunnel may return HTML instead of the API's JSON envelope.
      throw new ApiError(
        response.ok ? 502 : response.status,
        "INVALID_API_RESPONSE",
        "Máy chủ trả về dữ liệu không hợp lệ. Vui lòng kiểm tra địa chỉ API và đường hầm backend.",
        [],
        undefined,
        retryAfter(response.headers?.get("Retry-After")),
      );
    }
    if (!response.ok) {
      const error =
        payload?.error && typeof payload.error === "object"
          ? payload.error
          : undefined;
      throw new ApiError(
        response.status,
        typeof error?.code === "string" ? error.code : "HTTP_ERROR",
        typeof error?.message === "string" && error.message.trim()
          ? error.message
          : httpErrorMessage(response.status),
        error?.details ?? [],
        error?.lockedUntil,
        retryAfter(response.headers?.get("Retry-After")),
      );
    }
    return (options.unwrap === false ? payload : payload.data) as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (timedOut && options.timeoutMs !== undefined)
      throw new ApiError(
        408,
        "REQUEST_TIMEOUT",
        "Yêu cầu mất quá nhiều thời gian. Vui lòng thử lại sau.",
      );
    throw new Error(
      "Không kết nối được máy chủ. Kiểm tra địa chỉ API, mạng và backend rồi thử lại.",
    );
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener("abort", abort);
  }
}

function httpErrorMessage(status: number) {
  if (status === 404)
    return "Không tìm thấy API. Vui lòng kiểm tra địa chỉ máy chủ và phiên bản backend.";
  if (status === 401)
    return "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.";
  if (status === 403)
    return "Máy chủ từ chối truy cập. Vui lòng kiểm tra quyền truy cập và đường hầm backend.";
  if (status === 429)
    return "Máy chủ đang giới hạn yêu cầu. Vui lòng đợi rồi thử lại.";
  if (status >= 502 && status <= 504)
    return "Máy chủ hoặc đường hầm API tạm thời chưa phản hồi. Vui lòng thử lại sau.";
  if (status >= 500)
    return "Máy chủ gặp lỗi khi xử lý yêu cầu. Vui lòng thử lại sau.";
  return `Không thể xử lý yêu cầu (HTTP ${status}).`;
}

function retryAfter(value: string | null | undefined) {
  if (!value) return undefined;
  const seconds = /^\d+$/.test(value)
    ? Number(value)
    : Math.ceil((Date.parse(value) - Date.now()) / 1000);
  return Number.isFinite(seconds)
    ? Math.max(1, Math.min(seconds, 3600))
    : undefined;
}
