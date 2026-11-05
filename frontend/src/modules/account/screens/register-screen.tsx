import { useRef, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, View } from "react-native";
import { Stack } from "expo-router/stack";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { PasswordField } from "../components/password-field";
import { Field } from "@/shared/components/fields";
import { Button, Icon, Label, Notice, Tap } from "@/shared/components/ui";
import { colors } from "@/shared/theme";
import { useAccount } from "../context/account-context";
import { AccountError, type RegisterInput } from "../types";
import { passwordRequirements, validateRegister } from "../utils/validation";

export default function RegisterScreen() {
  const insets = useSafeAreaInsets();
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
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: "#EEEEEE" }}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <Stack.Screen options={{ headerShown: false }} />
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          flexGrow: 1,
          paddingTop: insets.top + 12,
          paddingBottom: Math.max(insets.bottom, 24),
          paddingHorizontal: 24,
        }}
      >
        <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
          <Tap
            label="Quay lại"
            disabled={busy}
            onPress={() =>
              router.canGoBack() ? router.back() : router.replace("/login")
            }
            style={{ width: 32 }}
          >
            <Icon name="6-6734-imgArrowLeft" size={20} />
          </Tap>
          <Label size={18} weight="semibold">
            SkyGo
          </Label>
        </View>
        <View style={{ marginTop: 44, gap: 20 }}>
          <View style={{ gap: 8 }}>
            <Label size={28} weight="bold">
              Tạo tài khoản mới
            </Label>
            <Label size={12} muted>
              Đăng ký để bắt đầu hành trình cùng SkyGo
            </Label>
          </View>
          <Field
            label="Họ tên"
            value={input.fullName}
            onChangeText={change("fullName")}
            autoComplete="name"
            autoCapitalize="words"
            editable={!busy}
            error={fields.fullName}
            placeholder="Nguyễn Văn An"
          />
          <Field
            label="Email"
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
          <Field
            label="Số điện thoại"
            value={input.phoneNumber}
            onChangeText={change("phoneNumber")}
            keyboardType="phone-pad"
            autoComplete="tel"
            editable={!busy}
            error={fields.phoneNumber}
            placeholder="090 123 4567"
          />
          <PasswordField
            label="Mật khẩu"
            value={input.password}
            onChangeText={change("password")}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="••••••••••••"
            autoComplete="new-password"
            textContentType="newPassword"
            editable={!busy}
            error={fields.password}
          />
          <View
            style={{ marginTop: -10, gap: 6 }}
            accessibilityLiveRegion="polite"
          >
            {passwordRequirements.map((rule) => {
              const passed = rule.test(input.password);
              const color = passed ? "#16803C" : colors.muted;
              return (
                <View
                  key={rule.label}
                  style={{ flexDirection: "row", alignItems: "center", gap: 8 }}
                  accessible
                  accessibilityLabel={`${rule.label}: ${passed ? "đã đạt" : "chưa đạt"}`}
                >
                  <Label size={13} color={color} weight="bold">
                    {passed ? "✓" : "○"}
                  </Label>
                  <Label size={12} color={color}>
                    {rule.label}
                  </Label>
                </View>
              );
            })}
          </View>
          <PasswordField
            label="Xác nhận mật khẩu"
            value={input.confirmPassword}
            onChangeText={change("confirmPassword")}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="••••••••••••"
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
              title="Đăng ký"
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
            <Label
              size={13}
              color={colors.primary}
              weight="semibold"
              style={{ textAlign: "center" }}
            >
              Đã có tài khoản?{" "}
              <Label color={colors.primary} weight="semibold">
                Đăng nhập
              </Label>
            </Label>
          </Tap>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
