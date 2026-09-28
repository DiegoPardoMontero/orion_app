"use client";

import { Plus, X } from "lucide-react";
import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type PointerEvent as EventoPuntero,
  type ReactNode,
} from "react";
import {
  aHhmm,
  aMinutos,
  bajarAlPaso,
  cuposPorSemana,
  DURACION_MINIMA,
  FIN_DEL_DIA,
  huecoLibre,
  PASO,
  rangoLargo,
  redondearAlPaso,
  seCruza,
  tramoArrastrado,
  ventanaDeHoras,
  type FranjaDia,
  type Intervalo,
} from "@/components/profesor/franjas";
import type { RuleResponse } from "@/lib/api/types";
import { hora12Compacta, rangoCompacto } from "@/lib/format";

/** ISO 1–7, igual que el backend: 1 = lunes … 7 = domingo. */
export const DIAS_SEMANA = [
  { valor: 1, nombre: "Lunes", corto: "Lun" },
  { valor: 2, nombre: "Martes", corto: "Mar" },
  { valor: 3, nombre: "Miércoles", corto: "Mié" },
  { valor: 4, nombre: "Jueves", corto: "Jue" },
  { valor: 5, nombre: "Viernes", corto: "Vie" },
  { valor: 6, nombre: "Sábado", corto: "Sáb" },
  { valor: 7, nombre: "Domingo", corto: "Dom" },
];

const ALTO_HORA = 44;
const PX_POR_MINUTO = ALTO_HORA / 60;
const ANCHO_HORAS = 52;

/** Cuánto hay que mover el mouse para que un clic pase a ser un arrastre. */
const UMBRAL_RATON = 4;
/** En pantalla táctil, moverse más que esto antes de tiempo es desplazar la página, no arrastrar. */
const UMBRAL_TACTIL = 10;
/** Mantener pulsado este tiempo convierte el toque en arrastre (como en los calendarios del celular). */
const ESPERA_TACTIL = 380;
/** Arrastrando cerca del borde de la ventana, la página se desplaza sola. */
const MARGEN_AUTODESPLAZAMIENTO = 56;

/** Una franja por guardar: el día y sus minutos de inicio y fin. */
export type Propuesta = { weekday: number } & Intervalo;

type Franja = FranjaDia & { regla: RuleResponse };

type Objetivo =
  | { clase: "vacio"; weekday: number; ancla: number }
  | { clase: "franja"; franja: Franja; parte: "cuerpo" | "inicio" | "fin"; desfase: number };

type Gesto = {
  pointerId: number;
  tactil: boolean;
  x0: number;
  y0: number;
  x: number;
  y: number;
  activo: boolean;
  objetivo: Objetivo;
  espera?: ReturnType<typeof setTimeout>;
};

/** Lo que se dibuja mientras se arrastra: la franja nueva, o la existente en su nuevo sitio. */
type Vista = { tipo: "crear" | "cambiar"; weekday: number; tramo: Intervalo; valido: boolean; reglaId?: string };

/**
 * El horario semanal como lo que es: una rejilla de horas por días, donde la duración de una franja
 * es su altura y el hueco entre dos se ve sin leer nada.
 *
 * <p>Desde el 27/09/2026 se maneja como un calendario (Pardo: «como cuando se crea una reunión de
 * Teams o Meet en Google Calendar»): arrastrar sobre un día abre una franja con resolución de media
 * hora, un clic abre una de una hora, una franja se mueve arrastrándola —también a otro día— y se
 * estira desde su borde de arriba o de abajo. En pantalla táctil, lo mismo tras mantener pulsado,
 * para que deslizar el dedo siga desplazando la página. El teclado tiene su camino: el «+» de cada
 * día y cada franja abren el formulario.
 *
 * <p>Una franja nueva nunca pisa a otra: el arrastre se detiene donde empieza la vecina. Tocarse sí
 * se puede —dos franjas contiguas son un solo tramo para los cupos—.
 */
