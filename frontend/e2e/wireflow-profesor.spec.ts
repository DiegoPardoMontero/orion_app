import { expect, test } from "@playwright/test";
import { api, aparece, apartarCelebraciones, entrar, estudianteNueva, horariosAmplios, SEMILLA, sqlLocal, ultimoCorreo } from "./apoyo";

/**
 * El wireflow del profesor y del admin (24/09/2026). María es la profesora de la semilla; lo que
 * cambia su perfil se deja como estaba al terminar, porque las demás pruebas cuentan con él.
 */

test.beforeAll(async ({ browser }) => {
  await horariosAmplios(browser);
});

test.beforeEach(async ({ page }) => {
  await apartarCelebraciones(page);
});

test("[p-agenda.1 p-agenda.3 p-actas.1] la agenda y las actas por escribir", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await expect(page).toHaveURL(/\/mis-clases/);
  await expect(page.getByText(/Ana Ramírez|Prueba|Estudiante/).first()).toBeVisible();
  await page.goto("/mis-clases?scope=past");
  await expect(page.getByRole("region", { name: "Actas por escribir" })).toBeVisible();
});

test("[p-perfil.1 p-perfil.2 p-perfil.5 e-cambios.4] el perfil se edita directo, con la tarifa en el mismo guardado", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/perfil");
  const barra = page.getByRole("region", { name: "Cambios sin guardar" });
  const tarifa = page.locator("#tarifa");
  const original = await tarifa.inputValue();
  await tarifa.fill("50000");
  await expect(page.getByText("Tú recibes")).toBeVisible();
  await expect(barra).toBeVisible();
  await barra.getByRole("button", { name: "Descartar" }).click();
  await expect(tarifa).toHaveValue(original);

  // Un titular de dos palabras no pasa, y el error sale en la barra.
  const titular = page.locator("#headline");
  const titularOriginal = await titular.inputValue();
  await titular.fill("Profesora inglés");
  await barra.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(barra.getByRole("alert")).toContainText(/mínimo/);
  await barra.getByRole("button", { name: "Descartar" }).click();
  await expect(titular).toHaveValue(titularOriginal);

  // Cambiar de pestaña con cambios pregunta.
  await page.locator("#city").fill("Medellín");
  await page.getByRole("link", { name: "Mis horarios" }).click();
  await expect(page.getByRole("dialog", { name: "¿Salir sin guardar?" })).toBeVisible();
  await page.getByRole("button", { name: "Salir sin guardar" }).click();
  await expect(page).toHaveURL(/seccion=horarios/);
  await page.goto("/perfil");
  await expect(page.locator("#city")).not.toHaveValue("Medellín");
});

test("[p-perfil.3 e-prueba.5] apagar la primera clase gratis la quita del perfil del estudiante", async ({ page, browser }) => {
  const estudianteCtx = await browser.newContext();
  const estudiante = await estudianteCtx.newPage();
  await estudianteNueva(estudiante, "Mira Prueba");

  await entrar(page, SEMILLA.maria);
  await page.goto("/perfil");
  const gratis = page.getByRole("switch", { name: "Primera clase gratis" });
  const barra = page.getByRole("region", { name: "Cambios sin guardar" });
  if ((await gratis.getAttribute("aria-checked")) !== "true") {
    await gratis.click();
    await barra.getByRole("button", { name: "Guardar cambios" }).click();
    await expect(page.getByText("Cambios guardados")).toBeVisible();
  }
  await gratis.click();
  await barra.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page.getByText("Cambios guardados")).toBeVisible();

  await estudiante.goto("/profesores");
  await estudiante.getByRole("link", { name: /Ver agenda/ }).first().click();
  await expect(estudiante.getByText("Cupos disponibles")).toBeVisible();
  await expect(estudiante.getByText("¿Cómo quieres tu primera clase?")).toHaveCount(0);

  // Se deja como estaba: las demás pruebas cuentan con que María la ofrece.
  await gratis.click();
  await barra.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page.getByText("Cambios guardados")).toBeVisible();
  await estudiante.reload();
  await expect(estudiante.getByText("¿Cómo quieres tu primera clase?")).toBeVisible();
  await estudianteCtx.close();
});

