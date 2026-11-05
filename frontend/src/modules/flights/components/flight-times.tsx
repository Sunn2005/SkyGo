import { View } from "react-native";
import { Icon, Label, Row, type AssetKey } from "@/shared/components/ui";
import { colors as c, space } from "@/shared/theme";
import { airport } from "../data/airports";
import type { Flight, Leg } from "../types";

export function FlightTimes({
  flight,
  leg,
  plane = "6-6734-imgPlane1",
  hideStops = false,
}: {
  flight: Flight;
  leg: Leg;
  plane?: AssetKey;
  hideStops?: boolean;
}) {
  return (
    <Row style={{ gap: 8 }}>
      <View style={{ flex: 1, minWidth: 0, gap: space.xs }}>
        <Label
          selectable
          size={24}
          weight="semibold"
          numberOfLines={1}
          adjustsFontSizeToFit
          minimumFontScale={0.8}
        >
          {flight.departure}
        </Label>
        <Label size={10} muted numberOfLines={1} adjustsFontSizeToFit>
          {leg.from} · {leg.from === "SGN" ? "Sài Gòn" : airport(leg.from).city}
        </Label>
      </View>
      <View style={{ flex: 1.25, minWidth: 0, gap: 5, alignItems: "center" }}>
        <Label size={10} muted style={{ textAlign: "center" }}>
          {flight.duration}
        </Label>
        <Row style={{ gap: 5 }}>
          <View style={{ flex: 1, height: 1, backgroundColor: c.line }} />
          <Icon name={plane} size={16} />
          <View style={{ flex: 1, height: 1, backgroundColor: c.line }} />
        </Row>
      </View>
      <View
        style={{ flex: 1, minWidth: 0, gap: space.xs, alignItems: "flex-end" }}
      >
        <Label
          selectable
          size={24}
          weight="semibold"
          numberOfLines={1}
          adjustsFontSizeToFit
          minimumFontScale={0.8}
        >
          {flight.arrival}
        </Label>
        <Label size={10} muted numberOfLines={1} adjustsFontSizeToFit>
          {leg.to} · {leg.to === "SGN" ? "Sài Gòn" : airport(leg.to).city}
        </Label>
      </View>
    </Row>
  );
}
