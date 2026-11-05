import { passengerSummary } from "@/modules/booking/services/booking-rules";
import { useState } from "react";
import { View } from "react-native";
import { Image } from "expo-image";
import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { airport } from "@/modules/flights/data/airports";
import { dateLabel, validDate } from "@/shared/utils/date";
import { tripLabel } from "@/modules/flights/utils/trip";
import { type TripType } from "@/modules/flights/types";
import { ActionBar } from "@/modules/booking/components/action-bar";
import { Divider, Icon, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { SelectField } from "@/shared/components/fields";
import { colors as c, radius, space } from "@/shared/theme";

export default function SearchScreen() {
  const b = useBooking();
  const { search: s, setSearch } = b;
  const [error, setError] = useState("");
  const select = (field: string) =>
    router.push({ pathname: "/options", params: { field } });
  const search = () => {
    const today = new Date();
    const todayISO = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
    if (s.from === s.to)
      return setError("Điểm đi và điểm đến của mỗi chặng phải khác nhau.");
    if (!validDate(s.date) || s.date < todayISO)
      return setError("Vui lòng chọn ngày khởi hành từ hôm nay trở đi.");
    if (
      s.type !== "one-way" &&
      (!validDate(s.secondDate) || s.secondDate < s.date)
    )
      return setError("Ngày bay tiếp theo phải từ ngày khởi hành trở đi.");
    if (
      !Number.isInteger(s.count) ||
      s.count < 1 ||
      s.count + (s.children ?? 0) > 4 ||
      (s.infants ?? 0) > s.count
    )
      return setError(
        "Chọn tối đa 4 khách có ghế riêng, em bé không nhiều hơn người lớn.",
      );
    setError("");
    b.startSearch();
    router.push("/results");
  };
  return (
    <Screen
      design
      title="SkyGo"
      home
      footer={<ActionBar title="Tìm chuyến bay" onPress={search} />}
    >
      <View style={{ gap: space.sm }}>
        <Label size={30} weight="bold">
          Bạn muốn đi đâu?
        </Label>
        <Label muted>Hành trình mới, thật dễ dàng.</Label>
      </View>
      <Row
        style={{
          gap: space.xs,
          backgroundColor: c.surface,
          padding: space.xs,
          borderRadius: radius.md,
        }}
      >
        {(["one-way", "round-trip"] as TripType[]).map((type) => (
          <Tap
            key={type}
            selected={s.type === type}
            onPress={() => setSearch((prev) => ({ ...prev, type }))}
            style={{
              flex: 1,
              minHeight: 36,
              paddingVertical: space.sm,
              backgroundColor: s.type === type ? c.white : c.surface,
              alignItems: "center",
              borderRadius: radius.sm,
            }}
          >
            <Label size={13} color={s.type === type ? c.primary : c.muted}>
              {tripLabel(type)}
            </Label>
          </Tap>
        ))}
      </Row>
      <View
        style={{
          borderWidth: 1,
          borderColor: c.line,
          borderRadius: radius.lg,
          padding: space.lg,
          gap: space.md,
        }}
      >
        <Tap label="Chọn điểm đi" onPress={() => select("from")}>
          <Row style={{ gap: 14 }}>
            <Icon name="home-design-imgPlaneTakeoff" />
            <View style={{ flex: 1, gap: 3 }}>
              <Label size={11} muted>
                ĐIỂM ĐI
              </Label>
              <Label size={20} weight="semibold">
                {airport(s.from).city}
              </Label>
              <Label size={12} muted>
                {airport(s.from).name} · {s.from}
              </Label>
            </View>
          </Row>
        </Tap>
        <Row>
          <View style={{ flex: 1 }}>
            <Divider />
          </View>
          <Tap
            label="Đổi điểm đi và điểm đến"
            onPress={() =>
              setSearch((prev) => ({ ...prev, from: prev.to, to: prev.from }))
            }
            style={{ minHeight: 22, paddingHorizontal: space.xs }}
          >
            <Icon name="home-design-imgArrowDownUp" size={18} />
          </Tap>
        </Row>
        <Tap label="Chọn điểm đến" onPress={() => select("to")}>
          <Row style={{ gap: 14 }}>
            <Icon name="home-design-imgPlaneLanding" />
            <View style={{ flex: 1, gap: 3 }}>
              <Label size={11} muted>
                ĐIỂM ĐẾN
              </Label>
              <Label size={20} weight="semibold">
                {airport(s.to).city}
              </Label>
              <Label size={12} muted>
                {airport(s.to).name} · {s.to}
              </Label>
            </View>
          </Row>
        </Tap>
      </View>
      <SelectField
        label="Ngày khởi hành"
        value={dateLabel(s.date)}
        onPress={() => select("date")}
      />
      {s.type !== "one-way" && (
        <SelectField
          label="Ngày về"
          value={dateLabel(s.secondDate)}
          onPress={() => select("secondDate")}
        />
      )}
      <SelectField
        label="Hành khách"
        value={passengerSummary(s)}
        onPress={() => select("count")}
      />
      <Row
        style={{
          backgroundColor: c.surface,
          borderRadius: radius.lg,
          overflow: "hidden",
          padding: 14,
          minHeight: 90,
          alignItems: "flex-start",
        }}
      >
        <View style={{ flex: 1, gap: 5 }}>
          <Label size={10} color={c.primary} weight="semibold">
            THÀNH PHỐ ĐANG VẪY GỌI
          </Label>
          <Label size={17} weight="semibold">
            Hà Nội đang chờ bạn.
          </Label>
          <Label size={12} muted>
            Một lần tìm, muôn hành trình.
          </Label>
        </View>
        <Image
          source={require("../../../../assets/hoan-kiem-lake.jpg")}
          style={{ width: 72, aspectRatio: 1, borderRadius: 10, flexShrink: 0 }}
          contentFit="cover"
          accessibilityLabel="Hồ Gươm Hà Nội"
        />
      </Row>
      {error ? <Notice error>{error}</Notice> : null}
    </Screen>
  );
}
