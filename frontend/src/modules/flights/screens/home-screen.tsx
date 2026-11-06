import { useCallback, useState } from "react";
import { Modal, ScrollView, StyleSheet, View } from "react-native";
import { Image } from "expo-image";
import { Stack } from "expo-router/stack";
import { router, useFocusEffect } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useBooking } from "@/modules/booking/context/booking-context";
import type { AirportCode } from "@/modules/flights/types";
import { rememberAirports } from "@/modules/flights/data/airports";
import { useAccount } from "@/modules/account/context/account-context";
import { API_BASE_URL } from "@/shared/api/client";
import {
  loadHome,
  type HomeData,
  type HomeRoute,
} from "../services/home-service";
import { Button, Icon, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { HomeIcon } from "@/modules/flights/components/home-icon";

const teal = "#00858C";
const ink = "#112B43";
const muted = "#62717D";
const shortDate = (iso: string) => iso.split("-").reverse().join("/");

export default function HomeScreen() {
  const inset = useSafeAreaInsets();
  const b = useBooking();
  const { session } = useAccount();
  const [notifications, setNotifications] = useState(false);
  const [error, setError] = useState("");
  const [data, setData] = useState<HomeData | null>(null);
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  useFocusEffect(
    useCallback(() => {
      // Retry creates a request even when the token is unchanged.
      void reload;
      const controller = new AbortController();
      setLoading(true);
      setData(null);
      setError("");
      loadHome(session?.accessToken, controller.signal)
        .then((result) => {
          if (!controller.signal.aborted) setData(result);
        })
        .catch((reason: unknown) => {
          if (!controller.signal.aborted)
            setError(
              reason instanceof Error
                ? reason.message
                : "Không tải được trang chủ.",
            );
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
      return () => controller.abort();
    }, [session?.accessToken, reload]),
  );
  const routes = data
    ? [
        ...(data.featuredRoute ? [data.featuredRoute] : []),
        ...data.otherRoutes,
      ].map((route) => ({
        ...route,
        from: route.origin.code,
        to: route.destination.code,
        title: `${route.origin.city} → ${route.destination.city}`,
        price: `${route.priceFrom.amount.toLocaleString("vi-VN")} đ`,
      }))
    : [];
  const upcoming = data?.upcomingFlight
    ? {
        leg: {
          from: data.upcomingFlight.origin.code,
          to: data.upcomingFlight.destination.code,
          date: data.upcomingFlight.departureAt.slice(0, 10),
        },
        flight: {
          departure: data.upcomingFlight.departureAt.slice(11, 16),
          arrival: data.upcomingFlight.arrivalAt.slice(11, 16),
        },
        bookingCode: data.upcomingFlight.bookingCode,
        originCity: data.upcomingFlight.origin.city,
        destinationCity: data.upcomingFlight.destination.city,
      }
    : null;
  const openRoute = (route: HomeRoute) => {
    rememberAirports(
      [route.origin, route.destination].map((item) => ({
        ...item,
        name: item.city,
        countryName: "",
      })),
    );
    b.setSearch((previous) => ({
      ...previous,
      from: route.origin.code as AirportCode,
      to: route.destination.code as AirportCode,
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
                      {upcoming.originCity}
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
                      {upcoming.destinationCity}
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
                  <Label size={12}>Mã đặt chỗ: {upcoming.bookingCode}</Label>
                </Row>
              </>
            ) : (
              <>
                <Label size={16} weight="semibold">
                  {loading
                    ? "Đang tải chuyến bay…"
                    : "Hành trình tiếp theo đang chờ bạn"}
                </Label>
                <Label size={13} color={muted}>
                  {error || "Chuyến bay đã xác nhận sẽ xuất hiện tại đây."}
                </Label>
                {error ? (
                  <Button
                    secondary
                    title="Tải lại"
                    onPress={() => setReload((value) => value + 1)}
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
          <Label size={20} weight="bold" color={ink}>
            {data?.routeSectionTitle ?? "Gợi ý hành trình"}
          </Label>
          {featured ? (
            <View style={styles.card}>
              {featured.imageUrl && (
                <Image
                  source={{ uri: `${API_BASE_URL}${featured.imageUrl}` }}
                  style={styles.photo}
                  contentFit="cover"
                  accessibilityLabel={featured.destination.city}
                />
              )}
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
          ) : (
            <Notice>
              {loading
                ? "Đang tải tuyến bay…"
                : error
                  ? "Chưa tải được tuyến bay."
                  : "Hiện chưa có tuyến bay đang mở bán."}
            </Notice>
          )}
          {routes.slice(1).map((route) => (
            <Tap
              key={route.routeId}
              label={`Chọn hành trình, ${route.title}`}
              onPress={() => openRoute(route)}
              style={[styles.card, styles.smallCard]}
            >
              {route.imageUrl ? (
                <Image
                  source={{ uri: `${API_BASE_URL}${route.imageUrl}` }}
                  style={styles.thumbnail}
                  contentFit="cover"
                  accessibilityLabel={route.destination.city}
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
            Giá cho 1 người lớn, một chiều, đã gồm VAT
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
