import type { Metadata } from "next";
import {
  ArrowRight,
  Briefcase,
  CalendarCheck,
  ChevronRight,
  Compass,
  GraduationCap,
  Heart,
  MessageCircle,
  Plane,
  ShieldCheck,
  Sparkles,
  Star,
  Target,
  TrendingUp,
  UserRoundSearch,
} from "lucide-react";
import Link from "next/link";
import type { CSSProperties } from "react";
import { Avatar } from "@/components/Avatar";
import { BuscadorHero } from "@/components/BuscadorHero";
import { EnsenaCta } from "@/components/EnsenaCta";
import { HeroCta } from "@/components/HeroCta";
import { Constelacion, Wordmark } from "@/components/marca";
import { NavPublica } from "@/components/NavPublica";
import { EstrellaRating } from "@/components/Rating";
import { Rigel } from "@/components/Rigel";
import { serverFetch } from "@/lib/api/server";
import type {
  GoalResponse,
  LanguageResponse,
  PagedProfessors,
  ProfessorCard,
} from "@/lib/api/types";
import { SITE_URL, whatsappSoporte } from "@/lib/config";
import { esGratis, tarifaClase } from "@/lib/format";

/**
 * Portada del marketplace. Server component (SEO + rendimiento): estática salvo las islas cliente
 * —`NavPublica`, `BuscadorHero`, `HeroCta`—. Los datos de catálogo y los profesores destacados se
 * resuelven en el servidor con `serverFetch` (ISR, revalidación cada 5 min). Datos SIEMPRE honestos:
 * nada de ratings ni contadores inventados, y la sección de profesores se OCULTA si hay menos de 4.
 */
export const metadata: Metadata = {
  title: "Orión · Encuentra al profesor de idiomas indicado",
  description:
    "Marketplace de profesores de inglés, francés y español. Reserva clases en vivo con profesores reales y aprende a tu manera: para el trabajo, los viajes, el estudio o la vida en el exterior.",
  alternates: { canonical: "/" },
  openGraph: {
    title: "Orión · Encuentra al profesor de idiomas indicado",
    description:
      "Profesores reales de inglés, francés y español. Reserva clases en vivo, a tu ritmo, y aprende para lo que te importa.",
    type: "website",
    images: [{ url: "/og.png", width: 1200, height: 630, alt: "Orión — marketplace de idiomas" }],
  },
};

/** Idiomas destacados en portada. Enlazan a la landing pública por idioma (código en mayúsculas). */
const IDIOMAS_DESTACADOS = [
  { code: "EN", nombre: "Inglés", emoji: "🇬🇧", texto: "El idioma del trabajo, los viajes y las oportunidades." },
  { code: "FR", nombre: "Francés", emoji: "🇫🇷", texto: "Para estudiar, migrar o reencontrarte con la cultura." },
  { code: "ES", nombre: "Español", emoji: "🇪🇸", texto: "Perfecciona tu español o apréndelo desde cero." },
];

const PASOS = [
  { icono: UserRoundSearch, titulo: "Encuentra", texto: "Filtra por idioma, objetivo u horario. Compara perfiles reales." },
  { icono: CalendarCheck, titulo: "Reserva", texto: "Elige un cupo disponible y confírmalo en segundos." },
  { icono: MessageCircle, titulo: "Aprende", texto: "Toma tu clase en vivo por videollamada. Todo se coordina dentro de Orión." },
  { icono: TrendingUp, titulo: "Avanza", texto: "Vuelve con el mismo profesor y construye una rutina." },
];

/**
 * Lo que sostiene la promesa, contado sin cifras que no podamos respaldar: cada punto describe algo
 * que la plataforma hace de verdad hoy, no una aspiración.
 */
const NOSOTROS = [
  {
    icono: ShieldCheck,
    titulo: "Revisamos a cada profesor",
    texto: "Nadie aparece en el directorio sin pasar por una postulación que revisa nuestro equipo: hoja de vida, formación y experiencia.",
  },
  {
    icono: Sparkles,
    titulo: "Clases en vivo, nunca grabadas",
    texto: "Sesenta minutos con una persona al otro lado que prepara la clase para ti. Sin videos enlatados ni ejercicios automáticos.",
  },
  {
    icono: Star,
    titulo: "Reseñas de quien sí tomó la clase",
    texto: "Solo puede calificar quien asistió. La nota que ves viene de estudiantes reales, no de un formulario abierto a cualquiera.",
  },
  {
    icono: MessageCircle,
    titulo: "Todo ocurre en un solo sitio",
    texto: "Reservas, pagos, mensajes y calendario viven dentro de Orión. No hay que perseguir a nadie por otro canal.",
  },
];

