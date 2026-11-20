import "expo-sqlite/localStorage/install";
import { ApiError } from "./client";

// Read-through cache backed by expo-sqlite's localStorage (a SQLite file on
// device). Only read-only GET data goes through it; holds, quotes, bookings,
// payments, AI chat and auth stay online-only.
const PREFIX = "skygo:cache:v1:";
const USER_INDEX = `${PREFIX}user-index`;
const MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

type Entry<T> = { savedAt: number; value: T };
export type CacheScope = "public" | "user";

let owner: string | null = null;

/** Set by AccountProvider. Null means signed out: user-scoped data is neither read nor written. */
export function setCacheOwner(userId: string | null) {
  owner = userId;
}

function storageKey(scope: CacheScope, key: string) {
  if (scope === "public") return `${PREFIX}public:${key}`;
  return owner ? `${PREFIX}user:${owner}:${key}` : null;
}

function safe<T>(action: () => T): T | undefined {
  try {
    return action();
  } catch {
    // Storage can be full or unavailable; the cache must never break the app.
    return undefined;
  }
}

function readIndex(): string[] {
  const raw = safe(() => localStorage.getItem(USER_INDEX));
  try {
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed)
      ? parsed.filter((k): k is string => typeof k === "string")
      : [];
  } catch {
    return [];
  }
}

function readEntry<T>(scope: CacheScope, key: string): Entry<T> | undefined {
  const full = storageKey(scope, key);
  if (!full) return undefined;
  const raw = safe(() => localStorage.getItem(full));
  if (!raw) return undefined;
  try {
    const entry = JSON.parse(raw) as Entry<T>;
    if (Date.now() - entry.savedAt > MAX_AGE_MS) return undefined;
    return entry;
  } catch {
    return undefined;
  }
}

export function cacheRead<T>(scope: CacheScope, key: string): T | undefined {
  return readEntry<T>(scope, key)?.value;
}

export function cacheWrite<T>(scope: CacheScope, key: string, value: T) {
  const full = storageKey(scope, key);
  if (!full) return;
  safe(() =>
    localStorage.setItem(
      full,
      JSON.stringify({ savedAt: Date.now(), value } satisfies Entry<T>),
    ),
  );
  if (scope === "user") {
    const index = readIndex();
    if (!index.includes(full))
      safe(() =>
        localStorage.setItem(USER_INDEX, JSON.stringify([...index, full])),
      );
  }
}

/** Remove every signed-in user's cached data (call on logout). */
export function clearUserCache() {
  for (const full of readIndex()) safe(() => localStorage.removeItem(full));
  safe(() => localStorage.removeItem(USER_INDEX));
}

// Network failure, timeout or gateway error: the server is unreachable.
// 4xx (including 401) must surface as normal, never be masked by old data.
function isUnreachable(error: unknown) {
  if (error instanceof ApiError)
    return error.status === 408 || (error.status >= 502 && error.status <= 504);
  return error instanceof Error && error.name !== "AbortError";
}

/**
 * Fetch fresh data and cache it. If the server is unreachable, fall back to the
 * last cached copy; if there is none, rethrow the original error.
 * With `freshForMs`, a cached copy younger than that is returned without any
 * request (for near-static catalogs such as airports and regions).
 */
export async function withOfflineCache<T>(
  scope: CacheScope,
  key: string,
  fetcher: () => Promise<T>,
  options: { freshForMs?: number } = {},
): Promise<T> {
  if (options.freshForMs) {
    const entry = readEntry<T>(scope, key);
    if (entry && Date.now() - entry.savedAt < options.freshForMs)
      return entry.value;
  }
  try {
    const value = await fetcher();
    cacheWrite(scope, key, value);
    return value;
  } catch (error) {
    if (isUnreachable(error)) {
      const cached = cacheRead<T>(scope, key);
      if (cached !== undefined) return cached;
    }
    throw error;
  }
}
