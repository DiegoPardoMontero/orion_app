import { expect, test, type Page } from "@playwright/test";
import { api, aparece, apartarCelebraciones, cerrarCelebraciones, entrar, estudianteNueva, horariosAmplios, SEMILLA, ultimoCorreo } from "./apoyo";

/**
 * El wireflow del estudiante (24/09/2026). Lo que cambia datos lo hace una estudiante recién
 * registrada; lo que solo mira usa a Ana, la de la semilla, que tiene historia (clases, saldo,
 * actas y logros).
 */

test.beforeAll(async ({ browser }) => {
  await horariosAmplios(browser);
});

test.beforeEach(async ({ page }) => {
  await apartarCelebraciones(page);
});

async function perfilDeMaria(page: Page) {
  await page.goto("/profesores");
  await cerrarCelebraciones(page);
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await page.waitForURL(/\/profesores\/[0-9a-f-]+$/);
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
}

const precios = async (page: Page) =>
  (await page.locator("main").innerText())
    .match(/\$\d{1,3}(?:\.\d{3})+/g)
    ?.map((p) => Number(p.replace(/\D/g, ""))) ?? [];

test("[e-buscar.1] ordenar por precio (y el cajón de filtros cabe en la pantalla)", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/profesores");
  await page.getByRole("button", { name: /Filtros/ }).first().click();
  // La hoja se alinea abajo: su título tiene que quedar dentro de la pantalla, no por encima.
  await page.waitForTimeout(400);
  const titulo = await page.getByRole("dialog", { name: "Filtros" }).getByRole("heading", { name: "Filtros" }).boundingBox();
  expect(titulo!.y).toBeGreaterThanOrEqual(0);
  await page.getByRole("dialog", { name: "Filtros" }).getByRole("button", { name: "Menor precio" }).click();
  await page.getByRole("button", { name: "Ver resultados" }).click();
  await expect(page.getByText("María Gómez").first()).toBeVisible();
  const menor = await precios(page);
  expect(menor.length).toBeGreaterThan(1);
  expect([...menor].sort((a, b) => a - b)).toEqual(menor);
  await page.getByRole("button", { name: /Filtros/ }).first().click();
  await page.getByRole("dialog", { name: "Filtros" }).getByRole("button", { name: "Mayor precio" }).click();
  await page.getByRole("button", { name: "Ver resultados" }).click();
  await page.waitForTimeout(800);
  const mayor = await precios(page);
  expect([...mayor].sort((a, b) => b - a)).toEqual(mayor);
});

test("[e-buscar.2 e-filtros.1 e-filtros.2 e-filtros.3] días y horas exactas, varias a la vez", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/profesores");
  await page.getByRole("button", { name: /Filtros/ }).first().click();
  const hoja = page.getByRole("dialog").last();
  const hora = (h: string) => hoja.getByRole("button", { name: h, exact: true });
  await hoja.getByRole("button", { name: /Mañana · todas/ }).click();
  for (const h of ["5 AM", "8 AM", "11 AM"]) await expect(hora(h)).toHaveAttribute("aria-pressed", "true");
  await hoja.getByRole("button", { name: /Mañana · quitar todas/ }).click();
  await expect(hora("8 AM")).toHaveAttribute("aria-pressed", "false");
  // María da clases el miércoles de 8 a 11: el miércoles a las 9 la trae, el domingo no.
  await hoja.getByRole("button", { name: "X", exact: true }).click();
  await hora("9 AM").click();
  await hora("7 PM").click();
  await hoja.getByRole("button", { name: "Ver resultados" }).click();
  await expect(page.getByText("María Gómez").first()).toBeVisible();
  await page.getByRole("button", { name: /Filtros/ }).first().click();
  await hoja.getByRole("button", { name: "X", exact: true }).click();
  await hoja.getByRole("button", { name: "D", exact: true }).click();
  await hoja.getByRole("button", { name: "Ver resultados" }).click();
  await expect(page.getByText(/Sin resultados/).first()).toBeVisible();
  await page.getByRole("button", { name: /Filtros/ }).first().click();
  await hoja.getByRole("button", { name: "Limpiar filtros" }).click();
  await expect(hora("9 AM")).toHaveAttribute("aria-pressed", "false");
});

