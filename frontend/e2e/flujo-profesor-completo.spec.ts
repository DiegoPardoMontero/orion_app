import { devices, expect, test, type Browser, type BrowserContext, type Locator, type Page } from "@playwright/test";
import {
  aceptarCondiciones,
  api,
  entrar,
  estudianteNueva,
  SEMILLA,
  sinDesbordeLateral,
  sqlLocal,
  ultimoCorreo,
  verificarCorreo,
} from "./apoyo";

/**
 * El camino completo de un profesor nuevo, en el navegador y de punta a punta (27/09/2026, antes de
 * abrir Orión a profesores reales): llega por «Enseña con Orión», se postula, el admin le pide un
 * cambio, lo corrige, lo aprueban, entra, arma su perfil y sus horarios, una estudiante reserva con
 * él, dicta la clase, escribe el acta y ve lo que gana. Y, aparte, el mismo camino entrando por la
 * invitación de profe fundador.
 *
 * <p>Un solo `test` largo por camino, con un `test.step` por tramo: lo que no bloquea el camino se
 * comprueba con `expect.soft`, así una falla no esconde las de más adelante; lo que sí lo bloquea
 * (no se puede seguir sin eso) es un `expect` normal.
 *
 * <p>Cada página se vigila: los errores de consola, las excepciones sin atrapar y las respuestas
 * ≥ 400 del API (menos las que la app espera) se anotan con el tramo y la pantalla donde salieron.
 * Al final, los errores de consola y los 5xx hacen fallar la prueba; los 4xx se listan.
 *
 * <p>SQL (`sqlLocal`) solo para lo que en local no se puede hacer por la app: la foto y la hoja de
 * vida (no hay Cloudinary) y mover la clase al pasado para que termine.
 */

// ------------------------------------------------------------------ vigilancia

type Hallazgo = {
  tipo: "consola" | "excepcion" | "http" | "desborde";
  quien: string;
  paso: string;
  pantalla: string;
  detalle: string;
};

const hallazgos: Hallazgo[] = [];
let pasoActual = "(antes de empezar)";

/**
 * Las respuestas ≥ 400 que la app provoca a propósito y maneja: no son hallazgos. Se afina con cada
 * corrida; lo que no esté aquí sale en el informe.
 */
const ESPERADAS: { metodo: string; ruta: RegExp; status: number; porque: string }[] = [
  { metodo: "GET", ruta: /^\/api\/v1\/auth\/me$/, status: 401, porque: "sin sesión, las pantallas públicas preguntan quién es" },
  {
    metodo: "GET",
    ruta: /^\/api\/v1\/me\/teacher-application$/,
    status: 404,
    porque: "el wizard pregunta primero y crea el borrador solo si no hay; el armazón de un estudiante pregunta lo mismo",
  },
  {
    metodo: "GET",
    ruta: /^\/api\/v1\/bookings\/[0-9a-f-]+\/lesson-note$/,
    status: 404,
    porque: "la pantalla del acta pregunta si ya hay una; la primera vez no la hay y muestra «Cuéntanos cómo estuvo»",
  },
];

function pantallaDe(page: Page): string {
  try {
    const u = new URL(page.url());
    return u.pathname + u.search;
  } catch {
    return page.url();
  }
}

