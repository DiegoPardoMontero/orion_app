import type { Metadata } from "next";
import { CalendarCheck, MessageCircle, UserRoundSearch } from "lucide-react";
import Link from "next/link";
import { HeroCta } from "@/components/HeroCta";
import { Constelacion, Wordmark } from "@/components/marca";
import { Rigel } from "@/components/Rigel";
import { whatsappSoporte } from "@/lib/config";

/**
 * Landing pública de marketing. EXCEPCIÓN CONSCIENTE a la regla de "todo client component": esta
 * página es pública y el SEO importa, así que se renderiza en el servidor (es estática salvo la
 * isla `HeroCta`, que decide el CTA según la sesión). Las pantallas con sesión siguen siendo
 * client components; esta no lo es a propósito.
 */
export const metadata: Metadata = {
  title: "Orión · Aprende inglés con confianza",
  description:
    "Academia de inglés conversacional para adultos que ya saben inglés pero aún no se atreven a hablarlo. Reserva clases en línea con profesores reales, a tu ritmo.",
  openGraph: {
    title: "Orión · Aprende inglés con confianza",
    description:
      "Clases de inglés conversacional con profesores reales. Para adultos que quieren pasar de entender a hablar.",
    type: "website",
    images: [{ url: "/og.png", width: 1200, height: 630, alt: "Orión — aprende inglés con confianza" }],
  },
};

const METODO = [
  { letra: "O", nombre: "Observe", texto: "Escucha y absorbe el inglés real, no el de los libros." },
  { letra: "R", nombre: "Relate", texto: "Conecta lo que aprendes con tu vida y tu trabajo." },
  { letra: "I", nombre: "Interact", texto: "Habla desde el primer minuto, sin miedo a equivocarte." },
  { letra: "O", nombre: "Optimize", texto: "Refina con la guía de tu profesor, clase a clase." },
  { letra: "N", nombre: "Navigate", texto: "Avanza a tu ritmo hacia la confianza que buscas." },
];

const PASOS = [
  {
    icono: UserRoundSearch,
    titulo: "Crea tu cuenta",
    texto: "Gratis y en un minuto. Sin tarjetas ni compromisos.",
  },
  {
    icono: CalendarCheck,
    titulo: "Elige tu profesor",
    texto: "Mira su perfil, su especialidad y sus horarios disponibles.",
  },
  {
    icono: MessageCircle,
    titulo: "Reserva tu clase",
    texto: "A la hora que te sirva. Coordinan el resto por WhatsApp.",
  },
];

