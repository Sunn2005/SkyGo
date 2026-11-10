import { useCallback, useRef, useState } from "react";
import { router, useFocusEffect } from "expo-router";
import { useAccount } from "@/modules/account/context/account-context";
import { useBooking } from "@/modules/booking/context/booking-context";
import { favoriteApi, type FavoritePage } from "./favorite-service";

export function useFavoriteFlights() {
  const { session } = useAccount();
  const token = session?.accessToken;
  const { favorites, setFavorites } = useBooking();
  const [snapshot, setSnapshot] = useState<{
    token: string;
    page: FavoritePage;
  } | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState<string[]>([]);
  const [retry, setRetry] = useState(0);
  const generation = useRef(0);
  const pending = useRef(new Set<string>());
  const morePending = useRef(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const page = snapshot?.token === token ? snapshot?.page : undefined;
  useFocusEffect(
    useCallback(() => {
      void retry;
      const run = ++generation.current;
      const controller = new AbortController();
      setError("");
      setSnapshot(null);
      setBusy([]);
      pending.current.clear();
      morePending.current = false;
      setLoadingMore(false);
      setLoading(!!token);
      if (token)
        favoriteApi
          .list(token, 0, controller.signal)
          .then((result) => {
            if (run !== generation.current) return;
            setSnapshot({ token, page: result });
            setFavorites(result.favoriteFlightIds);
          })
          .catch((reason) => {
            if (run === generation.current)
              setError(
                reason instanceof Error
                  ? reason.message
                  : "Chưa tải được chuyến đã lưu.",
              );
          })
          .finally(() => {
            if (run === generation.current) setLoading(false);
          });
      return () => {
        generation.current++;
        controller.abort();
      };
    }, [token, retry, setFavorites]),
  );

  const toggle = async (id: string, saved: boolean) => {
    if (!token) {
      router.push({ pathname: "/login", params: { returnTo: "results" } });
      return false;
    }
    if (!page || pending.current.has(id)) return false;
    const run = generation.current;
    pending.current.add(id);
    setBusy([...pending.current]);
    setError("");
    try {
      await (saved
        ? favoriteApi.remove(token, id)
        : favoriteApi.save(token, id));
      if (run !== generation.current) return false;
      setFavorites((ids) =>
        saved
          ? ids.filter((value) => value !== id)
          : [...new Set([...ids, id])],
      );
      return true;
    } catch (reason) {
      if (run === generation.current)
        setError(
          reason instanceof Error ? reason.message : "Chưa lưu được thay đổi.",
        );
      return false;
    } finally {
      if (run === generation.current) {
        pending.current.delete(id);
        setBusy([...pending.current]);
      }
    }
  };
  const loadMore = async () => {
    if (
      !token ||
      !page ||
      morePending.current ||
      page.pagination.page + 1 >= page.pagination.totalPages
    )
      return;
    const run = generation.current;
    morePending.current = true;
    setLoadingMore(true);
    setError("");
    try {
      const result = await favoriteApi.list(token, page.pagination.page + 1);
      if (run !== generation.current) return;
      setSnapshot({
        token,
        page: { ...result, data: [...page.data, ...result.data] },
      });
      setFavorites(result.favoriteFlightIds);
    } catch (reason) {
      if (run === generation.current)
        setError(
          reason instanceof Error
            ? reason.message
            : "Chưa tải được thêm chuyến.",
        );
    } finally {
      if (run === generation.current) {
        morePending.current = false;
        setLoadingMore(false);
      }
    }
  };
  return {
    page,
    favorites,
    error,
    loading,
    busy,
    loadingMore,
    toggle,
    loadMore,
    refresh: () => setRetry((n) => n + 1),
    signedIn: !!token,
  };
}
