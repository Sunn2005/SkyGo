import { useResponsiveLayout } from "@/shared/hooks/use-responsive-layout";
import type { ReactNode } from "react";
import { View } from "react-native";
import { Icon, Label, Row } from "@/shared/components/ui";
import { colors as c } from "@/shared/theme";
import { FlightTimes } from "@/modules/flights/components/flight-times";
import { airport } from "@/modules/flights/data/airports";
import type { Flight, Leg } from "@/modules/flights/types";
import { dateLabel } from "@/shared/utils/date";
export function Panel({
  children,
  tinted = false,
}: {
  children: ReactNode;
  tinted?: boolean;
}) {
  return (
    <View
      style={{
        backgroundColor: tinted ? "#F4F8FB" : "white",
        padding: 14,
        borderRadius: 10,
        gap: 12,
      }}
    >
      {children}
    </View>
  );
}
export function InfoRow({
  label,
  value,
  bold = false,
}: {
  label: string;
  value: string;
  bold?: boolean;
}) {
  const { compact } = useResponsiveLayout();
  return (
    <Row
      style={{
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: compact ? 4 : 12,
        flexDirection: compact ? "column" : "row",
      }}
    >
      <Label
        size={12}
        muted={!bold}
        style={{ flex: compact ? undefined : 1, minWidth: 0 }}
      >
        {label}
      </Label>
      <Label
        size={12}
        weight={bold ? "bold" : "medium"}
        style={{
          flex: compact ? undefined : 1.4,
          minWidth: 0,
          textAlign: compact ? "left" : "right",
        }}
        selectable
      >
        {value}
      </Label>
    </Row>
  );
}
export function Journey({
  flight,
  leg,
  expanded = false,
}: {
  flight: Flight;
  leg: Leg;
  expanded?: boolean;
}) {
  const domestic =
    ["SGN", "HAN", "DAD", "CXR", "PQC"].includes(leg.from) &&
    ["SGN", "HAN", "DAD", "CXR", "PQC"].includes(leg.to);
  const terminal = (code: string) =>
    domestic && code === "SGN"
      ? "Nhà ga T3"
      : domestic && code === "HAN"
        ? "Nhà ga T1"
        : airport(code as Leg["from"]).terminal;
  return (
    <Panel tinted={!expanded}>
      {!expanded && (
        <Label size={12} muted>
          {dateLabel(leg.date)}
        </Label>
      )}

      <FlightTimes flight={flight} leg={leg} hideStops={expanded} />
      {expanded && (
        <>
          <View style={{ height: 1, backgroundColor: c.line }} />
          {(["from", "to"] as const).map((key, i) => (
            <Row key={key} style={{ alignItems: "flex-start", gap: 8 }}>
              <Icon
                name={i ? "6-6734-imgPlaneLanding" : "6-6734-imgPlaneTakeoff"}
                size={16}
              />
              <View style={{ flex: 1, gap: 4 }}>
                <Label size={12}>
                  {airport(leg[key]).name} · {terminal(leg[key])}
                </Label>
                <Label size={11} muted>
                  {dateLabel(leg.date)} ·{" "}
                  {i ? flight.arrival : flight.departure}
                </Label>
              </View>
            </Row>
          ))}
        </>
      )}
    </Panel>
  );
}
export const passengerKind = (kind?: string) =>
  kind === "infant" ? "Em bé" : kind === "child" ? "Trẻ em" : "Người lớn";
