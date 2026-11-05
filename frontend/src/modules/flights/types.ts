import { airports } from "./data/airports";

export type AirportCode = (typeof airports)[number]["code"];
export type TripType = "one-way" | "round-trip";
export type Cabin = "Phổ thông" | "Thương gia";
export type Flight = {
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
