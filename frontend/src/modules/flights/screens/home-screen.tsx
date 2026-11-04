import { useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import { Image } from "expo-image";
import { Stack } from "expo-router/stack";
import { router } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useBooking } from "@/modules/booking/context/booking-context";
import type { AirportCode } from "@/modules/flights/types";
import { Icon, Label, Row, Tap } from "@/shared/components/ui";
import { HomeIcon } from "@/modules/flights/components/home-icon";

const teal = "#007D77";
const ink = "#09151D";
const muted = "#99A1AE";
const routes: {
  from: AirportCode;
  to: AirportCode;
  title: string;
  price: string;
}[] = [
  { from: "SGN", to: "HAN", title: "Sài Gòn → Hà Nội", price: "1.494.000 ₫" },
  { from: "HAN", to: "DAD", title: "Hà Nội → Đà Nẵng", price: "1.090.000 ₫" },
  { from: "SGN", to: "PQC", title: "Sài Gòn → Phú Quốc", price: "890.000 ₫" },
];

export default function HomeScreen() {
  const inset = useSafeAreaInsets();
  const { setSearch } = useBooking();
  const [showAll, setShowAll] = useState(false);
  const openRoute = (route: (typeof routes)[number]) => {
    setSearch((previous) => ({
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
        <Row style={{ gap: 10 }}>
          <Icon name="home-design-imgPlane" size={21} />
          <Label size={23} weight="bold" color="#104A48">
            SkyGo
          </Label>
        </Row>
        <View style={styles.hero}>
          <Label
            size={28}
            weight="bold"
            color={ink}
            style={{ letterSpacing: -1.1 }}
          >
            Đi đâu cũng có SkyGo.
          </Label>
          <Label size={15} color="#6E7B8B">
            Khám phá những hành trình được yêu thích.
          </Label>
        </View>
        <Tap
          label="Tìm chuyến bay, bạn muốn bay đến đâu?"
          onPress={() => router.push("/search")}
          style={styles.search}
        >
          <HomeIcon name="search" color={teal} size={23} />
          <Label size={15} color="#9BAABD" style={{ flex: 1 }}>
            Bạn muốn bay đến đâu?
          </Label>
          <HomeIcon name="arrow" color={teal} size={23} />
        </Tap>
        <View style={styles.section}>
          <Row style={styles.sectionHeading}>
            <Label
              size={18}
              weight="bold"
              color={ink}
              style={{ flex: 1, letterSpacing: -0.6 }}
            >
              Tuyến bay được chọn nhiều
            </Label>
            <View style={styles.month}>
              <Label size={13} color={teal}>
                Tháng {new Date().getMonth() + 1}
              </Label>
            </View>
          </Row>
          <View style={styles.card}>
            <View>
              <Image
                source={require("../../../../assets/hoan-kiem-lake.jpg")}
                style={styles.photo}
                contentFit="cover"
                accessibilityLabel="Tháp Rùa trên Hồ Gươm, Hà Nội"
              />
              <View style={styles.badge}>
                <HomeIcon name="trend" color={teal} size={17} />
                <Label size={13} weight="medium" color="#145E59">
                  Hành trình yêu thích
                </Label>
              </View>
            </View>
            <View style={styles.featureBody}>
              <View style={{ gap: 3 }}>
                <Label size={20} weight="bold" color={ink}>
                  {featured.title}
                </Label>
                <Label size={13} color={muted}>
                  {featured.from} → {featured.to}
                </Label>
              </View>
              <View style={styles.divider} />
              <Row
                style={{
                  justifyContent: "space-between",
                  gap: 10,
                  flexWrap: "wrap",
                }}
              >
                <View style={{ gap: 2 }}>
                  <Label size={13} color={muted}>
                    Một chiều từ
                  </Label>
                  <Label size={20} weight="bold" color={ink}>
                    {featured.price}
                  </Label>
                </View>
                <Tap
                  onPress={() => openRoute(featured)}
                  label={`Bay ngay, ${featured.title}`}
                  style={styles.primaryButton}
                >
                  <Label size={13} weight="semibold" color="#FFFFFF">
                    Bay ngay cùng SkyGo
                  </Label>
                </Tap>
              </Row>
            </View>
          </View>
        </View>
        <View style={styles.section}>
          <Row style={styles.sectionHeading}>
            <Label
              size={19}
              weight="bold"
              color={ink}
              style={{ flex: 1, letterSpacing: -0.6 }}
            >
              Cũng được yêu thích
            </Label>
            <Tap
              onPress={() => setShowAll((value) => !value)}
              label={showAll ? "Thu gọn các tuyến bay" : "Xem tất cả tuyến bay"}
            >
              <Label size={13} color={teal}>
                {showAll ? "Thu gọn" : "Xem tất cả"}
              </Label>
            </Tap>
          </Row>
          {(showAll ? routes : routes.slice(1)).map((route) => (
            <View
              key={`${route.from}-${route.to}`}
              style={[styles.card, styles.smallCard]}
            >
              <Row
                style={{
                  justifyContent: "space-between",
                  gap: 8,
                  flexWrap: "wrap",
                }}
              >
                <View style={{ gap: 3 }}>
                  <Label size={17} weight="semibold" color={ink}>
                    {route.title}
                  </Label>
                  <Label size={13} color={muted}>
                    {route.from} → {route.to}
                  </Label>
                </View>
                <View style={{ alignItems: "flex-end", gap: 3 }}>
                  <Label size={12} color={muted}>
                    Giá từ
                  </Label>
                  <Label size={18} weight="bold" color={ink}>
                    {route.price}
                  </Label>
                </View>
              </Row>
              <Tap
                onPress={() => openRoute(route)}
                label={`Bay ngay, ${route.title}`}
                style={styles.secondaryButton}
              >
                <Label size={13} weight="medium" color={teal}>
                  Bay ngay cùng SkyGo
                </Label>
              </Tap>
            </View>
          ))}
        </View>
      </ScrollView>
      <View
        style={[
          styles.navigation,
          { paddingBottom: Math.max(inset.bottom, 10) },
        ]}
      >
        {(
          [
            { title: "Trang chủ", icon: "home", path: "/" },
            { title: "Mua vé", icon: "ticket", path: "/search" },
            { title: "Lịch sử", icon: "clock", path: "/bookings" },
            { title: "Hồ sơ", icon: "user", path: "/profile" },
          ] as const
        ).map((tab, index) => (
          <Tap
            key={tab.path}
            label={tab.title}
            selected={index === 0}
            onPress={() => {
              if (index !== 0) router.push(tab.path);
            }}
            style={styles.tab}
          >
            <View
              style={[
                styles.tabIcon,
                index === 0 && { backgroundColor: "#E5F3F2" },
              ]}
            >
              <HomeIcon
                name={tab.icon}
                size={21}
                color={index === 0 ? teal : "#75869C"}
              />
            </View>
            <Label
              size={12}
              color={index === 0 ? teal : "#75869C"}
              weight={index === 0 ? "semibold" : "regular"}
            >
              {tab.title}
            </Label>
          </Tap>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F4F6F8" },
  content: { paddingHorizontal: 22, paddingTop: 18, paddingBottom: 38 },
  hero: { marginTop: 17, gap: 10 },
  search: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    backgroundColor: "white",
    borderRadius: 17,
    paddingHorizontal: 18,
    minHeight: 52,
    marginTop: 24,
    boxShadow: "0 2px 2px rgba(16,42,67,0.04)",
  },
  section: { marginTop: 30, gap: 16 },
  sectionHeading: { justifyContent: "space-between", gap: 8, minHeight: 30 },
  month: {
    borderRadius: 20,
    paddingHorizontal: 13,
    paddingVertical: 5,
    backgroundColor: "#EFFDF9",
  },
  card: {
    borderRadius: 17,
    backgroundColor: "white",
    overflow: "hidden",
    boxShadow: "0 2px 2px rgba(16,42,67,0.04)",
  },
  photo: { width: "100%", aspectRatio: 2.28 },
  badge: {
    position: "absolute",
    top: 13,
    left: 13,
    flexDirection: "row",
    alignItems: "center",
    gap: 7,
    backgroundColor: "#FFFFFFF5",
    paddingHorizontal: 13,
    paddingVertical: 4,
    borderRadius: 20,
  },
  featureBody: { padding: 17, gap: 13 },
  divider: { height: 1, backgroundColor: "#F0F1F4" },
  primaryButton: {
    backgroundColor: teal,
    borderRadius: 12,
    paddingHorizontal: 16,
    minHeight: 38,
    alignItems: "center",
  },
  smallCard: { padding: 17, gap: 14 },
  secondaryButton: {
    backgroundColor: "#E5F3F2",
    borderRadius: 12,
    minHeight: 38,
    alignItems: "center",
  },
  navigation: {
    flexDirection: "row",
    backgroundColor: "#FFFFFF",
    borderTopWidth: 1,
    borderTopColor: "#E4EAF0",
    paddingTop: 8,
    paddingHorizontal: 17,
  },
  tab: { flex: 1, alignItems: "center", gap: 3 },
  tabIcon: {
    width: 50,
    height: 29,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
  },
});
