import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { AppState } from "react-native";
import {
  clearUserCache,
  setCacheOwner,
} from "@/shared/api/offline-cache";
import { accountService } from "../data/api-account-service";
import {
  readStoredSession,
  writeStoredSession,
} from "../data/session-storage";
import {
  AccountError,
  type AccountSession,
  type AccountUser,
  type LoginInput,
  type RegisterInput,
} from "../types";

type AccountContextValue = {
  session: AccountSession | null;
  login: (input: LoginInput) => Promise<void>;
  register: (input: RegisterInput) => Promise<AccountUser>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
};
const AccountContext = createContext<AccountContextValue | null>(null);

export function AccountProvider({ children }: { children: ReactNode }) {
  // Restored synchronously so the first screen already fetches as the user.
  const [session, setSession] = useState<AccountSession | null>(
    readStoredSession,
  );
  const userId = session?.user.id ?? null;
  // Set during render (idempotent): child effects run before this provider's
  // effects, so the first data load must already see the right cache owner.
  setCacheOwner(userId);
  const previousUserId = useRef<string | null>(userId);
  useEffect(() => {
    // Drop user data only on a real sign-out / expiry.
    if (!userId && previousUserId.current) clearUserCache();
    previousUserId.current = userId;
  }, [userId]);
  useEffect(() => writeStoredSession(session), [session]);
  // Check a restored token once against the server (revoked elsewhere, user
  // edited). Network failures keep the session so the app works offline.
  const restoredToken = useRef(session?.accessToken);
  useEffect(() => {
    const token = restoredToken.current;
    if (!token) return;
    accountService
      .me(token)
      .then((user) =>
        setSession((current) =>
          current?.accessToken === token ? { ...current, user } : current,
        ),
      )
      .catch((error: unknown) => {
        if (error instanceof AccountError && error.code === "UNAUTHENTICATED")
          setSession((current) =>
            current?.accessToken === token ? null : current,
          );
      });
  }, []);
  useEffect(() => {
    if (!session) return;
    const expire = () => {
      if (Date.parse(session.expiresAt) <= Date.now()) setSession(null);
    };
    const timer = setTimeout(
      expire,
      Math.max(0, Date.parse(session.expiresAt) - Date.now()),
    );
    const listener = AppState.addEventListener("change", (state) => {
      if (state === "active") expire();
    });
    return () => {
      clearTimeout(timer);
      listener.remove();
    };
  }, [session]);

  const login = async (input: LoginInput) =>
    setSession(await accountService.login(input));
  const logout = async () => {
    if (session) {
      try {
        await accountService.logout(session.accessToken);
      } catch (error) {
        // A revoked/expired session is already logged out on the server.
        if (!(
          error instanceof AccountError && error.code === "UNAUTHENTICATED"
        ))
          throw error;
      }
    }
    setSession(null);
  };
  const refreshUser = async () => {
    if (!session) return;
    try {
      const user = await accountService.me(session.accessToken);
      setSession((current) =>
        current?.accessToken === session.accessToken
          ? { ...current, user }
          : current,
      );
    } catch (error) {
      if (error instanceof AccountError && error.code === "UNAUTHENTICATED")
        setSession((current) =>
          current?.accessToken === session.accessToken ? null : current,
        );
      throw error;
    }
  };
  return (
    <AccountContext.Provider
      value={{
        session,
        login,
        register: accountService.register,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AccountContext.Provider>
  );
}

export function useAccount() {
  const value = useContext(AccountContext);
  if (!value) throw new Error("useAccount must be used inside AccountProvider");
  return value;
}
