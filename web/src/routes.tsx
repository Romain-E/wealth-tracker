import type { RouteObject } from "react-router";
import { Layout, RouteError } from "./components/Layout";
import { AccountPage } from "./pages/AccountPage";
import { DashboardPage } from "./pages/DashboardPage";
import { NotFoundPage } from "./pages/NotFoundPage";

/** Shared by the browser router and the tests, so the tests exercise the real routing. */
export const routes: RouteObject[] = [
  {
    path: "/",
    element: <Layout />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: "comptes/:accountId", element: <AccountPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];
