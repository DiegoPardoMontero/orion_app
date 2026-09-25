import { expect, test, type Page } from "@playwright/test";
import { api, contadorDeLaClase, entrar, SEMILLA, simularAula } from "./apoyo";

/**
 * La clase, con JaaS simulado (en local no hay app-id de 8x8). Lo que se prueba es de Orión: la
 * antesala, el marco del aula, la ventana flotante al minimizar y la hoja de cierre. El contador del
 * iframe falso es el testigo: si sigue subiendo tras minimizar y volver, la llamada nunca se cortó.
 */

async function proximaClaseDeAna(page: Page): Promise<string> {
  const r = await api(page, "GET", "/api/v1/me/bookings?scope=upcoming");
  const clases = r.json as { id: string; status: string }[];
  const clase = clases.find((c) => c.status === "CONFIRMED");
  expect(clase, "Ana necesita una clase confirmada por delante").toBeTruthy();
  return clase!.id;
}

async function entrarAClase(page: Page) {
  await entrar(page, SEMILLA.ana);
  const id = await proximaClaseDeAna(page);
  await simularAula(page, id);
  await page.goto(`/mis-clases/${id}/aula`);
  return id;
}

test("[e-antesala.1 e-antesala.2] la antesala cerrada dice cuándo abre y deja probar la cámara", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  const id = await proximaClaseDeAna(page);
  await page.goto(`/mis-clases/${id}/aula`);
  await expect(page.getByText("La sala abre 10 minutos antes de la hora.")).toBeVisible();
  await expect(page.getByRole("button", { name: /Entrar/ }).first()).toBeDisabled();
  await expect(page.getByText(/cámara|micrófono/i).first()).toBeVisible();
});

test("[e-antesala-abierta.1 e-antesala-abierta.2 e-aula.1 e-aula.2] se entra con la elección de micrófono y se ve el tiempo", async ({ page }) => {
  await entrarAClase(page);
  await expect(page.getByText("María te espera")).toBeVisible();
  // Micrófono apagado en la antesala: Jitsi arranca silenciado.
  await page.getByRole("button", { name: /micrófono/i }).first().click();
  await page.getByRole("button", { name: /^Entrar/ }).first().click();
  await expect(page.getByText(/Quedan \d+ min/)).toBeVisible();
  const opciones = await page.evaluate(() => (window as unknown as { __jitsi: { opciones: { configOverwrite: { startWithAudioMuted: boolean } } } }).__jitsi.opciones.configOverwrite);
  expect(opciones.startWithAudioMuted).toBe(true);
  await page.evaluate(() => (window as unknown as { __jitsi: { emitir: (e: string, d: unknown) => void } }).__jitsi.emitir("participantJoined", { displayName: "María Gómez" }));
  await expect(page.getByText("María entró a la clase")).toBeVisible();
});

test("[e-mini.1 e-mini.4 e-aula.3] minimizar no corta la clase y volver la agranda sin reconectar", async ({ page }) => {
  await entrarAClase(page);
  await page.getByRole("button", { name: /^Entrar/ }).first().click();
  await expect(page.frameLocator("iframe").first().locator("#t")).toBeVisible();
  const antes = await contadorDeLaClase(page);
  await page.getByRole("button", { name: /Minimizar/ }).click();
  await expect(page).toHaveURL(/\/mis-clases$/);
  await page.locator('a[href="/mensajes"]:visible').first().click();
  await expect(page).toHaveURL(/\/mensajes$/);
  await page.waitForTimeout(800);
  const enMensajes = await contadorDeLaClase(page);
  expect(enMensajes).toBeGreaterThan(antes);
  await page.getByRole("button", { name: "Volver a la clase" }).first().click();
  await expect(page).toHaveURL(/\/aula$/);
  await page.waitForTimeout(500);
  expect(await contadorDeLaClase(page)).toBeGreaterThan(enMensajes);
  expect(await page.evaluate(() => (window as unknown as { __montajes: number }).__montajes)).toBe(1);
  // «Salir» pregunta antes de colgar.
  await page.getByRole("button", { name: "Salir", exact: true }).click();
  await expect(page.getByText("¿Salir de la clase?")).toBeVisible();
  await page.getByRole("button", { name: "Seguir en clase" }).click();
  await expect(page.getByText("¿Salir de la clase?")).toHaveCount(0);
});

