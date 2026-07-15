import { defineConfig, devices } from "@playwright/test";

/**
 * Suite de humo. Arranca `next dev` sola (webServer); asume que el backend y docker compose
 * están arriba con los datos de la semilla. Un solo worker: los tests reservan y cancelan sobre
 * la misma base, así que corren en serie para no pisarse.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [{ name: "movil", use: { ...devices["Pixel 7"] } }],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:3000/login",
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
