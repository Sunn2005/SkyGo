import {
  createContext,
  use,
  useState,
  useEffect,
  useLayoutEffect,
  useRef,
  type ReactNode,
} from "react";
import { flights } from "@/modules/flights/data/flights";
import { getLegs } from "@/modules/flights/utils/trip";
import { initialSearch, newTraveller } from "@/modules/booking/data/defaults";
import { type Flight, type Search } from "@/modules/flights/types";
import type { Traveller, Booking, BookingInput } from "@/modules/booking/types";

import { quote, flightPrice } from "../services/booking-rules";
import { bookingService } from "../data/booking-service";
import type { PaymentOutcome } from "@/modules/payment/types";

function useBookingState(accountId: string) {
  const ownerRef = useRef(accountId);

  const checkoutDraft = useRef({ fingerprint: "", key: "" });
  const [currentBooking, setCurrentBooking] = useState<Booking | null>(null);
  const [history, setHistory] = useState<Booking[]>([]);
  const [historyError, setHistoryError] = useState("");
  const [historyLoading, setHistoryLoading] = useState(accountId !== "guest");
  useLayoutEffect(() => {
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
  const [email, setEmail] = useState("minh.nguyen@example.com");
  const [phone, setPhone] = useState("+84 912 345 678");
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
          length: owner === "guest" && accountId !== "guest" ? search.count : 1,
        },
        (_, i) => newTraveller(i),
      ),
    );

    setEmail("minh.nguyen@example.com");
    setPhone("+84 912 345 678");
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
    return refreshBooking();
  };
  const updateTraveller = (i: number, patch: Partial<Traveller>) =>
    setTravellers((ps) =>
      ps.map((p, index) => (index === i ? { ...p, ...patch } : p)),
    );
  const startSearch = () => {
    setCurrentBooking(null);
    checkoutDraft.current = { fingerprint: "", key: "" };
    setSelected([]);
    setLegIndex(0);
    setTravellers((ps) =>
      Array.from({ length: search.count }, (_, i) => ps[i] ?? newTraveller(i)),
    );
  };
  return {
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
    priceFor: (flight: Flight) => flightPrice(flight, search.cabin),
  };
}
const BookingContext = createContext<ReturnType<typeof useBookingState> | null>(
  null,
);
export function BookingProvider({
  children,
  accountId = "guest",
}: {
  children: ReactNode;
  accountId?: string;
}) {
  const value = useBookingState(accountId);
  return <BookingContext value={value}>{children}</BookingContext>;
}
export function useBooking() {
  const value = use(BookingContext);
  if (!value) throw new Error("BookingProvider is required");
  return value;
}
