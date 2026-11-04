import { View } from "react-native";
import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { airport } from "@/modules/flights/data/airports";
import { flights } from "@/modules/flights/data/flights";
import { money } from "@/shared/utils/format";
import {
  Badge,
  Button,
  Card,
  Divider,
  Icon,
  Label,
  Row,
  Tap,
} from "@/shared/components/ui";
import { FlightTimes } from "@/modules/flights/components/flight-times";
import { Screen } from "@/shared/components/screen";
import { TripSummary } from "@/modules/booking/components/trip-summary";
import { colors as c, radius, space } from "@/shared/theme";

export default function ResultsScreen() {
  const b = useBooking();
  const leg = b.legs[b.legIndex] ?? b.legs[0];
  const results = flights
    .filter(
      (f) =>
        (b.airline === "all" || f.airline === b.airline) &&
        (!b.nonstop || f.stops === 0) &&
        b.priceFor(f) <= b.maxPrice &&
        (!b.morning || Number(f.departure.slice(0, 2)) < 12),
    )
    .sort((a, z) =>
      b.sort === "cheapest"
        ? b.priceFor(a) - b.priceFor(z)
        : a.stops - z.stops || b.priceFor(a) - b.priceFor(z),
    );
  const choose = (flight: (typeof flights)[number]) => {
    b.setCandidate(flight);
    router.push("/detail");
  };
  return (
    <Screen
      title={`${airport(leg.from).city} → ${airport(leg.to).city}`}
      right={
        <Tap
          label="Các chuyến bay đã lưu"
          onPress={() => router.push("/saved")}
        >
          <Icon name="6-6622-imgBell" size={21} />
        </Tap>
      }
    >
      {b.legs.length > 1 && (
        <Label color={c.primary} weight="semibold" size={13}>
          CHẶNG {b.legIndex + 1} / {b.legs.length}
        </Label>
      )}
      <TripSummary leg={leg} />
      <Row style={{ gap: space.sm }}>
        {[
          ["best", "Tốt nhất"],
          ["cheapest", "Giá thấp nhất"],
        ].map(([key, label]) => (
          <Tap
            key={key}
            selected={b.sort === key}
            onPress={() => b.setSort(key as "best" | "cheapest")}
            style={{
              flex: 1,
              borderWidth: 1,
              borderColor: b.sort === key ? c.primary : c.line,
              backgroundColor: b.sort === key ? c.tint : c.white,
              borderRadius: radius.sm,
              alignItems: "center",
              paddingVertical: space.sm,
            }}
          >
            <Label size={12} color={b.sort === key ? c.primary : c.ink}>
              {label}
            </Label>
          </Tap>
        ))}
        <Tap
          onPress={() => router.push("/filters")}
          style={{
            flex: 1,
            borderWidth: 1,
            borderColor: c.line,
            borderRadius: radius.sm,
            alignItems: "center",
            paddingVertical: space.sm,
          }}
        >
          <Row style={{ gap: 6 }}>
            <Icon name="6-6622-imgSlidersHorizontal" size={14} />
            <Label size={12}>Bộ lọc</Label>
          </Row>
        </Tap>
      </Row>
      <Label size={12} muted>
        {results.length} chuyến bay · VND · Đã gồm thuế phí
      </Label>
      {results.map((flight, i) => (
        <Card
          key={flight.id}
          selected={i === 0}
          style={{ padding: 14, gap: 10 }}
        >
          <Row>
            <View
              style={{
                padding: 7,
                borderRadius: radius.sm,
                backgroundColor: c.tint,
              }}
            >
              <Icon name="6-6622-imgPlane1" size={17} />
            </View>
            <Tap
              onPress={() => choose(flight)}
              style={{ flex: 1, minHeight: 36 }}
            >
              <Label size={14} weight="semibold">
                {flight.airline}
              </Label>
              <Label size={11} muted>
                {flight.number} · {b.search.cabin}
              </Label>
            </Tap>
            {i === 0 ? (
              <Badge>Gợi ý tốt nhất</Badge>
            ) : (
              <Tap
                label={
                  b.favorites.includes(flight.id)
                    ? "Bỏ lưu chuyến bay"
                    : "Lưu chuyến bay"
                }
                selected={b.favorites.includes(flight.id)}
                onPress={() =>
                  b.setFavorites((ids) =>
                    ids.includes(flight.id)
                      ? ids.filter((id) => id !== flight.id)
                      : [...ids, flight.id],
                  )
                }
                style={{ paddingHorizontal: space.sm }}
              >
                {b.favorites.includes(flight.id) ? (
                  <Label color={c.primary} weight="semibold" size={11}>
                    Đã lưu
                  </Label>
                ) : (
                  <Icon name="6-6622-imgHeart" size={19} />
                )}
              </Tap>
            )}
          </Row>
          <Tap
            label={`Chọn ${flight.airline} ${flight.departure}, ${money(b.priceFor(flight))}`}
            onPress={() => choose(flight)}
          >
            <FlightTimes flight={flight} leg={leg} plane="6-6622-imgPlane2" />
          </Tap>
          <Divider />
          <Tap onPress={() => choose(flight)}>
            <Row>
              <Label muted size={12} style={{ flex: 1 }}>
                {b.search.count} người · Mỗi người
              </Label>
              <Label selectable size={29} weight="bold">
                {money(b.priceFor(flight))}
              </Label>
              <Icon name="6-6622-imgChevronRight" size={18} />
            </Row>
          </Tap>
        </Card>
      ))}
      {!results.length && (
        <View style={{ paddingVertical: space.xxl, gap: space.lg }}>
          <Label size={20} weight="semibold">
            Chưa có chuyến bay phù hợp
          </Label>
          <Label muted>Thử bỏ bớt bộ lọc để xem thêm lựa chọn.</Label>
          <Button
            title="Xóa bộ lọc"
            onPress={() => {
              b.setAirline("all");
              b.setNonstop(false);
              b.setMaxPrice(10000000);
              b.setMorning(false);
            }}
          />
        </View>
      )}
      <Label size={11} muted>
        Chuyến bay và giá vé là dữ liệu mẫu để trải nghiệm ứng dụng.
      </Label>
    </Screen>
  );
}