export default function LandingPage() {
  const whatsapp = whatsappSoporte("Hola, quiero saber más sobre las clases de Orión.");

  return (
    <div className="flex-1">
      {/* — Hero — */}
      <header className="gradient-dawn relative overflow-hidden">
        <Constelacion className="pointer-events-none absolute -right-10 top-6 h-[220px] w-[220px] opacity-60 lg:h-[360px] lg:w-[360px]" />
        <div className="relative mx-auto flex max-w-5xl flex-col gap-8 px-6 py-14 lg:flex-row lg:items-center lg:gap-12 lg:px-8 lg:py-20">
          <div className="lg:flex-1">
            <Wordmark className="text-[17px] text-on-primary" />
            <h1 className="mt-6 max-w-[16ch] font-display text-[34px] font-bold leading-[1.08] text-on-primary lg:text-[52px]">
              Learn with confidence. Transform your opportunities.
            </h1>
            <p className="mt-4 max-w-[48ch] text-[15px] leading-relaxed text-on-primary/85 lg:text-[17px]">
              Clases de inglés conversacional con profesores reales, pensadas para adultos que ya
              saben inglés pero aún no se atreven a hablarlo. Reserva cuando quieras y practica sin
              miedo.
            </p>
            <div className="mt-8">
              <HeroCta />
            </div>
          </div>
          <div className="flex justify-center lg:flex-1">
            <Rigel pose="saludo" className="h-[220px] w-auto drop-shadow-2xl lg:h-[320px]" />
          </div>
        </div>
      </header>

      {/* — Qué es Orión — */}
      <section className="mx-auto max-w-3xl px-6 py-16 text-center lg:py-24">
        <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
          Qué es Orión
        </p>
        <h2 className="mt-3 text-balance font-display text-h2 font-bold">
          Una academia de conversación, no otra app de gramática.
        </h2>
        <p className="mt-4 text-pretty text-[16px] leading-relaxed text-text-secondary lg:text-[17px]">
          Orión nació de una idea sencilla: la mayoría de los adultos sabe más inglés del que se
          atreve a usar. Somos una comunidad de profesores que te acompañan a cruzar ese puente —de
          entender a hablar— con clases en vivo, cercanas y a tu ritmo.
        </p>
      </section>

      {/* — Método ORION® — */}
      <section className="bg-surface-sunken/60 py-16 lg:py-24">
        <div className="mx-auto max-w-5xl px-6 lg:px-8">
          <div className="text-center">
            <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
              El método
            </p>
            <h2 className="mt-3 font-display text-h2 font-bold">
              El Método ORION<span className="align-super text-[0.6em]">®</span>
            </h2>
            <p className="mx-auto mt-3 max-w-[52ch] text-[15px] text-text-secondary">
              Cinco movimientos que repites en cada clase hasta que hablar deja de dar miedo.
            </p>
          </div>
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            {METODO.map((paso, i) => (
              <div
                key={i}
                className="flex flex-col items-center rounded-card bg-surface-raised p-6 text-center shadow-sm"
              >
                <span className="gradient-avatar grid h-14 w-14 place-items-center rounded-full font-display text-[24px] font-extrabold text-on-primary">
                  {paso.letra}
                </span>
                <p className="mt-3 font-display text-[17px] font-bold">{paso.nombre}</p>
                <p className="mt-1.5 text-[13.5px] leading-relaxed text-text-secondary">{paso.texto}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* — Cómo funciona — */}
      <section className="mx-auto max-w-5xl px-6 py-16 lg:px-8 lg:py-24">
        <div className="text-center">
          <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
            Cómo funciona
          </p>
          <h2 className="mt-3 font-display text-h2 font-bold">Tu primera clase, en tres pasos.</h2>
        </div>
        <div className="mt-10 grid gap-6 md:grid-cols-3">
          {PASOS.map((paso, i) => {
            const Icono = paso.icono;
            return (
              <div key={i} className="text-center md:text-left">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-base bg-primary-soft text-primary-strong md:mx-0">
                  <Icono size={22} strokeWidth={1.75} />
                </div>
                <p className="mt-4 font-display text-[19px] font-bold">
                  <span className="text-primary">{i + 1}.</span> {paso.titulo}
                </p>
                <p className="mt-1.5 text-[15px] leading-relaxed text-text-secondary">{paso.texto}</p>
              </div>
            );
          })}
        </div>
      </section>

      {/* — Confianza comunicativa — */}
      <section className="mx-auto max-w-5xl px-6 pb-16 lg:px-8 lg:pb-24">
        <div className="grid items-center gap-8 rounded-card bg-night px-7 py-12 text-text-on-night lg:grid-cols-[1.4fr_1fr] lg:px-14 lg:py-16">
          <div>
            <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent-peach">
              El diferenciador
            </p>
            <h2 className="mt-3 font-display text-h2 font-bold text-on-primary">
              El problema no es cuánto inglés sabes. Es cuánto te atreves a usar.
            </h2>
            <p className="mt-4 text-[15px] leading-relaxed text-on-primary/85 lg:text-[16px]">
              Orión está hecho para ese salto: de entender a hablar, de dudar a fluir. Cada clase es
              una oportunidad de practicar en confianza, con alguien que te corrige sin juzgarte.
              Muy pronto tu <strong className="font-bold text-accent-peach">Confidence Score®</strong>{" "}
              te mostrará ese avance, clase a clase —lo estamos construyendo contigo.
            </p>
          </div>
          {/* Motivo de marca (no mascota): el presupuesto de densidad reserva el único
              Protagonista/Rigel de la landing para el hero (guía de mascota + decisión 4). */}
          <div className="flex justify-center">
            <Constelacion className="h-[160px] w-auto opacity-90 lg:h-[200px]" />
          </div>
        </div>
      </section>

      {/*
        Testimonios: sección deliberadamente OCULTA hasta tener citas reales y autorizadas de
        estudiantes (decisión 6 del minibrief). Publicar testimonios ficticios daña la confianza.
        Cuando Sofía entregue las citas, se quita el `hidden` y se llena.
      */}
      <section className="hidden" aria-hidden="true">
        {/* Maquetada, pendiente de testimonios reales. */}
      </section>

      {/* — CTA final + footer — */}
      <section className="gradient-dawn">
        <div className="mx-auto max-w-3xl px-6 py-16 text-center lg:py-20">
          <h2 className="font-display text-h1 font-bold text-on-primary">
            Tu inglés está a punto de amanecer.
          </h2>
          <p className="mx-auto mt-3 max-w-[46ch] text-[15px] text-on-primary/85">
            Crea tu cuenta gratis y reserva tu primera clase hoy.
          </p>
          <div className="mt-8 flex justify-center">
            <HeroCta />
          </div>
        </div>
      </section>

      <footer className="border-t border-border bg-surface">
        <div className="mx-auto flex max-w-5xl flex-col items-center gap-4 px-6 py-10 text-center sm:flex-row sm:justify-between sm:text-left lg:px-8">
          <Wordmark className="text-[15px] text-primary" />
          <div className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-[13.5px] font-semibold text-text-secondary">
            <Link href="/login" className="hover:text-text">
              Entrar
            </Link>
            <Link href="/registro" className="hover:text-text">
              Crear cuenta
            </Link>
            {whatsapp && (
              <a
                href={whatsapp}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 text-success hover:underline"
              >
                <MessageCircle size={15} strokeWidth={1.75} />
                Escríbenos
              </a>
            )}
          </div>
        </div>
        <p className="pb-8 text-center text-[12px] text-text-muted">
          © 2026 Orión Idiomas · Términos y privacidad, próximamente.
        </p>
      </footer>
    </div>
  );
}
