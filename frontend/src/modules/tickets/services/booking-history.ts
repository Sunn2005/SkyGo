import { loadHome } from "@/modules/flights/services/home-service";
import { paymentApi } from "@/modules/payment/services/payment-service";
import {
  knownBookingIds,
  rememberBookingId,
} from "@/modules/payment/services/known-bookings";

export async function loadBookingHistory(
  token: string,
  owner: string,
  storage: Pick<Storage, "getItem" | "setItem">,
  signal?: AbortSignal,
) {
  if (signal?.aborted) return { items: [], failed: false };
  const ids = knownBookingIds(owner, storage);
  let homeFailed = false;
  const detail = async (id: string) => ({
    data: await paymentApi.detail(token, id, signal),
    receivedAt: Date.now(),
  });
  // Home and known order details are independent. Recover the account's upcoming
  // booking after reinstalling without waiting before loading existing orders.
  const [results, home] = await Promise.all([
    Promise.allSettled(ids.map(detail)),
    loadHome(token, signal).catch(() => {
      homeFailed = true;
      return null;
    }),
  ]);
  if (signal?.aborted) return { items: [], failed: false };
  const upcoming = home?.upcomingFlight?.bookingId;
  if (upcoming) {
    rememberBookingId(owner, upcoming, storage);
    if (!ids.includes(upcoming))
      results.push(...(await Promise.allSettled([detail(upcoming)])));
  }
  return {
    items: results
      .flatMap((result) =>
        result.status === "fulfilled" ? [result.value] : [],
      )
      .sort(
        (a, b) => Date.parse(b.data.createdAt) - Date.parse(a.data.createdAt),
      ),
    failed:
      homeFailed || results.some((result) => result.status === "rejected"),
  };
}
