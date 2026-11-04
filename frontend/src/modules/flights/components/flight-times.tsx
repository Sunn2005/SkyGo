import { View } from "react-native";
import { Icon, Label, Row, type AssetKey } from "@/shared/components/ui";
import { colors as c, space } from "@/shared/theme";
import { airport } from "../data/airports";
import type { Flight, Leg } from "../types";

export function FlightTimes({
  flight,
  leg,
  plane = "6-6734-imgPlane1",
}: {
  flight: Flight;
  leg: Leg;
  plane?: AssetKey;
}) {
  return (
    <Row style={{ gap: space.md }}>
      <View style={{ flex: 1, gap: space.xs }}>
        <Label selectable size={26} weight="semibold">
          {flight.departure}
        </Label>
        <Label size={11} muted>
          {leg.from} · {airport(leg.from).city}
        </Label>
      </View>
      <View style={{ flex: 1.25, gap: 5, alignItems: "center" }}>
        <Label size={10} muted>
          {flight.duration}
        </Label>
        <Row style={{ gap: 5 }}>
          <View style={{ flex: 1, height: 1, backgroundColor: c.line }} />
          <Icon name={plane} size={16} />
          <View style={{ flex: 1, height: 1, backgroundColor: c.line }} />
        </Row>
        <Label size={10} color={c.primary}>
          {flight.stops ? "1 điểm dừng" : "Bay thẳng"}
        </Label>
      </View>
      <View style={{ flex: 1, gap: space.xs, alignItems: "flex-end" }}>
        <Label selectable size={26} weight="semibold">
          {flight.arrival}
        </Label>
        <Label size={11} muted>
          {leg.to} · {airport(leg.to).city}
        </Label>
      </View>
    </Row>
  );
}
