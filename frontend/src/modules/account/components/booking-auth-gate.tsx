import type { ReactNode } from "react";
import { Redirect, router, usePathname } from "expo-router";
import { Screen } from "@/shared/components/screen";
import { BookingHistoryLoginDialog } from "./booking-history-login-dialog";
import { useAccount } from "../context/account-context";
export function BookingAuthGate({
  children,
  returnTo,
}: {
  children: ReactNode;
  returnTo: string;
}) {
  const { session } = useAccount();
  const path = usePathname();
  // Also explain the sign-in requirement on a direct link or an expired session.
  if (!session && returnTo === "bookings")
    return (
      <Screen title="Lịch sử" design>
        <BookingHistoryLoginDialog
          visible={path === "/bookings"}
          onCancel={() => {
            if (router.canGoBack()) router.back();
            else router.replace("/");
          }}
          onConfirm={() =>
            router.replace({ pathname: "/login", params: { returnTo } })
          }
        />
      </Screen>
    );
  if (!session)
    return <Redirect href={{ pathname: "/login", params: { returnTo } }} />;
  return children;
}
