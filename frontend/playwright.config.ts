import { defineConfig, devices } from "@playwright/test";

/**
 * Suite de humo. Arranca `next dev` sola (webServer); asume que el backend y docker compose
 * están arriba con los datos de la semilla. Un solo worker: los tests reservan y cancelan sobre
 * la misma base, así que corren en serie para no pisarse.
 */
/**
 * El puerto se puede mover con E2E_PORT. Estaba fijo en el 3000 y con `reuseExistingServer`, lo
 * que significa que si algo más escuchaba ahí —otro proyecto en la misma máquina— la suite corría
 * contra ESE servidor y fallaba con un timeout que no dice por qué.
 */
const PORT = process.env.E2E_PORT ?? "3000";
const BASE = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  /**
   * 60 s y no los 30 por defecto. Estos tests manejan un `next dev`, que compila cada ruta la
   * primera vez que alguien la pide: el primer test que toca una pantalla nueva paga ese compilado
   * y se pasaba del límite. No es lentitud del producto —el build de producción no compila nada en
   * caliente— sino de la herramienta con la que se prueba, y hacer fallar un test por eso solo
   * enseña a volver a correrlo.
   */
  timeout: 60_000,
  reporter: [["list"]],
  use: {
    baseURL: BASE,
    trace: "on-first-retry",
  },
  projects: [{ name: "movil", use: { ...devices["Pixel 7"] } }],
  webServer: {
    command: `npm run dev -- --port ${PORT}`,
    url: `${BASE}/login`,
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
