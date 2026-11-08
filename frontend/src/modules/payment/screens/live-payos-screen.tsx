import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { AppState, Platform, Share, View } from "react-native";
import * as Linking from "expo-linking";
import { Image } from "expo-image";
import { router, useFocusEffect } from "expo-router";
import { useAccount } from "@/modules/account/context/account-context";
import { useBooking } from "@/modules/booking/context/booking-context";
import { ApiError } from "@/shared/api/client";
import { Screen } from "@/shared/components/screen";
import { Button, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Panel, InfoRow } from "@/modules/booking/components/design";
import { money } from "@/shared/utils/format";
import {
  paymentApi,
  type BookingDetail,
  type PaymentLink,
} from "../services/payment-service";
import {
  canPay,
  holdExpired,
  isBookingPaid,
  linkMatchesBooking,
  needsReconciliation,
  paymentMessage,
  serverNow,
} from "../services/payment-state";
import { paymentQrImage } from "../services/qr-code";

export default function LivePayosScreen({ bookingId }: { bookingId: string }) {
  const b = useBooking();
  const { session } = useAccount();
  const token = session?.accessToken;
  const acceptRef = useRef(b.acceptPaymentDetail);
  useLayoutEffect(() => {
    acceptRef.current = b.acceptPaymentDetail;
  }, [b.acceptPaymentDetail]);
  const [snapshot, setSnapshot] = useState<{
    data: BookingDetail;
    receivedAt: number;
  } | null>(null);
  const [link, setLink] = useState<PaymentLink | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [blocked, setBlocked] = useState(false);
  const [clock, setClock] = useState(Date.now);
  const scope = useRef<{
    check: () => Promise<void>;
    retry: () => Promise<void>;
  } | null>(null);
  useEffect(() => {
    const timer = setInterval(() => setClock(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useFocusEffect(
    useCallback(() => {
      if (!token) return;
      const controller = new AbortController();
      let active = true;
      let foreground =
        AppState.currentState !== "background" &&
        AppState.currentState !== "inactive";
      let timer: ReturnType<typeof setTimeout> | undefined;
      let inFlight: Promise<BookingDetail> | null = null;
      let mutation = false;
      let unsafeToRetry = false;
      let terminal = false;
      let initialising = true;
      const report = (cause: unknown) => {
        if (!active) return;
        const code = cause instanceof ApiError ? cause.code : undefined;
        if (
          [
            "PAYMENT_LINK_UNVERIFIED",
            "PAYMENT_STATUS_UNVERIFIED",
            "PAYMENT_RECONCILIATION_REQUIRED",
            "UNAUTHENTICATED",
          ].includes(code ?? "")
        ) {
          unsafeToRetry = true;
          setBlocked(true);
        }
        setError(
          paymentMessage(
            code,
            cause instanceof Error ? cause.message : undefined,
          ),
        );
      };
      const read = (): Promise<BookingDetail> => {
        if (inFlight) return inFlight;
        const request = paymentApi
          .detail(token, bookingId, controller.signal)
          .then((data) => {
            if (!active) return data;
            const receivedAt = Date.now();
            setSnapshot({ data, receivedAt });
            acceptRef.current(data);
            terminal =
              isBookingPaid(data) ||
              needsReconciliation(data) ||
              holdExpired(data, receivedAt);
            if (!canPay(data, receivedAt)) setLink(null);
            if (isBookingPaid(data)) router.replace("/success");
            return data;
          });
        inFlight = request;
        void request
          .finally(() => {
            if (inFlight === request) inFlight = null;
          })
          .catch(() => {});
        return request;
      };
      const createLink = async (data: BookingDetail) => {
        if (!active || !canPay(data, Date.now())) return;
        try {
          const result = await paymentApi.link(
            token,
            bookingId,
            controller.signal,
          );
          if (!active) return;
          // A webhook may commit while the link request is in progress.
          const latest = await read();
          if (!active) return;
          if (isBookingPaid(latest)) return;
          if (!linkMatchesBooking(result, latest))
            throw new Error(
              "Mã thanh toán không khớp đơn hiện tại. Vui lòng kiểm tra trạng thái.",
            );
          if (canPay(latest, Date.now())) setLink(result);
        } catch (cause) {
          if (!active) return;
          if (
            cause instanceof ApiError &&
            cause.code === "BOOKING_ALREADY_PAID"
          ) {
            await read();
            return;
          }
          throw cause;
        }
      };
      const check = async () => {
        if (!active || mutation || initialising) return;
        setBusy(true);
        try {
          await read();
          if (active && !unsafeToRetry) setError("");
        } catch (cause) {
          report(cause);
        } finally {
          if (active) {
            setBusy(false);
            schedulePoll();
          }
        }
      };
      const retry = async () => {
        if (!active || mutation || initialising) return;
        mutation = true;
        setBusy(true);
        setError("");
        setLink(null);
        try {
          // Finish an earlier status read before changing the hold.
          if (inFlight) await inFlight.catch(() => {});
          if (!active) return;
          await paymentApi.open(token, bookingId, controller.signal);
          if (!active) return;
          const data = await read();
          if (active) {
            setBlocked(false);
            await createLink(data);
          }
        } catch (cause) {
          report(cause);
        } finally {
          mutation = false;
          if (active) {
            setBusy(false);
            schedulePoll();
          }
        }
      };
      const poll = async () => {
        if (!active || !foreground || terminal) return;
        if (mutation) {
          schedulePoll();
          return;
        }
        try {
          await read();
        } catch (cause) {
          report(cause);
        }
        schedulePoll();
      };
      function schedulePoll() {
        clearTimeout(timer);
        if (active && foreground && !terminal)
          timer = setTimeout(() => void poll(), 5000);
      }
      scope.current = { check, retry };
      setLoading(true);
      setError("");
      setBlocked(false);
      setLink(null);
      void (async () => {
        try {
          const data = await read();
          if (active && foreground) await createLink(data);
        } catch (cause) {
          report(cause);
        } finally {
          initialising = false;
          if (active) {
            setLoading(false);
            schedulePoll();
          }
        }
      })();
      const listener = AppState.addEventListener("change", (state) => {
        foreground = state === "active";
        clearTimeout(timer);
        if (foreground && !initialising && !mutation) {
          terminal = false;
          void poll();
        }
      });
      return () => {
        active = false;
        controller.abort();
        clearTimeout(timer);
        listener.remove();
        scope.current = null;
      };
    }, [token, bookingId]),
  );
  const booking = snapshot?.data;
  const paid = !!booking && isBookingPaid(booking);
  const expired =
    !!booking && holdExpired(booking, snapshot!.receivedAt, clock);
  const reconciling = !!booking && needsReconciliation(booking);
  const remaining = booking?.seatHold
    ? Math.max(
        0,
        Math.ceil(
          (Date.parse(booking.seatHold.expiresAt) -
            serverNow(booking, snapshot!.receivedAt, clock)) /
            1000,
        ),
      )
    : 0;
  const payable =
    !!booking &&
    canPay(booking, snapshot!.receivedAt, clock) &&
    !!link &&
    linkMatchesBooking(link, booking) &&
    Date.parse(link.expiresAt) >
      serverNow(booking, snapshot!.receivedAt, clock);
  const qrPayload = link?.paymentLink.qrCode;
  const qr = useMemo(() => {
    if (!qrPayload) return null;
    try {
      return paymentQrImage(qrPayload);
    } catch {
      return null;
    }
  }, [qrPayload]);
  const checkoutUrl = link?.paymentLink.checkoutUrl;
  const openCheckout = async () => {
    if (!payable || !checkoutUrl) return;
    try {
      if (new URL(checkoutUrl).protocol !== "https:")
        throw new Error("Link thanh toán không hợp lệ.");
      await Linking.openURL(checkoutUrl);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không mở được PayOS.");
    }
  };
  return (
    <Screen
      design
      title="Thanh toán QR"
      step={2}
      onBack={() => router.replace("/booking")}
    >
      {loading && <Notice>Đang lấy thông tin thanh toán…</Notice>}
      {booking && (
        <>
          <Row style={{ justifyContent: "space-between" }}>
            <Label weight="semibold">
              {paid
                ? "Đã thanh toán"
                : reconciling
                  ? "Cần đối soát"
                  : expired
                    ? "Hết hạn giữ chỗ"
                    : "Chờ thanh toán"}
            </Label>
            {!paid && !expired && !reconciling && (
              <Label>
                {Math.floor(remaining / 60)}:
                {String(remaining % 60).padStart(2, "0")}
              </Label>
            )}
          </Row>
          <Panel tinted>
            <Label weight="bold">Mã đặt chỗ: {booking.bookingCode}</Label>
            {booking.legs.map((leg) => (
              <View key={leg.direction} style={{ gap: 4 }}>
                <Label>
                  {leg.origin.code} → {leg.destination.code} ·{" "}
                  {leg.flightNumber}
                </Label>
                <Label size={12}>
                  {new Date(leg.departureAt).toLocaleDateString("vi-VN")} ·{" "}
                  {leg.departureAt.slice(11, 16)} (giờ sân bay đi)
                </Label>
              </View>
            ))}
          </Panel>
          <Panel>
            <Label size={12}>Tổng cần thanh toán</Label>
            <Label size={30} weight="bold">
              {money(booking.price.totalAmount)}
            </Label>
            <InfoRow
              label="Vé chưa VAT"
              value={money(booking.price.fareSubtotal)}
            />
            <InfoRow
              label="Hành lý chưa VAT"
              value={money(booking.price.extraBaggageAmount)}
            />
            <InfoRow label="VAT" value={money(booking.price.vatAmount)} />
            {payable && (
              <>
                {qr ? (
                  <Image
                    source={{ uri: qr }}
                    style={{ width: "100%", height: 280 }}
                    contentFit="contain"
                    accessibilityLabel="Mã QR thanh toán PayOS cho đơn hiện tại"
                  />
                ) : (
                  <Notice>
                    Chưa hiển thị được QR. Bạn có thể mở trang thanh toán PayOS.
                  </Notice>
                )}
                <InfoRow
                  label="Nội dung chuyển khoản"
                  value={link!.paymentLink.description}
                />
                <Button
                  title="Mở trang thanh toán PayOS"
                  disabled={busy}
                  onPress={() => void openCheckout()}
                />
                {Platform.OS === "web" && qr ? (
                  <Button
                    secondary
                    title="Tải mã QR"
                    onPress={() => {
                      const a = document.createElement("a");
                      a.href = qr;
                      a.download = `skygo-${booking.bookingCode}.gif`;
                      a.click();
                    }}
                  />
                ) : (
                  <Button
                    secondary
                    title="Chia sẻ link thanh toán"
                    onPress={() =>
                      void Share.share({
                        message: checkoutUrl!,
                        url: checkoutUrl,
                      }).catch(() =>
                        setError("Không chia sẻ được link thanh toán."),
                      )
                    }
                  />
                )}
                <Label size={12}>
                  Quét QR bằng ứng dụng ngân hàng hoặc mở PayOS để thanh toán.
                  Quay lại app để kiểm tra trạng thái.
                </Label>
              </>
            )}
          </Panel>
          {reconciling && (
            <Notice error>
              Giao dịch cần đối soát. Vui lòng liên hệ hỗ trợ trước khi thanh
              toán thêm.
            </Notice>
          )}
          {expired && !paid && !reconciling && (
            <Notice>
              Thời gian giữ chỗ đã hết. Link cũ không còn được hiển thị. Chọn
              tiếp tục để BE kiểm tra khả năng giữ lại chỗ.
            </Notice>
          )}
        </>
      )}
      {!!error && <Notice error>{error}</Notice>}
      {!loading && (
        <Button
          title={paid ? "Xem vé" : "Kiểm tra trạng thái thanh toán"}
          disabled={busy}
          loading={busy}
          onPress={() =>
            paid ? router.replace("/booking") : void scope.current?.check()
          }
        />
      )}
      {!loading && !paid && !reconciling && !blocked && !payable && (
        <Button
          title="Tiếp tục thanh toán"
          disabled={busy}
          onPress={() => void scope.current?.retry()}
        />
      )}
      <Tap onPress={() => router.push("/help")}>
        <Label>Cần hỗ trợ? Liên hệ SkyGo</Label>
      </Tap>
    </Screen>
  );
}
