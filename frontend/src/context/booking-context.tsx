import { createContext, use, useState, type ReactNode } from "react";
import {
  flights,
  getLegs,
  initialSearch,
  newTraveller,
  type Flight,
  type Search,
  type Traveller,
} from "@/data/booking";

export type Receipt = {
  reference: string;
  search: Search;
  travellers: Traveller[];
  selected: Flight[];
  seats: Record<string, string>;
  email: string;
  phone: string;
  card: string;
  insurance: boolean;
  total: number;
  fare: number;
  taxes: number;
  baggage: number;
  seatTotal: number;
  protection: number;
};
function useBookingState() {
  const [search, setSearch] = useState<Search>(initialSearch);
  const [travellers, setTravellers] = useState<Traveller[]>([newTraveller(0)]);
  const [selected, setSelected] = useState<Flight[]>([]);
  const [legIndex, setLegIndex] = useState(0);
  const [candidate, setCandidate] = useState<Flight>(flights[0]);
  const [seats, setSeats] = useState<Record<string, string>>({ "0-0": "12A" });
  const [email, setEmail] = useState("minh.nguyen@example.com");
  const [phone, setPhone] = useState("+84 912 345 678");
  const [insurance, setInsurance] = useState(false);
  const [card, setCard] = useState("Mastercard ···· 9876");
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [sort, setSort] = useState<"best" | "cheapest">("best");
  const [airline, setAirline] = useState("all");
  const [nonstop, setNonstop] = useState(false);
  const [maxPrice, setMaxPrice] = useState(1000);
  const [morning, setMorning] = useState(false);
  const legs = getLegs(search);
  const multiplier = search.cabin === "Thương gia" ? 2 : 1;
  const base =
    selected.reduce((sum, f) => sum + f.price * multiplier, 0) * search.count;
  const taxes = selected.length * 86 * search.count;
  const fare = base - taxes;
  const baggage =
    travellers.reduce((sum, p) => sum + p.bags * 45, 0) * legs.length;
  const seatTotal = Object.values(seats).filter(Boolean).length * 24;
  const protection = insurance ? 29 * search.count : 0;
  const total = base + baggage + seatTotal + protection;
  const updateTraveller = (i: number, patch: Partial<Traveller>) =>
    setTravellers((ps) =>
      ps.map((p, index) => (index === i ? { ...p, ...patch } : p)),
    );
  const startSearch = () => {
    setSelected([]);
    setLegIndex(0);
    setSeats({ "0-0": "12A" });
    setTravellers((ps) =>
      Array.from({ length: search.count }, (_, i) => ps[i] ?? newTraveller(i)),
    );
  };
  const confirm = () => {
    const result: Receipt = {
      reference: "SG" + Math.random().toString(36).slice(2, 6).toUpperCase(),
      search: { ...search },
      travellers: travellers.map((p) => ({ ...p })),
      selected: [...selected],
      seats: { ...seats },
      email,
      phone,
      card,
      insurance,
      total,
      fare,
      taxes,
      baggage,
      seatTotal,
      protection,
    };
    setReceipt(result);
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
    seats,
    setSeats,
    email,
    setEmail,
    phone,
    setPhone,
    insurance,
    setInsurance,
    card,
    setCard,
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
    multiplier,
    base,
    fare,
    taxes,
    baggage,
    seatTotal,
    protection,
    total,
    startSearch,
    confirm,
  };
}
const BookingContext = createContext<ReturnType<typeof useBookingState> | null>(
  null,
);
export function BookingProvider({ children }: { children: ReactNode }) {
  const value = useBookingState();
  return <BookingContext value={value}>{children}</BookingContext>;
}
export function useBooking() {
  const value = use(BookingContext);
  if (!value) throw new Error("BookingProvider is required");
  return value;
}
