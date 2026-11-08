import { ApiError } from "@/shared/api/client";
import {
  bookingApi,
  passengerCounts,
  selection,
  tripType,
  type Hold,
} from "../data/api-booking-service";
import type { Flight, Search } from "../../flights/types";

export type HoldConfirmation = Pick<
  Hold,
  "seatHoldId" | "version" | "bookingId"
> & { legs?: Hold["legs"] };
export class HoldConfirmationRequired extends Error {
  constructor(
    public hold: HoldConfirmation,
    message = "Đổi lựa chọn sẽ đóng QR hiện tại. Đơn cũ vẫn được lưu để tiếp tục thanh toán sau; thời gian giữ chỗ không được gia hạn.",
  ) {
    super(message);
  }
}

// Keep the exact request after a lost response: PayOS cancellation cannot be undone.
export function createLiveSelection() {
  let pending: {
    token: string;
    fingerprint: string;
    bookingId: string;
    body: Parameters<typeof bookingApi.switch>[2];
    key: string;
  } | null = null;
  async function replay() {
    const op = pending!;
    try {
      const result = await bookingApi.switch(
        op.token,
        op.bookingId,
        op.body,
        op.key,
      );
      pending = null;
      return result.seatHold;
    } catch (error) {
      if (
        error instanceof ApiError &&
        error.status < 500 &&
        !["PAYMENT_STATUS_UNVERIFIED", "HOLD_SWITCH_IN_PROGRESS"].includes(
          error.code,
        )
      )
        pending = null;
      throw error;
    }
  }
  return {
    async select(
      token: string,
      search: Search,
      flights: Flight[],
      checkSession: () => void,
      confirmation?: HoldConfirmation,
    ): Promise<Hold> {
      checkSession();
      const fingerprint = JSON.stringify({
        tripType: tripType(search),
        passengerCounts: passengerCounts(search),
        selections: flights.map((f, i) => selection(search, f, i)),
      });
      if (pending?.token !== token) pending = null;
      if (pending) {
        if (pending.fingerprint !== fingerprint)
          throw new Error(
            "Lần đổi trước chưa rõ kết quả. Vui lòng thử lại đúng lựa chọn trước để hoàn tất.",
          );
        return replay();
      }
      const hold = await bookingApi.current(token);
      checkSession();
      if (!hold) {
        if (flights.length !== 1)
          throw new Error(
            "Giữ chỗ đã hết hạn. Vui lòng chọn lại chuyến đi trước.",
          );
        return bookingApi.hold(token, search, flights[0]);
      }
      if (
        confirmation &&
        (confirmation.seatHoldId !== hold.seatHoldId ||
          confirmation.version !== hold.version)
      )
        throw new HoldConfirmationRequired(
          hold,
          "Lựa chọn đã thay đổi. Vui lòng kiểm tra lượt giữ chỗ hiện tại và xác nhận lại.",
        );
      if (!hold.bookingId) {
        try {
          return await bookingApi.replace(token, hold, search, flights);
        } catch (error) {
          if (
            error instanceof ApiError &&
            error.code === "SEAT_HOLD_VERSION_CONFLICT"
          ) {
            const current = await bookingApi.current(token);
            checkSession();
            if (current)
              throw new HoldConfirmationRequired(
                current,
                "Lựa chọn đã thay đổi trên phiên khác. Vui lòng kiểm tra lượt giữ chỗ hiện tại và xác nhận lại toàn bộ lựa chọn.",
              );
          }
          throw error;
        }
      }
      if (
        !confirmation ||
        confirmation.seatHoldId !== hold.seatHoldId ||
        confirmation.version !== hold.version
      )
        throw new HoldConfirmationRequired({
          seatHoldId: hold.seatHoldId,
          version: hold.version,
          bookingId: hold.bookingId,
          ...(hold.legs ? { legs: hold.legs } : {}),
        });
      pending = {
        token,
        fingerprint,
        bookingId: hold.bookingId,
        key: `switch-${Date.now()}-${Math.random().toString(36).slice(2)}`,
        body: {
          expectedSeatHoldId: hold.seatHoldId,
          expectedVersion: hold.version,
          tripType: tripType(search),
          passengerCounts: passengerCounts(search),
          selections: flights.map((f, i) => selection(search, f, i)),
        },
      };
      return replay();
    },
  };
}
