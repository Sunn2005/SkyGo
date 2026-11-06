import {
  createContext,
  use,
  useState,
  useEffect,
  useLayoutEffect,
  useRef,
  type ReactNode,
} from "react";
import {
  reservationService,
  type Reservation,
} from "../services/reservation-service";
import { flights } from "@/modules/flights/data/flights";
import { getLegs } from "@/modules/flights/utils/trip";
import { initialSearch, newTraveller } from "@/modules/booking/data/defaults";
import { type Flight, type Search } from "@/modules/flights/types";
import type { Traveller, Booking, BookingInput } from "@/modules/booking/types";

import { quote, flightPrice, isDomestic } from "../services/booking-rules";
import { bookingService } from "../data/booking-service";
import type { PaymentOutcome } from "@/modules/payment/types";

type AccountContact = { email?: string | null; phoneNumber?: string };
function useBookingState(accountId: string, accountContact: AccountContact) {
  const ownerRef = useRef(accountId);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const reservationRef = useRef<Reservation | null>(null);
  const [now, setNow] = useState(Date.now);
  const [warningDismissed, setWarningDismissed] = useState(false);
  const [passengersConfirmed, setPassengersConfirmed] = useState(false);
  useEffect(() => {
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(tick);
  }, []);
  const remaining = reservation
    ? Math.max(0, Math.ceil((reservation.expiresAt - now) / 1000))
    : null;
  const releaseHold = () => {
    if (reservationRef.current)
      reservationService.release(reservationRef.current.id);
    reservationRef.current = null;
    setReservation(null);
    setWarningDismissed(false);
  };
  const selectFlight = async (flight: Flight) => {
    if (flight.source === "api")
      throw new Error(
        "Chưa có API giữ chỗ cho chuyến bay thật. Chưa tạo đơn đặt vé.",
      );
    const next = selected.slice(0, legIndex + 1);
    next[legIndex] = flight;
    // reserve() validates new capacity before replacing the previous hold.
    const hold = reservationService.reserve(
      accountId,
      search,
      next,
      reservationRef.current,
    );
    reservationRef.current = hold;
    setReservation(hold);
    setNow(hold.serverNow);
    setSelected(next);
    setPassengersConfirmed(false);
  };

  const checkoutDraft = useRef({ fingerprint: "", key: "" });
  const [currentBooking, setCurrentBooking] = useState<Booking | null>(null);
  const [history, setHistory] = useState<Booking[]>([]);
  const [historyError, setHistoryError] = useState("");
  const [historyLoading, setHistoryLoading] = useState(accountId !== "guest");
  useLayoutEffect(() => {
    if (ownerRef.current !== accountId && reservationRef.current) {
      reservationService.release(reservationRef.current.id);
      reservationRef.current = null;
    }
    ownerRef.current = accountId;
    checkoutDraft.current = { fingerprint: "", key: "" };
    return () => {
      ownerRef.current = "";
    };
  }, [accountId]);
  const [owner, setOwner] = useState(accountId);
  const [search, setSearch] = useState<Search>(initialSearch);
  const [travellers, setTravellers] = useState<Traveller[]>([newTraveller(0)]);
  const [selected, setSelected] = useState<Flight[]>([]);
  const [legIndex, setLegIndex] = useState(0);
  const [candidate, setCandidate] = useState<Flight>(flights[0]);
  const [email, setEmail] = useState(accountContact.email ?? "");
  const [phone, setPhone] = useState(accountContact.phoneNumber ?? "");
  const [favorites, setFavorites] = useState<string[]>([]);
  const [sort, setSort] = useState<"best" | "cheapest">("best");
  const [airline, setAirline] = useState("all");
  const [nonstop, setNonstop] = useState(false);
  const [maxPrice, setMaxPrice] = useState(10000000);
  const [morning, setMorning] = useState(false);
  // Reset before children render with a new account, without remounting navigation.
  if (owner !== accountId) {
    setOwner(accountId);
    if (owner !== "guest" || accountId === "guest") {
      setReservation(null);
      setSearch(initialSearch);
      setSelected([]);
      setLegIndex(0);
      setCandidate(flights[0]);
    }
    setCurrentBooking(null);
    setHistory([]);
    setHistoryError("");
    setHistoryLoading(accountId !== "guest");
    setTravellers(
      Array.from(
        {
          length:
            owner === "guest" && accountId !== "guest"
              ? search.count + (search.children ?? 0) + (search.infants ?? 0)
              : 1,
        },
        (_, i) => travellers[i] ?? newTraveller(i),
      ),
    );

    setEmail(accountContact.email ?? "");
    setPhone(accountContact.phoneNumber ?? "");
    setFavorites([]);
    setSort("best");
    setAirline("all");
    setNonstop(false);
    setMaxPrice(10000000);
    setMorning(false);
  }
  const receipt =
    currentBooking?.status === "confirmed" ? currentBooking.receipt : null;
  const legs = getLegs(search);
  const { fare, baggage, total } = quote(search, selected, travellers);
  useEffect(() => {
    let active = true;
    if (accountId === "guest") return;
    bookingService
      .list(accountId)
      .then((items) => {
        if (active) {
          setHistory(items);
          setHistoryError("");
        }
      })
      .catch(() => {
        if (active)
          setHistoryError("Chưa thể đọc đặt chỗ đã lưu. Hãy thử tải lại.");
      })
      .finally(() => {
        if (active) setHistoryLoading(false);
      });
    return () => {
      active = false;
    };
  }, [accountId]);
  const reloadHistory = async () => {
    setHistoryLoading(true);
    setHistoryError("");
    try {
      const items = await bookingService.list(accountId);
      if (ownerRef.current === accountId) setHistory(items);
    } catch {
      if (ownerRef.current === accountId)
        setHistoryError("Chưa thể đọc đặt chỗ đã lưu. Hãy thử tải lại.");
    } finally {
      if (ownerRef.current === accountId) setHistoryLoading(false);
    }
  };
  const acceptBooking = (booking: Booking) => {
    if (ownerRef.current !== booking.owner)
      throw new Error("Phiên đăng nhập đã thay đổi. Vui lòng mở lại đặt chỗ.");
    setCurrentBooking(booking);
    setHistory((items) => [
      booking,
      ...items.filter((item) => item.id !== booking.id),
    ]);
  };
  const checkout = async () => {
    if (accountId === "guest") throw new Error("Bạn cần đăng nhập để đặt vé.");
    if (
      !reservationRef.current ||
      reservationRef.current.expiresAt <= Date.now()
    )
      throw new Error("Thời gian giữ chỗ đã hết. Vui lòng tìm lại chuyến.");
    if (!passengersConfirmed)
      throw new Error("Vui lòng xác nhận thông tin hành khách.");
    const fingerprint = JSON.stringify({
      search,
      selected,
      travellers,
      email,
      phone,
    });
    if (checkoutDraft.current.fingerprint !== fingerprint)
      checkoutDraft.current = {
        fingerprint,
        key: `draft-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      };
    const payload: BookingInput = {
      reservationId: reservationRef.current.id,
      expiresAt: new Date(reservationRef.current.expiresAt).toISOString(),
      search,
      selected,
      travellers,
      email: email.trim(),
      phone: phone.trim(),
    };
    const booking = await bookingService.create(
      accountId,
      payload,
      checkoutDraft.current.key,
    );
    acceptBooking(booking); // Persisted before creating a payment request.
    const result = await bookingService.createPayment(accountId, booking.id);
    acceptBooking(result);
    return result;
  };
  const openBooking = async (id: string) => {
    const booking = await bookingService.get(accountId, id);
    acceptBooking(booking);
    return booking;
  };
  const retryPayment = async () => {
    if (!currentBooking) throw new Error("Không tìm thấy đặt chỗ.");
    const booking = await bookingService.createPayment(
      accountId,
      currentBooking.id,
    );
    acceptBooking(booking);
    return booking;
  };
  const refreshBooking = async () => {
    if (!currentBooking) throw new Error("Không tìm thấy đặt chỗ.");
    return openBooking(currentBooking.id);
  };
  const simulatePayment = async (outcome: PaymentOutcome) => {
    if (!currentBooking) throw new Error("Không tìm thấy đặt chỗ.");
    const payment = currentBooking.payments.at(-1);
    if (!payment) throw new Error("Chưa có yêu cầu thanh toán.");
    await bookingService.simulateWebhook(
      accountId,
      currentBooking.id,
      payment.id,
      outcome,
    );
    const result = await refreshBooking();
    if (outcome === "expired" && reservationRef.current) {
      reservationService.release(reservationRef.current.id);
      const expired = { ...reservationRef.current, expiresAt: Date.now() };
      reservationRef.current = expired;
      setReservation(expired);
    }
    if (result.status === "confirmed") {
      // Payment adapter has already rejected expired events.
      if (reservationRef.current)
        reservationService.confirm(reservationRef.current.id);
      reservationRef.current = null;
      setReservation(null);
    }
    return result;
  };
  const updateTraveller = (i: number, patch: Partial<Traveller>) => {
    if (Object.keys(patch).some((key) => key !== "extraBags"))
      setPassengersConfirmed(false);
    setTravellers((ps) =>
      ps.map((p, index) => (index === i ? { ...p, ...patch } : p)),
    );
  };
  const startSearch = () => {
    releaseHold();
    setPassengersConfirmed(false);
    setCurrentBooking(null);
    checkoutDraft.current = { fingerprint: "", key: "" };
    setSelected([]);
    setLegIndex(0);
    setTravellers((ps) => {
      const kinds = [
        ...Array(search.count).fill("adult"),
        ...Array(search.children ?? 0).fill("child"),
        ...Array(search.infants ?? 0).fill("infant"),
      ] as ("adult" | "child" | "infant")[];
      const seen = { adult: 0, child: 0, infant: 0 };
      return kinds.map((kind, i) => {
        const index = seen[kind]++;
        const old = ps.filter((p) => (p.kind ?? "adult") === kind)[index];
        return {
          ...(old ?? {
            ...newTraveller(i),
            birthDate: "",
            firstName: "",
            lastName: "",
          }),
          kind,
          extraBags: {},
          ...(kind === "adult"
            ? { title: old?.title ?? ("Ông" as const) }
            : {}),
          ...(kind === "infant"
            ? { companionIndex: index, extraBags: {} }
            : {}),
          ...(!isDomestic(search) ? { documentType: "passport" as const } : {}),
        };
      });
    });
  };
  return {
    reservation,
    remaining,
    warningDismissed,
    setWarningDismissed,
    passengersConfirmed,
    setPassengersConfirmed,
    selectFlight,
    search,
    setSearch,
    travellers,
    setTravellers,
    updateTraveller,
    selected,
    setSelected,
    legIndex,
    setLegIndex,
    candidate,
    setCandidate,
    email,
    setEmail,
    phone,
    setPhone,
    receipt,
    favorites,
    setFavorites,
    sort,
    setSort,
    airline,
    setAirline,
    nonstop,
    setNonstop,
    maxPrice,
    setMaxPrice,
    morning,
    setMorning,
    legs,
    fare,
    baggage,
    total,
    startSearch,
    accountId,
    currentBooking,
    history,
    historyError,
    historyLoading,
    reloadHistory,
    checkout,
    openBooking,
    retryPayment,
    refreshBooking,
    simulatePayment,
    priceFor: (flight: Flight) =>
      flightPrice(flight, flight.cabin ?? search.cabin),
  };
}
const BookingContext = createContext<ReturnType<typeof useBookingState> | null>(
  null,
);
export function BookingProvider({
  children,
  accountId = "guest",
  accountContact = {},
}: {
  children: ReactNode;
  accountId?: string;
  accountContact?: AccountContact;
}) {
  const value = useBookingState(accountId, accountContact);
  return <BookingContext value={value}>{children}</BookingContext>;
}
export function useBooking() {
  const value = use(BookingContext);
  if (!value) throw new Error("BookingProvider is required");
  return value;
}
