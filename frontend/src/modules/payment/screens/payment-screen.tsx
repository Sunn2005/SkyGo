import { useRef, useState } from "react";
import { Redirect, router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { fullName } from "@/modules/booking/utils/traveller";
import {
  bookingErrors,
  includedBagWeight,
} from "@/modules/booking/services/booking-rules";
import { money } from "@/shared/utils/format";
import { ActionBar } from "@/modules/booking/components/action-bar";
import {
  Card,
  Divider,
  Label,
  Notice,
  Row,
  SummaryRow,
  Tap,
} from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { TripSummary } from "@/modules/booking/components/trip-summary";
import { colors as c } from "@/shared/theme";
export default function PaymentScreen() {
  const b = useBooking();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const locked = useRef(false);
  if (b.selected.length !== b.legs.length) return <Redirect href="/" />;
  const pay = async () => {
    if (locked.current) return;
    if (
      Object.keys(
        bookingErrors(b.search, b.selected, b.travellers, b.email, b.phone),
      ).length
    ) {
      setError(
        "Thông tin hành khách hoặc liên hệ chưa hợp lệ. Chọn Sửa để kiểm tra.",
      );
      return;
    }
    locked.current = true;
    setPending(true);
    setError("");
    try {
      await b.checkout();
      router.push("/payos");
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Chưa thể tạo đặt chỗ. Vui lòng thử lại.",
      );
    } finally {
      locked.current = false;
      setPending(false);
    }
  };
  return (
    <Screen
      title="Kiểm tra & thanh toán"
      step={2}
      footer={
        <ActionBar
          title="Xác nhận & thanh toán PayOS"
          price={b.total}
          loading={pending}
          onPress={() => void pay()}
        />
      }
    >
      <TripSummary />
      {b.selected.map((flight, i) => (
        <Card key={i}>
          <Label weight="semibold">
            {i === 0 ? "Lượt đi" : "Lượt về"} · {b.legs[i].from} →{" "}
            {b.legs[i].to}
          </Label>
          <Label>
            {b.legs[i].date} · {flight.airline} · {flight.number}
          </Label>
          <Label>
            {flight.departure} → {flight.arrival} · {b.search.cabin}
          </Label>
        </Card>
      ))}
      <Row>
        <Label size={18} weight="semibold" style={{ flex: 1 }}>
          Hành khách & liên hệ
        </Label>
        <Tap
          disabled={pending}
          onPress={() =>
            router.push({
              pathname: "/traveller",
              params: { returnTo: "payment" },
            })
          }
        >
          <Label color={c.primary}>Sửa</Label>
        </Tap>
      </Row>
      {b.travellers.map((p, i) => (
        <Card key={i}>
          <Label weight="semibold">
            {fullName(p)} · {p.gender}
          </Label>
          <Label size={13}>Ngày sinh: {p.birthDate}</Label>
          <Label size={13}>
            {p.documentType === "cccd" ? "CCCD" : "Hộ chiếu"}:{" "}
            {p.documentNumber}
          </Label>
          {p.documentType === "passport" && (
            <Label size={13}>
              {p.nationality} · Cấp tại {p.issuingCountry} · Hết hạn{" "}
              {p.documentExpiry}
            </Label>
          )}
        </Card>
      ))}
      <Label>
        {b.email}
        {"\n"}
        {b.phone}
      </Label>
      <Row>
        <Label size={18} weight="semibold" style={{ flex: 1 }}>
          Hành lý
        </Label>
        <Tap
          disabled={pending}
          onPress={() =>
            router.push({
              pathname: "/baggage",
              params: { returnTo: "payment" },
            })
          }
        >
          <Label color={c.primary}>Sửa</Label>
        </Tap>
      </Row>
      <Label size={13}>
        Mỗi người mỗi chặng: xách tay 10 kg + 1 kiện tối đa{" "}
        {includedBagWeight(b.search.cabin)} kg đã gồm.
      </Label>
      {b.travellers.map((p, i) => (
        <Label key={i} size={13}>
          {fullName(p)}:{" "}
          {b.legs
            .map(
              (_, l) =>
                `${l === 0 ? "Đi" : "Về"}: mua thêm ${p.extraBags[l] ?? 0} kiện 23 kg`,
            )
            .join(" · ")}
        </Label>
      ))}
      <Card>
        <Label weight="semibold">Chi tiết giá · VND</Label>
        <SummaryRow label="Vé đã gồm thuế/phí" value={money(b.fare)} />
        <SummaryRow label="Hành lý mua thêm" value={money(b.baggage)} />
        <Divider />
        <SummaryRow label="Tổng thanh toán" value={money(b.total)} bold />
      </Card>
      <Card selected>
        <Label weight="semibold">PayOS</Label>
        <Label size={13}>
          Thanh toán chung cho toàn bộ hành khách và các chặng.
        </Label>
      </Card>
      {!!error && <Notice error>{error}</Notice>}
      <Notice>
        Chế độ thử nghiệm: tạo đặt chỗ mẫu, sau đó mô phỏng thanh toán PayOS.
        Không trừ tiền và không gửi email.
      </Notice>
    </Screen>
  );
}
