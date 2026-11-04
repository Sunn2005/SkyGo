import { useState } from "react";
import { Platform, View } from "react-native";
import { Host, Picker } from "@expo/ui";
import { DateTimePicker } from "@expo/ui/community/datetime-picker";
import { router, useLocalSearchParams } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { airports } from "@/modules/flights/data/airports";
import { dateLabel, validDate } from "@/shared/utils/date";
import { type Cabin } from "@/modules/flights/types";
import { ActionBar } from "@/modules/booking/components/action-bar";
import { Field } from "@/shared/components/fields";
import { Icon, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c, radius, space } from "@/shared/theme";

const titles: Record<string, string> = {
  from: "Chọn điểm đi",
  to: "Chọn điểm đến",
  date: "Ngày khởi hành",
  secondDate: "Ngày bay tiếp theo",
  count: "Số hành khách",
  cabin: "Hạng ghế",
};
export default function OptionsScreen() {
  const { field = "from" } = useLocalSearchParams<{ field: string }>();
  const { search, setSearch } = useBooking();
  const isDate = field === "date" || field === "secondDate";
  const isAirport = ["from", "to"].includes(field);
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState(
    field === "secondDate" ? search.secondDate : search.date,
  );
  const [quickStart] = useState(
    field === "secondDate" ? search.secondDate : search.date,
  );
  const [error, setError] = useState("");
  const [count, setCount] = useState(search.count);
  const [cabin, setCabin] = useState<Cabin>(search.cabin);
  const save = () => {
    if (isDate) {
      if (!validDate(draft))
        return setError(
          "Ngày không hợp lệ. Nhập theo định dạng NNNN-TT-NN, ví dụ 2026-11-16.",
        );
      setSearch((s) => ({ ...s, [field]: draft }));
    } else if (field === "count") setSearch((s) => ({ ...s, count }));
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
        <>
          <Field
            label="Tìm sân bay hoặc thành phố"
            placeholder="Tên thành phố, mã sân bay…"
            value={query}
            onChangeText={setQuery}
            autoFocus
          />
          {airports
            .filter((a) =>
              `${a.city} ${a.code} ${a.name}`
                .normalize("NFD")
                .replace(/[\u0300-\u036f]/g, "")
                .toLowerCase()
                .includes(
                  query
                    .normalize("NFD")
                    .replace(/[\u0300-\u036f]/g, "")
                    .toLowerCase(),
                ),
            )
            .map((a) => (
              <Tap
                key={a.code}
                label={`${a.city}, ${a.code}`}
                onPress={() => {
                  setSearch((s) => ({ ...s, [field]: a.code }));
                  router.back();
                }}
                style={{
                  borderWidth: 1,
                  borderColor: c.line,
                  padding: space.lg,
                  borderRadius: radius.md,
                }}
              >
                <Row>
                  <Icon name="home-design-imgPlane" />
                  <View style={{ flex: 1, gap: 3 }}>
                    <Label weight="semibold">{a.city}</Label>
                    <Label size={12} muted>
                      {a.name}
                    </Label>
                  </View>
                  <Label weight="semibold" color={c.primary}>
                    {a.code}
                  </Label>
                </Row>
              </Tap>
            ))}
          {!!query &&
            !airports.some((a) =>
              `${a.city} ${a.code} ${a.name}`
                .toLowerCase()
                .includes(query.toLowerCase()),
            ) && (
              <Label size={12} muted>
                Thử tìm bằng mã sân bay: LHR, JFK, SGN, HAN, DAD hoặc SIN.
              </Label>
            )}
        </>
      )}
      {isDate && (
        <>
          <Label size={20} weight="semibold">
            Chọn ngày cho hành trình
          </Label>
          {Platform.OS !== "web" && (
            <DateTimePicker
              value={
                new Date((validDate(draft) ? draft : quickStart) + "T12:00:00")
              }
              mode="date"
              display="inline"
              presentation="inline"
              locale="vi_VN"
              themeVariant="light"
              accentColor={c.primary}
              minimumDate={
                field === "secondDate"
                  ? new Date(search.date + "T00:00:00")
                  : new Date()
              }
              onValueChange={(_, date) => {
                setDraft(
                  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`,
                );
              }}
            />
          )}
          <Field
            label="Ngày bay (NNNN-TT-NN)"
            value={draft}
            onChangeText={setDraft}
            placeholder="2026-11-16"
            keyboardType="numbers-and-punctuation"
            autoCapitalize="none"
          />
          <Label size={12} muted>
            Hoặc chọn nhanh một ngày:
          </Label>
          {Array.from({ length: 7 }, (_, i) => {
            const d = new Date(quickStart + "T12:00:00");
            d.setDate(d.getDate() + i);
            const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
            return (
              <Tap
                key={iso}
                selected={draft === iso}
                onPress={() => setDraft(iso)}
                style={{
                  padding: space.md,
                  borderRadius: radius.sm,
                  backgroundColor: draft === iso ? c.tint : c.surface,
                }}
              >
                <Label color={draft === iso ? c.primary : c.ink}>
                  {dateLabel(iso)}
                </Label>
              </Tap>
            );
          })}
          {!!error && <Notice error>{error}</Notice>}
        </>
      )}
      {field === "count" && (
        <>
          <Label size={20} weight="semibold">
            Ai sẽ cùng bạn bay?
          </Label>
          <Label muted>
            Người lớn từ 12 tuổi. Bản mẫu hỗ trợ tối đa 4 người trong một đặt
            vé.
          </Label>
          <Host matchContents colorScheme="light" seedColor={c.primary}>
            <Picker
              selectedValue={count}
              onValueChange={setCount}
              testID="passenger-count"
            >
              {[1, 2, 3, 4].map((n) => (
                <Picker.Item key={n} label={`${n} người lớn`} value={n} />
              ))}
            </Picker>
          </Host>
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
            Giá vé mẫu đã gồm thuế/phí. Thương gia gồm 1 kiện ký gửi tối đa 32
            kg.
          </Notice>
        </>
      )}
    </Screen>
  );
}
