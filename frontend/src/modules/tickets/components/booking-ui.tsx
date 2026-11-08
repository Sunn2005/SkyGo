import { useState, type ReactNode } from "react";
import { View } from "react-native";
import { Card, Label, Row, Tap } from "@/shared/components/ui";
import { HomeIcon } from "@/modules/flights/components/home-icon";
import { colors as c } from "@/shared/theme";
import type {
  BookingDetail,
  BookingLeg,
} from "@/modules/payment/services/payment-service";
import {
  bookingPresentation,
  flightDate,
} from "../services/booking-presentation";

export function BookingStatus({
  booking,
  receivedAt,
}: {
  booking: BookingDetail;
  receivedAt: number;
}) {
  const status = bookingPresentation(booking, receivedAt);
  const palette =
    status.tone === "success"
      ? { background: "#E3F5EA", color: "#237047" }
      : status.tone === "warning"
        ? { background: "#FFF3DB", color: "#8A5609" }
        : { background: "#EDF1F5", color: c.muted };
  return (
    <View
      style={{
        alignSelf: "flex-start",
        paddingHorizontal: 10,
        paddingVertical: 5,
        borderRadius: 20,
        backgroundColor: palette.background,
      }}
    >
      <Label size={11} weight="semibold" color={palette.color}>
        {status.key === "paid" ? "✓ " : ""}
        {status.title}
      </Label>
    </View>
  );
}

export function BookingSectionTitle({ children }: { children: ReactNode }) {
  return (
    <Label size={17} weight="bold" style={{ marginTop: 4 }}>
      {children}
    </Label>
  );
}

export function BookingJourney({
  leg,
  compact = false,
}: {
  leg: BookingLeg;
  compact?: boolean;
}) {
  return (
    <View style={{ gap: compact ? 10 : 14 }}>
      <Row
        style={{ justifyContent: "space-between", flexWrap: "wrap", gap: 6 }}
      >
        <Label size={12} weight="semibold" color={c.primary}>
          {leg.direction === "RETURN" ? "CHUYẾN VỀ" : "CHUYẾN ĐI"} ·{" "}
          {flightDate(leg.departureAt)}
        </Label>
        <Label size={11} muted>
          {leg.flightNumber} ·{" "}
          {leg.cabinClass === "BUSINESS" ? "Thương gia" : "Phổ thông"}
        </Label>
      </Row>
      <Row style={{ gap: 12, alignItems: "center" }}>
        <View style={{ flex: 1, minWidth: 0, gap: 3 }}>
          <Label size={compact ? 23 : 28} weight="bold">
            {leg.departureAt.slice(11, 16)}
          </Label>
          <Label size={13} weight="semibold">
            {leg.origin.code}
          </Label>
          <Label size={11} muted numberOfLines={2}>
            {leg.origin.city}
          </Label>
        </View>
        <View style={{ flex: 1.1, alignItems: "center", gap: 8 }}>
          <Label size={10} muted>
            {Math.floor(leg.durationMinutes / 60)}h {leg.durationMinutes % 60}m
          </Label>
          <Row style={{ gap: 6 }}>
            <View style={{ height: 1, flex: 1, backgroundColor: c.line }} />
            <HomeIcon name="plane" color={c.primary} size={20} />
            <View style={{ height: 1, flex: 1, backgroundColor: c.line }} />
          </Row>
          <Label size={10} muted>
            {leg.airline.name}
          </Label>
        </View>
        <View style={{ flex: 1, minWidth: 0, alignItems: "flex-end", gap: 3 }}>
          <Label size={compact ? 23 : 28} weight="bold">
            {leg.arrivalAt.slice(11, 16)}
          </Label>
          <Label size={13} weight="semibold">
            {leg.destination.code}
          </Label>
          <Label
            size={11}
            muted
            numberOfLines={2}
            style={{ textAlign: "right" }}
          >
            {leg.destination.city}
          </Label>
        </View>
      </Row>
      {leg.arrivalAt.slice(0, 10) !== leg.departureAt.slice(0, 10) && (
        <Label size={11} muted>
          Ngày đến: {flightDate(leg.arrivalAt)}
        </Label>
      )}
      {!compact && (
        <Label size={10} muted>
          Giờ địa phương tại từng sân bay
        </Label>
      )}
    </View>
  );
}

export function BookingDisclosure({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  const [expanded, setExpanded] = useState(false);
  return (
    <Card style={{ padding: 16 }}>
      <Tap
        label={`${expanded ? "Thu gọn" : "Xem"} ${title}`}
        selected={expanded}
        onPress={() => setExpanded((value) => !value)}
      >
        <Row style={{ justifyContent: "space-between" }}>
          <View style={{ flex: 1, gap: 3 }}>
            <Label size={14} weight="semibold">
              {title}
            </Label>
            {subtitle && (
              <Label size={11} muted>
                {subtitle}
              </Label>
            )}
          </View>
          <Label size={18} color={c.primary}>
            {expanded ? "−" : "+"}
          </Label>
        </Row>
      </Tap>
      {expanded && (
        <View
          style={{
            gap: 10,
            paddingTop: 8,
            borderTopWidth: 1,
            borderTopColor: c.line,
          }}
        >
          {children}
        </View>
      )}
    </Card>
  );
}