test("[p-horarios.1 p-horarios.2 p-horarios.3 p-horarios.4] franjas, solapes y fechas bloqueadas", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/disponibilidad");
  await expect(page).toHaveURL(/\/perfil\?seccion=horarios/);
  const agregar = async () => {
    await page.getByRole("button", { name: "Añadir franja el sábado" }).click();
    const dialogo = page.getByRole("dialog", { name: /Nueva franja/ });
    await dialogo.locator("select").first().selectOption("19:00");
    await dialogo.locator("select").last().selectOption("20:00");
    await dialogo.getByRole("button", { name: /Guardar|Agregar|Añadir/ }).last().click();
    return dialogo;
  };
  try {
    const primera = await agregar();
    await expect(primera).toHaveCount(0);
    const dialogo = await agregar();
    await expect(dialogo.getByText(/se solapa/)).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(dialogo).toHaveCount(0);
  } finally {
    // Se quita la franja de prueba, pase lo que pase: las demás pruebas cuentan con el sábado como estaba.
    const reglas = (await api(page, "GET", "/api/v1/me/availability/rules")).json as { id: string; weekday: number; startTime: string }[];
    for (const r of reglas.filter((r) => r.weekday === 6 && r.startTime.startsWith("19:00"))) {
      await api(page, "DELETE", `/api/v1/me/availability/rules/${r.id}`);
    }
  }

  await page.getByRole("button", { name: "Bloquear una fecha" }).click();
  const fecha = new Date(Date.now() + 75 * 86400000).toISOString().slice(0, 10);
  const bloquear = page.getByRole("dialog", { name: "Bloquear una fecha" });
  await bloquear.locator('input[type="date"]').fill(fecha);
  await bloquear.getByRole("button", { name: /Bloquear/ }).last().click();
  await expect(bloquear).toHaveCount(0);
  const excepciones = (await api(page, "GET", "/api/v1/me/availability/exceptions")).json as { id: string; date: string }[];
  const creada = excepciones.find((e) => e.date === fecha);
  expect(creada).toBeTruthy();
  await api(page, "DELETE", `/api/v1/me/availability/exceptions/${creada!.id}`);
});

test("[p-invitar.1 p-invitar.2 p-invitar.3 p-invitar.4] invitar con el enlace corto y el mensaje listo", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/invitar");
  await expect(page.getByText(/\/p\/maria-gomez/)).toBeVisible();
  await expect(page.locator("#mensaje")).toHaveValue(/gratis/);
  for (const [red, destino] of [["WhatsApp", /wa\.me/], ["Facebook", /facebook\.com/], ["LinkedIn", /linkedin\.com/], ["X", /x\.com/], ["Telegram", /t\.me/], ["Correo", /^mailto:/]] as const) {
    await expect(page.getByRole("link", { name: red, exact: true })).toHaveAttribute("href", destino);
  }
  await page.locator("#mensaje").fill("Hola, reserva conmigo en Orión.");
  await expect(page.getByRole("link", { name: "WhatsApp", exact: true })).toHaveAttribute("href", /Hola%2C%20reserva%20conmigo/);
});

test("[p-ganancias.1 p-ganancias.2 p-ganancias.3 p-desempeno.1] ganancias y desempeño", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/ganancias");
  for (const estado of ["Retenido", "Por cobrar", "En camino", "Transferido"]) {
    await expect(page.getByText(estado).first()).toBeVisible();
  }
  await expect(page.locator('input[type="date"]').first()).toBeVisible();
  await page.goto("/desempeno");
  await expect(page.getByRole("heading", { name: "Mi desempeño" })).toBeVisible();
});

test("[p-estudiante.1 p-estudiante.2 p-estudiante.3] la ficha del estudiante, solo para sus profes", async ({ page, browser }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/mis-clases?scope=past");
  await page.getByRole("link", { name: "Ana Ramírez", exact: true }).first().click();
  await expect(page).toHaveURL(/\/estudiantes\/[0-9a-f-]+$/);
  await expect(page.getByText(/Practicó \d+ de \d+/).first()).toBeVisible();
  // Un profe que nunca tuvo clase con una estudiante (y con la ficha privada) no la ve.
  const otraCtx = await browser.newContext();
  const otra = await otraCtx.newPage();
  await estudianteNueva(otra, "Privada Prueba");
  const ella = (await api(otra, "GET", "/api/v1/auth/me")).json as { id: string };
  await otraCtx.close();
  const juanCtx = await browser.newContext();
  const juan = await juanCtx.newPage();
  await entrar(juan, SEMILLA.juan);
  const r = await api(juan, "GET", `/api/v1/students/${ella.id}/profile`);
  expect([403, 404]).toContain(r.status);
  await juanCtx.close();
});

