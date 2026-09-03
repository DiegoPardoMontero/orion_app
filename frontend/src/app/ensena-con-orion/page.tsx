import type { Metadata } from "next";
import {
  BadgeCheck,
  CalendarClock,
  ClipboardCheck,
  FileCheck2,
  Percent,
  Send,
  UserPlus,
  Users,
  Wallet,
} from "lucide-react";
import Link from "next/link";
import { Constelacion, Wordmark } from "@/components/marca";
import { EnsenaCta } from "@/components/EnsenaCta";
import { NavPublica } from "@/components/NavPublica";
import { Rigel } from "@/components/Rigel";
import { SITE_URL } from "@/lib/config";

/**
 * "Enseña en Orión": propuesta de valor para profesores. Página pública, server-rendered (SEO), sin
 * datos dinámicos —solo la isla `EnsenaCta` decide el destino según la sesión—. La comisión del 20%
 * se dice DE FRENTE, no en letra chica: es un compromiso de transparencia con el profesor.
 */
export const metadata: Metadata = {
  title: "Enseña en Orión · Construye tu agenda de clases de idiomas",
  description:
    "Publica tu perfil de profesor de idiomas, define tus horarios y recibe estudiantes reales. Tú pones la tarifa; Orión retiene una comisión del 20%. Sin cuotas por adelantado.",
  alternates: { canonical: "/ensena-con-orion" },
  openGraph: {
    title: "Enseña en Orión",
    description:
      "Publica tu perfil, define tus horarios y recibe estudiantes reales. Comisión transparente del 20%, sin cuotas por adelantado.",
    type: "website",
    images: [{ url: "/og.png", width: 1200, height: 630, alt: "Enseña en Orión" }],
  },
};

const BENEFICIOS = [
  {
    icono: CalendarClock,
    titulo: "Tu horario, tus reglas",
    texto: "Abres solo los cupos que quieras. Los estudiantes reservan; tú confirmas y das la clase.",
  },
  {
    icono: Users,
    titulo: "Estudiantes reales",
    texto: "Tu perfil aparece en el marketplace y en las landings por idioma. Sin buscar clientes por tu cuenta.",
  },
  {
    icono: Wallet,
    titulo: "Tú pones la tarifa",
    texto: "Defines cuánto cobras por hora. Verás el desglose con claridad antes de publicar tu perfil.",
  },
];

const REQUISITOS = [
  "Dominio comprobable del idioma que enseñas (nativo o certificado).",
  "Hoja de vida. Los certificados de enseñanza son opcionales.",
  "Compromiso de puntualidad, respeto y profesionalismo en cada clase.",
  "Conexión estable para las clases virtuales (o un espacio para las presenciales).",
];

const PASOS = [
  {
    icono: UserPlus,
    titulo: "Crea tu cuenta",
    texto: "Regístrate gratis y abre tu postulación de profesor desde tu panel.",
  },
  {
    icono: FileCheck2,
    titulo: "Completa tu perfil",
    texto: "Cuéntanos qué enseñas, tu experiencia y sube tus documentos. Aceptas el acuerdo del profesor.",
  },
  {
    icono: Send,
    titulo: "Envía a revisión",
    texto: "Nuestro equipo revisa tu postulación y te avisa. Podemos pedirte ajustes si hace falta.",
  },
  {
    icono: ClipboardCheck,
    titulo: "Publica y recibe reservas",
    texto: "Al aprobarte, publicas tu perfil, abres tu disponibilidad y empiezas a recibir estudiantes.",
  },
];

