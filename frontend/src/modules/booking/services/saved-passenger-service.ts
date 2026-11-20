import { apiRequest } from "@/shared/api/client";
import type { Traveller } from "../types";
import { validDate } from "@/shared/utils/date";

export type PassengerProfileInput = {
  title: "MR" | "MRS" | "MS" | null;
  givenNames: string;
  surname: string;
  dateOfBirth: string;
  gender: "MALE" | "FEMALE" | "OTHER";
  document: {
    documentType: "PERSONAL_ID" | "PASSPORT";
    number: string;
    issuingCountryCode: string;
    nationalityCountryCode: string | null;
    expiresOn: string | null;
  };
};
export type SavedPassenger = PassengerProfileInput & {
  savedPassengerId: string;
};
export type SavedPassengerSummary = Pick<
  SavedPassenger,
  "savedPassengerId" | "givenNames" | "surname" | "dateOfBirth"
> & { documentType: "PERSONAL_ID" | "PASSPORT"; documentNumberLast4: string };

const base = "/api/v1/saved-passengers";
export const savedPassengerApi = {
  list: (token: string) => apiRequest<SavedPassengerSummary[]>(base, { token }),
  get: (token: string, id: string) =>
    apiRequest<SavedPassenger>(`${base}/${encodeURIComponent(id)}`, { token }),
  create: (token: string, body: PassengerProfileInput) =>
    apiRequest<SavedPassenger>(base, { token, method: "POST", body }),
  update: (token: string, id: string, body: PassengerProfileInput) =>
    apiRequest<SavedPassenger>(`${base}/${encodeURIComponent(id)}`, {
      token,
      method: "PUT",
      body,
    }),
  remove: (token: string, id: string) =>
    apiRequest<void>(`${base}/${encodeURIComponent(id)}`, {
      token,
      method: "DELETE",
    }),
};

const isoDate = (value: string) => {
  const parts = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  return parts ? `${parts[3]}-${parts[2]}-${parts[1]}` : "";
};
const displayDate = (iso: string | null) =>
  iso ? iso.split("-").reverse().join("/") : "";
export function profileInput(p: Traveller): PassengerProfileInput {
  const passport = p.documentType === "passport";
  return {
    title: p.title
      ? { Ông: "MR" as const, Bà: "MRS" as const, "Cô/Chị": "MS" as const }[
          p.title
        ]
      : null,
    givenNames: p.firstName.trim(),
    surname: p.lastName.trim(),
    dateOfBirth: isoDate(p.birthDate),
    gender:
      p.gender === "Nam" ? "MALE" : p.gender === "Nữ" ? "FEMALE" : "OTHER",
    document: {
      documentType: passport ? "PASSPORT" : "PERSONAL_ID",
      number: p.documentNumber.trim().toUpperCase(),
      issuingCountryCode: passport
        ? p.issuingCountry.trim().toUpperCase()
        : "VN",
      nationalityCountryCode: passport
        ? p.nationality.trim().toUpperCase()
        : null,
      expiresOn: passport ? isoDate(p.documentExpiry) : null,
    },
  };
}
export function profileTraveller(p: SavedPassenger): Traveller {
  return {
    savedPassengerId: p.savedPassengerId,
    title: p.title
      ? { MR: "Ông" as const, MRS: "Bà" as const, MS: "Cô/Chị" as const }[
          p.title
        ]
      : undefined,
    firstName: p.givenNames,
    lastName: p.surname,
    birthDate: displayDate(p.dateOfBirth),
    gender: { MALE: "Nam", FEMALE: "Nữ", OTHER: "Khác" }[p.gender],
    documentType: p.document.documentType === "PASSPORT" ? "passport" : "cccd",
    documentNumber: p.document.number,
    nationality: p.document.nationalityCountryCode ?? "",
    issuingCountry: p.document.issuingCountryCode,
    documentExpiry: displayDate(p.document.expiresOn),
    extraBags: {},
  };
}
export function passengerKindOnDate(
  birth: string,
  departure: string,
): NonNullable<Traveller["kind"]> {
  let age = Number(departure.slice(0, 4)) - Number(birth.slice(0, 4));
  if (departure.slice(5) < birth.slice(5)) age--;
  return age >= 12 ? "adult" : age >= 2 ? "child" : "infant";
}
export function applySavedPassenger(
  profile: SavedPassenger,
  current: Traveller,
  domestic: boolean,
): Traveller {
  const next = profileTraveller(profile);
  return {
    ...next,
    kind: current.kind,
    companionIndex: current.companionIndex,
    extraBags: current.extraBags,
    title:
      (current.kind ?? "adult") === "adult" ? (next.title ?? "Ông") : undefined,
    ...(!domestic && next.documentType !== "passport"
      ? {
          documentType: "passport" as const,
          documentNumber: "",
          documentExpiry: "",
        }
      : {}),
  };
}
// Profile checks are independent of any itinerary; booking validation still checks age and expiry.
export function profileErrors(p: Traveller): Record<string, string> {
  const errors: Record<string, string> = {};
  const body = profileInput(p);
  if (!body.givenNames || body.givenNames.length > 200)
    errors.firstName = "Nhập tên đệm và tên, tối đa 200 ký tự.";
  if (!body.surname || body.surname.length > 200)
    errors.lastName = "Nhập họ, tối đa 200 ký tự.";
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  if (
    !validDate(body.dateOfBirth) ||
    body.dateOfBirth < "1900-01-01" ||
    body.dateOfBirth > today
  )
    errors.birthDate = "Ngày sinh phải từ năm 1900 đến hôm nay.";
  if (!["Nam", "Nữ", "Khác"].includes(p.gender))
    errors.gender = "Chọn giới tính.";
  if (p.documentType === "passport") {
    if (!/^[A-Z0-9]{6,12}$/.test(body.document.number))
      errors.documentNumber = "Hộ chiếu cần 6–12 chữ hoặc số.";
    if (!/^[A-Z]{2}$/.test(body.document.issuingCountryCode))
      errors.issuingCountry = "Nhập mã quốc gia gồm 2 chữ, ví dụ VN.";
    if (!/^[A-Z]{2}$/.test(body.document.nationalityCountryCode ?? ""))
      errors.nationality = "Nhập mã quốc tịch gồm 2 chữ, ví dụ VN.";
    if (!validDate(body.document.expiresOn ?? ""))
      errors.documentExpiry = "Nhập ngày hết hạn hợp lệ.";
  } else if (
    p.documentType !== "cccd" ||
    !/^\d{12}$/.test(body.document.number)
  )
    errors.documentNumber = "CCCD cần đúng 12 chữ số.";
  return errors;
}