export function CalendarioSemanal({
  reglas,
  duracionClase,
  guardando,
  reemplazando,
  bloqueado,
  onCrear,
  onCambiar,
  onEditar,
  onEliminar,
  onAnadir,
}: {
  reglas: RuleResponse[];
  /** Los minutos de una clase (las cifras del servidor), para decir cuántos cupos abre una franja. */
  duracionClase: number;
  /** La franja que se está guardando, para dibujarla mientras llega la respuesta. */
  guardando: Propuesta | null;
  /** La franja que se está moviendo en el servidor: se oculta hasta que llegue la nueva. */
  reemplazando: string | null;
  /** Con un cambio en vuelo no se empieza otro. */
  bloqueado: boolean;
  onCrear: (propuesta: Propuesta) => void;
  onCambiar: (regla: RuleResponse, propuesta: Propuesta) => void;
  onEditar: (regla: RuleResponse) => void;
  onEliminar: (regla: RuleResponse) => void;
  onAnadir: (weekday: number) => void;
}) {
  const franjas = useMemo<Franja[]>(
    () =>
      reglas.map((regla) => ({
        regla,
        weekday: regla.weekday!,
        inicio: aMinutos(regla.startTime!),
        fin: aMinutos(regla.endTime!),
      })),
    [reglas],
  );
  const { desde, hasta } = ventanaDeHoras(franjas);
  const filas = hasta - desde;

  const cuerpo = useRef<HTMLDivElement>(null);
  const gesto = useRef<Gesto | null>(null);
  const suprimirClicHasta = useRef(0);
  // Lo último que se pintó, para el temporizador del toque largo y el autodesplazamiento, que
  // corren fuera del render y verían una versión vieja de las franjas.
  const contexto = useRef({ franjas, desde, hasta, bloqueado });
  useLayoutEffect(() => {
    contexto.current = { franjas, desde, hasta, bloqueado };
  });

  const [vista, setVista] = useState<Vista | null>(null);
  const [cursor, setCursor] = useState<{ weekday: number; ancla: number } | null>(null);
  const [hoy] = useState(diaDeHoy);

  // Una pantalla táctil desplaza la página con el dedo: solo cuando ya hay un arrastre en marcha
  // se le pide al navegador que no lo haga. Tiene que ser un listener no pasivo, y los de React
  // (onTouchMove) lo son: su preventDefault no haría nada.
  useEffect(() => {
    const elemento = cuerpo.current;
    if (!elemento) return;
    const frenar = (evento: TouchEvent) => {
      if (gesto.current?.activo) evento.preventDefault();
    };
    elemento.addEventListener("touchmove", frenar, { passive: false });
    return () => elemento.removeEventListener("touchmove", frenar);
  }, []);

  useEffect(() => {
    if (!vista) return;
    const alTeclear = (evento: KeyboardEvent) => {
      if (evento.key === "Escape") terminar();
    };
    window.addEventListener("keydown", alTeclear);
    return () => window.removeEventListener("keydown", alTeclear);
  });

  useEffect(() => () => {
    if (gesto.current?.espera) clearTimeout(gesto.current.espera);
  }, []);

  function delDia(weekday: number, excepto?: string): Franja[] {
    return contexto.current.franjas.filter((f) => f.weekday === weekday && f.regla.id !== excepto);
  }

  /** Qué día y qué minuto hay bajo el puntero. */
  function medir(x: number, y: number) {
    const rect = cuerpo.current!.getBoundingClientRect();
    const { desde, hasta } = contexto.current;
    const anchoDia = (rect.width - ANCHO_HORAS) / 7;
    const columna = Math.floor((x - rect.left - ANCHO_HORAS) / anchoDia);
    const minuto = desde * 60 + (y - rect.top) / PX_POR_MINUTO;
    return {
      weekday: Math.min(7, Math.max(1, columna + 1)),
      minuto: Math.min(hasta * 60, Math.max(desde * 60, minuto)),
      dentro: x >= rect.left + ANCHO_HORAS && y >= rect.top && y <= rect.bottom,
    };
  }

  /** La media hora sobre la que está el puntero, sin salirse de la rejilla. */
  function mediaHora(minuto: number): number {
    return Math.min(bajarAlPaso(minuto), contexto.current.hasta * 60 - PASO);
  }

  function calcularVista(g: Gesto): Vista | null {
    const { desde, hasta } = contexto.current;
    const { weekday, minuto } = medir(g.x, g.y);
    const objetivo = g.objetivo;

    if (objetivo.clase === "vacio") {
      const tramo = tramoArrastrado(delDia(objetivo.weekday), objetivo.ancla, mediaHora(minuto));
      return tramo && { tipo: "crear", weekday: objetivo.weekday, tramo, valido: true };
    }

    const { franja, parte } = objetivo;
    const duracion = franja.fin - franja.inicio;
    const techo = Math.min(hasta * 60, FIN_DEL_DIA);
    const reglaId = franja.regla.id;

    if (parte === "cuerpo") {
      // Se mueve entera, de media en media hora, al día que haya debajo. Si cae encima de otra, se
      // dibuja en rojo y soltarla ahí no hace nada.
      const inicio = Math.max(desde * 60, Math.min(techo - duracion, redondearAlPaso(minuto - objetivo.desfase)));
      const tramo = { inicio, fin: inicio + duracion };
      return { tipo: "cambiar", weekday, tramo, valido: !seCruza(delDia(weekday, reglaId), tramo), reglaId };
    }

    // Estirar o encoger desde un borde: hasta la vecina como mucho, y nunca por debajo de la hora
    // (o de lo que ya medía, si era una media hora que unía dos franjas).
    const hueco = huecoLibre(delDia(franja.weekday, reglaId), franja.inicio) ?? franja;
    const minima = Math.min(DURACION_MINIMA, duracion);
    const tramo =
      parte === "fin"
        ? { inicio: franja.inicio, fin: Math.max(franja.inicio + minima, Math.min(hueco.fin, techo, redondearAlPaso(minuto))) }
        : { inicio: Math.min(franja.fin - minima, Math.max(hueco.inicio, desde * 60, redondearAlPaso(minuto))), fin: franja.fin };
    return { tipo: "cambiar", weekday: franja.weekday, tramo, valido: true, reglaId };
  }

  function alPresionar(evento: EventoPuntero<HTMLDivElement>) {
    if (contexto.current.bloqueado || gesto.current) return;
    if (evento.pointerType === "mouse" && evento.button !== 0) return;
    const destino = evento.target as HTMLElement;
    if (destino.closest("[data-sin-arrastre]")) return;

    const { weekday, minuto, dentro } = medir(evento.clientX, evento.clientY);
    const bloque = destino.closest<HTMLElement>("[data-franja]");
    let objetivo: Objetivo;
    if (bloque) {
      const franja = contexto.current.franjas.find((f) => f.regla.id === bloque.dataset.franja);
      if (!franja) return;
      const borde = destino.closest<HTMLElement>("[data-borde]")?.dataset.borde;
      objetivo = {
        clase: "franja",
        franja,
        parte: borde === "inicio" || borde === "fin" ? borde : "cuerpo",
        desfase: minuto - franja.inicio,
      };
    } else {
      if (!dentro) return;
      const ancla = mediaHora(minuto);
      if (!huecoLibre(delDia(weekday), ancla)) return;
      objetivo = { clase: "vacio", weekday, ancla };
      // Sin esto, arrastrar con el mouse selecciona el texto de las horas.
      if (evento.pointerType === "mouse") evento.preventDefault();
    }

    const g: Gesto = {
      pointerId: evento.pointerId,
      tactil: evento.pointerType === "touch",
      x0: evento.clientX,
      y0: evento.clientY,
      x: evento.clientX,
      y: evento.clientY,
      activo: false,
      objetivo,
    };
    if (g.tactil) g.espera = setTimeout(activar, ESPERA_TACTIL);
    gesto.current = g;
    setCursor(null);
  }

  function activar() {
    const g = gesto.current;
    if (!g || g.activo) return;
    g.activo = true;
    try {
      cuerpo.current?.setPointerCapture(g.pointerId);
    } catch {
      // El puntero ya se soltó: el pointerup que viene cierra el gesto.
    }
    if (g.tactil) navigator.vibrate?.(12);
    setVista(calcularVista(g));
    requestAnimationFrame(autodesplazar);
  }

  /** Mientras se arrastra junto al borde de la ventana, la página baja o sube sola. */
  function autodesplazar() {
    const g = gesto.current;
    if (!g?.activo) return;
    const alto = window.innerHeight;
    let paso = 0;
    if (g.y < MARGEN_AUTODESPLAZAMIENTO) paso = -Math.ceil((MARGEN_AUTODESPLAZAMIENTO - g.y) / 3);
    else if (g.y > alto - MARGEN_AUTODESPLAZAMIENTO) paso = Math.ceil((g.y - (alto - MARGEN_AUTODESPLAZAMIENTO)) / 3);
    if (paso !== 0) {
      window.scrollBy(0, paso);
      setVista(calcularVista(g));
    }
    requestAnimationFrame(autodesplazar);
  }

  function alMover(evento: EventoPuntero<HTMLDivElement>) {
    const g = gesto.current;
    if (!g) {
      // Sin gesto, el mouse solo deja ver dónde caería una franja de una hora.
      if (evento.pointerType !== "mouse" || contexto.current.bloqueado) return;
      const destino = evento.target as HTMLElement;
      const { weekday, minuto, dentro } = medir(evento.clientX, evento.clientY);
      const sobreAlgo = destino.closest("[data-franja],[data-sin-arrastre]");
      const siguiente = dentro && !sobreAlgo ? { weekday, ancla: mediaHora(minuto) } : null;
      if (siguiente?.weekday !== cursor?.weekday || siguiente?.ancla !== cursor?.ancla) setCursor(siguiente);
      return;
    }
    if (evento.pointerId !== g.pointerId) return;
    g.x = evento.clientX;
    g.y = evento.clientY;
    if (!g.activo) {
      const distancia = Math.hypot(g.x - g.x0, g.y - g.y0);
      if (g.tactil) {
        if (distancia > UMBRAL_TACTIL) terminar(); // está desplazando la página
      } else if (distancia > UMBRAL_RATON) {
        activar();
      }
      return;
    }
    setVista(calcularVista(g));
  }

  function alSoltar(evento: EventoPuntero<HTMLDivElement>) {
    const g = gesto.current;
    if (!g || evento.pointerId !== g.pointerId) return;
    const objetivo = g.objetivo;

    if (g.activo) {
      suprimirClicHasta.current = performance.now() + 400;
      const final = calcularVista(g);
      if (final?.valido) {
        if (objetivo.clase === "vacio") {
          onCrear({ weekday: final.weekday, ...final.tramo });
        } else {
          const { franja } = objetivo;
          const cambio =
            final.weekday !== franja.weekday || final.tramo.inicio !== franja.inicio || final.tramo.fin !== franja.fin;
          if (cambio) onCambiar(franja.regla, { weekday: final.weekday, ...final.tramo });
        }
      }
    } else if (objetivo.clase === "vacio") {
      // Un clic (o un toque) sin arrastrar: una hora desde la media hora pulsada, como antes.
      const tramo = tramoArrastrado(delDia(objetivo.weekday), objetivo.ancla, objetivo.ancla);
      if (tramo) onCrear({ weekday: objetivo.weekday, ...tramo });
    }
    // El clic sobre una franja sin arrastrarla lo atiende su botón (abre el formulario).
    terminar();
  }

  function terminar() {
    const g = gesto.current;
    if (g?.espera) clearTimeout(g.espera);
    if (g && cuerpo.current?.hasPointerCapture(g.pointerId)) cuerpo.current.releasePointerCapture(g.pointerId);
    gesto.current = null;
    setVista(null);
  }

  const lineas: CSSProperties = {
    backgroundImage: [
      `repeating-linear-gradient(to bottom, var(--color-border) 0 1px, transparent 1px ${ALTO_HORA}px)`,
      `repeating-linear-gradient(to bottom, transparent 0 ${ALTO_HORA / 2}px, color-mix(in oklch, var(--color-border) 55%, transparent) ${ALTO_HORA / 2}px ${ALTO_HORA / 2 + 1}px, transparent ${ALTO_HORA / 2 + 1}px ${ALTO_HORA}px)`,
    ].join(","),
  };
  const posicion = (tramo: Intervalo): CSSProperties => ({
    top: (tramo.inicio - desde * 60) * PX_POR_MINUTO + 1,
    height: (tramo.fin - tramo.inicio) * PX_POR_MINUTO - 2,
  });
  const cuposNuevos = (propuesta: Propuesta) =>
    cuposPorSemana([...franjas, propuesta], duracionClase) - cuposPorSemana(franjas, duracionClase);

  return (
    <div className="rounded-card border border-border bg-surface-raised shadow-sm">
      {/* Cabecera de días, pegada arriba mientras la rejilla se desplaza. El «+» es el camino del
          teclado: abre el formulario con desde y hasta. */}
      <div className="sticky top-0 z-30 grid grid-cols-[52px_repeat(7,minmax(0,1fr))] rounded-t-card border-b border-border bg-surface-raised">
        <div />
        {DIAS_SEMANA.map((dia) => (
          <div key={dia.valor} className="flex items-center justify-center gap-1 border-l border-border py-1.5">
            <span
              className={`text-[12.5px] font-bold ${dia.valor === hoy ? "text-primary-strong" : "text-text-secondary"}`}
            >
              {dia.corto}
              {dia.valor === hoy && <span className="sr-only"> (hoy)</span>}
            </span>
            <button
              type="button"
              aria-label={`Añadir franja el ${dia.nombre.toLowerCase()}`}
              title={`Añadir franja el ${dia.nombre.toLowerCase()}`}
              onClick={() => onAnadir(dia.valor)}
              className="grid h-7 w-7 place-items-center rounded-full text-text-muted transition-colors hover:bg-primary-soft hover:text-primary-strong focus-visible:shadow-focus"
            >
              <Plus size={14} strokeWidth={2.2} />
            </button>
          </div>
        ))}
      </div>

      <div
        ref={cuerpo}
        className={`relative grid select-none grid-cols-[52px_repeat(7,minmax(0,1fr))] [-webkit-touch-callout:none] ${
          vista?.tipo === "cambiar" ? "cursor-grabbing" : ""
        }`}
        style={{ height: filas * ALTO_HORA }}
        onPointerDown={alPresionar}
        onPointerMove={alMover}
        onPointerUp={alSoltar}
        onPointerCancel={terminar}
        onPointerLeave={() => setCursor(null)}
        onDragStart={(evento) => evento.preventDefault()}
        onContextMenu={(evento) => {
          // El toque largo abre el menú del sistema en algunos celulares: aquí es un arrastre.
          if (gesto.current) evento.preventDefault();
        }}
      >
        {/* Columna de horas: la etiqueta se sube media línea para quedar sobre su divisoria. */}
        <div className="relative" aria-hidden="true">
          {Array.from({ length: filas }, (_, i) => (
            <div
              key={i}
              className="absolute right-2 -translate-y-1/2 text-[11px] font-semibold tabular-nums text-text-muted"
              style={{ top: Math.max(i * ALTO_HORA, 7) }}
            >
              {hora12Compacta(`${String(desde + i).padStart(2, "0")}:00`)}
            </div>
          ))}
        </div>

        {DIAS_SEMANA.map((dia) => {
          const delDiaVisible = franjas.filter((f) => f.weekday === dia.valor);
          const sugerencia =
            cursor?.weekday === dia.valor && !vista
              ? tramoArrastrado(delDiaVisible, cursor.ancla, cursor.ancla)
              : null;
          const alFinal = dia.valor >= 6;

          return (
            <div
              key={dia.valor}
              className={`relative border-l border-border ${bloqueado ? "cursor-progress" : ""}`}
              style={lineas}
            >
              {sugerencia && (
                <div
                  aria-hidden="true"
                  className="pointer-events-none absolute inset-x-1 rounded-base bg-primary-soft/70 px-2 py-1 text-[11px] font-bold text-primary-strong"
                  style={posicion(sugerencia)}
                >
                  + {hora12Compacta(aHhmm(sugerencia.inicio))}
                </div>
              )}

              {delDiaVisible.map((franja) => {
                if (franja.regla.id === reemplazando) return null;
                const rango = rangoCompacto(aHhmm(franja.inicio), aHhmm(franja.fin));
                const moviendose = vista?.reglaId === franja.regla.id;
                return (
                  <div
                    key={franja.regla.id}
                    data-franja={franja.regla.id}
                    className={`group absolute inset-x-1 rounded-base bg-night text-on-primary shadow-sm transition-opacity ${
                      moviendose ? "opacity-30" : ""
                    }`}
                    style={posicion(franja)}
                  >
                    <button
                      type="button"
                      aria-label={`Franja del ${dia.nombre.toLowerCase()}, ${rangoLargo(franja.inicio, franja.fin)}. Cambiar horas`}
                      onClick={() => {
                        // Si venía de un arrastre, el clic que sigue no abre nada.
                        if (performance.now() < suprimirClicHasta.current) return;
                        onEditar(franja.regla);
                      }}
                      className="absolute inset-0 cursor-grab overflow-hidden rounded-base px-2 py-1 text-left focus-visible:shadow-focus active:cursor-grabbing"
                    >
                      <span className="block pr-5 text-[11.5px] font-bold leading-tight">{rango}</span>
                    </button>
                    {/* Los bordes que estiran: arriba cambia el inicio y abajo el fin. */}
                    <div data-borde="inicio" aria-hidden="true" className="absolute inset-x-0 top-0 h-2 cursor-ns-resize" />
                    <div
                      data-borde="fin"
                      aria-hidden="true"
                      className="absolute inset-x-0 bottom-0 flex h-2.5 cursor-ns-resize items-end justify-center pb-[3px]"
                    >
                      <span className="h-[3px] w-6 rounded-full bg-white/0 transition-colors group-hover:bg-white/55" />
                    </div>
                    <button
                      type="button"
                      data-sin-arrastre
                      aria-label={`Eliminar la franja de ${rango} el ${dia.nombre.toLowerCase()}`}
                      title="Eliminar"
                      onClick={() => onEliminar(franja.regla)}
                      className="absolute right-1 top-1 grid h-5 w-5 place-items-center rounded-full bg-white/15 opacity-80 transition hover:bg-primary hover:opacity-100 focus-visible:opacity-100 group-hover:opacity-100"
                    >
                      <X size={11} strokeWidth={2.6} />
                    </button>
                  </div>
                );
              })}

              {vista?.weekday === dia.valor && (
                <div
                  aria-hidden="true"
                  className={`pointer-events-none absolute inset-x-1 z-20 rounded-base ${
                    !vista.valido
                      ? "border-2 border-error bg-error-bg/90"
                      : vista.tipo === "crear"
                        ? "border-2 border-dashed border-primary bg-primary/15"
                        : "bg-night/90 shadow-lg ring-2 ring-primary"
                  }`}
                  style={posicion(vista.tramo)}
                >
                  <Etiqueta alFinal={alFinal} error={!vista.valido}>
                    {!vista.valido
                      ? "Se cruza con otra franja"
                      : `${rangoLargo(vista.tramo.inicio, vista.tramo.fin)}${
                          vista.tipo === "crear" ? ` · +${cuposNuevos({ weekday: dia.valor, ...vista.tramo })} cupos` : ""
                        }`}
                  </Etiqueta>
                </div>
              )}

              {guardando?.weekday === dia.valor && (
                <div
                  aria-hidden="true"
                  className="pointer-events-none absolute inset-x-1 z-10 animate-pulse rounded-base bg-night/60 px-2 py-1 text-[11.5px] font-bold text-on-primary"
                  style={posicion(guardando)}
                >
                  {rangoCompacto(aHhmm(guardando.inicio), aHhmm(guardando.fin))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/**
 * La hora que se está marcando, sobre el bloque fantasma. Va en una pastilla que puede salirse de
 * la columna: en la columna de un día no cabe «11:30 AM – 1:00 PM» en una línea.
 */
function Etiqueta({ alFinal, error, children }: { alFinal: boolean; error: boolean; children: ReactNode }) {
  return (
    <span
      className={`absolute top-1 whitespace-nowrap rounded-pill px-2.5 py-1 text-[12px] font-bold shadow-md ${
        alFinal ? "right-1" : "left-1"
      } ${error ? "bg-error text-on-primary" : "bg-night text-on-primary"}`}
    >
      {children}
    </span>
  );
}

/** El día de la semana de hoy en Bogotá, 1 = lunes … 7 = domingo. */
function diaDeHoy(): number {
  const corto = new Intl.DateTimeFormat("en-US", { timeZone: "America/Bogota", weekday: "short" }).format(new Date());
  return ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].indexOf(corto) + 1;
}
