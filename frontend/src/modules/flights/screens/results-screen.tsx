import { useCallback, useState } from "react";
import { router, useFocusEffect } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { money } from "@/shared/utils/format";
import { Button, Card, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { FlightTimes } from "../components/flight-times";
import { Screen } from "@/shared/components/screen";
import { TripSummary } from "@/modules/booking/components/trip-summary";
import { searchFlights } from "../services/flight-service";
import type { Flight } from "../types";

export default function ResultsScreen() {
  const b = useBooking();
  const { search } = b;
  const [direction, setDirection] = useState<"OUTBOUND" | "RETURN">("OUTBOUND");
  const [items, setItems] = useState<Flight[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [retry, setRetry] = useState(0);
  const activeDirection = search.type === "one-way" ? "OUTBOUND" : direction;
  const { type, from, to, date, secondDate, count, children, infants } = search;
  const searchKey = JSON.stringify([
    type,
    from,
    to,
    date,
    secondDate,
    count,
    children ?? 0,
    infants ?? 0,
    search.cabin,
  ]);
  const [previousSearchKey, setPreviousSearchKey] = useState(searchKey);
  // Reset before committing effects so a new search never requests the old page.
  if (previousSearchKey !== searchKey) {
    setPreviousSearchKey(searchKey);
    setPage(0);
    setDirection("OUTBOUND");
  }
  useFocusEffect(
    useCallback(() => {
      void retry;
      const controller = new AbortController();
      setLoading(true);
      setError("");
      setItems([]);
      setNotice("");
      searchFlights(
        {
          type,
          from,
          to,
          date,
          secondDate,
          count,
          children,
          infants,
          cabin: "Phổ thông",
        },
        activeDirection,
        page,
        controller.signal,
      )
        .then((result) => {
          if (!controller.signal.aborted) {
            setItems(result.data);
            setTotalPages(result.pagination.totalPages);
            setTotal(result.pagination.totalElements);
          }
        })
        .catch((cause: unknown) => {
          if (!controller.signal.aborted)
            setError(
              cause instanceof Error
                ? cause.message
                : "Không tải được chuyến bay.",
            );
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
      return () => controller.abort();
    }, [
      type,
      from,
      to,
      date,
      secondDate,
      count,
      children,
      infants,
      activeDirection,
      page,
      retry,
    ]),
  );
  const priceFor = (flight: Flight) =>
    search.cabin === "Thương gia"
      ? (flight.businessPrice ?? Infinity)
      : flight.price;
  const results = items
    .filter(
      (flight) =>
        flight.availableCabins?.includes(search.cabin) &&
        (b.airline === "all" || flight.airline === b.airline) &&
        priceFor(flight) <= b.maxPrice &&
        (!b.morning || Number(flight.departure.slice(0, 2)) < 12),
    )
    .sort((a, z) => priceFor(a) - priceFor(z));
  const leg =
    activeDirection === "OUTBOUND" ? b.legs[0] : (b.legs[1] ?? b.legs[0]);
  return (
    <Screen design title="Kết quả tìm chuyến">
      <TripSummary leg={leg} />
      {type === "round-trip" && (
        <Row>
          {(["OUTBOUND", "RETURN"] as const).map((value) => (
            <Tap
              key={value}
              selected={activeDirection === value}
              onPress={() => {
                setDirection(value);
                setPage(0);
              }}
            >
              <Label weight="semibold">
                {value === "OUTBOUND" ? "Chuyến đi" : "Chuyến về"}
              </Label>
            </Tap>
          ))}
        </Row>
      )}
      <Row>
        <Tap onPress={() => router.push("/search")}>
          <Label>Đổi tìm kiếm</Label>
        </Tap>
        <Tap onPress={() => router.push("/filters")}>
          <Label>Bộ lọc</Label>
        </Tap>
      </Row>
      {loading ? (
        <Notice>Đang tìm chuyến bay…</Notice>
      ) : error ? (
        <>
          <Notice error>{error}</Notice>
          <Button
            title="Thử lại"
            onPress={() => setRetry((value) => value + 1)}
          />
        </>
      ) : (
        <>
          <Label size={12} muted>
            {total} chuyến từ máy chủ · {results.length} phù hợp trên trang này
          </Label>
          <Notice>
            Giá một người lớn, một chiều, đã gồm VAT. Chức năng đặt vé sẽ có khi
            hệ thống hỗ trợ giữ chỗ.
          </Notice>
          {!!notice && <Notice>{notice}</Notice>}
          {results.map((flight) => (
            <Card key={flight.id}>
              <Label weight="semibold">
                {flight.airline} · {flight.number}
              </Label>
              <FlightTimes flight={flight} leg={leg} plane="6-6622-imgPlane2" />
              <Label size={12} muted>
                Giờ địa phương tại từng sân bay
              </Label>
              {flight.arrivalAt && (
                <Label size={12} muted>
                  Ngày đến: {flight.arrivalAt.slice(0, 10)}
                </Label>
              )}
              <Row>
                {flight.availableCabins?.map((cabin) => (
                  <Tap
                    key={cabin}
                    label={`Chọn ${flight.number} ${cabin}`}
                    onPress={() =>
                      setNotice(
                        "Chức năng đặt vé hiện chưa khả dụng. Chưa tạo đơn hoặc giữ chỗ cho chuyến bay này.",
                      )
                    }
                    style={{ flex: 1, paddingVertical: 12 }}
                  >
                    <Label>{cabin}</Label>
                    <Label weight="bold">
                      {money(
                        cabin === "Thương gia"
                          ? flight.businessPrice!
                          : flight.price,
                      )}
                    </Label>
                  </Tap>
                ))}
              </Row>
            </Card>
          ))}
          {!results.length && (
            <>
              <Notice>
                {total
                  ? "Không có chuyến phù hợp bộ lọc trên trang này."
                  : "Không có chuyến đang mở bán đủ chỗ trong ngày đã chọn."}
              </Notice>
              <Button
                secondary
                title="Xóa bộ lọc"
                onPress={() => {
                  b.setAirline("all");
                  b.setNonstop(false);
                  b.setMaxPrice(10000000);
                  b.setMorning(false);
                }}
              />
            </>
          )}
          {totalPages > 1 && (
            <Row>
              <Button
                secondary
                title="Trang trước"
                disabled={page === 0}
                onPress={() => setPage((value) => value - 1)}
              />
              <Label>
                {page + 1}/{totalPages}
              </Label>
              <Button
                secondary
                title="Trang sau"
                disabled={page + 1 >= totalPages}
                onPress={() => setPage((value) => value + 1)}
              />
            </Row>
          )}
        </>
      )}
    </Screen>
  );
}
