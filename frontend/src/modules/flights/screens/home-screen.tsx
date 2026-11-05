import { useRef, useState } from "react";
import { Modal, ScrollView, StyleSheet, View } from "react-native";
import { Image } from "expo-image";
import { Stack } from "expo-router/stack";
import { router } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useBooking } from "@/modules/booking/context/booking-context";
import type { AirportCode } from "@/modules/flights/types";
import { airport } from "@/modules/flights/data/airports";
import { getLegs } from "@/modules/flights/utils/trip";
import { Button, Icon, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { HomeIcon } from "@/modules/flights/components/home-icon";

const teal = "#00858C";
const ink = "#112B43";
const muted = "#62717D";
const routes: {
  from: AirportCode;
  to: AirportCode;
  title: string;
  price: string;
}[] = [
  { from: "SGN", to: "HAN", title: "Sài Gòn → Hà Nội", price: "1.494.000 đ" },
  { from: "HAN", to: "DAD", title: "Hà Nội → Đà Nẵng", price: "1.090.000 đ" },
  { from: "SGN", to: "PQC", title: "Sài Gòn → Phú Quốc", price: "890.000 đ" },
];
const city = (code: AirportCode) =>
  code === "SGN" ? "Sài Gòn" : airport(code).city;
const shortDate = (iso: string) => iso.split("-").reverse().join("/");

export default function HomeScreen() {
  const inset = useSafeAreaInsets();
  const b = useBooking();
  const [showAll, setShowAll] = useState(false);
  const [notifications, setNotifications] = useState(false);
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState("");
  const lock = useRef(false);
  const upcoming = b.history
    .filter((item) => item.status === "confirmed")
    .flatMap((booking) =>
      getLegs(booking.receipt.search).flatMap((leg, index) => {
        const flight = booking.receipt.selected[index];
        const departure = flight
          ? new Date(`${leg.date}T${flight.departure}:00`).getTime()
          : NaN;
        return departure > Date.now()
          ? [{ booking, leg, flight, departure }]
          : [];
      }),
    )
    .sort((a, z) => a.departure - z.departure)[0];
  const openTicket = async () => {
    if (!upcoming || lock.current) return;
    lock.current = true;
    setOpening(true);
    setError("");
    try {
      await b.openBooking(upcoming.booking.id);
      setNotifications(false);
      router.push("/booking");
    } catch {
      setError("Chưa thể mở vé. Vui lòng thử lại.");
    } finally {
      lock.current = false;
      setOpening(false);
    }
  };
  const openRoute = (route: (typeof routes)[number]) => {
    b.setSearch((previous) => ({
      ...previous,
      from: route.from,
      to: route.to,
      type: "one-way",
    }));
    router.push("/search");
  };
  const featured = routes[0];
  return (
    <View style={[styles.screen, { paddingTop: inset.top }]}>
      <Stack.Screen options={{ headerShown: false }} />
      <ScrollView
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <Row style={{ gap: 9 }}>
          <Icon name="home-design-imgPlane" size={31} />
          <Label size={23} weight="bold" color={ink} style={{ flex: 1 }}>
            SkyGo
          </Label>
          <Tap
            label="Thông báo"
            onPress={() => setNotifications(true)}
            style={{ width: 44, alignItems: "flex-end" }}
          >
            <Icon name="6-6622-imgBell" size={26} />
          </Tap>
        </Row>
        <View style={styles.hero}>
          <Label
            size={25}
            weight="bold"
            color={ink}
            style={{ letterSpacing: -0.8 }}
          >
            Hành trình mới đang chờ bạn
          </Label>
          <Label size={14} color={muted}>
            Khám phá điểm đến tiếp theo cùng SkyGo
          </Label>
        </View>
        <Tap
          label="Tìm chuyến bay, bạn muốn bay đến đâu?"
          onPress={() => router.push("/search")}
          style={styles.search}
        >
          <View style={styles.searchIcon}>
            <HomeIcon name="search" color="white" size={25} />
          </View>
          <Label size={16} weight="semibold" color={ink} style={{ flex: 1 }}>
            Bạn muốn bay đến đâu?
          </Label>
          <HomeIcon name="arrow" color={teal} size={24} />
        </Tap>
        <View style={styles.section}>
          <Label size={20} weight="bold" color={ink}>
            Chuyến bay sắp tới
          </Label>
          <View style={[styles.card, styles.upcoming]}>
            {upcoming ? (
              <>
                <View style={styles.confirmed}>
                  <Label size={12} color="#17632C" weight="semibold">
                    ✓ Đã xác nhận
                  </Label>
                </View>
                <Row style={{ gap: 12, alignItems: "center" }}>
                  <View>
                    <Label size={29} weight="bold" color={ink}>
                      {upcoming.leg.from}
                    </Label>
                    <Label size={14} color={muted}>
                      {city(upcoming.leg.from)}
                    </Label>
                  </View>
                  <Row style={{ flex: 1, gap: 4 }}>
                    <View style={styles.flightLine} />
                    <View style={{ transform: [{ rotate: "45deg" }] }}>
                      <Icon name="home-design-imgPlane" size={24} />
                    </View>
                    <View style={styles.flightLine} />
                  </Row>
                  <View style={{ alignItems: "flex-end" }}>
                    <Label size={29} weight="bold" color={ink}>
                      {upcoming.leg.to}
                    </Label>
                    <Label size={14} color={muted}>
                      {city(upcoming.leg.to)}
                    </Label>
                  </View>
                </Row>
                <Row style={{ gap: 8, flexWrap: "wrap" }}>
                  <HomeIcon name="calendar" size={17} color={muted} />
                  <Label size={12}>{shortDate(upcoming.leg.date)}</Label>
                  <View
                    style={{
                      width: 1,
                      height: 16,
                      backgroundColor: "#D2DCE2",
                      marginHorizontal: 3,
                    }}
                  />
                  <HomeIcon name="clock" size={17} color={muted} />
                  <Label size={12}>
                    {upcoming.flight.departure} – {upcoming.flight.arrival}
                  </Label>
                  <Tap
                    label="Xem vé chuyến bay sắp tới"
                    disabled={opening}
                    onPress={() => void openTicket()}
                    style={{ marginLeft: "auto" }}
                  >
                    <Row style={{ gap: 3 }}>
                      <Label size={13} color={teal} weight="semibold">
                        {opening ? "Đang mở…" : "Xem vé"}
                      </Label>
                      <Icon name="6-6622-imgChevronRight" size={16} />
                    </Row>
                  </Tap>
                </Row>
              </>
            ) : (
              <>
                <Label size={16} weight="semibold">
                  {b.historyLoading
                    ? "Đang tải chuyến bay…"
                    : "Hành trình tiếp theo đang chờ bạn"}
                </Label>
                <Label size={13} color={muted}>
                  {b.historyError ||
                    "Chuyến bay đã xác nhận sẽ xuất hiện tại đây."}
                </Label>
                {b.historyError ? (
                  <Button
                    secondary
                    title="Tải lại"
                    onPress={() => void b.reloadHistory()}
                  />
                ) : (
                  <Tap onPress={() => router.push("/search")}>
                    <Label color={teal} size={14} weight="semibold">
                      Khám phá chuyến bay →
                    </Label>
                  </Tap>
                )}
              </>
            )}
          </View>
          {!!error && <Notice error>{error}</Notice>}
        </View>
        <View style={styles.section}>
          <Row style={{ justifyContent: "space-between", gap: 6 }}>
            <Label size={20} weight="bold" color={ink}>
              Gợi ý hành trình
            </Label>
            <Tap
              label={showAll ? "Thu gọn các tuyến bay" : "Xem tất cả tuyến bay"}
              onPress={() => setShowAll((v) => !v)}
            >
              <Row style={{ gap: 3 }}>
                <Label size={13} weight="semibold" color={teal}>
                  {showAll ? "Thu gọn" : "Xem tất cả"}
                </Label>
                <Icon name="6-6622-imgChevronRight" size={16} />
              </Row>
            </Tap>
          </Row>
          <View style={styles.card}>
            <Image
              source={require("../../../../assets/hoan-kiem-lake.jpg")}
              style={styles.photo}
              contentFit="cover"
              accessibilityLabel="Tháp Rùa trên Hồ Gươm, Hà Nội"
            />
            <View style={styles.featureBody}>
              <Label size={19} weight="bold" color={ink}>
                {featured.title}
              </Label>
              <Row
                style={{
                  justifyContent: "space-between",
                  gap: 8,
                  alignItems: "flex-end",
                  flexWrap: "wrap",
                }}
              >
                <View style={{ gap: 1 }}>
                  <Label size={13} color={muted}>
                    Một chiều từ
                  </Label>
                  <Label size={24} weight="bold" color={teal}>
                    {featured.price}
                  </Label>
                </View>
                <Tap
                  label={`Chọn hành trình, ${featured.title}`}
                  onPress={() => openRoute(featured)}
                  style={styles.primaryButton}
                >
                  <Label size={13} weight="semibold" color="white">
                    Chọn hành trình
                  </Label>
                </Tap>
              </Row>
            </View>
          </View>
          {(showAll ? routes.slice(1) : routes.slice(1, 2)).map((route) => (
            <Tap
              key={route.to}
              label={`Chọn hành trình, ${route.title}`}
              onPress={() => openRoute(route)}
              style={[styles.card, styles.smallCard]}
            >
              {route.to === "DAD" ? (
                <Image
                  source={require("../../../../assets/da-nang-beach.jpg")}
                  style={styles.thumbnail}
                  contentFit="cover"
                  accessibilityLabel="Bãi biển Đà Nẵng"
                />
              ) : (
                <View
                  style={[
                    styles.thumbnail,
                    {
                      backgroundColor: "#E3F4F4",
                      alignItems: "center",
                      justifyContent: "center",
                    },
                  ]}
                >
                  <Icon name="home-design-imgPlane" size={28} />
                </View>
              )}
              <View style={{ flex: 1, gap: 3 }}>
                <Label size={16} weight="semibold" color={ink}>
                  {route.title}
                </Label>
                <Label size={13} color={muted}>
                  Giá từ{" "}
                  <Label size={14} color={teal} weight="semibold">
                    {route.price}
                  </Label>
                </Label>
              </View>
              <HomeIcon name="arrow" color={teal} size={23} />
            </Tap>
          ))}
          <Label
            size={11}
            color={muted}
            style={{ marginTop: -7, paddingHorizontal: 4 }}
          >
            Giá tham khảo cho 1 người lớn
          </Label>
        </View>
      </ScrollView>
      <Modal
        visible={notifications}
        transparent
        animationType="fade"
        onRequestClose={() => setNotifications(false)}
      >
        <View style={styles.overlay}>
          <View
            style={[
              styles.card,
              { padding: 20, gap: 14, width: "100%", maxWidth: 420 },
            ]}
          >
            <Label size={20} weight="bold">
              Thông báo
            </Label>
            <Label size={14} color={muted}>
              {upcoming
                ? `Bạn có chuyến bay ${upcoming.leg.from} → ${upcoming.leg.to} vào ${shortDate(upcoming.leg.date)} lúc ${upcoming.flight.departure}.`
                : "Bạn chưa có thông báo mới."}
            </Label>
            {upcoming && (
              <Button
                title="Xem vé"
                loading={opening}
                onPress={() => void openTicket()}
              />
            )}
            {!!error && <Notice error>{error}</Notice>}
            <Button
              secondary
              title="Đóng"
              onPress={() => setNotifications(false)}
            />
          </View>
        </View>
      </Modal>
    </View>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F0F6F8" },
  content: { paddingHorizontal: 16, paddingTop: 18, paddingBottom: 14 },
  hero: { marginTop: 20, gap: 2 },
  search: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    backgroundColor: "white",
    borderRadius: 16,
    paddingHorizontal: 13,
    minHeight: 58,
    marginTop: 16,
    boxShadow: "0 4px 16px rgba(16,42,67,0.04)",
  },
  searchIcon: {
    backgroundColor: teal,
    borderRadius: 10,
    width: 35,
    height: 35,
    alignItems: "center",
    justifyContent: "center",
  },
  section: { marginTop: 18, gap: 11 },
  card: {
    borderRadius: 16,
    backgroundColor: "white",
    overflow: "hidden",
    boxShadow: "0 2px 5px rgba(16,42,67,0.05)",
  },
  upcoming: { paddingHorizontal: 16, paddingTop: 12, paddingBottom: 5, gap: 5 },
  confirmed: {
    alignSelf: "flex-start",
    backgroundColor: "#E2F6EB",
    borderRadius: 20,
    paddingHorizontal: 10,
    paddingVertical: 3,
    marginBottom: 2,
  },
  flightLine: { height: 1, backgroundColor: "#CBD6DE", flex: 1 },
  photo: { width: "100%", aspectRatio: 3.05 },
  featureBody: {
    paddingHorizontal: 13,
    paddingTop: 5,
    paddingBottom: 12,
    gap: 3,
  },
  primaryButton: {
    backgroundColor: teal,
    borderRadius: 10,
    paddingHorizontal: 13,
    minHeight: 34,
    alignItems: "center",
  },
  smallCard: {
    flexDirection: "row",
    alignItems: "center",
    padding: 9,
    gap: 11,
  },
  thumbnail: { width: 99, height: 48, borderRadius: 10 },
  overlay: {
    flex: 1,
    backgroundColor: "#102A4380",
    justifyContent: "center",
    alignItems: "center",
    padding: 24,
  },
});
