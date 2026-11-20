import "expo-sqlite/localStorage/install";
import type { AccountSession } from "../types";

// Persisted sign-in so the user stays logged in across app restarts until they
// log out or the server-side session (expiresAt) runs out. expo-sqlite's
// localStorage is synchronous, so the session is available on the first render.
const KEY = "skygo:session:v1";

function isSession(value: unknown): value is AccountSession {
  if (!value || typeof value !== "object") return false;
  const s = value as Partial<AccountSession>;
  return (
    typeof s.accessToken === "string" &&
    typeof s.expiresAt === "string" &&
    !!s.user &&
    typeof s.user.id === "string"
  );
}

export function readStoredSession(): AccountSession | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (!isSession(parsed) || Date.parse(parsed.expiresAt) <= Date.now()) {
      localStorage.removeItem(KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function writeStoredSession(session: AccountSession | null) {
  try {
    if (session) localStorage.setItem(KEY, JSON.stringify(session));
    else localStorage.removeItem(KEY);
  } catch {
    // Storage unavailable: the user simply has to log in again next launch.
  }
}
