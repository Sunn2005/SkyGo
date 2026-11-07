import { loginDestination } from "../utils/login-destination";
import { useBooking } from "@/modules/booking/context/booking-context";
import { useEffect, useRef, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, View } from "react-native";
import { Stack } from "expo-router/stack";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { Field } from "@/shared/components/fields";
import { Button, Icon, Label, Notice, Tap } from "@/shared/components/ui";
import { colors } from "@/shared/theme";
import { PasswordField } from "../components/password-field";
import { useAccount } from "../context/account-context";
import { AccountError, type RegisterInput } from "../types";
import { normalizePhone, validateLogin } from "../utils/validation";

export default function LoginScreen() {
  const insets = useSafeAreaInsets();
  const {
    phoneNumber: registeredPhone,
    registered,
    returnTo,
  } = useLocalSearchParams<{
    phoneNumber?: string;
    registered?: string;
    returnTo?: string;
  }>();
  const { session, login } = useAccount();
  const [phoneNumber, setPhoneNumber] = useState(registeredPhone ?? "");
  const [password, setPassword] = useState("");
  const [fields, setFields] = useState<
    Partial<Record<keyof RegisterInput, string>>
  >({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [lock, setLock] = useState<{ phone: string; until: number } | null>(
    null,
  );
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    if (!lock) return;
    const timer = setInterval(() => {
      const timestamp = Date.now();
      setNow(timestamp);
      if (timestamp >= lock.until) setLock(null);
    }, 1000);
    return () => clearInterval(timer);
  }, [lock]);
  const remaining =
    lock && lock.phone === normalizePhone(phoneNumber)
      ? Math.max(0, Math.ceil((lock.until - now) / 1000))
      : 0;

  const { href: destination, resumeFlight } = loginDestination(returnTo);
  const { setPendingFlight } = useBooking();
  useEffect(() => {
    if (session && resumeFlight)
      router.dismissTo({
        pathname: "/results",
        params: { resumeSelection: "1" },
      });
  }, [session, resumeFlight]);
  if (session) return resumeFlight ? null : <Redirect href={destination} />;

  const submit = async () => {
    if (pending.current || remaining > 0) return;
    const next = validateLogin({ phoneNumber, password });
    setFields(next);
    setError("");
    if (Object.keys(next).length) return;
    pending.current = true;
    setBusy(true);
    try {
      await login({ phoneNumber, password });
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Chưa thể đăng nhập. Vui lòng thử lại.",
      );
      if (cause instanceof AccountError) {
        setFields(cause.fields);
        if (cause.retryAt) {
          setError("");
          setNow(Date.now());
          setLock({
            phone: normalizePhone(phoneNumber)!,
            until: cause.retryAt,
          });
        }
      }
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
            style={{ width: 32 }}
            onPress={() => {
              setPendingFlight(null);
              if (router.canGoBack()) router.back();
              else router.replace("/");
            }}
          >
            <Icon name="6-6734-imgArrowLeft" size={20} />
          </Tap>
          <Label size={18} weight="semibold" style={{ flex: 1 }}>
            SkyGo
          </Label>
          <Tap label="Trợ giúp" onPress={() => router.push("/help")}>
            <Icon name="6-6816-imgCircleHelp" size={20} />
          </Tap>
        </View>
        <View style={{ marginTop: 44, gap: 20 }}>
          <View style={{ gap: 8 }}>
            <Label size={28} weight="bold">
              Chào mừng trở lại
            </Label>
            {!!returnTo && (
              <Label size={12} muted>
                Đăng nhập để tiếp tục đặt vé hoặc xem đặt chỗ của bạn
              </Label>
            )}
          </View>
          {registered === "1" && (
            <Notice>
              Đăng ký thành công. Đăng nhập bằng số điện thoại của bạn để tiếp
              tục.
            </Notice>
          )}
          <Field
            label="Số điện thoại"
            value={phoneNumber}
            onChangeText={(value) => {
              setPhoneNumber(value);
              setError("");
              setFields({});
            }}
            keyboardType="phone-pad"
            autoComplete="tel"
            placeholder="090 123 4567"
            editable={!busy}
            error={fields.phoneNumber}
          />
          <PasswordField
            label="Mật khẩu"
            value={password}
            onChangeText={(value) => {
              setPassword(value);
              setError("");
              setFields((current) => ({ ...current, password: undefined }));
            }}
            autoComplete="current-password"
            textContentType="password"
            editable={!busy}
            error={fields.password}
            returnKeyType="go"
            onSubmitEditing={() => void submit()}
          />
          <Label size={11} muted style={{ marginTop: -12 }}>
            Mật khẩu từ 8 ký tự, có hoa, thường, số, ký tự đặc biệt.
          </Label>
          {remaining === 0 && !!error && <Notice error>{error}</Notice>}
          <View style={{ minHeight: 54 }}>
            <Button
              title="Đăng nhập"
              loading={busy}
              disabled={remaining > 0}
              onPress={() => void submit()}
            />
          </View>
          {remaining > 0 && (
            <View
              accessibilityRole="alert"
              accessibilityLiveRegion="polite"
              style={{
                padding: 14,
                gap: 6,
                borderRadius: 8,
                borderWidth: 1,
                borderColor: "#E9C68C",
                backgroundColor: "#FFF6E7",
              }}
            >
              <Label size={13} weight="semibold" color="#8A5700">
                Tài khoản tạm khóa
              </Label>
              <Label size={12} color="#8A5700">
                Thử lại sau{" "}
                {String(Math.floor(remaining / 60)).padStart(2, "0")}:
                {String(remaining % 60).padStart(2, "0")}
              </Label>
            </View>
          )}
          <Tap
            onPress={() =>
              router.replace({ pathname: "/register", params: { returnTo } })
            }
            disabled={busy}
          >
            <Label
              size={13}
              color={colors.primary}
              weight="semibold"
              style={{ textAlign: "center" }}
            >
              Chưa có tài khoản? Đăng ký
            </Label>
          </Tap>
          <View style={{ height: 1, backgroundColor: colors.line }} />
          <Tap
            label="Tiếp tục không cần đăng nhập"
            disabled={busy}
            onPress={() => {
              setPendingFlight(null);
              router.replace("/");
            }}
            style={{
              borderWidth: 1,
              borderColor: colors.primary,
              borderRadius: 10,
              minHeight: 48,
              alignItems: "center",
            }}
          >
            <Label size={13} color={colors.primary} weight="semibold">
              Tiếp tục không cần đăng nhập
            </Label>
          </Tap>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