test("[e-buscar.4 e-ficha.3] la franja de la ficha se cierra por un día; hacer visible la ficha", async ({ page }) => {
  await estudianteNueva(page, "Franja Prueba");
  await page.goto("/profesores");
  const franja = page.getByRole("complementary", { name: "Completa tu ficha" });
  await expect(franja).toBeVisible();
  await franja.getByRole("button", { name: "Recuérdamelo mañana" }).click();
  await expect(franja).toHaveCount(0);
  await page.reload();
  await expect(page.getByText("Profesores").first()).toBeVisible();
  await expect(page.getByRole("complementary", { name: "Completa tu ficha" })).toHaveCount(0);

  await page.goto("/cuenta?seccion=ficha");
  await expect(page.getByText("Tu ficha es privada")).toBeVisible();
  await page.getByRole("button", { name: "Hacerla visible" }).click();
  await expect(page.getByText("Tu ficha es visible")).toBeVisible();
  await page.getByRole("button", { name: "Volverla privada" }).click();
  await expect(page.getByText("Tu ficha es privada")).toBeVisible();
});

test("[e-perfil-profe.1 e-perfil-profe.2 e-perfil-profe.3] el perfil del profe con cuenta: días, cupos y mensaje", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await perfilDeMaria(page);
  await expect(page.getByRole("heading", { name: "Reseñas" })).toBeVisible();
  await expect(page.locator("main .grid-cols-3 button").first()).toBeVisible();
  await page.getByRole("button", { name: /Enviar mensaje/ }).click();
  await expect(page).toHaveURL(/\/mensajes\/[0-9a-f-]+$/);
});

