import { useEffect, useRef, useState, type ComponentRef } from "react";
import {
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { Field } from "@/shared/components/fields";
import { Screen } from "@/shared/components/screen";
import { Button, Icon, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { colors as c, radius, space } from "@/shared/theme";
import { airport } from "../data/airports";
import {
  loadPickerAirports,
  loadRegions,
  type ApiAirport,
  type Region,
} from "../services/flight-service";

export function AirportPicker({ field }: { field: "from" | "to" }) {
  const { search, setSearch } = useBooking();
  const [query, setQuery] = useState("");
  const [regions, setRegions] = useState<Region[]>([]);
  const [items, setItems] = useState<ApiAirport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [activeRegion, setActiveRegion] = useState<string | null>(null);
  const [expandedCountries, setExpandedCountries] = useState<
    Record<string, string | null>
  >({});
  const tabs = useRef<ComponentRef<typeof ScrollView>>(null);
  const excludedCode = field === "to" ? search.from : undefined;
  const selectedCode = search[field];
  const searching = !!query.trim();
  const otherCode = field === "from" ? search.to : search.from;
  const otherAirport = otherCode ? airport(otherCode) : undefined;

  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => {
      setLoading(true);
      setError("");
      setItems([]);
      Promise.all([
        loadRegions(controller.signal),
        loadPickerAirports(query, excludedCode, controller.signal),
      ])
        .then(([groups, airports]) => {
          if (controller.signal.aborted) return;
          const sorted = [...groups].sort((a, b) => a.sortOrder - b.sortOrder);
          setRegions(sorted);
          setItems(airports);
          // Keep the catalog's region codes, labels and ordering unchanged.
          if (!query.trim()) {
            const selected = airports.find(
              (item) => item.code === selectedCode,
            );
            setActiveRegion(
              (current) =>
                current ??
                selected?.regionCode ??
                sorted.find((group) => group.regionCode === "VIETNAM")
                  ?.regionCode ??
                sorted[0]?.regionCode ??
                null,
            );
          }
        })
        .catch((cause: unknown) => {
          if (!controller.signal.aborted)
            setError(
              cause instanceof Error
                ? cause.message
                : "Không tải được sân bay.",
            );
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, 250);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query, excludedCode, retry, selectedCode]);

  const changeQuery = (value: string) => {
    setQuery(value);
    setLoading(true);
    setError("");
    setItems([]);
  };
  const regionItems = items.filter((item) => item.regionCode === activeRegion);
  const countries = [...new Set(regionItems.map((item) => item.countryName))];
  const defaultCountry =
    regionItems.find((item) => item.code === selectedCode)?.countryName ??
    countries[0];
  const expandedCountry =
    activeRegion && activeRegion in expandedCountries
      ? expandedCountries[activeRegion]
      : defaultCountry;

  const airportRow = (item: ApiAirport, showCountry = false) => {
    const selected = selectedCode === item.code;
    return (
      <Pressable
        key={item.code}
        accessibilityRole="button"
        accessibilityLabel={`${item.city}, ${item.name}, ${item.code}${showCountry ? `, ${item.countryName}` : ""}`}
        accessibilityState={{ selected }}
        onPress={() => {
          setSearch((current) => ({
            ...current,
            [field]: item.code,
            ...(field === "from" && current.from !== item.code
              ? { to: "" }
              : {}),
          }));
          router.back();
        }}
        style={({ pressed }) => [
          styles.airportRow,
          selected && styles.selectedAirport,
          pressed && { opacity: 0.65 },
        ]}
      >
        <View style={styles.airportIcon}>
          <Icon
            name={
              field === "from"
                ? "home-design-imgPlaneTakeoff"
                : "home-design-imgPlaneLanding"
            }
            size={20}
          />
        </View>
        <View style={{ flex: 1, gap: 3 }}>
          <Label weight="semibold" color={selected ? c.primary : c.ink}>
            {item.city}
          </Label>
          <Label size={12} muted>
            {item.name}
          </Label>
          {showCountry && (
            <Label size={11} muted>
              {item.countryName}
            </Label>
          )}
        </View>
        <View style={{ alignItems: "flex-end", gap: 5 }}>
          <View style={styles.codeBadge}>
            <Label size={12} weight="bold" color={c.primary}>
              {item.code}
            </Label>
          </View>
          {selected && <Icon name="6-7220-imgCheck" size={16} />}
        </View>
      </Pressable>
    );
  };

  return (
    <View style={styles.container}>
      <Screen
        title={field === "from" ? "Chọn điểm khởi hành" : "Chọn điểm đến"}
        design
        topStyle={styles.topContainer}
        top={
          <View style={styles.pickerHeader}>
            {otherAirport && (
              <Row style={{ gap: space.sm }}>
                <Icon
                  name={
                    field === "from"
                      ? "home-design-imgPlaneLanding"
                      : "home-design-imgPlaneTakeoff"
                  }
                  size={16}
                />
                <Label size={12} muted style={{ flex: 1 }}>
                  {field === "from" ? "Điểm đến" : "Khởi hành từ"}:{" "}
                  {otherAirport.city} ({otherCode})
                </Label>
              </Row>
            )}
            <Field
              label="Tìm thành phố hoặc sân bay"
              placeholder="Ví dụ: Hà Nội, Nội Bài, HAN"
              value={query}
              onChangeText={changeQuery}
              autoCorrect={false}
              returnKeyType="search"
              trailing={
                query ? (
                  <Tap
                    label="Xóa tìm kiếm"
                    onPress={() => changeQuery("")}
                    style={{ width: 44, alignItems: "center" }}
                  >
                    <Label size={22} muted>
                      ×
                    </Label>
                  </Tap>
                ) : undefined
              }
            />
            {searching ? (
              <Label size={12} muted>
                Tìm kiếm trong tất cả khu vực
              </Label>
            ) : (
              <ScrollView
                ref={tabs}
                horizontal
                showsHorizontalScrollIndicator={false}
                keyboardShouldPersistTaps="handled"
                contentContainerStyle={{ gap: space.sm }}
                style={styles.tabs}
              >
                {regions.map((region) => (
                  <Pressable
                    key={region.regionCode}
                    accessibilityRole="tab"
                    accessibilityState={{
                      selected: activeRegion === region.regionCode,
                    }}
                    onLayout={(event) => {
                      if (activeRegion === region.regionCode)
                        tabs.current?.scrollTo({
                          x: Math.max(0, event.nativeEvent.layout.x - 8),
                          animated: false,
                        });
                    }}
                    onPress={() => setActiveRegion(region.regionCode)}
                    style={({ pressed }) => [
                      styles.tab,
                      activeRegion === region.regionCode && styles.activeTab,
                      pressed && { opacity: 0.65 },
                    ]}
                  >
                    <Label
                      size={13}
                      weight="semibold"
                      color={
                        activeRegion === region.regionCode ? c.white : c.muted
                      }
                      style={{ flexShrink: 0 }}
                    >
                      {region.regionName}
                    </Label>
                  </Pressable>
                ))}
              </ScrollView>
            )}
          </View>
        }
      >
        {loading && <Notice>Đang tải sân bay…</Notice>}
        {!!error && (
          <>
            <Notice error>{error}</Notice>
            <Button
              title="Thử lại"
              onPress={() => setRetry((value) => value + 1)}
            />
          </>
        )}
        {!loading &&
          !error &&
          (searching ? (
            <>
              <Label size={13} muted>
                {items.length} sân bay phù hợp
              </Label>
              {items.length ? (
                <View style={styles.list}>
                  {items.map((item) => airportRow(item, true))}
                </View>
              ) : (
                <Notice>
                  Không tìm thấy sân bay. Thử tên thành phố hoặc mã sân bay
                  khác.
                </Notice>
              )}
            </>
          ) : (
            <>
              <Label size={13} muted>
                Chọn quốc gia, sau đó chọn sân bay
              </Label>
              {countries.map((country) => {
                const airports = regionItems.filter(
                  (item) => item.countryName === country,
                );
                const open = expandedCountry === country;
                return (
                  <View key={country} style={styles.country}>
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel={`${country}, ${airports.length} sân bay`}
                      accessibilityState={{ expanded: open }}
                      onPress={() => {
                        if (activeRegion)
                          setExpandedCountries((current) => ({
                            ...current,
                            [activeRegion]: open ? null : country,
                          }));
                      }}
                      style={({ pressed }) => [
                        styles.countryHeader,
                        pressed && { opacity: 0.65 },
                      ]}
                    >
                      <View style={{ flex: 1, gap: 3 }}>
                        <Label weight="semibold">{country}</Label>
                        <Label size={12} muted>
                          {airports.length} sân bay
                        </Label>
                      </View>
                      <View
                        style={{
                          transform: [{ rotate: open ? "180deg" : "0deg" }],
                        }}
                      >
                        <Icon name="home-design-imgChevronDown" size={18} />
                      </View>
                    </Pressable>
                    {open && airports.map((item) => airportRow(item))}
                  </View>
                );
              })}
              {!regionItems.length && (
                <Notice>Chưa có sân bay trong khu vực này.</Notice>
              )}
            </>
          ))}
      </Screen>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    width: "100%",
    maxWidth: Platform.OS === "web" ? 402 : 600,
    minWidth: 0,
    alignSelf: "center",
  },
  pickerHeader: {
    width: "100%",
    minWidth: 0,
    gap: space.md,
    padding: space.lg,
    backgroundColor: c.white,
    borderWidth: 1,
    borderColor: c.line,
    borderRadius: radius.md,
    overflow: "hidden",
  },
  topContainer: {
    backgroundColor: c.background,
    borderBottomWidth: 0,
    paddingTop: space.md,
    paddingBottom: 0,
  },
  tabs: {
    width: "100%",
    minWidth: 0,
    flexGrow: 0,
    flexShrink: 1,
  },
  tab: {
    minHeight: 44,
    justifyContent: "center",
    paddingHorizontal: 16,
    borderRadius: radius.sm,
    backgroundColor: c.background,
  },
  activeTab: { backgroundColor: c.primary },
  country: {
    backgroundColor: c.white,
    borderWidth: 1,
    borderColor: c.line,
    borderRadius: radius.md,
    overflow: "hidden",
  },
  countryHeader: {
    minHeight: 68,
    padding: space.lg,
    flexDirection: "row",
    alignItems: "center",
    gap: space.md,
  },
  list: {
    borderRadius: radius.md,
    overflow: "hidden",
    backgroundColor: c.white,
  },
  airportRow: {
    minHeight: 82,
    padding: space.lg,
    flexDirection: "row",
    alignItems: "center",
    gap: space.md,
    borderTopWidth: 1,
    borderTopColor: c.line,
  },
  selectedAirport: { backgroundColor: c.tint },
  airportIcon: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: c.tint,
    alignItems: "center",
    justifyContent: "center",
  },
  codeBadge: {
    backgroundColor: c.tint,
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: 6,
  },
});
