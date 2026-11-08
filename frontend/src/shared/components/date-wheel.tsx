import { useEffect, useLayoutEffect, useRef, type ComponentRef } from "react";
import { FlatList, Platform, View, useWindowDimensions } from "react-native";
import { Label, Row, Tap } from "./ui";
import { colors as c } from "@/shared/theme";
import {
  changeDatePart,
  dateParts,
  dateWheelRanges,
  type DatePart,
} from "../utils/date-wheel";

function DateWheelColumn({
  label,
  values,
  value,
  onChange,
}: {
  label: string;
  values: number[];
  value: number;
  onChange: (value: number) => void;
}) {
  const list = useRef<ComponentRef<typeof FlatList<number>>>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { fontScale } = useWindowDimensions();
  const rowHeight = Math.max(44, Math.round(30 * fontScale));
  const index = Math.max(0, values.indexOf(value));
  const clearTimer = () => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
  };
  const commit = (offset: number) => {
    const selected = Math.max(
      0,
      Math.min(values.length - 1, Math.round(offset / rowHeight)),
    );
    if (values[selected] !== value) onChange(values[selected]);
  };
  useLayoutEffect(() => {
    if (timer.current) clearTimeout(timer.current);
    list.current?.scrollToOffset({
      offset: index * rowHeight,
      animated: false,
    });
  }, [index, rowHeight, values.length]);
  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );
  return (
    <View style={{ flex: 1, minWidth: 0, gap: 8 }}>
      <Label size={12} weight="semibold" muted style={{ textAlign: "center" }}>
        {label}
      </Label>
      <View
        style={{ height: rowHeight * 5, overflow: "hidden" }}
        accessibilityRole="adjustable"
        accessibilityLabel={label}
        accessibilityValue={{
          min: Math.min(...values),
          max: Math.max(...values),
          now: value,
          text: String(value),
        }}
        accessibilityActions={[
          { name: "increment", label: `Tăng ${label.toLowerCase()}` },
          { name: "decrement", label: `Giảm ${label.toLowerCase()}` },
        ]}
        onAccessibilityAction={(event) => {
          const next =
            value + (event.nativeEvent.actionName === "increment" ? 1 : -1);
          if (values.includes(next)) onChange(next);
        }}
      >
        <View
          pointerEvents="none"
          style={{
            position: "absolute",
            top: rowHeight * 2,
            height: rowHeight,
            left: 0,
            right: 0,
            backgroundColor: c.tint,
            borderRadius: 10,
            borderTopWidth: 1,
            borderBottomWidth: 1,
            borderColor: c.primary,
          }}
        />
        <FlatList
          ref={list}
          data={values}
          extraData={value}
          keyExtractor={(item) => String(item)}
          initialScrollIndex={index}
          initialNumToRender={9}
          windowSize={5}
          getItemLayout={(_, i) => ({
            length: rowHeight,
            offset: i * rowHeight,
            index: i,
          })}
          contentContainerStyle={{ paddingVertical: rowHeight * 2 }}
          showsVerticalScrollIndicator={false}
          snapToInterval={rowHeight}
          decelerationRate="fast"
          bounces={false}
          nestedScrollEnabled
          onScroll={(event) => {
            if (Platform.OS !== "web") return;
            clearTimer();
            const offset = event.nativeEvent.contentOffset.y;
            timer.current = setTimeout(() => commit(offset), 150);
          }}
          onScrollBeginDrag={clearTimer}
          onMomentumScrollBegin={clearTimer}
          onScrollEndDrag={(event) => {
            clearTimer();
            const offset = event.nativeEvent.contentOffset.y;
            timer.current = setTimeout(() => commit(offset), 150);
          }}
          onMomentumScrollEnd={(event) => {
            clearTimer();
            commit(event.nativeEvent.contentOffset.y);
          }}
          renderItem={({ item }) => (
            <Tap
              label={`${label} ${item}`}
              selected={item === value}
              onPress={() => onChange(item)}
              style={{
                height: rowHeight,
                minHeight: rowHeight,
                alignItems: "center",
              }}
            >
              <Label
                size={18}
                weight={item === value ? "bold" : "regular"}
                color={item === value ? c.primary : c.muted}
              >
                {String(item).padStart(2, "0")}
              </Label>
            </Tap>
          )}
        />
      </View>
    </View>
  );
}

export function DateWheel({
  value,
  minDate,
  maxDate,
  onChange,
}: {
  value: string;
  minDate: string;
  maxDate: string;
  onChange: (value: string) => void;
}) {
  const parts = dateParts(value),
    ranges = dateWheelRanges(value, minDate, maxDate);
  return (
    <Row style={{ gap: 10 }}>
      {(
        [
          { part: "year", label: "Năm" },
          { part: "month", label: "Tháng" },
          { part: "day", label: "Ngày" },
        ] as const
      ).map(({ part, label }) => (
        <DateWheelColumn
          key={part}
          label={label}
          values={ranges[part]}
          value={parts[part]}
          onChange={(next) =>
            onChange(
              changeDatePart(value, part as DatePart, next, minDate, maxDate),
            )
          }
        />
      ))}
    </Row>
  );
}
