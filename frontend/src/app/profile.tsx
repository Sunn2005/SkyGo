import { router } from "expo-router";
import { useBooking } from "@/context/booking-context";
import { Card, Icon, Label, Notice, Row, Screen, Tap } from "@/components/ui";
import { colors as c } from "@/theme";

export default function ProfileScreen() {
  const b = useBooking();
  return (
    <Screen title="Chào mừng đến SkyGo">
      <Label size={28} weight="bold">
        Đi xa, thật dễ.
      </Label>
      <Label muted>Khám phá trải nghiệm đặt vé cùng SkyGo.</Label>
      <Notice>Chế độ trải nghiệm · Dữ liệu mẫu · Không cần tài khoản</Notice>
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
      {b.receipt ? (
        <Tap onPress={() => router.push("/booking")}>
          <Card>
            <Label weight="semibold">
              Đặt vé gần nhất · {b.receipt.reference}
            </Label>
            <Label size={12} muted>
              Xem vé và chi tiết hành trình
            </Label>
          </Card>
        </Tap>
      ) : (
        <Card>
          <Label weight="semibold">Bạn chưa có đặt vé nào</Label>
          <Label size={12} muted>
            Hoàn tất một hành trình để xem vé mẫu tại đây.
          </Label>
        </Card>
      )}
      <Tap onPress={() => router.push("/help")}>
        <Label color={c.primary} weight="semibold">
          Trợ giúp & thông tin bản mẫu
        </Label>
      </Tap>
    </Screen>
  );
}