test("[e-reservar.1 e-reservar.2] el desglose descuenta el saldo a favor", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await perfilDeMaria(page);
  await page.locator("main .grid-cols-3 button").first().click();
  await expect(page.getByText("Tu saldo a favor")).toBeVisible();
  await expect(page.getByText(/Cubierto con tu saldo|Total a pagar/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Confirmar reserva" })).toBeEnabled();
});

test("[e-prueba.1 e-prueba.2 e-prueba.3 e-prueba.4 e-rigel.3 e-mis-clases.3] la clase de prueba gratis, una por pareja", async ({ page }) => {
  await estudianteNueva(page, "Prueba Gratis");
  await perfilDeMaria(page);
  const perfil = new URL(page.url()).pathname;
  await expect(page.getByText("¿Cómo quieres tu primera clase?")).toBeVisible();
  await page.locator("main .grid-cols-3 button").first().click();
  await page.getByRole("button", { name: /^Clase de prueba/ }).click();
  await expect(page.getByText(/Clase de prueba de \d+ minutos/)).toBeVisible();
  await page.getByRole("button", { name: "Confirmar reserva" }).click();
  await expect(page).toHaveURL(/\/mis-clases/);
  const tarjeta = page.locator("main li").filter({ hasText: "Clase de prueba" }).first();
  await expect(tarjeta).toBeVisible();

  // Rigel lo acompaña, una vez.
  await page.goto("/mensajes/rigel");
  await expect(page.getByRole("heading", { name: /Tu primera clase está en camino/ })).toBeVisible();

  await page.goto(perfil);
  await expect(page.getByText("Cupos disponibles")).toBeVisible();
  await expect(page.getByRole("button", { name: /^Clase de prueba/ })).toHaveCount(0);

  // Cancelada, no se gasta.
  await page.goto("/mis-clases");
  await page.locator("main li").filter({ hasText: "Clase de prueba" }).first().getByRole("button", { name: "Cancelar" }).click();
  await page.getByRole("button", { name: "Sí, cancelar" }).click();
  await page.goto(perfil);
  await expect(page.getByText("¿Cómo quieres tu primera clase?")).toBeVisible();
});

test("[e-reservar.4] con el correo sin verificar no se reserva, y se dice por qué", async ({ page }) => {
  const email = `wf.sinverificar.${Date.now()}@orion.local`;
  await page.goto("/registro");
  await page.waitForLoadState("networkidle");
  await page.locator("#nombre").fill("Sin Verificar");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("orion123*");
  for (const id of ["#mayor-de-edad", "#acepta-terminos", "#acepta-datos"]) await page.locator(id).check();
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await page.waitForURL(/\/profesores/);
  await page.getByRole("dialog").getByRole("button", { name: /^(Ahora no|Saltar)$/ }).click();
  await perfilDeMaria(page);
  await page.locator("main .grid-cols-3 button").first().click();
  await page.getByRole("button", { name: /^Clase de prueba/ }).click();
  await page.getByRole("button", { name: "Confirmar reserva" }).click();
  await expect(page.getByText(/Confirma tu correo antes de reservar/)).toBeVisible();
});

test("[e-reservar.5] dos personas al mismo cupo: la segunda sabe que se lo ganaron", async ({ page, browser }) => {
  const otraCtx = await browser.newContext();
  const otra = await otraCtx.newPage();
  await estudianteNueva(page, "Primera Rápida");
  await estudianteNueva(otra, "Segunda Lenta");
  for (const p of [page, otra]) {
    await perfilDeMaria(p);
    await p.locator("main .grid-cols-3 button").first().click();
    await p.getByRole("button", { name: /^Clase de prueba/ }).click();
  }
  await page.getByRole("button", { name: "Confirmar reserva" }).click();
  await expect(page).toHaveURL(/\/mis-clases/);
  await otra.getByRole("button", { name: "Confirmar reserva" }).click();
  // El chequeo amable dice que ya no está; si las dos llegan a la vez, la base decide y dice lo mismo.
  await expect(otra.getByText(/El cupo no está disponible|Alguien acaba de tomar este cupo/)).toBeVisible();
  await otraCtx.close();
});

test("[e-pago.1 e-mis-clases.1 e-mis-clases.2 e-mis-clases.4 e-calendario.1 e-calendario.2] Mis clases, la siguiente y el calendario", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  const r = await api(page, "GET", "/api/v1/me/bookings?scope=upcoming");
  const proxima = (r.json as { id: string; status: string }[]).find((c) => c.status === "CONFIRMED")!;
  await page.goto(`/pago/${proxima.id}`);
  await expect(page.getByText(/quedó confirmada/)).toBeVisible();
  await page.goto("/mis-clases");
  await expect(page.getByText("La siguiente").first()).toBeVisible();
  await expect(page.getByRole("link", { name: "Unirse a la clase" }).first()).toHaveAttribute("href", /\/aula$/);
  await page.getByRole("button", { name: "Calendario" }).click();
  const mes = await page.getByText(/(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre) \d{4}/i).first().innerText();
  await page.getByRole("button", { name: /siguiente|Mes siguiente/i }).first().click();
  await expect(page.getByText(/(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre) \d{4}/i).first()).not.toHaveText(mes);
});

