import {
  AccountError,
  type AccountService,
  type AccountSession,
  type AccountUser,
} from "../types";
import {
  normalizePhone,
  validateLogin,
  validateRegister,
} from "../utils/validation";

export const demoAccount = {
  phoneNumber: "0912345678",
  password: "SkyGoDemo@2027",
} as const;

// Demo only: credentials and sessions are volatile. Never persist passwords in FE.
export function createMockAccountService({
  now = Date.now,
  delayMs = 400,
}: { now?: () => number; delayMs?: number } = {}): AccountService {
  const accounts = new Map<string, { user: AccountUser; password: string }>([
    [
      "+84912345678",
      {
        user: {
          id: "demo-user",
          fullName: "Nguyễn Văn An",
          phoneNumber: "+84912345678",
          email: "an@example.com",
        },
        password: demoAccount.password,
      },
    ],
  ]);
  const attempts = new Map<
    string,
    { count: number; startedAt: number; lockedUntil: number }
  >();
  const sessions = new Map<string, AccountSession>();
  const windowMs = 15 * 60 * 1000;
  let nextId = 1;
  const wait = () =>
    new Promise<void>((resolve) => setTimeout(resolve, delayMs));
  const locked = (retryAt: number) =>
    new AccountError(
      "LOGIN_TEMPORARILY_LOCKED",
      "Đăng nhập sai 5 lần. Vui lòng thử lại sau 15 phút.",
      {},
      retryAt,
    );

  return {
    async register(input) {
      await wait();
      const fields = validateRegister(input);
      if (Object.keys(fields).length)
        throw new AccountError(
          "VALIDATION_ERROR",
          "Kiểm tra thông tin được đánh dấu.",
          fields,
        );
      const phone = normalizePhone(input.phoneNumber)!;
      if (accounts.has(phone))
        throw new AccountError(
          "PHONE_ALREADY_REGISTERED",
          "Số điện thoại này đã được đăng ký.",
          {
            phoneNumber:
              "Số điện thoại đã được sử dụng. Hãy đăng nhập hoặc dùng số khác.",
          },
        );
      const user: AccountUser = {
        id: `mock-user-${nextId++}`,
        fullName: input.fullName.trim(),
        phoneNumber: phone,
        email: input.email.trim() || null,
      };
      accounts.set(phone, { user, password: input.password });
      return { ...user };
    },
    async login(input) {
      await wait();
      const fields = validateLogin(input);
      if (Object.keys(fields).length)
        throw new AccountError(
          "VALIDATION_ERROR",
          "Kiểm tra thông tin được đánh dấu.",
          fields,
        );
      const phone = normalizePhone(input.phoneNumber)!;
      const timestamp = now();
      let attempt = attempts.get(phone);
      if (attempt && attempt.lockedUntil > timestamp)
        throw locked(attempt.lockedUntil);
      if (!attempt || timestamp - attempt.startedAt >= windowMs) {
        attempt = { count: 0, startedAt: timestamp, lockedUntil: 0 };
        attempts.set(phone, attempt);
      }
      const account = accounts.get(phone);
      if (!account || account.password !== input.password) {
        attempt.count += 1;
        if (attempt.count >= 5) {
          attempt.lockedUntil = timestamp + windowMs;
          throw locked(attempt.lockedUntil);
        }
        throw new AccountError(
          "INVALID_CREDENTIALS",
          "Số điện thoại hoặc mật khẩu chưa đúng.",
        );
      }
      attempts.delete(phone);
      const session: AccountSession = {
        accessToken: `mock-session-${nextId++}`,
        expiresAt: new Date(timestamp + 24 * 60 * 60 * 1000).toISOString(),
        user: { ...account.user },
      };
      sessions.set(session.accessToken, session);
      return { ...session, user: { ...session.user } };
    },
    async me(accessToken) {
      await wait();
      const session = sessions.get(accessToken);
      if (!session || Date.parse(session.expiresAt) <= now()) {
        sessions.delete(accessToken);
        throw new AccountError(
          "UNAUTHENTICATED",
          "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        );
      }
      return { ...session.user };
    },
    async logout(accessToken) {
      await wait();
      sessions.delete(accessToken);
    },
  };
}

export const accountService = createMockAccountService();
