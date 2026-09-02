import { expect, test, type Page } from "@playwright/test";

/**
 * Humo del MVP: los caminos que no pueden romperse nunca. Asume backend + docker con la semilla.
 *
 * Desde el Bloque 4 reservar cobra. Ana llega con saldo a favor sembrado (BillingDevSeeder), así
 * que sus clases se cubren con ese saldo y se confirman sin pasarela: todo lo que ya se probaba
 * aquí —sala virtual, cancelar, reprogramar— sigue igual. El camino con pasarela lo cubre la
 * prueba del final, con una estudiante recién registrada (sin saldo), que llega hasta la puerta de
 * Wompi y la intercepta. El otro lado —webhook, confirmación, expiración— lo cubren los tests de
 * integración del backend, que sí pueden firmar eventos.
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
  // La clase virtual trae su sala de videollamada automática (Jitsi).
  await expect(page.getByRole("link", { name: "Unirse a la clase" }).first()).toBeVisible();

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
  // Esperar a que el login termine (sesión establecida) antes de navegar, o /cuenta rebota a login.
  await expect(page).toHaveURL(/\/profesores/);
  await page.goto("/cuenta");
  await expect(page.getByRole("heading", { name: "Mi perfil" })).toBeVisible();

  // #telefono es el número local del PhoneInput (el país va aparte, Colombia por defecto).
  await page.locator("#telefono").fill("3009998877");
  await page.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page.getByText("tus datos quedaron actualizados")).toBeVisible();

  // Recargar y comprobar que el dato se guardó de verdad (se re-parsea del E.164 +57...).
  await page.reload();
  await expect(page.locator("#telefono")).toHaveValue("3009998877");
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

/**
 * Sin saldo, reservar lleva a la pasarela. Se corta la salida a Wompi y se comprueba lo que de
 * verdad importa desde este lado: que el checkout se arma con importe, moneda y firma, y que el
 * cupo queda apartado aunque el pago no haya entrado.
 *
 * Necesita claves de SANDBOX de Wompi en el backend (WOMPI_PUBLIC_KEY / WOMPI_INTEGRITY_SECRET con
 * prefijo _test_); sin ellas el backend no puede preparar un cobro y reservar responde 422.
 */
test("una estudiante sin saldo sale hacia Wompi y su cupo queda apartado", async ({ page }) => {
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.getByRole("link", { name: "Crea tu cuenta" }).click();

  const email = `pagadora.${Date.now()}@orion.local`;
  await page.locator("#nombre").fill("Paula Pagadora");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("orion123*");
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await expect(page).toHaveURL(/\/profesores/);

  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
  const cupos = page.locator("main .grid-cols-3 button");
  const hora = (await cupos.first().innerText()).trim();
  await cupos.first().click();

  await page.route("https://checkout.wompi.co/**", (route) => route.abort());
  const [request] = await Promise.all([
    page.waitForRequest("https://checkout.wompi.co/**"),
    page.getByRole("button", { name: "Continuar al pago" }).click(),
  ]);

  const checkout = request.url();
  // Sin firma de integridad Wompi rechaza el cobro; sin importe en centavos, cobra cualquier cosa.
  expect(checkout).toContain("amount-in-cents=");
  expect(checkout).toContain("signature:integrity=");
  expect(checkout).toContain("currency=COP");

  // El cupo se aparta desde ya: si no, dos estudiantes llegarían al checkout por el mismo horario
  // y el segundo pagaría una clase que ya no existe.
  await page.goto("/mis-clases");
  await expect(page.getByText("Te guardamos el cupo mientras pagas").first()).toBeVisible();

  await page.goto("/profesores");
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
  await expect(page.locator("main .grid-cols-3 button", { hasText: hora })).toHaveCount(0);
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

test("la landing pública lleva al registro en un clic", async ({ page }) => {
  await page.goto("/");
  // Portada marketplace (Bloque 7): el titular cambió al nuevo mensaje en español. El botón "Crea
  // tu cuenta" del hero (isla HeroCta, anónimo) sigue siendo el enlace del funnel hacia /registro.
  await expect(
    page.getByRole("heading", { name: /Encuentra al profesor indicado/i }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Crea tu cuenta" }).first().click();
  await expect(page).toHaveURL(/\/registro/);
});

test("el admin invita a un profesor; un enlace inválido se rechaza", async ({ page }) => {
  await login(page, USERS.admin);
  await expect(page).toHaveURL(/\/admin\/usuarios/);

  await page.getByRole("button", { name: "Invitar profesor" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.locator("#invite-email").fill(`profe.${Date.now()}@orion.local`);
  await dialog.getByRole("button", { name: "Enviar invitación" }).click();
  await expect(page.getByText(/Le enviamos la invitación/)).toBeVisible();

  // Un enlace de invitación inválido no deja pasar.
  await page.goto("/invitacion?token=token-inventado");
  await expect(page.getByRole("heading", { name: "Invitación no válida" })).toBeVisible();
});
