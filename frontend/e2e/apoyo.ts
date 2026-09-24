import { expect, type Page } from "@playwright/test";

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
