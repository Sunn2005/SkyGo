import { Redirect, router } from "expo-router";
import { View } from "react-native";
import { useBooking } from "@/modules/booking/context/booking-context";
import { fullName } from "@/modules/booking/utils/traveller";
import { getLegs } from "@/modules/flights/utils/trip";
import { includedBagWeight } from "@/modules/booking/services/booking-rules";
import { money } from "@/shared/utils/format";
import { Button, Label, Row } from "@/shared/components/ui";
import {
  Panel,
  InfoRow,
  Journey,
  passengerKind,
} from "@/modules/booking/components/design";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";
export default function BookingScreen() {
  const b = useBooking();
  const booking = b.currentBooking;
  if (!booking) return <Redirect href="/bookings" />;
  const r = booking.receipt;
  const legs = getLegs(r.search);
  const payment = booking.payments.at(-1);
  const paid = booking.status === "confirmed";
  return (
    <Screen
      design
      title="Chi tiết đặt chỗ"
      footer={
        <Button title="Về trang chủ" onPress={() => router.replace("/")} />
      }
    >
      <Row>
        <View style={{ flex: 1, gap: 3 }}>
          <Label size={10} muted>
            MÃ ĐẶT CHỖ
          </Label>
          <Label
            size={23}
            weight="bold"
            selectable
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.6}
          >
            {r.reference}
          </Label>
        </View>
        <Label size={11} color={c.primary}>
          {paid
            ? "Đã xác nhận"
            : payment?.status === "expired"
              ? "Hết hạn"
              : "Chờ thanh toán"}
        </Label>
      </Row>
      <Label size={11} muted>
        Tạo lúc {new Date(booking.createdAt).toLocaleString("vi-VN")}
      </Label>
      {r.selected.map((flight, i) => (
        <View key={i} style={{ gap: 5 }}>
          <Journey flight={flight} leg={legs[i]} />
          <Label size={11} muted>
            {flight.cabin ?? r.search.cabin} ·{" "}
            {r.travellers.filter((p) => p.kind !== "infant").length} khách có
            ghế riêng
          </Label>
        </View>
      ))}
      <Panel>
        <Label size={13} weight="bold">
          Hành khách
        </Label>
        {r.travellers.map((p, i) => (
          <View key={i} style={{ gap: 8, paddingTop: i ? 12 : 0 }}>
            <InfoRow label={fullName(p)} value={passengerKind(p.kind)} bold />
            <InfoRow
              label={p.documentType === "passport" ? "Hộ chiếu" : "CCCD"}
              value={`••••${p.documentNumber.slice(-4)}`}
            />
            {p.kind === "infant" ? (
              <Label size={12}>
                Ngồi cùng {fullName(r.travellers[p.companionIndex ?? 0])} ·
                Không có ghế riêng
              </Label>
            ) : (
              r.selected.map((f, l) => (
                <View key={l} style={{ gap: 6 }}>
                  <InfoRow
                    label={`Hạng vé${legs.length > 1 ? (l ? " · Về" : " · Đi") : ""}`}
                    value={f.cabin ?? r.search.cabin}
                  />
                  <InfoRow
                    label="Hành lý"
                    value={`Xách tay 10 kg + 1 kiện ${includedBagWeight(f.cabin ?? r.search.cabin)} kg${p.extraBags[l] ? ` + ${p.extraBags[l]} kiện mua thêm` : ""}`}
                  />
                </View>
              ))
            )}
          </View>
        ))}
      </Panel>
      <Panel>
        <Label size={13} weight="bold">
          Thông tin liên hệ
        </Label>
        <InfoRow label="Email" value={r.email} />
        <InfoRow label="Điện thoại" value={r.phone} />
      </Panel>
      <Panel>
        <Label size={13} weight="bold">
          Giá vé
        </Label>
        <InfoRow label="Vé đã gồm thuế, phí" value={money(r.fare)} />
        <InfoRow label="Hành lý ký gửi mua thêm" value={money(r.baggage)} />
        <View style={{ height: 1, backgroundColor: c.line }} />
        <InfoRow label="Tổng cộng" value={money(r.total)} bold />
      </Panel>
      <Panel>
        <Row style={{ justifyContent: "space-between" }}>
          <Label size={13} weight="bold">
            Thanh toán
          </Label>
          <Label size={10} color={c.primary}>
            {paid
              ? "ĐÃ THANH TOÁN"
              : payment?.status === "expired"
                ? "HẾT HẠN"
                : "CHƯA THANH TOÁN"}
          </Label>
        </Row>
        <InfoRow label="Phương thức" value="QR code" />
        {payment && <InfoRow label="Mã giao dịch" value={payment.id} />}
        {paid && payment?.completedAt && (
          <InfoRow
            label="Thời điểm thanh toán"
            value={new Date(payment.completedAt).toLocaleString("vi-VN")}
          />
        )}
      </Panel>
      {!paid && payment?.status !== "expired" && (
        <Button
          title="Tiếp tục thanh toán"
          onPress={() => router.push("/payos")}
        />
      )}
    </Screen>
  );
}