test("[p-agenda.2 e-prueba.4] cancelar la prueba gratis no promete dinero, y el profe también puede", async ({ page }) => {
  // Una estudiante nueva no tiene saldo: su única clase sin pasarela es la prueba gratis de María.
  const nombre = `Valeria Cancela ${Date.now() % 10000}`;
  const vale = await estudianteNueva(page, nombre);
  await perfilDeMaria(page);
  await page.locator("main .flex-wrap button").last().click();
  await page.locator("main .grid-cols-3 button").first().click();
  await page.getByRole("button", { name: /Clase de prueba/ }).click();
  await page.getByRole("button", { name: /Confirmar|Reservar/ }).last().click();
  await expect(page).toHaveURL(/\/mis-clases/);
  await cerrarCelebraciones(page);
  await page.locator("main li").filter({ hasText: "María" }).first().getByRole("button", { name: "Cancelar" }).click();
  await expect(page.getByText(/Es tu clase de prueba gratis: no hay dinero de por medio/)).toBeVisible();
  await expect(page.getByText(/saldo a favor/)).toHaveCount(0);
  await page.getByRole("button", { name: "Mantener clase" }).click();
  await entrar(page, SEMILLA.maria);
  await page.goto("/mis-clases");
  await cerrarCelebraciones(page);
  const tarjeta = page.locator("main li").filter({ hasText: nombre }).first();
  await tarjeta.getByRole("button", { name: "Cancelar" }).click();
  await expect(page.getByText(/Es su clase de prueba gratis: no hay dinero de por medio/)).toBeVisible();
  await expect(page.getByText(/saldo a favor/)).toHaveCount(0);
  await page.getByRole("button", { name: "Sí, cancelar" }).click();
  await expect(page.getByText("¿Cancelar esta clase?")).toHaveCount(0);
  await expect(page.locator("main li").filter({ hasText: nombre })).toHaveCount(0);
  expect(await ultimoCorreo(page, vale.email, /cancel/i)).not.toBeNull();
  // Cancelada, la prueba deja de contar: Valeria puede pedirla otra vez.
  await entrar(page, vale);
  await perfilDeMaria(page);
  await page.locator("main .flex-wrap button").last().click();
  await page.locator("main .grid-cols-3 button").first().click();
  await expect(page.getByRole("button", { name: /Clase de prueba/ })).toBeVisible();
});

test("[e-cancelar.1 e-cancelar.3 e-cancelar.4] cancelar con tiempo: elige adónde va el dinero y el profe se entera", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await perfilDeMaria(page);
  const dias = page.locator("main .flex-wrap button");
  await dias.last().click();
  const cupos = page.locator("main .grid-cols-3 button");
  const hora = (await cupos.last().innerText()).trim();
  await cupos.last().click();
  await page.getByRole("button", { name: "Confirmar reserva" }).click();
  await expect(page).toHaveURL(/\/mis-clases/);
  await cerrarCelebraciones(page);
  const [ini, mer] = hora.split(/\s+/);
  const tarjeta = page.locator("main li").filter({ hasText: new RegExp(`${ini.replace(":", "\\:")}\\s*[–-]\\s*\\d{1,2}\\:\\d{2}\\s*${mer}`) }).first();
  await tarjeta.getByRole("button", { name: "Cancelar" }).click();
  await expect(page.getByText("A mi saldo en Orión")).toBeVisible();
  await expect(page.getByText("Al medio de pago que usé")).toBeVisible();
  await page.getByText("A mi saldo en Orión").click();
  await page.getByRole("button", { name: "Sí, cancelar" }).click();
  await expect(page.getByText("¿Cancelar esta clase?")).toHaveCount(0);
  expect(await ultimoCorreo(page, SEMILLA.maria.email, /cancel/i)).not.toBeNull();
  await perfilDeMaria(page);
  await dias.last().click();
  await expect(page.locator("main .grid-cols-3 button", { hasText: hora })).toBeVisible();
});

