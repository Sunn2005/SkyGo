import { useState } from "react";
import { View } from "react-native";
import { Icon, Label, Row, Tap } from "@/shared/components/ui";
import { colors as c } from "@/shared/theme";
import { dateLabel, validDate } from "@/shared/utils/date";

export const localDateISO = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
export function FlightCalendar({
  value,
  minDate,
  onChange,
  rangeStart,
  rangeEnd,
  maxDate,
  quickYear = false,
}: {
  value: string;
  minDate: string;
  onChange: (value: string) => void;
  rangeStart?: string;
  rangeEnd?: string;
  maxDate?: string;
  quickYear?: boolean;
}) {
  const [month, setMonth] = useState(() =>
    (validDate(value) && value >= minDate ? value : minDate).slice(0, 7),
  );
  const [choosing, setChoosing] = useState<"year" | "month" | null>(null);
  const [yearPage, setYearPage] = useState(
    () => Number((value || minDate).slice(0, 4)) - 6,
  );
  const year = Number(month.slice(0, 4));
  const monthIndex = Number(month.slice(5, 7)) - 1;
  const offset = (new Date(year, monthIndex, 1, 12).getDay() + 6) % 7;
  const count = new Date(year, monthIndex + 1, 0, 12).getDate();
  const move = (delta: number) =>
    setMonth(
      localDateISO(new Date(year, monthIndex + delta, 1, 12)).slice(0, 7),
    );
  if (choosing) {
    const years = choosing === "year";
    const items = Array.from({ length: 12 }, (_, i) =>
      years ? yearPage + i : i + 1,
    );
    return (
      <View style={{ gap: 12 }}>
        <Row style={{ justifyContent: "space-between" }}>
          <Tap
            label="Nhóm năm trước"
            disabled={!years || yearPage <= +minDate.slice(0, 4)}
            onPress={() => setYearPage((v) => v - 12)}
          >
            <Label>‹</Label>
          </Tap>
          <Label weight="semibold">
            {years ? "Chọn năm" : `Chọn tháng · ${year}`}
          </Label>
          <Tap
            label="Nhóm năm sau"
            disabled={
              !years || (!!maxDate && yearPage + 12 > +maxDate.slice(0, 4))
            }
            onPress={() => setYearPage((v) => v + 12)}
          >
            <Label>›</Label>
          </Tap>
        </Row>
        <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
          {items.map((item) => {
            const candidate = `${year}-${String(item).padStart(2, "0")}`;
            const disabled = years
              ? item < +minDate.slice(0, 4) ||
                (!!maxDate && item > +maxDate.slice(0, 4))
              : candidate < minDate.slice(0, 7) ||
                (!!maxDate && candidate > maxDate.slice(0, 7));
            return (
              <Tap
                key={item}
                disabled={disabled}
                label={years ? `Năm ${item}` : `Tháng ${item}`}
                onPress={() => {
                  setMonth(years ? `${item}-${month.slice(5, 7)}` : candidate);
                  setChoosing(years ? "month" : null);
                }}
                style={{
                  width: "33.333%",
                  alignItems: "center",
                  minHeight: 48,
                }}
              >
                <Label color={c.primary}>
                  {years ? item : `Tháng ${item}`}
                </Label>
              </Tap>
            );
          })}
        </View>
        <Tap onPress={() => setChoosing(null)}>
          <Label style={{ textAlign: "center" }}>Quay lại lịch</Label>
        </Tap>
      </View>
    );
  }
  return (
    <View style={{ gap: 18 }}>
      <Row style={{ justifyContent: "space-between" }}>
        <Tap
          label="Tháng trước"
          disabled={month <= minDate.slice(0, 7)}
          onPress={() => move(-1)}
          style={{ width: 44, alignItems: "center" }}
        >
          <View style={{ transform: [{ rotate: "180deg" }] }}>
            <Icon name="6-6622-imgChevronRight" size={20} />
          </View>
        </Tap>
        {quickYear ? (
          <Tap
            label="Chọn tháng và năm"
            onPress={() => {
              setYearPage(year - 6);
              setChoosing("year");
            }}
          >
            <Label size={18} weight="semibold">
              Tháng {monthIndex + 1}, {year} ▾
            </Label>
          </Tap>
        ) : (
          <Label size={18} weight="semibold" accessibilityRole="header">
            Tháng {monthIndex + 1}, {year}
          </Label>
        )}
        <Tap
          disabled={!!maxDate && month >= maxDate.slice(0, 7)}
          label="Tháng sau"
          onPress={() => move(1)}
          style={{ width: 44, alignItems: "center" }}
        >
          <Icon name="6-6622-imgChevronRight" size={20} />
        </Tap>
      </Row>
      <Row style={{ gap: 0 }}>
        {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((day) => (
          <Label
            key={day}
            size={12}
            muted
            style={{ flex: 1, textAlign: "center" }}
          >
            {day}
          </Label>
        ))}
      </Row>
      <View style={{ gap: 4 }}>
        {Array.from({ length: Math.ceil((offset + count) / 7) }, (_, week) => (
          <Row key={week} style={{ gap: 0 }}>
            {Array.from({ length: 7 }, (_, weekday) => {
              const day = week * 7 + weekday - offset + 1;
              if (day < 1 || day > count)
                return (
                  <View key={weekday} style={{ flex: 1, minHeight: 44 }} />
                );
              const iso = `${month}-${String(day).padStart(2, "0")}`;
              const disabled = iso < minDate || (!!maxDate && iso > maxDate);
              const selected = iso === value;
              const inRange =
                rangeStart &&
                rangeEnd &&
                iso >= rangeStart &&
                iso <= rangeEnd &&
                !disabled;
              return (
                <Tap
                  key={weekday}
                  label={dateLabel(iso)}
                  disabled={disabled}
                  selected={selected}
                  onPress={() => onChange(iso)}
                  style={{
                    flex: 1,
                    minHeight: 44,
                    alignItems: "center",
                    borderRadius: 10,
                    backgroundColor: selected
                      ? c.primary
                      : inRange
                        ? c.tint
                        : "transparent",
                  }}
                >
                  <Label
                    size={15}
                    weight={selected ? "semibold" : "regular"}
                    color={
                      selected
                        ? c.white
                        : disabled
                          ? c.muted
                          : inRange
                            ? c.primary
                            : c.ink
                    }
                  >
                    {day}
                  </Label>
                </Tap>
              );
            })}
          </Row>
        ))}
      </View>
      <View
        style={{
          backgroundColor: c.tint,
          borderRadius: 12,
          padding: 16,
          gap: 4,
        }}
      >
        <Label size={12} color={c.primary}>
          Ngày đã chọn
        </Label>
        <Label size={16} weight="semibold" color={c.primary}>
          {dateLabel(value)}
        </Label>
      </View>
    </View>
  );
}