function vigilar(page: Page, quien: string) {
  page.on("console", (msg) => {
    if (msg.type() !== "error") return;
    const url = msg.location()?.url ?? "";
    const texto = msg.text();
    // El «Failed to load resource» de una respuesta del API ya lo cuenta el registro de respuestas.
    if (/Failed to load resource/.test(texto) && /\/api\//.test(url)) return;
    hallazgos.push({ tipo: "consola", quien, paso: pasoActual, pantalla: pantallaDe(page), detalle: `${texto}${url ? ` (${url})` : ""}` });
  });
  page.on("pageerror", (error) => {
    hallazgos.push({ tipo: "excepcion", quien, paso: pasoActual, pantalla: pantallaDe(page), detalle: error.message });
  });
  page.on("response", (respuesta) => {
    const status = respuesta.status();
    if (status < 400) return;
    let ruta = respuesta.url();
    try {
      ruta = new URL(respuesta.url()).pathname;
    } catch {
      // se queda con la URL entera
    }
    const esApi = ruta.startsWith("/api/");
    // Un 4xx fuera del API (un favicon, un chunk) no es del producto; un 5xx en cualquier sitio, sí.
    if (!esApi && status < 500) return;
    const metodo = respuesta.request().method();
    if (ESPERADAS.some((e) => e.metodo === metodo && e.ruta.test(ruta) && e.status === status)) return;
    hallazgos.push({ tipo: "http", quien, paso: pasoActual, pantalla: pantallaDe(page), detalle: `${status} ${metodo} ${ruta}` });
  });
}

/** Una pantalla del camino: se deja asentar, se revisa que no se desborde de lado y se captura. */
let capturas = 0;

async function revisarPantalla(page: Page, quien: string, nombre: string) {
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  // Cada pantalla queda capturada en test-results, para mirarla después.
  const archivo = `${String(++capturas).padStart(2, "0")}-${quien}-${nombre}`.replace(/[^\w@-]+/g, "_");
  await page.screenshot({ path: test.info().outputPath(`pantallas/${archivo}.png`), fullPage: true }).catch(() => {});
  let problema: string | null = null;
  try {
    await sinDesbordeLateral(page);
  } catch (error) {
    problema = (error as Error).message.split("\n")[0];
  }
  // `sinDesbordeLateral` compara contra `innerWidth`, y en un celular emulado (isMobile) el navegador
  // ensancha la ventana de diseño hasta lo que mida la página: una página de 416 px en un Pixel 7 de
  // 412 dice innerWidth = 416 y pasa. Aquí se mide contra el ancho real de la pantalla, y se dice qué
  // se sale.
  const pantalla = page.viewportSize()?.width ?? 0;
  const medida = await page.evaluate((ancho) => {
    const fuera: string[] = [];
    for (const el of Array.from(document.querySelectorAll("body *"))) {
      const caja = el.getBoundingClientRect();
      if (!caja.width || caja.right <= ancho + 1) continue;
      // Lo que vive dentro de algo que se desplaza de lado a propósito (pestañas, carruseles) no cuenta.
      let padre = el.parentElement;
      let enDesplazable = false;
      while (padre && padre !== document.body) {
        if (/(auto|scroll|hidden|clip)/.test(getComputedStyle(padre).overflowX)) {
          enDesplazable = true;
          break;
        }
        padre = padre.parentElement;
      }
      if (!enDesplazable) fuera.push(`<${el.tagName.toLowerCase()} class="${String(el.getAttribute("class") ?? "").slice(0, 60)}"> hasta ${Math.round(caja.right)} px`);
    }
    return { ancho: document.documentElement.scrollWidth, fuera: fuera.slice(0, 3) };
  }, pantalla);
  if (!problema && pantalla && medida.ancho > pantalla + 1) {
    problema = `la página mide ${medida.ancho} px en una pantalla de ${pantalla} px`;
  }
  if (problema) {
    const n = hallazgos.length;
    const captura = test.info().outputPath(`desborde-${n}-${nombre.replace(/[^\w-]+/g, "_")}.png`);
    await page.screenshot({ path: captura, fullPage: true }).catch(() => {});
    hallazgos.push({
      tipo: "desborde",
      quien,
      paso: pasoActual,
      pantalla: `${nombre} · ${pantallaDe(page)}`,
      detalle: `${problema}${medida.fuera.length ? ` · se sale: ${medida.fuera.join("; ")}` : ""} · captura: ${captura}`,
    });
  }
}

/** Un tramo del camino, con su nombre en cada hallazgo que salga mientras corre. */
async function paso(nombre: string, cuerpo: () => Promise<void>) {
  await test.step(nombre, async () => {
    pasoActual = nombre;
    await cuerpo();
  });
}

/** Lo que se vio, a la consola y a las anotaciones del informe; y la prueba falla por lo grave. */
function cerrarVigilancia() {
  const graves = hallazgos.filter(
    (h) => h.tipo === "consola" || h.tipo === "excepcion" || h.tipo === "desborde" || (h.tipo === "http" && /^5\d\d /.test(h.detalle)),
  );
  const leves = hallazgos.filter((h) => !graves.includes(h));
  for (const h of hallazgos) {
    test.info().annotations.push({ type: `hallazgo:${h.tipo}`, description: `[${h.quien}] ${h.paso} · ${h.pantalla} · ${h.detalle}` });
  }
  if (hallazgos.length) {
    console.log(`\n--- ${hallazgos.length} hallazgos de la vigilancia ---`);
    for (const h of hallazgos) console.log(`${h.tipo.padEnd(9)} [${h.quien}] ${h.paso} · ${h.pantalla}\n          ${h.detalle}`);
  }
  expect.soft(graves.map((h) => `${h.tipo} [${h.quien}] ${h.paso} · ${h.pantalla} · ${h.detalle}`), "errores de consola, excepciones, 5xx o desbordes").toEqual([]);
  if (leves.length) console.log(`(${leves.length} respuestas 4xx no esperadas: no hacen fallar la prueba, se reportan)`);
}

// ------------------------------------------------------------------ utilidades del camino

/**
 * Aparta los avisos de «Logro nuevo» cuando estorban a un clic. No usa `apartarCelebraciones` de
 * apoyo.ts: ese espera a que el aviso desaparezca después de un clic, y con varios logros en cola
 * («Y 2 más en camino») el siguiente sale en el mismo sitio y la espera no termina nunca.
 */
async function apartarLogros(page: Page) {
  await page.addLocatorHandler(
    page.locator('[role="dialog"][aria-labelledby="logro-nuevo"]'),
    async (logro) => {
      await logro.getByRole("button", { name: "Seguir", exact: true }).click();
    },
    { noWaitAfter: true },
  );
}

/** Un contexto móvil (el del proyecto) con su página, vigilada y con los logros apartados. */
async function abrir(browser: Browser, quien: string): Promise<{ ctx: BrowserContext; page: Page }> {
  const ctx = await browser.newContext();
  // Sin tope, un clic sobre algo que nunca aparece espera hasta el final de la prueba.
  ctx.setDefaultTimeout(20_000);
  ctx.setDefaultNavigationTimeout(45_000);
  const page = await ctx.newPage();
  vigilar(page, quien);
  await apartarLogros(page);
  return { ctx, page };
}

/** Lo mismo a 1280 px, con la sesión de otro contexto: para ver las pantallas de escritorio. */
async function abrirEscritorio(browser: Browser, quien: string, desde: BrowserContext): Promise<{ ctx: BrowserContext; page: Page }> {
  const ctx = await browser.newContext({
    ...devices["Desktop Chrome"],
    viewport: { width: 1280, height: 900 },
    storageState: await desde.storageState(),
  });
  ctx.setDefaultTimeout(20_000);
  ctx.setDefaultNavigationTimeout(45_000);
  const page = await ctx.newPage();
  vigilar(page, quien);
  await apartarLogros(page);
  return { ctx, page };
}

/** El nombre y la descripción del profe se leen enteros: ni «…», ni recortados por alto o ancho. */
async function comprobarEntero(loc: Locator, texto: string, que: string) {
  await expect.soft(loc, `${que}: el texto completo`).toHaveText(texto);
  const m = await loc.evaluate((el) => {
    const s = getComputedStyle(el);
    return {
      overflow: s.textOverflow,
      clamp: s.getPropertyValue("-webkit-line-clamp"),
      ancho: el.scrollWidth - el.clientWidth,
      alto: el.scrollHeight - el.clientHeight,
    };
  });
  expect.soft(m.overflow === "ellipsis" && m.ancho > 0, `${que}: cortado con «…»`).toBe(false);
  expect.soft(["", "none"].includes(m.clamp), `${que}: con line-clamp (${m.clamp})`).toBe(true);
  expect.soft(m.ancho, `${que}: se sale de su caja a lo ancho`).toBeLessThanOrEqual(1);
  expect.soft(m.alto, `${que}: se corta a lo alto`).toBeLessThanOrEqual(2);
}

/** El día de la semana ISO (1 = lunes … 7 = domingo) y la fecha, en Bogotá, a `dias` de hoy. */
function diaEnBogota(dias: number): { iso: number; fecha: string; nombre: string } {
  const instante = new Date(Date.now() + dias * 86_400_000);
  const fecha = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota" }).format(instante);
  const corto = new Intl.DateTimeFormat("en-US", { timeZone: "America/Bogota", weekday: "short" }).format(instante);
  const iso = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].indexOf(corto) + 1;
  const nombre = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"][iso - 1];
  return { iso, fecha, nombre };
}

const ACUERDOS = "Acepta los nuevos acuerdos";
const PAGO = "Falta a dónde te pagamos";
const BIENVENIDA = "Bienvenida a Orión";

/**
 * Los diálogos que le salen a un profe recién aprobado al entrar, en el orden en que llegan. Se
 * resuelve cada uno como lo haría él y se devuelve la secuencia para compararla con la esperada.
 */
async function atenderDialogosDeEntrada(page: Page, datosDePago: { llave: string; documento: string }): Promise<string[]> {
  const vistos: string[] = [];
  const edad = page.getByRole("dialog", { name: "Antes de seguir" });
  const whatsapp = page.getByRole("dialog", { name: "Falta tu WhatsApp" });
  const acuerdos = page.getByRole("dialog", { name: ACUERDOS });
  const pago = page.getByRole("dialog", { name: PAGO });
  const bienvenida = page.getByRole("dialog", { name: BIENVENIDA });
  const recorrido = page.getByRole("dialog").filter({ has: page.getByRole("heading", { name: /^Te muestro Orión/ }) });
  const cualquiera = edad.or(whatsapp).or(acuerdos).or(pago).or(bienvenida).or(recorrido);

  for (let i = 0; i < 8; i++) {
    const llego = await cualquiera.first().waitFor({ state: "visible", timeout: 10_000 }).then(() => true, () => false);
    if (!llego) break;
    if (await edad.isVisible()) {
      vistos.push("Antes de seguir");
      await edad.getByLabel(/mayor de 18 años/).check();
      await edad.getByRole("button", { name: "Confirmar" }).click();
      await expect(edad).toBeHidden();
    } else if (await whatsapp.isVisible()) {
      vistos.push("Falta tu WhatsApp");
      await whatsapp.locator("#whatsapp-obligatorio").fill("3001234567");
      await whatsapp.getByRole("button", { name: "Guardar" }).click();
      await expect(whatsapp).toBeHidden();
    } else if (await acuerdos.isVisible()) {
      vistos.push(ACUERDOS);
      const datos = acuerdos.getByLabel(/Autorizo el tratamiento de mis datos personales/);
      if (await datos.isVisible()) await datos.check();
      await acuerdos.getByRole("button", { name: "Aceptar los nuevos acuerdos" }).click();
      await expect(acuerdos).toBeHidden();
    } else if (await pago.isVisible()) {
      vistos.push(PAGO);
      await pago.locator("#llave").fill(datosDePago.llave);
      if (!(await pago.locator("#titular").inputValue()).trim()) {
        await pago.locator("#titular").fill("Titular de Prueba");
      }
      await pago.locator("#numero-documento").fill(datosDePago.documento);
      await pago.getByRole("button", { name: "Guardar mis datos de pago" }).click();
      await expect(pago).toBeHidden();
    } else if (await bienvenida.isVisible()) {
      vistos.push(BIENVENIDA);
      await bienvenida.getByRole("button", { name: "Lo veo después" }).first().click();
      await expect(bienvenida).toBeHidden();
    } else if (await recorrido.isVisible()) {
      vistos.push("Recorrido");
      await recorrido.getByRole("button", { name: /^(Ahora no|Saltar)$/ }).first().click();
      await expect(recorrido).toBeHidden();
    }
  }
  return vistos;
}

