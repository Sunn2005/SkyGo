import { useState } from "react";
import { router, useIsFocused, usePreventRemove } from "expo-router";
import {
  isPaymentBackAction,
  paymentReturnPath,
  type PaymentReturnTo,
} from "./payment-navigation";

export function usePaymentExit(
  returnTo: PaymentReturnTo,
  paid: boolean,
  busy: boolean,
) {
  const focused = useIsFocused();
  const [visible, setVisible] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const disablePrevention = usePreventRemove(
    focused && !paid && !leaving,
    ({ data, repeat }) => {
      if (isPaymentBackAction(data.action.type)) {
        setVisible(true);
      } else {
        // Payment confirmation, logout and other non-Back transitions can complete normally.
        setLeaving(true);
        repeat();
      }
    },
  );
  const leave = () => {
    if (busy || leaving) return;
    setLeaving(true);
    setVisible(false);
    disablePrevention();
    // Pop to the existing destination, removing QR/review/form screens above it.
    // Expo Router replaces the current screen if no destination is in the stack.
    router.dismissTo(paymentReturnPath(returnTo));
  };
  return {
    visible: visible && focused && !paid,
    requestBack: () => {
      if (paid) leave();
      else setVisible(true);
    },
    stay: () => setVisible(false),
    leave,
  };
}