test("[e-mini.2 e-mini.3] la ventana se arrastra y se pega al lado; micrófono desde la ventana", async ({ page }) => {
  await entrarAClase(page);
  await page.getByRole("button", { name: /^Entrar/ }).first().click();
  await page.getByRole("button", { name: /Minimizar/ }).click();
  await expect(page).toHaveURL(/\/mis-clases$/);
  const ventana = page.locator(".clase-flotante");
  await expect(ventana).toBeVisible();
  // La transición de minimizar dura 420 ms: se mide cuando ya se asentó.
  await page.waitForTimeout(700);
  const antes = (await ventana.boundingBox())!;
  const pantalla = page.viewportSize()!;
  expect(antes.x + antes.width).toBeGreaterThan(pantalla.width / 2);
  // Se arrastra por la barra de arriba hacia la izquierda y, al soltar, se pega a ese lado.
  const barra = ventana.getByText(/En clase/);
  const b = (await barra.boundingBox())!;
  await page.mouse.move(b.x + 10, b.y + 5);
  await page.mouse.down();
  await page.mouse.move(40, b.y - 100, { steps: 8 });
  await page.mouse.up();
  await page.waitForTimeout(600);
  const despues = (await ventana.boundingBox())!;
  expect(despues.x).toBeLessThan(pantalla.width / 2);
  await ventana.getByRole("button", { name: "Silenciar micrófono" }).click();
  await expect(ventana.getByRole("button", { name: "Activar micrófono" })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { __comandos: string[] }).__comandos)).toContain("toggleAudio");
});

test("[e-mini.5 e-cierre.2 e-cierre.3] colgar desde la ventana lleva a la hoja de cierre", async ({ page }) => {
  await entrarAClase(page);
  await page.getByRole("button", { name: /^Entrar/ }).first().click();
  await page.getByRole("button", { name: /Minimizar/ }).click();
  await expect(page).toHaveURL(/\/mis-clases$/);
  await page.getByRole("button", { name: "Colgar" }).click();
  await expect(page).toHaveURL(/\/aula$/);
  await expect(page.getByText(/¿Cómo te fue con María\?/)).toBeVisible();
  await expect(page.getByRole("link", { name: /Reservar la siguiente con María/ })).toBeVisible();
  await page.getByRole("button", { name: "Ahora no" }).click();
  await expect(page).toHaveURL(/\/mis-clases/);
  await expect(page.locator(".clase-flotante")).toBeHidden();
});

test("[e-cierre.1] calificar al colgar manda las estrellas y el comentario", async ({ page }) => {
  const id = await entrarAClase(page);
  // La clase simulada no terminó de verdad (el backend rechazaría calificarla): se intercepta la
  // calificación para ver qué manda la hoja. Que se guarde y sume puntos lo prueba ReviewLifecycleIT.
  let enviado: unknown = null;
  await page.route(`**/api/v1/bookings/${id}/review`, async (r) => {
    enviado = r.request().postDataJSON();
    await r.fulfill({ status: 201, json: {} });
  });
  await page.getByRole("button", { name: /^Entrar/ }).first().click();
  await expect(page.frameLocator("iframe").first().locator("#t")).toBeVisible();
  await page.evaluate(() => (window as unknown as { __jitsi: { emitir: (e: string) => void } }).__jitsi.emitir("readyToClose"));
  const hoja = page.getByRole("dialog", { name: "La clase terminó" });
  await expect(hoja).toBeVisible();
  await expect(hoja.getByRole("button", { name: "Enviar" })).toBeDisabled();
  await hoja.getByRole("radio", { name: "4: Muy buena" }).click();
  await expect(hoja.getByText("Muy buena", { exact: true })).toBeVisible();
  await hoja.getByLabel("Comentario opcional").fill("Muy clara con los tiempos verbales.");
  await hoja.getByRole("button", { name: "Enviar" }).click();
  await expect(hoja).toBeHidden();
  expect(enviado).toEqual({ rating: 4, comment: "Muy clara con los tiempos verbales." });
});