test("[p-mensajes.1 p-mensajes.2 p-rigel.1 p-rigel.2] el profe escribe solo a quien reservó; Rigel le habla a él", async ({ page, browser }) => {
  const otraCtx = await browser.newContext();
  const otra = await otraCtx.newPage();
  await estudianteNueva(otra, "Nunca Reservo");
  const yo = (await api(otra, "GET", "/api/v1/auth/me")).json as { id: string };
  await otraCtx.close();

  await entrar(page, SEMILLA.maria);
  const r = await api(page, "POST", "/api/v1/conversations", { counterpartId: yo.id });
  expect(r.status).toBe(403);
  await page.goto("/mensajes/rigel");
  await expect(page.getByRole("link", { name: /Mis horarios/ }).first()).toHaveAttribute("href", "/perfil?seccion=horarios");
  await expect(page.getByRole("link", { name: /Invitar estudiantes/ }).last()).toHaveAttribute("href", "/invitar");
});

test("[p-ayuda.1 e-recorrido.3] desde Ayuda se repite el recorrido del profesor", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/ayuda");
  await page.getByRole("button", { name: /Repetir el recorrido/ }).click();
  await expect(page.getByRole("dialog").getByText(/en 8 pasos|1 de 8/).first()).toBeVisible();
});

// ------------------------------------------------------------------ admin

test("[ad-panel.1 ad-aplicaciones.1 ad-reservas.1 ad-pagos.1 ad-devoluciones.1 ad-reclamos.1 ad-resenas.1 ad-aula.1] las pantallas del admin abren con sus datos o su estado vacío", async ({ page }) => {
  await entrar(page, SEMILLA.admin);
  for (const [ruta, titulo] of [
    ["/admin/panel", "Panel"],
    ["/admin/aplicaciones", "Postulaciones"],
    ["/admin/reservas", "Reservas"],
    ["/admin/pagos", "Pagos"],
    ["/admin/devoluciones", "Devoluciones"],
    ["/admin/reclamos", "Reclamos"],
    ["/admin/resenas", "Reseñas reportadas"],
    ["/admin/aula", "Aula"],
  ] as const) {
    await page.goto(ruta);
    await expect(page.getByRole("heading", { name: titulo, exact: true }).first()).toBeVisible();
  }
});

