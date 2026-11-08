import { useLayoutEffect, useRef, type ComponentRef } from "react";
import { ActivityIndicator, ScrollView, View } from "react-native";
import { Label, Tap } from "@/shared/components/ui";
import { colors as c } from "@/shared/theme";
import type { NearbyDay } from "../services/flight-service";

const dayName = (date: string) =>
  ["CN", "T2", "T3", "T4", "T5", "T6", "T7"][
    new Date(`${date}T12:00:00Z`).getUTCDay()
  ];

export function FlightDateStrip({
  days,
  selected,
  disabled,
  loading,
  canPrevious,
  canNext,
  onSelect,
  onExtend,
}: {
  days: NearbyDay[];
  selected: string;
  disabled: boolean;
  loading: "previous" | "next" | null;
  canPrevious: boolean;
  canNext: boolean;
  onSelect: (date: string) => void;
  onExtend: (side: "previous" | "next") => void;
}) {
  const scroll = useRef<ComponentRef<typeof ScrollView>>(null);
  const width = useRef(320);
  const centered = useRef("");
  const visibleDays = days.filter((day) => day.disabledReason !== "PAST_DATE");
  const center = () => {
    if (!selected || centered.current === selected) return;
    const index = visibleDays.findIndex((day) => day.date === selected);
    if (index < 0) return;
    scroll.current?.scrollTo({
      x: Math.max(
        0,
        (canPrevious ? 40 : 0) + index * 108 - (width.current - 108) / 2,
      ),
      animated: false,
    });
    centered.current = selected;
  };
  useLayoutEffect(() => {
    center();
  });
  return (
    <View style={{ gap: 8 }}>
      <ScrollView
        horizontal
        ref={scroll}
        showsHorizontalScrollIndicator={false}
        style={{ flexGrow: 0, height: 72 }}
        contentContainerStyle={{ alignItems: "flex-start" }}
        onLayout={(event) => {
          if (width.current !== event.nativeEvent.layout.width)
            centered.current = "";
          width.current = event.nativeEvent.layout.width;
          center();
        }}
        onContentSizeChange={center}
        onMomentumScrollEnd={(event) => {
          const { contentOffset, contentSize, layoutMeasurement } =
            event.nativeEvent;
          if (
            contentSize.width - contentOffset.x - layoutMeasurement.width <
              100 &&
            canNext &&
            !disabled &&
            !loading
          )
            onExtend("next");
        }}
      >
        {canPrevious && (
          <Tap
            label="Tải các ngày trước"
            disabled={disabled || !!loading}
            onPress={() => onExtend("previous")}
            style={{ width: 40, height: 72, alignItems: "center" }}
          >
            {loading === "previous" ? (
              <ActivityIndicator color={c.primary} />
            ) : (
              <Label color={c.primary} size={24}>
                ‹
              </Label>
            )}
          </Tap>
        )}
        {visibleDays.map((day) => {
          const active = selected === day.date;
          return (
            <Tap
              key={day.date}
              selected={active}
              disabled={disabled || !day.selectable}
              label={`${dayName(day.date)}, ${day.date.slice(8)}/${day.date.slice(5, 7)}${day.selectable ? `, ${day.pagination.totalElements} chuyến` : ", Không khả dụng"}`}
              onPress={() => {
                centered.current = "";
                onSelect(day.date);
              }}
              style={{
                width: 108,
                height: 72,
                paddingVertical: 10,
                alignItems: "center",
                gap: 5,
                borderBottomWidth: active ? 3 : 1,
                borderBottomColor: active ? c.primary : c.line,
              }}
            >
              <Label size={11} color={active ? c.primary : c.muted}>
                {day.selectable
                  ? `${day.pagination.totalElements} chuyến`
                  : "Không khả dụng"}
              </Label>
              <Label
                size={15}
                weight={active ? "bold" : "semibold"}
                color={active ? c.primary : c.ink}
              >
                {dayName(day.date)} - {day.date.slice(8)}/{day.date.slice(5, 7)}
              </Label>
            </Tap>
          );
        })}
        {canNext && (
          <Tap
            label="Tải thêm các ngày tiếp theo"
            disabled={disabled || !!loading}
            onPress={() => onExtend("next")}
            style={{
              width: 72,
              height: 72,
              alignItems: "center",
              borderBottomWidth: 1,
              borderBottomColor: c.line,
            }}
          >
            {loading === "next" ? (
              <ActivityIndicator color={c.primary} />
            ) : (
              <>
                <Label color={c.primary} size={24}>
                  ›
                </Label>
                <Label size={11} color={c.primary}>
                  Thêm ngày
                </Label>
              </>
            )}
          </Tap>
        )}
      </ScrollView>
      {loading === "previous" && (
        <Label size={11} muted>
          Đang tải các ngày trước…
        </Label>
      )}
    </View>
  );
}
