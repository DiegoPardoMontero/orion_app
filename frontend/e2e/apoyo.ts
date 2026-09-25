import { expect, type Browser, type Locator, type Page } from "@playwright/test";

/**
 * Las tres casillas obligatorias del registro (Bloque 9).
 *
 * <p>Van por separado y las tres son obligatorias: empaquetar la autorización de tratamiento de
 * datos con la aceptación de los términos la viciaría, porque el Decreto 1377 de 2013 la exige
 * previa, expresa e informada — y por tanto específica.
 */
export async function aceptarCondiciones(page: Page) {
  await page.locator("#mayor-de-edad").check();
  await page.locator("#acepta-terminos").check();
  await page.locator("#acepta-datos").check();
}

/** Dónde vive Mailpit en local: es el SMTP falso de `docker compose`. */
const MAILPIT = "http://localhost:8025";

type MailpitMensaje = { ID: string; To: { Address: string }[] };

/**
 * Confirma el correo de una cuenta recién creada abriendo el enlace que llegó a Mailpit.
 *
 * <p>Sin esto, una cuenta nueva no puede reservar: desde el Bloque 9 el correo hay que verificarlo
 * y `BookingService` lo comprueba. Se podría haber marcado la cuenta como verificada por SQL, pero
 * entonces el test no probaría el camino por el que pasa una persona de verdad — y ese camino, que
 * cruza el correo, es justo el que más fácil se rompe sin que nadie se entere.
 */
export async function verificarCorreo(page: Page, email: string) {
  const enlace = await esperarEnlaceDeVerificacion(page, email);
  await page.goto(enlace);
  await expect(page.getByText("tu correo quedó confirmado", { exact: false })).toBeVisible();
}

async function esperarEnlaceDeVerificacion(page: Page, email: string): Promise<string> {
  // El envío es síncrono tras el commit, pero Mailpit tarda unos milisegundos en indexarlo.
  for (let intento = 0; intento < 20; intento++) {
    const lista = await page.request.get(
      `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`,
    );
    if (lista.ok()) {
      const cuerpo = (await lista.json()) as { messages?: MailpitMensaje[] };
      const mensaje = cuerpo.messages?.[0];
      if (mensaje) {
        const detalle = await page.request.get(`${MAILPIT}/api/v1/message/${mensaje.ID}`);
        const texto = JSON.stringify(await detalle.json());
        const encontrado = texto.match(/https?:\/\/[^"\\\s]*\/verificar\?token=[A-Za-z0-9_-]+/);
        if (encontrado) {
          return encontrado[0];
        }
      }
    }
    await page.waitForTimeout(500);
  }
  throw new Error(`No llegó el correo de verificación para ${email} en 10 s`);
}

/**
 * Las cuentas nuevas ven el recorrido guiado al entrar (las de la semilla ya lo vieron). Las pruebas
 * que no son sobre él lo saltan: mientras está abierto, lo de atrás no recibe clics.
 */
export async function saltarRecorrido(page: Page) {
  // En el inicio el diseño dice «Ahora no»; a mitad de camino, «Saltar».
  const saltar = page.getByRole("dialog").getByRole("button", { name: /^(Ahora no|Saltar)$/ });
  await saltar.click({ timeout: 15_000 });
  await expect(saltar).toHaveCount(0);
}

/** Las cuentas de la semilla local (DevDataSeeder). */
export const SEMILLA = {
  ana: { email: "ana@orion.local", pass: "orion123*" },
  maria: { email: "maria@orion.local", pass: "orion123*" },
  juan: { email: "juan@orion.local", pass: "orion123*" },
  admin: { email: "admin@orion.local", pass: "admin123*" },
};

/** Entra y espera a que la sesión quede puesta (si no, la siguiente navegación rebota al login). */
export async function entrar(page: Page, user: { email: string; pass: string }) {
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.locator("#email").fill(user.email);
  await page.locator("#password").fill(user.pass);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20_000 });
}

