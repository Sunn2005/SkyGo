import { Share } from "react-native";
import { Redirect } from "expo-router";
import { useBooking } from "@/context/booking-context";
import { dateLabel, fullName, getLegs, money } from "@/data/booking";
import { Ticket } from "@/components/ticket";
import {
  ActionBar,
  Card,
  Divider,
  Label,
  Notice,
  Screen,
  SummaryRow,
} from "@/components/ui";

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
        )}\n${r.travellers.map(fullName).join(", ")}\nTổng: ${money(r.total)} USD\nChỉ là vé mô phỏng, không có giá trị đi lại.`,
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
        <SummaryRow label="Giá vé" value={money(r.fare)} />
        <SummaryRow label="Thuế & phí" value={money(r.taxes)} />
        <SummaryRow label="Hành lý" value={money(r.baggage)} />
        <SummaryRow label="Chỗ ngồi" value={money(r.seatTotal)} />
        {r.protection > 0 && (
          <SummaryRow label="Bảo vệ chuyến đi" value={money(r.protection)} />
        )}
        <Divider />
        <SummaryRow label="Tổng · USD" value={money(r.total)} bold />
      </Card>
      <Label size={14} selectable>
        {r.email}
        {"\n"}
        {r.phone}
      </Label>
      <Notice>
        Vé mô phỏng không có giá trị đi lại. Vé được giữ trong phiên ứng dụng
        hiện tại; khởi động lại sẽ xóa dữ liệu mẫu.
      </Notice>
    </Screen>
  );
}
