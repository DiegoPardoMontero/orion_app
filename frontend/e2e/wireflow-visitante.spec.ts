import { expect, test } from "@playwright/test";
import { aceptarCondiciones, api, entrar, SEMILLA, sinDesbordeLateral, ultimoCorreo } from "./apoyo";

/**
 * El wireflow, sin cuenta (24/09/2026). Cada prueba lleva entre corchetes los casos del wireflow que
 * cubre («pantalla.n»): así el resultado de la suite se puede marcar en la página como «Claude ya lo
 * probó», y quien pruebe a mano se concentra en lo que necesita un teléfono de verdad.
 */

// Sin modo serie: cada prueba es independiente, y una que falla no debe esconder el resultado de las demás.

test("[v-portada.1] en el celular, Rigel va antes del titular", async ({ page }) => {
  await page.goto("/");
  const titular = page.getByRole("heading", { level: 1 }).first();
  await expect(titular).toBeVisible();
  const rigel = page.locator('svg[role="img"]').first();
  const [r, t] = await Promise.all([rigel.boundingBox(), titular.boundingBox()]);
  expect(r && t && r.y < t.y).toBeTruthy();
  await sinDesbordeLateral(page);
});

test("[v-portada.2 e-filtros.4] el buscador de la portada llega con sus filtros puestos", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("radiogroup", { name: "¿Para qué lo necesitas?" }).getByRole("radio", { name: "Viaje" }).click();
  await page.getByRole("radiogroup", { name: "¿Cuándo puedes?" }).getByRole("radio", { name: "Noche" }).click();
  await page.getByRole("link", { name: /Ver profesores disponibles/ }).click();
  await expect(page).toHaveURL(/\/profesores\?goal=TRAVEL&schedule=EVENING/);
  await page.getByRole("button", { name: /Filtros/ }).first().click();
  for (const hora of ["6 PM", "7 PM", "8 PM", "9 PM"]) {
    await expect(page.getByRole("button", { name: hora, exact: true }).last()).toHaveAttribute("aria-pressed", "true");
  }
  await expect(page.getByRole("button", { name: "10 PM", exact: true }).last()).toHaveAttribute("aria-pressed", "false");
});

test("[v-portada.3 v-diagnostico.1 v-diagnostico.2 v-diagnostico.3] el diagnóstico se abre sin cuenta", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: /diagnóstico gratis/i }).first().click();
  await expect(page).toHaveURL(/\/diagnostico$/);
  await expect(page.getByText(/dos minutos|2 minutos/i).first()).toBeVisible();
  await page.getByRole("link", { name: /Déjanos tu número/ }).click();
  await expect(page).toHaveURL(/\/diagnostico\/llamame/);
  await page.goBack();
  await page.getByRole("link", { name: /Empezar con Meissa/ }).click();
  await expect(page).toHaveURL(/\/diagnostico\/empezar/);
});

test("[v-portada.6 v-diagnostico.4 v-diag-empezar.7] el diagnóstico es voluntario: se crea la cuenta sin hacerlo", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Crea tu cuenta", exact: true }).first().click();
  await expect(page).toHaveURL(/\/registro$/);
  await page.goto("/diagnostico");
  await expect(page.getByText(/Puedes hablarle en español/).first()).toBeVisible();
  await expect(page.getByText(/te queda en tu perfil/)).toBeVisible();
  await page.getByRole("link", { name: "Crear mi cuenta sin diagnóstico" }).click();
  await expect(page).toHaveURL(/\/registro$/);
  await page.goto("/diagnostico/empezar");
  await page.getByRole("link", { name: "Crea tu cuenta sin diagnóstico" }).click();
  await expect(page).toHaveURL(/\/registro$/);
});

