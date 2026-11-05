import { Platform, Share, View } from "react-native";
import { Image } from "expo-image";
import { useEffect, useRef, useState } from "react";
import { Redirect, router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import type { PaymentOutcome } from "../types";
import { demoQr } from "../data-demo-qr";
import { money } from "@/shared/utils/format";
import { Button, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Panel } from "@/modules/booking/components/design";
import { passengerSummary } from "@/modules/booking/services/booking-rules";
import { dateLabel } from "@/shared/utils/date";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";
export default function PayosScreen() {
  const b = useBooking();
  const [clock, tick] = useState(Date.now);
  useEffect(() => {
    const timer = setInterval(() => tick(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [showTools, setShowTools] = useState(false);
  const locked = useRef(false);
  const booking = b.currentBooking;
  const payment = booking?.payments.at(-1);
  const bookingId = booking?.id;
  const refreshRef = useRef(b.refreshBooking);
  useEffect(() => {
    refreshRef.current = b.refreshBooking;
  }, [b.refreshBooking]);
  useEffect(() => {
    if (!bookingId || payment?.status !== "pending") return;
    const timer = setInterval(() => {
      void refreshRef
        .current()
        .then((result) => {
          if (result.status === "confirmed") router.replace("/success");
        })
        .catch(() => setError("Chưa lấy được trạng thái thanh toán."));
    }, 3000);
    return () => clearInterval(timer);
  }, [bookingId, payment?.status]);
  const perform = async (action: "refresh" | "retry" | PaymentOutcome) => {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    setError("");
    try {
      const result =
        action === "refresh"
          ? await b.refreshBooking()
          : action === "retry"
            ? await b.retryPayment()
            : await b.simulatePayment(action);
      if (result.status === "confirmed") router.replace("/success");
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Chưa thể kiểm tra thanh toán.",
      );
    } finally {
      locked.current = false;
      setBusy(false);
    }
  };
  if (!booking) return <Redirect href="/bookings" />;
  const remaining = payment
    ? Math.max(0, Math.ceil((Date.parse(payment.expiresAt) - clock) / 1000))
    : 0;
  const waiting = payment?.status === "pending";
  const reference = `SKYGO ${booking.receipt.reference}`;
  return (
    <Screen
      design
      title="Thanh toán QR"
      step={2}
      onBack={() => router.replace("/bookings")}
    >
      <Row style={{ justifyContent: "space-between" }}>
        <Label size={12} color={c.primary}>
          {waiting
            ? "◷  Chờ thanh toán"
            : booking.status === "confirmed"
              ? "Đã thanh toán"
              : "Thanh toán chưa hoàn tất"}
        </Label>
        {waiting && (
          <Label size={12} color={c.primary}>
            Giữ chỗ còn {Math.floor(remaining / 60)}:
            {String(remaining % 60).padStart(2, "0")}
          </Label>
        )}
      </Row>
      <Panel tinted>
        <Label size={14} weight="semibold">
          ✈ {booking.receipt.search.from} → {booking.receipt.search.to}
        </Label>
        <Label size={12} muted>
          {dateLabel(booking.receipt.search.date)} ·{" "}
          {passengerSummary(booking.receipt.search)}
        </Label>
      </Panel>
      <Panel>
        <View style={{ alignItems: "center", gap: 3 }}>
          <Label size={12} muted>
            Tổng cần thanh toán
          </Label>
          <Label size={30} weight="bold">
            {money(booking.receipt.total)}
          </Label>
          <Label size={11} muted>
            Vé {money(booking.receipt.fare)}
            {booking.receipt.baggage
              ? ` + hành lý ${money(booking.receipt.baggage)}`
              : ""}
          </Label>
        </View>
        {waiting && (
          <>
            <Image
              source={{ uri: demoQr }}
              style={{ width: "100%", height: 240 }}
              contentFit="contain"
              accessibilityLabel="QR minh họa, không dùng để chuyển tiền"
            />
            <Button
              title="↓  Lưu mã QR"
              onPress={() => {
                if (Platform.OS === "web") {
                  const a = document.createElement("a");
                  a.href = demoQr;
                  a.download = "skygo-qr-demo.svg";
                  a.click();
                } else
                  void Share.share({
                    url: demoQr,
                    message: "QR minh họa SkyGo, không dùng để thanh toán.",
                  }).catch(() => setError("Chưa thể lưu mã QR."));
              }}
            />
          </>
        )}
        <View style={{ height: 1, backgroundColor: c.line }} />
        <Label size={11} muted>
          Nội dung chuyển khoản (minh họa)
        </Label>
        <Label size={13} weight="semibold" selectable>
          {reference}
        </Label>
      </Panel>
      <Label size={17} weight="bold">
        Thanh toán như thế nào?
      </Label>
      <Row style={{ alignItems: "flex-start" }}>
        <Label color={c.primary} weight="bold">
          1
        </Label>
        <Label size={13} style={{ flex: 1 }}>
          Mở ứng dụng ngân hàng, quét mã QR hoặc chọn ảnh mã vừa lưu trong thư
          viện.
        </Label>
      </Row>
      <Row style={{ alignItems: "flex-start" }}>
        <Label color={c.primary} weight="bold">
          2
        </Label>
        <Label size={13} style={{ flex: 1 }}>
          Kiểm tra số tiền và nội dung chuyển khoản trước khi xác nhận.
        </Label>
      </Row>
      {waiting ? (
        <Button
          title="Đã thanh toán"
          loading={busy}
          disabled={busy || remaining === 0}
          onPress={() => void perform("paid")}
        />
      ) : booking.status === "confirmed" ? (
        <Button
          title="Xem đặt chỗ"
          onPress={() => router.replace("/booking")}
        />
      ) : (
        <>
          <Notice error>
            {payment?.status === "expired"
              ? "Thời gian giữ chỗ đã hết. Vui lòng tìm lại chuyến bay."
              : "Thanh toán chưa hoàn tất. Bạn có thể thử lại khi giữ chỗ còn hiệu lực."}
          </Notice>
          {payment?.status !== "expired" && remaining > 0 && (
            <Button title="Thử lại" onPress={() => void perform("retry")} />
          )}
          <Button
            title="Tìm lại chuyến bay"
            onPress={() => {
              b.startSearch();
              router.replace("/search");
            }}
          />
        </>
      )}
      <Tap onPress={() => router.push("/help")}>
        <Label
          size={12}
          weight="semibold"
          color={c.primary}
          style={{ textAlign: "center" }}
        >
          Cần hỗ trợ? Liên hệ SkyGo ›
        </Label>
      </Tap>
      {!!error && <Notice error>{error}</Notice>}
    </Screen>
  );
}
