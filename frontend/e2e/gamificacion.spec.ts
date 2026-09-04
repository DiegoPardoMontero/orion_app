import { expect, test, type Page } from "@playwright/test";

/**
 * El Bloque 8 de punta a punta. Asume backend + docker con la semilla, como el humo.
 *
 * Un tramo del recorrido del brief NO está aquí y es a propósito: «toma su primera clase y se
 * encienden dos estrellas». Cerrar una clase exige que la clase haya terminado, y todos los cupos
 * que la semilla ofrece son futuros — no hay forma de llegar a eso desde el navegador sin inventar
 * un endpoint para forzar el reloj. Ese tramo lo cubren dos tests de integración del backend
 * (`unaClaseCompletadaConcedeSusPuntosYEnciendeLaPrimeraEstrella` y
 * `laAsistenciaRegistradaPorElProfesorTambienEnciendeLaEstrella`), que sí pueden congelar el
 * `Clock`. Lo que sí se prueba aquí es todo lo que un estudiante puede provocar por sí mismo.
 */

const USERS = {
  ana: { email: "ana@orion.local", pass: "orion123*" },
  carlos: { email: "carlos@orion.local", pass: "orion123*" },
};

async function login(page: Page, user: { email: string; pass: string }) {
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.locator("#email").fill(user.email);
  await page.locator("#password").fill(user.pass);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page).toHaveURL(/\/profesores/);
}

async function logout(page: Page) {
  await page.getByRole("button", { name: "Menú de usuario" }).click();
  await page.getByRole("button", { name: "Salir" }).click();
  await page.waitForURL("**/login");
}

test.describe.configure({ mode: "serial" });

test("Ana declara su objetivo: se enciende una estrella y llega la notificación", async ({
  page,
}) => {
  await login(page, USERS.ana);
  await page.goto("/cuenta");
  await expect(page.getByRole("heading", { name: "Mi ficha" })).toBeVisible();

  await page.getByRole("button", { name: "Intermedio" }).click();
  // El primer objetivo del catálogo, sea cual sea: lo que importa es que haya uno declarado.
  const objetivos = page.locator("fieldset", { hasText: "¿Para qué lo aprendes?" });
  await objetivos.getByRole("button").first().click();
  await page.getByRole("button", { name: "Guardar mi ficha" }).click();
  await expect(page.getByText("Tu profesor ya lo puede ver")).toBeVisible();

  // El encendido: la celebración salta sola, sin haber entrado al tablero de logros.
  await expect(page.getByText("Estrella encendida")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Objetivo declarado" })).toBeVisible();

  // Y la estrella queda encendida en el cielo. Dentro de <main>: el título de la celebración
  // lleva el mismo nombre, y sin acotar se resuelven dos elementos.
  await page.goto("/logros");
  await expect(page.getByRole("heading", { name: "Tu cielo" })).toBeVisible();
  await expect(page.getByRole("main").getByText("Objetivo declarado")).toBeVisible();

  // Y hay una notificación por ello. Una sola, aunque se hayan encendido varias.
  await page.getByRole("button", { name: /^Notificaciones/ }).click();
  await expect(page.getByText(/Encendiste .*estrella/).first()).toBeVisible();
});

test("Ana equipa una pieza desbloqueada y sigue puesta al recargar", async ({ page }) => {
  await login(page, USERS.ana);
  await page.goto("/logros/avatar");
  await expect(page.getByRole("heading", { name: "Tu avatar" })).toBeVisible();

  // Por el nombre exacto de la pieza: hay «Órbita», «Órbita doble» y «Órbita amanecer», y un
  // nombre que sea prefijo de otro elige la equivocada.
  const pieza = (nombre: string) =>
    page.locator("button").filter({ has: page.getByText(nombre, { exact: true }) });

  // «Órbita» se desbloquea con la primera clase, que Ana ya tiene en la semilla.
  await expect(pieza("Órbita")).toBeEnabled();
  await pieza("Órbita").click();
  await page.getByRole("button", { name: "Guardar" }).click();
  await expect(page.getByText("Guardado")).toBeVisible();

  await page.reload();
  await expect(pieza("Órbita")).toHaveAttribute("aria-pressed", "true");

  // Y lo bloqueado sigue a la vista, con su condición legible y sin poder pulsarse.
  await expect(pieza("Corona constelación")).toBeDisabled();
  // La condición se lee en español, no como código de logro: «24 semanas consecutivas», nunca
  // `constancia-24-semanas`.
  await expect(pieza("Corona constelación")).toContainText(/semanas consecutivas/);
});

/**
 * Que el frontend solo enseñe lo desbloqueado es comodidad; la comprobación que manda es la del
 * servidor. Aquí se salta la pantalla entera y se pide por API una pieza que Ana no tiene.
 */
test("equipar una pieza bloqueada por API responde 422", async ({ page }) => {
  await login(page, USERS.ana);

  const respuesta = await page.request.put("http://localhost:8080/api/v1/me/cosmetics", {
    headers: { "X-XSRF-TOKEN": await tokenCsrf(page) },
    data: { frameCode: "cielo", paletteCode: "trazo", skyCode: "crema", accessories: [] },
  });

  expect(respuesta.status()).toBe(422);
});

test("el perfil público se enciende y se apaga: Carlos lo ve y deja de verlo", async ({ page }) => {
  await login(page, USERS.ana);
  await page.goto("/cuenta");
  await expect(page.getByRole("heading", { name: "Mi ficha" })).toBeVisible();

  const idAna = await page.evaluate(async () => {
    const r = await fetch("/api/v1/auth/me", { credentials: "include" });
    return (await r.json()).id as string;
  });

  await page.getByRole("button", { name: "Hacerlo visible" }).click();
  // La fecha solo se pide la primera vez; si ya estaba puesta, el botón publica directo.
  const fecha = page.locator("#nacimiento");
  if (await fecha.isVisible()) {
    await fecha.fill("1995-04-12");
    await page.getByRole("button", { name: "Hacerlo visible" }).click();
  }
  await expect(page.getByText("Tu ficha es pública")).toBeVisible();
  await logout(page);

  await login(page, USERS.carlos);
  await page.goto(`/estudiantes/${idAna}`);
  await expect(page.getByRole("heading", { name: "Ana Ramírez" })).toBeVisible();
  await logout(page);

  await login(page, USERS.ana);
  await page.goto("/cuenta");
  await page.getByRole("button", { name: "Volverlo privado" }).click();
  await expect(page.getByText("Tu ficha es privada")).toBeVisible();
  await logout(page);

  await login(page, USERS.carlos);
  await page.goto(`/estudiantes/${idAna}`);
  // Nunca «no tienes permiso»: eso confirmaría que el perfil existe. El servidor responde 404.
  await expect(page.getByRole("heading", { name: "No encontramos este perfil" })).toBeVisible();
});

/** El token CSRF que el backend deja en una cookie legible por JS. */
async function tokenCsrf(page: Page): Promise<string> {
  const cookies = await page.context().cookies();
  return cookies.find((c) => c.name === "XSRF-TOKEN")?.value ?? "";
}
