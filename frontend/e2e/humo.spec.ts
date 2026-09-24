import { expect, test, type Page } from "@playwright/test";
import { aceptarCondiciones, saltarRecorrido, verificarCorreo } from "./apoyo";

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

/**
 * El cupo se anuncia como «8:00 AM» y la tarjeta de la clase lo muestra como «8:00 – 9:00 AM»: el
 * meridiano va una sola vez, al final del rango. Buscar la tarjeta por el texto del cupo no
 * encuentra nada, y buscarla solo por «8:00» encuentra también la de las 8 de la noche.
 */
function comoEnLaTarjeta(hora: string): RegExp {
  const [inicio, meridiano] = hora.split(/\s+/);
  return new RegExp(`${inicio.replace(":", "\\:")}\\s*[–-]\\s*\\d{1,2}\\:\\d{2}\\s*${meridiano}`);
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
  // El admin entra al panel: lo primero que necesita ver es si algo espera su decisión.
  await expect(page).toHaveURL(/\/admin\/panel/);
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
  const cancelar = page
    .locator("main li")
    .filter({ hasText: comoEnLaTarjeta(hora) })
    .getByRole("button", { name: "Cancelar" });
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
  await aceptarCondiciones(page);
  await page.getByRole("button", { name: "Crear cuenta" }).click();

  // El backend crea la cuenta y abre sesión de una vez: el estudiante cae en su home, ya dentro.
  await expect(page).toHaveURL(/\/profesores/);
  await expect(page.getByRole("heading", { name: "Profesores" })).toBeVisible();

  // Y lo recibe el recorrido guiado (handoff §7): va de pantalla en pantalla, se puede retomar si
  // se cierra a mitad y, terminado, no vuelve.
  const recorrido = page.getByRole("dialog");
  await expect(recorrido.getByRole("heading", { name: "Te muestro Orión en 6 pasos" })).toBeVisible();
  await recorrido.getByRole("button", { name: "Empezar" }).click();
  await expect(recorrido.getByText("1 de 6")).toBeVisible();
  await expect(recorrido.getByRole("heading", { name: "Encuentra tu profe" })).toBeVisible();
  await expect(recorrido.getByRole("button", { name: "Atrás" })).toBeDisabled();
  await recorrido.getByRole("button", { name: "Siguiente" }).click();
  // El paso 2 lleva al perfil de un profesor, a sus horarios.
  await expect(recorrido.getByText("2 de 6")).toBeVisible();
  await expect(page).toHaveURL(/\/profesores\/[0-9a-f-]+$/);
  await recorrido.getByRole("button", { name: "Siguiente" }).click();
  await expect(recorrido.getByText("3 de 6")).toBeVisible();
  await expect(page).toHaveURL(/\/mis-clases/);

  // Cerrar a mitad: al volver, Rigel pregunta si seguimos.
  await page.reload();
  await expect(recorrido.getByRole("heading", { name: "¿Seguimos donde íbamos?" })).toBeVisible();
  await recorrido.getByRole("button", { name: "Seguir" }).click();
  await expect(recorrido.getByText("3 de 6")).toBeVisible();
  await recorrido.getByRole("button", { name: "Siguiente" }).click();
  await expect(recorrido.getByText("4 de 6 · Meissa")).toBeVisible();
  await recorrido.getByRole("button", { name: "Siguiente" }).click();
  await expect(recorrido.getByText("5 de 6")).toBeVisible();
  await expect(page).toHaveURL(/\/cuenta\?seccion=cielo/);
  await recorrido.getByRole("button", { name: "Siguiente" }).click();
  await expect(recorrido.getByText("6 de 6")).toBeVisible();
  await recorrido.getByRole("button", { name: "Terminar" }).click();
  await expect(recorrido.getByRole("heading", { name: "¡Listo! Ya conoces Orión." })).toBeVisible();
  await recorrido.getByRole("button", { name: "Buscar profe" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page).toHaveURL(/\/profesores$/);
  await page.reload();
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { name: "Profesores" })).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

