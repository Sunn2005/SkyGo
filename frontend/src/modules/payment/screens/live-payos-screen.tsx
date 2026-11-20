import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { AppState, View } from "react-native";
import { Image } from "expo-image";
import { router, useFocusEffect } from "expo-router";
import { useAccount } from "@/modules/account/context/account-context";
import { useBooking } from "@/modules/booking/context/booking-context";
import {
  bookingApi,
  type Hold,
} from "@/modules/booking/data/api-booking-service";
import { ApiError } from "@/shared/api/client";
import { Screen } from "@/shared/components/screen";
import { Button, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Panel, InfoRow } from "@/modules/booking/components/design";
import { money } from "@/shared/utils/format";
import { PassengerPriceBreakdown } from "../components/passenger-price-breakdown";
import {
  paymentApi,
  type BookingDetail,
  type PaymentLink,
} from "../services/payment-service";
import {
  canPay,
  canResume,
  holdExpired,
  isBookingPaid,
  linkMatchesBooking,
  needsReconciliation,
  paymentMessage,
  serverNow,
} from "../services/payment-state";
import { SaveQrButton } from "../components/save-qr-button";
import { paymentQrImage } from "../services/qr-code";
import { PaymentExitDialog } from "../components/payment-exit-dialog";
import { usePaymentExit } from "../services/use-payment-exit";
import type { PaymentReturnTo } from "../services/payment-navigation";

