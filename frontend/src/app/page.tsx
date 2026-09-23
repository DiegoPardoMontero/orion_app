import type { Metadata } from "next";
import {
  ArrowRight,
  CalendarCheck,
  ChevronRight,
  MessageCircle,
  Search,
  Sparkles,
  UserRoundSearch,
} from "lucide-react";
import Link from "next/link";
import type { CSSProperties } from "react";
import { Avatar } from "@/components/Avatar";
import { Constelacion, Wordmark } from "@/components/marca";
import { NavPublica } from "@/components/NavPublica";
import { Redes } from "@/components/Redes";
import { BuscadorRapido } from "@/components/portada/BuscadorRapido";
import { PorQueOrion } from "@/components/portada/PorQueOrion";
import { PreguntasDeLaPortada } from "@/components/PreguntasFrecuentes";
import { EstrellaRating } from "@/components/Rating";
import { Rigel } from "@/components/Rigel";
import { serverFetch } from "@/lib/api/server";
import type { PagedProfessors, ProfessorCard, PublicFigures } from "@/lib/api/types";
import { minutos } from "@/lib/cifras";
import { REDES_SOCIALES, SITE_URL, whatsappSoporte } from "@/lib/config";
import { esGratis, tarifaClase } from "@/lib/format";

/**
 * Portada del marketplace, con la estructura y los textos que propuso Sofía el 23/09/2026 y las
 * decisiones de Pardo encima: el diagnóstico va SÍ o SÍ en el hero, el Método ORION lleva ™ (no ®)
 * y no promete que el profesor apruebe los ejercicios (aprueba el acta, y de ahí salen), el
 * catálogo se ve sin cuenta, y las cifras de empleabilidad llevan su fuente.
 *
 * <p>Server component (SEO + rendimiento), con islas cliente para el buscador, las pestañas y las
 * preguntas. Datos SIEMPRE honestos: nada de ratings ni contadores inventados, los plazos salen de
 * Ajustes, y la sección de profesores se OCULTA si hay menos de 4.
 */
export const metadata: Metadata = {
  title: "Orión · Encuentra tu profesor de inglés",
  description:
    "Clases de inglés en vivo, uno a uno, con profesores verificados. Empieza con un diagnóstico gratis, elige con quién y cuándo, y paga solo la clase que reservas.",
  alternates: { canonical: "/" },
  openGraph: {
    title: "Orión · Encuentra tu profesor de inglés",
    description:
      "Profesores de inglés verificados. Clases en vivo, a tu ritmo, sin permanencia ni renovación automática.",
    type: "website",
    images: [{ url: "/og.png", width: 1200, height: 630, alt: "Orión, clases de inglés en vivo" }],
  },
};

/** Los tres pasos, cada uno con su color: del amanecer a la noche, para que se lean como un avance. */
const pasos = (minutosDiagnostico: string) => [
  {
    icono: Sparkles,
    titulo: "Cuéntanos tu punto de partida.",
    texto: `Haz tu diagnóstico gratuito: en ${minutosDiagnostico} de conversación sabes cómo arrancas y tienes tres profesores elegidos para ti. Sin pagar nada.`,
    fondo: "bg-accent-peach-soft",
    tinta: "text-[#8a5a33]",
  },
  {
    icono: UserRoundSearch,
    titulo: "Elige a tu profesor.",
    texto: "Filtra por objetivo, horario y precio. Cada perfil muestra experiencia, tarifa y reseñas reales.",
    fondo: "bg-primary-soft",
    tinta: "text-primary-strong",
  },
  {
    icono: CalendarCheck,
    titulo: "Reserva y empieza.",
    texto: "Clase en vivo, uno a uno. Pagas solo la clase que reservaste.",
    fondo: "bg-accent-lavender-soft",
    tinta: "text-info",
  },
];

/**
 * El Método ORION™: el marco del seguimiento, no una metodología impuesta al profesor, que enseña
 * con su estilo y su material. «Optimizar» describe lo que el producto hace de verdad: el profesor
 * aprueba el acta de la clase y los ejercicios salen de ella; no hay una segunda revisión de los
 * ejercicios (Pardo, 23/09/2026).
 */
