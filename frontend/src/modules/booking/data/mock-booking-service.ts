import type { Booking, BookingService } from "../types";
import type { PaymentOutcome } from "../../payment/types";
import { bookingInput, bookingSnapshot } from "./booking-snapshot";
import { bookingErrors, quote } from "../services/booking-rules";
import { flights } from "../../flights/data/flights";

export interface BookingStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}
const STORAGE_KEY = "skygo.mock.bookings.v1";
const copy = <T>(value: T): T =>
  value === undefined ? value : JSON.parse(JSON.stringify(value));
export function createMockBookingService(
  storage: BookingStorage,
  now = Date.now,
) {
  let queue: Promise<unknown> = Promise.resolve();
  const read = (): Booking[] => {
    const raw = storage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed))
      throw new Error("Dữ liệu đặt chỗ mẫu không hợp lệ.");
    return (parsed as Booking[]).map((booking) => {
      const receipt = bookingSnapshot(booking.receipt);
      return {
        ...booking,
        receipt,
        fingerprint: JSON.stringify(bookingInput(receipt)),
      };
    });
  };
  const requireOwner = (owner: string) => {
    if (!owner || owner === "guest")
      throw new Error("Bạn cần đăng nhập để đặt vé.");
  };
  const transact = <T>(
    owner: string,
    fn: (all: Booking[]) => T,
  ): Promise<T> => {
    const task = queue.then(() => {
      requireOwner(owner);
      const all = read();
      const result = fn(all);
      storage.setItem(STORAGE_KEY, JSON.stringify(all));
      return copy(result);
    });
    queue = task.catch(() => undefined);
    return task;
  };
  const find = (all: Booking[], owner: string, id: string) => {
    const booking = all.find((b) => b.id === id && b.owner === owner);
    if (!booking) throw new Error("Không tìm thấy đặt chỗ của bạn.");
    booking.payments.forEach((p) => {
      if (p.status === "pending" && Date.parse(p.expiresAt) <= now())
        p.status = "expired";
    });
    return booking;
  };
  const id = (prefix: string) =>
    `${prefix}${now().toString(36)}${Math.random().toString(36).slice(2, 9)}`.toUpperCase();
  const api: BookingService & {
    simulateWebhook(
      owner: string,
      bookingId: string,
      paymentId: string,
      outcome: PaymentOutcome,
    ): Promise<void>;
  } = {
    create: (owner, input, key) =>
      transact(owner, (all) => {
        const {
          search,
          selected,
          travellers,
          email,
          phone,
          expiresAt,
          reservationId,
        } = bookingInput(input);
        if (
          Object.keys(bookingErrors(search, selected, travellers, email, phone))
            .length
        )
          throw new Error(
            "Thông tin đặt chỗ chưa hợp lệ. Vui lòng kiểm tra hành khách và liên hệ.",
          );
        if (
          expiresAt &&
          (!Number.isFinite(Date.parse(expiresAt)) ||
            Date.parse(expiresAt) <= now())
        )
          throw new Error("Thời gian giữ chỗ đã hết.");
        const canonicalFlights = selected.map((f) => {
          const match = flights.find((item) => item.id === f.id);
          if (!match)
            throw new Error("Chuyến bay không còn phù hợp. Vui lòng chọn lại.");
          return { ...match, ...(f.cabin ? { cabin: f.cabin } : {}) };
        });
        if (!key) throw new Error("Thiếu mã xác nhận thao tác.");
        const fingerprint = JSON.stringify(
          bookingInput({
            search,
            selected: canonicalFlights,
            travellers,
            email,
            phone,
            ...(expiresAt ? { expiresAt } : {}),
            ...(reservationId ? { reservationId } : {}),
          }),
        );
        const existing = all.find(
          (b) =>
            b.owner === owner &&
            (b.idempotencyKey === key ||
              (reservationId && b.receipt.reservationId === reservationId)),
        );
        if (existing) {
          if (existing.fingerprint !== fingerprint)
            throw new Error(
              "Thông tin đã thay đổi. Vui lòng tạo yêu cầu đặt chỗ mới.",
            );
          return existing;
        }
        const bookingId = id("SG");
        let reference = bookingId.slice(-6);
        while (all.some((b) => b.receipt.reference === reference))
          reference = id("SG").slice(-6);
        const booking: Booking = {
          id: bookingId,
          owner,
          idempotencyKey: key,
          fingerprint,
          status: "pending_payment",
          createdAt: new Date(now()).toISOString(),
          payments: [],
          receipt: {
            ...copy({
              search,
              travellers,
              email,
              phone,
              ...(expiresAt ? { expiresAt } : {}),
              ...(reservationId ? { reservationId } : {}),
            }),
            selected: copy(canonicalFlights),
            reference,
            ...quote(search, canonicalFlights, travellers),
          },
        };
        all.unshift(booking);
        return booking;
      }),
    createPayment: (owner, bookingId) =>
      transact(owner, (all) => {
        const booking = find(all, owner, bookingId);
        if (booking.status === "confirmed") return booking;
        if (
          booking.receipt.expiresAt &&
          Date.parse(booking.receipt.expiresAt) <= now()
        )
          throw new Error("Giữ chỗ đã hết hạn. Vui lòng tìm lại chuyến bay.");
        if (!booking.payments.some((p) => p.status === "pending"))
          booking.payments.push({
            id: id("PAY"),
            status: "pending",
            createdAt: new Date(now()).toISOString(),
            expiresAt:
              booking.receipt.expiresAt ??
              new Date(now() + 15 * 60000).toISOString(),
          });
        return booking;
      }),
    get: (owner, bookingId) =>
      transact(owner, (all) => find(all, owner, bookingId)),
    list: (owner) =>
      transact(owner, (all) =>
        all.filter((b) => b.owner === owner).map((b) => find(all, owner, b.id)),
      ),
    // Test harness for a verified provider event; production must move this to BE.
    simulateWebhook: (owner, bookingId, paymentId, outcome) =>
      transact(owner, (all) => {
        const booking = find(all, owner, bookingId);
        const payment = booking.payments.find((p) => p.id === paymentId);
        if (!payment) throw new Error("Không tìm thấy yêu cầu thanh toán.");
        if (payment.status !== "pending" || booking.status === "confirmed")
          return;
        payment.status = outcome;
        payment.completedAt = new Date(now()).toISOString();
        if (outcome === "paid") booking.status = "confirmed";
      }),
  };
  return api;
}