export default function LivePayosScreen({
  bookingId,
  returnOutcome,
  returnTo = "booking",
}: {
  bookingId: string;
  returnOutcome?: "success" | "cancel";
  returnTo?: PaymentReturnTo;
}) {
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
  const [confirmation, setConfirmation] = useState<Hold | null>(null);
  const [clock, setClock] = useState(Date.now);
  const scope = useRef<{
    retry: (hold?: Hold) => Promise<void>;
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
      let terminal = false;
      let initialising = true;
      const report = (cause: unknown) => {
        if (!active) return;
        const code = cause instanceof ApiError ? cause.code : undefined;
        if (
          [
            "PAYMENT_LINK_UNVERIFIED",
            "PAYMENT_RECONCILIATION_REQUIRED",
            "UNAUTHENTICATED",
          ].includes(code ?? "")
        ) {
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
              data.bookingStatus === "CANCELLED" ||
              (!!data.bookingClosesAt &&
                serverNow(data, receivedAt) >=
                  Date.parse(data.bookingClosesAt)) ||
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
      const retry = async (confirmedHold?: Hold) => {
        if (!active || mutation || initialising) return;
        mutation = true;
        setBusy(true);
        setError("");
        setLink(null);
        try {
          // Finish an earlier status read before changing the hold.
          if (inFlight) await inFlight.catch(() => {});
          if (!active) return;
          const before = await read();
          if (!active) return;
          if (!canPay(before, Date.now())) {
            if (!canResume(before, Date.now()))
              throw new Error(
                "Đơn hiện không thể tiếp tục thanh toán. Vui lòng kiểm tra trạng thái.",
              );
            await paymentApi.resume(
              token,
              bookingId,
              confirmedHold
                ? {
                    expectedSeatHoldId: confirmedHold.seatHoldId,
                    expectedVersion: confirmedHold.version,
                  }
                : undefined,
              controller.signal,
            );
          }
          if (!active) return;
          setConfirmation(null);
          const data = await read();
          if (active) {
            setBlocked(false);
            await createLink(data);
          }
        } catch (cause) {
          if (
            active &&
            cause instanceof ApiError &&
            ["ACTIVE_HOLD_EXISTS", "SEAT_HOLD_VERSION_CONFLICT"].includes(
              cause.code,
            )
          ) {
            try {
              const current = await bookingApi.current(token);
              if (active && current && !current.bookingId) {
                setConfirmation(current);
                setError(
                  "Bạn đang giữ lựa chọn khác. Tiếp tục đơn này sẽ thay lựa chọn đó bằng hành trình đã lưu. Thiếu ghế sẽ giữ nguyên lựa chọn hiện tại.",
                );
              } else if (active) {
                setConfirmation(null);
                setError(paymentMessage("HOLD_ATTACHED_TO_BOOKING"));
              }
            } catch (error) {
              report(error);
            }
          } else {
            setConfirmation(null);
            report(cause);
          }
          // Refresh cutoff/reconciliation after a rejected mutation too.
          if (active) await read().catch(() => {});
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
      scope.current = { retry };
      setSnapshot(null);
      setConfirmation(null);
      setLoading(true);
      setError("");
      setBlocked(false);
      setLink(null);
      void (async () => {
        try {
          const data = await read();
          if (active && foreground && !returnOutcome) await createLink(data);
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
    }, [token, bookingId, returnOutcome]),
  );
  const booking = snapshot?.data;
  const paid = !!booking && isBookingPaid(booking);
  const expired =
    !!booking && holdExpired(booking, snapshot!.receivedAt, clock);
  const reconciling = !!booking && needsReconciliation(booking);
  const resumable =
    !!booking && canResume(booking, snapshot!.receivedAt, clock);
  const closed =
    !!booking &&
    (booking.bookingStatus === "CANCELLED" ||
      (!!booking.bookingClosesAt &&
        serverNow(booking, snapshot!.receivedAt, clock) >=
          Date.parse(booking.bookingClosesAt)));
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
  const exit = usePaymentExit(returnTo, paid, busy || loading);
  return (
    <Screen design title="Thanh toán QR" step={2} onBack={exit.requestBack}>
      <PaymentExitDialog
        visible={exit.visible}
        remaining={
          booking?.seatHold?.status === "ACTIVE" &&
          !expired &&
          !closed &&
          !reconciling
            ? remaining
            : 0
        }
        busy={busy || loading}
        onStay={exit.stay}
        onLeave={exit.leave}
      />
      {loading && <Notice>Đang lấy thông tin thanh toán…</Notice>}
      {returnOutcome && !paid && (
        <Notice>
          {returnOutcome === "cancel"
            ? "Bạn đã rời trang thanh toán. Đơn vẫn được lưu; đang kiểm tra trạng thái thanh toán."
            : "Đang kiểm tra kết quả từ hệ thống. Khi thanh toán được xác nhận, app sẽ hiển thị vé của bạn."}
        </Notice>
      )}
      {booking && (
        <>
          <Row style={{ justifyContent: "space-between" }}>
            <Label weight="semibold">
              {paid
                ? "Đã thanh toán"
                : reconciling
                  ? "Cần đối soát"
                  : closed
                    ? "Đã quá hạn đặt vé"
                    : expired
                      ? "Cần giữ chỗ lại"
                      : "Chờ thanh toán"}
            </Label>
            {!paid && !expired && !closed && !reconciling && (
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
          <PassengerPriceBreakdown passengers={booking.passengers} />
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
                    Chưa hiển thị được QR. Vui lòng mở lại đơn để thử lại.
                  </Notice>
                )}
                <InfoRow
                  label="Nội dung chuyển khoản"
                  value={link!.paymentLink.description}
                />
                {qr && (
                  <SaveQrButton
                    uri={qr}
                    name={`skygo-${booking.bookingCode}`}
                    disabled={busy}
                  />
                )}
                <Label size={12}>
                  Quét QR bằng ứng dụng ngân hàng hoặc chọn ảnh PNG đã lưu. App
                  tự cập nhật kết quả khi bạn quay lại.
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
          {closed && !paid && !reconciling && (
            <Notice error>
              Đã quá thời hạn đặt vé. Đơn không thể tiếp tục thanh toán.
            </Notice>
          )}
          {expired && !closed && !paid && !reconciling && (
            <Notice>
              Đơn cần giữ chỗ lại. Đơn và giá đã lưu vẫn được giữ nguyên. Chọn
              tiếp tục để kiểm tra khả năng giữ lại chỗ.
            </Notice>
          )}
        </>
      )}
      {!!error && <Notice error>{error}</Notice>}
      {!loading && paid && (
        <Button title="Xem vé" onPress={() => router.replace("/booking")} />
      )}
      {confirmation && !loading && resumable && !blocked && (
        <Panel>
          <Label weight="bold">
            Thay lựa chọn đang giữ bằng đơn {booking?.bookingCode}?
          </Label>
          <Button
            title="Xác nhận tiếp tục đơn đã lưu"
            disabled={busy}
            onPress={() => void scope.current?.retry(confirmation)}
          />
          <Button
            secondary
            title="Giữ lựa chọn hiện tại"
            disabled={busy}
            onPress={() => {
              setConfirmation(null);
              setError("");
            }}
          />
        </Panel>
      )}
      {!confirmation &&
        !loading &&
        !paid &&
        !reconciling &&
        !blocked &&
        !payable &&
        !closed &&
        (resumable ||
          (!!booking && canPay(booking, snapshot!.receivedAt, clock))) && (
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
