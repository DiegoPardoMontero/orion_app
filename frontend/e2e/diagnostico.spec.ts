import { expect, test, type Page } from "@playwright/test";
import { aceptarCondiciones } from "./apoyo";

/**
 * El diagnóstico de confianza, de la portada al resultado.
 *
 * <p><strong>Lo que este test NO puede probar, y por qué.</strong> La conversación en sí es una
 * conexión WebRTC contra un tercero. En la suite corre `ScriptedVoiceProvider` —el proveedor falso,
 * que es el valor por defecto precisamente para que ningún test toque la red ni el saldo de nadie—
 * así que la credencial que llega al navegador no abre ninguna sesión real. Los turnos se empujan
 * por la API, que es exactamente lo que hace el cliente de voz cuando la conversación sí ocurre.
 *
 * <p>O sea: aquí se prueba todo el recorrido salvo los dos minutos de audio, que ningún test de
 * navegador puede sostener. Lo que se prueba es lo que se rompe — las puertas, el cálculo, el
 * resultado y el salto a reservar.
 */

const CLAVE = "orion123*";

/**
 * El header CSRF, que `page.request` no pone solo.
 *
 * <p>Toda petición que muta lo exige —cookie `XSRF-TOKEN` legible por JS más header
 * `X-XSRF-TOKEN`—, y la aplicación lo añade en su capa de fetch. Un test que llama a la API a mano
 * tiene que hacer lo mismo, y que falte es justo lo que este proyecto quiere que falle.
 */
async function cabeceras(page: Page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies();
  const xsrf = cookies.find((c) => c.name === "XSRF-TOKEN")?.value;
  return xsrf ? { "X-XSRF-TOKEN": xsrf } : {};
}

/** Empuja un turno como lo haría el cliente de voz: solo lo que el navegador puede medir. */
async function turno(page: Page, id: string, i: number, texto: string, latencia: number) {
  const r = await page.request.post(`/api/v1/assessments/${id}/turns`, {
    headers: await cabeceras(page),
    data: {
      turnIndex: i,
      speaker: "USER",
      transcript: texto,
      latencyMs: latencia,
      durationMs: 4200,
    },
  });
  expect(r.status()).toBe(204);
}

