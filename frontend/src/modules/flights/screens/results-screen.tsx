import { flightPrice } from "@/modules/booking/services/booking-rules";
import { useCallback, useEffect, useRef, useState } from "react";
import { Modal, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { useAccount } from "@/modules/account/context/account-context";
import { useBooking } from "@/modules/booking/context/booking-context";
import { flights } from "@/modules/flights/data/flights";
import { money } from "@/shared/utils/format";
import {
  Badge,
  Button,
  Card,
  Divider,
  Icon,
  Label,
  Notice,
  Row,
  Tap,
} from "@/shared/components/ui";
import { FlightTimes } from "@/modules/flights/components/flight-times";
import { Screen } from "@/shared/components/screen";
import { TripSummary } from "@/modules/booking/components/trip-summary";
import { colors as c, radius, space } from "@/shared/theme";

export default function ResultsScreen() {
  const b = useBooking();
  const { session } = useAccount();
  const { resumeSelection } = useLocalSearchParams<{
    resumeSelection?: string;
  }>();
  const [askLogin, setAskLogin] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selecting = useRef(false);
  const resumed = useRef(false);
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
  const choose = useCallback(
    async (flight: (typeof flights)[number]) => {
      if (selecting.current) return;
      b.setCandidate(flight);
      if (!session) {
        setAskLogin(true);
        return;
      }
      selecting.current = true;
      setBusy(true);
      setError("");
      try {
        await b.selectFlight(flight);
        if (b.legIndex < b.legs.length - 1) {
          b.setLegIndex(b.legIndex + 1);
          router.replace("/results");
        } else router.push("/traveller");
      } catch (cause) {
        setError(
          cause instanceof Error
            ? cause.message
            : "Chưa thể giữ chỗ. Vui lòng chọn lại chuyến bay.",
        );
      } finally {
        selecting.current = false;
        setBusy(false);
      }
    },
    [b, session],
  );
  useEffect(() => {
    if (resumeSelection !== "1") {
      resumed.current = false;
      return;
    }
    if (session && !resumed.current) {
      resumed.current = true;
      router.setParams({ resumeSelection: undefined });
      void choose(b.candidate);
    }
  }, [resumeSelection, session, choose, b.candidate]);
  return (
    <Screen
      design
      title={b.legIndex ? "Chuyến về" : "Chuyến đi"}
      right={
        <Tap
          label="Các chuyến bay đã lưu"
          onPress={() => router.push("/saved")}
        >
          <Icon name="6-6622-imgBell" size={21} />
        </Tap>
      }
    >
      <Modal
        visible={askLogin}
        transparent
        animationType="fade"
        onRequestClose={() => setAskLogin(false)}
      >
        <View
          style={{
            flex: 1,
            backgroundColor: "#102A4380",
            justifyContent: "center",
            padding: 24,
          }}
        >
          <Card>
            <Label size={22} weight="bold">
              Đăng nhập để đặt vé
            </Label>
            <Label>
              Lựa chọn của bạn được lưu tạm. Giá và chỗ sẽ được kiểm tra lại sau
              khi đăng nhập.
            </Label>
            <Button
              title="Đăng nhập để tiếp tục"
              onPress={() => {
                setAskLogin(false);
                router.push({
                  pathname: "/login",
                  params: { returnTo: "flight-selection" },
                });
              }}
            />
            <Button
              title="Để sau"
              secondary
              onPress={() => setAskLogin(false)}
            />
          </Card>
        </View>
      </Modal>
      {!!error && <Notice error>{error}</Notice>}
      {busy && <Label color={c.primary}>Đang kiểm tra chỗ và giá…</Label>}
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
              disabled={busy}
              onPress={() => void choose(flight)}
              style={{ flex: 1, minHeight: 36 }}
            >
              <Label size={13} weight="semibold">
                {flight.airline}
              </Label>
              <Label size={11} muted>
                {flight.number}
              </Label>
            </Tap>
            {i === 0 ? (
              <Badge>Đề xuất</Badge>
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
            disabled={busy}
            onPress={() => void choose(flight)}
          >
            <FlightTimes flight={flight} leg={leg} plane="6-6622-imgPlane2" />
          </Tap>
          <Divider />
          <Label size={12} muted>
            Chọn hạng vé cho {b.legIndex === 0 ? "chặng đi" : "chặng về"}
          </Label>
          <Row>
            {(["Phổ thông", "Thương gia"] as const).map((cabin) => (
              <Tap
                key={cabin}
                label={`Chọn ${flight.number} ${cabin}`}
                disabled={busy}
                onPress={() => void choose({ ...flight, cabin })}
                style={{
                  flex: 1,
                  borderWidth: 1,
                  borderColor: c.primary,
                  borderRadius: 10,
                  backgroundColor: c.tint,
                  padding: 10,
                }}
              >
                <Label size={12}>{cabin}</Label>
                <Label size={16} weight="bold">
                  {money(flightPrice(flight, cabin))}
                </Label>
              </Tap>
            ))}
          </Row>
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
    </Screen>
  );
}
