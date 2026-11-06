import { apiRequest, ApiError } from "@/shared/api/client";
import {
  AccountError,
  type AccountService,
  type AccountUser,
  type RegisterInput,
} from "../types";

type ApiUser = Omit<AccountUser, "id"> & { userId: string };
const mapUser = ({ userId, ...user }: ApiUser): AccountUser => ({
  ...user,
  id: userId,
});

async function accountRequest<T>(
  path: string,
  options?: Parameters<typeof apiRequest>[1],
) {
  try {
    return await apiRequest<T>(`/api/v1/auth${path}`, options);
  } catch (error) {
    if (error instanceof ApiError) {
      const fields: Partial<Record<keyof RegisterInput, string>> = {};
      for (const detail of error.details) {
        if (
          [
            "fullName",
            "email",
            "phoneNumber",
            "password",
            "confirmPassword",
          ].includes(detail.field)
        )
          fields[detail.field as keyof RegisterInput] = detail.message;
      }
      throw new AccountError(
        error.code as AccountError["code"],
        error.message,
        fields,
        error.lockedUntil ? Date.parse(error.lockedUntil) : undefined,
      );
    }
    throw error;
  }
}

export const accountService: AccountService = {
  async register(input) {
    return mapUser(
      await accountRequest<ApiUser>("/register", {
        method: "POST",
        body: input,
      }),
    );
  },
  async login(input) {
    const result = await accountRequest<{
      accessToken: string;
      expiresAt: string;
      user: ApiUser;
    }>("/login", { method: "POST", body: input });
    return {
      accessToken: result.accessToken,
      expiresAt: result.expiresAt,
      user: mapUser(result.user),
    };
  },
  async me(token) {
    return mapUser(await accountRequest<ApiUser>("/me", { token }));
  },
  async logout(token) {
    await accountRequest<void>("/logout", { method: "POST", token });
  },
};
