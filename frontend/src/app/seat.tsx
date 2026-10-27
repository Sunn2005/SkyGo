import { useState } from "react";
import { ScrollView, View } from "react-native";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { useBooking } from "@/context/booking-context";
import { fullName, occupiedSeats, seatKind } from "@/data/booking";
import { ActionBar, Icon, Label, Row, Screen, Tap } from "@/components/ui";
import { colors as c, radius, space } from "@/theme";

export default function SeatScreen() {
  const b = useBooking();
  const { returnTo } = useLocalSearchParams<{ returnTo?: string }>();
  const [legIndex, setLegIndex] = useState(0);
  const [personIndex, setPersonIndex] = useState(0);
  if (b.selected.length !== b.legs.length) return <Redirect href="/" />;
  const leg = b.legs[legIndex];
  const key = `${legIndex}-${personIndex}`;
  const selected = b.seats[key] ?? "";
  const otherSeats = new Set(
    b.travellers.map((_, i) =>
      i === personIndex ? "" : b.seats[`${legIndex}-${i}`],
    ),
  );
  const choose = (seat: string) =>
    b.setSeats((previous) => ({
      ...previous,
      [key]: previous[key] === seat ? "" : seat,
    }));
  return (
    <Screen
      title="Chọn chỗ ngồi"
      step={2}
      footer={
        <ActionBar
          title={returnTo ? "Lưu chỗ ngồi" : "Tiếp tục thanh toán"}
          price={b.total}
          onPress={() => (returnTo ? router.back() : router.push("/payment"))}
        />
      }
    >
      {b.legs.length > 1 && (
        <Row>
          {b.legs.map((l, i) => (
            <Tap
              key={i}
              selected={i === legIndex}
              onPress={() => setLegIndex(i)}
              style={{
                flex: 1,
                padding: space.sm,
                borderRadius: radius.sm,
                backgroundColor: i === legIndex ? c.tint : c.surface,
              }}
            >
              <Label size={12} color={i === legIndex ? c.primary : c.ink}>
                Chặng {i + 1}: {l.from} → {l.to}
              </Label>
            </Tap>
          ))}
        </Row>
      )}
      <Row>
        <Icon name="6-6975-imgPlane" />
        <View style={{ flex: 1 }}>
          <Label size={14} weight="semibold">
            {leg.from} → {leg.to} · {b.selected[legIndex]?.number ?? "SH 201"}
          </Label>
          <Label size={11} muted>
            {leg.date.split("-").reverse().join("/")} · Airbus A321neo ·{" "}
            {b.search.cabin}
          </Label>
        </View>
      </Row>
      {b.travellers.length > 1 && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          <Row>
            {b.travellers.map((p, i) => (
              <Tap
                key={i}
                onPress={() => setPersonIndex(i)}
                selected={i === personIndex}
                style={{
                  padding: space.sm,
                  backgroundColor: i === personIndex ? c.tint : c.surface,
                  borderRadius: radius.sm,
                }}
              >
                <Label size={12} color={i === personIndex ? c.primary : c.ink}>
                  {fullName(p)} · {b.seats[`${legIndex}-${i}`] || "Tự xếp"}
                </Label>
              </Tap>
            ))}
          </Row>
        </ScrollView>
      )}
      <Row style={{ gap: space.lg }}>
        {[
          { text: "Còn trống", color: c.white },
          { text: "Đã có người", color: c.occupied },
          { text: "Đang chọn", color: c.primary },
        ].map((item) => (
          <Row key={item.text} style={{ gap: 5 }}>
            <View
              style={{
                width: 11,
                height: 11,
                borderWidth: 1,
                borderColor: c.line,
                borderRadius: 3,
                backgroundColor: item.color,
              }}
            />
            <Label size={10} muted>
              {item.text}
            </Label>
          </Row>
        ))}
      </Row>
      <View
        style={{
          backgroundColor: c.surface,
          paddingVertical: 14,
          paddingHorizontal: space.xl,
          borderTopLeftRadius: 64,
          borderTopRightRadius: 64,
          borderBottomLeftRadius: radius.lg,
          borderBottomRightRadius: radius.lg,
          gap: 6,
        }}
      >
        <Label size={9} muted style={{ textAlign: "center" }}>
          ĐẦU KHOANG ↑ · HÀNG 10–16
        </Label>
        <Row style={{ gap: 6 }}>
          {["A", "B", "C", "", "D", "E", "F"].map((col, i) => (
            <View key={i} style={{ flex: col ? 1 : 0.7, alignItems: "center" }}>
              <Label size={11} muted>
                {col}
              </Label>
            </View>
          ))}
        </Row>
        {Array.from({ length: 7 }, (_, i) => i + 10).map((row) => (
          <Row key={row} style={{ gap: 6 }}>
            {["A", "B", "C", "", "D", "E", "F"].map((col, j) => {
              const seat = `${row}${col}`;
              const blocked = occupiedSeats.has(seat) || otherSeats.has(seat);
              const active = selected === seat;
              if (!col)
                return (
                  <View key={j} style={{ flex: 0.7, alignItems: "center" }}>
                    <Label size={10} muted>
                      {row}
                    </Label>
                  </View>
                );
              return (
                <Tap
                  key={j}
                  label={`Ghế ${seat}, ${blocked ? "đã có người" : active ? "đang chọn" : seatKind(seat)}`}
                  disabled={blocked}
                  selected={active}
                  onPress={() => choose(seat)}
                  style={{
                    flex: 1,
                    minHeight: 32,
                    aspectRatio: 1.12,
                    borderRadius: 5,
                    alignItems: "center",
                    backgroundColor: active
                      ? c.primary
                      : blocked
                        ? c.occupied
                        : c.white,
                    borderWidth: blocked || active ? 0 : 1,
                    borderColor: c.line,
                  }}
                >
                  {active ? (
                    <Icon name="6-6975-imgCheck" size={18} />
                  ) : (
                    <Label size={9} color={blocked ? c.muted : c.ink}>
                      {blocked ? "×" : seat}
                    </Label>
                  )}
                </Tap>
              );
            })}
          </Row>
        ))}
      </View>
      <Row
        style={{
          backgroundColor: c.tint,
          padding: 14,
          borderRadius: radius.md,
        }}
      >
        <View style={{ flex: 1, gap: space.xs }}>
          <Label size={14} weight="semibold">
            {fullName(b.travellers[personIndex])} ·{" "}
            {selected || "Tự động xếp chỗ"}
          </Label>
          <Label size={12} color={c.primary}>
            {selected
              ? `${seatKind(selected)} · Chỗ để chân tiêu chuẩn`
              : "Xếp chỗ miễn phí khi làm thủ tục"}
          </Label>
        </View>
        <Label size={24} weight="bold">
          {selected ? "$24" : "$0"}
        </Label>
      </Row>
      <Tap
        onPress={() => b.setSeats((previous) => ({ ...previous, [key]: "" }))}
      >
        <Label size={12} muted>
          Không chọn chỗ? Xếp chỗ miễn phí khi làm thủ tục.
        </Label>
        {!!selected && (
          <Label size={12} color={c.primary} weight="semibold">
            Bỏ chọn ghế {selected}
          </Label>
        )}
      </Tap>
    </Screen>
  );
}
