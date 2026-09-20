import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap, type TransactionRequest } from "./client";

/** A chart window, as ISO dates, both ends included. */
export interface DateWindow {
  from: string;
  to: string;
}

export const queryKeys = {
  portfolio: ["portfolio"] as const,
  account: (accountId: string) => ["account", accountId] as const,
  performance: (scope: string, window: DateWindow) =>
    ["performance", scope, window.from, window.to] as const,
};

export function usePortfolio() {
  return useQuery({
    queryKey: queryKeys.portfolio,
    queryFn: () => unwrap(api.GET("/api/v1/portfolio")),
  });
}

export function useAccount(accountId: string) {
  return useQuery({
    queryKey: queryKeys.account(accountId),
    queryFn: () =>
      unwrap(api.GET("/api/v1/accounts/{accountId}", { params: { path: { accountId } } })),
  });
}

/**
 * Changing the window keeps the previous curve on screen until the new one arrives, rather than
 * flashing an empty chart: the page dims it instead (see `isPlaceholderData`).
 */
export function usePortfolioPerformance(window: DateWindow) {
  return useQuery({
    queryKey: queryKeys.performance("portfolio", window),
    queryFn: () => unwrap(api.GET("/api/v1/performance", { params: { query: window } })),
    placeholderData: keepPreviousData,
  });
}

export function useAccountPerformance(accountId: string, window: DateWindow) {
  return useQuery({
    queryKey: queryKeys.performance(accountId, window),
    queryFn: () =>
      unwrap(
        api.GET("/api/v1/accounts/{accountId}/performance", {
          params: { path: { accountId }, query: window },
        }),
      ),
    placeholderData: keepPreviousData,
  });
}

/**
 * Records a movement. On success every cached figure is dropped: a deposit changes the account,
 * the portfolio total, the allocation and the performance, and refetching all of it is cheaper
 * than reasoning about which of them can safely stay.
 */
export function useRecordMovement(accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: TransactionRequest) =>
      unwrap(
        api.POST("/api/v1/accounts/{accountId}/transactions", {
          params: { path: { accountId } },
          body,
        }),
      ),
    onSuccess: () => queryClient.invalidateQueries(),
  });
}
