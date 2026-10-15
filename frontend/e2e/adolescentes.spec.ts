import { expect, test } from "@playwright/test";
import { entrar, estudianteNueva, horariosAmplios, SEMILLA, sqlLocal } from "./apoyo";

/**
 * Bloque 12: un adolescente bajo la cuenta del adulto. El admin habilita a María (los documentos y el
 * código, por SQL: lo que importa aquí es la pantalla del adulto), la madre crea el perfil con la
 * autorización, reserva «para Sofi» y lo ve en Mis clases. Juan, sin habilitar, no se puede elegir.
 */
test.beforeAll(async ({ browser }) => {
  await horariosAmplios(browser);
});

test.beforeEach(async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
});

const idDe = (email: string) => sqlLocal(`select id from users where email = '${email}'`);

test("[a-cuenta.1 a-reservar.1 a-clases.1] la madre crea el perfil de Sofi, reserva para ella con un profe habilitado y lo ve en Mis clases", async ({ page }) => {
  const maria = idDe(SEMILLA.maria.email);
  // María queda habilitada: código aceptado, dos documentos y el interruptor del admin, como en producción.
  sqlLocal(`insert into agreement_acceptances (user_id, document_code, version, accepted_at) values ('${maria}', 'MINORS_CODE_OF_CONDUCT', '1.0', now()) on conflict do nothing`);
  // Sin índice único por (user_id, doc_type): se insertan solo si no están, para poder repetir la corrida.
  sqlLocal(`insert into teacher_documents (user_id, doc_type, file_name, storage_key, content_type, size_bytes) select '${maria}', 'REGISTRY_CHECK', 'registro.pdf', 'e2e/registro', 'application/pdf', 10 where not exists (select 1 from teacher_documents where user_id = '${maria}' and doc_type = 'REGISTRY_CHECK')`);
  sqlLocal(`insert into teacher_documents (user_id, doc_type, file_name, storage_key, content_type, size_bytes) select '${maria}', 'BACKGROUND_CHECK', 'antecedentes.pdf', 'e2e/antecedentes', 'application/pdf', 10 where not exists (select 1 from teacher_documents where user_id = '${maria}' and doc_type = 'BACKGROUND_CHECK')`);
  sqlLocal(`update professor_profiles set minors_cleared_until = current_date + 365, minors_cleared_at = now() where user_id = '${maria}'`);

  await estudianteNueva(page, "Madre de Sofi");
  // «Primera reserva» se enciende encima de la página cuando le llega el logro, no en un momento
  // fijo: el manejador lo cierra en cuanto aparezca, esté donde esté la prueba.
  await page.addLocatorHandler(page.getByRole("dialog").filter({ hasText: "Primera reserva" }), async (d) => {
    await d.getByRole("button", { name: "Seguir" }).click();
  });

  // En Cuenta → Mi ficha y mis datos: «Adolescentes a mi cargo».
  await page.goto("/cuenta?seccion=ficha");
  await expect(page.getByRole("heading", { name: "Adolescentes a mi cargo" })).toBeVisible();
  await page.getByRole("button", { name: "Agregar a un adolescente" }).click();
  await page.getByLabel("Nombre de pila").fill("Sofi");
  await page.getByRole("button", { name: "Se defiende" }).click();
  await page.getByRole("button", { name: "Seguir" }).click();
  await expect(page.getByRole("dialog")).toContainText("Autorización del representante legal");
  await expect(page.getByText(/padre, madre o tutor legal/).first()).toBeVisible();
  // Por rol: el texto legal y la casilla dicen las dos «Declaro que soy el padre…».
  await page.getByRole("checkbox", { name: /Declaro que soy el padre, la madre o el tutor legal de Sofi/ }).check();
  await page.getByRole("checkbox", { name: /Hablé con Sofi/ }).check();
  await page.getByRole("button", { name: "Autorizar y crear el perfil" }).click();
  await expect(page.getByText(/Sofi · 1[3-7] años/)).toBeVisible();
  await expect(page.getByText("Autorizado")).toBeVisible();

  // Con Juan (sin habilitar) Sofi no se puede elegir; con María sí.
  const juan = idDe(SEMILLA.juan.email);
  await page.goto(`/profesores/${juan}`);
  await expect(page.getByText("¿Para quién es la clase?")).toBeVisible();
  await expect(page.getByRole("button", { name: /^Para Sofi/ })).toBeDisabled();
  await expect(page.getByText(/todavía no está habilitado para dar clase a adolescentes/)).toBeVisible();

  await page.goto(`/profesores/${maria}`);
  await expect(page.getByText(/Habilitado por Orión para clases con adolescentes/)).toBeVisible();
  await page.getByRole("button", { name: /^Para Sofi/ }).click();
  await page.locator("main [aria-label='Horarios del día'] button").first().click();
  // La de prueba, gratis: en local no hay Wompi, y además es la regla que importa (una por cada hijo).
  await page.getByRole("button", { name: /^Clase de prueba/ }).click();
  await page.getByRole("button", { name: /Confirmar reserva/ }).click();
  await expect(page).toHaveURL(/\/mis-clases/, { timeout: 20_000 });
  await expect(page.locator("main li").filter({ hasText: "Para Sofi" }).first()).toBeVisible();

  // Una prueba por cada hijo (decisión 9 del 14/10): Sofi ya usó la suya con María; la madre conserva la propia.
  await page.goto(`/profesores/${maria}`);
  await page.getByRole("button", { name: /^Para Sofi/ }).click();
  await page.locator("main [aria-label='Horarios del día'] button").first().click();
  await expect(page.getByText("Total a pagar")).toBeVisible();
  await expect(page.getByRole("button", { name: /^Clase de prueba/ })).toHaveCount(0);
  await page.getByRole("button", { name: "Para mí" }).click();
  await expect(page.getByRole("button", { name: /^Clase de prueba/ })).toBeVisible();

  // El buscador para un adolescente solo trae a María.
  await page.goto("/profesores");
  await page.getByRole("button", { name: /Filtros/ }).first().click().catch(() => undefined);
  const toggle = page.getByRole("switch", { name: "Para un adolescente" }).first();
  await expect(toggle).toBeVisible();
  await toggle.click();
  await expect(page.getByText(/María/).first()).toBeVisible();
  await expect(page.locator("main").getByText("Juan Torres")).toHaveCount(0);
});

