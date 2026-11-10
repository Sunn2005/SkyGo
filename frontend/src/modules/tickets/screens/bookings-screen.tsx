import { useCallback, useRef, useState } from "react";
import { View } from "react-native";
import { router, useFocusEffect } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { useAccount } from "@/modules/account/context/account-context";
import { HomeIcon } from "@/modules/flights/components/home-icon";
import { money } from "@/shared/utils/format";
import { Button, Card, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";
import {
  flightDate,
  passengerCountLabel,
} from "../services/booking-presentation";
import {
  isSummaryPaid,
  listBookings,
  summaryStatus,
  type BookingFilter,
  type BookingPage,
  type BookingSummary,
} from "../services/booking-list";

type Snapshot = {
  token: string;
  filter: BookingFilter;
  items: BookingSummary[];
  pagination: BookingPage["pagination"];
};

export default function BookingsScreen() {
  const b = useBooking();
  const { session } = useAccount();
  const token = session?.accessToken;
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [filter, setFilter] = useState<BookingFilter>("all");
  const items =
    snapshot && snapshot.token === token && snapshot.filter === filter
      ? snapshot.items
      : [];
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const requestGeneration = useRef(0);

  useFocusEffect(
    useCallback(() => {
      void retry;
      const generation = ++requestGeneration.current;
      if (!token) {
        setLoading(false);
        setSnapshot(null);
        return () => {
          requestGeneration.current++;
        };
      }
      const controller = new AbortController();
      setLoading(true);
      setLoadingMore(false);
      setError("");
      setSnapshot(null);
      listBookings(token, 0, filter, controller.signal)
        .then(({ data, pagination }) => {
          if (requestGeneration.current === generation)
            setSnapshot({ token, filter, items: data, pagination });
        })
        .catch(() => {
          if (requestGeneration.current === generation)
            setError("Chưa tải được đặt chỗ. Vui lòng thử lại.");
        })
        .finally(() => {
          if (requestGeneration.current === generation) setLoading(false);
        });
      return () => {
        requestGeneration.current++;
        controller.abort();
      };
    }, [token, filter, retry]),
  );

  const loadMore = async () => {
    if (
      !token ||
      !snapshot ||
      snapshot.token !== token ||
      snapshot.filter !== filter ||
      loadingMore
    )
      return;
    if (snapshot.pagination.page + 1 >= snapshot.pagination.totalPages) return;
    const generation = requestGeneration.current;
    setLoadingMore(true);
    setError("");
    try {
      const { data, pagination } = await listBookings(
        token,
        snapshot.pagination.page + 1,
        filter,
      );
      if (requestGeneration.current !== generation) return;
      setSnapshot((current) =>
        current?.token === token &&
        current.filter === filter &&
        current.pagination.page + 1 === pagination.page
          ? { token, filter, items: [...current.items, ...data], pagination }
          : current,
      );
    } catch {
      if (requestGeneration.current === generation)
        setError("Chưa tải được thêm đặt chỗ. Vui lòng thử lại.");
    } finally {
      if (requestGeneration.current === generation) setLoadingMore(false);
    }
  };

  const visible = items;
  const hasMore =
    !!snapshot &&
    snapshot.token === token &&
    snapshot.filter === filter &&
    snapshot.pagination.page + 1 < snapshot.pagination.totalPages;

  return (
    <Screen
      design
      title="Đặt chỗ của bạn"
      right={
        <Tap
          label="Tải lại danh sách đặt chỗ"
          onPress={() => setRetry((n) => n + 1)}
          style={{ width: 44, alignItems: "center" }}
        >
          <Label size={24} color={c.primary}>
            ↻
          </Label>
        </Tap>
      }
    >
      <View style={{ gap: 6, paddingVertical: 4 }}>
        <Label size={23} weight="bold">
          Vé và hành trình của bạn
        </Label>
        <Label size={12} muted>
          Chọn đặt chỗ để xem vé và quản lý hành trình của bạn.
        </Label>
      </View>
      <Row style={{ gap: 8 }}>
        {(
          [
            { value: "all", label: "Tất cả" },
            { value: "paid", label: "Đã xác nhận" },
            { value: "pending", label: "Chờ thanh toán" },
          ] as const
        ).map((option) => (
          <Tap
            key={option.value}
            selected={filter === option.value}
            onPress={() => setFilter(option.value)}
            style={{
              flex: 1,
              minHeight: 44,
              alignItems: "center",
              padding: 8,
              borderRadius: 12,
              borderWidth: 1,
              borderColor: filter === option.value ? c.primary : c.line,
              backgroundColor: filter === option.value ? c.tint : c.surface,
            }}
          >
            <Label
              size={11}
              weight="semibold"
              color={filter === option.value ? c.primary : c.muted}
              style={{ textAlign: "center" }}
            >
              {option.label}
            </Label>
          </Tap>
        ))}
      </Row>
      {!loading && !!items.length && (
        <Label size={11} muted>
          {snapshot?.pagination.totalElements} đặt chỗ
        </Label>
      )}
      {loading && (
        <Card>
          <Label size={13} muted>
            Đang cập nhật hành trình của bạn…
          </Label>
          {[80, 140, 100].map((width, index) => (
            <View
              key={index}
              style={{
                width,
                height: 12,
                borderRadius: 6,
                backgroundColor: c.occupied,
              }}
            />
          ))}
        </Card>
      )}
      {!!error && <Notice error>{error}</Notice>}
      {visible.map((item) => {
        const status = summaryStatus(item);
        const palette =
          status.tone === "success"
            ? { background: "#E3F5EA", color: "#237047" }
            : status.tone === "warning"
              ? { background: "#FFF3DB", color: "#8A5609" }
              : { background: "#EDF1F5", color: c.muted };
        return (
          <Tap
            key={item.bookingId}
            label={`Xem chi tiết đặt chỗ ${item.bookingCode}`}
            onPress={() => {
              b.selectPaymentBooking(item.bookingId);
              router.push("/booking");
            }}
          >
            <Card style={{ width: "100%", padding: 16, gap: 14 }}>
              <Row
                style={{
                  justifyContent: "space-between",
                  gap: 8,
                  flexWrap: "wrap",
                }}
              >
                <View
                  style={{
                    alignSelf: "flex-start",
                    backgroundColor: palette.background,
                    paddingHorizontal: 10,
                    paddingVertical: 5,
                    borderRadius: 20,
                  }}
                >
                  <Label size={11} weight="semibold" color={palette.color}>
                    {status.label}
                  </Label>
                </View>
                <Label
                  size={10}
                  muted
                  numberOfLines={1}
                  style={{ flexShrink: 1 }}
                >
                  {item.bookingCode}
                </Label>
              </Row>
              {item.legs.map((leg, index) => (
                <View
                  key={leg.direction}
                  style={{
                    gap: 8,
                    paddingTop: index ? 12 : 0,
                    borderTopWidth: index ? 1 : 0,
                    borderTopColor: c.line,
                  }}
                >
                  <Row
                    style={{
                      justifyContent: "space-between",
                      flexWrap: "wrap",
                      gap: 6,
                    }}
                  >
                    <Label size={12} weight="semibold" color={c.primary}>
                      {leg.direction === "RETURN" ? "CHUYẾN VỀ" : "CHUYẾN ĐI"} ·{" "}
                      {flightDate(leg.departureAt)}
                    </Label>
                    <Label size={11} muted>
                      {leg.cabinClass === "BUSINESS"
                        ? "Thương gia"
                        : "Phổ thông"}
                    </Label>
                  </Row>
                  <Label size={15} weight="semibold">
                    {leg.origin.city} ({leg.origin.code}) →{" "}
                    {leg.destination.city} ({leg.destination.code})
                  </Label>
                </View>
              ))}
              <Label size={11} muted>
                {passengerCountLabel(item.passengerCounts)}
              </Label>
              <Row
                style={{
                  borderTopWidth: 1,
                  borderTopColor: c.line,
                  paddingTop: 12,
                  justifyContent: "space-between",
                  flexWrap: "wrap",
                  gap: 8,
                }}
              >
                <View style={{ gap: 2 }}>
                  <Label size={10} muted>
                    Tổng tiền
                  </Label>
                  <Label size={19} weight="bold" color={c.primary}>
                    {money(item.totalAmount)}
                  </Label>
                </View>
                <Row style={{ gap: 5 }}>
                  <Label size={12} weight="semibold" color={c.primary}>
                    {isSummaryPaid(item) ? "Xem chi tiết & vé" : "Xem đặt chỗ"}
                  </Label>
                  <HomeIcon name="arrow" color={c.primary} size={17} />
                </Row>
              </Row>
            </Card>
          </Tap>
        );
      })}
      {!loading && !visible.length && !error && (
        <Card style={{ alignItems: "center", paddingVertical: 28, gap: 12 }}>
          <HomeIcon name="ticket" color={c.primary} size={30} />
          <Label size={17} weight="bold" style={{ textAlign: "center" }}>
            {filter !== "all"
              ? "Chưa có đặt chỗ trong mục này"
              : "Sẵn sàng cho chuyến đi đầu tiên?"}
          </Label>
          <Label size={12} muted style={{ textAlign: "center" }}>
            {filter !== "all"
              ? "Các hành trình khác vẫn nằm trong mục Tất cả."
              : "Sau khi đặt vé, bạn có thể xem lại hành trình và vé điện tử tại đây."}
          </Label>
          <Button
            title={filter !== "all" ? "Xem tất cả đặt chỗ" : "Tìm chuyến bay"}
            onPress={() =>
              filter !== "all" ? setFilter("all") : router.push("/search")
            }
          />
        </Card>
      )}
      {!loading && hasMore && (
        <Button
          secondary
          title={loadingMore ? "Đang tải…" : "Xem thêm đặt chỗ"}
          onPress={() => {
            if (!loadingMore) void loadMore();
          }}
        />
      )}
      {!loading && !!items.length && (
        <Button
          secondary
          title="Khám phá hành trình mới"
          onPress={() => router.push("/search")}
        />
      )}
      {!loading && !!error && !items.length && (
        <Button
          secondary
          title="Tải lại đặt chỗ"
          onPress={() => setRetry((n) => n + 1)}
        />
      )}
    </Screen>
  );
}