test("[e-pasadas.1 e-resumen.1] los resúmenes de las clases pasadas", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/mis-clases?scope=past");
  const resumenes = page.getByRole("region", { name: "Resúmenes de tus clases" });
  await expect(resumenes).toBeVisible();
  await resumenes.getByRole("link").first().click();
  await expect(page).toHaveURL(/\/acta$/);
  await expect(page.getByRole("heading", { name: "Lo que trabajaron" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Palabras nuevas" })).toBeVisible();
});

test("[e-pasadas.2] calificar una clase dictada", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/mis-clases?scope=past");
  const calificar = page.getByRole("button", { name: "Calificar" }).first();
  test.skip(!(await aparece(calificar)), "Ana ya calificó todas sus clases en esta base");
  await calificar.click();
  const dialogo = page.getByRole("dialog", { name: /¿Cómo estuvo tu clase con/ });
  await expect(dialogo.getByRole("button", { name: "Enviar reseña" })).toBeDisabled();
  await dialogo.getByRole("radio", { name: "5 estrellas" }).click();
  await dialogo.getByLabel("Comentario (opcional)").fill("Muy buena clase, practicamos lo del trabajo.");
  await dialogo.getByRole("button", { name: "Enviar reseña" }).click();
  await expect(page.getByText("¡Gracias! Ya calificaste esta clase.").first()).toBeVisible();
});

test("[e-mensajes.1 e-mensajes.2 e-rigel.1 e-rigel.2 e-rigel.4] Rigel fijo arriba, con botones y sin caja para responder", async ({ page }) => {
  await estudianteNueva(page, "Luna Prueba");
  await page.goto("/mensajes");
  const fila = page.getByRole("link", { name: /Rigel · Orión/ }).locator("visible=true").first();
  await expect(fila).toBeVisible();
  await fila.click();
  await expect(page).toHaveURL(/\/mensajes\/rigel$/);
  await expect(page.getByRole("heading", { name: /¡Hola, Luna! Soy Rigel/ })).toBeVisible();
  await expect(page.getByRole("link", { name: /Buscar profesor/ }).first()).toHaveAttribute("href", "/profesores");
  await expect(page.getByRole("link", { name: /Completar mi ficha/ }).first()).toHaveAttribute("href", "/cuenta?seccion=ficha");
  await expect(page.locator("textarea")).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Escríbenos desde Ayuda" })).toHaveAttribute("href", "/ayuda");
  // Leído, el número de «Mensajes» se apaga.
  await page.goto("/mensajes");
  await expect(page.getByRole("link", { name: /Rigel · Orión/ }).locator("visible=true").first().locator(".bg-primary")).toHaveCount(0);
});

test("[e-hilo.1 e-hilo.2 e-hilo.3] el chat con el profe: saludo de Orión y teléfonos ocultos", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/mensajes");
  await page.getByRole("link", { name: /María Gómez/ }).first().click();
  await expect(page.getByText(/Enviado por Orión/).first()).toBeVisible();
  const caja = page.getByRole("textbox", { name: "Escribe un mensaje" });
  await caja.fill(`Mi WhatsApp es 3001234567 (${Date.now() % 1000})`);
  await page.getByRole("button", { name: "Enviar mensaje" }).click();
  await expect(page.getByText(/oculta datos de contacto/).first()).toBeVisible();
  await expect(page.getByText("3001234567")).toHaveCount(0);
});

test("[e-perfil.1 e-perfil.2 e-ficha.5] Mi perfil: racha y puntos junto al nombre, sin explicarlos ni ofrecer postular", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/cuenta");
  await expect(page.getByText(/semanas? en racha|racha/i).first()).toBeVisible();
  await expect(page.locator("main").getByTitle("Tus puntos en Orión").first()).toBeVisible();
  // Pardo, 25/09/2026: los puntos no se explican.
  await expect(page.getByText("Tus puntos", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Cómo se hacen")).toHaveCount(0);
  // Y desde la cuenta de estudiante no se postula a profesor.
  await page.goto("/cuenta?seccion=ficha");
  await expect(page.getByText("Solo para ti")).toBeVisible();
  await expect(page.getByText(/Enseña en Orión|Postúlate como profesor/)).toHaveCount(0);
  await page.goto("/aplicacion");
  await page.waitForURL((u) => !u.pathname.startsWith("/aplicacion"));
});

test("[e-campana.1 e-campana.2] las notificaciones llevan a su pantalla y se marcan leídas", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.getByRole("button", { name: /^Notificaciones/ }).first().click();
  await expect(page.getByText("Notificaciones").first()).toBeVisible();
  const marcar = page.getByRole("button", { name: "Marcar todas" });
  if (await marcar.isVisible()) {
    await marcar.click();
    await expect(marcar).toHaveCount(0);
  }
});