/**
 * La clase de prueba (Q7): María la ofrece gratis desde su perfil y una estudiante nueva —que aún
 * no tiene clases con ella— la reserva; sin pasarela, queda confirmada y marcada como prueba.
 */
test("María ofrece una clase de prueba gratis y una estudiante nueva la reserva", async ({ page }) => {
  await login(page, USERS.maria);
  await page.goto("/perfil");
  await page.getByRole("button", { name: "Editar mi perfil" }).click();
  // El interruptor nace encendido, pero sin precio no es una oferta.
  await page.locator("#precio-prueba").fill("0");
  await page.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page.getByText("Listo, tu perfil quedó actualizado.")).toBeVisible();
  await logout(page);

  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.getByRole("link", { name: "Crea tu cuenta" }).click();
  const email = `prueba.${Date.now()}@orion.local`;
  await page.locator("#nombre").fill("Pía Prueba");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("orion123*");
  await aceptarCondiciones(page);
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await expect(page).toHaveURL(/\/profesores/);
  await saltarRecorrido(page);
  await verificarCorreo(page, email);

  await page.goto("/profesores");
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await page.waitForURL(/\/profesores\/[0-9a-f-]+$/);
  const perfilDeMaria = new URL(page.url()).pathname;
  await expect(page.getByText("Clase de prueba gratis")).toBeVisible();
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
  await page.locator("main .grid-cols-3 button").first().click();
  await page.getByRole("button", { name: /^Clase de prueba/ }).click();
  await page.getByRole("button", { name: "Confirmar reserva" }).click();

  await expect(page).toHaveURL(/\/mis-clases/);
  await expect(page.getByText("Clase de prueba").first()).toBeVisible();

  // Una sola por pareja: de vuelta en el perfil de María ya no se ofrece. Se entra por la dirección
  // y no con un clic: la celebración de «Primera reserva» puede estar encima.
  await page.goto(perfilDeMaria);
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
  await expect(page.getByRole("button", { name: /^Clase de prueba/ })).toHaveCount(0);
});

/**
 * Sin cuenta (decisión del 23/09/2026): el catálogo y el perfil de un profesor se ven —es el enlace
 * que el profesor comparte en sus redes—, y reservar pide entrar; al entrar, se vuelve al mismo
 * perfil, ya con su agenda.
 */
test("sin cuenta se ven los profesores; al entrar para reservar, vuelve al mismo perfil", async ({ page }) => {
  await page.goto("/profesores");
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await page.waitForURL(/\/profesores\/[0-9a-f-]+$/);
  const perfil = new URL(page.url()).pathname;
  await expect(page.getByText("Reserva tu clase")).toBeVisible();

  await page.getByRole("link", { name: "Ya tengo cuenta" }).click();
  await expect(page).toHaveURL(/\/login\?volver=/);
  await page.waitForLoadState("networkidle");
  await page.locator("#email").fill(USERS.ana.email);
  await page.locator("#password").fill(USERS.ana.pass);
  await page.getByRole("button", { name: "Entrar" }).click();

  await page.waitForURL((u) => u.pathname === perfil);
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
  await logout(page);
});