/** La postulación de un aspirante, lista para enviar: lo que no se puede subir en local, por SQL. */
function fotoYHojaDeVidaPorSql(email: string, applicationId: string, fotoUrl: string) {
  // En local no hay Cloudinary: la foto y la hoja de vida quedan como las dejaría una subida real.
  sqlLocal(`update users set photo_url = '${fotoUrl}' where email = '${email}'`);
  sqlLocal(
    `insert into teacher_documents (user_id, application_id, doc_type, file_name, storage_key, content_type, size_bytes)
     select id, '${applicationId}', 'CV', 'hoja-de-vida.pdf', 'e2e/hoja-de-vida.pdf', 'application/pdf', 48213
       from users where email = '${email}'`,
  );
}

/** Las páginas abiertas del camino: si algo lo corta, se captura cada una para ver dónde quedó. */
const abiertas = new Map<string, Page>();

async function capturarAbiertas() {
  for (const [nombre, page] of abiertas) {
    if (!page.isClosed()) await page.screenshot({ path: test.info().outputPath(`corte-${nombre}.png`), fullPage: true }).catch(() => {});
  }
}

// ------------------------------------------------------------------ el camino

test("un profesor nuevo: de «Enseña con Orión» a su primera clase dictada, con acta y ganancias", async ({ browser }, info) => {
  test.setTimeout(15 * 60_000);
  hallazgos.length = 0;
  const base = String(info.project.use.baseURL ?? "http://localhost:3000");
  const sello = Date.now();
  const profe = {
    // Un nombre y una descripción largos a propósito: se tienen que leer enteros en todas partes.
    nombre: "Maximiliano Andrés Castañeda Villavicencio",
    email: `flujo.profe.${sello}@orion.local`,
    pass: "orion123*",
    titular: "Inglés conversacional para profesionales que necesitan presentar, negociar y viajar con confianza",
    bio:
      "Soy profesor de inglés desde hace seis años. Trabajo con adultos que ya estudiaron el idioma pero se " +
      "bloquean al hablar. Mis clases son conversaciones reales sobre tu trabajo y tus planes, con corrección " +
      "amable y tareas cortas para practicar entre una clase y la siguiente.",
    tarifa: 60_000,
  };
  const notaDeCambios = "Cuéntale al estudiante cómo preparas entrevistas de trabajo y márcalo en tus objetivos.";
  const manana = diaEnBogota(1);
  const pasado = diaEnBogota(2);

  const { ctx: profeCtx, page: p } = await abrir(browser, "profe");
  const { ctx: adminCtx, page: admin } = await abrir(browser, "admin");
  const { ctx: estCtx, page: e } = await abrir(browser, "estudiante");
  abiertas.set("profe", p);
  abiertas.set("admin", admin);
  abiertas.set("estudiante", e);
  const estudiante = { nombre: "Lucía Fernández", email: "" };
  let profeId = "";
  let applicationId = "";
  let bookingId = "";
  let estudianteId = "";

  try {
    // ---------------------------------------------------------------- 1
    await paso("1. El visitante llega por «Enseña con Orión», crea su cuenta de profesor y confirma el correo", async () => {
      await p.goto("/ensena-con-orion");
      await expect(p.getByRole("heading", { name: "Enseña inglés. Construye tu agenda en Orión." })).toBeVisible();
      await revisarPantalla(p, "profe", "ensena-con-orion");
      await p.getByRole("link", { name: /Crea tu cuenta y postúlate/ }).first().click();
      await expect(p).toHaveURL(/\/registro\?rol=profesor/);
      await p.waitForLoadState("networkidle");
      // «Quiero enseñar» ya viene elegido desde la página de profesores.
      await expect(p.getByRole("button", { name: /Quiero enseñar/ })).toHaveAttribute("aria-pressed", "true");
      await expect(p.getByText("Creamos tu cuenta y sigues con tu postulación", { exact: false })).toBeVisible();
      await revisarPantalla(p, "profe", "registro-profesor");

      await p.locator("#nombre").fill(profe.nombre);
      await p.locator("#email").fill(profe.email);
      await p.locator("#password").fill(profe.pass);
      await aceptarCondiciones(p);
      await p.getByRole("button", { name: "Crear cuenta y postularme" }).click();
      await p.waitForURL(/\/aplicacion$/, { timeout: 20_000 });
      // Se ve el aviso de correo sin confirmar mientras tanto.
      await expect(p.getByRole("heading", { name: "Datos personales" })).toBeVisible({ timeout: 20_000 });

      await verificarCorreo(p, profe.email);
      await revisarPantalla(p, "profe", "verificar");
      await p.getByRole("link", { name: /Entrar a Orión/ }).click();
      // El aspirante vuelve a su postulación, no al buscador de profesores.
      await p.waitForURL(/\/aplicacion$/, { timeout: 20_000 });
      await expect(p.getByRole("heading", { name: "Datos personales" })).toBeVisible({ timeout: 20_000 });

      const yo = (await api(p, "GET", "/api/v1/auth/me")).json as { id: string; role: string; emailVerified: boolean };
      profeId = yo.id;
      expect.soft(yo.role, "la cuenta nace como aspirante a profesor").toBe("TEACHER_APPLICANT");
      expect.soft(yo.emailVerified, "el correo quedó verificado").toBe(true);
    });

    // ---------------------------------------------------------------- 2
    await paso("2. Llena la postulación paso a paso (foto y hoja de vida por SQL) y la envía", async () => {
      const aplicacion = (await api(p, "GET", "/api/v1/me/teacher-application")).json as { id: string; status: string };
      expect(aplicacion.status).toBe("DRAFT");
      applicationId = aplicacion.id;
      fotoYHojaDeVidaPorSql(profe.email, applicationId, `${base}/icon-192.png`);
      // Con la foto puesta, el wizard la muestra al volver a cargar.
      await p.reload();
      await expect(p.getByRole("heading", { name: "Datos personales" })).toBeVisible({ timeout: 20_000 });
      await expect(p.getByText("Paso 1 de 6")).toBeVisible();
      await revisarPantalla(p, "profe", "aplicacion-1-datos");

      // 1 · Datos personales
      await p.locator("#headline").fill(profe.titular);
      await p.getByRole("button", { name: /Siguiente/ }).click();

      // 2 · Enseñanza
      await expect(p.getByRole("heading", { name: "Enseñanza" })).toBeVisible();
      await p.locator("#bio").fill(profe.bio);
      await p.locator("button", { hasText: "Inglés" }).click();
      await p.getByRole("button", { name: "Intermedio", exact: true }).click();
      await p.getByRole("button", { name: "Avanzado", exact: true }).click();
      await p.getByRole("button", { name: "Conversación", exact: true }).click();
      await p.getByRole("button", { name: "Negocios", exact: true }).click();
      await revisarPantalla(p, "profe", "aplicacion-2-ensenanza");
      await p.getByRole("button", { name: /Siguiente/ }).click();

      // 3 · Experiencia (sin clase gratis: la reserva de la estudiante tiene que cobrar comisión)
      await expect(p.getByRole("heading", { name: "Experiencia" })).toBeVisible();
      await p.locator("#city").fill("Medellín");
      await p.locator("#years").fill("6");
      await p.locator("#education").fill("Licenciatura en Lenguas Modernas");
      await p.getByRole("switch", { name: "Certificado" }).click();
      await expect(p.getByRole("switch", { name: "Primera clase gratis" })).toHaveAttribute("aria-checked", "false");
      await revisarPantalla(p, "profe", "aplicacion-3-experiencia");
      await p.getByRole("button", { name: /Siguiente/ }).click();

      // 4 · Documentos: la hoja de vida sembrada por SQL aparece como subida.
      await expect(p.getByRole("heading", { name: "Documentos" })).toBeVisible();
      await expect.soft(p.getByText("hoja-de-vida.pdf")).toBeVisible();
      await revisarPantalla(p, "profe", "aplicacion-4-documentos");
      await p.getByRole("button", { name: /Siguiente/ }).click();

      // 5 · Acuerdo del profesor
      await expect(p.getByRole("heading", { name: "Acuerdo", exact: true })).toBeVisible();
      // La casilla es controlada: al marcarla se acepta en el servidor y se cambia por «Aceptaste…».
      // Se habilita cuando llega el texto del acuerdo.
      const casilla = p.getByLabel("He leído y acepto el acuerdo del profesor de Orión.");
      await expect(casilla).toBeEnabled();
      await casilla.click();
      await expect(p.getByText("Aceptaste el acuerdo. ¡Listo!")).toBeVisible();
      await revisarPantalla(p, "profe", "aplicacion-5-acuerdo");
      await p.getByRole("button", { name: /Siguiente/ }).click();

      // 6 · Revisar y enviar
      await expect(p.getByRole("heading", { name: "Revisar y enviar" })).toBeVisible();
      await expect(p.getByText("¡Todo listo! Revisa que esté a tu gusto y envía tu postulación a revisión.")).toBeVisible();
      await revisarPantalla(p, "profe", "aplicacion-6-enviar");

      // Antes de enviarla, las dos pantallas del aspirante a 1280 px.
      const esc = await abrirEscritorio(browser, "profe@1280", profeCtx);
      try {
        await esc.page.goto("/aplicacion");
        await expect(esc.page.getByRole("heading", { name: "Datos personales" })).toBeVisible({ timeout: 20_000 });
        await revisarPantalla(esc.page, "profe@1280", "aplicacion");
        await esc.page.goto("/aplicacion/estado");
        await expect(esc.page.getByRole("heading", { name: "Estado de tu postulación" })).toBeVisible();
        await revisarPantalla(esc.page, "profe@1280", "aplicacion-estado-borrador");
      } finally {
        await esc.ctx.close();
      }

      await p.getByRole("button", { name: "Enviar a revisión" }).click();
      await p.waitForURL(/\/aplicacion\/estado$/);
      await expect(p.getByText(/Tu postulación está en la fila de revisión/)).toBeVisible();
      await revisarPantalla(p, "profe", "aplicacion-estado-enviada");
    });

    // ---------------------------------------------------------------- 3
    await paso("3. El admin pide cambios, el aspirante corrige y reenvía, el admin aprueba y llega el correo", async () => {
      await entrar(admin, SEMILLA.admin);
      await admin.goto("/admin/aplicaciones");
      await expect(admin.getByRole("heading", { name: "Postulaciones", exact: true })).toBeVisible();
      const fila = admin.locator("tr", { hasText: profe.email });
      await expect(fila).toBeVisible({ timeout: 15_000 });
      await revisarPantalla(admin, "admin", "admin-aplicaciones");
      await fila.getByRole("link", { name: "Revisar" }).click();
      await expect(admin).toHaveURL(new RegExp(`/admin/aplicaciones/${applicationId}$`));
      await expect(admin.getByText(profe.titular).first()).toBeVisible();
      await revisarPantalla(admin, "admin", "admin-aplicacion-detalle");

      await admin.getByRole("button", { name: "Empezar revisión" }).click();
      await admin.getByRole("button", { name: "Pedir cambios" }).click();
      const modal = admin.getByRole("dialog", { name: "Pedir cambios" });
      await modal.locator("textarea").fill(notaDeCambios);
      await modal.getByRole("button", { name: "Pedir cambios" }).click();
      await expect(modal).toBeHidden();
      await expect(admin.getByText("Sin acciones disponibles en este estado.")).toBeVisible();

      // El aspirante: la nota en su estado, por correo y en el resumen.
      const correoCambios = await ultimoCorreo(p, profe.email, /necesita cambios/);
      expect.soft(correoCambios, "llega el correo de «necesita cambios»").not.toBeNull();
      expect.soft(correoCambios ?? "", "el correo trae la nota de la revisión").toContain("entrevistas de trabajo");
      for (const [, enlace] of (correoCambios ?? "").matchAll(/href=\\"([^"\\]+)\\"/g)) {
        expect.soft(enlace, "los enlaces del correo de cambios son absolutos").toMatch(/^https?:\/\//);
      }
      await p.goto("/aplicacion/estado");
      await expect(p.getByText("Comentario de la revisión")).toBeVisible();
      await expect(p.getByText(notaDeCambios)).toBeVisible();
      await revisarPantalla(p, "profe", "aplicacion-estado-cambios");
      await p.getByRole("link", { name: /Editar y reenviar/ }).click();
      await p.waitForURL(/\/aplicacion$/);
      await expect(p.getByRole("heading", { name: "Tu postulación" })).toBeVisible();
      await expect(p.getByText("Lo que pide la revisión")).toBeVisible();
      await revisarPantalla(p, "profe", "aplicacion-resumen");

      // Corrige solo la sección de enseñanza y vuelve al resumen.
      await p.locator("section", { has: p.getByRole("heading", { name: "Enseñanza", exact: true }) }).getByRole("button", { name: "Editar" }).click();
      await expect(p.locator("#bio")).toHaveValue(profe.bio);
      profe.bio += " También preparo entrevistas de trabajo en inglés, con simulacros como los de verdad.";
      await p.locator("#bio").fill(profe.bio);
      await p.getByRole("button", { name: "Entrevistas", exact: true }).click();
      await p.getByRole("button", { name: "Guardar y volver al resumen" }).click();
      await expect(p.getByRole("heading", { name: "Tu postulación" })).toBeVisible();
      await expect.soft(p.getByText(/También preparo entrevistas de trabajo/)).toBeVisible();
      await p.getByRole("button", { name: "Enviar a revisión" }).click();
      await p.waitForURL(/\/aplicacion\/estado$/);
      await expect(p.getByText(/Tu postulación está en la fila de revisión/)).toBeVisible();

      // El admin la ve de nuevo en revisión y la aprueba.
      await admin.reload();
      await admin.getByRole("button", { name: "Empezar revisión" }).click();
      await admin.getByRole("button", { name: "Aprobar" }).click();
      await expect(admin.getByText("Sin acciones disponibles en este estado.")).toBeVisible();
      await revisarPantalla(admin, "admin", "admin-aplicacion-aprobada");

      const aprobada = await ultimoCorreo(p, profe.email, /fue aprobada/);
      expect(aprobada, "llega el correo de aprobación").not.toBeNull();
      const enlaces = [...(aprobada ?? "").matchAll(/href=\\"([^"\\]+)\\"/g)].map((m) => m[1]);
      expect.soft(enlaces.length, "el correo de aprobación trae enlaces").toBeGreaterThan(0);
      for (const enlace of enlaces) expect.soft(enlace, "los enlaces del correo de aprobación son absolutos").toMatch(/^https?:\/\//);

      // Quien tenía abierta su postulación la ve aprobada al volver a mirarla, con la misma sesión.
      await p.goto("/aplicacion/estado");
      await p.waitForLoadState("networkidle");
      console.log(`El aspirante recién aprobado, en /aplicacion/estado, queda en: ${pantallaDe(p)}`);
      await expect.soft(p.getByText(/Tu postulación fue aprobada/).first(), "ve su postulación aprobada").toBeVisible();
      await revisarPantalla(p, "profe", "aplicacion-estado-aprobada");
    });

    // ---------------------------------------------------------------- 4
    await paso("4. El profe aprobado entra: los diálogos en orden y ninguno se repite al recargar", async () => {
      await profeCtx.clearCookies();
      await entrar(p, { email: profe.email, pass: profe.pass });
      await expect(p).toHaveURL(/\/mis-clases/);
      const vistos = await atenderDialogosDeEntrada(p, { llave: "300 765 4321", documento: "1098765432" });
      console.log(`Diálogos al entrar: ${vistos.join(" → ") || "(ninguno)"}`);
      // Aceptó Términos y política al registrarse y el acuerdo del profesor en la postulación: no le
      // falta ninguno. Lo que sí falta es a dónde pagarle, y después la bienvenida (o el recorrido,
      // si no hay video de bienvenida en Ajustes).
      expect.soft(vistos, "no le vuelve a pedir acuerdos que ya aceptó").not.toContain(ACUERDOS);
      expect.soft(vistos, "no le pide la edad ni el WhatsApp, que dio al registrarse").not.toContain("Antes de seguir");
      expect.soft(vistos.filter((v) => v !== ACUERDOS)[0], "lo primero, a dónde le pagamos").toBe(PAGO);
      expect.soft(vistos.at(-1), "lo último, la bienvenida o el recorrido").toMatch(new RegExp(`^(${BIENVENIDA}|Recorrido)$`));
      expect.soft(new Set(vistos).size, "ningún diálogo sale dos veces").toBe(vistos.length);
      await revisarPantalla(p, "profe", "mis-clases-primera-vez");

      await p.reload();
      await p.waitForLoadState("networkidle");
      await p.waitForTimeout(2500);
      for (const nombre of [ACUERDOS, PAGO, BIENVENIDA, "Antes de seguir", "Falta tu WhatsApp"]) {
        await expect.soft(p.getByRole("dialog", { name: nombre }), `«${nombre}» no vuelve al recargar`).toHaveCount(0);
      }
      await expect.soft(p.getByRole("dialog").filter({ has: p.getByRole("heading", { name: /^Te muestro Orión/ }) }), "el recorrido no vuelve al recargar").toHaveCount(0);
      // Los datos de pago quedaron guardados (enmascarados).
      await p.goto("/perfil?seccion=pagos");
      await expect.soft(p.getByText("••••4321")).toBeVisible();
      await revisarPantalla(p, "profe", "perfil-pagos");
    });

    // ---------------------------------------------------------------- 5
    const figuras = (await (await p.request.get("/api/v1/catalog/figures")).json()) as {
      commissionPercent: number;
      founderCommissionPercent: number;
    };
    const comision = Math.round((profe.tarifa * figuras.commissionPercent) / 100);
    const paraElProfe = profe.tarifa - comision;
    const cop = (n: number) => `$${new Intl.NumberFormat("es-CO").format(n)}`;

    await paso("5. Arma su perfil: tarifa, franjas (una a la media hora), una fecha bloqueada; publica y se ve sin cuenta", async () => {
      await p.goto("/mis-clases");
      // Rigel le recuerda lo que falta para recibir estudiantes.
      await expect.soft(p.getByRole("complementary", { name: "Completa tu perfil" }), "la franja de Rigel dice qué falta del perfil").toBeVisible();
      const antes = (await api(p, "GET", "/api/v1/me/profile/pending")).json as { missing: string[] };
      console.log(`Lo que le falta al perfil recién aprobado: ${antes.missing.join(", ")}`);
      const heredados = ["FOTO", "TITULAR", "DESCRIPCION", "IDIOMAS"].filter((x) => antes.missing.includes(x));
      expect.soft(heredados, "lo que traía la postulación ya cuenta en el perfil").toEqual([]);

      await p.goto("/perfil");
      await expect(p.locator("#tarifa")).toBeVisible();
      // Lo que llenó en la postulación ya está en su perfil.
      await expect.soft(p.locator("#headline")).toHaveValue(profe.titular);
      await expect.soft(p.locator("#bio")).toHaveValue(profe.bio);
      await expect.soft(p.locator("#city")).toHaveValue("Medellín");
      await revisarPantalla(p, "profe", "perfil");

      await p.locator("#tarifa").fill(String(profe.tarifa));
      await expect.soft(p.getByText(`Recibes ${cop(paraElProfe)} por clase (comisión de Orión: ${figuras.commissionPercent} %).`)).toBeVisible();
      const desglose = p.locator("section", { has: p.locator("#tarifa") });
      await expect.soft(desglose.getByText(cop(paraElProfe), { exact: true })).toBeVisible();
      await expect.soft(desglose.getByText(cop(comision), { exact: true })).toBeVisible();
      await p.getByRole("switch", { name: "Perfil visible" }).click();
      const barra = p.getByRole("region", { name: "Cambios sin guardar" });
      await barra.getByRole("button", { name: "Guardar cambios" }).click();
      await expect(p.getByText("Cambios guardados")).toBeVisible();
      await p.reload();
      await expect(p.locator("#tarifa")).toHaveValue(String(profe.tarifa));
      await expect(p.getByRole("switch", { name: "Perfil visible" })).toHaveAttribute("aria-checked", "true");

      // Mis horarios: mañana de 5:30 a 7:30 PM (arranca a la media hora) y pasado mañana de 8 a 10 AM.
      await p.getByRole("link", { name: "Mis horarios" }).click();
      await expect(p).toHaveURL(/seccion=horarios/);
      await expect(p.getByRole("heading", { name: "Mis horarios" })).toBeVisible();
      await revisarPantalla(p, "profe", "perfil-horarios");
      /** Abre una franja por el formulario; devuelve el error que muestre, o null si se guardó. */
      const agregarFranja = async (dia: { nombre: string }, desde: string, hasta: string): Promise<string | null> => {
        await p.getByRole("button", { name: `Añadir franja el ${dia.nombre}` }).click();
        const dialogo = p.getByRole("dialog", { name: /Nueva franja/ });
        await dialogo.locator("select").first().selectOption(desde);
        await dialogo.locator("select").last().selectOption(hasta);
        await dialogo.getByRole("button", { name: "Añadir franja" }).click();
        const alerta = dialogo.getByRole("alert");
        await expect(async () => {
          expect((await dialogo.count()) === 0 || (await alerta.count()) > 0).toBe(true);
        }).toPass({ timeout: 10_000 });
        if ((await dialogo.count()) === 0) return null;
        const mensaje = (await alerta.innerText()).trim();
        await p.screenshot({ path: test.info().outputPath(`franja-${desde.replace(":", "")}-rechazada.png`) });
        await dialogo.getByRole("button", { name: "Cancelar" }).click();
        await expect(dialogo).toBeHidden();
        return mensaje;
      };

      const errorMediaHora = await agregarFranja(manana, "17:30", "19:30");
      if (errorMediaHora) {
        // BUG: el formulario ofrece empezar y terminar a la media hora (MisHorarios.tsx, HORAS), pero
        // AvailabilityRuleService.requireWholeHour exige :00 y responde 400 con el nombre del campo:
        // «startTime debe estar alineado a la hora en punto (:00)». Se sigue con 5:00 a 7:00 PM, que
        // también ofrece el cupo de las 5:30: los cupos sí avanzan cada media hora.
        expect.soft(errorMediaHora, "BUG: el formulario ofrece la franja de 5:30 PM y el servidor la rechaza").toBeNull();
        expect(await agregarFranja(manana, "17:00", "19:00"), "la franja en punto se guarda").toBeNull();
      } else {
        await expect.soft(p.getByText(/5:30/).first(), "la franja de la media hora se ve en la semana").toBeVisible();
      }
      expect(await agregarFranja(pasado, "08:00", "10:00"), "la franja de pasado mañana se guarda").toBeNull();
      await revisarPantalla(p, "profe", "perfil-horarios-con-franjas");

      // Bloquea pasado mañana entero: ese día tiene franja, así que el bloqueo tiene que quitarle los cupos.
      await p.getByRole("button", { name: "Bloquear una fecha" }).click();
      const bloquear = p.getByRole("dialog", { name: "Bloquear una fecha" });
      await bloquear.locator("#fecha").fill(pasado.fecha);
      await bloquear.locator("#motivo-bloqueo").fill("Cita médica");
      await bloquear.getByRole("button", { name: "Bloquear", exact: true }).click();
      await expect(bloquear).toBeHidden();
      await revisarPantalla(p, "profe", "perfil-horarios-bloqueo");

      const cupos = (await api(p, "GET", `/api/v1/professors/${profeId}/slots`)).json as { slots: { startsAt: string }[] };
      const horas = cupos.slots.map((c) => ({
        fecha: new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota" }).format(new Date(c.startsAt)),
        hora: new Intl.DateTimeFormat("en-GB", { timeZone: "America/Bogota", hour: "2-digit", minute: "2-digit" }).format(new Date(c.startsAt)),
      }));
      console.log(`Cupos que ofrece: ${horas.map((h) => `${h.fecha} ${h.hora}`).join(", ")}`);
      expect.soft(horas.filter((h) => h.fecha === pasado.fecha), "la fecha bloqueada no ofrece cupos").toEqual([]);
      expect.soft(horas.some((h) => h.fecha === manana.fecha && h.hora === "17:30"), "la franja de la media hora ofrece el cupo de las 5:30").toBe(true);

      const despues = (await api(p, "GET", "/api/v1/me/profile/pending")).json as { missing: string[] };
      expect.soft(despues.missing, "con tarifa, horarios y publicado, no le falta nada").toEqual([]);

      // Sin cuenta, en el catálogo y en su perfil público: nombre y descripción enteros.
      const visitante = await abrir(browser, "visitante");
      abiertas.set("visitante", visitante.page);
      try {
        const v = visitante.page;
        await v.goto("/profesores");
        await expect(v.getByRole("heading", { name: "Profesores" })).toBeVisible();
        // Su tarjeta es la que lleva a su perfil (el nombre se repite si quedaron profes de otra corrida).
        const enlace = v.locator(`a[href="/profesores/${profeId}"]`);
        await expect(enlace.first(), "el profe nuevo aparece en el catálogo sin cuenta").toBeVisible({ timeout: 20_000 });
        const tarjeta = v.locator("div").filter({ has: enlace }).filter({ hasText: profe.nombre }).last();
        await comprobarEntero(tarjeta.getByText(profe.nombre, { exact: true }), profe.nombre, "tarjeta del catálogo · nombre");
        await comprobarEntero(tarjeta.getByText(profe.titular, { exact: true }), profe.titular, "tarjeta del catálogo · descripción");
        await revisarPantalla(v, "visitante", "profesores");
        await enlace.first().click();
        await v.waitForURL(new RegExp(`/profesores/${profeId}$`));
        await comprobarEntero(v.getByRole("heading", { level: 1, name: profe.nombre }), profe.nombre, "perfil público · nombre");
        await comprobarEntero(v.getByText(profe.titular, { exact: true }).first(), profe.titular, "perfil público · descripción");
        await expect.soft(v.getByText(cop(profe.tarifa)).first()).toBeVisible();
        await expect.soft(v.getByText("Reserva tu clase")).toBeVisible();
        await revisarPantalla(v, "visitante", "perfil-publico");
      } finally {
        await visitante.ctx.close();
        abiertas.delete("visitante");
      }
    });

    // ---------------------------------------------------------------- 6
    await paso("6. Una estudiante reserva con él la clase de la media hora; él la ve en su agenda, en la campana y en su correo", async () => {
      estudiante.email = (await estudianteNueva(e, estudiante.nombre)).email;
      estudianteId = ((await api(e, "GET", "/api/v1/auth/me")).json as { id: string }).id;
      // Saldo a favor desde el admin, como una compensación: así la reserva se confirma sin pasarela.
      const saldo = await api(admin, "POST", "/api/v1/admin/credits", { studentId: estudianteId, amountCop: 100_000, reason: "ADMIN_ADJUSTMENT" });
      expect(saldo.status, "el admin le da saldo a favor").toBeLessThan(300);

      await e.goto(`/profesores/${profeId}`);
      await expect(e.getByText("Cupos disponibles")).toBeVisible({ timeout: 20_000 });
      await comprobarEntero(e.getByRole("heading", { level: 1, name: profe.nombre }), profe.nombre, "perfil con sesión · nombre");
      // Mañana es el único día con cupos (pasado mañana quedó bloqueado), así que es el que viene elegido.
      const cupo = e.locator("main [aria-label='Horarios del día'] button", { hasText: /^5:30/ });
      await expect(cupo.first(), "el cupo de las 5:30 se ofrece").toBeVisible();
      await revisarPantalla(e, "estudiante", "perfil-profe-reservar");
      await cupo.first().click();
      await expect.soft(e.getByText("Tu saldo a favor")).toBeVisible();
      await e.getByRole("button", { name: "Confirmar reserva" }).click();
      await e.waitForURL(/\/mis-clases/, { timeout: 20_000 });
      await expect(e.getByText("¡Clase reservada!")).toBeVisible();
      await revisarPantalla(e, "estudiante", "mis-clases-reservada");

      const mias = (await api(e, "GET", "/api/v1/me/bookings?scope=upcoming")).json as { id: string; status: string; counterpart?: { id: string } }[];
      const reserva = mias.find((b) => b.counterpart?.id === profeId);
      expect(reserva, "la reserva quedó en sus próximas clases").toBeTruthy();
      expect(reserva!.status).toBe("CONFIRMED");
      bookingId = reserva!.id;

      // El profe: en su agenda, en la campana y en su correo.
      await p.goto("/mis-clases");
      await expect(p.getByText(estudiante.nombre).first()).toBeVisible({ timeout: 20_000 });
      await expect.soft(p.getByText(/5:30 – 6:25 PM/).first()).toBeVisible();
      await revisarPantalla(p, "profe", "mis-clases-con-reserva");
      const campana = p.getByRole("button", { name: /^Notificaciones/ }).first();
      await expect.soft(campana, "la campana dice que hay algo sin leer").toHaveAccessibleName(/sin leer/);
      await campana.click();
      const avisos = p.getByRole("dialog", { name: "Notificaciones" });
      await expect.soft(avisos.getByText(`Nueva clase con ${estudiante.nombre}`), "la campana avisa la reserva").toBeVisible();
      await revisarPantalla(p, "profe", "campana");
      await p.keyboard.press("Escape");

      const correo = await ultimoCorreo(p, profe.email, /Sala de la clase/);
      expect.soft(correo, "le llega el correo de la reserva con la sala").not.toBeNull();
      const sala = (correo ?? "").match(/Sala de la clase: ([^\s"\\<]+)/)?.[1] ?? "";
      console.log(`Enlace a la sala en el correo del profe: ${sala}`);
      expect.soft(sala, "el enlace a la sala del correo es absoluto").toMatch(/^https?:\/\/.+\/mis-clases\/[0-9a-f-]+\/aula$/);
      // Y el de la estudiante, igual.
      const suyo = await ultimoCorreo(e, estudiante.email, /Sala de la clase/);
      const salaDeElla = (suyo ?? "").match(/Sala de la clase: ([^\s"\\<]+)/)?.[1] ?? "";
      expect.soft(salaDeElla, "el enlace a la sala del correo de la estudiante es absoluto").toMatch(/^https?:\/\//);
    });

    // ---------------------------------------------------------------- 7
    await paso("7. La clase termina (SQL): asistencia, el acta publicada y la estudiante la lee", async () => {
      // La clase se mueve al pasado: empezó hace 65 minutos y terminó hace 10 (dura lo mismo).
      sqlLocal(
        `update bookings set starts_at = now() - interval '65 minutes', ends_at = now() - interval '65 minutes' + (ends_at - starts_at) where id = '${bookingId}'`,
      );
      await p.goto("/mis-clases?scope=past");
      await expect(p.getByText(estudiante.nombre).first()).toBeVisible({ timeout: 20_000 });
      await revisarPantalla(p, "profe", "mis-clases-pasadas");
      await p.getByRole("button", { name: "Registrar asistencia" }).first().click();
      const asistencia = p.getByRole("dialog", { name: /asistió a la clase/ });
      await asistencia.getByRole("button", { name: "Asistió", exact: true }).click();
      await asistencia.getByRole("button", { name: "Guardar" }).click();
      await expect(asistencia).toBeHidden();

      const acta = p.getByRole("link", { name: /Contar cómo estuvo|Terminar el acta/ }).first();
      await expect(acta).toBeVisible({ timeout: 15_000 });
      await expect.soft(p.getByRole("region", { name: "Actas por escribir" })).toBeVisible();
      await acta.click();
      await p.waitForURL(new RegExp(`/mis-clases/${bookingId}/acta$`));
      await revisarPantalla(p, "profe", "acta-nueva");
      await p.locator("#notas").fill("Trabajamos presentaciones en reuniones; dice 'I am agree' y le costó 'follow up'. Aprendió 'deadline' (fecha límite).");
      await p.getByRole("button", { name: "Generar acta" }).click();
      const publicar = p.getByRole("button", { name: "Publicar", exact: true });
      await expect(publicar).toBeVisible({ timeout: 30_000 });
      await revisarPantalla(p, "profe", "acta-borrador");
      await publicar.click();
      await expect(p.getByText(/^Publicada · /)).toBeVisible();
      await revisarPantalla(p, "profe", "acta-publicada");

      await e.goto("/mis-clases?scope=past");
      const resumenes = e.getByRole("region", { name: "Resúmenes de tus clases" });
      await resumenes.getByRole("link", { name: new RegExp(profe.nombre.split(" ")[0]) }).first().click();
      await expect(e.getByRole("heading", { name: "Resumen de tu clase" })).toBeVisible();
      await expect.soft(e.getByText(/presentaciones en reuniones/).first()).toBeVisible();
      await revisarPantalla(e, "estudiante", "acta-de-la-estudiante");
    });

    // ---------------------------------------------------------------- 8
    await paso("8. Ganancias: la comisión de Ajustes y «Clases por liquidar» dice por qué espera", async () => {
      await p.goto("/ganancias");
      await expect(p.getByRole("heading", { name: "Mis ganancias" })).toBeVisible();
      const linea = p.locator("li", { hasText: estudiante.nombre });
      await expect(linea.first(), "la clase aparece en «Clase por clase»").toBeVisible({ timeout: 15_000 });
      await expect.soft(linea.first()).toContainText(cop(profe.tarifa));
      await expect.soft(linea.first(), `comisión del ${figuras.commissionPercent} % (no es fundador)`).toContainText(`− ${cop(comision)}`);
      await expect.soft(linea.first()).toContainText(cop(paraElProfe));
      const porLiquidar = p.locator("li", { hasText: /Clase del .* · Lucía F\./ });
      await expect.soft(porLiquidar.first(), "está en «Clases por liquidar»").toBeVisible();
      await expect.soft(porLiquidar.first(), "dice por qué espera").toContainText(/En plazo de reclamo hasta el|Entra en el corte del|Tiene un reclamo abierto/);
      await expect.soft(porLiquidar.first()).toContainText(cop(paraElProfe));
      // Ya registró su llave: no hay aviso de datos de pago.
      await expect.soft(p.getByText("Para pagarte necesitamos tu llave Bre-B.")).toHaveCount(0);
      await revisarPantalla(p, "profe", "ganancias");
    });

    // ---------------------------------------------------------------- 9
    await paso("9. Todas las pantallas del profe, en el celular y a 1280 px, sin desbordes ni errores", async () => {
      const rutas = [
        ["/mis-clases", "agenda"],
        ["/mis-clases?scope=past", "agenda-pasadas"],
        [`/mis-clases/${bookingId}/acta`, "acta"],
        ["/perfil", "perfil"],
        ["/perfil?seccion=horarios", "horarios"],
        ["/perfil?seccion=pagos", "pagos"],
        ["/ganancias", "ganancias"],
        ["/desempeno", "desempeno"],
        ["/invitar", "invitar"],
        ["/mensajes", "mensajes"],
        ["/mensajes/rigel", "rigel"],
        ["/ayuda", "ayuda"],
        [`/estudiantes/${estudianteId}`, "ficha-estudiante"],
        [`/profesores/${profeId}`, "perfil-publico"],
      ] as const;
      // En el celular, las que el camino no pisó.
      for (const [ruta, nombre] of rutas.filter(([, n]) => ["desempeno", "invitar", "mensajes", "rigel", "ayuda", "ficha-estudiante"].includes(n))) {
        await p.goto(ruta);
        await p.locator("main").first().waitFor({ timeout: 20_000 });
        await revisarPantalla(p, "profe", nombre);
      }
      const esc = await abrirEscritorio(browser, "profe@1280", profeCtx);
      abiertas.set("profe-1280", esc.page);
      try {
        for (const [ruta, nombre] of rutas) {
          await esc.page.goto(ruta);
          await esc.page.locator("main").first().waitFor({ timeout: 20_000 });
          await revisarPantalla(esc.page, "profe@1280", nombre);
        }
      } finally {
        await esc.ctx.close();
        abiertas.delete("profe-1280");
      }
    });
  } catch (error) {
    await capturarAbiertas();
    throw error;
  } finally {
    abiertas.clear();
    // Se oculta al terminar: un profe recién creado sale PRIMERO en el catálogo (arranque en frío del
    // ranking), y las demás pruebas —y quienes trabajan en esta base— esperan ver a María ahí.
    if (profeId) sqlLocal(`update professor_profiles set is_published = false where user_id = '${profeId}'`);
    await estCtx.close().catch(() => {});
    await profeCtx.close().catch(() => {});
    await adminCtx.close().catch(() => {});
    cerrarVigilancia();
  }
});

// ------------------------------------------------------------------ por invitación de fundador

test("un profe fundador: la invitación del admin, el registro con su correo fijo y el 15 % al aprobarlo", async ({ browser }, info) => {
  test.setTimeout(8 * 60_000);
  hallazgos.length = 0;
  const base = String(info.project.use.baseURL ?? "http://localhost:3000");
  const invitado = {
    nombre: "Valentina Ríos Castellanos",
    email: `flujo.fundador.${Date.now()}@orion.local`,
    pass: "orion123*",
  };
  const cop = (n: number) => `$${new Intl.NumberFormat("es-CO").format(n)}`;

  const { ctx: adminCtx, page: admin } = await abrir(browser, "admin");
  const { ctx: invCtx, page: inv } = await abrir(browser, "fundador");
  // Las comisiones vigentes salen de Ajustes (platform_settings), por su endpoint público.
  const figuras = (await (await admin.request.get("/api/v1/catalog/figures")).json()) as {
    commissionPercent: number;
    founderCommissionPercent: number;
  };
  abiertas.set("admin", admin);
  abiertas.set("fundador", inv);
  let enlace = "";

  try {
    await paso("F1. El admin invita desde Usuarios y le llega el correo con el enlace", async () => {
      await entrar(admin, SEMILLA.admin);
      await admin.goto("/admin/usuarios");
      await expect(admin.getByRole("heading", { name: "Usuarios", exact: true }).first()).toBeVisible();
      await revisarPantalla(admin, "admin", "admin-usuarios");
      await admin.getByRole("button", { name: /Invitar profesor/ }).click();
      const dialogo = admin.getByRole("dialog");
      await dialogo.locator("#invite-email").fill(invitado.email);
      await dialogo.locator("#invite-nombre").fill("Valentina");
      await revisarPantalla(admin, "admin", "admin-invitar");
      await dialogo.getByRole("button", { name: /Enviar invitación/ }).click();
      await expect(admin.getByText(/Le enviamos la invitación/)).toBeVisible();

      const correo = await ultimoCorreo(admin, invitado.email, /invitacion\//);
      expect(correo, "llega el correo de la invitación").not.toBeNull();
      const absoluto = correo!.match(/https?:\/\/[^"\\\s]*\/invitacion\/[A-Za-z0-9_-]+/)?.[0] ?? "";
      expect.soft(absoluto, "el enlace de la invitación es absoluto").toMatch(/^https?:\/\//);
      enlace = absoluto.replace(/^https?:\/\/[^/]+/, "") || (correo!.match(/\/invitacion\/[A-Za-z0-9_-]+/)?.[0] ?? "");
      expect(enlace).toMatch(/^\/invitacion\//);
    });

    await paso("F2. La invitada abre el enlace, se registra con el correo fijo y cae en su postulación", async () => {
      await inv.goto(enlace);
      await expect(inv.getByRole("heading", { name: "Valentina, queremos que seas de los primeros profes de Orión." })).toBeVisible();
      await expect.soft(inv.getByText(new RegExp(`Por ser de los profes fundadores, tienes ${figuras.founderCommissionPercent} %`))).toBeVisible();
      await revisarPantalla(inv, "fundador", "invitacion");
      await inv.getByRole("link", { name: "Aceptar la invitación" }).click();
      await expect(inv).toHaveURL(/\/registro\?invitacion=/);
      await inv.waitForLoadState("networkidle");
      await expect(inv.locator("#email")).toHaveValue(invitado.email);
      await expect(inv.locator("#email")).toHaveAttribute("readonly", "");
      await expect(inv.getByRole("button", { name: /Quiero aprender/ })).toBeHidden();
      await revisarPantalla(inv, "fundador", "registro-invitacion");
      await inv.locator("#nombre").fill(invitado.nombre);
      await inv.locator("#password").fill(invitado.pass);
      await aceptarCondiciones(inv);
      await inv.getByRole("button", { name: "Crear cuenta y postularme" }).click();
      await inv.waitForURL(/\/aplicacion$/, { timeout: 20_000 });
      await expect(inv.getByRole("heading", { name: "Datos personales" })).toBeVisible({ timeout: 20_000 });
      await revisarPantalla(inv, "fundador", "aplicacion");
      const yo = (await api(inv, "GET", "/api/v1/auth/me")).json as { role: string; emailVerified: boolean };
      expect.soft(yo.role, "la cuenta de la invitación nace como aspirante").toBe("TEACHER_APPLICANT");
      console.log(`Correo de la invitada verificado al registrarse: ${yo.emailVerified}`);
    });

    await paso("F3. Completa y envía la postulación (API; foto y hoja de vida por SQL) y el admin la aprueba", async () => {
      const aplicacion = (await api(inv, "GET", "/api/v1/me/teacher-application")).json as { id: string; status: string };
      expect(aplicacion.status).toBe("DRAFT");
      fotoYHojaDeVidaPorSql(invitado.email, aplicacion.id, `${base}/icon-192.png`);
      const guardado = await api(inv, "PUT", "/api/v1/me/teacher-application", {
        headline: "Inglés para viajar y conversar sin miedo desde la primera clase",
        bio:
          "Enseño inglés hace ocho años a adultos que quieren viajar y trabajar con otras culturas. Mis clases son " +
          "prácticas y cercanas, con situaciones reales y mucha conversación desde el primer día.",
        countryCode: "CO",
        city: "Bogotá",
        yearsExperience: 8,
        education: "Licenciatura en Idiomas",
        certified: false,
        acceptsTrial: false,
        languages: [{ code: "EN", isNative: false, levels: ["BEGINNER", "INTERMEDIATE"] }],
        goals: ["CONVERSATION", "TRAVEL"],
        isPublished: false,
      });
      expect(guardado.status, "guarda la postulación").toBe(200);
      expect((await api(inv, "POST", "/api/v1/me/agreements/TEACHER_AGREEMENT/accept")).status).toBeLessThan(300);
      const enviada = await api(inv, "POST", "/api/v1/me/teacher-application/submit");
      expect(enviada.status, `la envía (${JSON.stringify(enviada.json)})`).toBe(200);

      await admin.goto(`/admin/aplicaciones/${aplicacion.id}`);
      await admin.getByRole("button", { name: "Empezar revisión" }).click();
      await admin.getByRole("button", { name: "Aprobar" }).click();
      await expect(admin.getByText("Sin acciones disponibles en este estado.")).toBeVisible();
      expect.soft(await ultimoCorreo(admin, invitado.email, /fue aprobada/), "llega el correo de aprobación").not.toBeNull();
    });

    await paso("F4. Entra como profe y tiene el beneficio de fundador en su tarifa y en Usuarios", async () => {
      await invCtx.clearCookies();
      await entrar(inv, { email: invitado.email, pass: invitado.pass });
      const vistos = await atenderDialogosDeEntrada(inv, { llave: "310 555 1234", documento: "52123456" });
      console.log(`Diálogos al entrar (fundador): ${vistos.join(" → ") || "(ninguno)"}`);
      expect.soft(vistos, "no le vuelve a pedir acuerdos que ya aceptó").not.toContain(ACUERDOS);

      const perfil = (await api(inv, "GET", "/api/v1/me/profile")).json as { founder?: { rateBps?: number } | null };
      expect.soft(perfil.founder?.rateBps, "el perfil trae la comisión de fundador").toBe(figuras.founderCommissionPercent * 100);
      await inv.goto("/perfil");
      await inv.locator("#tarifa").fill("60000");
      const conFundador = 60_000 - Math.round((60_000 * figuras.founderCommissionPercent) / 100);
      const conLaBase = 60_000 - Math.round((60_000 * figuras.commissionPercent) / 100);
      // getByText con texto (no expresión) busca la subcadena: la frase sigue con «durante N meses…».
      await expect.soft(
        inv.getByText(`Recibes ${cop(conFundador)} por clase: ${figuras.founderCommissionPercent} % de comisión como profe fundador`),
      ).toBeVisible();
      await expect.soft(inv.getByText(`Después recibirás ${cop(conLaBase)} (comisión de Orión: ${figuras.commissionPercent} %)`)).toBeVisible();
      await revisarPantalla(inv, "fundador", "perfil-fundador");
      await inv.getByRole("region", { name: "Cambios sin guardar" }).getByRole("button", { name: "Descartar" }).click();

      await admin.goto("/admin/usuarios");
      await admin.getByRole("searchbox", { name: "Buscar usuarios por nombre o correo" }).fill(invitado.email);
      const fila = admin.locator("tr", { hasText: invitado.email });
      await expect.soft(fila.getByText(new RegExp(`^Fundador · ${figuras.founderCommissionPercent} %`))).toBeVisible();
      await revisarPantalla(admin, "admin", "admin-usuarios-fundador");
    });
  } catch (error) {
    await capturarAbiertas();
    throw error;
  } finally {
    abiertas.clear();
    await invCtx.close().catch(() => {});
    await adminCtx.close().catch(() => {});
    cerrarVigilancia();
  }
});