test.describe("Diagnóstico de confianza", () => {
  test("sin cuenta, de la portada al resultado, y al crear la cuenta se muda a ella", async ({
    page,
  }) => {
    // 1 · La portada lleva al diagnóstico, y la puerta lleva a hablar: sin cuenta y sin pasos.
    await page.goto("/");
    await page.getByRole("link", { name: "Hacer mi diagnóstico gratis" }).first().click();
    await expect(page).toHaveURL(/\/diagnostico$/);
    await page.getByRole("link", { name: "Empezar con Meissa" }).click();
    await expect(page).toHaveURL(/\/diagnostico\/empezar/);
    await page.waitForLoadState("networkidle");

    // 2 · Nombre y dos casillas, separadas y desmarcadas. Sin las tres cosas no se avanza.
    const empezar = page.getByRole("button", { name: "Empezar la conversación" });
    await expect(empezar).toBeDisabled();
    await page.getByPlaceholder("Tu nombre").fill("Eduardo");
    await page.getByRole("checkbox", { name: "Soy mayor de 18 años" }).check();
    await expect(empezar).toBeDisabled();
    await page.getByRole("checkbox", { name: /Autorizo el uso de mi voz/ }).check();
    await expect(empezar).toBeEnabled();

    // 3 · El lead y la conversación, por API: pulsar arrancaría también la voz, y con el proveedor
    //     falso esa conexión no lleva a ninguna parte. La cookie del lead queda en el navegador.
    const lead = await page.request.post("/api/v1/assessment-leads", {
      headers: await cabeceras(page),
      data: { firstName: "Eduardo", adult: true, acceptsVoice: true },
    });
    expect(lead.status()).toBe(201);

    const creada = await page.request.post("/api/v1/assessments", {
      headers: await cabeceras(page),
      data: { languageCode: "EN", goals: [] },
    });
    expect(creada.status()).toBe(201);
    const { assessmentId } = (await creada.json()) as { assessmentId: string };

    // 4 · Cuatro turnos: el mínimo para que haya número.
    await turno(page, assessmentId, 0, "Okay hi, my name is Eduardo and I work in logistics", 400);
    await turno(page, assessmentId, 1, "I coordinate shipments and talk to suppliers every day", 520);
    await turno(page, assessmentId, 2, "I want to use English at work, mostly in meetings", 610);
    await turno(page, assessmentId, 3, "If I could speak better I would lead those calls myself", 480);

    const cerrada = await page.request.post(`/api/v1/assessments/${assessmentId}/complete`, {
      headers: await cabeceras(page),
      data: { goals: [] },
    });
    expect(cerrada.status()).toBe(200);
    const resultado = (await cerrada.json()) as { status: string; score: number | null };
    expect(resultado.status).toBe("COMPLETED");
    expect(resultado.score).not.toBeNull();

    // 5 · Volver: ya no pregunta el nombre, y como hay enfriamiento, enseña el resultado que ya
    //     tiene, con la oferta de guardarlo.
    await page.goto("/diagnostico/empezar");
    await expect(page.getByRole("heading", { name: "Hola de nuevo, Eduardo." })).toBeVisible();
    await page.getByRole("button", { name: "Empezar la conversación" }).click();
    await expect(page.getByText("Tu punto de partida")).toBeVisible();
    await expect(page.getByText(String(resultado.score)).first()).toBeVisible();
    await expect(page.getByText("¿Te lo guardamos?")).toBeVisible();

    // 6 · Crear la cuenta desde ahí: el diagnóstico pasa a ella y se ve en el perfil.
    await page.getByRole("link", { name: "Crear cuenta" }).click();
    await expect(page).toHaveURL(/\/registro\?desde=diagnostico/);
    await page.waitForLoadState("networkidle");
    await page.locator("#nombre").fill("Eduardo Prueba");
    await page.locator("#email").fill(`diagnostico.${Date.now()}@orion.local`);
    await page.locator("#password").fill(CLAVE);
    await aceptarCondiciones(page);
    await page.getByRole("button", { name: "Crear cuenta" }).click();
    await expect(page).toHaveURL(/\/cuenta\?seccion=resumen/);
    await page.waitForLoadState("networkidle");
    await expect(page.getByText("Confidence Score")).toBeVisible();
    await expect(page.getByText(String(resultado.score)).first()).toBeVisible();
  });

  test("quien se queda corto no recibe puntaje, pero sí etiqueta y resumen", async ({ page }) => {
    await page.goto("/diagnostico/empezar");
    await page.waitForLoadState("networkidle");

    const lead = await page.request.post("/api/v1/assessment-leads", {
      headers: await cabeceras(page),
      data: { firstName: "Eduardo", adult: true, acceptsVoice: true },
    });
    expect(lead.status()).toBe(201);
    const creada = await page.request.post("/api/v1/assessments", {
      headers: await cabeceras(page),
      data: { languageCode: "EN", goals: [] },
    });
    expect(creada.status()).toBe(201);
    const { assessmentId } = (await creada.json()) as { assessmentId: string };

    await turno(page, assessmentId, 0, "Hello, I am Eduardo", 500);
    await turno(page, assessmentId, 1, "I work in logistics", 600);

    const cerrada = await page.request.post(`/api/v1/assessments/${assessmentId}/complete`, {
      headers: await cabeceras(page),
      data: { goals: [] },
    });

    const resultado = (await cerrada.json()) as {
      status: string;
      score: number | null;
      label: string;
      summary: string | null;
    };
    // Un puntaje sacado de dos frases parece un dato y no lo es: sin número. Pero no con las manos
    // vacías: etiqueta de quien empieza y un resumen con su nombre.
    expect(resultado.status).toBe("ABANDONED");
    expect(resultado.score).toBeNull();
    expect(resultado.label).toBe("Primeros pasos");
    expect(resultado.summary).toContain("Eduardo");
  });
});