test("[p-perfil.9] el profe ve «Clases con adolescentes» en su perfil, con los tres pasos", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/perfil");
  await expect(page.getByRole("heading", { name: "Clases con adolescentes" })).toBeVisible();
  await expect(page.getByText("Aceptar el código de conducta para clases con menores")).toBeVisible();
  await expect(page.getByText("Consulta del Registro de inhabilidades")).toBeVisible();
  await expect(page.getByText("Certificado de antecedentes judiciales")).toBeVisible();

  // Los enlaces oficiales (15/10/2026): uno por certificado, a la Policía Nacional, en otra pestaña.
  const enlaces = page.getByRole("link", { name: "Consultar en la Policía Nacional" });
  await expect(enlaces).toHaveCount(2);
  await expect(enlaces.nth(0)).toHaveAttribute("href", "https://inhabilidades.policia.gov.co:8080/");
  await expect(enlaces.nth(1)).toHaveAttribute("href", "https://antecedentes.policia.gov.co:7005/WebJudicial/");
  await expect(enlaces.nth(0)).toHaveAttribute("target", "_blank");
  // Y lo que el formulario pide (15/10/2026): la empresa consultante y su NIT.
  await expect(page.getByText(/empresa consultante escribe «Orion Idiomas» y como NIT 1013576324-1/)).toBeVisible();

  // El previsualizador: con el documento de la semilla (sin Cloudinary local) dice que no puede abrirlo,
  // y deja los dos enlaces de salida. Con Cloudinary real muestra el archivo.
  const ver = page.getByRole("button", { name: /^Ver / }).first();
  if (await ver.isVisible().catch(() => false)) {
    await ver.click();
    const visor = page.getByRole("dialog");
    await expect(visor.getByRole("link", { name: "Abrir en otra pestaña" })).toBeVisible();
    await expect(visor.getByRole("link", { name: "Descargar" })).toBeVisible();
    await expect(visor.getByText(/No podemos abrir documentos en este momento|No pudimos abrir el documento/)).toBeVisible({ timeout: 15_000 });
  }
});
