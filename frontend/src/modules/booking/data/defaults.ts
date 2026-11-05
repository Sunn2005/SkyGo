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
export const newTraveller = (_i: number): Traveller => ({
  firstName: "",
  lastName: "",
  birthDate: "",
  gender: "Nam",
  extraBags: {},
  documentType: "cccd",
  documentNumber: "",
  nationality: "",
  issuingCountry: "",
  documentExpiry: "",
});
