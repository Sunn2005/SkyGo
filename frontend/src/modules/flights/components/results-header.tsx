import { View } from "react-native";
import { router } from "expo-router";
import { Icon, Label, Row, Tap } from "@/shared/components/ui";
import { colors as c } from "@/shared/theme";
import { dateLabel } from "@/shared/utils/date";
import { passengerSummary } from "@/modules/booking/services/booking-rules";
import { airport } from "../data/airports";
import { HomeIcon } from "./home-icon";
import type { Leg, Search } from "../types";

export function ResultsHeader({
  leg,
  search,
  disabled,
}: {
  leg: Leg;
  search: Search;
  disabled: boolean;
}) {
  const city = (code: string) => airport(code).city.replace(/^TP\.\s*/, "");
  return (
    <Row
      style={{
        paddingHorizontal: 12,
        paddingVertical: 12,
        gap: 10,
        backgroundColor: c.tint,
      }}
    >
      <Tap
        label="Quay lại"
        disabled={disabled}
        onPress={() =>
          router.canGoBack() ? router.back() : router.replace("/")
        }
        style={{
          width: 38,
          height: 38,
          minHeight: 38,
          borderRadius: 19,
          alignItems: "center",
          backgroundColor: c.surface,
          borderWidth: 1,
          borderColor: c.line,
        }}
      >
        <Icon name="6-6734-imgArrowLeft" size={20} />
      </Tap>
      <Tap
        label="Thay đổi hành trình"
        disabled={disabled}
        onPress={() => router.push("/search")}
        style={{ flex: 1, gap: 4 }}
      >
        <Row style={{ gap: 6 }}>
          <Label
            size={15}
            weight="bold"
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.8}
            style={{ flexShrink: 1 }}
          >
            {city(leg.from)}
          </Label>
          <HomeIcon name="plane" color={c.primary} size={17} />
          <Label
            size={15}
            weight="bold"
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.8}
            style={{ flexShrink: 1 }}
          >
            {city(leg.to)}
          </Label>
          <Icon name="home-design-imgChevronDown" size={12} />
        </Row>
        <Label size={11} muted numberOfLines={2}>
          {dateLabel(leg.date)} · {passengerSummary(search)}
        </Label>
      </Tap>
      <Row
        style={{
          gap: 0,
          backgroundColor: c.surface,
          borderRadius: 22,
          borderWidth: 1,
          borderColor: c.line,
        }}
      >
        <Tap
          label="Hỗ trợ"
          onPress={() => router.push("/help")}
          style={{ width: 34, minHeight: 38, alignItems: "center" }}
        >
          <Icon name="6-6816-imgCircleHelp" size={19} />
        </Tap>
        <View style={{ height: 18, width: 1, backgroundColor: c.line }} />
        <Tap
          label="Trang chủ"
          disabled={disabled}
          onPress={() => router.replace("/")}
          style={{ width: 34, minHeight: 38, alignItems: "center" }}
        >
          <HomeIcon name="home" color={c.ink} size={19} />
        </Tap>
      </Row>
    </Row>
  );
}