test("[e-mis-clases.5] una clase en curso sigue en Próximas con «Unirse a la clase» y sin «Cancelar»", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  // El backend decide qué está en curso con su reloj (MyBookingsIT lo prueba con el reloj congelado);
  // aquí se marca en curso la primera clase confirmada para ver cómo la pinta la pantalla.
  await page.route("**/api/v1/me/bookings?scope=upcoming", async (route) => {
    const respuesta = await route.fetch();
    const clases = (await respuesta.json()) as { status: string; inProgress?: boolean }[];
    const i = clases.findIndex((c) => c.status === "CONFIRMED");
    if (i >= 0) clases[i].inProgress = true;
    await route.fulfill({ response: respuesta, json: clases });
  });
  await page.goto("/mis-clases");
  const tarjeta = page.locator("li", { has: page.getByText("En curso", { exact: true }) }).first();
  await expect(tarjeta).toBeVisible();
  await expect(tarjeta.getByRole("link", { name: /Unirse a la clase/ })).toBeVisible();
  await expect(tarjeta.getByRole("button", { name: "Cancelar", exact: true })).toHaveCount(0);
});

test("[v-ensena.3] con la sesión de un estudiante, «Enseña con Orión» no ofrece postular", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/ensena-con-orion");
  await expect(page.getByText(/desde ella no se postula/).first()).toBeVisible();
  await expect(page.getByRole("link", { name: /Empieza tu postulación/ })).toHaveCount(0);
});

test("[e-ficha.1 e-ficha.4] los últimos logros dicen qué son; cambiar la contraseña", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/cuenta?seccion=ficha");
  await expect(page.getByText("Tus últimos logros")).toBeVisible();
  await expect(page.getByText(/su color es el de su familia/)).toBeVisible();

  const cuenta = await estudianteNueva(page, "Clave Nueva");
  await page.goto("/cuenta?seccion=ficha");
  await page.getByRole("button", { name: /Cambiar contraseña/ }).click();
  await page.locator("#actual").fill(cuenta.pass);
  await page.locator("#nueva").fill("clave-nueva-456");
  await page.getByRole("button", { name: "Cambiar", exact: true }).click();
  await expect(page.getByText(/quedó|cambiada|lista/i).first()).toBeVisible();
  await page.context().clearCookies();
  await entrar(page, { email: cuenta.email, pass: "clave-nueva-456" });
  await expect(page).toHaveURL(/\/profesores/);
});