/**
 * Una estudiante nueva, registrada por la pantalla, con el correo verificado y el recorrido saltado.
 * Las pruebas que cambian datos usan una así, para no tocar lo que las demás dan por sentado.
 */
export async function estudianteNueva(page: Page, nombre = "Estudiante de Prueba"): Promise<{ email: string; pass: string }> {
  const email = `wf.${Date.now()}.${Math.floor(Math.random() * 1000)}@orion.local`;
  await page.goto("/registro");
  await page.waitForLoadState("networkidle");
  await page.locator("#nombre").fill(nombre);
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("orion123*");
  await aceptarCondiciones(page);
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await page.waitForURL(/\/profesores/, { timeout: 20_000 });
  await saltarRecorrido(page);
  await verificarCorreo(page, email);
  return { email, pass: "orion123*" };
}

/** El texto del último correo que llegó a esa dirección en Mailpit (asunto + cuerpo), o null. */
export async function ultimoCorreo(page: Page, email: string, contiene?: RegExp): Promise<string | null> {
  for (let intento = 0; intento < 20; intento++) {
    const lista = await page.request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
    if (lista.ok()) {
      const cuerpo = (await lista.json()) as { messages?: MailpitMensaje[] };
      for (const mensaje of cuerpo.messages ?? []) {
        const detalle = await page.request.get(`${MAILPIT}/api/v1/message/${mensaje.ID}`);
        const texto = JSON.stringify(await detalle.json());
        if (!contiene || contiene.test(texto)) return texto;
      }
    }
    await page.waitForTimeout(500);
  }
  return null;
}

