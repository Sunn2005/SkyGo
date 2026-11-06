export type AirportCode = string;
export type TripType = "one-way" | "round-trip";
export type Cabin = "Phổ thông" | "Thương gia";
export type Flight = {
  source?: "api";
  availableCabins?: Cabin[];
  departureAt?: string;
  arrivalAt?: string;
  cabin?: Cabin;
  id: string;
  airline: string;
  number: string;
  departure: string;
  arrival: string;
  price: number;
  businessPrice?: number;
  duration: string;
  stops: number;
};
export type Search = {
  type: TripType;
  from: AirportCode;
  to: AirportCode;
  date: string;
  secondDate: string;
  count: number; // Adults; children and lap infants are separate.
  children?: number;
  infants?: number;
  cabin: Cabin;
};
export type Leg = { from: AirportCode; to: AirportCode; date: string };