test("[e-cambios.1 e-cambios.2 e-cambios.3 e-cambios.4 e-cambios.5] editar la ficha sin modo de edición", async ({ page }) => {
  await estudianteNueva(page, "Cambios Prueba");
  await page.goto("/cuenta?seccion=ficha");
  const barra = page.getByRole("region", { name: "Cambios sin guardar" });
  await expect(barra).toHaveCount(0);
  await page.getByRole("button", { name: "Intermedio" }).click();
  await expect(barra).toBeVisible();
  await barra.getByRole("button", { name: "Descartar" }).click();
  await expect(barra).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Intermedio" })).toHaveAttribute("aria-pressed", "false");

  // Ficha y datos se guardan juntos con una sola barra.
  await page.getByRole("button", { name: "Avanzado" }).click();
  await page.locator("#telefono").fill("3007654321");
  await barra.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page.getByText("Cambios guardados")).toBeVisible();
  await page.reload();
  await expect(page.getByRole("button", { name: "Avanzado" })).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#telefono")).toHaveValue("3007654321");

  // El WhatsApp se cambia pero no se borra: es obligatorio desde el 25/09.
  await page.locator("#telefono").fill("");
  await barra.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page.getByText("Tu WhatsApp es obligatorio: escríbelo completo.")).toBeVisible();
  await barra.getByRole("button", { name: "Descartar" }).click();
  await expect(page.locator("#telefono")).toHaveValue("3007654321");

  // Salir con cambios pregunta.
  await page.getByRole("button", { name: "Principiante" }).click();
  await page.locator('a[href="/mis-clases"]:visible').first().click();
  await expect(page.getByRole("dialog", { name: "¿Salir sin guardar?" })).toBeVisible();
  await page.getByRole("button", { name: "Seguir editando" }).click();
  await expect(page).toHaveURL(/\/cuenta/);
  let pregunto = false;
  page.on("dialog", async (d) => {
    if (d.type() === "beforeunload") pregunto = true;
    await d.dismiss();
  });
  await page.reload({ timeout: 3000 }).catch(() => {});
  expect(pregunto).toBe(true);
  await page.locator('a[href="/mis-clases"]:visible').first().click();
  await page.getByRole("button", { name: "Salir sin guardar" }).click();
  await expect(page).toHaveURL(/\/mis-clases/);
});

test("[e-cielo.1 e-cielo.2 e-saldo.1 e-saldo.2] Mi cielo por familias y el saldo con su historia", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/logros");
  await page.getByRole("tab", { name: "Constancia" }).click();
  await expect(page.getByRole("tab", { name: "Constancia" })).toHaveAttribute("aria-selected", "true");
  await page.goto("/saldo");
  await expect(page.getByText(/Saldo a favor/).first()).toBeVisible();
  await expect(page.getByText(/\$\d{1,3}(\.\d{3})+/).first()).toBeVisible();
});

test("[e-ayuda.1 e-ayuda.2 e-ayuda.3 e-ayuda-hilo.1 e-ayuda-hilo.2 e-ayuda-hilo.3 ad-soporte.2 ad-soporte-hilo.1 ad-soporte-hilo.2] una solicitud de ayuda, de ida y vuelta", async ({ page, browser }) => {
  const cuenta = await estudianteNueva(page, "Ayuda Prueba");
  await page.goto("/ayuda");
  await expect(page.getByText(/WhatsApp/).first()).toBeVisible();
  await expect(page.getByText("Preguntas frecuentes").first()).toBeVisible();
  await page.getByRole("button", { name: "Nueva solicitud" }).click();
  await page.locator("#categoria").selectOption({ index: 1 });
  const asunto = `No me deja reservar ${Date.now() % 100000}`;
  await page.locator("#asunto").fill(asunto);
  await page.locator("#cuerpo").fill("Intento reservar con María y no me aparece el botón.");
  await page.getByRole("button", { name: /Enviar/ }).last().click();
  await expect(page.getByText(/ORN-/).first()).toBeVisible();
  await expect(page.getByText(/Esperando respuesta/).first()).toBeVisible();
  const codigo = (await page.getByText(/ORN-[A-Z0-9]+/).first().innerText()).match(/ORN-[A-Z0-9]+/)![0];

  const adminCtx = await browser.newContext();
  const admin = await adminCtx.newPage();
  await entrar(admin, SEMILLA.admin);
  await admin.goto("/admin/soporte");
  await admin.getByText(asunto).click();
  await admin.locator("textarea").fill("Hola, ya lo revisamos: confirma tu correo y vuelve a intentarlo.");
  await admin.getByRole("button", { name: "Responder" }).click();
  await expect(admin.getByText(/ya lo revisamos/)).toBeVisible();
  expect(await ultimoCorreo(page, cuenta.email, /respond|respuesta|te contestamos|te respondimos/i)).not.toBeNull();

  await page.goto(`/ayuda/${codigo}`);
  await expect(page.getByText(/ya lo revisamos/)).toBeVisible();
  await page.locator("textarea").fill("Gracias, ya funcionó.");
  await page.getByRole("button", { name: "Responder" }).click();
  await expect(page.getByText("Gracias, ya funcionó.")).toBeVisible();

  await admin.reload();
  await admin.getByRole("button", { name: "Cerrar solicitud" }).click();
  await expect(admin.getByText(/Cerrada/).first()).toBeVisible();
  await adminCtx.close();
});

