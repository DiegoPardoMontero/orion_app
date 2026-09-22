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
import { EnsenaCta } from "@/components/EnsenaCta";
import { HeroCta } from "@/components/HeroCta";
import { Constelacion, Wordmark } from "@/components/marca";
import { NavPublica } from "@/components/NavPublica";
import { EstrellaRating } from "@/components/Rating";
import { PreguntasFrecuentes } from "@/components/PreguntasFrecuentes";
import { Rigel } from "@/components/Rigel";
import { serverFetch } from "@/lib/api/server";
import type {
  GoalResponse,
  PagedProfessors,
  ProfessorCard,
} from "@/lib/api/types";
import { SITE_URL, whatsappSoporte } from "@/lib/config";
import { esGratis, tarifaClase } from "@/lib/format";

/**
 * Portada del marketplace. Server component (SEO + rendimiento): estática salvo las islas cliente
 * —`NavPublica` y `HeroCta`—. Los datos de catálogo y los profesores destacados se
 * resuelven en el servidor con `serverFetch` (ISR, revalidación cada 5 min). Datos SIEMPRE honestos:
 * nada de ratings ni contadores inventados, y la sección de profesores se OCULTA si hay menos de 4.
 */
export const metadata: Metadata = {
  title: "Orión · Encuentra a tu profesor de inglés",
  description:
    "Clases de inglés en vivo, uno a uno, con profesores verificados. Reserva a la hora que te sirva y aprende para lo que te importa: el trabajo, los viajes, el estudio o la vida en el exterior.",
  alternates: { canonical: "/" },
  openGraph: {
    title: "Orión · Encuentra a tu profesor de inglés",
    description:
      "Profesores de inglés verificados. Reserva clases en vivo, a tu ritmo, y aprende para lo que te importa.",
    type: "website",
    images: [{ url: "/og.png", width: 1200, height: 630, alt: "Orión, clases de inglés en vivo" }],
  },
};

/**
 * Los cuatro pasos, cada uno con su color. Antes eran cuatro círculos grises idénticos, y la única
 * pista de que fueran etapas distintas era el número. El color va del amanecer a la noche —durazno,
 * coral, lavanda, tinta— para que el recorrido se lea como un avance y no como una lista.
 */
const PASOS = [
  {
    icono: UserRoundSearch,
    titulo: "Encuentra",
    texto: "Filtra por objetivo, nivel u horario. Compara perfiles reales.",
    fondo: "bg-accent-peach-soft",
    tinta: "text-[#8a5a33]",
  },
  {
    icono: CalendarCheck,
    titulo: "Reserva",
    texto: "Elige un cupo disponible y confírmalo en segundos.",
    fondo: "bg-primary-soft",
    tinta: "text-primary-strong",
  },
  {
    icono: MessageCircle,
    titulo: "Aprende",
    texto: "Toma tu clase en vivo por videollamada. Todo se coordina dentro de Orión.",
    fondo: "bg-accent-lavender-soft",
    tinta: "text-info",
  },
  {
    icono: TrendingUp,
    titulo: "Avanza",
    texto: "Vuelve con el mismo profesor y construye una rutina.",
    fondo: "bg-success-bg",
    tinta: "text-success",
  },
];

/**
 * El Método ORION®: el marco de acompañamiento, no una metodología impuesta al profesor.
 *
 * <p>Cada profesor mantiene su libertad pedagógica. Lo que el método estructura es la capa de
 * seguimiento y feedback: qué se observa, cómo se conecta con lo que el estudiante ya sabe, cuándo
 * se corrige y con qué se cierra la clase. Cada letra se apoya en teoría educativa establecida, y
 * por eso se cita — sin la cita esto sería un acrónimo bonito.
 */
