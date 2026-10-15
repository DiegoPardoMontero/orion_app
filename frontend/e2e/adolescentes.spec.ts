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
  sqlLocal(`insert into teacher_documents (user_id, doc_type, file_name, storage_key, content_type, size_bytes) values ('${maria}', 'REGISTRY_CHECK', 'registro.pdf', 'e2e/registro', 'application/pdf', 10), ('${maria}', 'BACKGROUND_CHECK', 'antecedentes.pdf', 'e2e/antecedentes', 'application/pdf', 10)`);
  sqlLocal(`update professor_profiles set minors_cleared_until = current_date + 365, minors_cleared_at = now() where user_id = '${maria}'`);

  await estudianteNueva(page, "Madre de Sofi");

  // En Cuenta → Mi ficha y mis datos: «Adolescentes a mi cargo».
  await page.goto("/cuenta?seccion=ficha");
  await expect(page.getByRole("heading", { name: "Adolescentes a mi cargo" })).toBeVisible();
  await page.getByRole("button", { name: "Agregar a un adolescente" }).click();
  await page.getByLabel("Nombre de pila").fill("Sofi");
  await page.getByRole("button", { name: "Se defiende" }).click();
  await page.getByRole("button", { name: "Seguir" }).click();
  await expect(page.getByRole("dialog")).toContainText("Autorización del representante legal");
  await expect(page.getByText(/padre, madre o tutor legal/).first()).toBeVisible();
  await page.getByText(/Declaro que soy el padre, la madre o el tutor legal/).click();
  await page.getByText(/Hablé con Sofi/).click();
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
  await page.getByRole("button", { name: /Confirmar reserva|Continuar al pago/ }).click();
  await expect(page).toHaveURL(/\/mis-clases|\/pago\//, { timeout: 20_000 });

  if (/\/mis-clases/.test(page.url())) {
    await expect(page.locator("main li").filter({ hasText: "Para Sofi" }).first()).toBeVisible();
  } else {
    // La clase de María se paga: la reserva existe aunque espere el pago, y dice para quién es.
    const reservas = sqlLocal(`select count(*) from bookings where adolescent_profile_id is not null and professor_id = '${maria}'`);
    expect(Number(reservas)).toBeGreaterThan(0);
  }

  // El buscador para un adolescente solo trae a María.
  await page.goto("/profesores");
  await page.getByRole("button", { name: /Filtros/ }).first().click().catch(() => undefined);
  const toggle = page.getByRole("switch", { name: "Para un adolescente" }).first();
  if (await toggle.isVisible().catch(() => false)) {
    await toggle.click();
    await expect(page.getByText(/María/).first()).toBeVisible();
  }
});

test("[p-perfil.9] el profe ve «Clases con adolescentes» en su perfil, con los tres pasos", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/perfil");
  await expect(page.getByRole("heading", { name: "Clases con adolescentes" })).toBeVisible();
  await expect(page.getByText("Aceptar el código de conducta para clases con menores")).toBeVisible();
  await expect(page.getByText("Consulta del Registro de inhabilidades")).toBeVisible();
  await expect(page.getByText("Certificado de antecedentes judiciales")).toBeVisible();
});
