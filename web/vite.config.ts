/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    // The browser only ever talks to its own origin, in development as in production (where nginx
    // plays this role). No CORS configuration exists anywhere, because none is needed.
    // API_PROXY points the dev server at an API running elsewhere, e.g. a compose stack on
    // another port. The preview server reuses this setting.
    proxy: {
      "/api": process.env.API_PROXY ?? "http://localhost:8080",
    },
  },
  build: {
    sourcemap: true,
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    // Styles play no part in what the tests check.
    css: false,
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/api/generated.ts", "src/test/**", "src/main.tsx", "**/*.test.{ts,tsx}"],
      reporter: ["text-summary", "html"],
      // A floor, not a target: set just under today's figures so coverage cannot quietly erode.
      thresholds: { lines: 85, statements: 85, functions: 80, branches: 65 },
    },
  },
});
