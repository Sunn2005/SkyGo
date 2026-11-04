export type AccountUser = {
  id: string;
  fullName: string;
  phoneNumber: string;
  email: string | null;
};

export type LoginInput = { phoneNumber: string; password: string };
export type RegisterInput = LoginInput & {
  fullName: string;
  email: string;
  confirmPassword: string;
};
export type AccountSession = {
  accessToken: string;
  expiresAt: string;
  user: AccountUser;
};
export type AccountErrorCode =
  | "VALIDATION_ERROR"
  | "PHONE_ALREADY_REGISTERED"
  | "INVALID_CREDENTIALS"
  | "LOGIN_TEMPORARILY_LOCKED"
  | "UNAUTHENTICATED";

export class AccountError extends Error {
  constructor(
    public code: AccountErrorCode,
    message: string,
    public fields: Partial<Record<keyof RegisterInput, string>> = {},
    public retryAt?: number,
  ) {
    super(message);
    this.name = "AccountError";
  }
}

// Replace the mock implementation with an API adapter; screens only use context.
export interface AccountService {
  register(input: RegisterInput): Promise<AccountUser>;
  login(input: LoginInput): Promise<AccountSession>;
  me(accessToken: string): Promise<AccountUser>;
  logout(accessToken: string): Promise<void>;
}
