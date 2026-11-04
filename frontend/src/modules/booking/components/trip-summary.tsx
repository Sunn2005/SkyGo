import { View } from "react-native";
import { Icon, Label, Row } from "@/shared/components/ui";
import { colors as c, radius, space } from "@/shared/theme";
import { dateLabel } from "@/shared/utils/date";
import type { Leg } from "@/modules/flights/types";
import { useBooking } from "../context/booking-context";

export function TripSummary({ leg }: { leg?: Leg }) {
  const { legs, search } = useBooking();
  const route = leg ?? legs[0];
  return (
    <Row
      style={{
        backgroundColor: c.surface,
        padding: space.md,
        borderRadius: radius.sm,
      }}
    >
      <Icon name="6-6816-imgPlane" />
      <View style={{ flex: 1, gap: 2 }}>
        <Label size={14} weight="semibold">
          {route.from} → {route.to}
          {!leg && legs.length > 1 ? ` · ${legs.length} chặng` : ""}
        </Label>
        <Label size={12} muted>
          {dateLabel(route.date)} · {search.count} người · {search.cabin}
        </Label>
      </View>
    </Row>
  );
}
