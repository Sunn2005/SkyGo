import { View } from "react-native";
import type { BookingSnapshot } from "@/modules/booking/types";
import { dateLabel } from "@/shared/utils/date";
import { fullName } from "@/modules/booking/utils/traveller";
import { getLegs } from "@/modules/flights/utils/trip";
import { includedBagWeight } from "@/modules/booking/services/booking-rules";
import { money } from "@/shared/utils/format";
import {
  Badge,
  Card,
  Divider,
  Label,
  Row,
  SummaryRow,
} from "@/shared/components/ui";
import { FlightTimes } from "@/modules/flights/components/flight-times";
import { space } from "@/shared/theme";

export function Ticket({ receipt: r }: { receipt: BookingSnapshot }) {
  const legs = getLegs(r.search);
  return (
    <Card style={{ gap: 15 }}>
      <Row>
        <View style={{ flex: 1, gap: 2 }}>
          <Label size={11} muted>
            MÃ ĐẶT VÉ MẪU
          </Label>
          <Label selectable size={21} weight="bold">
            {r.reference}
          </Label>
        </View>
        <Badge>Đã xác nhận</Badge>
      </Row>
      <Divider />
      {legs.map((leg, i) => (
        <View key={i} style={{ gap: space.md }}>
          {legs.length > 1 && (
            <Label size={12} weight="semibold">
              Chặng {i + 1}
            </Label>
          )}
          <FlightTimes
            flight={r.selected[i]}
            leg={leg}
            plane="6-7220-imgPlane"
          />
          <Label size={12} muted>
            {dateLabel(leg.date)} · {r.selected[i].airline}{" "}
            {r.selected[i].number}
          </Label>
          <Divider />
        </View>
      ))}
      {r.travellers.map((p, i) => (
        <View key={i} style={{ gap: space.sm }}>
          <SummaryRow
            label={`Hành khách ${r.travellers.length > 1 ? i + 1 : ""}`}
            value={fullName(p)}
          />
          <SummaryRow label="Hạng vé" value={r.search.cabin} />
          <SummaryRow
            label="Hành lý đã gồm / chặng"
            value={`Xách tay 10 kg + 1 × ${includedBagWeight(r.search.cabin)} kg`}
          />
          {legs.map((_, l) => (
            <SummaryRow
              key={l}
              label={`Mua thêm · ${l === 0 ? "Lượt đi" : "Lượt về"}`}
              value={`${p.extraBags[l] ?? 0} kiện × 23 kg`}
            />
          ))}
        </View>
      ))}
      <Divider />
      <SummaryRow label="Đã trả mẫu · PayOS" value={money(r.total)} bold />
    </Card>
  );
}
