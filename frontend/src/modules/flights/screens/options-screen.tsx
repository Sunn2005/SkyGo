import { useState } from "react";
import { View } from "react-native";
import { Host, Picker } from "@expo/ui";
import { FlightCalendar, localDateISO } from "../components/flight-calendar";
import { router, useLocalSearchParams } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { AirportPicker } from "../components/airport-picker";
import { dateLabel, validDate } from "@/shared/utils/date";
import { type Cabin } from "@/modules/flights/types";
import { ActionBar } from "@/modules/booking/components/action-bar";
import { Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";

const titles: Record<string, string> = {
  from: "Chọn điểm đi",
  to: "Chọn điểm đến",
  date: "Ngày khởi hành",
  secondDate: "Ngày về",
  count: "Số hành khách",
  cabin: "Hạng ghế",
};
export default function OptionsScreen() {
  const { field = "from" } = useLocalSearchParams<{ field: string }>();
  const { search, setSearch } = useBooking();
  const isDate = field === "date" || field === "secondDate";
  const isAirport = ["from", "to"].includes(field);
  const [today] = useState(() => localDateISO(new Date()));
  const minDate =
    field === "secondDate" && search.date > today ? search.date : today;
  const [draft, setDraft] = useState(() => {
    const date = field === "secondDate" ? search.secondDate : search.date;
    return validDate(date) && date >= minDate ? date : minDate;
  });
  const [error, setError] = useState("");
  const [count, setCount] = useState(search.count);
  const [children, setChildren] = useState(search.children ?? 0);
  const [infants, setInfants] = useState(search.infants ?? 0);
  const [cabin, setCabin] = useState<Cabin>(search.cabin);
  const save = () => {
    if (isDate) {
      const currentToday = localDateISO(new Date());
      const minimum =
        field === "secondDate" && search.date > currentToday
          ? search.date
          : currentToday;
      if (!validDate(draft) || draft < minimum)
        return setError("Vui lòng chọn ngày hợp lệ trên lịch.");
      setSearch((s) => ({
        ...s,
        [field]: draft,
        ...(field === "date" && s.secondDate < draft
          ? { secondDate: draft }
          : {}),
      }));
    } else if (field === "count")
      setSearch((s) => ({ ...s, count, children, infants }));
    else if (field === "cabin") setSearch((s) => ({ ...s, cabin }));
    router.back();
  };
  return (
    <Screen
      title={titles[field] ?? "Lựa chọn"}
      footer={
        !isAirport ? <ActionBar title="Áp dụng" onPress={save} /> : undefined
      }
    >
      {isAirport && (
        <AirportPicker key={field} field={field === "to" ? "to" : "from"} />
      )}
      {isDate && (
        <>
          <Label size={22} weight="semibold">
            {field === "secondDate" ? "Chọn ngày về" : "Chọn ngày khởi hành"}
          </Label>
          <Label size={13} muted>
            {field === "secondDate"
              ? `Ngày về từ ${dateLabel(search.date)} trở đi.`
              : "Chạm vào ngày trên lịch để chọn ngày bay."}
          </Label>
          <FlightCalendar
            value={draft}
            minDate={minDate}
            onChange={(value) => {
              setDraft(value);
              setError("");
            }}
            rangeStart={
              search.type === "round-trip"
                ? field === "secondDate"
                  ? search.date
                  : draft
                : undefined
            }
            rangeEnd={
              search.type === "round-trip"
                ? field === "secondDate"
                  ? draft
                  : search.secondDate
                : undefined
            }
          />
          {!!error && <Notice error>{error}</Notice>}
        </>
      )}
      {field === "count" && (
        <>
          <Label size={20} weight="semibold">
            Ai sẽ cùng bạn bay?
          </Label>
          <Label size={12} muted>
            Tối đa 4 khách có ghế riêng. Em bé ngồi cùng người lớn, không cần
            ghế riêng.
          </Label>
          {[
            {
              label: "Người lớn",
              hint: "Từ 12 tuổi",
              value: count,
              min: 1,
              max: 4 - children,
              change: (n: number) => {
                setCount(n);
                setInfants((v) => Math.min(v, n));
              },
            },
            {
              label: "Trẻ em",
              hint: "Từ 2 đến dưới 12 tuổi",
              value: children,
              min: 0,
              max: 4 - count,
              change: setChildren,
            },
            {
              label: "Em bé",
              hint: "Dưới 2 tuổi · Ngồi cùng người lớn",
              value: infants,
              min: 0,
              max: count,
              change: setInfants,
            },
          ].map((item) => (
            <Row key={item.label}>
              <View style={{ flex: 1 }}>
                <Label weight="semibold">{item.label}</Label>
                <Label size={12} muted>
                  {item.hint}
                </Label>
              </View>
              <Tap
                label={`Giảm ${item.label}`}
                disabled={item.value <= item.min}
                onPress={() => item.change(item.value - 1)}
                style={{ width: 44, alignItems: "center" }}
              >
                <Label size={24}>−</Label>
              </Tap>
              <Label>{item.value}</Label>
              <Tap
                label={`Tăng ${item.label}`}
                disabled={item.value >= item.max}
                onPress={() => item.change(item.value + 1)}
                style={{ width: 44, alignItems: "center" }}
              >
                <Label size={24}>+</Label>
              </Tap>
            </Row>
          ))}
        </>
      )}
      {field === "cabin" && (
        <>
          <Label size={20} weight="semibold">
            Bay theo cách của bạn
          </Label>
          <Host matchContents colorScheme="light" seedColor={c.primary}>
            <Picker
              selectedValue={cabin}
              onValueChange={setCabin}
              testID="cabin-picker"
            >
              <Picker.Item value="Phổ thông" label="Phổ thông" />
              <Picker.Item value="Thương gia" label="Thương gia" />
            </Picker>
          </Host>
          <Notice>
            Giá tìm kiếm là giá một người lớn đã gồm VAT. Quyền lợi hành lý và
            tổng tiền của nhóm khách được xác nhận ở bước kiểm tra đặt vé.
          </Notice>
        </>
      )}
    </Screen>
  );
}
