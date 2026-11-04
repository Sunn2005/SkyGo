import { useEffect, useRef, useState } from "react";
import { View } from "react-native";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { Screen } from "@/shared/components/screen";
import { Field } from "@/shared/components/fields";
import { Button, Card, Label, Notice, Tap } from "@/shared/components/ui";
import { colors } from "@/shared/theme";
import { PasswordField } from "../components/password-field";
import { useAccount } from "../context/account-context";
import { demoAccount } from "../data/mock-account-service";
import { AccountError, type RegisterInput } from "../types";
import { normalizePhone, validateLogin } from "../utils/validation";

export default function LoginScreen() {
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

  const destinations = {
    detail: "/detail",
    results: "/results",
    traveller: "/traveller",
    baggage: "/baggage",
    payment: "/payment",
    payos: "/payos",
    booking: "/booking",
    success: "/success",
    bookings: "/bookings",
  } as const;
  const destination =
    returnTo && Object.hasOwn(destinations, returnTo)
      ? destinations[returnTo as keyof typeof destinations]
      : "/";
  if (session) return <Redirect href={destination} />;

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
    <Screen title="Đăng nhập">
      <Label size={28} weight="bold">
        Chào mừng trở lại.
      </Label>
      <Label muted>Đăng nhập để bắt đầu hành trình cùng SkyGo.</Label>
      {!!returnTo && (
        <Notice>Đăng nhập để tiếp tục đặt vé hoặc xem đặt chỗ của bạn.</Notice>
      )}
      {registered === "1" && (
        <Notice>
          Đăng ký thành công. Đăng nhập bằng số điện thoại của bạn để tiếp tục.
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
        placeholder="0912 345 678"
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
      {remaining > 0 ? (
        <Notice error>
          Đăng nhập tạm khóa. Thử lại sau {Math.floor(remaining / 60)} phút{" "}
          {remaining % 60} giây, hoặc dùng số điện thoại khác.
        </Notice>
      ) : (
        !!error && <Notice error>{error}</Notice>
      )}
      <View style={{ minHeight: 54 }}>
        <Button
          title="Đăng nhập"
          loading={busy}
          disabled={remaining > 0}
          onPress={() => void submit()}
        />
      </View>
      <Tap
        onPress={() =>
          router.replace({ pathname: "/register", params: { returnTo } })
        }
        disabled={busy}
      >
        <Label style={{ textAlign: "center" }}>
          Chưa có tài khoản?{" "}
          <Label color={colors.primary} weight="semibold">
            Đăng ký
          </Label>
        </Label>
      </Tap>
      <Card style={{ backgroundColor: colors.surface }}>
        <Label weight="semibold">Trải nghiệm bằng tài khoản mẫu</Label>
        <Label selectable size={13} muted>
          SĐT: {demoAccount.phoneNumber}
          {"\n"}Mật khẩu: {demoAccount.password}
        </Label>
        <Tap
          disabled={busy}
          onPress={() => {
            setPhoneNumber(demoAccount.phoneNumber);
            setPassword(demoAccount.password);
            setError("");
            setFields({});
          }}
        >
          <Label color={colors.primary} weight="semibold">
            Điền tài khoản mẫu
          </Label>
        </Tap>
      </Card>
      <Label size={12} muted>
        Chế độ dữ liệu mẫu. Tài khoản và phiên đăng nhập được đặt lại khi tải
        lại ứng dụng.
      </Label>
    </Screen>
  );
}
