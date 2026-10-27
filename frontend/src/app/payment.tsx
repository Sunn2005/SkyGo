import { useEffect, useRef, useState } from "react";
import { View } from "react-native";
import { Redirect, router } from "expo-router";
import { useBooking } from "@/context/booking-context";
import { fullName, money } from "@/data/booking";
import {
  ActionBar,
  Card,
  Divider,
  Icon,
  Label,
  Notice,
  Row,
  Screen,
  SummaryRow,
  Tap,
  TripSummary,
} from "@/components/ui";
import { colors as c, space } from "@/theme";

export default function PaymentScreen() {
  const b = useBooking();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const locked = useRef(false);
  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );
  if (b.selected.length !== b.legs.length) return <Redirect href="/" />;
  const pay = () => {
    if (locked.current) return;
    if (b.selected.length !== b.legs.length)
      return setError(
        "Bạn chưa chọn đủ chuyến bay. Quay về trang chủ để tìm lại.",
      );
    locked.current = true;
    setPending(true);
    setError("");
    timer.current = setTimeout(() => {
      if (b.card.includes("0002")) {
        setError(
          "Thẻ mẫu bị từ chối. Hãy đổi sang Mastercard hoặc Visa rồi thử lại.",
        );
        setPending(false);
        locked.current = false;
        return;
      }
      b.confirm();
      setPending(false);
      locked.current = false;
      router.replace("/success");
    }, 900);
  };
  return (
    <Screen
      title="Kiểm tra & thanh toán"
      step={3}
      footer={
        <ActionBar
          title={
            pending ? "Đang xác nhận…" : `Thanh toán mẫu ${money(b.total)}`
          }
          price={b.total}
          loading={pending}
          onPress={pay}
        />
      }
    >
      <TripSummary />
      <Label size={20} weight="semibold">
        Phương thức thanh toán
      </Label>
      <Tap
        disabled={pending}
        label="Đổi thẻ thanh toán mẫu"
        onPress={() => router.push("/cards")}
      >
        <Card selected style={{ backgroundColor: c.tint, padding: 15 }}>
          <Row>
            <Icon name="6-7139-imgSelected" size={18} />
            <Icon name="6-7139-imgCreditCard" size={24} />
            <View style={{ flex: 1, gap: space.xs }}>
              <Label size={14} weight="semibold">
                {b.card}
              </Label>
              <Label size={11} muted>
                Thẻ mẫu · Hết hạn 08/28
              </Label>
            </View>
            <Label size={12} color={c.primary}>
              Sửa
            </Label>
          </Row>
        </Card>
      </Tap>
      <Tap
        disabled={pending}
        onPress={() => router.push("/cards")}
        style={{ minHeight: 24 }}
      >
        <Label size={13} weight="semibold" color={c.primary}>
          ＋ Chọn thẻ mẫu khác
        </Label>
      </Tap>
      <Row style={{ justifyContent: "space-between" }}>
        <Label size={18} weight="semibold">
          Hành khách & dịch vụ
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
          <Label size={12} color={c.primary}>
            Sửa
          </Label>
        </Tap>
      </Row>
      {b.travellers.map((p, i) => (
        <Label key={i} size={14}>
          {fullName(p)} · Người lớn · {p.gender}
        </Label>
      ))}
      <Label selectable size={12} muted>
        {b.email}
        {"\n"}
        {b.phone}
      </Label>
      <Row>
        <Tap
          disabled={pending}
          label="Sửa hành lý"
          onPress={() =>
            router.push({
              pathname: "/baggage",
              params: { returnTo: "payment" },
            })
          }
          style={{ flex: 1 }}
        >
          <Row style={{ gap: space.sm }}>
            <Icon name="6-7139-imgLuggage" size={16} />
            <Label size={12} style={{ flex: 1 }}>
              {b.travellers.reduce((sum, p) => sum + p.bags, 0)} kiện ký gửi /
              chặng
            </Label>
          </Row>
        </Tap>
        <Tap
          disabled={pending}
          label="Sửa chỗ ngồi"
          onPress={() =>
            router.push({ pathname: "/seat", params: { returnTo: "payment" } })
          }
          style={{ flex: 1 }}
        >
          <Row style={{ gap: space.sm }}>
            <Icon name="6-7139-imgArmchair" size={16} />
            <Label size={12} style={{ flex: 1 }}>
              {b.legs.length > 1
                ? `${Object.values(b.seats).filter(Boolean).length} chỗ / ${b.legs.length} chặng`
                : Object.values(b.seats).filter(Boolean).join(", ") ||
                  "Tự động xếp chỗ"}
            </Label>
          </Row>
        </Tap>
      </Row>
      <Card
        style={{ backgroundColor: c.surface, borderWidth: 0, gap: space.sm }}
      >
        <Label size={16} weight="semibold">
          Chi tiết giá
        </Label>
        <SummaryRow label="Giá vé" value={money(b.fare)} />
        <SummaryRow label="Thuế & phí sân bay" value={money(b.taxes)} />
        <SummaryRow label="Hành lý ký gửi" value={money(b.baggage)} />
        <SummaryRow label="Chọn chỗ ngồi" value={money(b.seatTotal)} />
        {b.insurance && (
          <SummaryRow label="Bảo vệ chuyến đi" value={money(b.protection)} />
        )}
        <Divider />
        <SummaryRow label="Tổng · USD" value={money(b.total)} bold />
      </Card>
      {error ? <Notice error>{error}</Notice> : null}
      <Notice icon="6-7139-imgLockKeyhole">
        Thanh toán mô phỏng, không trừ tiền và không gửi email. Xác nhận để tạo
        vé mẫu trong phiên sử dụng này.
      </Notice>
    </Screen>
  );
}
