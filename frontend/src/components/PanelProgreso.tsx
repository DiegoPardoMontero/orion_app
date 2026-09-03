"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, CalendarDays, Clock, Flame, Trophy, Video } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { Avatar } from "@/components/Avatar";
import { Constelacion } from "@/components/marca";
import { Rigel, type RigelPose } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import { diaBogota, fechaCorta, horaBogota } from "@/lib/format";

type ProximaClase = {
  id: string;
  startsAt: string;
  modality: string;
  meetingLink: string | null;
  professorId: string;
  professorName: string | null;
  professorPhotoUrl: string | null;
};

type ProfesorPracticado = {
  id: string;
  fullName: string | null;
  photoUrl: string | null;
  headline: string | null;
  lessons: number;
  lastLessonAt: string;
};

type Progreso = {
  lessonsTaken: number;
  minutesTotal: number;
  currentStreakWeeks: number;
  bestStreakWeeks: number;
  nextLesson: ProximaClase | null;
  professors: ProfesorPracticado[];
  lessonsByDay: Record<string, number>;
  mapFrom: string;
  today: string;
};

/**
 * Cuántos días faltan para una fecha, contando días de calendario en Bogotá y no intervalos de 24
 * horas: una clase mañana a las 8 de la mañana es «mañana» aunque falten 15 horas, no «hoy».
 */
function diasHasta(iso: string, hoy: string): number {
  const a = new Date(`${diaBogota(iso)}T00:00:00Z`).getTime();
  const b = new Date(`${hoy}T00:00:00Z`).getTime();
  return Math.round((a - b) / 86_400_000);
}

function cuandoEs(iso: string, hoy: string): string {
  const dias = diasHasta(iso, hoy);
  if (dias <= 0) return "Hoy";
  if (dias === 1) return "Mañana";
  if (dias < 7) return `En ${dias} días`;
  return fechaCorta(iso);
}

type Saludo = {
  pose: RigelPose;
  /** Lo que va encima de la cifra. Corto: es una entradilla, no una frase. */
  encima: string;
  /** El titular del banner. Se saca del texto corrido para poder darle tamaño de titular. */
  cifra: string | null;
  /** El remate, debajo. Es lo que da sentido a la cifra. */
  debajo: string;
};

/**
 * Lo que dice Rigel. No felicita por nada: cada mensaje se apoya en un dato real del panel, y
 * cuando no hay ninguno, invita a empezar en vez de fingir un logro.
 *
 * Se devuelve en tres piezas y no como una frase porque la cifra es el titular: encajada dentro
 * del párrafo se perdía, y lo que se quiere que se lea a un metro de distancia es cuántas clases
 * lleva esta persona.
 */
function saludoDe(progreso: Progreso): Saludo {
  const { lessonsTaken, currentStreakWeeks, nextLesson, today } = progreso;
  const clases = `${lessonsTaken} ${lessonsTaken === 1 ? "clase" : "clases"}`;

  if (lessonsTaken === 0 && !nextLesson) {
    return {
      pose: "saludo",
      encima: "Tu punto de partida",
      cifra: null,
      debajo:
        "Aquí vas a ver cómo avanzas: las clases que llevas, las horas de práctica y las semanas seguidas. Reserva la primera y empezamos a contar.",
    };
  }
  if (lessonsTaken === 0) {
    return {
      pose: "animo",
      encima: "Ya está agendada",
      cifra: null,
      debajo:
        "Tu primera clase está reservada. De ahí en adelante, todo lo que practiques se queda registrado aquí.",
    };
  }
  if (nextLesson && diasHasta(nextLesson.startsAt, today) <= 0) {
    return {
      pose: "animo",
      encima: "Hoy tienes clase. Llevas",
      cifra: clases,
      debajo: "Nos vemos en un rato. Una más para la cuenta.",
    };
  }
  if (currentStreakWeeks >= 4) {
    return {
      pose: "celebracion",
      encima: "Ya vas por",
      cifra: clases,
      debajo: `Y ${currentStreakWeeks} semanas seguidas sin fallar una. Esto ya no es intentarlo: es tu rutina.`,
    };
  }
  if (currentStreakWeeks >= 2) {
    return {
      pose: "animo",
      encima: "Ya vas por",
      cifra: clases,
      debajo: `${currentStreakWeeks} semanas seguidas. Una más y esto se vuelve costumbre.`,
    };
  }
  return {
    pose: "guia",
    encima: "Ya vas por",
    cifra: clases,
    debajo: "Cada una cuenta. Reserva la siguiente y sigue sumando.",
  };
}