const METODO = [
  {
    letra: "O",
    palabra: "Observar",
    texto: "Tu profesor identifica tus oportunidades de mejora: la velocidad al hablar, la coherencia, la gramática, la pronunciación.",
    color: "#FFC189",
  },
  {
    letra: "R",
    palabra: "Relacionar",
    texto: "Tus clases se conectan con tu objetivo real. Si te preparas para una entrevista, practicas entrevistas; si vas a presentar el IELTS, trabajas sobre el examen.",
    color: "#E8764F",
  },
  {
    letra: "I",
    palabra: "Interactuar",
    texto: "Las clases son conversación, no teoría: practicas hablando con tu profesor sobre las situaciones que vas a enfrentar en tu vida real.",
    color: "#E8503A",
  },
  {
    letra: "O",
    palabra: "Optimizar",
    texto: "Al terminar la clase, tu profesor deja sus observaciones y aprueba el resumen; de ahí Orión arma ejercicios de práctica hechos para ti.",
    color: "#B9A7E6",
  },
  {
    letra: "N",
    palabra: "Navegar",
    texto: "Sales de cada clase sabiendo en qué trabajar hasta la próxima para mejorar.",
    color: "#5E4A8A",
  },
];

const METODO_PROFESOR = [
  { letra: "O", palabra: "Observar", texto: "Identificas las oportunidades de mejora de tu estudiante durante la clase." },
  { letra: "R", palabra: "Relacionar", texto: "Conectas lo que trabajan con el objetivo real por el que está aprendiendo." },
  { letra: "I", palabra: "Interactuar", texto: "La conversación es el centro de la clase, en su contexto." },
  {
    letra: "O",
    palabra: "Optimizar",
    texto: "Dictas tus observaciones en un minuto, revisas el resumen que armamos y lo publicas: los ejercicios de tu estudiante salen de ahí.",
  },
  { letra: "N", palabra: "Navegar", texto: "Defines en qué debe enfocarse hasta la próxima sesión." },
];

/**
 * Lo que sostiene la promesa, contado sin cifras que no podamos respaldar: cada punto describe algo
 * que la plataforma hace de verdad hoy, no una aspiración.
 */
const NOSOTROS = [
  {
    titulo: "Revisamos a cada profesor",
    texto: "Nadie aparece en el directorio sin pasar por una postulación que revisa nuestro equipo: hoja de vida, formación y experiencia.",
  },
  {
    titulo: "Clases en vivo, nunca grabadas",
    texto: "Cada clase es con una persona al otro lado que la prepara para ti. Sin videos enlatados.",
  },
  {
    titulo: "Reseñas de quien sí tomó la clase",
    texto: "Solo puede calificar quien asistió. La nota que ves viene de estudiantes reales, no de un formulario abierto a cualquiera.",
  },
  {
    titulo: "Todo ocurre en un solo sitio",
    texto: "Reservas, pagos, mensajes y calendario viven dentro de Orión. No hay que perseguir a nadie por otro canal.",
  },
];

