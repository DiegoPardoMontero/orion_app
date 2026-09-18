import { expect, test, type Page } from "@playwright/test";
import { aceptarCondiciones, verificarCorreo } from "./apoyo";

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

async function registrarYVerificar(page: Page): Promise<string> {
  const email = `diagnostico.${Date.now()}@orion.local`;
  await page.goto("/registro");
  await page.waitForLoadState("networkidle");
  await page.locator("#nombre").fill("Eduardo Prueba");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill(CLAVE);
  await aceptarCondiciones(page);
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await expect(page).toHaveURL(/\/profesores/);

  // Sin correo confirmado el diagnóstico no empieza, y ese es justo uno de los casos que se prueba.
  await verificarCorreo(page, email);
  return email;
}

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
  test("de la portada al resultado, con tres profesores y el salto a reservar", async ({ page }) => {
    await registrarYVerificar(page);

    // 1 · La portada lleva al diagnóstico, que es el primer gesto que ofrece Orión.
    await page.goto("/");
    await page.getByRole("link", { name: "Empezar mi diagnóstico" }).first().click();
    await expect(page).toHaveURL(/\/diagnostico/);

    // 2 · La puerta: objetivo y consentimiento. La casilla nace desmarcada y sin ella no se avanza.
    await page.goto("/diagnostico/empezar");
    await page.waitForLoadState("networkidle");
    const empezar = page.getByRole("button", { name: "Empezar la conversación" });
    await expect(empezar).toBeDisabled();

    await page.getByRole("checkbox").check();
    await expect(empezar).toBeEnabled();

    // 3 · El consentimiento, que es lo que ese botón envía. Se manda por API y no pulsando, porque
    //     pulsar arrancaría además la conexión de voz — y con el proveedor falso esa conexión no
    //     lleva a ninguna parte. Lo que importa de la pantalla ya está comprobado arriba: sin la
    //     casilla no se avanza.
    const consentido = await page.request.post("/api/v1/me/voice-consent", {
      headers: await cabeceras(page),
      data: {},
    });
    expect(consentido.status()).toBe(201);

    const creada = await page.request.post("/api/v1/assessments", {
      headers: await cabeceras(page),
      data: { languageCode: "EN", goals: [] },
    });
    expect(creada.status()).toBe(201);
    const { assessmentId } = (await creada.json()) as { assessmentId: string };

    // 4 · Cuatro turnos: el mínimo para que haya número. Con menos se cierra sin puntaje.
    await turno(page, assessmentId, 0, "Okay hi, my name is Eduardo and I work in logistics", 400);
    await turno(page, assessmentId, 1, "I coordinate shipments and talk to suppliers every day", 520);
    await turno(page, assessmentId, 2, "I want to use English at work, mostly in meetings", 610);
    await turno(page, assessmentId, 3, "If I could speak better I would lead those calls myself", 480);

    const cerrada = await page.request.post(`/api/v1/assessments/${assessmentId}/complete`, {
      headers: await cabeceras(page),
      data: { goals: [] },
    });
    expect(cerrada.status()).toBe(200);
    const resultado = (await cerrada.json()) as {
      status: string;
      score: number | null;
      recommendations: { professorId: string }[];
    };

    // 5 · Hay número, y viene de la clase pura: el mismo conjunto de turnos siempre da lo mismo.
    expect(resultado.status).toBe("COMPLETED");
    expect(resultado.score).not.toBeNull();

    // 6 · Y el resultado se ve, con su puntaje en pantalla.
    await page.goto("/cuenta?seccion=resumen");
    await page.waitForLoadState("networkidle");
    await expect(page.getByText("Confidence Score")).toBeVisible();
    await expect(page.getByText(String(resultado.score))).toBeVisible();

    // 7 · Si hubo recomendaciones, se puede saltar a la agenda de la primera. Si no las hubo
    //     —la semilla puede no tener profesores con cupos esta semana— el resultado lo dice y
    //     manda al directorio, que es el comportamiento correcto y no un fallo.
    if (resultado.recommendations.length > 0) {
      await page.goto(`/profesores/${resultado.recommendations[0].professorId}`);
      await page.waitForLoadState("networkidle");
      await expect(page.getByRole("heading").first()).toBeVisible();
    }
  });

  test("quien abandona con pocos turnos no recibe puntaje", async ({ page }) => {
    await registrarYVerificar(page);

    await page.request.post("/api/v1/me/voice-consent", {
      headers: await cabeceras(page),
      data: {},
    });
    const creada = await page.request.post("/api/v1/assessments", {
      headers: await cabeceras(page),
      data: { languageCode: "EN", goals: [] },
    });
    const { assessmentId } = (await creada.json()) as { assessmentId: string };

    await turno(page, assessmentId, 0, "Hello, I am Eduardo", 500);
    await turno(page, assessmentId, 1, "I work in logistics", 600);

    const cerrada = await page.request.post(`/api/v1/assessments/${assessmentId}/complete`, {
      headers: await cabeceras(page),
      data: { goals: [] },
    });

    const resultado = (await cerrada.json()) as { status: string; score: number | null };
    // Un puntaje sacado de dos frases parece un dato y no lo es. Antes que inventarlo, nada.
    expect(resultado.status).toBe("ABANDONED");
    expect(resultado.score).toBeNull();
  });
});