/**
 * El panel del estudiante: lo que ya recorrió, con qué frecuencia y qué viene después. Todo sale de
 * sus reservas —no hay ninguna métrica inventada ni nada que tenga que rellenar a mano—, y por eso
 * los números crecen solos y nunca contradicen a la agenda.
 */
export function PanelProgreso() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["me", "progress"],
    queryFn: () => apiFetch<Progreso>("/api/v1/me/progress"),
    staleTime: 60_000,
  });

  // Sin panel se sigue pudiendo editar la cuenta, que es lo que esta pantalla ya hacía: un fallo
  // aquí no debe dejar a nadie sin poder cambiar su teléfono.
  if (isPending || isError || !data) return null;

  const saludo = saludoDe(data);
  const horas = Math.round(data.minutesTotal / 60);

  return (
    <section className="mt-6">
      {/* Rigel y su frase: la única parte que interpreta los números en vez de mostrarlos. */}
      <div className="gradient-dawn relative overflow-hidden rounded-card px-5 py-5 lg:px-8 lg:py-7">
        <Constelacion className="pointer-events-none absolute -right-8 -top-10 h-[180px] w-[180px] opacity-30 lg:h-[240px] lg:w-[240px]" />

        <div className="relative flex items-center gap-4 lg:gap-7">
          <Rigel
            pose={saludo.pose}
            decorativo
            className="h-[104px] w-auto shrink-0 drop-shadow-lg lg:h-[150px]"
          />

          <div className="min-w-0">
            <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-on-primary/75">
              {saludo.encima}
            </p>

            {saludo.cifra && (
              <p className="mt-0.5 font-display text-[34px] font-extrabold leading-none text-on-primary lg:text-[52px]">
                {saludo.cifra}
              </p>
            )}

            <p className="mt-2 max-w-[46ch] text-[13.5px] leading-relaxed text-on-primary/90 lg:text-[15px]">
              {saludo.debajo}
            </p>

            {/* El fuego solo aparece cuando hay racha: encendido sin nada detrás no significaría nada. */}
            {data.currentStreakWeeks >= 1 && (
              <span className="mt-3 inline-flex items-center gap-1.5 rounded-pill bg-night/35 py-1.5 pl-2 pr-3.5 text-[13px] font-bold text-on-primary backdrop-blur-sm">
                <span className="llama grid h-6 w-6 shrink-0 place-items-center rounded-full bg-gradient-to-b from-[#ffc189] to-[#e8503a]">
                  <Flame size={13} strokeWidth={2.4} className="text-[#5a2436]" />
                </span>
                {data.currentStreakWeeks}{" "}
                {data.currentStreakWeeks === 1 ? "semana en racha" : "semanas en racha"}
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Cifra
          icono={<CalendarDays size={19} strokeWidth={2.1} />}
          tono="lavanda"
          valor={data.lessonsTaken}
          etiqueta={data.lessonsTaken === 1 ? "clase tomada" : "clases tomadas"}
        />
        <Cifra
          icono={<Clock size={19} strokeWidth={2.1} />}
          tono="menta"
          valor={horas}
          etiqueta={horas === 1 ? "hora de práctica" : "horas de práctica"}
        />
        <Cifra
          icono={<Flame size={19} strokeWidth={2.1} />}
          tono="fuego"
          valor={data.currentStreakWeeks}
          etiqueta={data.currentStreakWeeks === 1 ? "semana seguida" : "semanas seguidas"}
          destacada={data.currentStreakWeeks >= 2}
        />
        {/* La mejor racha se muestra siempre: es lo que queda cuando la actual se corta, y verla
            ahí evita que una semana floja borre de un plumazo lo que ya se consiguió. */}
        <Cifra
          icono={<Trophy size={19} strokeWidth={2.1} />}
          tono="oro"
          valor={data.bestStreakWeeks}
          etiqueta="tu mejor racha"
        />
      </div>

      {data.nextLesson && <ProximaClaseTarjeta clase={data.nextLesson} hoy={data.today} />}

      <MapaDelAnio porDia={data.lessonsByDay} desde={data.mapFrom} hasta={data.today} />

      {data.professors.length > 0 && <Profesores lista={data.professors} />}
    </section>
  );
}

/**
 * Cada métrica lleva su propia pastilla de color, con degradado y sombra, como el icono de una
 * aplicación. Los cuatro iconos planos del mismo gris se leían como una tabla: el color es lo que
 * hace que cada cifra se reconozca de un vistazo, sin tener que leer su etiqueta.
 */
const TONOS_CIFRA = {
  lavanda: "from-[#c9b8f0] to-[#a68fdb] text-[#2e1e4e]",
  menta: "from-[#a8e0c2] to-[#5cb98a] text-[#14432c]",
  fuego: "from-[#ffc189] to-[#e8503a] text-[#5a2436]",
  oro: "from-[#ffd97a] to-[#f0a72c] text-[#5a3a06]",
} as const;

function Cifra({
  icono,
  tono,
  valor,
  etiqueta,
  destacada = false,
}: {
  icono: React.ReactNode;
  tono: keyof typeof TONOS_CIFRA;
  valor: number;
  etiqueta: string;
  destacada?: boolean;
}) {
  return (
    <div
      className={`rounded-card border p-4 transition-shadow hover:shadow-md ${
        destacada ? "border-primary/40 bg-primary-soft/50" : "border-border bg-surface-raised"
      }`}
    >
      <span
        className={`grid h-10 w-10 place-items-center rounded-[12px] bg-gradient-to-b shadow-sm ${TONOS_CIFRA[tono]}`}
      >
        {icono}
      </span>
      <p className="mt-2.5 font-display text-[28px] font-extrabold leading-none tabular-nums">
        {valor}
      </p>
      <p className="mt-1 text-[12px] leading-tight text-text-secondary">{etiqueta}</p>
    </div>
  );
}

function ProximaClaseTarjeta({ clase, hoy }: { clase: ProximaClase; hoy: string }) {
  const virtual = clase.modality === "VIRTUAL";
  return (
    <div className="mt-3 rounded-card border border-border bg-surface-raised p-4">
      <p className="text-[11px] font-bold uppercase tracking-[0.08em] text-text-muted">
        Tu próxima clase
      </p>
      <div className="mt-2 flex flex-wrap items-center gap-3">
        <Avatar nombre={clase.professorName ?? ""} fotoUrl={clase.professorPhotoUrl} />
        <div className="min-w-0 flex-1">
          <p className="font-display text-[16px] font-bold">
            {cuandoEs(clase.startsAt, hoy)} · {horaBogota(clase.startsAt)}
          </p>
          <p className="truncate text-[13px] text-text-secondary">
            con {clase.professorName ?? "tu profesor"}
          </p>
        </div>
        {virtual && clase.meetingLink && (
          <a
            href={clase.meetingLink}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex h-10 shrink-0 items-center gap-2 rounded-pill bg-primary px-4 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
          >
            <Video size={16} strokeWidth={1.75} />
            Unirse
          </a>
        )}
      </div>
    </div>
  );
}

/**
 * Un año de clases en una cuadrícula: cada columna es una semana, cada casilla un día. Contesta de
 * un vistazo algo que una lista no puede —si las clases se reparten o se apelotonan— y hace visible
 * el hueco de un mes sin practicar mejor que cualquier número.
 */
function MapaDelAnio({
  porDia,
  desde,
  hasta,
}: {
  porDia: Record<string, number>;
  desde: string;
  hasta: string;
}) {
  const inicio = new Date(`${desde}T00:00:00Z`);
  // La cuadrícula arranca en lunes para que cada columna sea una semana entera.
  const offset = (inicio.getUTCDay() + 6) % 7;
  inicio.setUTCDate(inicio.getUTCDate() - offset);

  const fin = new Date(`${hasta}T00:00:00Z`);
  const semanas: { fecha: string; clases: number; futuro: boolean }[][] = [];

  for (let cursor = new Date(inicio); cursor <= fin; ) {
    const semana: { fecha: string; clases: number; futuro: boolean }[] = [];
    for (let d = 0; d < 7; d++) {
      const fecha = cursor.toISOString().slice(0, 10);
      semana.push({ fecha, clases: porDia[fecha] ?? 0, futuro: cursor > fin });
      cursor.setUTCDate(cursor.getUTCDate() + 1);
    }
    semanas.push(semana);
  }

  const total = Object.values(porDia).reduce((suma, n) => suma + n, 0);

  const carril = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (carril.current) carril.current.scrollLeft = carril.current.scrollWidth;
  }, []);

  return (
    <div className="mt-3 rounded-card border border-border bg-surface-raised p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="text-[11px] font-bold uppercase tracking-[0.08em] text-text-muted">
          Tu último año
        </p>
        <p className="text-[12px] text-text-secondary">
          {total === 0
            ? "Todavía sin clases"
            : `${total} ${total === 1 ? "clase" : "clases"} en 12 meses`}
        </p>
      </div>

      {/*
        Si un año no cabe, se ve el final y no el principio: lo que importa son las últimas semanas.
        Arrancar por la izquierda dejaba fuera de pantalla justo la racha en curso.
      */}
      <div ref={carril} className="mt-3 overflow-x-auto">
        <div className="flex gap-[2px]">
          {semanas.map((semana, i) => (
            <div key={i} className="flex flex-col gap-[2px]">
              {/* El rótulo del mes va sobre la primera semana que lo estrena. */}
              <span className="h-[13px] text-[9.5px] font-bold uppercase leading-none text-text-muted">
                {etiquetaMes(semana, semanas[i - 1])}
              </span>
              {semana.map((dia) => (
                <span
                  key={dia.fecha}
                  title={
                    dia.futuro
                      ? undefined
                      : `${dia.fecha}: ${dia.clases === 0 ? "sin clases" : `${dia.clases} ${dia.clases === 1 ? "clase" : "clases"}`}`
                  }
                  className={`h-[10px] w-[10px] rounded-[2.5px] ${
                    dia.futuro
                      ? "bg-transparent"
                      : dia.clases === 0
                        ? "bg-surface-sunken"
                        : dia.clases === 1
                          ? "bg-primary/45"
                          : "bg-primary"
                  }`}
                />
              ))}
            </div>
          ))}
        </div>
      </div>

      <div className="mt-2.5 flex items-center gap-1.5 text-[11px] text-text-muted">
        <span>Menos</span>
        <span className="h-[10px] w-[10px] rounded-[3px] bg-surface-sunken" />
        <span className="h-[10px] w-[10px] rounded-[3px] bg-primary/45" />
        <span className="h-[10px] w-[10px] rounded-[3px] bg-primary" />
        <span>Más</span>
      </div>
    </div>
  );
}

