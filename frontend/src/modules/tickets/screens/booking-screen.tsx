import { Share } from "react-native";
import { Redirect } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { dateLabel } from "@/shared/utils/date";
import { fullName } from "@/modules/booking/utils/traveller";
import { getLegs } from "@/modules/flights/utils/trip";
import { money } from "@/shared/utils/format";
import { Ticket } from "@/modules/tickets/components/ticket";
import { ActionBar } from "@/modules/booking/components/action-bar";
import {
  Card,
  Divider,
  Label,
  Notice,
  SummaryRow,
} from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";

export default function BookingScreen() {
  const { receipt: r } = useBooking();
  if (!r) return <Redirect href="/" />;
  const share = () => {
    void Share.share({
      message: `VÉ MẪU SKYGO · ${r.reference}\n${getLegs(r.search)
        .map(
          (l, i) =>
            `${l.from} → ${l.to} · ${dateLabel(l.date)} · ${r.selected[i].number} · ${r.selected[i].departure}–${r.selected[i].arrival}`,
        )
        .join(
          "\n",
        )}\n${r.travellers.map(fullName).join(", ")}\nTổng: ${money(r.total)} VND\nChỉ là vé mô phỏng, không có giá trị đi lại.`,
    });
  };
  return (
    <Screen
      title="Đặt vé của bạn"
      footer={<ActionBar title="Chia sẻ vé mẫu" onPress={share} />}
    >
      <Ticket receipt={r} />
      <Card>
        <Label weight="semibold">Chi tiết thanh toán mẫu</Label>
        <SummaryRow label="Vé đã gồm thuế/phí" value={money(r.fare)} />
        <SummaryRow label="Hành lý" value={money(r.baggage)} />
        <Divider />
        <SummaryRow label="Tổng · VND" value={money(r.total)} bold />
      </Card>
      <Label size={14} selectable>
        {r.email}
        {"\n"}
        {r.phone}
      </Label>
      <Notice>
        Vé mô phỏng không có giá trị đi lại. Đặt chỗ mẫu được lưu trên thiết bị
        theo tài khoản đăng nhập.
      </Notice>
    </Screen>
  );
}
