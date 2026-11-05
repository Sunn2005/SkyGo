import { useRef, useState } from "react";
import { View } from "react-native";
import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import {
  Button,
  Card,
  Icon,
  Label,
  Notice,
  Row,
  Tap,
} from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";
import { useAccount } from "../context/account-context";

export default function ProfileScreen() {
  const b = useBooking();
  const { session, logout, refreshUser } = useAccount();
  const [busy, setBusy] = useState<"logout" | "refresh" | null>(null);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const user = session?.user;
  const perform = async (action: "logout" | "refresh") => {
    if (pending.current) return;
    pending.current = true;
    setBusy(action);
    setError("");
    try {
      if (action === "logout") {
        await logout();
        router.replace("/");
      } else await refreshUser();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Chưa thể thực hiện. Vui lòng thử lại.",
      );
    } finally {
      pending.current = false;
      setBusy(null);
    }
  };
  return (
    <Screen title="Tài khoản">
      <Card style={{ backgroundColor: c.tint, borderColor: c.tint }}>
        <Row style={{ alignItems: "flex-start" }}>
          <View
            style={{
              width: 52,
              height: 52,
              borderRadius: 26,
              backgroundColor: c.white,
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            {user ? (
              <Label size={22} weight="bold" color={c.primary}>
                {user.fullName
                  .trim()
                  .split(/\s+/)
                  .slice(-1)[0]
                  .slice(0, 1)
                  .toUpperCase()}
              </Label>
            ) : (
              <Icon name="6-6816-imgUserRound" size={26} />
            )}
          </View>
          <View style={{ flex: 1, gap: 4 }}>
            <Label size={22} weight="bold">
              {user ? `Xin chào, ${user.fullName}` : "Đi xa, thật dễ."}
            </Label>
            <Label size={13} muted>
              {user
                ? "Sẵn sàng cho hành trình tiếp theo của bạn."
                : "Đăng nhập để bắt đầu hành trình cùng SkyGo."}
            </Label>
          </View>
        </Row>
      </Card>
      {user ? (
        <Card>
          <Label size={18} weight="semibold">
            Thông tin tài khoản
          </Label>
          <AccountDetail label="Họ và tên" value={user.fullName} />
          <AccountDetail label="Số điện thoại" value={user.phoneNumber} />
          <AccountDetail label="Email" value={user.email ?? "Chưa cung cấp"} />
          <Tap disabled={busy !== null} onPress={() => void perform("refresh")}>
            <Label color={c.primary} weight="semibold">
              {busy === "refresh" ? "Đang tải thông tin…" : "Tải lại thông tin"}
            </Label>
          </Tap>
        </Card>
      ) : (
        <View style={{ gap: 10 }}>
          <View style={{ minHeight: 54 }}>
            <Button title="Đăng nhập" onPress={() => router.push("/login")} />
          </View>
          <View style={{ minHeight: 54 }}>
            <Button
              title="Tạo tài khoản"
              secondary
              onPress={() => router.push("/register")}
            />
          </View>
        </View>
      )}
      {!!error && <Notice error>{error}</Notice>}
      <Tap onPress={() => router.push("/saved")}>
        <Card>
          <Row>
            <Icon name="6-6622-imgHeart" />
            <Label weight="semibold" style={{ flex: 1 }}>
              Chuyến bay đã lưu
            </Label>
            <Label color={c.primary}>{b.favorites.length}</Label>
          </Row>
        </Card>
      </Tap>
      <Tap onPress={() => router.push("/help")}>
        <Label color={c.primary} weight="semibold">
          Trợ giúp & thông tin bản mẫu
        </Label>
      </Tap>
      {user && (
        <View style={{ minHeight: 54 }}>
          <Button
            title="Đăng xuất"
            secondary
            loading={busy === "logout"}
            disabled={busy === "refresh"}
            onPress={() => void perform("logout")}
          />
        </View>
      )}
    </Screen>
  );
}

function AccountDetail({ label, value }: { label: string; value: string }) {
  return (
    <View style={{ gap: 4 }}>
      <Label size={12} muted>
        {label}
      </Label>
      <Label selectable weight="medium">
        {value}
      </Label>
    </View>
  );
}
