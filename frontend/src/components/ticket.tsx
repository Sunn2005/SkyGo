import { View } from "react-native";
import { type Receipt } from "@/context/booking-context";
import { dateLabel, fullName, getLegs, money } from "@/data/booking";
import {
  Badge,
  Card,
  Divider,
  FlightTimes,
  Label,
  Row,
  SummaryRow,
} from "@/components/ui";
import { space } from "@/theme";

export function Ticket({ receipt: r }: { receipt: Receipt }) {
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
          <SummaryRow
            label="Hạng ghế & chỗ ngồi"
            value={`${r.search.cabin} · ${legs.map((_, l) => r.seats[`${l}-${i}`] || "Tự xếp").join(" / ")}`}
          />
          <SummaryRow
            label="Hành lý / chặng"
            value={`Xách tay${p.bags ? ` + ${p.bags} × 23 kg` : ""}`}
          />
        </View>
      ))}
      <Divider />
      <SummaryRow
        label={`Đã trả mẫu · ${r.card.split("···· ")[1] ?? "Thẻ mẫu"}`}
        value={money(r.total)}
        bold
      />
    </Card>
  );
}