test("[v-diag-empezar.1 v-diag-empezar.2] sin las dos casillas no se empieza", async ({ page }) => {
  await page.goto("/diagnostico/empezar");
  await page.waitForLoadState("networkidle");
  const empezar = page.getByRole("button", { name: /Empezar la conversación/ });
  await page.getByRole("textbox").first().fill("Sofía");
  await expect(empezar).toBeDisabled();
  await page.getByRole("checkbox", { name: /mayor de 18/ }).check();
  await expect(empezar).toBeDisabled();
  await page.getByRole("checkbox", { name: /voz/ }).check();
  await expect(empezar).toBeEnabled();
});

test("[v-portada.4 v-catalogo.1 v-catalogo.3 v-perfil-profe.1 v-perfil-profe.2] el catálogo y el perfil se ven sin cuenta", async ({ page }) => {
  await page.goto("/profesores");
  await expect(page.getByText("María Gómez").first()).toBeVisible();
  // La tarjeta de práctica es solo de estudiantes.
  await expect(page.getByText("Para esta semana")).toHaveCount(0);
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  await expect(page).toHaveURL(/\/profesores\/[0-9a-f-]+$/);
  const perfil = new URL(page.url()).pathname;
  await expect(page.getByText(/\$45\.000/).first()).toBeVisible();
  await expect(page.getByText("Primera clase gratis").first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Reseñas" })).toBeVisible();
  // Los horarios, con cuenta: crearla o entrar vuelve a este mismo perfil.
  await expect(page.getByText(/Crea tu cuenta gratis para ver los horarios/)).toBeVisible();
  await expect(page.getByRole("link", { name: "Crear mi cuenta gratis" })).toHaveAttribute("href", `/registro?volver=${encodeURIComponent(perfil)}`);
  await expect(page.getByRole("link", { name: "Ya tengo cuenta" })).toHaveAttribute("href", `/login?volver=${encodeURIComponent(perfil)}`);
});

test("[v-catalogo.2] los filtros funcionan sin cuenta", async ({ page }) => {
  await page.goto("/profesores");
  await page.getByRole("button", { name: /Filtros/ }).first().click();
  await page.getByRole("button", { name: "D", exact: true }).last().click();
  await page.getByRole("button", { name: /Ver resultados/ }).click();
  // Nadie de la semilla da clases el domingo.
  await expect(page.getByText(/Sin resultados|Ningún profesor/).first()).toBeVisible();
});

test("[v-perfil-profe.3] «Enviar mensaje» sin cuenta pide entrar", async ({ page }) => {
  await page.goto("/profesores");
  await page.getByRole("link", { name: /Ver agenda/ }).first().click();
  const perfil = new URL(page.url()).pathname;
  await page.getByRole("button", { name: /Enviar mensaje/ }).click();
  await expect(page).toHaveURL(new RegExp(`/(registro|login)\\?volver=${encodeURIComponent(perfil)}`));
});

test("[v-enlace.2] un enlace corto inventado no es un error técnico", async ({ page }) => {
  const r = await page.goto("/p/nadie-con-este-nombre");
  expect(r?.status()).toBe(404);
  await expect(page.getByText(/no (existe|encontr)/i).first()).toBeVisible();
});

test("[v-ensena.1 v-ensena.2] «Enseña con Orión» lleva al registro con «Quiero enseñar»", async ({ page }) => {
  await page.goto("/ensena-con-orion");
  await sinDesbordeLateral(page);
  await page.getByRole("link", { name: /postúlate/i }).first().click();
  await expect(page).toHaveURL(/\/registro/);
  await expect(page.getByRole("button", { name: /Quiero enseñar/ })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByText(/sigues con tu postulación/).first()).toBeVisible();
});

test("[v-idioma.1 v-idioma.2 v-idioma.3] la página del inglés trae profesores y un idioma inventado no existe", async ({ page }) => {
  await page.goto("/idiomas/en");
  await expect(page.getByText("María Gómez").first()).toBeVisible();
  await page.getByRole("link", { name: /Ver profesores/ }).first().click();
  await expect(page).toHaveURL(/\/profesores\?language=EN/);
  const r = await page.goto("/idiomas/xx");
  expect(r?.status()).toBe(404);
});

test("[v-llamame.1 v-llamame.2 v-llamame.3 v-llamame-listo.1 ad-llamadas.1] pedir que nos escriban", async ({ page }) => {
  await page.goto("/diagnostico/llamame");
  await page.waitForLoadState("networkidle");
  const enviar = page.getByRole("button", { name: "Que me escriban" });
  const nombre = `Prueba ${Date.now() % 100000}`;
  await page.locator("input").first().fill(nombre);
  await page.locator('input[type="tel"]').first().fill("3001234567");
  await expect(enviar).toBeDisabled();
  await page.getByRole("checkbox").first().check();
  await expect(enviar).toBeEnabled();
  // Un número que no puede ser un WhatsApp no deja enviar.
  await page.locator('input[type="tel"]').first().fill("12");
  await expect(enviar).toBeDisabled();
  await page.locator('input[type="tel"]').first().fill("3001234567");
  await enviar.click();
  await expect(page.getByRole("heading", { name: new RegExp(`Listo, ${nombre.split(" ")[0]}`) })).toBeVisible();

  await entrar(page, SEMILLA.admin);
  await page.goto("/admin/llamadas");
  await expect(page.getByText(nombre)).toBeVisible();
});

test("[v-login.1] cada rol entra a su propio inicio", async ({ browser }) => {
  for (const [user, inicio] of [[SEMILLA.ana, /\/profesores/], [SEMILLA.maria, /\/mis-clases/], [SEMILLA.admin, /\/admin\/panel/]] as const) {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await entrar(page, user);
    await expect(page).toHaveURL(inicio);
    await ctx.close();
  }
});

test("[v-login-error.1] la contraseña equivocada se dice en español y no revela el correo", async ({ page }) => {
  for (const email of [SEMILLA.ana.email, "nadie@orion.local"]) {
    await page.goto("/login");
    await page.waitForLoadState("networkidle");
    await page.locator("#email").fill(email);
    await page.locator("#password").fill("equivocada123");
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page.getByText("Correo o contraseña incorrectos.")).toBeVisible();
  }
});