test("[e-aula.4] si se cae la conexión, se puede volver a entrar", async ({ page }) => {
  await entrarAClase(page);
  await page.getByRole("button", { name: /^Entrar/ }).first().click();
  await expect(page.frameLocator("iframe").first().locator("#t")).toBeVisible();
  await page.evaluate(() => (window as unknown as { __jitsi: { emitir: (e: string) => void } }).__jitsi.emitir("connectionFailed"));
  await expect(page.getByRole("button", { name: "Volver a la sala" })).toBeVisible();
  await page.getByRole("button", { name: "Volver a la sala" }).click();
  await expect(page.frameLocator("iframe").first().locator("#t")).toBeVisible();
  await expect(page.getByText(/Quedan \d+ min/)).toBeVisible();
});

test("[e-antesala.3] cuando llega la hora, la sala se abre sin recargar", async ({ page }) => {
  await page.clock.install();
  await entrar(page, SEMILLA.ana);
  const id = await proximaClaseDeAna(page);
  const ahora = Date.now();
  const aula = await simularAula(page, id, {
    state: "CLOSED",
    opensAt: new Date(ahora + 30_000).toISOString(),
    startsAt: new Date(ahora + 10 * 60_000).toISOString(),
    endsAt: new Date(ahora + 65 * 60_000).toISOString(),
  });
  await page.goto(`/mis-clases/${id}/aula`);
  await expect(page.getByText(/La sala abre a las/)).toBeVisible();
  // La antesala vuelve a preguntar cada minuto; se adelanta el reloj en vez de esperarlo.
  aula.state = "OPEN";
  await page.clock.runFor(61_000);
  await expect(page.getByText("Sala abierta")).toBeVisible();
  await expect(page.getByRole("button", { name: /^Entrar/ }).first()).toBeEnabled();
});

test("[p-antesala.1 p-antesala.2 p-antesala.3] el profe abre la sala antes, ve si el estudiante entró y también minimiza", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  const r = await api(page, "GET", "/api/v1/me/bookings?scope=upcoming");
  const clase = (r.json as { id: string; status: string }[]).find((c) => c.status === "CONFIRMED");
  expect(clase, "María necesita una clase confirmada por delante").toBeTruthy();
  const ahora = Date.now();
  const aula = await simularAula(page, clase!.id, {
    state: "OPEN",
    moderator: true,
    counterpart: { name: "Ana Ramírez", firstName: "Ana", photoUrl: null, headline: null },
    counterpartPresent: false,
    displayName: "María Gómez",
    opensAt: new Date(ahora - 2 * 60_000).toISOString(),
    startsAt: new Date(ahora + 8 * 60_000).toISOString(),
    endsAt: new Date(ahora + 63 * 60_000).toISOString(),
  });
  await page.goto(`/mis-clases/${clase!.id}/aula`);
  await expect(page.getByText("Sala abierta")).toBeVisible();
  await expect(page.getByRole("button", { name: "Abrir la sala" })).toBeEnabled();

  // Ya empezó y Ana entró: la antesala lo dice.
  Object.assign(aula, {
    state: "STARTED",
    counterpartPresent: true,
    startsAt: new Date(ahora - 3 * 60_000).toISOString(),
    endsAt: new Date(ahora + 52 * 60_000).toISOString(),
  });
  await page.reload();
  await expect(page.getByText("Ana te espera")).toBeVisible();
  await page.getByRole("button", { name: "Entrar con Ana" }).click();
  await expect(page.frameLocator("iframe").first().locator("#t")).toBeVisible();

  // Y minimiza como el estudiante: la clase sigue en la ventana flotante.
  await page.getByRole("button", { name: /Minimizar/ }).click();
  await expect(page).toHaveURL(/\/mis-clases$/);
  await expect(page.locator(".clase-flotante")).toBeVisible();
  await expect(page.getByRole("button", { name: "Volver a la clase" }).first()).toBeVisible();
});

test("[e-mini.6] con la clase en curso, recargar pregunta antes de salir", async ({ page }) => {
  await entrarAClase(page);
  await page.getByRole("button", { name: /^Entrar/ }).first().click();
  await expect(page.frameLocator("iframe").first().locator("#t")).toBeVisible();
  // El navegador solo muestra la advertencia si hubo un gesto en la página.
  await page.mouse.click(10, 10);
  let pregunto = false;
  page.on("dialog", async (d) => {
    if (d.type() === "beforeunload") pregunto = true;
    await d.dismiss();
  });
  await page.reload({ timeout: 3000 }).catch(() => {});
  expect(pregunto).toBe(true);
});
