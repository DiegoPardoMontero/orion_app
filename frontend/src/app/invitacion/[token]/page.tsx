import type { Metadata } from "next";
import Link from "next/link";
import { Star } from "lucide-react";
import { Constelacion, Wordmark } from "@/components/marca";
import { serverFetch } from "@/lib/api/server";

/**
 * La invitación personal al profesor (handoff `design_handoff_orion_invitacion`, 25/09/2026): lo
 * primero que ve el profe que recibe el enlace de Sofía. Pública y sin la navegación de la app.
 *
 * <p>El token se resuelve aquí, en el servidor. Vigente, muestra a quién se saluda, quién invita,
 * cuándo vence y el beneficio de profe fundador si lo trae; vencida o usada, solo el estado —el
 * backend no manda más—, y un token que no existe se ve igual que uno vencido. «Aceptar la
 * invitación» lleva al registro de profesor con el correo puesto y bloqueado; el token se consume
 * al crear la cuenta, no al abrir el enlace.
 *
 * <p>Rigel sale del SVG del paquete, sin redibujar, y Meissa no aparece: nunca comparten pantalla.
 */
export const metadata: Metadata = {
  title: "Tu invitación · Orión",
  robots: { index: false, follow: false },
};

type Invitacion = {
  state: "VALID" | "EXPIRED" | "USED";
  email: string | null;
  professorName: string | null;
  invitedByName: string | null;
  invitedByTitle: string | null;
  expiresAt: string | null;
  founder: { rateBps: number; periodMonths: number; baseRateBps: number } | null;
};

/** «9 de octubre», en Bogotá. */
function diaYMes(iso: string) {
  return new Intl.DateTimeFormat("es-CO", { day: "numeric", month: "long", timeZone: "America/Bogota" }).format(
    new Date(iso),
  );
}

const porcentaje = (bps: number) => `${bps / 100} %`;
const meses = (n: number) => (n === 1 ? "tu primer mes" : `tus primeros ${n} meses`);

