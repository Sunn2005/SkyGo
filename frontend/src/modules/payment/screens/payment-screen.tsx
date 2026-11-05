import { useRef, useState } from "react";
import { View } from "react-native";
import { Redirect, router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { fullName } from "@/modules/booking/utils/traveller";
import {
  bookingErrors,
  includedBagWeight,
  EXTRA_BAG_PRICE,
} from "@/modules/booking/services/booking-rules";
import { money } from "@/shared/utils/format";
import { FlightTimes } from "@/modules/flights/components/flight-times";
import { dateLabel } from "@/shared/utils/date";
import { airport } from "@/modules/flights/data/airports";
import { ActionBar } from "@/modules/booking/components/action-bar";
import {
  Panel,
  InfoRow,
  passengerKind,
} from "@/modules/booking/components/design";
import { Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";
export default function PaymentScreen() {
  const b = useBooking();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const locked = useRef(false);
  if (b.selected.length !== b.legs.length) return <Redirect href="/" />;
  const pay = async () => {
    if (locked.current || !b.passengersConfirmed) return;
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
  const editPassengers = () =>
    router.push({ pathname: "/traveller", params: { returnTo: "payment" } });
  const editBaggage = () =>
    router.push({ pathname: "/baggage", params: { returnTo: "payment" } });
  const editFlight = () => {
    b.setLegIndex(0);
    router.push("/results");
  };
  const line = <View style={{ height: 1, backgroundColor: c.line }} />;
  return (
    <Screen
      design
      title="Kiểm tra & thanh toán"
      step={2}
      footer={
        <ActionBar
          title="Tiếp tục thanh toán"
          price={b.total}
          loading={pending}
          disabled={!b.passengersConfirmed}
          onPress={() => void pay()}
        />
      }
    >
      <Label size={13} muted>
        Kiểm tra thông tin trước khi thanh toán
      </Label>
      <Panel>
        <SectionHeader
          title="Hành trình"
          onEdit={editFlight}
          disabled={pending}
        />
        {b.selected.map((flight, i) => (
          <View key={i} style={{ gap: 10 }}>
            {i > 0 && line}
            <View style={{ alignItems: "flex-start" }}>
              <Label
                size={11}
                color={c.primary}
                weight="semibold"
                style={{
                  backgroundColor: c.tint,
                  paddingHorizontal: 8,
                  paddingVertical: 4,
                  borderRadius: 6,
                }}
              >
                {b.search.type === "round-trip"
                  ? i
                    ? "LƯỢT VỀ"
                    : "LƯỢT ĐI"
                  : "MỘT CHIỀU"}{" "}
                · {(flight.cabin ?? b.search.cabin).toUpperCase()}
              </Label>
            </View>
            <Label size={19} weight="bold">
              {b.legs[i].from === "SGN"
                ? "Sài Gòn"
                : airport(b.legs[i].from).city}{" "}
              →{" "}
              {b.legs[i].to === "SGN" ? "Sài Gòn" : airport(b.legs[i].to).city}
            </Label>
            <Label size={12} muted>
              {dateLabel(b.legs[i].date)}
            </Label>
            <FlightTimes flight={flight} leg={b.legs[i]} hideStops />
            <Label size={12} muted>
              {flight.airline} · {flight.number} ·{" "}
              {flight.stops ? "Có điểm dừng" : "Bay thẳng"}
            </Label>
          </View>
        ))}
      </Panel>
      <Panel>
        <SectionHeader
          title="Hành khách & liên hệ"
          onEdit={editPassengers}
          disabled={pending}
        />
        {b.travellers.map((p, i) => (
          <View key={i} style={{ gap: 10 }}>
            {i > 0 && line}
            <Row>
              <Label size={17} weight="bold" style={{ flex: 1 }}>
                {fullName(p)}
              </Label>
              <Label
                size={11}
                color={c.primary}
                style={{ backgroundColor: c.tint, padding: 5, borderRadius: 6 }}
              >
                {passengerKind(p.kind)}
              </Label>
            </Row>
            <Row style={{ alignItems: "flex-start" }}>
              <View style={{ flex: 1, gap: 3 }}>
                <Label size={12} muted>
                  Ngày sinh
                </Label>
                <Label size={13}>{p.birthDate}</Label>
              </View>
              <View style={{ flex: 1, gap: 3 }}>
                <Label size={12} muted>
                  {p.documentType === "passport" ? "Hộ chiếu" : "CCCD"}
                </Label>
                <Label size={13}>••••{p.documentNumber.slice(-4)}</Label>
              </View>
            </Row>
            {p.kind === "infant" && (
              <Label size={12} muted>
                Ngồi cùng {fullName(b.travellers[p.companionIndex ?? 0])} ·
                Không có ghế riêng
              </Label>
            )}
          </View>
        ))}
        {line}
        <InfoRow label="Email" value={b.email} />
        <InfoRow label="Điện thoại" value={b.phone} />
      </Panel>
      <Panel>
        <SectionHeader
          title="Hành lý"
          editLabel="Thêm hành lý"
          onEdit={editBaggage}
          disabled={pending}
        />
        {b.selected.map((flight, l) => (
          <View key={l} style={{ gap: 12 }}>
            {l > 0 && line}
            {b.selected.length > 1 && (
              <Label size={13} weight="semibold">
                {l ? "Chặng về" : "Chặng đi"} · {b.legs[l].from} →{" "}
                {b.legs[l].to}
              </Label>
            )}
            <Label size={11} muted weight="semibold">
              ĐÃ BAO GỒM · MỖI KHÁCH CÓ GHẾ RIÊNG
            </Label>
            {[
              "Xách tay 10 kg",
              `Ký gửi 1 kiện × ${includedBagWeight(flight.cabin ?? b.search.cabin)} kg`,
            ].map((text) => (
              <Row
                key={text}
                style={{
                  backgroundColor: c.tint,
                  borderRadius: 8,
                  padding: 10,
                  gap: 8,
                }}
              >
                <Label color={c.primary} weight="bold">
                  ✓
                </Label>
                <Label size={13}>{text}</Label>
              </Row>
            ))}
            {b.travellers.map((p, i) =>
              p.kind === "infant" ? (
                <Label key={i} size={12} muted>
                  {fullName(p)} · Em bé không mua hành lý riêng
                </Label>
              ) : (p.extraBags[l] ?? 0) > 0 ? (
                <View key={i} style={{ gap: 4 }}>
                  {line}
                  <Label size={13} weight="semibold">
                    Mua thêm
                  </Label>
                  <InfoRow
                    label={`${p.extraBags[l]} kiện × ${money(EXTRA_BAG_PRICE)}`}
                    value={money((p.extraBags[l] ?? 0) * EXTRA_BAG_PRICE)}
                    bold
                  />
                  <Label size={11} muted>
                    Áp dụng cho {fullName(p)} · {l ? "Chặng về" : "Chặng đi"}
                  </Label>
                </View>
              ) : null,
            )}
          </View>
        ))}
        {b.baggage === 0 && (
          <Label size={12} muted>
            Chưa mua thêm hành lý
          </Label>
        )}
      </Panel>
      <Panel>
        <Label size={18} weight="bold">
          Chi tiết giá
        </Label>
        {line}
        <InfoRow label="Vé đã gồm thuế, phí" value={money(b.fare)} />
        <InfoRow label="Hành lý mua thêm" value={money(b.baggage)} />
      </Panel>
      {!b.passengersConfirmed && (
        <Notice error>
          Vui lòng xác nhận lại thông tin hành khách trước khi thanh toán.
        </Notice>
      )}
      {!!error && <Notice error>{error}</Notice>}
    </Screen>
  );
}
function SectionHeader({
  title,
  onEdit,
  disabled,
  editLabel = "Sửa",
}: {
  title: string;
  onEdit: () => void;
  disabled: boolean;
  editLabel?: string;
}) {
  return (
    <>
      <Row style={{ justifyContent: "space-between" }}>
        <Label size={18} weight="bold" style={{ flex: 1 }}>
          {title}
        </Label>
        <Tap
          label={`${editLabel} ${title.toLowerCase()}`}
          disabled={disabled}
          onPress={onEdit}
        >
          <Label size={13} weight="semibold" color={c.primary}>
            {editLabel}
          </Label>
        </Tap>
      </Row>
      <View style={{ height: 1, backgroundColor: c.line }} />
    </>
  );
}
