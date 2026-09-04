export const APP = "http://localhost:3000";
export const D = process.env.CLAUDE_JOB_DIR + "/tmp/shots";
export const fallos = [];
export async function paso(n, fn) {
  try { const r = await fn(); console.log(`  ok    ${n}`); return r; }
  catch (e) { const m = String(e).split("\n")[0]; fallos.push(`${n} → ${m}`); console.log(`FALLA   ${n}\n        ${m}`); }
}
export async function api(page, path, o = {}) {
  return page.evaluate(async ([p, op]) => {
    const x = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="))?.split("=")[1] ?? "";
    const r = await fetch(p, { ...op, credentials: "include", headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": decodeURIComponent(x), ...(op.headers ?? {}) } });
    const t = await r.text(); try { return { status: r.status, body: JSON.parse(t) }; } catch { return { status: r.status, body: t }; }
  }, [path, o]);
}
export async function entrar(page, email, pass) {
  await page.goto(`${APP}/login`); await page.waitForLoadState("networkidle");
  await page.locator("#email").fill(email); await page.locator("#password").fill(pass);
  await page.getByRole("button", { name: "Entrar" }).click(); await page.waitForTimeout(2500);
}
export function resumen(titulo) {
  console.log(`\n=== ${titulo}: ${fallos.length ? fallos.length + " fallos" : "todo verde"} ===`);
  fallos.forEach((f) => console.log("  · " + f));
}