export default async function InvitacionPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  // Sin caché: el mismo enlace pasa de vigente a usado en cuanto se crea la cuenta.
  const invitacion =
    (await serverFetch<Invitacion>(`/api/v1/auth/invite?token=${encodeURIComponent(token)}`, 0)) ??
    ({ state: "EXPIRED" } as Invitacion);

  const vigente = invitacion.state === "VALID";
  const usada = invitacion.state === "USED";
  const nombre = invitacion.professorName?.trim();

  const titular = vigente
    ? nombre
      ? `${nombre}, queremos que seas de los primeros profes de Orión.`
      : "Queremos que seas de los primeros profes de Orión."
    : usada
      ? "Esta invitación ya se usó."
      : "Esta invitación ya venció.";

  return (
    <main className="flex min-h-dvh flex-col bg-surface text-text lg:grid lg:grid-cols-[520px_1fr]">
      {/* El amanecer: arriba en el celular (300 px), panel izquierdo a todo el alto en escritorio. */}
      <div className="gradient-dawn relative h-[300px] shrink-0 overflow-hidden lg:h-auto lg:min-h-dvh">
        <Constelacion className="pointer-events-none absolute left-6 top-14 h-[110px] w-[110px] opacity-35 lg:left-12 lg:top-24 lg:h-[180px] lg:w-[180px]" />
        <Constelacion className="pointer-events-none absolute right-6 top-24 h-[80px] w-[80px] -scale-x-100 opacity-35 lg:right-16 lg:top-36 lg:h-[130px] lg:w-[130px]" />
        <Link
          href="/"
          className="absolute left-5 top-[22px] whitespace-nowrap rounded-base focus-visible:shadow-focus lg:left-12 lg:top-[30px]"
        >
          <Wordmark className="text-[16px] text-on-primary lg:text-[18px]" />
        </Link>
        {/* eslint-disable-next-line @next/next/no-img-element -- el SVG del paquete, tal cual */}
        <img
          src="/rigel/rigel-saluda.svg"
          alt=""
          aria-hidden="true"
          className="absolute left-1/2 top-[78px] w-[160px] -translate-x-1/2 lg:top-[300px] lg:w-[260px]"
        />
        <p className="absolute bottom-11 left-12 hidden font-display text-[22px] font-bold text-text lg:block">
          Find your right teacher, learn your way
        </p>
      </div>

      <div className="flex flex-1 flex-col lg:items-center lg:justify-center lg:px-20">
        <div className="flex flex-1 flex-col gap-[18px] px-5 pb-4 pt-6 lg:w-full lg:max-w-[720px] lg:flex-none lg:gap-[26px] lg:p-0">
          {vigente && (
            <span className="inline-flex w-fit items-center gap-1.5 whitespace-nowrap rounded-pill bg-accent-lavender-soft px-3 py-1.5 text-[12px] font-bold text-lavanda-ink">
              <Star size={14} strokeWidth={2.2} aria-hidden="true" />
              Invitación personal · Profes fundadores
            </span>
          )}

          <h1 className="text-balance font-display text-[30px] font-bold leading-[1.1] lg:text-[46px]">{titular}</h1>

          {vigente ? (
            <>
              <p className="text-[16px] leading-[1.55] text-text lg:text-[18px]">
                Estamos lanzando Orión y abrimos las puertas a un grupo pequeño de profesores. Nos alegra mucho contar
                contigo desde el comienzo.
              </p>

              {/* El beneficio real de ser fundador, con los números de la API (brief del profe fundador). */}
              {invitacion.founder && (
                <div className="rounded-card border border-border bg-surface-raised px-4 py-3.5 lg:px-5 lg:py-4">
                  <p className="text-[15px] font-semibold leading-[1.5] text-text lg:text-[16px]">
                    La comisión de Orión es {porcentaje(invitacion.founder.baseRateBps)}. Como profe fundador, tienes{" "}
                    {porcentaje(invitacion.founder.rateBps)} durante {meses(invitacion.founder.periodMonths)} de
                    clases.
                  </p>
                  <p className="mt-1 text-[13px] leading-[1.5] text-text-muted lg:text-[14px]">
                    {invitacion.founder.periodMonths === 1 ? "El mes empieza" : `Los ${invitacion.founder.periodMonths} meses empiezan`}{" "}
                    a contar desde tu primera clase pagada.
                  </p>
                </div>
              )}

              {invitacion.invitedByName && (
                <p className="flex items-center gap-3 text-[14px] text-text-secondary lg:text-[16px]">
                  <span
                    aria-hidden="true"
                    className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-accent-peach font-display text-[15px] font-extrabold text-text lg:h-11 lg:w-11 lg:text-[18px]"
                  >
                    {invitacion.invitedByName.trim().charAt(0).toUpperCase()}
                  </span>
                  <span className="whitespace-nowrap">
                    Te invita <strong className="font-bold text-text">{invitacion.invitedByName}</strong>
                    {invitacion.invitedByTitle ? `, ${invitacion.invitedByTitle}` : ""}
                  </span>
                </p>
              )}
            </>
          ) : (
            <p className="text-[16px] leading-[1.55] text-text lg:text-[18px]">
              Escríbele a quien te invitó y te enviamos un enlace nuevo.
            </p>
          )}

          {/* El pie: pegado abajo en el celular; debajo del texto en escritorio. */}
          {(vigente || usada) && (
            <div className="sticky bottom-0 -mx-5 mt-auto bg-surface px-5 pb-7 pt-4 lg:static lg:mx-0 lg:mt-0 lg:bg-transparent lg:p-0">
              <Link
                href={vigente ? `/registro?invitacion=${encodeURIComponent(token)}` : "/login"}
                className="flex h-[52px] w-full items-center justify-center whitespace-nowrap rounded-pill bg-primary px-[34px] text-[15px] font-bold text-on-primary shadow-[0_10px_24px_rgba(232,80,58,.35)] transition-colors hover:bg-primary-strong focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-text lg:h-14 lg:w-fit"
              >
                {vigente ? "Aceptar la invitación" : "Inicia sesión"}
              </Link>
              {vigente && invitacion.expiresAt && (
                <p className="mt-3 text-center text-[12px] leading-relaxed text-text-muted lg:text-left lg:text-[13px]">
                  Es solo para ti y vence el {diaYMes(invitacion.expiresAt)}.{" "}
                  <span className="whitespace-nowrap">
                    ¿Ya tienes cuenta?{" "}
                    <Link
                      href="/login"
                      className="rounded-base font-bold text-primary-strong underline underline-offset-2 focus-visible:shadow-focus"
                    >
                      Inicia sesión
                    </Link>
                  </span>
                </p>
              )}
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
