import { expect, test } from "@playwright/test";
import { entrar, SEMILLA, sqlLocal } from "./apoyo";

/**
 * Las liquidaciones bajo mandato de punta a punta (brief de liquidaciones, pasos 2, 4 y 5): María
 * registra su llave Bre-B, el admin aprueba su liquidación y registra el pago, y ella ve el
 * comprobante.
 *
 * La liquidación se siembra por SQL: la semilla no trae clases pagadas por la pasarela, y el corte
 * corre una vez por quincena. Se usa la última quincena ya cerrada, la que el admin ve por defecto.
 */

/** La última quincena cerrada hoy en Bogotá: la del 1 al 15 si ya pasó el 16; si no, la del 16 a fin de mes anterior. */
function ultimaQuincena(): { inicio: string; fin: string; corte: string } {
  const hoy = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota" }).format(new Date());
  const [a, m, d] = hoy.split("-").map(Number);
  const dos = (n: number) => String(n).padStart(2, "0");
  if (d >= 16) {
    return { inicio: `${a}-${dos(m)}-01`, fin: `${a}-${dos(m)}-15`, corte: `${a}-${dos(m)}-16` };
  }
  const anterior = new Date(Date.UTC(a, m - 1, 0));
  const am = anterior.getUTCMonth() + 1;
  const aa = anterior.getUTCFullYear();
  return { inicio: `${aa}-${dos(am)}-16`, fin: `${aa}-${dos(am)}-${dos(anterior.getUTCDate())}`, corte: `${a}-${dos(m)}-01` };
}

function sembrarLiquidacionDeMaria(): void {
  const q = ultimaQuincena();
  sqlLocal(
    `insert into payout_cuts (period_start, period_end, cutoff_at, payouts_created) values ('${q.inicio}', '${q.fin}', '${q.corte} 05:00:00+00', 0) on conflict do nothing`,
  );
  sqlLocal(`
    with b as (
      select b.id, b.student_id, b.professor_id, b.starts_at from bookings b join users u on u.id = b.professor_id
       where u.email = 'maria@orion.local' and b.status = 'COMPLETED'
         and not exists (select 1 from payments p where p.booking_id = b.id)
       order by b.starts_at limit 1),
    pago as (
      insert into payments (booking_id, student_id, professor_id, amount_cop, credit_applied_cop, charged_cop,
                            commission_rate_bps, commission_cop, professor_earnings_cop, status, paid_at, released_at)
      select id, student_id, professor_id, 45000, 0, 45000, 1500, 6750, 38250, 'RELEASED', starts_at, starts_at from b
      returning id, booking_id, professor_id),
    liquidacion as (
      insert into payouts (professor_id, period_start, period_end, cutoff_at, committed_pay_date, status,
                           gross_cop, commission_cop, adjustments_cop, amount_cop)
      select professor_id, '${q.inicio}', '${q.fin}', '${q.corte} 05:00:00+00', '${q.corte}', 'DRAFT', 45000, 6750, 0, 38250 from pago
      returning id)
    insert into payout_lines (payout_id, kind, booking_id, payment_id, class_at, student_label, gross_cop,
                              commission_rate_bps, commission_cop, net_cop, description)
    select liquidacion.id, 'CLASS', pago.booking_id, pago.id, (select starts_at from b), 'Ana R.', 45000, 1500, 6750, 38250,
           'Clase sembrada para el e2e'
      from liquidacion, pago`);
}

test("[p-perfil.7 p-datos-pago.1 p-datos-pago.2 ad-pagos.3 ad-liquidacion.1 ad-liquidacion.2 ad-liquidacion.3 p-ganancias.5 p-ganancias.7] la llave Bre-B, el pago de la liquidación y el comprobante", async ({ browser }) => {
  // 1. María cambia a dónde se le paga (la semilla ya trae una llave: es obligatoria): lo ve enmascarado.
  const profe = await (await browser.newContext()).newPage();
  await entrar(profe, SEMILLA.maria);
  await profe.goto("/perfil?seccion=pagos");
  await expect(profe.getByText("La llave debe estar a tu nombre. Solo el equipo de Orión ve estos datos completos.")).toBeVisible();
  await expect(profe.getByText("••••6543")).toBeVisible();
  await profe.getByRole("button", { name: "Cambiar mis datos de pago" }).click();
  await profe.locator("#llave").fill("300 123 4567");
  await profe.locator("#titular").fill("María Gómez");
  await profe.locator("#numero-documento").fill("1020304050");
  await profe.getByRole("button", { name: "Guardar el cambio" }).click();
  await expect(profe.getByText("••••4567")).toBeVisible();
  await expect(profe.getByText("3001234567")).toHaveCount(0);

  sembrarLiquidacionDeMaria();

  // 2. El admin la ve en la quincena, la aprueba, ve la llave completa y registra el pago.
  const admin = await (await browser.newContext()).newPage();
  await entrar(admin, SEMILLA.admin);
  await admin.goto("/admin/pagos");
  await admin.getByRole("button", { name: "Liquidaciones" }).click();
  await expect(admin.getByText(/Próximo corte/)).toBeVisible();
  await admin.getByRole("button", { name: "Ver la liquidación de María Gómez" }).click();
  const detalle = admin.getByRole("dialog", { name: "Liquidación de María Gómez" });
  await expect(detalle.getByText("Clase sembrada para el e2e")).toBeVisible();
  await detalle.getByRole("button", { name: "Aprobar" }).click();
  await expect(detalle.getByText("3001234567")).toBeVisible();
  await detalle.locator("#referencia").fill("BREB-E2E-1");
  await expect(detalle.getByRole("button", { name: "Registrar pago" })).toBeDisabled();
  await detalle.getByLabel("Confirmo que el nombre que mostró mi banco coincide con el titular.").check();
  await detalle.getByRole("button", { name: "Registrar pago" }).click();
  await expect(detalle.getByText(/Pagada el .* referencia BREB-E2E-1/)).toBeVisible();

  // 3. María la ve pagada en «Mis ganancias» y abre su comprobante.
  await profe.goto("/ganancias");
  await expect(profe.getByText("Orión recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena, menos la comisión.")).toBeVisible();
  await expect(profe.getByText(/^Pagada el /)).toBeVisible();
  await profe.getByRole("link", { name: "Comprobante" }).click();
  await expect(profe).toHaveURL(/\/comprobante\//);
  await expect(profe.getByRole("heading", { name: "Comprobante de liquidación" })).toBeVisible();
  await expect(profe.getByText("BREB-E2E-1")).toBeVisible();
  await expect(profe.getByText("••••4567")).toBeVisible();
  await expect(profe.getByRole("button", { name: "Imprimir o guardar como PDF" })).toBeVisible();
});
