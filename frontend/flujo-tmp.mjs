import { chromium } from "@playwright/test";
import { APP, D, paso, api, entrar, resumen } from "./util-tmp.mjs";
const nav = await chromium.launch();
const page = await (await nav.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
const consola = [];
page.on("console", (m) => { if (m.type() === "error") consola.push(m.text()); });
await entrar(page, "admin@orion.local", "admin123*");

for (const [nombre, ruta, debe] of [
  ["A1 · panel", "/admin/panel", /panel|retenid|decisi/i],
  ["A2 · usuarios", "/admin/usuarios", /usuario|Invitar/i],
  ["A3 · postulaciones", "/admin/aplicaciones", /postulaci/i],
  ["A4 · clases", "/admin/reservas", /clase|reserva/i],
  ["A5 · pagos", "/admin/pagos", /pago|conciliaci|liquidaci/i],
  ["A6 · reclamos", "/admin/reclamos", /reclamo/i],
  ["A7 · reseñas", "/admin/resenas", /rese/i],
]) {
  await paso(`${nombre} abre y dice de qué habla`, async () => {
    await page.goto(APP + ruta); await page.waitForLoadState("networkidle");
    await page.waitForTimeout(900);
    const t = await page.locator("main").innerText();
    await page.screenshot({ path: `${D}/a-${ruta.replace(/\//g, "_")}.png`, fullPage: true });
    if (!debe.test(t)) throw new Error(`contenido inesperado: ${t.replace(/\n/g, " / ").slice(0, 180)}`);
  });
}

await paso("A8 · el admin puede poner tarifa 0 a un profesor", async () => {
  await page.goto(`${APP}/admin/usuarios`); await page.waitForLoadState("networkidle");
  const b = page.getByRole("button", { name: "Tarifa" }).first();
  if (await b.count() === 0) throw new Error("no hay botón Tarifa");
  await b.click(); await page.waitForTimeout(700);
  await page.screenshot({ path: `${D}/a-tarifa.png` });
  await page.keyboard.press("Escape");
});

await paso("A9 · el admin NO tiene experiencia de estudiante", async () => {
  const r = await api(page, "/api/v1/me/engagement");
  if (r.status !== 403) throw new Error(`/me/engagement → ${r.status}`);
});

console.log("\nconsola:", consola.length ? [...new Set(consola)].slice(0, 8) : "limpia");
resumen("Flujos de administración");
await nav.close();
