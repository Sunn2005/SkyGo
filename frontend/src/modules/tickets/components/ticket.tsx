import { View } from "react-native";
import type { BookingSnapshot } from "@/modules/booking/types";
import { fullName } from "@/modules/booking/utils/traveller";
import { getLegs } from "@/modules/flights/utils/trip";
import { includedBagWeight } from "@/modules/booking/services/booking-rules";
import { money } from "@/shared/utils/format";
import { Label, Row } from "@/shared/components/ui";
import { Journey, InfoRow } from "@/modules/booking/components/design";
import { colors as c } from "@/shared/theme";
export function Ticket({ receipt: r }: { receipt: BookingSnapshot }) {
  const legs = getLegs(r.search);
  return (
    <View
      style={{
        borderWidth: 1,
        borderColor: c.line,
        borderRadius: 12,
        padding: 14,
        gap: 16,
      }}
    >
      <Row>
        <View style={{ flex: 1, gap: 3 }}>
          <Label size={10} muted>
            MÃ ĐẶT CHỖ
          </Label>
          <Label size={22} weight="bold" selectable>
            {r.reference}
          </Label>
        </View>
        <Label size={11} color={c.primary}>
          Đã xác nhận
        </Label>
      </Row>
      <View style={{ height: 1, backgroundColor: c.line }} />
      {r.selected.map((flight, i) => (
        <View key={i} style={{ gap: 6 }}>
          <Journey flight={flight} leg={legs[i]} />
          <Label size={11} muted>
            {flight.cabin ?? r.search.cabin} · SkyGo
          </Label>
        </View>
      ))}
      <InfoRow
        label="Hành khách"
        value={r.travellers.map(fullName).join(", ")}
      />
      <InfoRow label="Hành lý xách tay" value="10 kg" />
      <InfoRow
        label="Ký gửi miễn cước"
        value={r.selected
          .map(
            (f) =>
              `1 kiện × ${includedBagWeight(f.cabin ?? r.search.cabin)} kg`,
          )
          .join(" · ")}
      />
      {r.travellers.some((p) => p.kind === "infant") && (
        <Label size={12} muted>
          Em bé ngồi cùng người lớn, không có ghế riêng.
        </Label>
      )}
      <InfoRow
        label="Hành lý mua thêm"
        value={`${r.travellers.reduce((n, p) => n + Object.values(p.extraBags).reduce((a, v) => a + v, 0), 0)} kiện`}
      />
      <View style={{ height: 1, backgroundColor: c.line }} />
      <InfoRow
        label="Đã thanh toán"
        value={money(r.total)}
        bold
      />
    </View>
  );
}