test("[ad-usuarios.1 ad-usuarios.2] buscar usuarios e invitar a un profesor", async ({ page }) => {
  await entrar(page, SEMILLA.admin);
  await page.goto("/admin/usuarios");
  // Por su nombre accesible, no por el texto de ejemplo: un lector de pantalla no lee el placeholder.
  await page.getByRole("searchbox", { name: "Buscar usuarios por nombre o correo" }).fill("ana@orion.local");
  await expect(page.getByText("ana@orion.local").first()).toBeVisible();
  await page.getByRole("button", { name: /Invitar profesor/ }).click();
  const correo = `wf.invitado.${Date.now()}@orion.local`;
  await page.getByPlaceholder("profesor@correo.com").fill(correo);
  await page.getByRole("dialog").getByRole("button", { name: /Invitar|Enviar/ }).last().click();
  expect(await ultimoCorreo(page, correo, /invitacion\//)).not.toBeNull();
});

test("[ad-usuarios.4] el admin quita y vuelve a dar el beneficio de profe fundador", async ({ page }) => {
  await entrar(page, SEMILLA.admin);
  await page.goto("/admin/usuarios");
  await page.getByRole("searchbox", { name: "Buscar usuarios por nombre o correo" }).fill("juan@orion.local");
  const fila = page.locator("tr", { hasText: "juan@orion.local" });
  // Los profes que ya estaban son fundadores (V70; la semilla hace lo mismo).
  await expect(fila.getByText(/^Fundador · 15 %/)).toBeVisible();
  await fila.getByRole("button", { name: "Quitar fundador" }).click();
  // La confirmación es un diálogo: dentro de la fila la estiraba hasta pedir scroll horizontal.
  const confirmar = page.getByRole("dialog", { name: "¿Quitar el beneficio de fundador?" });
  await expect(confirmar.getByText("Solo cambia las reservas nuevas.")).toBeVisible();
  await confirmar.getByRole("button", { name: "Quitar", exact: true }).click();
  await expect(fila.getByText("Sin beneficio de fundador")).toBeVisible();
  await fila.getByRole("button", { name: "Hacer fundador" }).click();
  await expect(fila.getByText(/^Fundador · 15 %/)).toBeVisible();
});

test("[p-perfil.6] la tarifa dice cuánto recibe el profe fundador y cuánto después", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/perfil");
  const tarifa = page.locator("#tarifa");
  await tarifa.fill("60000");
  await expect(page.getByText(/Recibes \$51\.000 por clase: 15 % de comisión como profe fundador/)).toBeVisible();
  await expect(page.getByText(/Después recibirás \$48\.000 \(20 %\)/)).toBeVisible();
});

test("[ad-ajustes.2 ad-sistema.1 ad-sistema.2] el historial de ajustes y el correo de prueba", async ({ page }) => {
  await entrar(page, SEMILLA.admin);
  await page.goto("/admin/ajustes");
  await page.getByRole("button", { name: /Historial/ }).click();
  await expect(page.getByRole("dialog").first()).toBeVisible();
  await page.keyboard.press("Escape");
  await page.goto("/admin/sistema");
  await expect(page.getByText(/Videollamada|JaaS/).first()).toBeVisible();
  const destino = `wf.prueba.${Date.now()}@orion.local`;
  await page.getByPlaceholder("tu correo de Gmail, por ejemplo").fill(destino);
  await page.getByRole("button", { name: "Enviar correo de prueba" }).click();
  expect(await ultimoCorreo(page, destino)).not.toBeNull();
});

// ------------------------------------------------------------------ aspirante, invitación y más del admin

test("[a-postulacion.2 a-postulacion.4 a-estado.1] la postulación: país con bandera, prueba gratis y lo que falta", async ({ page }) => {
  await page.goto("/registro");
  await page.waitForLoadState("networkidle");
  await page.getByRole("button", { name: /Quiero enseñar/ }).click();
  await page.locator("#nombre").fill("Aspirante Prueba");
  await page.locator("#email").fill(`wf.aspirante.${Date.now()}@orion.local`);
  await page.locator("#password").fill("orion123*");
  for (const id of ["#mayor-de-edad", "#acepta-terminos", "#acepta-datos"]) await page.locator(id).check();
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await page.waitForURL(/\/aplicacion/);
  // El país y la prueba gratis están en el tercer paso; cada «Siguiente» guarda.
  for (let i = 0; i < 3 && !(await page.locator("#country").isVisible()); i++) {
    await page.getByRole("button", { name: /Siguiente/ }).click();
    await page.waitForTimeout(700);
  }
  const pais = page.locator("#country");
  await expect(pais.locator('option[value="CO"]').first()).toHaveText(/Colombia/);
  // Colombia y los frecuentes van arriba.
  expect(await pais.locator("option:not([value=''])").first().getAttribute("value")).toBe("CO");
  await expect(page.getByRole("switch", { name: "Primera clase gratis" })).toBeVisible();
  await page.goto("/aplicacion/estado");
  await expect(page.getByText("Te falta por completar")).toBeVisible();
});

test("[v-invitacion.1 v-invitacion.3 v-invitacion.4 ad-usuarios.2] la invitación: la pantalla, el registro con su correo y el enlace ya usado", async ({ page, browser }) => {
  await entrar(page, SEMILLA.admin);
  await page.goto("/admin/usuarios");
  await page.getByRole("button", { name: /Invitar profesor/ }).click();
  const correo = `wf.invitada.${Date.now()}@orion.local`;
  const dialogo = page.getByRole("dialog");
  await dialogo.locator("#invite-email").fill(correo);
  await dialogo.locator("#invite-nombre").fill("Mariana");
  await dialogo.locator("#invite-cargo").fill("directora académica");
  await dialogo.getByRole("button", { name: /Enviar invitación/ }).click();
  await expect(page.getByText(/Le enviamos la invitación/)).toBeVisible();
  const texto = await ultimoCorreo(page, correo, /invitacion\//);
  const enlace = texto!.match(/https?:\/\/[^"\\\s]*\/invitacion\/[A-Za-z0-9_-]+/)![0].replace(/^https?:\/\/[^/]+/, "");

  const ctx = await browser.newContext();
  const invitada = await ctx.newPage();
  await invitada.goto(enlace);
  await expect(invitada.getByRole("heading", { name: "Mariana, queremos que seas de los primeros profes de Orión." })).toBeVisible();
  await expect(invitada.getByText("Invitación personal · Profes fundadores")).toBeVisible();
  await expect(invitada.getByText(/Te invita/)).toContainText("directora académica");
  await expect(invitada.getByText(/Como profe fundador, tienes 15 %/)).toBeVisible();
  await invitada.getByRole("link", { name: "Aceptar la invitación" }).click();

  // El registro de profesor con el correo de la invitación, sin poder cambiarlo ni elegir «aprender».
  await expect(invitada).toHaveURL(/\/registro\?invitacion=/);
  await expect(invitada.locator("#email")).toHaveValue(correo);
  await expect(invitada.locator("#email")).toHaveAttribute("readonly", "");
  await expect(invitada.getByRole("button", { name: /Quiero aprender/ })).toBeHidden();
  await invitada.locator("#nombre").fill("Mariana Ruiz");
  await invitada.locator("#password").fill("orion123*");
  for (const id of ["#mayor-de-edad", "#acepta-terminos", "#acepta-datos"]) await invitada.locator(id).check();
  await invitada.getByRole("button", { name: "Crear cuenta y postularme" }).click();
  await invitada.waitForURL(/\/aplicacion/);

  // El mismo enlace, ya usado: no se vuelve a usar y lleva a iniciar sesión.
  await invitada.goto(enlace);
  await expect(invitada.getByRole("heading", { name: "Esta invitación ya se usó." })).toBeVisible();
  await expect(invitada.getByRole("link", { name: "Inicia sesión" }).first()).toBeVisible();
  await ctx.close();
});

test("[p-bienvenida.1 p-bienvenida.2 p-bienvenida.3] con video: la bienvenida una vez, «Lo veo después» y Rigel ofrece el recorrido", async ({ page, browser }) => {
  await entrar(page, SEMILLA.admin);
  // Los profesores de la semilla ya la vieron: la ve uno recién aprobado. Desde la V71 la invitación
  // pasa por la postulación, que en local no se completa sin Cloudinary: el admin crea al profe y la
  // aprobación se escribe en la base, como la dejaba el enlace de invitación de antes.
  const video = "https://www.youtube.com/watch?v=aqz-KE-bpKQ";
  expect((await api(page, "PUT", "/api/v1/admin/settings/professor_welcome_video_url", { value: video })).status).toBe(200);
  try {
    const correo = `wf.bienvenida.${Date.now()}@orion.local`;
    expect(
      (await api(page, "POST", "/api/v1/admin/users", { email: correo, fullName: "Bienvenida Prueba", role: "PROFESSOR", password: "orion123*" })).status,
    ).toBe(201);
    sqlLocal(
      `insert into teacher_applications (user_id, status, reviewed_at) select id, 'APPROVED', now() from users where email = '${correo}'`,
    );
    const ctx = await browser.newContext();
    const profe = await ctx.newPage();
    await entrar(profe, { email: correo, pass: "orion123*" });

    // La cuenta que crea el admin no declaró la mayoría de edad: la pide el diálogo, hablándole como profe.
    const antesDeSeguir = profe.getByRole("dialog", { name: "Antes de seguir" });
    await expect(antesDeSeguir.getByText(/seguir dando clases/)).toBeVisible({ timeout: 20_000 });
    await expect(antesDeSeguir.getByText(/saldo/)).toHaveCount(0);
    await antesDeSeguir.getByLabel(/mayor de 18 años/).check();
    await antesDeSeguir.getByRole("button", { name: "Confirmar" }).click();

    const bienvenida = profe.getByRole("dialog", { name: "Bienvenida a Orión" });
    await expect(bienvenida).toBeVisible({ timeout: 20_000 });
    await expect(bienvenida.getByRole("button", { name: "Empezar el recorrido" }).first()).toBeVisible();
    await bienvenida.getByRole("button", { name: "Lo veo después" }).first().click();
    await expect(bienvenida).toBeHidden();
    await expect(profe).toHaveURL(/\/mis-clases/);

    // Rigel lo ofrece en la agenda, y el recorrido lleva de pantalla en pantalla.
    const aviso = profe.getByRole("complementary", { name: "El recorrido de Orión" });
    await expect(aviso).toBeVisible();
    await aviso.getByRole("button", { name: "Empezar el recorrido" }).click();
    const recorrido = profe.getByRole("dialog");
    await expect(recorrido.getByRole("heading", { name: "Te muestro Orión en 8 pasos" })).toBeVisible();
    await recorrido.getByRole("button", { name: "Empezar" }).click();
    await expect(recorrido.getByText(/^1 de \d/)).toBeVisible();
    const antes = profe.url();
    await recorrido.getByRole("button", { name: "Siguiente" }).click();
    await expect(recorrido.getByText(/^2 de \d/)).toBeVisible();
    await expect.poll(() => profe.url()).not.toBe(antes);
    await recorrido.getByRole("button", { name: /^(Saltar|Ahora no)$/ }).click();

    // Una sola vez: ni la bienvenida ni el aviso vuelven.
    await profe.goto("/mis-clases");
    await profe.waitForTimeout(1500);
    await expect(profe.getByRole("dialog", { name: "Bienvenida a Orión" })).toHaveCount(0);
    await expect(profe.getByRole("complementary", { name: "El recorrido de Orión" })).toHaveCount(0);
    await ctx.close();
  } finally {
    await api(page, "PUT", "/api/v1/admin/settings/professor_welcome_video_url", { value: "" });
  }
});

test("[p-acta-publicada.1] el acta publicada se corrige hasta la fecha que dice", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  await page.goto("/mis-clases?scope=past");
  // Las etiquetas del acta llegan con su propia consulta: se espera a la primera antes de decidir.
  await page.getByRole("link", { name: /Ver el acta|Terminar el acta|Contar cómo estuvo/ }).first().waitFor({ timeout: 10_000 }).catch(() => {});
  const publicada = page.getByRole("link", { name: "Ver el acta" }).first();
  test.skip(!(await aparece(publicada, 2000)), "Sin actas publicadas en esta base");
  await publicada.click();
  await expect(page.getByText(/corregirla hasta|corregir hasta/i).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Corregir" })).toBeVisible();
});

test("[p-invitar.5] sin perfil publicado, «Invitar» avisa que nadie lo va a encontrar", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  // María está publicada y las demás pruebas cuentan con eso: se le cambia solo lo que ve la pantalla.
  await page.route("**/api/v1/me/profile", async (r) => {
    if (r.request().method() !== "GET") return r.continue();
    const real = await r.fetch();
    await r.fulfill({ response: real, json: { ...(await real.json()), isPublished: false } });
  });
  await page.goto("/invitar");
  await expect(page.getByText(/Tu perfil todavía no está publicado: quien abra el enlace no te va a encontrar/)).toBeVisible();
});

test("[p-perfil.4] publicar sin tarifa pide fijarla primero", async ({ page }) => {
  await entrar(page, SEMILLA.maria);
  // Un profesor recién aprobado: sin tarifa y sin publicar. Que el servidor también lo rechace lo
  // prueba ProfessorDirectoryIT.publishingWithoutARateIsRejected.
  await page.route("**/api/v1/me/profile", async (r) => {
    if (r.request().method() !== "GET") return r.continue();
    const real = await r.fetch();
    const perfil = await real.json();
    await r.fulfill({ response: real, json: { ...perfil, isPublished: false, hourlyRateCop: null, rate: null, canPublish: false } });
  });
  await page.goto("/perfil");
  await expect(page.getByText(/Los estudiantes dejarán de verte/)).toBeVisible();
  await page.getByRole("switch", { name: "Perfil visible" }).click();
  await expect(page.getByText("Fija tu tarifa antes de publicar: escribe un precio por hora más arriba y guarda.")).toBeVisible();
});

test("[ad-usuarios.3] el admin crea un usuario", async ({ page }) => {
  await entrar(page, SEMILLA.admin);
  await page.goto("/admin/usuarios");
  await page.getByRole("button", { name: "Crear usuario" }).click();
  const dialogo = page.getByRole("dialog", { name: "Crear usuario" });
  const correo = `wf.creado.${Date.now()}@orion.local`;
  await dialogo.locator("#nombre").fill("Creado Prueba");
  await dialogo.locator("#email").fill(correo);
  await dialogo.getByRole("button").last().click();
  await page.getByPlaceholder("Buscar por nombre o correo").fill(correo);
  await expect(page.getByText(correo).first()).toBeVisible();
});
