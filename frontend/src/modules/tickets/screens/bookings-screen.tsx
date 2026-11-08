import { useCallback, useState } from "react";
import { View } from "react-native";
import { router, useFocusEffect } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { useAccount } from "@/modules/account/context/account-context";
import type { BookingDetail } from "@/modules/payment/services/payment-service";
import { isBookingPaid } from "@/modules/payment/services/payment-state";
import { HomeIcon } from "@/modules/flights/components/home-icon";
import { money } from "@/shared/utils/format";
import { Button, Card, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";
import { BookingJourney, BookingStatus } from "../components/booking-ui";
import {
  bookingPresentation,
  passengerCountLabel,
} from "../services/booking-presentation";
import { loadBookingHistory } from "../services/booking-history";

type HistoryItem = { data: BookingDetail; receivedAt: number };
export default function BookingsScreen() {
  const b = useBooking();
  const { session } = useAccount();
  const token = session?.accessToken;
  const owner = b.accountId;
  const [snapshot, setSnapshot] = useState<{
    token: string;
    items: HistoryItem[];
  } | null>(null);
  const items = snapshot?.token === token ? (snapshot?.items ?? []) : [];
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [filter, setFilter] = useState<"all" | "paid" | "pending">("all");
  useFocusEffect(
    useCallback(() => {
      void retry;
      if (!token) return;
      const controller = new AbortController();
      setLoading(true);
      setError("");
      setSnapshot(null);
      loadBookingHistory(token, owner, localStorage, controller.signal)
        .then((result) => {
          if (controller.signal.aborted) return;
          setSnapshot({ token, items: result.items });
          if (result.failed)
            setError(
              "Một số đặt chỗ chưa tải được. Vui lòng tải lại để cập nhật.",
            );
        })
        .catch(() => {
          if (!controller.signal.aborted)
            setError("Chưa tải được đặt chỗ. Vui lòng thử lại.");
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
      return () => controller.abort();
    }, [token, owner, retry]),
  );
  const visible = items.filter(
    ({ data, receivedAt }) =>
      filter === "all" ||
      (filter === "paid"
        ? isBookingPaid(data)
        : ["pending", "rehold"].includes(
            bookingPresentation(data, receivedAt).key,
          )),
  );
  const paidCount = items.filter(({ data }) => isBookingPaid(data)).length;
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
          {items.length} đặt chỗ · {paidCount} đã xác nhận
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
      {!!error && (
        <>
          <Notice error>{error}</Notice>
          <Button
            secondary
            title="Tải lại đặt chỗ"
            onPress={() => setRetry((n) => n + 1)}
          />
        </>
      )}
      {visible.map(({ data, receivedAt }) => (
        <Tap
          key={data.bookingId}
          label={`Xem chi tiết đặt chỗ ${data.bookingCode}`}
          onPress={() => {
            b.selectPaymentBooking(data.bookingId);
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
              <BookingStatus booking={data} receivedAt={receivedAt} />
              <Label
                size={10}
                muted
                numberOfLines={1}
                style={{ flexShrink: 1 }}
              >
                {data.bookingCode}
              </Label>
            </Row>
            {data.legs.map((leg, index) => (
              <View
                key={leg.direction}
                style={{
                  paddingTop: index ? 12 : 0,
                  borderTopWidth: index ? 1 : 0,
                  borderTopColor: c.line,
                }}
              >
                <BookingJourney leg={leg} compact />
              </View>
            ))}
            <Label size={11} muted>
              {passengerCountLabel(data.passengerCounts)}
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
                  {money(data.price.totalAmount)}
                </Label>
              </View>
              <Row style={{ gap: 5 }}>
                <Label size={12} weight="semibold" color={c.primary}>
                  {isBookingPaid(data) ? "Xem chi tiết & vé" : "Xem đặt chỗ"}
                </Label>
                <HomeIcon name="arrow" color={c.primary} size={17} />
              </Row>
            </Row>
          </Card>
        </Tap>
      ))}
      {!loading && !visible.length && !error && (
        <Card style={{ alignItems: "center", paddingVertical: 28, gap: 12 }}>
          <View
            style={{
              width: 64,
              height: 64,
              backgroundColor: c.tint,
              borderRadius: 32,
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <HomeIcon name="ticket" color={c.primary} size={30} />
          </View>
          <Label size={17} weight="bold" style={{ textAlign: "center" }}>
            {items.length
              ? "Chưa có đặt chỗ trong mục này"
              : "Sẵn sàng cho chuyến đi đầu tiên?"}
          </Label>
          <Label size={12} muted style={{ textAlign: "center" }}>
            {items.length
              ? "Các hành trình khác vẫn nằm trong mục Tất cả."
              : "Sau khi đặt vé, bạn có thể xem lại hành trình và vé điện tử tại đây."}
          </Label>
          <Button
            title={items.length ? "Xem tất cả đặt chỗ" : "Tìm chuyến bay"}
            onPress={() =>
              items.length ? setFilter("all") : router.push("/search")
            }
          />
        </Card>
      )}
      {!loading && !!items.length && (
        <>
          <Button
            secondary
            title="Khám phá hành trình mới"
            onPress={() => router.push("/search")}
          />
          <Label size={10} muted style={{ textAlign: "center" }}>
            Các đơn đã lưu trên thiết bị và chuyến sắp tới của tài khoản.
          </Label>
        </>
      )}
    </Screen>
  );
}
