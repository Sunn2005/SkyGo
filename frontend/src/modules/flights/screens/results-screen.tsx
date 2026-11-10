import {
  useCallback,
  useState,
  useRef,
  useLayoutEffect,
  useEffect,
} from "react";
import { View } from "react-native";
import { router, useFocusEffect, useLocalSearchParams } from "expo-router";
import { useFavoriteFlights } from "../services/use-favorite-flights";
import { useBooking } from "@/modules/booking/context/booking-context";
import { money } from "@/shared/utils/format";
import { Button, Card, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { FlightTimes } from "../components/flight-times";
import { Screen } from "@/shared/components/screen";
import { FlightSortControl } from "../components/flight-sort-control";
import { FlightDateStrip } from "../components/flight-date-strip";
import { mergeNearbyDays, nearbyWindowSearch } from "../services/nearby-days";
import { ResultsHeader } from "../components/results-header";
import { colors as c } from "@/shared/theme";
import {
  HoldConfirmationRequired,
  type HoldConfirmation,
} from "@/modules/booking/services/live-selection";
import { bookingApi } from "@/modules/booking/data/api-booking-service";
import { useAccount } from "@/modules/account/context/account-context";
import { ApiError } from "@/shared/api/client";
import { paymentMessage } from "@/modules/payment/services/payment-state";
import {
  searchFlights,
  searchNearbyFlights,
  type NearbyDay,
} from "../services/flight-service";
import type { Flight, Search } from "../types";

export default function ResultsScreen() {
  const saved = useFavoriteFlights();
  const { savedFlightId, savedFlightDate } = useLocalSearchParams<{
    savedFlightId?: string;
    savedFlightDate?: string;
  }>();
  const b = useBooking();
  const { session } = useAccount();
  const [existingBookingId, setExistingBookingId] = useState<string | null>(
    null,
  );
  const { search } = b;
  const [direction, setDirection] = useState<"OUTBOUND" | "RETURN">("OUTBOUND");
  const [days, setDays] = useState<NearbyDay[]>([]);
  const [dateTab, setDateTab] = useState("");
  const requestScope = useRef<AbortController | null>(null);
  const moreBusy = useRef(false);
  const rangeBusy = useRef(false);
  const [rangeLoading, setRangeLoading] = useState<"previous" | "next" | null>(
    null,
  );
  const [rangeError, setRangeError] = useState("");
  const [loadingMore, setLoadingMore] = useState(false);
  const [confirmation, setConfirmation] = useState<{
    flight: Flight;
    cabin: NonNullable<Flight["cabin"]>;
    index: number;
    hold: HoldConfirmation;
    search: Search;
  } | null>(null);
  const [retryChoice, setRetryChoice] = useState<{
    flight: Flight;
    cabin: NonNullable<Flight["cabin"]>;
    index: number;
    search: Search;
    hold?: HoldConfirmation;
  } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const selecting = useRef(false);
  const [pendingSelection, setPendingSelection] = useState(false);
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
    setDateTab("");
    setDirection(
      type === "round-trip" && b.selected[0] ? "RETURN" : "OUTBOUND",
    );
  }
  useFocusEffect(
    useCallback(() => {
      void retry;
      const controller = new AbortController();
      requestScope.current = controller;
      moreBusy.current = false;
      rangeBusy.current = false;
      setRangeLoading(null);
      setRangeError("");
      setLoadingMore(false);
      setLoading(true);
      setError("");
      setDays([]);
      setConfirmation(null);
      setRetryChoice(null);
      setExistingBookingId(null);
      setNotice("");
      searchNearbyFlights(
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
        controller.signal,
      )
        .then((result) => {
          if (!controller.signal.aborted) {
            setDays(result.days);
            setDateTab((current) =>
              result.days.some((day) => day.date === current && day.selectable)
                ? current
                : result.centerDate,
            );
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
      retry,
    ]),
  );
  const selectableDays = days.filter((day) => day.selectable);
  const canPrevious =
    !!selectableDays.length &&
    !days.some((day) =>
      ["PAST_DATE", "RETURN_NOT_AFTER_DEPARTURE"].includes(
        day.disabledReason ?? "",
      ),
    );
  const canNext =
    !!selectableDays.length &&
    !days.some((day) => day.disabledReason === "DEPARTURE_NOT_BEFORE_RETURN");
  const extendDates = async (side: "previous" | "next") => {
    const controller = requestScope.current;
    if (
      !controller ||
      controller.signal.aborted ||
      rangeBusy.current ||
      pendingSelection ||
      !selectableDays.length ||
      (side === "previous" ? !canPrevious : !canNext)
    )
      return;
    rangeBusy.current = true;
    setRangeLoading(side);
    setRangeError("");
    const edge =
      side === "previous"
        ? selectableDays[0].date
        : selectableDays[selectableDays.length - 1].date;
    try {
      const result = await searchNearbyFlights(
        nearbyWindowSearch(search, activeDirection, edge, side),
        activeDirection,
        controller.signal,
      );
      if (!controller.signal.aborted)
        setDays((current) => mergeNearbyDays(current, result.days));
    } catch (cause) {
      if (!controller.signal.aborted)
        setRangeError(
          cause instanceof Error
            ? cause.message
            : "Chưa tải được ngày khác. Vui lòng thử lại.",
        );
    } finally {
      if (!controller.signal.aborted) {
        rangeBusy.current = false;
        setRangeLoading(null);
      }
    }
  };
  const day = days.find((item) => item.date === dateTab);
  const items = day?.flights ?? [];
  const total = day?.pagination.totalElements ?? 0;
  const effectiveSearch = {
    ...search,
    ...(activeDirection === "OUTBOUND"
      ? { date: dateTab || search.date }
      : { secondDate: dateTab || search.secondDate }),
  };
  const loadMore = async () => {
    const controller = requestScope.current;
    if (
      !day ||
      !day.selectable ||
      !controller ||
      controller.signal.aborted ||
      moreBusy.current
    )
      return;
    moreBusy.current = true;
    setLoadingMore(true);
    setNotice("");
    try {
      const result = await searchFlights(
        effectiveSearch,
        activeDirection,
        day.pagination.page + 1,
        controller.signal,
        10,
      );
      if (!controller.signal.aborted)
        setDays((current) =>
          current.map((value) =>
            value.date !== day.date
              ? value
              : {
                  ...value,
                  flights: [
                    ...new Map(
                      [...value.flights, ...result.data].map((f) => [f.id, f]),
                    ).values(),
                  ],
                  pagination: {
                    ...result.pagination,
                    hasMore:
                      result.pagination.page + 1 < result.pagination.totalPages,
                  },
                },
          ),
        );
    } catch (cause) {
      if (!controller.signal.aborted)
        setNotice(
          cause instanceof Error ? cause.message : "Chưa tải thêm được chuyến.",
        );
    } finally {
      if (!controller.signal.aborted) {
        moreBusy.current = false;
        setLoadingMore(false);
      }
    }
  };
  // A saved flight can be beyond the first page. Load until it is found or
  // the result is exhausted; a request error stops automatic loading.
  useEffect(() => {
    if (
      savedFlightId &&
      savedFlightDate === dateTab &&
      activeDirection === "OUTBOUND" &&
      !loading &&
      !loadingMore &&
      !notice &&
      day?.pagination.hasMore &&
      !items.some((flight) => flight.id === savedFlightId)
    ) {
      void loadMore();
    }
  });
  const choose = async (
    flight: Flight,
    cabin: NonNullable<Flight["cabin"]>,
    index = activeDirection === "RETURN" ? 1 : 0,
    confirmedHold?: HoldConfirmation,
    selectionSearch = effectiveSearch,
  ) => {
    if (selecting.current) return;
    if (b.accountId === "guest") {
      b.setPendingFlight({
        flight: { ...flight, cabin },
        index,
        searchKey: JSON.stringify(search),
        selectionSearch,
      });
      router.push({
        pathname: "/login",
        params: { returnTo: "flight-selection" },
      });
      return;
    }
    selecting.current = true;
    setPendingSelection(true);
    setNotice("");
    b.setLegIndex(index);
    try {
      await b.selectFlight(
        { ...flight, cabin },
        index,
        confirmedHold,
        selectionSearch,
      );
      setConfirmation(null);
      setRetryChoice(null);
      if (type === "round-trip" && index === 0) {
        setDirection("RETURN");
        setDateTab("");
        setNotice("Đã giữ chuyến đi. Vui lòng chọn chuyến về.");
      } else router.push("/traveller");
    } catch (cause) {
      if (cause instanceof HoldConfirmationRequired) {
        setRetryChoice(null);
        setConfirmation({
          flight,
          cabin,
          index,
          hold: cause.hold,
          search: selectionSearch,
        });
        setNotice(cause.message);
      } else {
        setConfirmation(null);
        setRetryChoice({
          flight,
          cabin,
          index,
          search: selectionSearch,
          hold: confirmedHold,
        });
        if (
          cause instanceof ApiError &&
          cause.code === "PAYMENT_LINK_UNVERIFIED" &&
          session
        ) {
          const current = await bookingApi
            .current(session.accessToken)
            .catch(() => null);
          setExistingBookingId(current?.bookingId ?? null);
        }
        setNotice(
          cause instanceof ApiError
            ? paymentMessage(cause.code, cause.message)
            : cause instanceof Error
              ? cause.message
              : "Chưa thể giữ chỗ.",
        );
      }
    } finally {
      selecting.current = false;
      setPendingSelection(false);
    }
  };
  const resumeRef = useRef<
    (intent: typeof b.pendingFlight, accountId: string) => void
  >(() => {});
  useLayoutEffect(() => {
    resumeRef.current = (intent, accountId) => {
      if (!intent || accountId === "guest" || selecting.current) return;
      b.setPendingFlight(null);
      if (intent.searchKey !== JSON.stringify(search)) {
        setNotice("Tìm kiếm đã thay đổi. Vui lòng chọn lại chuyến bay.");
        return;
      }
      setDirection(intent.index === 1 ? "RETURN" : "OUTBOUND");
      void choose(
        intent.flight,
        intent.flight.cabin ?? search.cabin,
        intent.index,
        undefined,
        intent.selectionSearch ?? search,
      );
    };
  });
  useFocusEffect(
    useCallback(() => {
      resumeRef.current(b.pendingFlight, b.accountId);
    }, [b.accountId, b.pendingFlight]),
  );
  const priceFor = (flight: Flight) =>
    search.cabin === "Thương gia"
      ? (flight.businessPrice ?? Infinity)
      : flight.price;
  const results = items
    .filter((flight) => flight.availableCabins?.includes(search.cabin))
    .sort((a, z) =>
      b.sort === "cheapest"
        ? priceFor(a) - priceFor(z)
        : (a.departureAt ?? a.departure).localeCompare(
            z.departureAt ?? z.departure,
          ),
    );
  const leg = {
    ...(activeDirection === "OUTBOUND" ? b.legs[0] : (b.legs[1] ?? b.legs[0])),
    date:
      dateTab ||
      (activeDirection === "OUTBOUND" ? search.date : search.secondDate),
  };
  return (
    <Screen
      design
      title="Chọn chuyến bay"
      customHeader={
        <ResultsHeader leg={leg} search={search} disabled={pendingSelection} />
      }
      top={
        <View style={{ gap: 6 }}>
          {type === "round-trip" && (
            <Row style={{ gap: 8, paddingTop: 6 }}>
              {(["OUTBOUND", "RETURN"] as const).map((value) => (
                <Tap
                  key={value}
                  selected={activeDirection === value}
                  disabled={pendingSelection}
                  onPress={() => {
                    setDirection(value);
                    setDateTab("");
                    setConfirmation(null);
                  }}
                  style={{
                    flex: 1,
                    alignItems: "center",
                    borderRadius: 10,
                    backgroundColor:
                      activeDirection === value ? c.tint : c.background,
                  }}
                >
                  <Label
                    size={13}
                    weight="semibold"
                    color={activeDirection === value ? c.primary : c.muted}
                  >
                    {value === "OUTBOUND" ? "1. Chuyến đi" : "2. Chuyến về"}
                  </Label>
                </Tap>
              ))}
            </Row>
          )}
          {loading ? (
            <Label size={12} muted style={{ paddingVertical: 20 }}>
              Đang tải ngày bay…
            </Label>
          ) : (
            <FlightDateStrip
              key={`${searchKey}:${activeDirection}`}
              days={days}
              selected={dateTab}
              disabled={pendingSelection}
              loading={rangeLoading}
              canPrevious={canPrevious}
              canNext={canNext}
              onExtend={(side) => void extendDates(side)}
              onSelect={(value) => {
                setDateTab(value);
                setConfirmation(null);
                setRetryChoice(null);
                setNotice("");
              }}
            />
          )}
          <FlightSortControl
            value={b.sort}
            onChange={b.setSort}
            disabled={pendingSelection}
          />
          {!!rangeError && (
            <Label size={12} color={c.danger}>
              {rangeError}
            </Label>
          )}
        </View>
      }
    >
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
          <Label size={13} muted>
            Đã gồm thuế VAT
          </Label>
          {!!notice && <Notice>{notice}</Notice>}
          {!!saved.error && (
            <>
              <Notice error>{saved.error}</Notice>
              <Button
                secondary
                title="Tải lại chuyến đã lưu"
                onPress={saved.refresh}
              />
            </>
          )}
          {existingBookingId && (
            <Button
              secondary
              title="Mở đơn hiện tại để kiểm tra QR"
              onPress={() => {
                b.selectPaymentBooking(existingBookingId);
                router.push("/payos");
              }}
            />
          )}
          {retryChoice && (
            <Button
              secondary
              title="Thử lại lựa chọn vừa gửi"
              disabled={pendingSelection}
              onPress={() =>
                void choose(
                  retryChoice.flight,
                  retryChoice.cabin,
                  retryChoice.index,
                  retryChoice.hold,
                  retryChoice.search,
                )
              }
            />
          )}
          {confirmation && (
            <Card>
              <Label weight="bold">Xác nhận đổi lựa chọn</Label>
              {confirmation.hold.legs?.map((leg) => (
                <Label key={leg.direction} size={12}>
                  Đang giữ: {leg.origin.code} → {leg.destination.code} ·{" "}
                  {leg.departureAt.slice(0, 10)} {leg.departureAt.slice(11, 16)}{" "}
                  · {leg.cabinClass === "BUSINESS" ? "Thương gia" : "Phổ thông"}
                </Label>
              ))}
              <Label>
                {confirmation.flight.number} · {confirmation.cabin} ·{" "}
                {confirmation.index
                  ? confirmation.search.secondDate
                  : confirmation.search.date}
              </Label>
              <Button
                title={
                  confirmation.hold.bookingId
                    ? "Đóng QR cũ và đổi lựa chọn"
                    : "Xác nhận thay lựa chọn hiện tại"
                }
                disabled={pendingSelection}
                onPress={() =>
                  void choose(
                    confirmation.flight,
                    confirmation.cabin,
                    confirmation.index,
                    confirmation.hold,
                    confirmation.search,
                  )
                }
              />
              <Button
                secondary
                title="Giữ lựa chọn hiện tại"
                disabled={pendingSelection}
                onPress={() => {
                  setConfirmation(null);
                  setNotice("");
                }}
              />
            </Card>
          )}
          {results.map((flight) => (
            <Card
              key={flight.id}
              style={
                flight.id === savedFlightId
                  ? { borderWidth: 2, borderColor: c.primary }
                  : undefined
              }
            >
              {flight.id === savedFlightId && (
                <Label color={c.primary} weight="semibold">
                  Chuyến bay bạn đã lưu
                </Label>
              )}
              <Row>
                <Label weight="semibold" style={{ flex: 1 }}>
                  {flight.airline} · {flight.number}
                </Label>
                <Tap
                  label={
                    saved.favorites.includes(flight.id)
                      ? "Bỏ yêu thích chuyến bay"
                      : "Lưu chuyến bay yêu thích"
                  }
                  selected={saved.favorites.includes(flight.id)}
                  disabled={
                    saved.busy.includes(flight.id) ||
                    (saved.signedIn && !saved.page)
                  }
                  style={{
                    minWidth: 44,
                    minHeight: 44,
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                  onPress={() =>
                    void saved.toggle(
                      flight.id,
                      saved.favorites.includes(flight.id),
                    )
                  }
                >
                  <Label size={27} color={c.primary}>
                    {saved.favorites.includes(flight.id) ? "♥" : "♡"}
                  </Label>
                </Tap>
              </Row>
              <FlightTimes flight={flight} leg={leg} plane="6-6622-imgPlane2" />
              {flight.arrivalAt &&
                flight.departureAt &&
                flight.arrivalAt.slice(0, 10) !==
                  flight.departureAt.slice(0, 10) && (
                  <Label size={12} muted>
                    Ngày đến: {flight.arrivalAt.slice(0, 10)}
                  </Label>
                )}
              <Row>
                {flight.availableCabins?.map((cabin) => (
                  <Tap
                    key={cabin}
                    label={`Chọn ${flight.number} ${cabin}`}
                    disabled={pendingSelection}
                    onPress={() => void choose(flight, cabin)}
                    style={{
                      flex: 1,
                      padding: 12,
                      gap: 4,
                      borderRadius: 12,
                      backgroundColor: c.tint,
                      borderWidth: 1,
                      borderColor: c.primary,
                    }}
                  >
                    <Label size={12} color={c.primary}>
                      {cabin}
                    </Label>
                    <Label size={20} weight="bold" color={c.primary}>
                      {money(
                        cabin === "Thương gia"
                          ? flight.businessPrice!
                          : flight.price,
                      )}
                    </Label>
                    <Label size={12} weight="semibold" color={c.primary}>
                      Chọn vé →
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
                  ? "Không có chuyến thuộc hạng vé đã chọn trong danh sách đã tải."
                  : "Không tìm thấy chuyến bay phù hợp với hành trình và ngày đã chọn."}
              </Notice>
              <Button
                secondary
                title="Đổi tìm kiếm"
                onPress={() => router.push("/search")}
              />
            </>
          )}
          {day?.pagination.hasMore && (
            <Button
              secondary
              title="Tải thêm chuyến trong ngày"
              loading={loadingMore}
              disabled={pendingSelection}
              onPress={() => void loadMore()}
            />
          )}
        </>
      )}
    </Screen>
  );
}
