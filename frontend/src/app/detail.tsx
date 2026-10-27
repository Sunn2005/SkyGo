import { Share, View } from "react-native";
import { router } from "expo-router";
import { useBooking } from "@/context/booking-context";
import { airport, dateLabel, money, tripLabel } from "@/data/booking";
import {
  ActionBar,
  Badge,
  Card,
  Divider,
  FlightTimes,
  Icon,
  Label,
  Notice,
  Row,
  Screen,
  SummaryRow,
  Tap,
} from "@/components/ui";
import { space } from "@/theme";

export default function DetailScreen() {
  const b = useBooking();
  const f = b.candidate;
  const leg = b.legs[b.legIndex] ?? b.legs[0];
  const price = f.price * b.multiplier * b.search.count;
  const continueBooking = () => {
    b.setSelected((previous) => {
      const next = [...previous];
      next[b.legIndex] = f;
      return next;
    });
    if (b.legIndex < b.legs.length - 1) {
      b.setLegIndex(b.legIndex + 1);
      router.replace("/results");
    } else router.push("/traveller");
  };
  return (
    <Screen
      title="Chi tiết chuyến bay"
      right={
        <Tap
          label="Chia sẻ chuyến bay"
          onPress={() => {
            void Share.share({
              message: `${f.airline} ${f.number}: ${leg.from} → ${leg.to}, ${dateLabel(leg.date)}, ${f.departure}–${f.arrival}. ${money(price)} USD. Dữ liệu mẫu SkyGo.`,
            });
          }}
        >
          <Icon name="6-6734-imgShare2" size={21} />
        </Tap>
      }
      footer={
        <ActionBar
          title={
            b.legIndex < b.legs.length - 1
              ? "Chọn chặng tiếp theo"
              : "Tiếp tục đặt vé"
          }
          price={price}
          onPress={continueBooking}
        />
      }
    >
      <View style={{ alignItems: "flex-start", gap: space.sm }}>
        <Badge>
          {tripLabel(b.search.type).toUpperCase()} ·{" "}
          {b.search.cabin.toUpperCase()}
        </Badge>
        <Label size={28} weight="bold">
          Hành trình đến {airport(leg.to).city}
        </Label>
        <Label muted>
          {dateLabel(leg.date)} · {b.search.count} người lớn
        </Label>
      </View>
      <Card>
        <Row>
          <Icon name="6-6734-imgPlane" />
          <Label size={14} weight="semibold" style={{ flex: 1 }}>
            {f.airline} · {f.number}
          </Label>
          <Badge>{f.stops ? "1 điểm dừng" : "Bay thẳng"}</Badge>
        </Row>
        <FlightTimes flight={f} leg={leg} />
        <Divider />
        <Row style={{ alignItems: "flex-start" }}>
          <Icon name="6-6734-imgPlaneTakeoff" size={18} />
          <View style={{ flex: 1 }}>
            <Label size={13}>
              {airport(leg.from).name} · {airport(leg.from).terminal}
            </Label>
            <Label size={12} muted>
              {dateLabel(leg.date)} · {f.departure}
            </Label>
          </View>
        </Row>
        <Row style={{ alignItems: "flex-start" }}>
          <Icon name="6-6734-imgPlaneLanding" size={18} />
          <View style={{ flex: 1 }}>
            <Label size={13}>
              {airport(leg.to).name} · {airport(leg.to).terminal}
            </Label>
            <Label size={12} muted>
              {dateLabel(leg.date)} · {f.arrival}
            </Label>
          </View>
        </Row>
        {f.stops > 0 && (
          <Notice>
            Quá cảnh tại Singapore · Thời gian chờ 3 giờ 30 phút (mô phỏng).
          </Notice>
        )}
        <Divider />
        <Row style={{ justifyContent: "space-between" }}>
          <Row style={{ gap: space.sm }}>
            <Icon name="6-6734-imgUtensils" size={16} />
            <Label size={12}>Có suất ăn</Label>
          </Row>
          <Row style={{ gap: space.sm }}>
            <Icon name="6-6734-imgWifi" size={16} />
            <Label size={12}>Có Wi-Fi</Label>
          </Row>
        </Row>
      </Card>
      <Label size={17} weight="semibold">
        Quyền lợi vé
      </Label>
      <SummaryRow label="Đồ cá nhân + xách tay 7 kg" value="Đã gồm" />
      <SummaryRow label="Hành lý ký gửi & chỗ ngồi" value="Chọn ở bước sau" />
      <Notice>
        Vé {b.search.cabin} · Đổi vé từ $100 và chênh lệch giá. Không hoàn vé.
        Giờ hiển thị là giờ địa phương.
      </Notice>
      <SummaryRow label="Giá vé" value={money(price - 86 * b.search.count)} />
      <SummaryRow
        label="Thuế & phí sân bay"
        value={money(86 * b.search.count)}
      />
    </Screen>
  );
}
