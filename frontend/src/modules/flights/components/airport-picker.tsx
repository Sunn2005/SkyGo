import { useEffect, useState } from "react";
import { View } from "react-native";
import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { Field } from "@/shared/components/fields";
import { Button, Label, Notice, Tap } from "@/shared/components/ui";
import {
  loadAirports,
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
  const [expanded, setExpanded] = useState<string | null>(null);
  const origin = field === "to" ? search.from : undefined;
  useEffect(() => {
    void retry;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      setLoading(true);
      setError("");
      setItems([]);
      Promise.all([
        loadRegions(controller.signal),
        (async () => {
          const all: ApiAirport[] = [];
          let page = 0;
          while (true) {
            const result = await loadAirports(
              { keyword: query, origin, page },
              controller.signal,
            );
            all.push(...result.data);
            if (++page >= result.pagination.totalPages) return all;
          }
        })(),
      ])
        .then(([groups, airports]) => {
          if (!controller.signal.aborted) {
            setRegions(groups.sort((a, b) => a.sortOrder - b.sortOrder));
            setItems(airports);
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
  }, [query, origin, retry]);
  return (
    <>
      <Field
        label="Tìm thành phố hoặc sân bay"
        placeholder="Tên hoặc mã sân bay"
        value={query}
        onChangeText={(value) => {
          setQuery(value);
          setLoading(true);
          setItems([]);
        }}
      />
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
      {!loading && !error && !items.length && (
        <Notice>
          Không có sân bay phù hợp với từ khóa tìm kiếm.
        </Notice>
      )}
      {regions.map((region) => {
        const airports = items.filter(
          (item) => item.regionCode === region.regionCode,
        );
        if (!airports.length) return null;
        const open = !!query.trim() || expanded === region.regionCode;
        const countries = [
          ...new Set(airports.map((item) => item.countryName)),
        ];
        return (
          <View key={region.regionCode} style={{ gap: 8 }}>
            <Tap
              label={`${open ? "Thu gọn" : "Mở rộng"} ${region.regionName}`}
              onPress={() => setExpanded(open ? null : region.regionCode)}
            >
              <Label weight="semibold">
                {open ? "▾" : "▸"} {region.regionName}
              </Label>
            </Tap>
            {open &&
              countries.map((country) => (
                <View key={country} style={{ gap: 6, paddingLeft: 12 }}>
                  <Label muted>{country}</Label>
                  {airports
                    .filter((item) => item.countryName === country)
                    .map((item) => (
                      <Tap
                        key={item.code}
                        label={`${item.city}, ${item.name}, ${item.code}`}
                        selected={search[field] === item.code}
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
                        style={{ paddingVertical: 10 }}
                      >
                        <Label weight="semibold">
                          {item.city} ({item.code})
                        </Label>
                        <Label size={12} muted>
                          {item.name}
                        </Label>
                      </Tap>
                    ))}
                </View>
              ))}
          </View>
        );
      })}
    </>
  );
}