const METODO = [
  {
    letra: "O",
    palabra: "Observe",
    promesa: "Vemos lo que todavía no oyes.",
    texto:
      "Identificamos lo que no estás percibiendo del idioma y te lo ponemos delante, con input que puedas entender.",
    teoria: "Hipótesis del noticing (Schmidt) · Input comprensible (Krashen)",
    color: "#FFC189",
  },
  {
    letra: "R",
    palabra: "Relate",
    promesa: "Lo nuevo se engancha a lo tuyo.",
    texto:
      "Conectamos cada cosa nueva con lo que ya sabes y con tu contexto real: tu trabajo, tu viaje, tu vida.",
    teoria: "Aprendizaje significativo (Ausubel)",
    color: "#E8764F",
  },
  {
    letra: "I",
    palabra: "Interact",
    promesa: "Se aprende hablando, no escuchando.",
    texto:
      "El idioma se consolida produciéndolo. Por eso la clase es conversación y no una exposición que atiendes.",
    teoria: "Hipótesis de la interacción (Long) · Hipótesis del output (Swain)",
    color: "#E8503A",
  },
  {
    letra: "O",
    palabra: "Optimize",
    promesa: "Dos o tres correcciones, no cuarenta.",
    texto:
      "Se corrige lo que más te cambia el resultado, y se vuelve a ello con el tiempo en vez de señalarlo todo una vez.",
    teoria: "Feedback efectivo (Hattie & Timperley) · Repetición espaciada",
    color: "#B9A7E6",
  },
  {
    letra: "N",
    palabra: "Navigate",
    promesa: "Sales sabiendo cuál es el siguiente paso.",
    texto:
      "Cada clase cierra con algo concreto y medible para la siguiente, dentro de lo que ya casi puedes hacer solo.",
    teoria: "Feed forward y autorregulación (Zimmerman) · Zona de desarrollo próximo (Vygotsky)",
    color: "#5E4A8A",
  },
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
  const [paged, goals] = await Promise.all([
    serverFetch<PagedProfessors>("/api/v1/professors?size=4"),
    serverFetch<GoalResponse[]>("/api/v1/catalog/goals"),
  ]);

  const profesores = paged?.content ?? [];
  // Regla de honestidad del brief: mostrar la sección SOLO si hay al menos 4 profesores publicados.
  const mostrarProfesores = profesores.length >= 4;
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
                  "Clases de inglés en vivo, uno a uno, con profesores verificados.",
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
              {/* Primero qué es Orión y después qué hacer: quien llega por primera vez no sabía si
                  esto era una app de test o una academia. */}
              <h1 className="max-w-[20ch] font-display text-[34px] font-bold leading-[1.08] text-on-primary lg:text-[52px]">
                Orión es una academia de inglés especializada.
              </h1>
              <p className="mt-4 max-w-[40ch] font-display text-[20px] font-semibold leading-snug text-on-primary lg:text-[26px]">
                Antes de empezar, toma tu diagnóstico de inglés.
              </p>
              <p className="mt-3 max-w-[52ch] text-[15px] leading-relaxed text-on-primary/85 lg:text-[17px]">
                Dos minutos de conversación, gratis, sin cuenta y sin examen. Al colgar sabes cómo hablas y
                tienes tres profesores elegidos por lo que contaste.
              </p>
            </div>
            <div className="flex justify-center lg:justify-end">
              <Rigel pose="saludo" className="h-[190px] w-auto drop-shadow-2xl lg:h-[280px]" />
            </div>
          </div>

          {/*
            El buscador de IDIOMA / OBJETIVO / HORARIO se retiró de aquí. Pedía tres decisiones a
            alguien que todavía no sabe qué necesita, y una de ellas —el idioma— ya no es una
            decisión. El primer gesto pasa a ser la conversación de dos minutos, que es lo único que
            Orión tiene y nadie más: se termina sabiendo tu nivel y con tres profesores elegidos por
            lo que dijiste. Buscar a mano sigue estando, un clic más abajo, para quien ya lo tiene
            claro.
          */}
          {/* Una sola acción grande y el resto en una fila de secundarios iguales: el diagnóstico
              es el primer paso que Orión propone, y los otros tres son para quien ya sabe lo que
              quiere. */}
          <Link
            href="/diagnostico"
            className="mt-9 inline-flex h-[60px] w-full items-center justify-center gap-2 rounded-pill bg-surface px-7 text-[17px] font-bold text-primary-strong shadow-lg transition-transform hover:-translate-y-0.5 focus-visible:shadow-focus"
          >
            <Sparkles size={20} strokeWidth={2.2} />
            Empezar mi diagnóstico
          </Link>
          <div className="mt-3">
            <HeroCta />
          </div>
        </div>
      </header>


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

                <span
                  className={`paso-icono relative z-10 grid h-14 w-14 place-items-center rounded-full shadow-sm ${paso.fondo} ${paso.tinta}`}
                >
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

      {/*
        El Método ORION®: la sección con más peso de la portada, y a propósito. Es lo que distingue
        a Orión de un catálogo de profesores — no una metodología impuesta al profesor, que mantiene
        su libertad pedagógica, sino la capa de seguimiento que estructura el progreso del
        estudiante sobre teoría educativa establecida.

        Cada letra trae su cita. Sin ella esto sería un acrónimo bonito, y la diferencia entre un
        método y un eslogan es exactamente esa.

        La animación es CSS con retardos escalonados (`aparece`), sin estado ni temporizadores, y
        `prefers-reduced-motion` la apaga con el reset global.
      */}
      <section
        id="metodo"
        className="relative scroll-mt-20 overflow-hidden bg-[linear-gradient(160deg,#2E1E4E_0%,#4A2E63_45%,#7A4A8C_100%)] py-14 text-text-on-night lg:py-20"
      >
        <Constelacion className="pointer-events-none absolute -left-16 bottom-0 h-[260px] w-[260px] opacity-25 lg:h-[420px] lg:w-[420px]" />

        <div className="relative mx-auto max-w-6xl px-5 lg:px-8">
          <div className="max-w-[58ch]">
            <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent-peach">
              El acompañamiento
            </p>
            <h2 className="mt-3 font-display text-[36px] font-bold leading-[1.05] lg:text-[54px]">
              Método ORION<span className="align-super text-[0.45em]">®</span>
            </h2>
            <p className="mt-4 text-[16px] leading-relaxed text-text-on-night/85 lg:text-[18px]">
              Tu profesor enseña como sabe hacerlo, y esa libertad no se toca. Lo que Orión estructura
              es lo que pasa alrededor: qué se observa de ti, cómo se conecta con lo que ya sabes,
              qué se corrige y con qué sales de cada clase. Cinco pasos, cada uno apoyado en teoría
              que existe desde antes que nosotros.
            </p>
          </div>

          <ol className="mt-11 grid gap-px overflow-hidden rounded-card bg-text-on-night/15">
            {METODO.map((paso, i) => (
              <li
                key={paso.palabra}
                className="aparece grid gap-3 bg-[#2E1E4E] p-6 transition-colors hover:bg-[#38254f] sm:grid-cols-[auto_1fr] sm:items-start sm:gap-6 lg:grid-cols-[auto_minmax(0,18ch)_1fr] lg:p-7"
                style={{ "--i": i } as CSSProperties}
              >
                <span
                  aria-hidden="true"
                  className="font-display text-[52px] font-bold leading-none lg:text-[64px]"
                  style={{ color: paso.color }}
                >
                  {paso.letra}
                </span>

                <div>
                  <p className="font-display text-[20px] font-bold lg:text-[22px]">{paso.palabra}</p>
                  <p className="mt-1 text-[14.5px] font-semibold" style={{ color: paso.color }}>
                    {paso.promesa}
                  </p>
                </div>

                <div className="lg:pt-1">
                  <p className="text-[14.5px] leading-relaxed text-text-on-night/85">{paso.texto}</p>
                  <p className="mt-2 text-[11.5px] uppercase tracking-[0.06em] text-text-on-night/45">
                    {paso.teoria}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/*
        Nosotros, minimalista. Antes era un titular, un párrafo y cuatro tarjetas con icono: la
        misma forma que el resto de la página, así que no se leía como una declaración sino como
        otro bloque de features. Ahora manda una palabra —la promesa— y todo lo demás la explica.
      */}
      <section id="nosotros" className="scroll-mt-20 bg-night py-14 text-text-on-night lg:py-20">
        <div className="mx-auto max-w-6xl px-5 lg:px-8">
          <div className="grid gap-8 lg:grid-cols-[1fr_1.25fr] lg:items-end">
            <div>
              <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent-peach">
                Nosotros
              </p>
              <p className="mt-3 font-display text-[64px] font-bold leading-[0.95] lg:text-[104px]">
                Nadie
                <span className="block text-accent-peach">improvisa.</span>
              </p>
            </div>
            <p className="max-w-[52ch] text-[16px] leading-relaxed text-text-on-night/85 lg:text-[18px]">
              Aprender inglés no puede depender de la suerte con la que elegiste profesor. En Orión
              cada profesor pasa por verificación de documentos, experiencia y entrevista antes de
              publicarse, y lo que ocurre después —reservar, pagar, hablar, dar la clase— vive en un
              solo sitio. Lo demás es tu tiempo, y no lo gastamos.
            </p>
          </div>

          <ul className="mt-12 grid gap-x-8 gap-y-8 border-t border-text-on-night/15 pt-8 sm:grid-cols-2 lg:grid-cols-4">
            {NOSOTROS.map((punto, i) => (
              <li key={i} className="aparece" style={{ "--i": i } as CSSProperties}>
                <p className="font-display text-[17px] font-bold text-accent-peach">{punto.titulo}</p>
                <p className="mt-1.5 text-[14px] leading-relaxed text-text-on-night/75">
                  {punto.texto}
                </p>
              </li>
            ))}
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
                className="inline-flex h-[52px] w-full items-center justify-center gap-2 rounded-pill border-[1.5px] border-on-primary/35 px-7 text-[15px] font-bold text-on-primary transition-colors hover:bg-on-primary/10 focus-visible:shadow-focus sm:w-auto sm:min-w-[236px]"
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

      {/* Las preguntas van al final, después del salto a profesores: quien llega aquí ya ha visto
           la propuesta entera, y ponerlas antes obligaba a pasar por encima de ellas a quien solo
           quería reservar. El cierre sigue siendo la llamada a la acción, no una lista de dudas. */}
      <section id="preguntas" className="mx-auto max-w-3xl scroll-mt-20 px-5 pb-12 lg:px-8 lg:pb-16">
        <PreguntasFrecuentes rol="general" className="mt-0" />
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
          © 2026 Orión Idiomas ·{" "}
          <Link href="/terminos" className="underline underline-offset-2 hover:text-text">
            Términos y condiciones
          </Link>{" "}
          ·{" "}
          <Link href="/privacidad" className="underline underline-offset-2 hover:text-text">
            Política de tratamiento de datos
          </Link>
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