test("[e-recorrido.2 e-recorrido-paso.3 v-verificar.1] el recorrido: teclado, aplazarlo y el correo verificado", async ({ page }) => {
  const email = `wf.recorrido.${Date.now()}@orion.local`;
  await page.goto("/registro");
  await page.waitForLoadState("networkidle");
  await page.locator("#nombre").fill("Teclado Prueba");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("orion123*");
  for (const id of ["#mayor-de-edad", "#acepta-terminos", "#acepta-datos"]) await page.locator(id).check();
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await page.waitForURL(/\/profesores/);
  const recorrido = page.getByRole("dialog");
  await recorrido.getByRole("button", { name: "Empezar" }).click();
  await expect(recorrido.getByText("1 de 6")).toBeVisible();
  await page.keyboard.press("ArrowRight");
  await expect(recorrido.getByText("2 de 6")).toBeVisible();
  await page.keyboard.press("ArrowLeft");
  await expect(recorrido.getByText("1 de 6")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByText("1 de 6")).toHaveCount(0);
  // Aplazado, Rigel lo ofrece una sola vez.
  await page.goto("/mis-clases");
  await expect(page.getByText(/recorrido/i).first()).toBeVisible();
  // El enlace del correo verifica la cuenta.
  const correo = await ultimoCorreo(page, email, /verificar\?token=/);
  await page.goto(correo!.match(/https?:\/\/[^"\\\s]*\/verificar\?token=[A-Za-z0-9_-]+/)![0].replace(/^https?:\/\/[^/]+/, ""));
  await expect(page.getByText("tu correo quedó confirmado", { exact: false })).toBeVisible();
});

test("[e-buscar.3] la tarjeta de práctica se cierra hasta la práctica siguiente", async ({ page }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/profesores");
  const cerrar = page.getByRole("button", { name: "Ocultar hasta la próxima práctica" });
  test.skip(!(await aparece(cerrar)), "Ana no tiene una práctica pendiente en esta base");
  await cerrar.click();
  await expect(cerrar).toHaveCount(0);
  await page.reload();
  await expect(page.getByText("Profesores").first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Ocultar hasta la próxima práctica" })).toHaveCount(0);
});

test("[e-pasadas.3 ad-reclamos.1] reportar un problema de una clase llega a los reclamos del admin", async ({ page, browser }) => {
  await entrar(page, SEMILLA.ana);
  await page.goto("/mis-clases?scope=past");
  const reportar = page.getByRole("button", { name: "Reportar un problema" }).first();
  test.skip(!(await aparece(reportar)), "Ninguna clase de Ana está dentro del plazo para reclamar");
  await reportar.click();
  const dialogo = page.getByRole("dialog", { name: "Reportar un problema" });
  await dialogo.getByText("Hubo un problema técnico").click();
  await dialogo.locator("#descripcion-reclamo").fill("Se cortó el audio a mitad de la clase y no volvió.");
  await dialogo.getByRole("button").last().click();
  await expect(dialogo).toHaveCount(0);
  const adminCtx = await browser.newContext();
  const admin = await adminCtx.newPage();
  await entrar(admin, SEMILLA.admin);
  await admin.goto("/admin/reclamos");
  await expect(admin.getByText(/Se cortó el audio|problema técnico/i).first()).toBeVisible();
  await adminCtx.close();
});
