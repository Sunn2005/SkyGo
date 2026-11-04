import type { Traveller } from "../types";

export const fullName = (p: Traveller) => `${p.lastName} ${p.firstName}`.trim();
