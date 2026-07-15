import { expect, test, type Page } from "@playwright/test";

/**
 * Humo del MVP: los caminos que no pueden romperse nunca. Asume backend + docker con la semilla.
 *
 * Lecciones ya aprendidas y aplicadas aquí:
 *  - esperar la hidratación (networkidle) antes de teclear, o el submit nativo pierde lo escrito;
 *  - getByRole con { exact: true } cuando un texto es subcadena de otro ("Asistió" ⊂ "No asistió").
 */

const USERS = {
  ana: { email: "ana@orion.local", pass: "orion123*" },
  maria: { email: "maria@orion.local", pass: "orion123*" },
  admin: { email: "admin@orion.local", pass: "admin123*" },
};

async function login(page: Page, user: { email: string; pass: string }) {
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.locator("#email").fill(user.email);
  await page.locator("#password").fill(user.pass);
  await page.getByRole("button", { name: "Entrar" }).click();
}

async function logout(page: Page) {
  await page.getByRole("button", { name: "Menú de usuario" }).click();
  await page.getByRole("button", { name: "Salir" }).click();
  await page.waitForURL("**/login");
}

test.describe.configure({ mode: "serial" });

test("cada rol entra y sale de su propio home", async ({ page }) => {
  await login(page, USERS.ana);
  await expect(page).toHaveURL(/\/profesores/);
  await logout(page);

  await login(page, USERS.maria);
  await expect(page).toHaveURL(/\/mis-clases/);
  await logout(page);

  await login(page, USERS.admin);
  await expect(page).toHaveURL(/\/admin\/usuarios/);
  await logout(page);
});

test("Ana reserva un cupo de María: aparece en Mis clases y desaparece de la agenda", async ({
  page,
}) => {
  await login(page, USERS.ana);
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await expect(page.getByText("Cupos disponibles")).toBeVisible();

  const cupos = page.locator("main .grid-cols-3 button");
  const hora = (await cupos.first().innerText()).trim();
  await cupos.first().click();
  await page.getByRole("button", { name: "Confirmar reserva" }).click();

  await expect(page).toHaveURL(/\/mis-clases/);
  await expect(page.getByText("¡Clase reservada!")).toBeVisible();

  // El cupo ya no está en la agenda de María.
  await page.goto("/profesores");
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
  await expect(page.locator("main .grid-cols-3 button", { hasText: hora })).toHaveCount(0);
});

test("María ve en sus próximas clases la reserva de Ana", async ({ page }) => {
  await login(page, USERS.maria);
  await expect(page.getByText("Ana Ramírez")).toBeVisible();
});

test("Ana reserva un cupo lejano y lo cancela: el cupo vuelve a la agenda", async ({ page }) => {
  await login(page, USERS.ana);
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await expect(page.getByText("Cupos disponibles")).toBeVisible();

  // El último día del rango (lunes de la semana que viene) está a más de 24 h → cancelable.
  const dias = page.locator("main .flex-wrap button");
  await dias.last().click();
  const cupos = page.locator("main .grid-cols-3 button");
  const hora = (await cupos.last().innerText()).trim();
  await cupos.last().click();
  await page.getByRole("button", { name: "Confirmar reserva" }).click();
  await expect(page).toHaveURL(/\/mis-clases/);

  // Puede haber más de una clase; tomamos el botón Cancelar que esté habilitado (el de la lejana).
  const cancelar = page.getByRole("button", { name: "Cancelar" }).and(page.locator(":not([disabled])")).first();
  await expect(cancelar).toBeEnabled();
  await cancelar.click();
  await expect(page.getByText("¿Cancelar esta clase?")).toBeVisible();
  await page.getByRole("button", { name: "Sí, cancelar" }).click();

  // El cupo cancelado vuelve a estar libre en la agenda.
  await page.goto("/profesores");
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
  await dias.last().click();
  await expect(page.locator("main .grid-cols-3 button", { hasText: hora })).toBeVisible();
});
