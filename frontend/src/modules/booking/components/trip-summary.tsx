import { passengerSummary } from "../services/booking-rules";
import { View } from "react-native";
import { Icon, Label, Row } from "@/shared/components/ui";
import { colors as c, radius, space } from "@/shared/theme";
import { dateLabel } from "@/shared/utils/date";
import type { Leg } from "@/modules/flights/types";
import { useBooking } from "../context/booking-context";

export function TripSummary({ leg }: { leg?: Leg }) {
  const { legs, search, selected, remaining, isLive } = useBooking();
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
          {dateLabel(route.date)} · {passengerSummary(search)}
          {!leg && selected.length
            ? ` · ${selected.map((f) => f.cabin ?? search.cabin).join(" / ")}`
            : ""}
        </Label>
        {isLive && remaining !== null && (
          <Label size={12} color={remaining <= 300 ? c.primary : c.primary}>
            {remaining > 0
              ? `Giữ chỗ còn ${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")}`
              : "Giữ chỗ đã hết hạn"}
          </Label>
        )}
      </View>
    </Row>
  );
}