/** Una llamada al API con la sesión y el token CSRF de la página, como la haría la app. */
export async function api(page: Page, method: string, path: string, body?: unknown): Promise<{ status: number; json: unknown }> {
  return page.evaluate(
    async ({ method, path, body }) => {
      const x = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="))?.split("=")[1];
      const r = await fetch(path, {
        method,
        headers: { "Content-Type": "application/json", ...(x ? { "X-XSRF-TOKEN": decodeURIComponent(x) } : {}) },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      return { status: r.status, json: await r.json().catch(() => null) };
    },
    { method, path, body },
  );
}

/** Que la página no se desplace de lado: nada más ancho que la pantalla. */
export async function sinDesbordeLateral(page: Page) {
  const { ancho, pantalla } = await page.evaluate(() => ({
    ancho: document.documentElement.scrollWidth,
    pantalla: window.innerWidth,
  }));
  expect(ancho, "la página se desplaza de lado").toBeLessThanOrEqual(pantalla + 1);
}

/**
 * El aula con JaaS simulado: en local no hay app-id de 8x8. La respuesta del aula dice que está
 * abierta y el «external_api.js» falso dibuja un iframe con un contador; si el contador sigue al
 * minimizar y volver, el iframe no se recargó. `window.__jitsi` deja disparar eventos del falso.
 */
export async function simularAula(page: Page, bookingId: string, extra: Record<string, unknown> = {}) {
  const ahora = Date.now();
  const aula = {
    state: "STARTED",
    startsAt: new Date(ahora - 12 * 60000).toISOString(),
    endsAt: new Date(ahora + 43 * 60000).toISOString(),
    opensAt: new Date(ahora - 22 * 60000).toISOString(),
    expiresAt: new Date(ahora + 90 * 60000).toISOString(),
    classMinutes: 55,
    moderator: false,
    counterpart: { name: "María Gómez", firstName: "María", photoUrl: null, headline: null },
    counterpartPresent: true,
    displayName: "Ana Ramírez",
    domain: "jitsi.falso.test",
    room: "vpaas-falso/sala",
    token: "t",
    ...extra,
  };
  const jitsi = `window.JitsiMeetExternalAPI = class {
    constructor(d, o) {
      this.l = {}; this.opciones = o; window.__jitsi = this; window.__montajes = (window.__montajes || 0) + 1;
      this.f = document.createElement("iframe");
      this.f.style.cssText = "border:0;width:100%;height:100%;display:block";
      this.f.srcdoc = '<body style="margin:0;background:#2e1e4e;color:#fff;font:16px sans-serif"><div id=t>0</div><script>let n=0;setInterval(()=>{document.getElementById("t").textContent=String(++n)},250)<\\/script></body>';
      o.parentNode.appendChild(this.f);
    }
    addListener(e, fn) { (this.l[e] ||= []).push(fn); }
    emitir(e, datos) { (this.l[e] || []).forEach((fn) => fn(datos)); }
    executeCommand(c) {
      window.__comandos = [...(window.__comandos || []), c];
      if (c === "hangup") setTimeout(() => this.emitir("readyToClose"), 30);
      if (c === "toggleAudio") setTimeout(() => this.emitir("audioMuteStatusChanged", { muted: !(this.mudo = !this.mudo) ? false : true }), 10);
    }
    dispose() { this.f.remove(); }
  };`;
  await page.route(`**/api/v1/bookings/${bookingId}/classroom`, (r) => r.fulfill({ json: aula }));
  await page.route("https://jitsi.falso.test/**", (r) => r.fulfill({ body: jitsi, contentType: "application/javascript" }));
}

/** El contador del iframe de la clase simulada: sube solo mientras el iframe siga vivo. */
export async function contadorDeLaClase(page: Page): Promise<number> {
  return Number((await page.frameLocator("iframe").first().locator("#t").textContent()) ?? "0");
}

/**
 * Horarios de sobra para María: la semilla solo le da el lunes de 6 a 9 PM y el miércoles de 8 a
 * 11 AM, y las pruebas que reservan se comían esos cupos. Lunes a sábado de 12 a 5 PM (el día va
 * como número, 1 = lunes); el domingo queda libre, porque otras pruebas lo usan como el día en que
 * no enseña nadie. Si ya estaban, el solape se rechaza y da igual.
 */
export async function horariosAmplios(browser: Browser) {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await entrar(page, SEMILLA.maria);
  for (const weekday of [1, 2, 3, 4, 5, 6]) {
    await api(page, "POST", "/api/v1/me/availability/rules", { weekday, startTime: "12:00", endTime: "17:00" });
  }
  await ctx.close();
}

/**
 * Cierra las celebraciones de «Logro nuevo» que haya en pantalla. Salen solas cuando algo enciende
 * una estrella —una reserva, la quinta clase— y, mientras están, tapan lo de atrás.
 */
export async function cerrarCelebraciones(page: Page) {
  for (let i = 0; i < 4; i++) {
    const logro = page.locator('[role="dialog"][aria-labelledby="logro-nuevo"]');
    // `isVisible` no espera (ignora su timeout): hay que darle al aviso tiempo de llegar.
    const llego = await logro.waitFor({ state: "visible", timeout: 1500 }).then(() => true, () => false);
    if (!llego) return;
    // Si la página tiene `apartarCelebraciones`, el manejador puede cerrarlo antes que este clic.
    await logro.getByRole("button").last().click({ timeout: 3000 }).catch(() => {});
    await page.waitForTimeout(300);
  }
}

/**
 * Si algo aparece en pantalla dentro del plazo. Para las pruebas que se saltan cuando a la base le
 * faltan datos: `isVisible` contesta al instante, antes de que la página cargue, y las saltaba sin
 * motivo.
 */
export async function aparece(locator: Locator, timeout = 8000): Promise<boolean> {
  return locator.first().waitFor({ state: "visible", timeout }).then(() => true, () => false);
}

/**
 * Lo mismo, pero para siempre: el aviso de logro puede llegar tarde —cuando la prueba ya está en
 * otra pantalla— y Playwright lo aparta en cuanto estorba a un clic.
 */
export async function apartarCelebraciones(page: Page) {
  await page.addLocatorHandler(page.locator('[role="dialog"][aria-labelledby="logro-nuevo"]'), async (logro) => {
    await logro.getByRole("button").last().click();
  });
}
