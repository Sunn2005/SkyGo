import type { LoginInput, RegisterInput } from "../types";

// Mock convention from the API draft. Confirm this rule with BE on integration.
export function normalizePhone(value: string) {
  const phone = value.trim().replace(/[\s()-]/g, "");
  if (/^0[35789]\d{8}$/.test(phone)) return `+84${phone.slice(1)}`;
  if (/^\+84[35789]\d{8}$/.test(phone)) return phone;
  return null;
}

export function validateLogin(input: LoginInput) {
  const errors: Partial<Record<keyof RegisterInput, string>> = {};
  if (!normalizePhone(input.phoneNumber))
    errors.phoneNumber = "Nhập số di động Việt Nam gồm 10 số hoặc dạng +84.";
  if (!input.password) errors.password = "Vui lòng nhập mật khẩu.";
  return errors;
}

export const passwordRequirements = [
  { label: "Ít nhất 8 ký tự", test: (value: string) => value.length >= 8 },
  {
    label: "Có chữ hoa và chữ thường",
    test: (value: string) => /[A-Z]/.test(value) && /[a-z]/.test(value),
  },
  {
    label: "Có số và ký tự đặc biệt",
    test: (value: string) =>
      /[0-9]/.test(value) && /[^A-Za-z0-9\s]/.test(value),
  },
];

export function validateRegister(input: RegisterInput) {
  const errors = validateLogin(input);
  if (
    input.email.trim() &&
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email.trim())
  )
    errors.email = "Vui lòng nhập email hợp lệ hoặc để trống.";
  if (!passwordRequirements.every((rule) => rule.test(input.password)))
    errors.password =
      "Ít nhất 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.";
  if (!input.confirmPassword || input.password !== input.confirmPassword)
    errors.confirmPassword = "Mật khẩu xác nhận chưa khớp.";
  return errors;
}