/** Íconos para las tarjetas de objetivo, por código conocido; genérico como respaldo. */
const ICONO_OBJETIVO: Record<string, typeof Target> = {
  WORK: Briefcase,
  BUSINESS: Briefcase,
  TRAVEL: Plane,
  STUDY: GraduationCap,
  ACADEMIC: GraduationCap,
  CONVERSATION: MessageCircle,
  LIVING_ABROAD: Compass,
  RELOCATION: Compass,
  PERSONAL_GROWTH: Heart,
  PERSONAL: Heart,
};

export default async function PortadaPage() {
  const [paged, languages, goals] = await Promise.all([
    serverFetch<PagedProfessors>("/api/v1/professors?size=4"),
    serverFetch<LanguageResponse[]>("/api/v1/catalog/languages"),
    serverFetch<GoalResponse[]>("/api/v1/catalog/goals"),
  ]);

  const profesores = paged?.content ?? [];
  // Regla de honestidad del brief: mostrar la sección SOLO si hay al menos 4 profesores publicados.
  const mostrarProfesores = profesores.length >= 4;
  const idiomas = languages ?? [];
  const objetivos = goals ?? [];
  // Seis en portada: la rejilla es de tres columnas y el séptimo dejaba una fila con una sola
  // tarjeta suelta. Los demás siguen estando en los filtros del directorio.
  const objetivosDestacados = objetivos.slice(0, 6);
  const whatsapp = whatsappSoporte("Hola, quiero saber más sobre las clases de Orión.");

  return (
    <div className="flex-1">
      <script
        type="application/ld+json"
        // JSON-LD honesto: identidad de la organización y su buscador. Sin ratings ni conteos falsos.
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            "@context": "https://schema.org",
            "@graph": [
              {
                "@type": "Organization",
                "@id": `${SITE_URL}/#organization`,
                name: "Orión Idiomas",
                url: SITE_URL,
                description:
                  "Marketplace de profesores de idiomas (inglés, francés y español) para clases en vivo.",
              },
              {
                "@type": "WebSite",
                "@id": `${SITE_URL}/#website`,
                url: SITE_URL,
                name: "Orión",
                publisher: { "@id": `${SITE_URL}/#organization` },
                inLanguage: "es-CO",
              },
            ],
          }),
        }}
      />

      <NavPublica />

      {/* — Hero — */}
      <header className="gradient-dawn relative overflow-hidden">
        <Constelacion className="pointer-events-none absolute -right-10 top-6 h-[220px] w-[220px] opacity-60 lg:h-[360px] lg:w-[360px]" />
        <div className="relative mx-auto max-w-6xl px-5 py-12 lg:px-8 lg:py-16">
          <div className="grid gap-10 lg:grid-cols-[1.35fr_1fr] lg:items-center">
            <div>
              <p className="text-[12px] font-bold uppercase tracking-[0.16em] text-on-primary/80">
                Find the right teacher, learn your way
              </p>
              <h1 className="mt-3 max-w-[20ch] font-display text-[34px] font-bold leading-[1.08] text-on-primary lg:text-[52px]">
                Encuentra al profesor indicado. Aprende a tu manera.
              </h1>
              <p className="mt-4 max-w-[52ch] text-[15px] leading-relaxed text-on-primary/85 lg:text-[17px]">
                Profesores reales de inglés, francés y español para clases en vivo. Compara perfiles,
                elige tu horario y reserva cuando quieras —a tu ritmo y sin miedo a equivocarte.
              </p>
            </div>
            <div className="flex justify-center lg:justify-end">
              <Rigel pose="saludo" className="h-[190px] w-auto drop-shadow-2xl lg:h-[280px]" />
            </div>
          </div>

          {/* Buscador de 3 campos */}
          <div className="mt-10">
            <BuscadorHero languages={idiomas} goals={objetivos} />
            <div className="mt-5">
              <HeroCta />
            </div>
          </div>
        </div>
      </header>

      {/* — Idiomas destacados — */}
      <section id="idiomas" className="mx-auto max-w-6xl scroll-mt-20 px-5 pb-10 pt-8 lg:px-8 lg:pb-12 lg:pt-10">
        <div className="text-center">
          <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
            Idiomas
          </p>
          <h2 className="mt-2 font-display text-h2 font-bold">Elige el idioma que quieres hablar.</h2>
          <p className="mx-auto mt-2 max-w-[54ch] text-[15px] text-text-secondary">
            Tres idiomas con profesores publicados hoy. Entra al que te interesa para ver quién
            enseña qué.
          </p>
        </div>
        <div className="mt-6 grid gap-4 md:grid-cols-3">
          {IDIOMAS_DESTACADOS.map((idioma, i) => (
            <Link
              key={idioma.code}
              href={`/idiomas/${idioma.code}`}
              style={{ "--i": i } as CSSProperties}
              className="aparece group flex flex-col rounded-card bg-surface-raised p-7 shadow-md transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-lg"
            >
              <span aria-hidden="true" className="text-[40px] leading-none">
                {idioma.emoji}
              </span>
              <p className="mt-4 font-display text-[22px] font-bold">{idioma.nombre}</p>
              <p className="mt-1.5 flex-1 text-[14px] leading-relaxed text-text-secondary">
                {idioma.texto}
              </p>
              <span className="mt-4 inline-flex items-center gap-1 text-[14px] font-bold text-primary-strong">
                Ver profesores
                <ChevronRight
                  size={16}
                  strokeWidth={2}
                  className="transition-transform group-hover:translate-x-0.5"
                />
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* — Conoce a los profesores (real, se oculta con <4) — */}
      {mostrarProfesores && (
        <section className="bg-surface-sunken/50 py-12 lg:py-16">
          <div className="mx-auto max-w-6xl px-5 lg:px-8">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
                  La comunidad
                </p>
                <h2 className="mt-2 font-display text-h2 font-bold">Conoce a los profesores.</h2>
                <p className="mt-2 max-w-[48ch] text-[15px] text-text-secondary">
                  Perfiles reales con su tarifa, sus idiomas y sus horarios a la vista.
                </p>
              </div>
              <Link
                href="/profesores"
                className="inline-flex h-11 items-center gap-1.5 rounded-pill border-[1.5px] border-primary px-5 text-[14px] font-bold text-primary-strong transition-colors hover:bg-primary-soft focus-visible:shadow-focus"
              >
                Ver todos
                <ChevronRight size={16} strokeWidth={2} />
              </Link>
            </div>
            <ul className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {profesores.map((profesor, i) => (
                <li key={profesor.id} className="aparece" style={{ "--i": i } as CSSProperties}>
                  <TarjetaProfesor profesor={profesor} />
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}

      {/* — Cómo funciona — */}
      <section id="como-funciona" className="mx-auto max-w-6xl scroll-mt-20 px-5 pb-12 pt-8 lg:px-8 lg:pb-16 lg:pt-10">
        <div className="text-center">
          <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
            Cómo funciona Orión
          </p>
          <h2 className="mt-2 font-display text-h2 font-bold">De encontrar a avanzar, en cuatro pasos.</h2>
          <p className="mx-auto mt-2 max-w-[54ch] text-[15px] text-text-secondary">
            Del primer filtro a la primera clase hay minutos, no trámites. Esto es todo el camino.
          </p>
        </div>

        {/*
          Los cuatro pasos encadenados. La animación se enciende paso a paso en bucle porque lo que
          cuesta explicar de un proceso no son las etapas sino que van en un orden: verlo recorrerse
          solo dice eso sin una línea de texto. Es CSS con retardos escalonados —ver `.paso-icono` en
          globals.css—, así que no hay temporizador que mantener ni estado que se desincronice.
        */}
        <ol className="mt-8 grid gap-x-2 gap-y-6 sm:grid-cols-2 lg:grid-cols-[repeat(4,1fr)]">
          {PASOS.map((paso, i) => {
            const Icono = paso.icono;
            return (
              <li
                key={i}
                className="relative flex flex-col items-center px-2 text-center"
                style={{ "--i": i } as CSSProperties}
              >
                {/*
                  La flecha vive en el paso ANTERIOR y apunta al siguiente; el último no la lleva.
                  Va del borde de un icono al del siguiente (los 34 px del radio más un respiro), no
                  de centro a centro: si no, la línea nace dentro del círculo.
                */}
                {i < PASOS.length - 1 && (
                  <span
                    aria-hidden="true"
                    className="pointer-events-none absolute top-7 hidden lg:flex lg:items-center"
                    style={{ left: "calc(50% + 34px)", width: "calc(100% - 68px)" }}
                  >
                    <span className="h-[2px] w-full rounded-full bg-border" />
                    {/*
                      La punta es un triángulo CSS colgado del borde derecho de la propia barra, así
                      que viaja con ella mientras crece. Como elemento aparte había que recortar el
                      contenedor para ocultarla al principio, y ese mismo recorte se la comía al
                      final.
                    */}
                    <span
                      className="paso-flecha absolute left-0 top-1/2 h-[2px] -translate-y-1/2 rounded-full bg-primary after:absolute after:right-0 after:top-1/2 after:-translate-y-1/2 after:border-y-[4.5px] after:border-l-[7px] after:border-y-transparent after:border-l-primary after:content-['']"
                      style={{ "--i": i } as CSSProperties}
                    />
                  </span>
                )}

                <span className="paso-icono relative z-10 grid h-14 w-14 place-items-center rounded-full bg-surface-sunken text-text-muted shadow-sm">
                  <Icono size={24} strokeWidth={1.9} />
                </span>

                <p className="mt-4 font-display text-[18px] font-bold">
                  <span className="text-primary-strong">{i + 1}.</span> {paso.titulo}
                </p>
                <p className="mt-1.5 max-w-[30ch] text-[14px] leading-relaxed text-text-secondary">
                  {paso.texto}
                </p>
              </li>
            );
          })}
        </ol>
      </section>

      {/* — Nosotros — */}
      <section id="nosotros" className="scroll-mt-20 bg-surface-sunken/60 py-12 lg:py-16">
        <div className="mx-auto max-w-6xl px-5 lg:px-8">
          <div className="text-center">
            <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
              Nosotros
            </p>
            <h2 className="mt-2 font-display text-h2 font-bold">
              Una academia, no un catálogo.
            </h2>
            <p className="mx-auto mt-2 max-w-[58ch] text-[15px] leading-relaxed text-text-secondary">
              Orión existe para que aprender un idioma deje de depender de la suerte con la que
              elegiste profesor. Nos ocupamos de que quien está al otro lado sepa enseñar, de que
              reservar sea cuestión de minutos y de que todo lo demás no te robe tiempo.
            </p>
          </div>

          <ul className="mt-8 grid gap-4 sm:grid-cols-2">
            {NOSOTROS.map((punto, i) => {
              const Icono = punto.icono;
              return (
                <li
                  key={i}
                  className="aparece flex gap-4 rounded-card bg-surface-raised p-6 shadow-sm"
                  style={{ "--i": i } as CSSProperties}
                >
                  <span className="grid h-11 w-11 shrink-0 place-items-center rounded-base bg-primary-soft text-primary-strong">
                    <Icono size={20} strokeWidth={1.9} />
                  </span>
                  <div>
                    <p className="font-display text-[17px] font-bold">{punto.titulo}</p>
                    <p className="mt-1.5 text-[14px] leading-relaxed text-text-secondary">
                      {punto.texto}
                    </p>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      </section>

      {/* — Aprende para lo que te importa — */}
      {objetivosDestacados.length > 0 && (
        <section className="mx-auto max-w-6xl px-5 py-12 lg:px-8 lg:py-16">
          <div className="text-center">
            <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
              Tu objetivo
            </p>
            <h2 className="mt-2 font-display text-h2 font-bold">Aprende para lo que te importa.</h2>
            <p className="mx-auto mt-2 max-w-[54ch] text-[15px] text-text-secondary">
              Cada objetivo filtra el directorio por quienes lo enseñan. Empieza por el tuyo.
            </p>
          </div>
          <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {objetivosDestacados.map((objetivo, i) => {
              const Icono = ICONO_OBJETIVO[objetivo.code ?? ""] ?? Target;
              return (
                <Link
                  key={objetivo.code}
                  href={`/profesores?goal=${objetivo.code}`}
                  style={{ "--i": i } as CSSProperties}
                  className="aparece group flex items-center gap-4 rounded-card bg-surface-raised p-5 shadow-sm transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-md"
                >
                  <span className="grid h-11 w-11 shrink-0 place-items-center rounded-base bg-accent-peach-soft text-[#8a5a33]">
                    <Icono size={20} strokeWidth={1.9} />
                  </span>
                  <span className="flex-1 font-display text-[16px] font-bold">{objetivo.nameEs}</span>
                  <ChevronRight
                    size={18}
                    strokeWidth={2}
                    className="text-text-muted transition-transform group-hover:translate-x-0.5"
                  />
                </Link>
              );
            })}
          </div>
        </section>
      )}

      {/* — Enseña en Orión — */}
      <section className="mx-auto max-w-6xl px-5 pb-12 lg:px-8 lg:pb-16">
        <div className="grid items-center gap-8 rounded-card bg-night px-7 py-12 text-text-on-night lg:grid-cols-[1.5fr_1fr] lg:px-14 lg:py-16">
          <div>
            <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent-peach">
              Para profesores
            </p>
            <h2 className="mt-3 font-display text-h2 font-bold text-on-primary">
              ¿Enseñas idiomas? Construye tu agenda en Orión.
            </h2>
            <p className="mt-4 text-[15px] leading-relaxed text-on-primary/85 lg:text-[16px]">
              Publica tu perfil, define tus horarios y recibe estudiantes reales. Tú pones la tarifa;
              nosotros ponemos la plataforma. Sin cuotas por adelantado.
            </p>
            {/* La acción principal es postularse; quien prefiera leer antes tiene la página
                completa al lado. Antes solo estaba el enlace, y un profesor decidido tenía que
                dar dos saltos para llegar al formulario. */}
            <div className="mt-7 flex flex-col gap-3 sm:flex-row sm:items-center">
              <EnsenaCta />
              <Link
                href="/ensena-con-orion"
                className="inline-flex h-[52px] items-center justify-center gap-2 rounded-pill border-[1.5px] border-on-primary/35 px-7 text-[15px] font-bold text-on-primary transition-colors hover:bg-on-primary/10 focus-visible:shadow-focus"
              >
                Cómo funciona
                <ArrowRight size={18} strokeWidth={1.9} />
              </Link>
            </div>
          </div>
          <div className="flex justify-center">
            <Rigel pose="animo" decorativo className="h-[170px] w-auto lg:h-[210px]" />
          </div>
        </div>
      </section>

      {/* — CTA final — */}
      <section className="gradient-dawn">
        <div className="mx-auto max-w-3xl px-5 py-12 text-center lg:py-16">
          <h2 className="text-balance font-display text-h1 font-bold text-on-primary">
            Tu camino con los idiomas empieza con el profesor indicado.
          </h2>
          <p className="mx-auto mt-3 max-w-[46ch] text-[15px] text-on-primary/85">
            Crea tu cuenta gratis y reserva tu primera clase hoy.
          </p>
          <div className="mt-8 flex justify-center">
            <HeroCta />
          </div>
        </div>
      </section>

      {/* — Footer — */}
      <footer className="border-t border-border bg-surface">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-4 px-5 py-10 text-center sm:flex-row sm:justify-between sm:text-left lg:px-8">
          <Wordmark className="text-[15px] text-primary-strong" />
          <div className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-[13.5px] font-semibold text-text-secondary">
            <Link href="/profesores" className="hover:text-text">
              Encuentra un profesor
            </Link>
            <Link href="/ensena-con-orion" className="hover:text-text">
              Enseña en Orión
            </Link>
            <Link href="/login" className="hover:text-text">
              Iniciar sesión
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

/** Tarjeta de profesor para la portada (server-rendered): datos reales, sin ratings inventados. */
function TarjetaProfesor({ profesor }: { profesor: ProfessorCard }) {
  return (
    <Link
      href={`/profesores/${profesor.id}`}
      className="flex h-full flex-col rounded-card bg-surface-raised p-5 shadow-md transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-lg"
    >
      <div className="flex items-start gap-3">
        <Avatar nombre={profesor.fullName ?? ""} fotoUrl={profesor.photoUrl} size="lg" />
        <div className="min-w-0 flex-1">
          <p className="truncate font-display text-[16px] font-bold">{profesor.fullName}</p>
          {profesor.headline && (
            <p className="mt-0.5 line-clamp-2 text-[13px] font-semibold text-text-secondary">
              {profesor.headline}
            </p>
          )}
          <div className="mt-1.5">
            <EstrellaRating ratingAvg={profesor.ratingAvg} ratingCount={profesor.ratingCount} />
          </div>
        </div>
      </div>
      {profesor.languages && profesor.languages.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {profesor.languages.map((idioma) => (
            <span
              key={idioma.code}
              className="inline-flex items-center gap-1 rounded-pill bg-surface-sunken px-2.5 py-1 text-[12px] font-semibold text-text-secondary"
            >
              {idioma.flagEmoji && <span aria-hidden="true">{idioma.flagEmoji}</span>}
              {idioma.nameEs}
            </span>
          ))}
        </div>
      )}
      <div className="mt-4 flex items-end justify-between gap-2 border-t border-border pt-4">
        {tarifaClase(profesor.hourlyRateCop) ? (
          <p className="font-display text-[17px] font-bold text-text">
            {tarifaClase(profesor.hourlyRateCop)}
            {!esGratis(profesor.hourlyRateCop) && (
              <span className="ml-1 text-[11px] font-semibold text-text-muted">/ hora</span>
            )}
          </p>
        ) : (
          <span className="text-[12.5px] text-text-muted">Tarifa por confirmar</span>
        )}
        <span className="inline-flex items-center gap-1 text-[13px] font-bold text-primary-strong">
          Ver agenda
          <ChevronRight size={15} strokeWidth={2} />
        </span>
      </div>
    </Link>
  );
}