export default async function PortadaPage() {
  const [paged, cifras] = await Promise.all([
    serverFetch<PagedProfessors>("/api/v1/professors?size=4"),
    serverFetch<PublicFigures>("/api/v1/catalog/figures"),
  ]);

  const profesores = paged?.content ?? [];
  // Regla de honestidad del brief: mostrar la sección SOLO si hay al menos 4 profesores publicados.
  const mostrarProfesores = profesores.length >= 4;
  // Lo que dura el diagnóstico sale de Ajustes, como en la pantalla del diagnóstico.
  const duracion = minutos(cifras?.assessmentMinutes ?? 2);
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
                description: "Clases de inglés en vivo, uno a uno, con profesores verificados.",
                sameAs: REDES_SOCIALES.map((red) => red.url),
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

      {/* — 1. Hero: el diagnóstico SÍ o SÍ (Pardo, 23/09/2026) — */}
      <header className="gradient-dawn relative overflow-hidden">
        <Constelacion className="pointer-events-none absolute -right-10 top-6 h-[220px] w-[220px] opacity-60 lg:h-[360px] lg:w-[360px]" />
        <div className="relative mx-auto max-w-6xl px-5 pb-20 pt-12 lg:px-8 lg:pb-24 lg:pt-16">
          <div className="grid gap-10 lg:grid-cols-[1.35fr_1fr] lg:items-center">
            <div>
              <h1 className="max-w-[18ch] text-balance font-display text-[36px] font-bold leading-[1.06] text-on-primary lg:text-[56px]">
                Encuentra tu profesor. Aprende a tu manera.
              </h1>
              <p className="mt-4 max-w-[46ch] text-[16px] leading-relaxed text-on-primary/90 lg:text-[19px]">
                Clases de inglés en vivo con profesores reales. Tú eliges con quién, cuándo y a qué ritmo.
              </p>

              {/* Una acción grande y una secundaria: el diagnóstico es el primer paso que Orión
                  propone, y buscar a mano es para quien ya sabe lo que quiere. «Quiero enseñar» no
                  va aquí: tiene su pestaña más abajo y su entrada en la cabecera. */}
              <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center">
                <Link
                  href="/diagnostico"
                  className="inline-flex h-[60px] items-center justify-center gap-2 rounded-pill bg-surface px-7 text-[17px] font-bold text-primary-strong shadow-lg transition-transform hover:-translate-y-0.5 focus-visible:shadow-focus"
                >
                  <Sparkles size={20} strokeWidth={2.2} />
                  Hacer mi diagnóstico gratis
                </Link>
                <Link
                  href="/profesores"
                  className="inline-flex h-[52px] items-center justify-center gap-2 rounded-pill border-[1.5px] border-on-primary/45 px-6 text-[15px] font-bold text-on-primary transition-colors hover:bg-on-primary/10 focus-visible:shadow-focus"
                >
                  <Search size={17} strokeWidth={2} />
                  Buscar profesor
                </Link>
              </div>
              <p className="mt-3 max-w-[56ch] text-[13.5px] leading-relaxed text-on-primary/85">
                {duracion} de conversación, gratis y sin cuenta: al terminar sabes cómo arrancas y tienes tres
                profesores elegidos para ti.
              </p>
              <p className="mt-6 text-[13px] font-semibold tracking-[0.02em] text-on-primary">
                ✦ Sin permanencia · Sin renovación automática · Profesores verificados
              </p>
            </div>
            <div className="hidden justify-center lg:flex lg:justify-end">
              <Rigel pose="saludo" className="h-[280px] w-auto drop-shadow-2xl" />
            </div>
          </div>
        </div>
      </header>

      {/* — 2. Buscador rápido: dos preguntas, sin cuenta — */}
      <section aria-label="Buscar profesor" className="relative z-10 mx-auto -mt-12 max-w-6xl px-5 lg:px-8">
        <BuscadorRapido />
      </section>

      {/* — Conoce a los profesores (real, se oculta con <4) — */}
      {mostrarProfesores && (
        <section className="mx-auto max-w-6xl px-5 pt-12 lg:px-8 lg:pt-16">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">La comunidad</p>
              <h2 className="mt-2 font-display text-h2 font-bold">Conoce a los profesores.</h2>
              <p className="mt-2 max-w-[48ch] text-[15px] text-text-secondary">
                Perfiles reales con su tarifa, su experiencia y sus horarios a la vista.
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
        </section>
      )}

      {/* — 3. Por qué ahora: el gancho de empleabilidad, con su fuente — */}
      <section className="mx-auto max-w-6xl px-5 py-12 lg:px-8 lg:py-16">
        <div className="grid gap-8 rounded-card bg-night px-7 py-10 text-text-on-night lg:grid-cols-[1.4fr_1fr] lg:items-center lg:px-14 lg:py-14">
          <div>
            <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent-peach">Por qué ahora</p>
            <h2 className="mt-3 text-balance font-display text-[26px] font-bold leading-[1.15] lg:text-[34px]">
              Hablar inglés aumenta hasta en 24 % tu probabilidad de conseguir un trabajo mejor pagado.
            </h2>
            <p className="mt-4 max-w-[56ch] text-[15.5px] leading-relaxed text-text-on-night/85">
              Y solo el 2,2 % de quienes buscan empleo en Colombia dice tener un nivel alto. Ahí está tu ventaja:
              aprender inglés no es un gasto, es de las decisiones que más rápido cambian tus oportunidades de
              trabajo y tu salario.
            </p>
            <p className="mt-5 text-[11.5px] leading-relaxed text-text-on-night/55">
              Fuente: Anif, British Council y Universidad de los Andes (2025), con datos del Servicio Público de
              Empleo, 2019–2024.
            </p>
          </div>
          <dl className="grid grid-cols-2 gap-4">
            <div className="rounded-card bg-text-on-night/[0.07] p-5">
              <dt className="text-[12.5px] leading-snug text-text-on-night/75">Más probabilidad de un trabajo mejor pagado</dt>
              <dd className="mt-2 font-display text-[40px] font-bold leading-none text-accent-peach lg:text-[48px]">24 %</dd>
            </div>
            <div className="rounded-card bg-text-on-night/[0.07] p-5">
              <dt className="text-[12.5px] leading-snug text-text-on-night/75">Buscan empleo con inglés alto</dt>
              <dd className="mt-2 font-display text-[40px] font-bold leading-none text-accent-peach lg:text-[48px]">2,2 %</dd>
            </div>
          </dl>
        </div>
      </section>

      {/* — 4. Cómo funciona: tres pasos — */}
      <section id="como-funciona" className="mx-auto max-w-6xl scroll-mt-20 px-5 pb-12 lg:px-8 lg:pb-16">
        <div className="text-center">
          <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">Cómo funciona Orión</p>
          <h2 className="mt-2 font-display text-h2 font-bold">Empezar toma menos de lo que crees.</h2>
        </div>

        {/*
          Los pasos encadenados. La animación se enciende paso a paso en bucle porque lo que cuesta
          explicar de un proceso no son las etapas sino que van en un orden. Es CSS con retardos
          escalonados —ver `.paso-icono` en globals.css—, sin temporizador ni estado.
        */}
        <ol className="mt-8 grid gap-x-2 gap-y-6 sm:grid-cols-3">
          {pasos(duracion).map((paso, i, todos) => {
            const Icono = paso.icono;
            return (
              <li key={paso.titulo} className="relative flex flex-col items-center px-2 text-center" style={{ "--i": i } as CSSProperties}>
                {/* La flecha vive en el paso ANTERIOR y va del borde de un icono al del siguiente. */}
                {i < todos.length - 1 && (
                  <span
                    aria-hidden="true"
                    className="pointer-events-none absolute top-7 hidden sm:flex sm:items-center"
                    style={{ left: "calc(50% + 34px)", width: "calc(100% - 68px)" }}
                  >
                    <span className="h-[2px] w-full rounded-full bg-border" />
                    <span
                      className="paso-flecha absolute left-0 top-1/2 h-[2px] -translate-y-1/2 rounded-full bg-primary after:absolute after:right-0 after:top-1/2 after:-translate-y-1/2 after:border-y-[4.5px] after:border-l-[7px] after:border-y-transparent after:border-l-primary after:content-['']"
                      style={{ "--i": i } as CSSProperties}
                    />
                  </span>
                )}
                <span className={`paso-icono relative z-10 grid h-14 w-14 place-items-center rounded-full shadow-sm ${paso.fondo} ${paso.tinta}`}>
                  <Icono size={24} strokeWidth={1.9} />
                </span>
                <p className="mt-4 font-display text-[18px] font-bold">
                  <span className="text-primary-strong">{i + 1}.</span> {paso.titulo}
                </p>
                <p className="mt-1.5 max-w-[34ch] text-[14px] leading-relaxed text-text-secondary">{paso.texto}</p>
              </li>
            );
          })}
        </ol>
      </section>

      {/* — 5. El Método ORION™ — */}
      <section
        id="metodo"
        className="relative scroll-mt-20 overflow-hidden bg-[linear-gradient(160deg,#2E1E4E_0%,#4A2E63_45%,#7A4A8C_100%)] py-14 text-text-on-night lg:py-20"
      >
        <Constelacion className="pointer-events-none absolute -left-16 bottom-0 h-[260px] w-[260px] opacity-25 lg:h-[420px] lg:w-[420px]" />

        <div className="relative mx-auto max-w-6xl px-5 lg:px-8">
          <div className="max-w-[60ch]">
            <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent-peach">El seguimiento</p>
            <h2 className="mt-3 font-display text-[36px] font-bold leading-[1.05] lg:text-[54px]">
              Método ORION<span className="align-super text-[0.4em]">™</span>
            </h2>
            <p className="mt-3 font-display text-[20px] font-semibold text-accent-peach lg:text-[24px]">
              Tu profesor enseña. Orión se encarga del seguimiento.
            </p>
            <p className="mt-4 text-[16px] leading-relaxed text-text-on-night/85 lg:text-[17px]">
              En Orión los profesores son libres: cada uno enseña con el estilo y el material que considere mejor
              para ti. Lo que nosotros aportamos es la continuidad: convertimos lo trabajado en cada clase en
              ejercicios concretos para que practiques por tu cuenta y llegues más preparado a la siguiente.
            </p>
          </div>

          <ol className="mt-10 grid gap-px overflow-hidden rounded-card bg-text-on-night/15">
            {METODO.map((paso, i) => (
              <li
                key={paso.palabra}
                className="aparece grid gap-3 bg-[#2E1E4E] p-6 sm:grid-cols-[3.5rem_minmax(0,14ch)_1fr] sm:items-start sm:gap-6 lg:p-7"
                style={{ "--i": i } as CSSProperties}
              >
                <span aria-hidden="true" className="font-display text-[52px] font-bold leading-none lg:text-[60px]" style={{ color: paso.color }}>
                  {paso.letra}
                </span>
                <p className="font-display text-[20px] font-bold sm:pt-2 lg:text-[22px]">{paso.palabra}</p>
                <p className="text-[15px] leading-relaxed text-text-on-night/85 sm:pt-2">{paso.texto}</p>
              </li>
            ))}
          </ol>

          <p className="mt-8 max-w-[58ch] font-display text-[19px] font-semibold leading-snug lg:text-[22px]">
            Sin ORION, una clase termina y ahí queda. Con ORION, sales sabiendo exactamente en qué trabajar hasta
            la próxima.
          </p>

          {/* Para profesores, dentro de la misma sección (Sofía): el mismo método visto desde su lado. */}
          <div className="mt-12 rounded-card border border-text-on-night/15 bg-text-on-night/[0.05] p-6 lg:p-9">
            <p className="font-display text-[20px] font-bold">¿Eres profesor?</p>
            <p className="mt-2 max-w-[64ch] text-[15px] leading-relaxed text-text-on-night/85">
              Tú planeas y dictas tus clases a tu manera; ORION se encarga de lo que viene después: al terminar
              dictas en un minuto lo que viste, revisas el resumen que armamos y lo publicas, y de ahí salen los
              ejercicios de práctica de tu estudiante entre clases.
            </p>
            <ul className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
              {METODO_PROFESOR.map((paso) => (
                <li key={paso.palabra + paso.letra} className="text-[14px] leading-relaxed text-text-on-night/80">
                  <span className="font-display text-[16px] font-bold text-accent-peach">
                    {paso.letra} — {paso.palabra}.
                  </span>{" "}
                  {paso.texto}
                </li>
              ))}
            </ul>
            <p className="mt-5 text-[13.5px] text-text-on-night/65">
              Ninguna de estas etapas te obliga a cambiar tu metodología: son el marco del seguimiento, no de tu
              clase.
            </p>
          </div>
        </div>
      </section>

      {/* — 6. Por qué Orión, en dos pestañas — */}
      <section className="mx-auto max-w-5xl px-5 py-12 lg:px-8 lg:py-16">
        <h2 className="text-center font-display text-h2 font-bold">Aquí las reglas juegan a tu favor.</h2>
        <div className="mt-6">
          <PorQueOrion />
        </div>
      </section>

      {/* — 7. Diagnóstico gratuito — */}
      <section className="mx-auto max-w-6xl px-5 pb-12 lg:px-8 lg:pb-16">
        <div className="gradient-dawn grid items-center gap-6 rounded-card px-7 py-10 lg:grid-cols-[1.5fr_1fr] lg:px-14 lg:py-14">
          <div>
            <h2 className="text-balance font-display text-h2 font-bold text-on-primary">¿No sabes por dónde empezar?</h2>
            <p className="mt-3 max-w-[54ch] text-[15.5px] leading-relaxed text-on-primary/90">
              Haz tu diagnóstico gratis. En {duracion} de conversación sabes cómo arrancas y te recomendamos tres
              profesores elegidos por lo que contaste. Te lo entregamos aunque todavía no reserves ninguna clase.
            </p>
            <Link
              href="/diagnostico"
              className="mt-6 inline-flex h-[56px] items-center justify-center gap-2 rounded-pill bg-surface px-7 text-[16px] font-bold text-primary-strong shadow-lg transition-transform hover:-translate-y-0.5 focus-visible:shadow-focus"
            >
              <Sparkles size={19} strokeWidth={2.2} />
              Hacer mi diagnóstico gratis
            </Link>
          </div>
          <div className="hidden justify-center lg:flex">
            <Rigel pose="animo" decorativo className="h-[200px] w-auto" />
          </div>
        </div>
      </section>

      {/* — Nosotros: destino del «Sobre Orión» del pie — */}
      <section id="nosotros" className="scroll-mt-20 bg-night py-14 text-text-on-night lg:py-20">
        <div className="mx-auto max-w-6xl px-5 lg:px-8">
          <div className="grid gap-8 lg:grid-cols-[1fr_1.25fr] lg:items-end">
            <div>
              <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent-peach">Nosotros</p>
              <p className="mt-3 font-display text-[64px] font-bold leading-[0.95] lg:text-[104px]">
                Nadie
                <span className="block text-accent-peach">improvisa.</span>
              </p>
            </div>
            <p className="max-w-[52ch] text-[16px] leading-relaxed text-text-on-night/85 lg:text-[18px]">
              Aprender inglés no puede depender de la suerte con la que elegiste profesor. En Orión cada profesor
              pasa por verificación de documentos, experiencia y entrevista antes de publicarse, y lo que ocurre
              después —reservar, pagar, hablar, dar la clase— vive en un solo sitio. Lo demás es tu tiempo, y no
              lo gastamos.
            </p>
          </div>

          <ul className="mt-12 grid gap-x-8 gap-y-8 border-t border-text-on-night/15 pt-8 sm:grid-cols-2 lg:grid-cols-4">
            {NOSOTROS.map((punto, i) => (
              <li key={punto.titulo} className="aparece" style={{ "--i": i } as CSSProperties}>
                <p className="font-display text-[17px] font-bold text-accent-peach">{punto.titulo}</p>
                <p className="mt-1.5 text-[14px] leading-relaxed text-text-on-night/75">{punto.texto}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* — 9. Preguntas frecuentes, en dos pestañas — */}
      <section id="preguntas" className="mx-auto max-w-3xl scroll-mt-20 px-5 py-12 lg:px-8 lg:py-16">
        <PreguntasDeLaPortada />
      </section>

      {/* — 10. Cierre — */}
      <section className="gradient-dawn">
        <div className="mx-auto max-w-3xl px-5 py-12 text-center lg:py-16">
          <h2 className="text-balance font-display text-h1 font-bold text-on-primary">
            Tu primera clase está a un par de clics.
          </h2>
          <p className="mx-auto mt-3 max-w-[46ch] text-[15px] text-on-primary/90">
            Sin permanencia y sin renovación automática: pagas solo la clase que reservas.
          </p>
          <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
            <Link
              href="/profesores"
              className="inline-flex h-[52px] items-center justify-center gap-2 rounded-pill bg-surface px-7 text-[15px] font-bold text-primary-strong shadow-lg transition-transform hover:-translate-y-0.5 focus-visible:shadow-focus"
            >
              Buscar profesor
              <ArrowRight size={18} strokeWidth={1.9} />
            </Link>
            <Link
              href="/diagnostico"
              className="inline-flex h-[52px] items-center justify-center gap-2 rounded-pill border-[1.5px] border-on-primary/45 px-7 text-[15px] font-bold text-on-primary transition-colors hover:bg-on-primary/10 focus-visible:shadow-focus"
            >
              <Sparkles size={17} strokeWidth={2} />
              Hacer mi diagnóstico gratis
            </Link>
          </div>
        </div>
      </section>

      {/* — 11. Pie — */}
      <footer className="border-t border-border bg-surface">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-5 py-10 lg:flex-row lg:items-start lg:justify-between lg:px-8">
          <div>
            <Wordmark className="text-[16px] text-primary-strong" />
            <p className="mt-1 text-[13px] italic text-text-muted sm:whitespace-nowrap">Find your right teacher, learn your way.</p>
            <Redes className="-ml-3 mt-3" />
          </div>
          <nav aria-label="Pie de página" className="flex flex-wrap gap-x-5 gap-y-2 text-[13.5px] font-semibold text-text-secondary">
            <Link href="/#nosotros" className="hover:text-text">
              Sobre Orión
            </Link>
            <Link href="/#preguntas" className="hover:text-text">
              Preguntas frecuentes
            </Link>
            <Link href="/terminos#cancelaciones-y-reprogramacion" className="hover:text-text">
              Políticas de cancelación
            </Link>
            <Link href="/terminos" className="hover:text-text">
              Términos y condiciones
            </Link>
            <Link href="/privacidad" className="hover:text-text">
              Política de privacidad
            </Link>
            {whatsapp && (
              <a
                href={whatsapp}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 text-success hover:underline"
              >
                <MessageCircle size={15} strokeWidth={1.75} />
                Contacto por WhatsApp
              </a>
            )}
          </nav>
        </div>
        <p className="pb-8 text-center text-[12px] text-text-muted">Orión © 2026</p>
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
            <p className="mt-0.5 line-clamp-2 text-[13px] font-semibold text-text-secondary">{profesor.headline}</p>
          )}
          <div className="mt-1.5">
            <EstrellaRating ratingAvg={profesor.ratingAvg} ratingCount={profesor.ratingCount} />
          </div>
        </div>
      </div>
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
          Ver perfil
          <ChevronRight size={15} strokeWidth={2} />
        </span>
      </div>
    </Link>
  );
}
