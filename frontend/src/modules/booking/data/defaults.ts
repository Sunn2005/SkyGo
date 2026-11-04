import type { Search } from "@/modules/flights/types";
import type { Traveller } from "../types";

export const initialSearch: Search = {
  type: "one-way",
  from: "SGN",
  to: "HAN",
  date: "2026-11-16",
  secondDate: "2026-11-23",
  count: 1,
  cabin: "Phổ thông",
};
export const newTraveller = (i: number): Traveller => ({
  firstName: i === 0 ? "Minh" : "",
  lastName: i === 0 ? "Nguyễn" : "",
  birthDate: "12/03/1990",
  gender: "Nam",
  extraBags: {},
  documentType: "cccd",
  documentNumber: "",
  nationality: "",
  issuingCountry: "",
  documentExpiry: "",
});
