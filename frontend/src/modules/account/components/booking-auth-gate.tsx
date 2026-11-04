import type { ReactNode } from "react";
import { Redirect } from "expo-router";
import { useAccount } from "../context/account-context";
export function BookingAuthGate({
  children,
  returnTo,
}: {
  children: ReactNode;
  returnTo: string;
}) {
  const { session } = useAccount();
  if (!session)
    return <Redirect href={{ pathname: "/login", params: { returnTo } }} />;
  return children;
}