export default function EnsenaConOrionPage() {
  return (
    <div className="flex-1">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            "@context": "https://schema.org",
            "@type": "WebPage",
            name: "Enseña en Orión",
            url: `${SITE_URL}/ensena-con-orion`,
            inLanguage: "es-CO",
            description:
              "Propuesta para profesores de idiomas: publica tu perfil, define tus horarios y recibe estudiantes. Comisión del 20%.",
            publisher: {
              "@type": "Organization",
              name: "Orión Idiomas",
              url: SITE_URL,
            },
          }),
        }}
      />

      <NavPublica />

      {/* — Hero — */}
      <header className="gradient-dawn relative overflow-hidden">
        <Constelacion className="pointer-events-none absolute -right-10 top-6 h-[220px] w-[220px] opacity-60 lg:h-[340px] lg:w-[340px]" />
        <div className="relative mx-auto max-w-5xl px-5 py-14 lg:px-8 lg:py-20">
          <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr] lg:items-center">
            <div>
              <p className="text-[12px] font-bold uppercase tracking-[0.16em] text-on-primary/80">
                Para profesores de idiomas
              </p>
              <h1 className="mt-3 max-w-[20ch] font-display text-[32px] font-bold leading-[1.1] text-on-primary lg:text-[48px]">
                Enseña idiomas. Construye tu agenda en Orión.
              </h1>
              <p className="mt-4 max-w-[52ch] text-[15px] leading-relaxed text-on-primary/85 lg:text-[17px]">
                Publica tu perfil, define tus horarios y recibe estudiantes reales que quieren
                aprender. Tú pones la tarifa; nosotros ponemos la plataforma.
              </p>
              <div className="mt-8">
                <EnsenaCta />
              </div>
            </div>
            <div className="flex justify-center lg:justify-end">
              <Rigel pose="guia" className="h-[180px] w-auto drop-shadow-2xl lg:h-[260px]" />
            </div>
          </div>
        </div>
      </header>

      {/* — Beneficios — */}
      <section className="mx-auto max-w-5xl px-5 py-16 lg:px-8 lg:py-24">
        <div className="text-center">
          <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
            Qué ganas
          </p>
          <h2 className="mt-3 font-display text-h2 font-bold">Lo tuyo es enseñar. Lo demás lo ponemos nosotros.</h2>
        </div>
        <div className="mt-10 grid gap-5 md:grid-cols-3">
          {BENEFICIOS.map((beneficio, i) => {
            const Icono = beneficio.icono;
            return (
              <div key={i} className="rounded-card bg-surface-raised p-6 shadow-sm">
                <div className="flex h-12 w-12 items-center justify-center rounded-base bg-primary-soft text-primary-strong">
                  <Icono size={22} strokeWidth={1.75} />
                </div>
                <p className="mt-4 font-display text-[18px] font-bold">{beneficio.titulo}</p>
                <p className="mt-1.5 text-[14px] leading-relaxed text-text-secondary">{beneficio.texto}</p>
              </div>
            );
          })}
        </div>
      </section>

      {/* — Comisión 20%, de frente — */}
      <section className="mx-auto max-w-5xl px-5 pb-16 lg:px-8 lg:pb-24">
        <div className="grid items-center gap-6 rounded-card bg-night px-7 py-10 text-text-on-night lg:grid-cols-[auto_1fr] lg:px-12 lg:py-12">
          <div className="flex items-center gap-4">
            <span className="grid h-16 w-16 shrink-0 place-items-center rounded-full bg-accent-peach text-night">
              <Percent size={30} strokeWidth={2.2} />
            </span>
            <p className="font-display text-[40px] font-extrabold leading-none text-on-primary lg:text-[52px]">
              20%
            </p>
          </div>
          <div>
            <h2 className="font-display text-h3 font-bold text-on-primary">Comisión clara, sin sorpresas.</h2>
            <p className="mt-2 text-[15px] leading-relaxed text-on-primary/85">
              Orión retiene una comisión del <strong className="font-bold text-accent-peach">20%</strong>{" "}
              sobre tu tarifa por cada clase reservada. Lo demás es tuyo. Sin cuotas por adelantado ni
              costos ocultos: verás el desglose completo antes de publicar tu perfil.
            </p>
          </div>
        </div>
      </section>

      {/* — Requisitos — */}
      <section className="bg-surface-sunken/50 py-16 lg:py-24">
        <div className="mx-auto max-w-3xl px-5 lg:px-8">
          <div className="text-center">
            <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
              Requisitos
            </p>
            <h2 className="mt-3 font-display text-h2 font-bold">Lo que buscamos en un profesor.</h2>
          </div>
          <ul className="mx-auto mt-8 max-w-xl space-y-3">
            {REQUISITOS.map((requisito, i) => (
              <li
                key={i}
                className="flex items-start gap-3 rounded-card bg-surface-raised px-5 py-4 text-[14.5px] leading-relaxed text-text shadow-sm"
              >
                <BadgeCheck size={20} strokeWidth={2} className="mt-0.5 shrink-0 text-success" />
                {requisito}
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* — Paso a paso de la solicitud — */}
      <section className="mx-auto max-w-5xl px-5 py-16 lg:px-8 lg:py-24">
        <div className="text-center">
          <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
            Cómo postularte
          </p>
          <h2 className="mt-3 font-display text-h2 font-bold">De la solicitud a tu primera reserva.</h2>
        </div>
        <ol className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {PASOS.map((paso, i) => {
            const Icono = paso.icono;
            return (
              <li key={i} className="rounded-card bg-surface-raised p-6 shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="flex h-12 w-12 items-center justify-center rounded-base bg-primary-soft text-primary-strong">
                    <Icono size={22} strokeWidth={1.75} />
                  </span>
                  <span className="font-display text-[28px] font-extrabold text-border-strong">
                    {i + 1}
                  </span>
                </div>
                <p className="mt-4 font-display text-[17px] font-bold">{paso.titulo}</p>
                <p className="mt-1.5 text-[13.5px] leading-relaxed text-text-secondary">{paso.texto}</p>
              </li>
            );
          })}
        </ol>
      </section>

      {/* — CTA final — */}
      <section className="gradient-dawn">
        <div className="mx-auto max-w-3xl px-5 py-16 text-center lg:py-20">
          <h2 className="text-balance font-display text-h1 font-bold text-on-primary">
            Empieza a enseñar en Orión.
          </h2>
          <p className="mx-auto mt-3 max-w-[46ch] text-[15px] text-on-primary/85">
            Crea tu cuenta, completa tu postulación y publica tu perfil cuando te aprobemos.
          </p>
          <div className="mt-8 flex justify-center">
            <EnsenaCta />
          </div>
        </div>
      </section>

      {/* — Footer — */}
      <footer className="border-t border-border bg-surface">
        <div className="mx-auto flex max-w-5xl flex-col items-center gap-4 px-5 py-10 text-center sm:flex-row sm:justify-between sm:text-left lg:px-8">
          <Wordmark className="text-[15px] text-primary-strong" />
          <div className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-[13.5px] font-semibold text-text-secondary">
            <Link href="/" className="hover:text-text">
              Inicio
            </Link>
            <Link href="/profesores" className="hover:text-text">
              Encuentra un profesor
            </Link>
            <Link href="/login" className="hover:text-text">
              Iniciar sesión
            </Link>
          </div>
        </div>
        <p className="pb-8 text-center text-[12px] text-text-muted">
          © 2026 Orión Idiomas · Términos y privacidad, próximamente.
        </p>
      </footer>
    </div>
  );
}
