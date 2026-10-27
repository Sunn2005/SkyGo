import { useState } from "react";
import { View } from "react-native";
import { Image } from "expo-image";
import { router } from "expo-router";
import { useBooking } from "@/context/booking-context";
import {
  airport,
  dateLabel,
  tripLabel,
  validDate,
  type TripType,
} from "@/data/booking";
import { assets } from "@/data/assets";
import {
  ActionBar,
  Divider,
  Icon,
  Label,
  Notice,
  Row,
  Screen,
  SelectField,
  Tap,
} from "@/components/ui";
import { colors as c, radius, space } from "@/theme";

export default function HomeScreen() {
  const b = useBooking();
  const { search: s, setSearch } = b;
  const [error, setError] = useState("");
  const select = (field: string) =>
    router.push({ pathname: "/options", params: { field } });
  const search = () => {
    const today = new Date();
    const todayISO = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
    if (s.from === s.to || (s.type === "multi-city" && s.to === s.onward))
      return setError("Điểm đi và điểm đến của mỗi chặng phải khác nhau.");
    if (!validDate(s.date) || s.date < todayISO)
      return setError("Vui lòng chọn ngày khởi hành từ hôm nay trở đi.");
    if (
      s.type !== "one-way" &&
      (!validDate(s.secondDate) || s.secondDate < s.date)
    )
      return setError("Ngày bay tiếp theo phải từ ngày khởi hành trở đi.");
    setError("");
    b.startSearch();
    router.push("/results");
  };
  return (
    <Screen
      title="SkyGo"
      home
      right={
        <Tap
          label="Thông tin SkyGo và vé đã đặt"
          onPress={() => router.push("/profile")}
        >
          <Icon name="home-design-imgCircleUserRound" size={21} />
        </Tap>
      }
      footer={<ActionBar title="Tìm chuyến bay" onPress={search} />}
    >
      <View style={{ gap: space.sm }}>
        <Label size={30} weight="bold">
          Bạn muốn đi đâu?
        </Label>
        <Label muted>Hành trình mới, khởi đầu thật dễ dàng.</Label>
      </View>
      <Row
        style={{
          gap: space.xs,
          backgroundColor: c.surface,
          padding: space.xs,
          borderRadius: radius.md,
        }}
      >
        {(["one-way", "round-trip", "multi-city"] as TripType[]).map((type) => (
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
      {s.type === "multi-city" && (
        <SelectField
          label="Điểm đến chặng 2"
          value={`${airport(s.onward).city} · ${s.onward}`}
          onPress={() => select("onward")}
        />
      )}
      {s.type !== "one-way" && (
        <SelectField
          label={s.type === "round-trip" ? "Ngày về" : "Ngày bay chặng 2"}
          value={dateLabel(s.secondDate)}
          onPress={() => select("secondDate")}
        />
      )}
      <Row style={{ alignItems: "flex-start" }}>
        <SelectField
          half
          label="Hành khách"
          value={`${s.count} người lớn`}
          onPress={() => select("count")}
        />
        <SelectField
          half
          label="Hạng ghế"
          value={s.cabin}
          onPress={() => select("cabin")}
        />
      </Row>
      <Row
        style={{
          backgroundColor: c.surface,
          borderRadius: radius.lg,
          overflow: "hidden",
          padding: 14,
          height: 108,
          alignItems: "flex-start",
        }}
      >
        <View style={{ flex: 1, gap: 5 }}>
          <Label size={10} color={c.primary} weight="semibold">
            THÀNH PHỐ ĐANG VẪY GỌI
          </Label>
          <Label size={17} weight="semibold">
            New York chờ bạn.
          </Label>
          <Label size={12} muted>
            Một tìm kiếm, vạn hành trình.
          </Label>
        </View>
        <Image
          source={assets["home-design-imgNewYorkSkyline"]}
          style={{ width: 100, height: 108, borderRadius: 10 }}
          contentFit="cover"
          accessibilityLabel="Đường chân trời New York"
        />
      </Row>
      {error ? <Notice error>{error}</Notice> : null}
    </Screen>
  );
}