test("un estudiante edita su perfil y persiste", async ({ page }) => {
  await login(page, USERS.ana);
  // Esperar a que el login termine (sesión establecida) antes de navegar, o /cuenta rebota a login.
  await expect(page).toHaveURL(/\/profesores/);
  // Los datos de contacto van con la ficha, en «Solo para ti» (24/09): el enlace viejo sigue sirviendo.
  await page.goto("/cuenta?seccion=datos");
  await expect(page.getByRole("heading", { name: "Mi perfil" })).toBeVisible();

  // #telefono es el número local del PhoneInput (el país va aparte, Colombia por defecto).
  await page.locator("#telefono").fill("3009998877");
  await page.getByRole("button", { name: "Guardar mis datos" }).click();
  await expect(page.getByText("tus datos quedaron actualizados")).toBeVisible();

  // Recargar y comprobar que el dato se guardó de verdad (se re-parsea del E.164 +57...).
  await page.reload();
  await expect(page.locator("#telefono")).toHaveValue("3009998877");
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
  await aceptarCondiciones(page);
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await expect(page).toHaveURL(/\/profesores/);
  await saltarRecorrido(page);

  // Desde el Bloque 9 una cuenta sin correo verificado puede mirar pero no reservar. Se verifica
  // por el camino real —el enlace que llegó al buzón— y no por SQL: ese camino cruza el correo,
  // que es justo lo que más fácil se rompe sin que nadie se entere.
  await verificarCorreo(page, email);

  await page.goto("/profesores");
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

/**
 * El camino del profesor, que antes no existía: llegaba al login, se registraba como estudiante y
 * tenía que descubrir por su cuenta dónde estaba la postulación. Ahora el login lo dice y el
 * registro lo deja directamente en su wizard.
 */
test("un profesor se postula desde el login y aterriza en su postulación", async ({ page }) => {
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.getByRole("link", { name: "Postúlate para dar clases" }).click();

  await expect(page).toHaveURL(/\/registro\?rol=profesor/);
  // La intención llega preseleccionada y el copy cambia con ella.
  await expect(page.getByText("Tu perfil aparece en el marketplace cuando la aprobamos")).toBeVisible();

  const email = `profe.${Date.now()}@orion.local`;
  await page.locator("#nombre").fill("Profe Nuevo");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("orion123*");
  await aceptarCondiciones(page);
  await page.getByRole("button", { name: "Crear cuenta y postularme" }).click();

  // No al buscador de profesores: a su propia postulación.
  await expect(page).toHaveURL(/\/aplicacion/);
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

/**
 * La portada de Sofía (23/09/2026): el buscador rápido lleva al directorio ya filtrado y sin pedir
 * cuenta —«Trabajo» son negocios y entrevistas; «Fin de semana», sábado y domingo—, y crear la
 * cuenta sigue a un clic desde la cabecera.
 */
test("la portada: del buscador al directorio sin cuenta, y a crear la cuenta en un clic", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Encuentra tu profesor. Aprende a tu manera." })).toBeVisible();
  await expect(page.getByRole("link", { name: "Hacer mi diagnóstico gratis" }).first()).toBeVisible();

  await page.getByRole("radio", { name: "Trabajo" }).click();
  await page.getByRole("radio", { name: "Fin de semana" }).click();
  await page.getByRole("link", { name: "Ver profesores disponibles" }).click();
  await expect(page).toHaveURL(/\/profesores\?goal=BUSINESS&goal=INTERVIEW&day=SATURDAY&day=SUNDAY/);
  await expect(page.getByRole("heading", { name: "Profesores" })).toBeVisible();

  await page.goto("/");
  await page.getByRole("button", { name: "Abrir menú" }).click();
  await page.getByRole("link", { name: "Crear cuenta" }).click();
  await expect(page).toHaveURL(/\/registro/);
});

test("el admin invita a un profesor; un enlace inválido se rechaza", async ({ page }) => {
  await login(page, USERS.admin);
  // Esperar a que la sesión quede establecida antes de navegar: si no, /admin/usuarios rebota a
  // login. El admin aterriza en el panel, no en usuarios.
  await expect(page).toHaveURL(/\/admin\/panel/);
  await page.goto("/admin/usuarios");

  await page.getByRole("button", { name: "Invitar profesor" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.locator("#invite-email").fill(`profe.${Date.now()}@orion.local`);
  await dialog.getByRole("button", { name: "Enviar invitación" }).click();
  await expect(page.getByText(/Le enviamos la invitación/)).toBeVisible();

  // Un enlace de invitación inválido no deja pasar.
  await page.goto("/invitacion?token=token-inventado");
  await expect(page.getByRole("heading", { name: "Invitación no válida" })).toBeVisible();
});

/**
 * El acta (Bloque 10): María cuenta la clase que se cerró desde que existen las actas, revisa el
 * borrador y lo publica; Ana lo encuentra en sus clases pasadas y lo lee. Sin IA en local: el
 * borrador lo arma la regla simple, y cada término entre comillas se vuelve una palabra nueva.
 */
test("María escribe y publica el acta de una clase; Ana la lee", async ({ page }) => {
  await login(page, USERS.maria);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  await page.goto("/mis-clases?scope=past");
  const porEscribir = page.getByRole("region", { name: "Actas por escribir" });
  await porEscribir.getByRole("link", { name: /Ana Ramírez/ }).first().click();

  await page.locator("#notas").fill("Trabajamos past simple; sigue diciendo 'I go yesterday' y le costó 'used to'.");
  await page.getByRole("button", { name: "Generar acta" }).click();
  await expect(page.getByRole("button", { name: "Publicar", exact: true })).toBeVisible();
  await expect(page.getByText("used to", { exact: true })).toBeVisible();
  // Corrige una sección antes de publicar: lo que se publica es lo que está en pantalla.
  await page.getByLabel("Lo que sigue").fill("La próxima: condicionales.");
  await page.getByRole("button", { name: "Publicar", exact: true }).click();
  await expect(page.getByText(/^Publicada · /)).toBeVisible();
  // Publicada se lee, con hasta cuándo se puede corregir.
  await expect(page.getByText(/^Puedes corregirla hasta el/)).toBeVisible();
  await page.goto("/mis-clases?scope=past");
  await logout(page);

  await login(page, USERS.ana);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  await page.goto("/mis-clases?scope=past");
  const resumenes = page.getByRole("region", { name: "Resúmenes de tus clases" });
  await resumenes.getByRole("link", { name: /María Gómez/ }).first().click();
  await expect(page.getByRole("heading", { name: "Resumen de tu clase" })).toBeVisible();
  // «Trabajamos past simple; sigue…» es el acta; la invitación de abajo lo resume sin el punto y coma.
  await expect(page.getByText(/Trabajamos past simple; sigue/)).toBeVisible();
  await expect(page.getByText("La próxima: condicionales.")).toBeVisible();
  // Lo que escribió en crudo es su cuaderno: no llega aquí.
  await expect(page.getByText("Tus notas originales")).toHaveCount(0);
});

/**
 * Responde el ejercicio que esté en pantalla, sea del tipo que sea. `vuelta` 0 es el primer intento y
 * 1 el segundo: en el segundo se elige otra cosa, porque lo mismo ya se sabe que no era.
 */
async function responder(page: Page, vuelta: number) {
  const comprobar = page.getByRole("button", { name: "Comprobar" });
  const seguir = page.getByRole("button", { name: "Seguir", exact: true });
  const casi = page.getByRole("button", { name: "Intentar otra vez" });
  const listo = async () => (await comprobar.getAttribute("aria-disabled")) !== "true";

  const parejas = page.getByRole("group", { name: "Palabras en inglés" });
  if (await parejas.isVisible()) {
    // Cada término con el primer significado libre: los que no van gastan intento, y a los dos fallos
    // se muestran los que faltaban.
    for (let k = 0; k < 6 && !(await seguir.isVisible()); k++) {
      if (await casi.isVisible()) await casi.click();
      const termino = parejas.locator("button:not([aria-disabled])").first();
      const significado = page
        .getByRole("group", { name: "Significados en español" })
        .locator("button:not([aria-disabled])")
        .first();
      if (!(await termino.isVisible())) break;
      await termino.click();
      await significado.click();
      await expect(termino.or(seguir).or(casi).first()).toBeVisible();
      await page.waitForTimeout(300);
    }
    return;
  }

  const campo = page.locator("textarea:not([disabled])");
  if (await campo.isVisible()) {
    const etiqueta = await page.locator("label:has(textarea) > span").first().textContent();
    if (etiqueta === "Tu frase") {
      const termino = (await page.getByText(/^Escribe una frase tuya con «/).first().textContent())?.match(/«(.+)»/)?.[1] ?? "used to";
      await campo.fill(vuelta === 0 ? `Last year I ${termino} every weekend with my friends.` : `I think ${termino} is useful at work.`);
    } else {
      await campo.fill(vuelta === 0 ? "used to" : "I used to go there.");
    }
  } else if (await page.getByRole("radiogroup", { name: "Opciones" }).isVisible()) {
    const opciones = page.getByRole("radio");
    await (vuelta === 0 ? opciones.first() : opciones.last()).click();
  } else if (await page.getByRole("list", { name: "Conversación para ordenar" }).isVisible()) {
    await page.getByRole("button", { name: /^Bajar:/ }).nth(vuelta).click();
  } else if (await page.getByRole("group", { name: "Respuestas sugeridas" }).isVisible()) {
    const sugeridas = page.getByRole("group", { name: "Respuestas sugeridas" }).locator("button:not([aria-hidden])");
    await expect(sugeridas.first()).not.toHaveAttribute("aria-disabled", "true");
    await (vuelta === 0 ? sugeridas.first() : sugeridas.last()).click();
  } else {
    // Fichas (completa, arma la frase) o palabras (caza el error): se tocan hasta poder comprobar.
    const grupo = page.getByRole("group", { name: /^(Fichas|Palabras de la frase)$/ });
    const fichas = grupo.getByRole("button");
    for (let k = 0; k < 12 && !(await listo()); k++) {
      const n = await fichas.count();
      if (n === 0) break;
      await fichas.nth(vuelta === 0 ? 0 : n - 1).click();
    }
    if (!(await listo())) {
      // Arma la frase, segundo intento: las fichas vuelven puestas como estaban. Se saca la primera y
      // se pone al final, y así el orden ya es otro.
      await page.getByRole("group", { name: /^Tu frase/ }).getByRole("button").first().click();
      await fichas.first().click();
    }
  }
  await comprobar.click();
}

/**
 * La práctica (Bloque 10, Parte B), de punta a punta con el generador sin IA: del acta que María
 * acaba de publicar sale el set; Ana entra desde «Practicar esto», resuelve los ejercicios, ve el
 * cierre con sus puntos, y María lo ve en la ficha de Ana como un resumen, sin respuestas.
 */
test("Ana practica lo de su clase y María lo ve en su ficha", async ({ page }) => {
  await login(page, USERS.ana);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  await page.goto("/mis-clases?scope=past");
  await page.getByRole("region", { name: "Resúmenes de tus clases" }).getByRole("link", { name: /María Gómez/ }).first().click();
  await page.waitForURL(/\/acta$/);

  // El set se genera en segundo plano (cada 3 s en local): al pie del resumen, «Practicar» aparece
  // cuando está listo (antes, «Estamos preparando tu práctica»).
  const practicar = page.getByRole("link", { name: /^(Practicar|Seguir)$/ });
  await expect(async () => {
    await page.reload();
    await expect(practicar).toBeVisible({ timeout: 2000 });
  }).toPass({ timeout: 30_000 });
  await practicar.click();
  // El inicio: de qué clase sale, la constelación por encender y el aviso de que María lo verá.
  // (Si una corrida anterior ya lo empezó, se entra directo al ejercicio.)
  const empezar = page.getByRole("button", { name: "Empezar" });
  await expect(empezar.or(page.getByRole("button", { name: "Salir y seguir luego" })).first()).toBeVisible();
  if (await empezar.isVisible()) {
    await expect(page.getByRole("img", { name: "Tu constelación, por encender" }).first()).toBeVisible();
    await expect(page.getByText(/María verá cómo te fue/).first()).toBeVisible();
    await empezar.click();
  }

  // Un ejercicio por pantalla, hasta el cierre. Se responde lo que haya en pantalla; si sale «Casi…»,
  // se intenta distinto y el ejercicio se cierra de un modo u otro.
  const cierre = page.getByRole("heading", { name: /^Constelación (completa|perfecta)$/ });
  for (let i = 0; i < 8; i++) {
    const seguir = page.getByRole("button", { name: "Seguir", exact: true });
    const saltar = page.getByRole("button", { name: "Saltar este" });
    await expect(page.getByRole("button", { name: "Comprobar" }).or(seguir).or(saltar).or(cierre).first()).toBeVisible({
      timeout: 15_000,
    });
    if (await cierre.isVisible()) break;
    // El navegador de pruebas no suele traer voz en inglés: el de escucha se salta, sin contar como error.
    if (await saltar.isVisible()) {
      await saltar.click();
    } else if (!(await seguir.isVisible())) {
      await responder(page, 0);
      const otraVez = page.getByRole("button", { name: "Intentar otra vez" });
      await expect(seguir.or(otraVez)).toBeVisible({ timeout: 10_000 });
      if (await otraVez.isVisible()) {
        await expect(page.getByText("Casi…")).toBeVisible();
        await otraVez.click();
        await responder(page, 1);
      }
    }
    await seguir.click();
    // La transición (la estrella que se enciende) tapa la pantalla un momento y se va sola.
    await expect(seguir).toHaveCount(0, { timeout: 5000 });
  }
  await expect(cierre).toBeVisible({ timeout: 15_000 });
  // Los logros que encendió al terminar llegan uno a uno, cada uno con su «Seguir».
  const logroNuevo = page.getByRole("dialog").getByRole("button", { name: "Seguir" });
  for (let k = 0; k < 4 && (await logroNuevo.isVisible({ timeout: 2000 }).catch(() => false)); k++) {
    await logroNuevo.click();
  }
  await expect(page.getByRole("img", { name: /^Constelación (completa|perfecta):/ })).toBeVisible();
  await expect(page.getByText("Completaste el set")).toBeVisible();
  await expect(page.getByText(/ya puede ver cómo te fue/)).toBeVisible();
  await page.goto("/mis-clases");
  await logout(page);

  await login(page, USERS.maria);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  await page.goto("/mis-clases?scope=past");
  await page.getByRole("link", { name: "Ana Ramírez", exact: true }).first().click();
  await expect(page.getByText(/Practicó 1 de 1 vez este mes/)).toBeVisible();
  // La fila de esa práctica abre el acta, donde está cómo le fue ejercicio por ejercicio.
  await page.getByRole("link", { name: /Completada/ }).first().click();
  await expect(page.getByRole("heading", { name: "Cómo le fue a Ana" })).toBeVisible();
  await expect(page.getByText(/^Lo que respondió$/).first()).toBeVisible();
});

/**
 * Variante de fallo (brief, C2): sin la IA —apagada desde Ajustes, que es lo mismo que ve el
 * profesor cuando el proveedor cae o el presupuesto se agota— el acta se escribe a mano en los
 * mismos campos y se publica igual. Sin un solo mensaje de error técnico.
 */
test("sin IA, María escribe el acta a mano y la publica igual", async ({ page }) => {
  await login(page, USERS.admin);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  const cabeceras = async () => {
    const xsrf = (await page.context().cookies()).find((c) => c.name === "XSRF-TOKEN")?.value ?? "";
    return { "X-XSRF-TOKEN": xsrf };
  };
  const apagar = await page.request.put("/api/v1/admin/settings/ai_lesson_notes_enabled", {
    headers: await cabeceras(),
    data: { value: "false" },
  });
  expect(apagar.ok()).toBeTruthy();

  try {
    await logout(page);
    await login(page, USERS.maria);
    await page.waitForURL((u) => !u.pathname.startsWith("/login"));
    await page.goto("/mis-clases?scope=past");
    const porEscribir = page.getByRole("region", { name: "Actas por escribir" });
    await porEscribir.getByRole("link", { name: /Ana Ramírez/ }).first().click();

    await page.locator("#notas").fill("Repasamos el presente perfecto y le cuesta la pronunciación de la th.");
    await page.getByRole("button", { name: "Generar acta" }).click();
    await expect(page.getByText("Escríbela con tus palabras", { exact: false })).toBeVisible();
    await page.getByLabel("Lo que trabajamos").fill("Presente perfecto.");
    await page.getByRole("button", { name: "Publicar", exact: true }).click();
    await expect(page.getByText(/^Publicada · /)).toBeVisible();
  } finally {
    await page.goto("/mis-clases");
    await logout(page);
    await login(page, USERS.admin);
    await page.waitForURL((u) => !u.pathname.startsWith("/login"));
    await page.request.put("/api/v1/admin/settings/ai_lesson_notes_enabled", {
      headers: await cabeceras(),
      data: { value: "true" },
    });
  }
});

/**
 * El ensayo del Bloque 10 (23/09/2026): el admin crea desde Sistema una clase de prueba ya dictada,
 * María escribe el acta de esa clase en ese mismo momento y, publicada, ve debajo los ejercicios que
 * salieron de ella con su respuesta esperada; el admin ve en qué va.
 */
test("el admin ensaya el acta: María la escribe y ve los ejercicios que salieron", async ({ page }) => {
  await login(page, USERS.admin);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  await page.goto("/admin/sistema");
  const ensayo = page.locator("#ensayo-del-acta");
  await ensayo.getByPlaceholder("estudiante@correo.com").fill(USERS.ana.email);
  await ensayo.getByPlaceholder("profesor@correo.com").fill(USERS.maria.email);
  await ensayo.getByRole("button", { name: "Crear clase ya dictada" }).click();
  await expect(ensayo.getByText("Ensayo creado y cerrado.")).toBeVisible();
  const acta = await ensayo.getByRole("link", { name: "el acta de la clase" }).getAttribute("href");
  expect(acta).toMatch(/^\/mis-clases\/[\w-]+\/acta$/);
  await logout(page);

  await login(page, USERS.maria);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  // Entra a «Próximas», y aun así se entera de que tiene actas por escribir.
  await page.goto("/mis-clases");
  await expect(page.getByRole("button", { name: /actas? por escribir/ })).toBeVisible();
  await page.goto(acta!);
  await page.locator("#notas").fill("Ensayo: repasamos past simple; dijo 'I go yesterday' y aprendió 'shipment' (envío).");
  await page.getByRole("button", { name: "Generar acta" }).click();
  await expect(page.getByRole("button", { name: "Publicar", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Publicar", exact: true }).click();
  await expect(page.getByText(/^Publicada · /)).toBeVisible();

  // La práctica se genera en segundo plano; la sección se actualiza sola mientras tanto.
  const practica = page.getByRole("heading", { name: /^Cómo le fue a / });
  await expect(practica).toBeVisible();
  await expect(async () => {
    await page.reload();
    await expect(page.getByText(/^Lista · .+ aún no empieza$/)).toBeVisible({ timeout: 2000 });
  }).toPass({ timeout: 30_000 });
  // Cada ejercicio, con lo que se esperaba: todavía sin hacer.
  await expect(page.getByText("Sin hacer", { exact: true }).first()).toBeVisible();
  await logout(page);

  await login(page, USERS.admin);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  await page.goto("/admin/sistema");
  await expect(page.locator("#ensayo-del-acta").getByText(/Práctica lista/).first()).toBeVisible();
});
