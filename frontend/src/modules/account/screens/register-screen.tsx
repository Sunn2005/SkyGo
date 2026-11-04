import { useRef, useState } from "react";
import { View } from "react-native";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { Screen } from "@/shared/components/screen";
import { Field } from "@/shared/components/fields";
import { Button, Label, Notice, Tap } from "@/shared/components/ui";
import { colors } from "@/shared/theme";
import { PasswordField } from "../components/password-field";
import { useAccount } from "../context/account-context";
import { AccountError, type RegisterInput } from "../types";
import { validateRegister } from "../utils/validation";

export default function RegisterScreen() {
  const { returnTo } = useLocalSearchParams<{ returnTo?: string }>();
  const { session, register } = useAccount();
  const [input, setInput] = useState<RegisterInput>({
    fullName: "",
    phoneNumber: "",
    email: "",
    password: "",
    confirmPassword: "",
  });
  const [fields, setFields] = useState<
    Partial<Record<keyof RegisterInput, string>>
  >({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  if (session) return <Redirect href="/profile" />;
  const change = (key: keyof RegisterInput) => (value: string) => {
    setInput((current) => ({ ...current, [key]: value }));
    setFields((current) => ({ ...current, [key]: undefined }));
    setError("");
  };
  const submit = async () => {
    if (pending.current) return;
    const next = validateRegister(input);
    setFields(next);
    setError("");
    if (Object.keys(next).length) return;
    pending.current = true;
    setBusy(true);
    try {
      const user = await register(input);
      router.replace({
        pathname: "/login",
        params: { registered: "1", phoneNumber: user.phoneNumber, returnTo },
      });
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Chưa thể đăng ký. Vui lòng thử lại.",
      );
      if (cause instanceof AccountError) setFields(cause.fields);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  return (
    <Screen title="Đăng ký tài khoản">
      <Label size={28} weight="bold">
        Hành trình bắt đầu từ đây.
      </Label>
      <Label muted>Tạo tài khoản SkyGo bằng số điện thoại của bạn.</Label>
      <Notice>
        Đây là bản mẫu. Hãy dùng thông tin và mật khẩu giả; dữ liệu được đặt lại
        khi tải lại ứng dụng.
      </Notice>
      <Field
        label="Họ và tên"
        value={input.fullName}
        onChangeText={change("fullName")}
        autoComplete="name"
        textContentType="name"
        editable={!busy}
        error={fields.fullName}
        placeholder="Nguyễn Văn An"
      />
      <Field
        label="Số điện thoại"
        value={input.phoneNumber}
        onChangeText={change("phoneNumber")}
        keyboardType="phone-pad"
        autoComplete="tel"
        editable={!busy}
        error={fields.phoneNumber}
        placeholder="0912 345 678"
      />
      <Field
        label="Email (không bắt buộc)"
        value={input.email}
        onChangeText={change("email")}
        keyboardType="email-address"
        autoComplete="email"
        autoCapitalize="none"
        autoCorrect={false}
        editable={!busy}
        error={fields.email}
        placeholder="ban@example.com"
      />
      <PasswordField
        label="Mật khẩu"
        value={input.password}
        onChangeText={change("password")}
        autoComplete="new-password"
        textContentType="newPassword"
        editable={!busy}
        error={fields.password}
      />
      <Label size={12} muted>
        Ít nhất 12 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt.
      </Label>
      <PasswordField
        label="Xác nhận mật khẩu"
        value={input.confirmPassword}
        onChangeText={change("confirmPassword")}
        autoComplete="new-password"
        textContentType="newPassword"
        editable={!busy}
        error={fields.confirmPassword}
        returnKeyType="go"
        onSubmitEditing={() => void submit()}
      />
      {!!error && <Notice error>{error}</Notice>}
      <View style={{ minHeight: 54 }}>
        <Button
          title="Tạo tài khoản"
          onPress={() => void submit()}
          loading={busy}
        />
      </View>
      <Tap
        onPress={() =>
          router.replace({ pathname: "/login", params: { returnTo } })
        }
        disabled={busy}
      >
        <Label style={{ textAlign: "center" }}>
          Đã có tài khoản?{" "}
          <Label color={colors.primary} weight="semibold">
            Đăng nhập
          </Label>
        </Label>
      </Tap>
    </Screen>
  );
}