test("[v-login-error.2] tras muchos intentos seguidos con un correo, frena con un mensaje claro", async ({ page }) => {
  // En local el tope es 50 por (conexión + correo) —ver application-local.yml—; en producción, 5.
  // Un correo inventado para no gastar los intentos de nadie de la semilla.
  const email = `wf.frena.${Date.now()}@orion.local`;
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  let frenado = false;
  for (let i = 0; i < 60 && !frenado; i++) {
    const r = await page.request.post("/api/v1/auth/login", { data: { email, password: "equivocada123" } });
    frenado = r.status() === 429;
  }
  expect(frenado).toBe(true);
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("equivocada123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByText(/Demasiados intentos con este correo/)).toBeVisible();
});

test("[v-registro.1 v-registro.2 v-registro.3 v-registro.4] el registro valida y manda el correo", async ({ page }) => {
  await page.goto("/registro");
  await page.waitForLoadState("networkidle");
  await page.locator("#nombre").fill("Correo Repetido");
  await page.locator("#password").fill("abc");
  await expect(page.getByText("Muy corta todavía")).toBeVisible();
  const crear = page.getByRole("button", { name: "Crear cuenta" });
  await page.locator("#email").fill(SEMILLA.ana.email);
  await page.locator("#password").fill("orion123*");
  await expect(crear).toBeDisabled();
  await aceptarCondiciones(page);
  await crear.click();
  await expect(page.getByText("Ya existe una cuenta con ese correo")).toBeVisible();

  const email = `wf.registro.${Date.now()}@orion.local`;
  await page.locator("#email").fill(email);
  await crear.click();
  await page.waitForURL(/\/profesores/);
  expect(await ultimoCorreo(page, email, /verificar\?token=/)).not.toBeNull();
});

test("[v-registro-google.2] sin un ingreso reciente con Google, dice que venció", async ({ page }) => {
  await page.goto("/registro/completar");
  await expect(page.getByText("Tu ingreso venció")).toBeVisible();
});

test("[v-recuperar.1 v-recuperar.2 v-restablecer.1 v-restablecer.2] recuperar la contraseña", async ({ page, browser }) => {
  // Una cuenta propia, para no cambiarle la clave a la semilla.
  const email = `wf.clave.${Date.now()}@orion.local`;
  await page.goto("/registro");
  await page.waitForLoadState("networkidle");
  await page.locator("#nombre").fill("Clave Olvidada");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("orion123*");
  await aceptarCondiciones(page);
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await page.waitForURL(/\/profesores/);
  const otra = await browser.newContext();
  const sesionVieja = await otra.newPage();
  await entrar(sesionVieja, { email, pass: "orion123*" }).catch(() => {});

  for (const correo of [email, "nadie@orion.local"]) {
    await page.goto("/recuperar");
    await page.waitForLoadState("networkidle");
    await page.getByRole("textbox").first().fill(correo);
    await page.getByRole("button", { name: /Enviar enlace/ }).click();
    await expect(page.getByText(/enviamos un enlace/)).toBeVisible();
  }
  const correo = await ultimoCorreo(page, email, /restablecer\?token=/);
  const enlace = correo?.match(/https?:\/\/[^"\\\s]*\/restablecer\?token=[A-Za-z0-9_-]+/)?.[0];
  expect(enlace).toBeTruthy();
  await page.goto(enlace!.replace(/^https?:\/\/[^/]+/, ""));
  await page.locator('input[type="password"]').first().fill("nueva-clave-123");
  await page.getByRole("button", { name: /Guardar contraseña/ }).click();
  await expect(page.getByText(/quedó|lista|cambiada/i).first()).toBeVisible();
  // El enlace es de un solo uso.
  await page.goto(enlace!.replace(/^https?:\/\/[^/]+/, ""));
  await page.locator('input[type="password"]').first().fill("otra-clave-123");
  await page.getByRole("button", { name: /Guardar contraseña/ }).click();
  await expect(page.getByText(/no es válido|venció|ya se usó|expir/i).first()).toBeVisible();
  // Y la sesión que estaba abierta en otro lado se cierra.
  const r = await api(sesionVieja, "GET", "/api/v1/auth/me");
  expect(r.status).toBe(401);
  await otra.close();
});

test("[v-verificar.2 v-invitacion.2] los enlaces inválidos dicen qué hacer", async ({ page }) => {
  await page.goto("/verificar?token=invalido");
  await expect(page.getByText("No pudimos confirmar tu correo")).toBeVisible();
  // El enlace viejo (?token=) lleva a la pantalla de ahora; uno que no existe se ve vencido.
  await page.goto("/invitacion?token=invalido");
  await expect(page).toHaveURL(/\/invitacion\/invalido$/);
  await expect(page.getByRole("heading", { name: "Esta invitación ya venció." })).toBeVisible();
  await expect(page.getByRole("link", { name: "Aceptar la invitación" })).toHaveCount(0);
});

test("[v-terminos.1 v-privacidad.1] los documentos legales tienen versión y responsable", async ({ page }) => {
  for (const ruta of ["/terminos", "/privacidad"]) {
    await page.goto(ruta);
    await expect(page.getByText(/Versión \d/).first()).toBeVisible();
    await expect(page.getByText(/Diego Alejandro Pardo Montero|Responsable/).first()).toBeVisible();
    await sinDesbordeLateral(page);
  }
});

test("[v-portada.5] los enlaces del pie de la portada abren", async ({ page }) => {
  await page.goto("/");
  const pie = page.locator("footer");
  await pie.getByRole("link", { name: /Términos/ }).first().click();
  await expect(page).toHaveURL(/\/terminos/);
  await page.goBack();
  await page.locator("footer").getByRole("link", { name: /Privacidad|tratamiento/i }).first().click();
  await expect(page).toHaveURL(/\/privacidad/);
});
