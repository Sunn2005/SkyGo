import { Share, View } from "react-native";
import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { airport } from "@/modules/flights/data/airports";
import { dateLabel } from "@/shared/utils/date";
import { money } from "@/shared/utils/format";
import { tripLabel } from "@/modules/flights/utils/trip";
import { ActionBar } from "@/modules/booking/components/action-bar";
import {
  Badge,
  Card,
  Divider,
  Icon,
  Label,
  Notice,
  Row,
  SummaryRow,
  Tap,
} from "@/shared/components/ui";
import { FlightTimes } from "@/modules/flights/components/flight-times";
import { Screen } from "@/shared/components/screen";
import { useAccount } from "@/modules/account/context/account-context";
import { includedBagWeight } from "@/modules/booking/services/booking-rules";
import { space } from "@/shared/theme";

export default function DetailScreen() {
  const b = useBooking();
  const { session } = useAccount();
  const f = b.candidate;
  const leg = b.legs[b.legIndex] ?? b.legs[0];
  const price = b.priceFor(f) * b.search.count;
  const continueBooking = () => {
    b.setSelected((previous) => {
      const next = [...previous];
      next[b.legIndex] = f;
      return next;
    });
    const nextRoute = b.legIndex < b.legs.length - 1 ? "results" : "traveller";
    if (nextRoute === "results") b.setLegIndex(b.legIndex + 1);
    if (!session) {
      router.push({ pathname: "/login", params: { returnTo: nextRoute } });
      return;
    }
    if (nextRoute === "results") {
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
              message: `${f.airline} ${f.number}: ${leg.from} → ${leg.to}, ${dateLabel(leg.date)}, ${f.departure}–${f.arrival}. ${money(price)}. Dữ liệu mẫu SkyGo.`,
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
      <SummaryRow label="Đồ cá nhân + xách tay 10 kg" value="Đã gồm" />
      <SummaryRow
        label="Hành lý ký gửi"
        value={`1 kiện tối đa ${includedBagWeight(b.search.cabin)} kg đã gồm`}
      />
      <Notice>
        Giá minh họa đã gồm thuế/phí. Giờ hiển thị là giờ địa phương.
      </Notice>
      <SummaryRow label="Vé gồm thuế/phí" value={money(price)} />
    </Screen>
  );
}