const MESES_CORTOS = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

type Casilla = { fecha: string; clases: number; futuro: boolean };

/**
 * El mes de una columna, solo cuando lo estrena. Se compara con la columna anterior en vez de
 * mirar el día del mes: una semana puede empezar el 29 y contener ya el día 1 del mes siguiente,
 * y con la regla ingenua ese mes se quedaría sin rótulo.
 */
function etiquetaMes(semana: Casilla[], anterior: Casilla[] | undefined): string {
  const mes = new Date(`${semana[0].fecha}T00:00:00Z`).getUTCMonth();
  if (!anterior) return MESES_CORTOS[mes];
  const mesAnterior = new Date(`${anterior[0].fecha}T00:00:00Z`).getUTCMonth();
  return mes === mesAnterior ? "" : MESES_CORTOS[mes];
}

function Profesores({ lista }: { lista: ProfesorPracticado[] }) {
  return (
    <div className="mt-3 rounded-card border border-border bg-surface-raised p-4">
      <p className="text-[11px] font-bold uppercase tracking-[0.08em] text-text-muted">
        Con quién has practicado
      </p>
      <ul className="mt-2.5 flex flex-col gap-2.5">
        {lista.map((profesor) => (
          <li key={profesor.id}>
            <Link
              href={`/profesores/${profesor.id}`}
              className="group flex items-center gap-3 rounded-base p-1.5 transition-colors hover:bg-surface-sunken"
            >
              <Avatar nombre={profesor.fullName ?? ""} fotoUrl={profesor.photoUrl} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-[14px] font-bold">{profesor.fullName}</p>
                <p className="text-[12.5px] text-text-secondary">
                  {profesor.lessons} {profesor.lessons === 1 ? "clase" : "clases"} juntos
                </p>
              </div>
              <span className="inline-flex shrink-0 items-center gap-1 text-[13px] font-bold text-primary-strong">
                Reservar
                <ArrowRight
                  size={15}
                  strokeWidth={2}
                  className="transition-transform group-hover:translate-x-0.5"
                />
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
