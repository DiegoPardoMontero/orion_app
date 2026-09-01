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
  // .first(): basta con que Ana aparezca entre sus clases; puede tener más de una reserva.
  await expect(page.getByText("Ana Ramírez").first()).toBeVisible();
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

  // Cancelamos exactamente la clase que acabamos de reservar (la de `hora`), no "la primera
  // cancelable": si Ana tiene varias reservas el mismo día, un .first() tocaría la equivocada
  // y el cupo que verificamos abajo nunca reaparecería.
  const cancelar = page.locator("main li", { hasText: hora }).getByRole("button", { name: "Cancelar" });
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

test("un estudiante nuevo se registra desde el login y aterriza dentro", async ({ page }) => {
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.getByRole("link", { name: "Crea tu cuenta" }).click();
  await expect(page).toHaveURL(/\/registro/);

  // Correo único por corrida: el registro es idempotente-hostil (un email solo se puede usar una vez).
  const email = `nuevo.${Date.now()}@orion.local`;
  await page.locator("#nombre").fill("Nueva Estudiante");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("orion123*");
  await page.getByRole("button", { name: "Crear cuenta" }).click();

  // El backend crea la cuenta y abre sesión de una vez: el estudiante cae en su home, ya dentro.
  await expect(page).toHaveURL(/\/profesores/);
  await expect(page.getByRole("heading", { name: "Profesores" })).toBeVisible();
});

test("un estudiante edita su perfil y persiste", async ({ page }) => {
  await login(page, USERS.ana);
  await page.goto("/cuenta");
  await expect(page.getByRole("heading", { name: "Mi perfil" })).toBeVisible();

  await page.locator("#telefono").fill("+573009998877");
  await page.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page.getByText("tus datos quedaron actualizados")).toBeVisible();

  // Recargar y comprobar que el dato se guardó de verdad.
  await page.reload();
  await expect(page.locator("#telefono")).toHaveValue("+573009998877");
});

test("un estudiante reprograma una clase a otro cupo", async ({ page }) => {
  await login(page, USERS.ana);

  // Aseguramos una clase reprogramable: reservamos un cupo lejano (> 24 h).
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
  await page.locator("main .flex-wrap button").last().click();
  const cupos = page.locator("main .grid-cols-3 button");
  await cupos.first().click();
  await page.getByRole("button", { name: "Confirmar reserva" }).click();
  await expect(page).toHaveURL(/\/mis-clases/);

  // Abrimos Reprogramar en la primera clase habilitada y elegimos un nuevo cupo.
  const reprogramar = page
    .getByRole("button", { name: "Reprogramar" })
    .and(page.locator(":not([disabled])"))
    .first();
  await reprogramar.click();

  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText("Elige un día")).toBeVisible();
  await dialog.locator(".flex-wrap button").last().click();
  await dialog.locator(".grid-cols-3 button").last().click();
  await dialog.getByRole("button", { name: "Confirmar cambio" }).click();

  // Éxito: el modal se cierra. Si hubiera fallado (cupo ocupado, etc.) seguiría abierto con aviso.
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("recuperar contraseña: pide enlace y rechaza un token inválido", async ({ page }) => {
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.getByRole("link", { name: "¿Olvidaste tu contraseña?" }).click();
  await expect(page).toHaveURL(/\/recuperar/);

  await page.locator("#email").fill("ana@orion.local");
  await page.getByRole("button", { name: "Enviar enlace" }).click();
  // Mensaje neutro (no revela si el correo existe).
  await expect(page.getByText("Revisa tu correo")).toBeVisible();

  // Un token inválido no cambia nada: el backend responde 422 y se ve el aviso.
  await page.goto("/restablecer?token=token-invalido-de-prueba");
  await page.locator("#password").fill("clave-nueva-1");
  await page.getByRole("button", { name: "Guardar contraseña" }).click();
  await expect(page.getByText(/no es válido|expiró/i)).toBeVisible();
});
