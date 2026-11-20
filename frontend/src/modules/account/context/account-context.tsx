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
  const [session, setSession] = useState<AccountSession | null>(null);
  const userId = session?.user.id ?? null;
  const previousUserId = useRef<string | null>(null);
  useEffect(() => {
    // Scope the offline cache to the signed-in user; drop it only on a real
    // sign-out (not at app start, when no session exists yet).
    setCacheOwner(userId);
    if (!userId && previousUserId.current) clearUserCache();
    previousUserId.current = userId;
  }, [userId]);
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
