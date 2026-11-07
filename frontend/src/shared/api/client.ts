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
  } = {},
): Promise<T> {
  const controller = new AbortController();
  const abort = () => controller.abort();
  options.signal?.addEventListener("abort", abort);
  if (options.signal?.aborted) controller.abort();
  const timeout = setTimeout(abort, 15000);
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
    const payload = await response.json();
    if (!response.ok) {
      const error = payload.error;
      throw new ApiError(
        response.status,
        error?.code ?? "HTTP_ERROR",
        error?.message ?? "Không thể xử lý yêu cầu.",
        error?.details ?? [],
        error?.lockedUntil,
      );
    }
    return (options.unwrap === false ? payload : payload.data) as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new Error(
      "Không kết nối được máy chủ. Kiểm tra địa chỉ API, mạng và backend rồi thử lại.",
    );
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener("abort", abort);
  }
}
